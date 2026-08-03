package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.adapter.mcp.dto.McpInvestmentAllocationDto;
import com.quorumtrading.adapter.mcp.dto.McpInvestmentPlanDto;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
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
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.InvestmentPlanStatus;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferencesPatch;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MCP tools for recurring investment plans.
 *
 * <p>execute_investment_plan_now triggers real broker action — its description
 * makes that explicit so an aggressive agent doesn't fire it without context.</p>
 */
@Singleton
@ActivateRequestContext
public class InvestmentPlanTools extends McpToolBase {

    @Inject CreateInvestmentPlanUseCase createUseCase;
    @Inject UpdateInvestmentPlanUseCase updateUseCase;
    @Inject PauseInvestmentPlanUseCase pauseUseCase;
    @Inject ResumeInvestmentPlanUseCase resumeUseCase;
    @Inject TerminateInvestmentPlanUseCase terminateUseCase;
    @Inject GetInvestmentPlanUseCase getUseCase;
    @Inject ListInvestmentPlansUseCase listUseCase;
    @Inject ListInvestmentAllocationsUseCase listAllocationsUseCase;
    @Inject GetInvestmentAllocationUseCase getAllocationUseCase;
    @Inject ExecuteInvestmentPlanUseCase executeUseCase;
    @Inject BrokerAccountRepository brokerAccountRepository;
    @Inject McpUserContext mcpUserContext;
    @Inject com.quorumtrading.adapter.mcp.mapper.UiLinks uiLinks;

    /**
     * Whether the DCA scheduler runs here. Off by default, so every plan this tool returns has to
     * carry it — an agent reading ACTIVE + nextExecutionAt would otherwise report to its user that
     * the money is scheduled to move when nothing will move it.
     */
    @org.eclipse.microprofile.config.inject.ConfigProperty(
            name = "quorumtrading.scheduler.investments.enabled", defaultValue = "false")
    boolean automaticExecutionEnabled;

