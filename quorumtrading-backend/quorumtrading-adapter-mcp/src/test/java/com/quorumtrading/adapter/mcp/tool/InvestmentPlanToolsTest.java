package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.command.CreateInvestmentPlanCommand;
import com.quorumtrading.application.usecase.command.ExecuteInvestmentPlanCommand;
import com.quorumtrading.application.usecase.command.UpdateInvestmentPlanCommand;
import com.quorumtrading.application.usecase.investmentplan.CreateInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.ExecuteInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.GetInvestmentAllocationUseCase;
import com.quorumtrading.application.usecase.investmentplan.GetInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.ListInvestmentAllocationsUseCase;
import com.quorumtrading.application.usecase.investmentplan.ListInvestmentPlansUseCase;
import com.quorumtrading.application.usecase.investmentplan.PauseInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.ResumeInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.TerminateInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.UpdateInvestmentPlanUseCase;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.enums.AllocationAction;
import com.quorumtrading.model.enums.AllocationStatus;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.InvestmentPlanStatus;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferencesPatch;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The ten recurring-investment MCP tools, which had no test.
 *
 * <p>This is the only MCP surface that moves real money on a schedule, and it is where the
 * dog-fooding sweep found the largest cluster of defects — a paused plan executing anyway, a
 * strategy cap that did nothing, a $250 contribution reporting a $50,000 portfolio. That
 * concentration was not a coincidence: nothing here was tested.</p>
 *
 * <p>What these assert is the boundary's job — that the arguments an agent passes arrive at the
 * use case as written. A silently dropped {@code confirmLive}, {@code maxStrategiesActive} or
 * currency does not fail; it just quietly does something other than what was asked, which is the
 * failure mode this whole area has repeatedly had.</p>
 */
@QuarkusTest
@DisplayName("InvestmentPlanTools")
class InvestmentPlanToolsTest {

    @Inject InvestmentPlanTools tools;

    @InjectMock CreateInvestmentPlanUseCase createUseCase;
    @InjectMock ListInvestmentPlansUseCase listUseCase;
    @InjectMock GetInvestmentPlanUseCase getUseCase;
    @InjectMock PauseInvestmentPlanUseCase pauseUseCase;
    @InjectMock ResumeInvestmentPlanUseCase resumeUseCase;
    @InjectMock TerminateInvestmentPlanUseCase terminateUseCase;
    @InjectMock ExecuteInvestmentPlanUseCase executeUseCase;
    @InjectMock ListInvestmentAllocationsUseCase listAllocationsUseCase;
    @InjectMock GetInvestmentAllocationUseCase getAllocationUseCase;
    @InjectMock UpdateInvestmentPlanUseCase updateUseCase;
    @InjectMock McpUserContext mcpUserContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UserId USER_ID = UserId.generate();
    private static final BrokerAccountId BROKER_ID = BrokerAccountId.generate();
    private static final InvestmentPlanId PLAN_ID = InvestmentPlanId.generate();

    @BeforeEach
    void setUp() {
        when(mcpUserContext.getUserId()).thenReturn(USER_ID);
    }

