package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.InvestmentAllocationRepository;
import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyPickerPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.command.ExecuteInvestmentPlanCommand;
import com.quorumtrading.application.usecase.contribution.ContributionOutcome;
import com.quorumtrading.application.usecase.contribution.RecordSimulationContributionUseCase;
import com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.AllocationAction;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code maxStrategiesActive} and {@code preferExistingHoldings} must actually constrain a tick.
 *
 * <p>Both were accepted, stored and echoed back by the API while nothing enforced them: the default
 * heuristic picker filters on ACTIVE status and ticker policy only, and {@code maxStrategiesActive}
 * appeared in main code solely inside the LLM picker's prompt, where it is advisory. A plan created
 * with a cap of 1 was observed running two live simulations on two strategies — and since the
 * heuristic picks the most-recently <em>updated</em> strategy, editing any strategy silently
 * redirected the next contribution into a brand-new simulation, without bound.</p>
 *
 * <p>The plan's own simulations are derived from its allocation rows, not from "every LIVE
 * simulation on this broker": a user's unrelated simulations must neither count against the cap
 * nor become top-up targets.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteInvestmentPlanService — picker preference enforcement")
class ExecuteInvestmentPlanPickerLimitsTest {

    @Mock private InvestmentPlanRepository planRepository;
    @Mock private InvestmentAllocationRepository allocationRepository;
    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private StrategyPickerPort strategyPicker;
    @Mock private CreateSimulationUseCase createSimulationUseCase;
    @Mock private RecordSimulationContributionUseCase recordContributionUseCase;
    @Mock private DispatchNotificationUseCase dispatchNotificationUseCase;

    private ExecuteInvestmentPlanService service;

    private UserId userId;
    private InvestmentPlanId planId;
    private BrokerAccountId brokerAccountId;
    private StrategyId pickedStrategyId;
    private StrategyId heldStrategyId;
    private SimulationId heldSimId;
    private Instant executionDate;

