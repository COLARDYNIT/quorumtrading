package com.quorumtrading.application.service;

import com.quorumtrading.application.event.SimulationCompletedEvent;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.EndSimulationResult;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.ExternalPauseReason;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EndSimulationService}: the LIVE-expiry arm, and the stall detector that
 * replaced a wall-clock reaper.
 *
 * <p>Several of these tests exist because the behaviour they forbid was shipped and did damage.
 * Read {@code docs/adr/0004-watchdogs-pause-on-evidence-never-fabricate-completion.md} before
 * relaxing any of them — in particular, any test asserting {@code never()} on
 * {@code updateStatus} / {@code brokerAccountRepository.save} / {@code publish} is pinning a
 * specific side effect that leaked in production, not being defensive for its own sake.</p>
 */
class EndSimulationServiceTest {

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private EventPublisher<SimulationCompletedEvent> simulationCompletedEventPublisher;

    @Mock
    private BrokerAccountRepository brokerAccountRepository;

    @Mock
    private SimulationContributionRepository contributionRepository;

    private static final Duration LEASE_STALE_AFTER = Duration.ofMinutes(20);
    private static final Duration STALL_GRACE = Duration.ofMinutes(30);
    private static final Duration OBSERVATION_GAP_RESET = Duration.ofMinutes(20);

