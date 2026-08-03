package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.command.CreateInvestmentPlanCommand;
import com.quorumtrading.application.usecase.command.ExecuteInvestmentPlanCommand;
import com.quorumtrading.application.usecase.command.UpdateInvestmentPlanCommand;
import com.quorumtrading.application.usecase.investmentplan.CreateInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.ExecuteInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.GetInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.ListInvestmentPlansUseCase;
import com.quorumtrading.application.usecase.investmentplan.PauseInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.ResumeInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.TerminateInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.UpdateInvestmentPlanUseCase;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.AllocationAction;
import com.quorumtrading.model.enums.AllocationStatus;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.InvestmentPlanStatus;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferencesPatch;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static com.quorumtrading.adapter.rest.resource.TestConstants.TEST_USER_ID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InvestmentPlanResource}.
 *
 * <p>Uses mocked use cases (provided by the module's {@code MockProducer}) to test the
 * REST layer in isolation, following the {@code SimulationResourceTest} pattern:
 * authentication via the {@code dev_session} cookie handled by
 * {@code TestDevSessionAuthMechanism} plus a mocked {@code UserRepository}.</p>
 *
 * <p>Includes the go-live checklist guardrail: creating a plan against an
 * {@code ALPACA_LIVE} broker without {@code confirmLive=true} must be rejected with a
 * 4xx and succeed once the flag is passed. The guardrail itself lives in
 * {@code InvestmentPlanService} (application layer, covered there); here the mocked
 * use case reproduces its contract — throw {@code IllegalStateException} when
 * {@code confirmLive} is false — so the test proves the resource (a) forwards the
 * request flag into the command and (b) maps the rejection to a 400 problem body.</p>
 */
@QuarkusTest
class InvestmentPlanResourceTest {

    private static final String TEST_EMAIL = "test@quorumtrading.local";
    private static final String BASE = "/api/v1/investment-plans";

    @InjectMock
    CreateInvestmentPlanUseCase createUseCase;

    @InjectMock
    UpdateInvestmentPlanUseCase updateUseCase;

    @InjectMock
    PauseInvestmentPlanUseCase pauseUseCase;

    @InjectMock
    ResumeInvestmentPlanUseCase resumeUseCase;

    @InjectMock
    TerminateInvestmentPlanUseCase terminateUseCase;

    @InjectMock
    GetInvestmentPlanUseCase getUseCase;

    @InjectMock
    ListInvestmentPlansUseCase listUseCase;

    @InjectMock
    ExecuteInvestmentPlanUseCase executeUseCase;

    @InjectMock
    UserRepository userRepository;

    @InjectMock
    ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    private InvestmentPlanId planId;
    private BrokerAccountId brokerAccountId;

    @BeforeEach
    void setUp() {
        User testUser = User.reconstitute(
                TEST_USER_ID, TEST_EMAIL, "Test User", null,
                com.quorumtrading.model.enums.UserRole.USER,
                Instant.now(), Instant.now(), List.of(), true, false);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(resolveCurrentUserUseCase.execute(TEST_EMAIL)).thenReturn(TEST_USER_ID);

        planId = InvestmentPlanId.generate();
        brokerAccountId = BrokerAccountId.generate();
    }

    // ----- Fixtures -----

    private InvestmentPlan plan() {
        return InvestmentPlan.reconstitute(
                planId, TEST_USER_ID, brokerAccountId,
                BudgetMode.FIXED_AMOUNT, Money.usd(new BigDecimal("200.00")),
                1, PickerPolicy.AI_PICK, InvestmentPlanPreferences.EMPTY,
                InvestmentPlanStatus.ACTIVE,
                Instant.parse("2026-08-01T00:00:00Z"), null,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z"));
    }

    private InvestmentPlan pausedPlan() {
        return InvestmentPlan.reconstitute(
                planId, TEST_USER_ID, brokerAccountId,
                BudgetMode.FIXED_AMOUNT, Money.usd(new BigDecimal("200.00")),
                1, PickerPolicy.AI_PICK, InvestmentPlanPreferences.EMPTY,
                InvestmentPlanStatus.PAUSED,
                null, null,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z"));
    }

    private String createBody(boolean includeConfirmLive) {
        return "{"
                + "\"brokerAccountId\": \"" + brokerAccountId.value() + "\","
                + "\"budgetMode\": \"FIXED_AMOUNT\","
                + "\"amount\": 200.00,"
                + "\"currency\": \"USD\","
                + "\"dayOfMonth\": 1,"
                + "\"pickerPolicy\": \"AI_PICK\""
                + (includeConfirmLive ? ",\"confirmLive\": true" : "")
                + "}";
    }

    // ----- Create -----

    @Test
    void create_shouldReturn201WithPlanBody() {
        when(createUseCase.execute(any(CreateInvestmentPlanCommand.class))).thenReturn(plan());

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body(createBody(false))
        .when()
            .post(BASE)
        .then()
            .statusCode(201)
            .header("Location", containsString(planId.value().toString()))
            .body("id", equalTo(planId.value().toString()))
            .body("userId", equalTo(TEST_USER_ID.value().toString()))
            .body("brokerAccountId", equalTo(brokerAccountId.value().toString()))
            .body("budgetMode", equalTo("FIXED_AMOUNT"))
            .body("amount", is(200.00f))
            .body("currency", equalTo("USD"))
            .body("dayOfMonth", is(1))
            .body("pickerPolicy", equalTo("AI_PICK"))
            .body("status", equalTo("ACTIVE"))
            .body("nextExecutionAt", notNullValue());

        // The command must carry the authenticated user and the payload fields verbatim.
        ArgumentCaptor<CreateInvestmentPlanCommand> captor =
                ArgumentCaptor.forClass(CreateInvestmentPlanCommand.class);
        verify(createUseCase).execute(captor.capture());
        CreateInvestmentPlanCommand cmd = captor.getValue();
        assertEquals(TEST_USER_ID, cmd.userId());
        assertEquals(brokerAccountId, cmd.brokerAccountId());
        assertEquals(BudgetMode.FIXED_AMOUNT, cmd.budgetMode());
        assertEquals(0, cmd.amount().amount().compareTo(new BigDecimal("200.00")));
        assertEquals(1, cmd.dayOfMonth());
        assertEquals(PickerPolicy.AI_PICK, cmd.pickerPolicy());
        assertFalse(cmd.confirmLive(), "confirmLive defaults to false when the payload omits it");
    }

    /**
     * {@code nextExecutionAt} is a promise about the user's money, and on a default install it is
     * one nothing keeps: the DCA scheduler is disabled, so an ACTIVE plan dated for next month
     * never fires and the user finds out weeks later that nothing happened. The date may stay —
     * it is the schedule the plan would follow — but the response has to say whether anything is
     * going to act on it.
     */
    @Test
    void create_shouldSayWhetherTheScheduleWillActuallyFire() {
        when(createUseCase.execute(any(CreateInvestmentPlanCommand.class))).thenReturn(plan());

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body(createBody(false))
        .when()
            .post(BASE)
        .then()
            .statusCode(201)
            .body("nextExecutionAt", notNullValue())
            .body("automaticExecutionEnabled", equalTo(false));
    }

    @Test
    void get_shouldSayWhetherTheScheduleWillActuallyFire() {
        when(getUseCase.getOwnedBy(planId, TEST_USER_ID)).thenReturn(plan());

        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .get(BASE + "/" + planId.value())
        .then()
            .statusCode(200)
            .body("automaticExecutionEnabled", equalTo(false));
    }

    @Test
    void create_shouldReturn400WhenRequiredFieldsMissing() {
        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body("{\"amount\": 200.00}")
        .when()
            .post(BASE)
        .then()
            .statusCode(400)
            .body("detail", containsString("brokerAccountId"));
    }

    @Test
    void create_shouldReturn400WhenBudgetModeUnknown() {
        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body("{"
                + "\"brokerAccountId\": \"" + brokerAccountId.value() + "\","
                + "\"budgetMode\": \"YOLO\","
                + "\"dayOfMonth\": 1,"
                + "\"pickerPolicy\": \"AI_PICK\""
                + "}")
        .when()
            .post(BASE)
        .then()
            .statusCode(400)
            .body("detail", containsString("budgetMode"));
    }

    @Test
    void create_shouldReturn404WhenBrokerAccountUnknown() {
        when(createUseCase.execute(any(CreateInvestmentPlanCommand.class)))
                .thenThrow(new NoSuchElementException("Broker account not found: " + brokerAccountId));

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body(createBody(false))
        .when()
            .post(BASE)
        .then()
            .statusCode(404)
            .body("detail", containsString("Broker account not found"));
    }

    @Test
    void create_shouldReturn401WhenNotAuthenticated() {
        given()
            .contentType(ContentType.JSON)
            .body(createBody(false))
        .when()
            .post(BASE)
        .then()
            .statusCode(401);
    }

    // ----- Go-live checklist guardrail: ALPACA_LIVE requires confirmLive=true -----

    /**
     * Reproduce the application-layer guardrail contract on the mock: reject any
     * command that did not acknowledge live trading with {@code confirmLive=true}.
     */
    private void stubLiveBrokerGuardrail() {
        when(createUseCase.execute(any(CreateInvestmentPlanCommand.class))).thenAnswer(inv -> {
            CreateInvestmentPlanCommand cmd = inv.getArgument(0);
            if (!cmd.confirmLive()) {
                throw new IllegalStateException(
                        "Broker is ALPACA_LIVE; pass confirmLive=true to acknowledge that this plan will trade real money.");
            }
            return plan();
        });
    }

    @Test
    void create_shouldRejectAlpacaLiveBrokerWithoutConfirmLive() {
        stubLiveBrokerGuardrail();

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body(createBody(false)) // no confirmLive → JSON default false
        .when()
            .post(BASE)
        .then()
            .statusCode(400)
            .body("detail", containsString("confirmLive=true"))
            .body("detail", containsString("ALPACA_LIVE"));
    }

    @Test
    void create_shouldAcceptAlpacaLiveBrokerWithConfirmLive() {
        stubLiveBrokerGuardrail();

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body(createBody(true)) // confirmLive: true
        .when()
            .post(BASE)
        .then()
            .statusCode(201)
            .body("id", equalTo(planId.value().toString()));

        // And the flag must have arrived on the command as true (not defaulted).
        ArgumentCaptor<CreateInvestmentPlanCommand> captor =
                ArgumentCaptor.forClass(CreateInvestmentPlanCommand.class);
        verify(createUseCase).execute(captor.capture());
        assertTrue(captor.getValue().confirmLive(),
                "confirmLive=true from the payload must be forwarded to the use case");
    }

    // ----- Get / List -----

    @Test
    void get_shouldReturnPlan() {
        when(getUseCase.getOwnedBy(planId, TEST_USER_ID)).thenReturn(plan());

        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .get(BASE + "/" + planId.value())
        .then()
            .statusCode(200)
            .body("id", equalTo(planId.value().toString()))
            .body("status", equalTo("ACTIVE"));
    }

    @Test
    void get_shouldReturn404WhenNotFound() {
        when(getUseCase.getOwnedBy(any(InvestmentPlanId.class), eq(TEST_USER_ID)))
                .thenThrow(new NoSuchElementException("Investment plan not found"));

        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .get(BASE + "/" + UUID.randomUUID())
        .then()
            .statusCode(404)
            .body("detail", containsString("Investment plan not found"));
    }

    @Test
    void get_shouldReturn404WhenOwnedByAnotherUser() {
        // Ownership violations surface as 404, not 403 — plan existence is not leaked.
        when(getUseCase.getOwnedBy(planId, TEST_USER_ID))
                .thenThrow(new SecurityException("Investment plan does not belong to user"));

        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .get(BASE + "/" + planId.value())
        .then()
            .statusCode(404);
    }

    @Test
    void get_shouldReturn400OnMalformedId() {
        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .get(BASE + "/not-a-uuid")
        .then()
            .statusCode(400)
            .body("detail", containsString("Invalid plan ID format"));
    }

    @Test
    void list_shouldReturnPlansForCurrentUser() {
        when(listUseCase.listForUser(TEST_USER_ID, Optional.empty())).thenReturn(List.of(plan()));

        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .get(BASE)
        .then()
            .statusCode(200)
            .body("$.size()", is(1))
            .body("[0].id", equalTo(planId.value().toString()));
    }

    @Test
    void list_shouldForwardStatusFilter() {
        when(listUseCase.listForUser(TEST_USER_ID, Optional.of(InvestmentPlanStatus.PAUSED)))
                .thenReturn(List.of());

        given()
            .cookie("dev_session", TEST_EMAIL)
            .queryParam("status", "PAUSED")
        .when()
            .get(BASE)
        .then()
            .statusCode(200)
            .body("$.size()", is(0));

        verify(listUseCase).listForUser(TEST_USER_ID, Optional.of(InvestmentPlanStatus.PAUSED));
    }

    // ----- Update -----

    @Test
    void update_shouldForwardPartialChangesAndReturnUpdatedPlan() {
        when(updateUseCase.execute(any(UpdateInvestmentPlanCommand.class))).thenReturn(plan());

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body("{\"amount\": 350.00, \"dayOfMonth\": 15}")
        .when()
            .put(BASE + "/" + planId.value())
        .then()
            .statusCode(200)
            .body("id", equalTo(planId.value().toString()));

        ArgumentCaptor<UpdateInvestmentPlanCommand> captor =
                ArgumentCaptor.forClass(UpdateInvestmentPlanCommand.class);
        verify(updateUseCase).execute(captor.capture());
        UpdateInvestmentPlanCommand cmd = captor.getValue();
        assertEquals(planId, cmd.planId());
        assertEquals(TEST_USER_ID, cmd.userId());
        assertEquals(0, cmd.amount().orElseThrow().amount().compareTo(new BigDecimal("350.00")));
        assertEquals(15, cmd.dayOfMonth().orElseThrow());
        assertTrue(cmd.budgetMode().isEmpty(), "untouched fields stay empty in a partial update");
        assertTrue(cmd.brokerAccountId().isEmpty());
    }

    /**
     * The endpoint's schema says "pass only the fields you want to change". It used to rebuild the
     * whole preferences object from the payload, so a JSON field that was simply absent — which is
     * indistinguishable from null once a record is deserialised — overwrote the stored value. An
     * edit that only meant to add a blocked ticker therefore erased the plan's allow-list, its risk
     * tolerance and its strategy cap, widening the picker to the user's entire pool.
     */
    @Test
    void update_shouldNotClearPreferencesThePayloadDoesNotMention() {
        when(updateUseCase.execute(any(UpdateInvestmentPlanCommand.class))).thenReturn(plan());

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body("{\"preferences\": {\"blockedTickers\": [\"DOGE\"]}}")
        .when()
            .put(BASE + "/" + planId.value())
        .then()
            .statusCode(200);

        ArgumentCaptor<UpdateInvestmentPlanCommand> captor =
                ArgumentCaptor.forClass(UpdateInvestmentPlanCommand.class);
        verify(updateUseCase).execute(captor.capture());
        InvestmentPlanPreferencesPatch patch = captor.getValue().preferences().orElseThrow();

        assertEquals(List.of("DOGE"), patch.blockedTickers().orElseThrow());
        assertTrue(patch.allowedTickers().isEmpty(), "an unmentioned allow-list must not be cleared");
        assertTrue(patch.riskTolerance().isEmpty(), "an unmentioned risk tolerance must not be cleared");
        assertTrue(patch.maxStrategiesActive().isEmpty(), "an unmentioned cap must not be cleared");
        assertTrue(patch.preferExistingHoldings().isEmpty(),
                "an unmentioned flag must not be reset to a default");
    }

    @Test
    void update_shouldStillClearAListSentExplicitlyEmpty() {
        when(updateUseCase.execute(any(UpdateInvestmentPlanCommand.class))).thenReturn(plan());

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body("{\"preferences\": {\"allowedTickers\": []}}")
        .when()
            .put(BASE + "/" + planId.value())
        .then()
            .statusCode(200);

        ArgumentCaptor<UpdateInvestmentPlanCommand> captor =
                ArgumentCaptor.forClass(UpdateInvestmentPlanCommand.class);
        verify(updateUseCase).execute(captor.capture());

        // Present-but-empty is a clear; absent would have meant "leave it alone".
        assertEquals(List.of(),
                captor.getValue().preferences().orElseThrow().allowedTickers().orElseThrow());
    }

    @Test
    void update_shouldReturn400WhenDomainRejectsChange() {
        when(updateUseCase.execute(any(UpdateInvestmentPlanCommand.class)))
                .thenThrow(new IllegalArgumentException("dayOfMonth must be between 1 and 28 (got 31)"));

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body("{\"dayOfMonth\": 31}")
        .when()
            .put(BASE + "/" + planId.value())
        .then()
            .statusCode(400)
            .body("detail", containsString("dayOfMonth"));
    }

    // ----- Pause / Resume / Terminate -----

    @Test
    void pause_shouldReturnPausedPlan() {
        when(pauseUseCase.pause(planId, TEST_USER_ID)).thenReturn(pausedPlan());

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .post(BASE + "/" + planId.value() + "/pause")
        .then()
            .statusCode(200)
            .body("status", equalTo("PAUSED"));

        verify(pauseUseCase).pause(planId, TEST_USER_ID);
    }

    @Test
    void resume_shouldReturnActivePlan() {
        when(resumeUseCase.resume(planId, TEST_USER_ID)).thenReturn(plan());

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .post(BASE + "/" + planId.value() + "/resume")
        .then()
            .statusCode(200)
            .body("status", equalTo("ACTIVE"));

        verify(resumeUseCase).resume(planId, TEST_USER_ID);
    }

    @Test
    void resume_shouldReturn400WhenPlanTerminated() {
        when(resumeUseCase.resume(planId, TEST_USER_ID))
                .thenThrow(new IllegalStateException("Investment plan is TERMINATED and cannot be modified"));

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .post(BASE + "/" + planId.value() + "/resume")
        .then()
            .statusCode(400)
            .body("detail", containsString("TERMINATED"));
    }

    @Test
    void terminate_shouldReturn204() {
        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .delete(BASE + "/" + planId.value())
        .then()
            .statusCode(204);

        verify(terminateUseCase).terminate(planId, TEST_USER_ID);
    }

    // ----- Manual execute -----

    @Test
    void execute_shouldReturnAllocationAndAdvanceSchedule() {
        StrategyId strategyId = StrategyId.generate();
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        ContributionId contributionId = ContributionId.generate();
        Instant now = Instant.parse("2026-07-06T06:00:00Z");
        InvestmentAllocation allocation = InvestmentAllocation.create(
                InvestmentAllocationId.generate(), planId, TEST_USER_ID, now,
                AllocationStatus.EXECUTED,
                Money.usd(new BigDecimal("200.00")), Money.usd(new BigDecimal("200.00")),
                strategyId, "most recently updated active strategy", BigDecimal.ZERO,
                AllocationAction.TOP_UP_EXISTING, simulationId, contributionId,
                List.of(), null, now);
        when(executeUseCase.execute(any(ExecuteInvestmentPlanCommand.class))).thenReturn(allocation);

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .post(BASE + "/" + planId.value() + "/execute")
        .then()
            .statusCode(200)
            .body("planId", equalTo(planId.value().toString()))
            .body("status", equalTo("EXECUTED"))
            .body("actualAmount", is(200.00f))
            .body("currency", equalTo("USD"))
            .body("action", equalTo("TOP_UP_EXISTING"))
            .body("targetSimulationId", equalTo(simulationId.value().toString()))
            .body("contributionId", equalTo(contributionId.value().toString()));

        // Manual execute is a real tick: ownership-scoped and schedule-advancing.
        ArgumentCaptor<ExecuteInvestmentPlanCommand> captor =
                ArgumentCaptor.forClass(ExecuteInvestmentPlanCommand.class);
        verify(executeUseCase).execute(captor.capture());
        ExecuteInvestmentPlanCommand cmd = captor.getValue();
        assertEquals(planId, cmd.planId());
        assertEquals(TEST_USER_ID, cmd.userId());
        assertTrue(cmd.advanceSchedule(), "manual execute must advance the schedule");
    }

    @Test
    void execute_shouldReturn404WhenPlanNotFound() {
        when(executeUseCase.execute(any(ExecuteInvestmentPlanCommand.class)))
                .thenThrow(new NoSuchElementException("Plan not found"));

        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .post(BASE + "/" + planId.value() + "/execute")
        .then()
            .statusCode(404)
            .body("detail", containsString("Investment plan not found"));
    }

    @Test
    void execute_shouldReturn401WhenNotAuthenticated() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .post(BASE + "/" + planId.value() + "/execute")
        .then()
            .statusCode(401);
    }
}