    @BeforeEach
    void setUp() {
        service = new ExecuteInvestmentPlanService(
                planRepository, allocationRepository, brokerAccountRepository, simulationRepository,
                strategyRepository, strategyPicker, createSimulationUseCase,
                recordContributionUseCase, dispatchNotificationUseCase);

        userId = UserId.generate();
        planId = InvestmentPlanId.generate();
        brokerAccountId = BrokerAccountId.generate();
        pickedStrategyId = StrategyId.generate();
        heldStrategyId = StrategyId.generate();
        heldSimId = new SimulationId(UUID.randomUUID());
        executionDate = Instant.parse("2026-06-01T06:00:00Z");

        // The service's same-day guard probes for an already-funded allocation before it funds.
        // Empty is the honest default here: these fixtures each describe a plan's first tick of
        // the day.
        lenient().when(allocationRepository.findFundedOnDay(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(allocationRepository.save(any(InvestmentAllocation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(planRepository.save(any(InvestmentPlan.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(strategyRepository.findById(any())).thenReturn(Optional.empty());
    }

    private InvestmentPlan planWith(Integer cap, boolean preferExisting) {
        InvestmentPlan plan = InvestmentPlan.create(
                planId, userId, brokerAccountId, BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("250.00")), 1, PickerPolicy.AI_PICK,
                new InvestmentPlanPreferences(null, List.of(), List.of(), cap, preferExisting),
                Instant.parse("2026-05-15T00:00:00Z"));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        return plan;
    }

    private void brokerHas(String available) {
        BrokerAccount account = org.mockito.Mockito.mock(BrokerAccount.class);
        lenient().when(account.getBrokerType()).thenReturn(BrokerType.ALPACA_PAPER);
        lenient().when(account.getAvailableBalance()).thenReturn(Money.usd(new BigDecimal(available)));
        when(brokerAccountRepository.findById(brokerAccountId)).thenReturn(Optional.of(account));
    }

    private void pickerChooses(StrategyId id) {
        when(strategyPicker.pick(any())).thenReturn(
                StrategyPickerPort.Outcome.chose(id, "picked", BigDecimal.ZERO));
    }

    /** A LIVE simulation this plan already funded, recorded on a prior allocation row. */
    private void planAlreadyFunded(SimulationId simId, StrategyId strategyId) {
        InvestmentAllocation prior = org.mockito.Mockito.mock(InvestmentAllocation.class);
        lenient().when(prior.getTargetSimulationId()).thenReturn(simId);
        when(allocationRepository.findByPlanId(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(prior));

        Simulation sim = org.mockito.Mockito.mock(Simulation.class);
        lenient().when(sim.getStatus()).thenReturn(SimulationStatus.LIVE);
        lenient().when(sim.getSimulationId()).thenReturn(simId);
        lenient().when(sim.getStrategyId()).thenReturn(strategyId);
        lenient().when(sim.getBrokerAccountId()).thenReturn(brokerAccountId);
        when(simulationRepository.findById(simId)).thenReturn(Optional.of(sim));
    }

    private void planHasFundedNothing() {
        when(allocationRepository.findByPlanId(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
    }

    private void topUpSucceeds() {
        Contribution contribution = org.mockito.Mockito.mock(Contribution.class);
        lenient().when(contribution.id()).thenReturn(ContributionId.generate());
        when(recordContributionUseCase.execute(any())).thenReturn(ContributionOutcome.created(contribution));
    }

    private ExecuteInvestmentPlanCommand command() {
        return new ExecuteInvestmentPlanCommand(planId, userId, executionDate, true);
    }

    private InvestmentAllocation savedAllocation() {
        ArgumentCaptor<InvestmentAllocation> captor = ArgumentCaptor.forClass(InvestmentAllocation.class);
        verify(allocationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("at the cap, a differently-picked strategy tops up instead of spawning a second simulation")
    void shouldTopUpRatherThanExceedTheCap() {
        planWith(1, false);
        brokerHas("5000.00");
        pickerChooses(pickedStrategyId);
        when(simulationRepository.findByStrategyId(pickedStrategyId)).thenReturn(List.of());
        planAlreadyFunded(heldSimId, heldStrategyId);
        topUpSucceeds();

        service.execute(command());

        verify(createSimulationUseCase, never()).execute(any());
        InvestmentAllocation row = savedAllocation();
        assertThat(row.getAction()).isEqualTo(AllocationAction.TOP_UP_EXISTING);
        assertThat(row.getTargetSimulationId()).isEqualTo(heldSimId);
        assertThat(row.getChosenStrategyId()).isEqualTo(heldStrategyId);
        assertThat(row.getPickerReasoning()).contains("maxStrategiesActive cap of 1");
    }

    @Test
    @DisplayName("preferExistingHoldings tops up an existing simulation even with no cap set")
    void shouldTopUpWhenPreferExistingHoldingsIsSet() {
        planWith(null, true);
        brokerHas("5000.00");
        pickerChooses(pickedStrategyId);
        when(simulationRepository.findByStrategyId(pickedStrategyId)).thenReturn(List.of());
        planAlreadyFunded(heldSimId, heldStrategyId);
        topUpSucceeds();

        service.execute(command());

        verify(createSimulationUseCase, never()).execute(any());
        assertThat(savedAllocation().getPickerReasoning()).contains("preferExistingHoldings");
    }

    @Test
    @DisplayName("below the cap and not preferring top-ups, a new simulation is still created")
    void shouldCreateNewSimulationWhenNeitherPreferenceBlocksIt() {
        planWith(3, false);
        brokerHas("5000.00");
        pickerChooses(pickedStrategyId);
        when(simulationRepository.findByStrategyId(pickedStrategyId)).thenReturn(List.of());
        planAlreadyFunded(heldSimId, heldStrategyId);
        when(createSimulationUseCase.execute(any())).thenReturn(new SimulationId(UUID.randomUUID()));

        service.execute(command());

        verify(createSimulationUseCase).execute(any());
        assertThat(savedAllocation().getAction()).isEqualTo(AllocationAction.NEW_SIMULATION);
    }

    @Test
    @DisplayName("the first ever tick creates a simulation even though preferExistingHoldings is on")
    void shouldCreateTheFirstSimulationWithNothingToTopUp() {
        planWith(1, true);
        brokerHas("5000.00");
        pickerChooses(pickedStrategyId);
        when(simulationRepository.findByStrategyId(pickedStrategyId)).thenReturn(List.of());
        planHasFundedNothing();
        when(createSimulationUseCase.execute(any())).thenReturn(new SimulationId(UUID.randomUUID()));

        service.execute(command());

        verify(createSimulationUseCase).execute(any());
        assertThat(savedAllocation().getAction()).isEqualTo(AllocationAction.NEW_SIMULATION);
    }

    @Test
    @DisplayName("a finished simulation frees a cap slot")
    void shouldNotCountCompletedSimulationsAgainstTheCap() {
        planWith(1, false);
        brokerHas("5000.00");
        pickerChooses(pickedStrategyId);
        when(simulationRepository.findByStrategyId(pickedStrategyId)).thenReturn(List.of());

        InvestmentAllocation prior = org.mockito.Mockito.mock(InvestmentAllocation.class);
        lenient().when(prior.getTargetSimulationId()).thenReturn(heldSimId);
        when(allocationRepository.findByPlanId(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(prior));
        Simulation finished = org.mockito.Mockito.mock(Simulation.class);
        lenient().when(finished.getStatus()).thenReturn(SimulationStatus.COMPLETED);
        when(simulationRepository.findById(heldSimId)).thenReturn(Optional.of(finished));

        when(createSimulationUseCase.execute(any())).thenReturn(new SimulationId(UUID.randomUUID()));

        service.execute(command());

        verify(createSimulationUseCase).execute(any());
    }
}