    private InvestmentPlan plan() {
        return InvestmentPlan.create(
                PLAN_ID, USER_ID, BROKER_ID, BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("250.00")), 1, PickerPolicy.AI_PICK,
                InvestmentPlanPreferences.EMPTY, Instant.parse("2026-05-15T00:00:00Z"));
    }

    private InvestmentAllocation allocation(AllocationStatus status, String failureReason) {
        return InvestmentAllocation.create(
                InvestmentAllocationId.generate(), PLAN_ID, USER_ID,
                Instant.parse("2026-06-01T06:00:00Z"), status,
                Money.usd(new BigDecimal("250.00")),
                status == AllocationStatus.EXECUTED ? Money.usd(new BigDecimal("250.00")) : null,
                status == AllocationStatus.EXECUTED ? StrategyId.generate() : null,
                "picker said so", BigDecimal.ZERO,
                status == AllocationStatus.EXECUTED ? AllocationAction.NEW_SIMULATION : null,
                // An EXECUTED row must name the simulation it funded — the aggregate enforces it,
                // because an execution nobody can trace to a simulation is not an audit trail.
                status == AllocationStatus.EXECUTED ? new SimulationId(UUID.randomUUID()) : null,
                null, List.of(), failureReason, Instant.parse("2026-06-01T06:00:05Z"));
    }

    private JsonNode json(ToolResponse response) throws Exception {
        assertThat(response.isError()).isFalse();
        return objectMapper.readTree(response.firstContent().asText().text());
    }

    private ToolResponse createWith(Optional<Boolean> confirmLive, Optional<Integer> maxStrategies,
                                    Optional<Boolean> preferExisting, Optional<String> currency) {
        return tools.createInvestmentPlan(
                BROKER_ID.value().toString(), "FIXED_AMOUNT", Optional.of("250.00"), currency,
                1, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                maxStrategies, preferExisting, confirmLive);
    }

    private CreateInvestmentPlanCommand capturedCreate() {
        ArgumentCaptor<CreateInvestmentPlanCommand> captor =
                ArgumentCaptor.forClass(CreateInvestmentPlanCommand.class);
        verify(createUseCase).execute(captor.capture());
        return captor.getValue();
    }

    // ---- create ----

    @Test
    @DisplayName("create passes the amount, currency and schedule through as written")
    void shouldCreateAPlan() throws Exception {
        when(createUseCase.execute(any(CreateInvestmentPlanCommand.class))).thenReturn(plan());

        JsonNode result = json(createWith(Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of("USD")));

        assertThat(result.get("id").asText()).isEqualTo(PLAN_ID.value().toString());
        CreateInvestmentPlanCommand command = capturedCreate();
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.brokerAccountId()).isEqualTo(BROKER_ID);
        assertThat(command.budgetMode()).isEqualTo(BudgetMode.FIXED_AMOUNT);
        assertThat(command.amount().amount()).isEqualByComparingTo("250.00");
        assertThat(command.dayOfMonth()).isEqualTo(1);
    }

    @Test
    @DisplayName("confirmLive reaches the use case — it is the only guard on real-money plans")
    void shouldPassConfirmLiveThrough() {
        when(createUseCase.execute(any(CreateInvestmentPlanCommand.class))).thenReturn(plan());

        createWith(Optional.of(true), Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(capturedCreate().confirmLive()).isTrue();
    }

    @Test
    @DisplayName("omitting confirmLive defaults to false, never to consent")
    void shouldDefaultConfirmLiveToFalse() {
        when(createUseCase.execute(any(CreateInvestmentPlanCommand.class))).thenReturn(plan());

        createWith(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(capturedCreate().confirmLive())
                .as("an absent confirmation must never be read as consent to trade real money")
                .isFalse();
    }

    @Test
    @DisplayName("the picker preferences reach the use case rather than being dropped")
    void shouldPassPickerPreferencesThrough() {
        when(createUseCase.execute(any(CreateInvestmentPlanCommand.class))).thenReturn(plan());

        tools.createInvestmentPlan(
                BROKER_ID.value().toString(), "FIXED_AMOUNT", Optional.of("250.00"), Optional.empty(),
                15, Optional.empty(), Optional.of("conservative"),
                Optional.of("AAPL,MSFT"), Optional.of("GME"),
                Optional.of(2), Optional.of(false), Optional.empty());

        InvestmentPlanPreferences prefs = capturedCreate().preferences();
        assertThat(prefs.riskTolerance()).isEqualTo("conservative");
        assertThat(prefs.allowedTickers()).containsExactly("AAPL", "MSFT");
        assertThat(prefs.blockedTickers()).containsExactly("GME");
        assertThat(prefs.maxStrategiesActive()).isEqualTo(2);
        assertThat(prefs.preferExistingHoldings()).isFalse();
    }

    @Test
    @DisplayName("preferExistingHoldings defaults to true when omitted")
    void shouldDefaultPreferExistingHoldings() {
        when(createUseCase.execute(any(CreateInvestmentPlanCommand.class))).thenReturn(plan());

        createWith(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(capturedCreate().preferences().preferExistingHoldings()).isTrue();
    }

    @Test
    @DisplayName("an unknown budgetMode is an error, not a silent default")
    void shouldRejectUnknownBudgetMode() {
        ToolResponse response = tools.createInvestmentPlan(
                BROKER_ID.value().toString(), "WHATEVER", Optional.of("250.00"), Optional.empty(),
                1, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(response.isError()).isTrue();
        verify(createUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("a malformed broker account id is an error, not a call")
    void shouldRejectMalformedBrokerAccountId() {
        ToolResponse response = tools.createInvestmentPlan(
                "not-a-uuid", "FIXED_AMOUNT", Optional.of("250.00"), Optional.empty(),
                1, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(response.isError()).isTrue();
        verify(createUseCase, never()).execute(any());
    }

    // ---- read ----

    @Test
    @DisplayName("list returns the user's plans with a count")
    void shouldListPlans() throws Exception {
        InvestmentPlan existing = plan();
        when(listUseCase.listForUser(any(UserId.class), any())).thenReturn(List.of(existing));

        JsonNode result = json(tools.listInvestmentPlans(Optional.empty(), Optional.empty()));

        assertThat(result.get("count").asInt()).isEqualTo(1);
        assertThat(result.get("plans").get(0).get("id").asText()).isEqualTo(PLAN_ID.value().toString());
    }

    @Test
    @DisplayName("a status filter is parsed and passed, not ignored")
    void shouldPassStatusFilterThrough() {
        when(listUseCase.listForUser(any(UserId.class), any())).thenReturn(List.of());

        tools.listInvestmentPlans(Optional.of("PAUSED"), Optional.empty());

        verify(listUseCase).listForUser(USER_ID, Optional.of(InvestmentPlanStatus.PAUSED));
    }

    @Test
    @DisplayName("an unknown status filter errors rather than listing everything")
    void shouldRejectUnknownStatusFilter() {
        ToolResponse response = tools.listInvestmentPlans(Optional.of("SNOOZED"), Optional.empty());

        assertThat(response.isError()).isTrue();
        verify(listUseCase, never()).listForUser(any(), any());
    }

    @Test
    @DisplayName("get returns one plan, scoped to its owner")
    void shouldGetOnePlan() throws Exception {
        when(getUseCase.getOwnedBy(any(InvestmentPlanId.class), any(UserId.class))).thenReturn(plan());

        JsonNode result = json(tools.getInvestmentPlan(PLAN_ID.value().toString()));

        assertThat(result.get("id").asText()).isEqualTo(PLAN_ID.value().toString());
        assertThat(result.get("uiUrl").asText())
            .isEqualTo("http://localhost:3000/investment-plans/" + PLAN_ID.value());
        verify(getUseCase).getOwnedBy(PLAN_ID, USER_ID);
    }

    @Test
    @DisplayName("another user's plan surfaces as an error, not as data")
    void shouldNotReturnAnotherUsersPlan() {
        when(getUseCase.getOwnedBy(any(InvestmentPlanId.class), any(UserId.class)))
                .thenThrow(new SecurityException("not yours"));

        assertThat(tools.getInvestmentPlan(PLAN_ID.value().toString()).isError()).isTrue();
    }

    // ---- lifecycle ----

    @Test
    @DisplayName("pause and resume are scoped to the calling user")
    void shouldPauseAndResumeAsTheOwner() throws Exception {
        when(pauseUseCase.pause(any(InvestmentPlanId.class), any(UserId.class))).thenReturn(plan());
        when(resumeUseCase.resume(any(InvestmentPlanId.class), any(UserId.class))).thenReturn(plan());

        json(tools.pauseInvestmentPlan(PLAN_ID.value().toString()));
        json(tools.resumeInvestmentPlan(PLAN_ID.value().toString()));

        verify(pauseUseCase).pause(PLAN_ID, USER_ID);
        verify(resumeUseCase).resume(PLAN_ID, USER_ID);
    }

    @Test
    @DisplayName("delete terminates and says so")
    void shouldTerminateAPlan() throws Exception {
        JsonNode result = json(tools.deleteInvestmentPlan(PLAN_ID.value().toString()));

        assertThat(result.get("status").asText()).isEqualTo("terminated");
        verify(terminateUseCase).terminate(PLAN_ID, USER_ID);
    }

    // ---- execute ----

    @Test
    @DisplayName("a manual tick returns the allocation it wrote")
    void shouldExecuteNow() throws Exception {
        InvestmentAllocation executed = allocation(AllocationStatus.EXECUTED, null);
        when(executeUseCase.execute(any(ExecuteInvestmentPlanCommand.class))).thenReturn(executed);

        JsonNode result = json(tools.executeInvestmentPlanNow(PLAN_ID.value().toString()));

        assertThat(result.get("status").asText()).isEqualTo("EXECUTED");

        ArgumentCaptor<ExecuteInvestmentPlanCommand> captor =
                ArgumentCaptor.forClass(ExecuteInvestmentPlanCommand.class);
        verify(executeUseCase).execute(captor.capture());
        assertThat(captor.getValue().planId()).isEqualTo(PLAN_ID);
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("a refused tick surfaces as an error — a paused plan must not read as success")
    void shouldSurfaceARefusedExecution() {
        when(executeUseCase.execute(any(ExecuteInvestmentPlanCommand.class)))
                .thenThrow(new IllegalStateException("Investment plan " + PLAN_ID.value()
                        + " is PAUSED and cannot be executed. Resume it first."));

        ToolResponse response = tools.executeInvestmentPlanNow(PLAN_ID.value().toString());

        assertThat(response.isError()).isTrue();
        assertThat(response.firstContent().asText().text()).contains("PAUSED");
    }

    // ---- allocations ----

    @Test
    @DisplayName("allocations list for a plan, newest first, with the limit passed through")
    void shouldListAllocations() throws Exception {
        InvestmentAllocation executed = allocation(AllocationStatus.EXECUTED, null);
        when(listAllocationsUseCase.forPlan(any(InvestmentPlanId.class), any(UserId.class), anyInt()))
                .thenReturn(List.of(executed));

        JsonNode result = json(tools.listInvestmentAllocations(PLAN_ID.value().toString(), Optional.of(10)));

        assertThat(result.get("count").asInt()).isEqualTo(1);
        verify(listAllocationsUseCase).forPlan(PLAN_ID, USER_ID, 10);
    }

    @Test
    @DisplayName("omitting the limit uses the documented default of 50")
    void shouldDefaultTheAllocationLimit() {
        when(listAllocationsUseCase.forPlan(any(InvestmentPlanId.class), any(UserId.class), anyInt()))
                .thenReturn(List.of());

        tools.listInvestmentAllocations(PLAN_ID.value().toString(), Optional.empty());

        verify(listAllocationsUseCase).forPlan(PLAN_ID, USER_ID, 50);
    }

    @Test
    @DisplayName("a skipped allocation keeps its reason — it is the only trace of a missed contribution")
    void shouldExposeTheSkipReason() throws Exception {
        InvestmentAllocation skipped = allocation(AllocationStatus.SKIPPED_INSUFFICIENT_FUNDS,
                "Broker available balance $12.00 is less than the plan amount $250.00");
        when(getAllocationUseCase.getOwnedBy(any(InvestmentAllocationId.class), any(UserId.class)))
                .thenReturn(skipped);

        JsonNode result = json(tools.getInvestmentAllocation(
                skipped.getId().value().toString()));

        assertThat(result.get("status").asText()).isEqualTo("SKIPPED_INSUFFICIENT_FUNDS");
        assertThat(result.toString()).contains("less than the plan amount");
    }

    @Test
    @DisplayName("a malformed allocation id errors rather than reaching the use case")
    void shouldRejectAMalformedAllocationId() {
        assertThat(tools.getInvestmentAllocation("not-a-uuid").isError()).isTrue();
        verify(getAllocationUseCase, never()).getOwnedBy(any(), any());
    }

    // ---- update: partial means partial ----

    private ToolResponse updateWith(Optional<String> riskTolerance, Optional<String> allowedTickers,
                                    Optional<String> blockedTickers, Optional<Integer> maxStrategies,
                                    Optional<Boolean> preferExisting, Optional<String> amount) {
        when(updateUseCase.execute(any(UpdateInvestmentPlanCommand.class))).thenReturn(plan());
        return tools.updateInvestmentPlan(
                PLAN_ID.value().toString(), Optional.empty(), Optional.empty(), amount,
                Optional.empty(), Optional.empty(), riskTolerance, allowedTickers, blockedTickers,
                maxStrategies, preferExisting, Optional.empty());
    }

    private UpdateInvestmentPlanCommand capturedUpdate() {
        ArgumentCaptor<UpdateInvestmentPlanCommand> captor =
                ArgumentCaptor.forClass(UpdateInvestmentPlanCommand.class);
        verify(updateUseCase).execute(captor.capture());
        return captor.getValue();
    }

    /**
     * The tool promises "pass only the fields you want to change". It used to rebuild the whole
     * preferences object from the arguments it received, so an amount edit that also set a
     * block-list erased the plan's allow-list, its risk tolerance and its strategy cap — widening
     * the picker from one ticker to the user's entire pool, with money.
     */
    @Test
    @DisplayName("an argument the caller omits arrives at the use case as absent, not as empty")
    void shouldNotInventPreferenceValuesTheCallerDidNotSend() {
        updateWith(Optional.empty(), Optional.empty(), Optional.of("DOGE"),
                Optional.empty(), Optional.empty(), Optional.of("300.00"));

        InvestmentPlanPreferencesPatch patch = capturedUpdate().preferences().orElseThrow();

        assertThat(patch.blockedTickers()).contains(List.of("DOGE"));
        assertThat(patch.allowedTickers()).as("omitted — must not arrive as an empty list").isEmpty();
        assertThat(patch.riskTolerance()).as("omitted — must not arrive as null").isEmpty();
        assertThat(patch.maxStrategiesActive()).as("omitted — must not arrive as null").isEmpty();
        assertThat(patch.preferExistingHoldings()).as("omitted — must not arrive as a default").isEmpty();
    }

    @Test
    @DisplayName("an update naming no preference carries no preference patch at all")
    void shouldSendNoPatchWhenNoPreferenceIsNamed() {
        updateWith(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of("275.00"));

        assertThat(capturedUpdate().preferences()).isEmpty();
    }

    @Test
    @DisplayName("every preference the caller does send is passed through as written")
    void shouldPassThroughEveryPreferenceTheCallerSends() {
        updateWith(Optional.of("aggressive"), Optional.of("BTC, ETH"), Optional.of("GME"),
                Optional.of(2), Optional.of(false), Optional.empty());

        InvestmentPlanPreferencesPatch patch = capturedUpdate().preferences().orElseThrow();

        assertThat(patch.riskTolerance()).contains("aggressive");
        assertThat(patch.allowedTickers()).contains(List.of("BTC", "ETH"));
        assertThat(patch.blockedTickers()).contains(List.of("GME"));
        assertThat(patch.maxStrategiesActive()).contains(2);
        assertThat(patch.preferExistingHoldings()).contains(false);
    }

    @Test
    @DisplayName("an empty string clears a ticker list, as the argument description promises")
    void shouldTreatAnEmptyStringAsAClear() {
        updateWith(Optional.empty(), Optional.of(""), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());

        // Present-but-empty is a clear; absent would have been "leave it alone".
        assertThat(capturedUpdate().preferences().orElseThrow().allowedTickers())
                .contains(List.of());
    }
}
