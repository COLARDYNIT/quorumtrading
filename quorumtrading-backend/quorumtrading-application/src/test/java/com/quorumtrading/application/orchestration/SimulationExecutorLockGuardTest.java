package com.quorumtrading.application.orchestration;

import com.quorumtrading.application.event.ConsensusStartedEvent;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationLockPort;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.service.BrokerRouter;
import com.quorumtrading.application.usecase.ReachConsensusUseCase;
import com.quorumtrading.application.usecase.UpdatePortfolioWithMarketValueUseCase;
import com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the two-level execution guard of {@link SimulationExecutor#startSimulation}.
 *
 * <p>The pickup scheduler re-offers every SIMULATING simulation on each 30s tick. The DB
 * lease used to be the only guard, and it is not re-entrant call-to-call — so every re-offer
 * of a run already executing in the same JVM burned a DB round-trip and logged
 * "already executing on another instance" although only one instance existed. The in-memory
 * set now absorbs same-JVM re-offers before the lease is ever consulted, and the boolean
 * return tells callers whether the entity in their hands was actually executed by this call
 * (and is therefore theirs to save).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SimulationExecutorLockGuardTest {

    @Mock private ReachConsensusUseCase reachConsensusUseCase;
    @Mock private UpdatePortfolioWithMarketValueUseCase updatePortfolioWithMarketValueUseCase;
    @Mock private StrategyParser strategyParser;
    @Mock private StrategyRepository strategyRepository;
    @Mock private MarketEventRepository marketEventRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private EventPublisher<ConsensusStartedEvent> consensusStartedEventPublisher;
    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private SimulationPortfolioRepository simulationPortfolioRepository;
    @Mock private BrokerRouter brokerRouter;
    @Mock private SimulationRepository simulationRepository;
    @Mock private SimulationLockPort simulationLockPort;
    @Mock private DispatchNotificationUseCase dispatchNotificationUseCase;
    @Mock private ConsensusDecisionRepository consensusDecisionRepository;

    private SimulationExecutor executor;
    private java.util.concurrent.ExecutorService consensusExecutor;
    private Simulation simulation;

    @BeforeEach
    void setUp() {
        consensusExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        executor = new SimulationExecutor(
            new BoundedConsensusEvaluator(reachConsensusUseCase, null, consensusExecutor,
                java.time.Duration.ofSeconds(30)),
            updatePortfolioWithMarketValueUseCase, strategyParser,
            strategyRepository, marketEventRepository, portfolioRepository,
            consensusStartedEventPublisher, brokerAccountRepository,
            simulationPortfolioRepository, brokerRouter, simulationRepository,
            simulationLockPort, dispatchNotificationUseCase, consensusDecisionRepository);

        simulation = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "Lock Guard Sim",
            new StrategyId(UUID.randomUUID()),
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-02-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000")));
        simulation.start(); // past start date → SIMULATING
    }

    @Test
    @DisplayName("lease held elsewhere → returns false without executing or unlocking")
    void leaseRefusedReturnsFalseWithoutExecuting() {
        when(simulationLockPort.tryLock(any(), any(), any())).thenReturn(false);

        boolean executed = executor.startSimulation(simulation, "strategy");

        assertThat(executed).isFalse();
        verify(strategyRepository, times(0)).findById(any());
        // The lease is not ours — releasing it would free a lock some other executor holds
        verify(simulationLockPort, times(0)).unlock(any(), any());
    }

    @Test
    @Timeout(20)
    @DisplayName("same-JVM re-submit while running → false, and the DB lease is consulted only once")
    void inFlightResubmitIsAbsorbedBeforeTheDbLease() throws Exception {
        when(simulationLockPort.tryLock(any(), any(), any())).thenReturn(true);

        CountDownLatch executorEntered = new CountDownLatch(1);
        CountDownLatch releaseExecutor = new CountDownLatch(1);
        when(strategyRepository.findById(any())).thenAnswer(invocation -> {
            executorEntered.countDown();
            releaseExecutor.await(15, TimeUnit.SECONDS);
            return Optional.empty(); // → IllegalStateException → run fails and cleans up
        });

        AtomicBoolean firstResult = new AtomicBoolean(true);
        Thread first = new Thread(() -> {
            try {
                firstResult.set(executor.startSimulation(simulation, "strategy"));
            } catch (RuntimeException expected) {
                // The blocked run ends in the failure path once released — irrelevant here
            }
        });
        first.start();
        assertThat(executorEntered.await(15, TimeUnit.SECONDS)).isTrue();

        // Re-submit while the first call is still executing in this JVM
        boolean resubmitted = executor.startSimulation(simulation, "strategy");
        assertThat(resubmitted).isFalse();
        verify(simulationLockPort, times(1)).tryLock(any(), any(), any());

        releaseExecutor.countDown();
        first.join(15_000);

        // The in-flight slot must be released after the run ends: a fresh submit reaches the lease again
        try {
            executor.startSimulation(simulation, "strategy");
        } catch (RuntimeException expected) {
            // Same missing-strategy failure path as the first run — only the lease count matters here
        }
        verify(simulationLockPort, times(2)).tryLock(any(), any(), any());
    }

}