    @Tool(name = "create_investment_plan", description =
            "Create a recurring investment plan. On dayOfMonth the plan picks a strategy via the AI "
                    + "picker and funds it (top up an existing live simulation on that strategy, or "
                    + "start a new one). "
                    + "IMPORTANT: automatic monthly execution is OFF by default on every instance — "
                    + "the scheduler moves real money, so an operator must enable it explicitly. Until "
                    + "then the plan will sit ACTIVE with a nextExecutionAt date and never fire; "
                    + "execute_investment_plan_now is the only thing that will fund it. The plan's "
                    + "automaticExecutionEnabled field says which applies here — tell the user, because "
                    + "a plan that silently never invests looks identical to one that is working. "
                    + "budgetMode: FIXED_AMOUNT (require exact amount) | FIXED_OR_PARTIAL (accept less if "
                    + "available balance is short) | ALL_AVAILABLE (invest entire available balance). "
                    + "WARNING: against ALPACA_LIVE brokers this trades real money — pass confirmLive=true "
                    + "to acknowledge.")
    public ToolResponse createInvestmentPlan(
            @ToolArg(description = "UUID of the broker account to fund from") String brokerAccountId,
            @ToolArg(description = "FIXED_AMOUNT | FIXED_OR_PARTIAL | ALL_AVAILABLE") String budgetMode,
            @ToolArg(description = "Amount (ignored if ALL_AVAILABLE)") Optional<String> amount,
            @ToolArg(description = "Currency, default USD") Optional<String> currency,
            @ToolArg(description = "Day of month to fire (1-28)") int dayOfMonth,
            @ToolArg(description = "Picker policy, currently AI_PICK only") Optional<String> pickerPolicy,
            @ToolArg(description = "Optional risk tolerance hint for the picker") Optional<String> riskTolerance,
            @ToolArg(description = "Optional allowed tickers as comma-separated list, e.g. 'AAPL,MSFT'") Optional<String> allowedTickers,
            @ToolArg(description = "Optional blocked tickers as comma-separated list, e.g. 'GME,AMC'") Optional<String> blockedTickers,
            @ToolArg(description = "Max concurrent live simulations the plan may spawn") Optional<Integer> maxStrategiesActive,
            @ToolArg(description = "Prefer topping up an existing live simulation when possible") Optional<Boolean> preferExistingHoldings,
            @ToolArg(description = "Set true to authorize live (real money) trading") Optional<Boolean> confirmLive
    ) {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            BrokerAccountId brokerId = new BrokerAccountId(UUID.fromString(brokerAccountId));
            BudgetMode mode = parseEnum(BudgetMode.class, budgetMode, "budgetMode");
            PickerPolicy policy = pickerPolicy.map(p -> parseEnum(PickerPolicy.class, p, "pickerPolicy"))
                    .orElse(PickerPolicy.AI_PICK);

            Money money = null;
            if (amount.isPresent()) {
                Money.Currency cur = currency.filter(c -> !c.isBlank())
                        .map(c -> parseEnum(Money.Currency.class, c, "currency"))
                        .orElse(Money.Currency.USD);
                money = new Money(parseBigDecimal(amount.get(), "amount"), cur);
            }

            InvestmentPlanPreferences prefs = new InvestmentPlanPreferences(
                    riskTolerance.orElse(null),
                    parseTickerCsv(allowedTickers),
                    parseTickerCsv(blockedTickers),
                    maxStrategiesActive.orElse(null),
                    preferExistingHoldings.orElse(true));

            InvestmentPlan plan = createUseCase.execute(new CreateInvestmentPlanCommand(
                    userId, brokerId, mode, money, dayOfMonth, policy, prefs,
                    confirmLive.orElse(false)));
            return toJson(toDto(plan));
        });
    }

    @Tool(name = "list_investment_plans", description = "List the current user's recurring investment plans. Optional status filter: ACTIVE | PAUSED | TERMINATED.")
    public ToolResponse listInvestmentPlans(
            @ToolArg(description = "Status filter") Optional<String> status,
            @ToolArg(description = "Maximum number of results (default 20, max 100)") Optional<Integer> limit
    ) {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            Optional<InvestmentPlanStatus> filter = status.filter(s -> !s.isBlank())
                    .map(s -> parseEnum(InvestmentPlanStatus.class, s, "status"));
            // This tool accepted no bound at all, so a caller could not cap the response and
            // hasMore had nothing to report. paginate supplies both.
            int effectiveLimit = parseLimit(limit, 20, 100);
            Page<InvestmentPlan> page = paginate(listUseCase.listForUser(userId, filter), effectiveLimit);
            List<McpInvestmentPlanDto> dtos = page.items().stream().map(this::toDto).toList();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("plans", dtos);
            body.put("count", dtos.size());
            body.put("hasMore", page.hasMore());
            return toJson(body);
        });
    }

    @Tool(name = "get_investment_plan", description = "Get one investment plan by ID.")
    public ToolResponse getInvestmentPlan(@ToolArg(description = "Plan UUID") String planId) {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            InvestmentPlan plan = getUseCase.getOwnedBy(InvestmentPlanId.fromString(planId), userId);
            return toJson(toDto(plan));
        });
    }

    @Tool(name = "update_investment_plan", description =
            "Partially update an investment plan. Pass only the fields you want to change — every "
                    + "field you omit keeps its stored value, including the picker preferences. To "
                    + "clear a ticker list pass an empty string; to clear riskTolerance pass an "
                    + "empty string. Returns the full updated plan, so you can confirm what changed.")
    public ToolResponse updateInvestmentPlan(
            @ToolArg(description = "Plan UUID") String planId,
            @ToolArg(description = "New broker account UUID") Optional<String> brokerAccountId,
            @ToolArg(description = "New budget mode") Optional<String> budgetMode,
            @ToolArg(description = "New amount") Optional<String> amount,
            @ToolArg(description = "New currency") Optional<String> currency,
            @ToolArg(description = "New day of month (1-28)") Optional<Integer> dayOfMonth,
            @ToolArg(description = "New risk tolerance hint") Optional<String> riskTolerance,
            @ToolArg(description = "New allowed tickers as comma-separated list, e.g. 'AAPL,MSFT'. Pass empty string to clear.") Optional<String> allowedTickers,
            @ToolArg(description = "New blocked tickers as comma-separated list, e.g. 'GME,AMC'. Pass empty string to clear.") Optional<String> blockedTickers,
            @ToolArg(description = "New max active strategies") Optional<Integer> maxStrategiesActive,
            @ToolArg(description = "New preferExistingHoldings") Optional<Boolean> preferExistingHoldings,
            @ToolArg(description = "Required when moving the plan onto a LIVE broker account: set true to acknowledge that this plan will trade REAL money. Same acknowledgement create_investment_plan requires.") Optional<Boolean> confirmLive
    ) {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            Optional<BrokerAccountId> brokerOpt = brokerAccountId.filter(s -> !s.isBlank())
                    .map(s -> new BrokerAccountId(UUID.fromString(s)));
            Optional<BudgetMode> modeOpt = budgetMode.filter(s -> !s.isBlank())
                    .map(s -> parseEnum(BudgetMode.class, s, "budgetMode"));
            Optional<Money> amountOpt = amount.filter(s -> !s.isBlank()).map(s -> {
                Money.Currency cur = currency.filter(c -> !c.isBlank())
                        .map(c -> parseEnum(Money.Currency.class, c, "currency"))
                        .orElse(Money.Currency.USD);
                return new Money(parseBigDecimal(s, "amount"), cur);
            });

            // One Optional per preference, so an argument the caller omitted stays absent all the
            // way into the use case and leaves the stored value alone. Building a whole
            // InvestmentPlanPreferences here is what used to erase everything the caller did not
            // happen to name.
            Optional<InvestmentPlanPreferencesPatch> prefsOpt = Optional.of(
                    new InvestmentPlanPreferencesPatch(
                            riskTolerance,
                            allowedTickers.map(InvestmentPlanTools::parseTickerList),
                            blockedTickers.map(InvestmentPlanTools::parseTickerList),
                            maxStrategiesActive,
                            preferExistingHoldings))
                    .filter(patch -> !patch.isEmpty());

            InvestmentPlan plan = updateUseCase.execute(new UpdateInvestmentPlanCommand(
                    InvestmentPlanId.fromString(planId), userId,
                    brokerOpt, modeOpt, amountOpt, dayOfMonth, prefsOpt,
                    confirmLive.orElse(false)));
            return toJson(toDto(plan));
        });
    }

    @Tool(name = "pause_investment_plan", description = "Pause a plan. The scheduler skips paused plans until resumed.")
    public ToolResponse pauseInvestmentPlan(@ToolArg(description = "Plan UUID") String planId) {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            InvestmentPlan plan = pauseUseCase.pause(InvestmentPlanId.fromString(planId), userId);
            return toJson(toDto(plan));
        });
    }

    @Tool(name = "resume_investment_plan", description = "Resume a paused plan. Next execution is recomputed from now.")
    public ToolResponse resumeInvestmentPlan(@ToolArg(description = "Plan UUID") String planId) {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            InvestmentPlan plan = resumeUseCase.resume(InvestmentPlanId.fromString(planId), userId);
            return toJson(toDto(plan));
        });
    }

    @Tool(name = "delete_investment_plan", description = "Terminate an investment plan. This is irreversible. Existing allocations remain for audit.")
    public ToolResponse deleteInvestmentPlan(@ToolArg(description = "Plan UUID") String planId) {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            terminateUseCase.terminate(InvestmentPlanId.fromString(planId), userId);
            return toJson(Map.of("status", "terminated", "id", planId));
        });
    }

    @Tool(name = "execute_investment_plan_now", description =
            "Run one execution tick immediately. WARNING: this triggers real picker work and, if the "
                    + "broker is paper/live, a real order. Use sparingly for testing. Writes an allocation "
                    + "audit row regardless of success.")
    public ToolResponse executeInvestmentPlanNow(@ToolArg(description = "Plan UUID") String planId) {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            InvestmentAllocation allocation = executeUseCase.execute(new ExecuteInvestmentPlanCommand(
                    InvestmentPlanId.fromString(planId), userId, Instant.now(), true));
            return toJson(toDto(allocation));
        });
    }

    @Tool(name = "list_investment_allocations", description =
            "List allocations for a plan (newest first). Each allocation describes one execution attempt — "
                    + "success, skip (with reason), or failure.")
    public ToolResponse listInvestmentAllocations(
            @ToolArg(description = "Plan UUID") String planId,
            @ToolArg(description = "Max rows to return (default 50, max 200)") Optional<Integer> limit
    ) {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            List<McpInvestmentAllocationDto> dtos = listAllocationsUseCase.forPlan(
                    InvestmentPlanId.fromString(planId), userId, parseLimit(limit, 50, 200)).stream()
                    .map(this::toDto).toList();
            return toJson(Map.of("allocations", dtos, "count", dtos.size()));
        });
    }

    @Tool(name = "get_investment_allocation", description = "Get one allocation by ID — includes the picker reasoning that led to the action.")
    public ToolResponse getInvestmentAllocation(@ToolArg(description = "Allocation UUID") String allocationId) {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            InvestmentAllocation allocation = getAllocationUseCase.getOwnedBy(
                    InvestmentAllocationId.fromString(allocationId), userId);
            return toJson(toDto(allocation));
        });
    }

    // ---- Mappers ----

    private McpInvestmentPlanDto toDto(InvestmentPlan plan) {
        return new McpInvestmentPlanDto(
                plan.getId().value().toString(),
                plan.getBrokerAccountId().value().toString(),
                plan.getBudgetMode().name(),
                plan.getAmount() != null ? plan.getAmount().amount().toPlainString() : null,
                plan.getAmount() != null ? plan.getAmount().currency().name() : "USD",
                plan.getDayOfMonth(),
                plan.getPickerPolicy().name(),
                new McpInvestmentPlanDto.Preferences(
                        plan.getPreferences().riskTolerance(),
                        plan.getPreferences().allowedTickers(),
                        plan.getPreferences().blockedTickers(),
                        plan.getPreferences().maxStrategiesActive(),
                        plan.getPreferences().preferExistingHoldings()),
                plan.getStatus().name(),
                plan.getNextExecutionAt() != null ? plan.getNextExecutionAt().toString() : null,
                plan.getLastExecutionAt() != null ? plan.getLastExecutionAt().toString() : null,
                plan.getCreatedAt().toString(),
                plan.getUpdatedAt().toString(),
                automaticExecutionEnabled,
                uiLinks.investmentPlan(plan.getId().value().toString()));
    }

    private McpInvestmentAllocationDto toDto(InvestmentAllocation a) {
        return new McpInvestmentAllocationDto(
                a.getId().value().toString(),
                a.getPlanId().value().toString(),
                a.getExecutionDate().toString(),
                a.getStatus().name(),
                a.getIntendedAmount().amount().toPlainString(),
                a.getActualAmount() != null ? a.getActualAmount().amount().toPlainString() : null,
                a.getIntendedAmount().currency().name(),
                a.getChosenStrategyId() != null ? a.getChosenStrategyId().value().toString() : null,
                a.getPickerReasoning(),
                a.getPickerLlmCostUsd().toPlainString(),
                a.getAction() != null ? a.getAction().name() : null,
                a.getTargetSimulationId() != null ? a.getTargetSimulationId().value().toString() : null,
                a.getContributionId() != null ? a.getContributionId().value().toString() : null,
                a.getBrokerOrderRefs(),
                a.getFailureReason(),
                a.getCreatedAt().toString());
    }

    /**
     * Parse a comma-separated ticker list into List&lt;String&gt;.
     *
     * <p>Tickers come in over MCP as a CSV string (e.g. "AAPL,MSFT") rather than
     * a JSON array because the upstream {@code quarkus-mcp-server-http} 1.9.1
     * cannot generate a JSON Schema for {@code Optional<List<String>>} tool args
     * — when it encounters one, the whole tool registration pass aborts and a
     * batch of unrelated tool classes silently drop off the MCP {@code tools/list}
     * response. Flat strings sidestep that issue. Empty / missing input → empty list.</p>
     */
    private static List<String> parseTickerCsv(Optional<String> csv) {
        return csv.map(InvestmentPlanTools::parseTickerList).orElseGet(List::of);
    }

    /**
     * Splits a comma-separated ticker argument. A blank string yields an empty list, which on the
     * update path is the documented way to clear a list — distinct from omitting the argument,
     * which leaves the stored list alone.
     */
    private static List<String> parseTickerList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        String[] parts = csv.split(",");
        List<String> out = new java.util.ArrayList<>(parts.length);
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