    /** A clock the tests drive by hand — the guards are about time, so time must be an input. */
    private MutableClock clock;
    private EndSimulationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clock = new MutableClock(Instant.parse("2026-08-02T10:00:00Z"));
        service = newService(EndSimulationService.STALL_ACTION_PAUSE);
        // Armed by default: most tests are about the predicate, not the guards. The guard tests
        // below construct their own service and drive the clock themselves.
        clock.advance(STALL_GRACE.plusMinutes(1));
        when(simulationRepository.updateStatus(any(), any(), any(), any(), any())).thenReturn(true);
    }

    private EndSimulationService newService(String stallAction) {
        return new EndSimulationService(
                simulationRepository, portfolioRepository, simulationCompletedEventPublisher,
                brokerAccountRepository, contributionRepository,
                LEASE_STALE_AFTER, STALL_GRACE, OBSERVATION_GAP_RESET, stallAction, clock);
    }

    // ================================================================================
    // The LIVE-expiry arm — this simulation genuinely finished its period
    // ================================================================================

    @Test
    void shouldEndExpiredSimulation() {
        // Given: a LIVE simulation whose period elapsed an hour ago
        Instant endDate = clock.instant().minus(1, ChronoUnit.HOURS);
        Simulation simulation = createSimulation(endDate, SimulationStatus.LIVE, false);

        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(simulation));

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(1, result.simulationsEnded());
        assertEquals(0, result.simulationsSkipped());
        assertEquals(0, result.simulationsFailed());
        assertEquals(0, result.simulationsPaused());
        assertTrue(result.errors().isEmpty());

        // COMPLETED at 100% is still the outcome — but it now travels through the narrow
        // compare-and-set writers rather than a whole-entity save().
        verify(simulationRepository).updateStatus(eq(simulation.getSimulationId()),
                eq(SimulationStatus.LIVE), eq(SimulationStatus.COMPLETED), notNull(), isNull());
        verify(simulationRepository).updateProgress(simulation.getSimulationId(), 100);
        assertEquals(SimulationStatus.COMPLETED, simulation.getStatus());
        assertNotNull(simulation.getCompletedAt());

        // The old code also called setPaused(true) here and a test asserted it. Production
        // silently dropped it (save() never wrote the pause columns), so the assertion proved
        // nothing; persisting it now would leave COMPLETED rows in findPausedSimulations().
        verify(simulationRepository, never()).updatePauseState(any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("a LIVE row transitioned by someone else in the meantime is not double-ended")
    void shouldNotDoubleEndWhenAnotherWriterWonTheTransition() {
        Instant endDate = clock.instant().minus(1, ChronoUnit.HOURS);
        Simulation simulation = createSimulation(endDate, SimulationStatus.LIVE, false);

        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(simulation));
        when(simulationRepository.updateStatus(any(), any(), any(), any(), any())).thenReturn(false);

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(0, result.simulationsEnded());
        assertEquals(1, result.simulationsSkipped());
        // Losing the compare-and-set must stop the side effects too: releasing the same capital
        // twice is what produced "Cannot release more than reserved" eight times in one incident.
        verify(simulationRepository, never()).updateProgress(any(), anyInt());
        verify(brokerAccountRepository, never()).save(any());
        verify(simulationCompletedEventPublisher, never()).publish(any());
    }

    @Test
    void shouldEndMultipleExpiredSimulations() {
        Instant endDate = clock.instant().minus(1, ChronoUnit.HOURS);
        Simulation sim1 = createSimulation(endDate, SimulationStatus.LIVE, false);
        Simulation sim2 = createSimulation(endDate, SimulationStatus.LIVE, false);

        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(sim1, sim2));

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(2, result.simulationsEnded());
        assertEquals(0, result.simulationsSkipped());
        assertEquals(0, result.simulationsFailed());
        verify(simulationRepository, times(2))
                .updateStatus(any(), eq(SimulationStatus.LIVE), eq(SimulationStatus.COMPLETED), any(), any());
    }

    @Test
    @DisplayName("a SIMULATING backtest is never completed by the expiry arm, however old")
    void shouldNotCompleteBacktestJustBecauseItIsOld() {
        // This is the old shouldForceCompleteStuckSimulatingSimulation, inverted. It used to
        // assert COMPLETED for a SIMULATING backtest two hours past its endDate, with no
        // liveness input of any kind — the bug, written down as a test. A backtest's endDate is
        // in the past from the moment it is created, so "expired" says nothing about whether it
        // has finished.
        Instant endDate = clock.instant().minus(2, ChronoUnit.HOURS);
        Simulation simulation = createSimulation(endDate, SimulationStatus.SIMULATING, false);

        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(simulation));

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(0, result.simulationsEnded());
        assertEquals(1, result.simulationsSkipped());
        assertEquals(SimulationStatus.SIMULATING, simulation.getStatus());
        verify(simulationRepository, never()).updateStatus(any(), any(), any(), any(), any());
        verify(simulationRepository, never()).save(any(Simulation.class));
        verify(brokerAccountRepository, never()).save(any());
        verify(simulationCompletedEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("a young SIMULATING backtest is skipped by the expiry arm too")
    void shouldSkipSimulatingSimulationRegardlessOfAge() {
        // Formerly shouldSkipSimulatingSimulationWithinGracePeriod. Same intent — a running
        // backtest is not this arm's business — but no longer expressed as a wall-clock window.
        Instant endDate = clock.instant().minus(30, ChronoUnit.MINUTES);
        Simulation simulation = createSimulation(endDate, SimulationStatus.SIMULATING, false);

        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(simulation));

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(0, result.simulationsEnded());
        assertEquals(1, result.simulationsSkipped());
        verify(simulationRepository, never()).updateStatus(any(), any(), any(), any(), any());
    }

    @Test
    void shouldSkipAlreadyCompletedSimulation() {
        Instant endDate = clock.instant().minus(1, ChronoUnit.HOURS);
        Simulation simulation = createSimulation(endDate, SimulationStatus.COMPLETED, true);

        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(simulation));

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(0, result.simulationsEnded());
        assertEquals(1, result.simulationsSkipped());
        verify(simulationRepository, never()).updateStatus(any(), any(), any(), any(), any());
    }

    @Test
    void shouldSkipAlreadyFailedSimulation() {
        Instant endDate = clock.instant().minus(1, ChronoUnit.HOURS);
        Simulation simulation = createSimulation(endDate, SimulationStatus.FAILED, true);

        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(simulation));

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(0, result.simulationsEnded());
        assertEquals(1, result.simulationsSkipped());
        verify(simulationRepository, never()).updateStatus(any(), any(), any(), any(), any());
    }

    @Test
    void shouldSkipPendingSimulation() {
        Instant endDate = clock.instant().minus(1, ChronoUnit.HOURS);
        Simulation simulation = createSimulation(endDate, SimulationStatus.PENDING, false);

        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(simulation));

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(0, result.simulationsEnded());
        assertEquals(1, result.simulationsSkipped());
        verify(simulationRepository, never()).updateStatus(any(), any(), any(), any(), any());
    }

    @Test
    void shouldSkipWaitingForDataSimulation() {
        Instant endDate = clock.instant().minus(1, ChronoUnit.HOURS);
        Simulation simulation = createSimulation(endDate, SimulationStatus.WAITING_FOR_DATA, false);

        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(simulation));

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(0, result.simulationsEnded());
        assertEquals(1, result.simulationsSkipped());
        verify(simulationRepository, never()).updateStatus(any(), any(), any(), any(), any());
    }

    @Test
    void shouldHandleNoExpiredSimulations() {
        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(0, result.simulationsEnded());
        assertEquals(0, result.simulationsSkipped());
        assertEquals(0, result.simulationsFailed());
        assertEquals(0, result.simulationsPaused());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void shouldHandleRepositoryWriteError() {
        Instant endDate = clock.instant().minus(1, ChronoUnit.HOURS);
        Simulation simulation = createSimulation(endDate, SimulationStatus.LIVE, false);

        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(simulation));
        when(simulationRepository.updateStatus(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Database error"));

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(0, result.simulationsEnded());
        assertEquals(1, result.simulationsFailed());
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().get(0).contains("Database error"));
    }

    @Test
    void shouldContinueProcessingAfterOneFailure() {
        Instant endDate = clock.instant().minus(1, ChronoUnit.HOURS);
        Simulation sim1 = createSimulation(endDate, SimulationStatus.LIVE, false);
        Simulation sim2 = createSimulation(endDate, SimulationStatus.LIVE, false);

        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(sim1, sim2));
        when(simulationRepository.updateStatus(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Database error"))
                .thenReturn(true);

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(1, result.simulationsEnded());
        assertEquals(1, result.simulationsFailed());
        assertEquals(1, result.errors().size());
    }

    @Test
    void shouldHandleGlobalError() {
        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenThrow(new RuntimeException("Global database error"));

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(0, result.simulationsEnded());
        assertEquals(0, result.simulationsSkipped());
        assertEquals(0, result.simulationsFailed());
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().get(0).contains("Global error"));
    }

    // ================================================================================
    // The stall detector — evidence-based, pause-only
    // ================================================================================

    @Test
    @DisplayName("a stalled backtest is paused with EXECUTION_STALLED and nothing else happens")
    void shouldPauseStalledBacktestAndDoNothingElse() {
        Simulation stalled = createSimulation(
                clock.instant().minus(200, ChronoUnit.DAYS), SimulationStatus.SIMULATING, false);
        when(simulationRepository.findStalledBacktests(LEASE_STALE_AFTER, STALL_GRACE))
                .thenReturn(List.of(stalled));

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(1, result.simulationsPaused());
        assertEquals(0, result.simulationsEnded());
        verify(simulationRepository).updatePauseState(stalled.getSimulationId(), false, true,
                ExternalPauseReason.EXECUTION_STALLED);

        // These three are the side effects that leaked live, every five minutes, on a run that
        // was still trading: a fabricated terminal state, a released capital reservation, and a
        // completion event feeding the results analyser. None may happen.
        verify(simulationRepository, never()).updateStatus(any(), any(), any(), any(), any());
        verify(simulationRepository, never()).save(any(Simulation.class));
        verify(simulationRepository, never()).updateProgress(any(), anyInt());
        verify(brokerAccountRepository, never()).save(any());
        verify(simulationCompletedEventPublisher, never()).publish(any());
        assertEquals(SimulationStatus.SIMULATING, stalled.getStatus());
    }

    @Test
    @DisplayName("the detector asks the database for evidence, never for age")
    void shouldQueryWithTheConfiguredEvidenceWindows() {
        service.endExpiredSimulations();
        verify(simulationRepository).findStalledBacktests(LEASE_STALE_AFTER, STALL_GRACE);
    }

    @Test
    @DisplayName("a second tick on an already-paused row does nothing (the query excludes it)")
    void shouldBeIdempotentAcrossTicks() {
        Simulation stalled = createSimulation(
                clock.instant().minus(200, ChronoUnit.DAYS), SimulationStatus.SIMULATING, false);
        when(simulationRepository.findStalledBacktests(any(), any()))
                .thenReturn(List.of(stalled))   // first tick: found
                .thenReturn(List.of());         // second tick: now paused, so excluded

        service.endExpiredSimulations();
        clock.advance(Duration.ofMinutes(5));
        EndSimulationResult second = service.endExpiredSimulations();

        assertEquals(0, second.simulationsPaused());
        verify(simulationRepository, times(1)).updatePauseState(any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("stall-action=off disables the detector entirely; the LIVE arm still runs")
    void shouldNotDetectWhenActionIsOff() {
        service = newService(EndSimulationService.STALL_ACTION_OFF);
        clock.advance(STALL_GRACE.plusMinutes(1));

        service.endExpiredSimulations();

        verify(simulationRepository, never()).findStalledBacktests(any(), any());
        verify(simulationRepository, never()).updatePauseState(any(), anyBoolean(), anyBoolean(), any());
        verify(simulationRepository).findExpiredSimulations(any());
    }

    @Test
    @DisplayName("a failure to pause one stalled run is reported and does not stop the rest")
    void shouldReportPauseFailuresAndContinue() {
        Simulation first = createSimulation(
                clock.instant().minus(200, ChronoUnit.DAYS), SimulationStatus.SIMULATING, false);
        Simulation second = createSimulation(
                clock.instant().minus(200, ChronoUnit.DAYS), SimulationStatus.SIMULATING, false);
        when(simulationRepository.findStalledBacktests(any(), any()))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("write failed"))
                .doNothing()
                .when(simulationRepository).updatePauseState(any(), anyBoolean(), anyBoolean(), any());

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(1, result.simulationsPaused());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("write failed"));
    }

    @Test
    @DisplayName("a query failure in the detector does not sink the LIVE arm's result")
    void shouldSurviveADetectorQueryFailure() {
        Instant endDate = clock.instant().minus(1, ChronoUnit.HOURS);
        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(createSimulation(endDate, SimulationStatus.LIVE, false)));
        when(simulationRepository.findStalledBacktests(any(), any()))
                .thenThrow(new RuntimeException("detector query failed"));

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(1, result.simulationsEnded());
        assertEquals(0, result.simulationsPaused());
        assertTrue(result.errors().get(0).contains("detector query failed"));
    }

    // ================================================================================
    // The guards — "a watchdog may not count time it was not awake for"
    // ================================================================================

    @Test
    @DisplayName("G1: nothing is touched within stall-grace of process start (rolling deploys)")
    void shouldNotActWithinStallGraceOfProcessStart() {
        clock = new MutableClock(Instant.parse("2026-08-02T10:00:00Z"));
        service = newService(EndSimulationService.STALL_ACTION_PAUSE);
        clock.advance(Duration.ofMinutes(3));   // 3 minutes of uptime, grace is 30

        when(simulationRepository.findStalledBacktests(any(), any()))
                .thenReturn(List.of(createSimulation(
                        clock.instant().minus(200, ChronoUnit.DAYS), SimulationStatus.SIMULATING, false)));

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(0, result.simulationsPaused());
        verify(simulationRepository, never()).findStalledBacktests(any(), any());
        verify(simulationRepository, never()).updatePauseState(any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("G2: a 31-hour gap between ticks re-arms instead of acting (laptop suspend)")
    void shouldReArmAfterAnObservationGap() {
        clock = new MutableClock(Instant.parse("2026-08-02T10:00:00Z"));
        service = newService(EndSimulationService.STALL_ACTION_PAUSE);

        // Tick normally for long enough to be armed.
        for (int i = 0; i < 8; i++) {
            clock.advance(Duration.ofMinutes(5));
            service.endExpiredSimulations();
        }
        verify(simulationRepository, atLeastOnce()).findStalledBacktests(any(), any());
        clearInvocations(simulationRepository);

        // Lid closed for 31 hours. Every wall-clock signal in the database aged with it.
        clock.advance(Duration.ofHours(31));
        EndSimulationResult afterWake = service.endExpiredSimulations();

        assertEquals(0, afterWake.simulationsPaused());
        verify(simulationRepository, never()).findStalledBacktests(any(), any());

        // ...and it stays re-armed for a full stall-grace afterwards.
        clock.advance(Duration.ofMinutes(5));
        service.endExpiredSimulations();
        verify(simulationRepository, never()).findStalledBacktests(any(), any());
    }

    @Test
    @DisplayName("G2: hundreds of coalesced catch-up ticks after a gap cause zero transitions")
    void shouldNotActDuringACoalescedTickBurst() {
        // quarkus.quartz.misfire-policy…=ignore-misfire-policy replays every missed firing
        // back-to-back on wake: after a 31h suspend the 5-minute trigger wants ~372 of them, and
        // they all land within seconds. This is exactly why confirmation cannot be counted in
        // ticks — a "confirm over N consecutive ticks" counter is satisfied in milliseconds, on
        // precisely the case it was meant to protect against.
        clock = new MutableClock(Instant.parse("2026-08-02T10:00:00Z"));
        service = newService(EndSimulationService.STALL_ACTION_PAUSE);
        for (int i = 0; i < 8; i++) {
            clock.advance(Duration.ofMinutes(5));
            service.endExpiredSimulations();
        }
        clearInvocations(simulationRepository);

        clock.advance(Duration.ofHours(31));
        when(simulationRepository.findStalledBacktests(any(), any()))
                .thenReturn(List.of(createSimulation(
                        clock.instant().minus(200, ChronoUnit.DAYS), SimulationStatus.SIMULATING, false)));

        for (int tick = 0; tick < 400; tick++) {
            clock.advance(Duration.ofMillis(3));
            service.endExpiredSimulations();
        }

        verify(simulationRepository, never()).findStalledBacktests(any(), any());
        verify(simulationRepository, never()).updatePauseState(any(), anyBoolean(), anyBoolean(), any());
        verify(simulationRepository, never()).updateStatus(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("after the window has been observed continuously, it acts exactly once")
    void shouldActOnceTheWindowHasBeenObservedContinuously() {
        clock = new MutableClock(Instant.parse("2026-08-02T10:00:00Z"));
        service = newService(EndSimulationService.STALL_ACTION_PAUSE);

        Simulation stalled = createSimulation(
                clock.instant().minus(200, ChronoUnit.DAYS), SimulationStatus.SIMULATING, false);
        when(simulationRepository.findStalledBacktests(any(), any()))
                .thenReturn(List.of(stalled))
                .thenReturn(List.of());   // paused now, so the predicate excludes it

        int paused = 0;
        // 5-minute ticks for well over the 30-minute grace.
        for (int i = 0; i < 12; i++) {
            clock.advance(Duration.ofMinutes(5));
            paused += service.endExpiredSimulations().simulationsPaused();
        }

        assertEquals(1, paused);
        verify(simulationRepository, times(1)).updatePauseState(
                stalled.getSimulationId(), false, true, ExternalPauseReason.EXECUTION_STALLED);
    }

    @Test
    @DisplayName("a paused backtest is never touched — that is the DB predicate's job, and ours")
    void shouldNeverTouchAPausedBacktest() {
        // The predecessor read no pause flag at all, so pausing a backtest and going to lunch
        // returned a COMPLETED run — as did the SIMULATING+paused state that
        // restart_simulation_from_decision deliberately parks a run in. The database predicate
        // excludes both, so an empty result here is the contract; this test pins that the
        // service adds no second path that could reach such a row.
        Simulation userPaused = createSimulation(
                clock.instant().minus(400, ChronoUnit.DAYS), SimulationStatus.SIMULATING, true);
        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(userPaused));
        when(simulationRepository.findStalledBacktests(any(), any())).thenReturn(List.of());

        EndSimulationResult result = service.endExpiredSimulations();

        assertEquals(0, result.simulationsEnded());
        assertEquals(0, result.simulationsPaused());
        assertEquals(SimulationStatus.SIMULATING, userPaused.getStatus());
        assertTrue(userPaused.isPaused());
        verify(simulationRepository, never()).updateStatus(any(), any(), any(), any(), any());
        verify(simulationRepository, never()).updatePauseState(any(), anyBoolean(), anyBoolean(), any());
    }

    // ================================================================================
    // Return accounting (unchanged behaviour, LIVE arm)
    // ================================================================================

    @Test
    void shouldComputeReturnAgainstTotalInvestedWhenContributionsExist() {
        // A LIVE sim seeded at 10000 that received a 10000 DCA top-up, now worth 10000. With the
        // top-up the basis is 20000, so the honest return is −50%: the contribution must NOT be
        // counted as profit.
        Instant endDate = clock.instant().minus(1, ChronoUnit.HOURS);
        Simulation simulation = createSimulation(endDate, SimulationStatus.LIVE, false);
        SimulationId simId = simulation.getSimulationId();

        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(simulation));
        when(portfolioRepository.findLatestBySimulationId(simId))
                .thenReturn(Optional.of(new PortfolioSnapshot(
                        simId, clock.instant(), Map.of(),
                        Money.usd(new BigDecimal("10000.00")),
                        Money.usd(new BigDecimal("10000.00")))));
        when(contributionRepository.findBySimulationId(simId))
                .thenReturn(List.of(new Contribution(
                        ContributionId.generate(), simId,
                        Money.usd(new BigDecimal("10000.00")),
                        endDate.minus(5, ChronoUnit.DAYS), null, null, clock.instant())));

        service.endExpiredSimulations();

        ArgumentCaptor<SimulationCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(SimulationCompletedEvent.class);
        verify(simulationCompletedEventPublisher).publish(eventCaptor.capture());
        SimulationCompletedEvent event = eventCaptor.getValue();

        assertEquals(0, event.finalPortfolioValue().compareTo(new BigDecimal("10000.00")));
        assertEquals(-50.0, event.totalReturn().doubleValue(), 1e-6,
                "Return must be against total invested (20000), not seed (10000)");
    }

    @Test
    void shouldComputeNaiveReturnWhenNoContributions() {
        // Regression guard: with no contributions, basis = seed. 10000 → 11000 = +10%.
        Instant endDate = clock.instant().minus(1, ChronoUnit.HOURS);
        Simulation simulation = createSimulation(endDate, SimulationStatus.LIVE, false);
        SimulationId simId = simulation.getSimulationId();

        when(simulationRepository.findExpiredSimulations(any(Instant.class)))
                .thenReturn(List.of(simulation));
        when(portfolioRepository.findLatestBySimulationId(simId))
                .thenReturn(Optional.of(new PortfolioSnapshot(
                        simId, clock.instant(), Map.of(),
                        Money.usd(new BigDecimal("11000.00")),
                        Money.usd(new BigDecimal("11000.00")))));
        when(contributionRepository.findBySimulationId(simId))
                .thenReturn(List.of());

        service.endExpiredSimulations();

        ArgumentCaptor<SimulationCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(SimulationCompletedEvent.class);
        verify(simulationCompletedEventPublisher).publish(eventCaptor.capture());
        assertEquals(10.0, eventCaptor.getValue().totalReturn().doubleValue(), 1e-6);
    }

    private Simulation createSimulation(Instant endDate, SimulationStatus status, boolean paused) {
        Instant startDate = endDate.minus(30, ChronoUnit.DAYS);

        Simulation simulation = new Simulation(
                new SimulationId(UUID.randomUUID()),
                "Test Simulation",
                new StrategyId(UUID.randomUUID()),
                startDate,
                endDate,
                Money.usd(new BigDecimal("10000.00"))
        );

        if (status == SimulationStatus.COMPLETED) {
            simulation.setStatus(status);
            simulation.setCompletedAt(clock.instant());
        } else if (status == SimulationStatus.FAILED) {
            simulation.fail("test failure");
        } else {
            simulation.setStatus(status);
        }

        // Deprecated setPaused() for test setup, to skip state-machine validation.
        simulation.setPaused(paused);

        return simulation;
    }

    /**
     * A clock the test moves by hand. The guards are statements about observed time, so a test
     * that let them read the real clock could only assert "nothing happened within 30 minutes of
     * now", which is every test and no test.
     */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
