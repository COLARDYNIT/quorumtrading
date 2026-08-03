package com.quorumtrading.adapter.rest.schema;

import com.quorumtrading.adapter.rest.dto.response.StrategySchemaResponse;
import com.quorumtrading.adapter.rest.dto.response.StrategySchemaResponse.*;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.ConsensusMethod;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides the strategy configuration schema for frontend validation.
 *
 * <p>This provider generates schema definitions that match the validation
 * rules in {@link com.quorumtrading.model.orchestration.StrategyConfigParser}.
 * A test verifies these stay in sync.</p>
 *
 * @see StrategySchemaResponse
 */
@ApplicationScoped
public class StrategySchemaProvider {

    /**
     * Schema version - increment when validation rules change.
     * Frontend can cache schema and invalidate on version change.
     */
    private static final String SCHEMA_VERSION = "3.1.0";

    /**
     * Generate the complete strategy schema.
     */
    public StrategySchemaResponse getSchema() {
        return new StrategySchemaResponse(
            SCHEMA_VERSION,
            buildFieldDefinitions(),
            buildEnumDefinitions(),
            buildConditionalRules()
        );
    }

    private Map<String, FieldDefinition> buildFieldDefinitions() {
        Map<String, FieldDefinition> fields = new LinkedHashMap<>();

        // Basic configuration fields
        fields.put("version", FieldDefinition.string(
            "Strategy configuration version",
            "v1.0",
            false
        ));

        fields.put("consensus_method", FieldDefinition.enumField(
            "How to decide what to do when AI agents disagree. " +
            "ORCHESTRATOR_OVERRIDE (recommended) adds safety rules. " +
            "MAJORITY or MAJORITY_VOTE uses simple voting.",
            "ORCHESTRATOR_OVERRIDE",
            false,
            "ConsensusMethod"
        ));

        fields.put("consensus_threshold", FieldDefinition.number(
            "In MAJORITY mode, HOLD agents with confidence below this value abstain from voting. " +
            "Higher = more HOLD agents abstain (stronger signals pass through). Lower = more HOLD agents vote.",
            0.6,
            false,
            0.0,
            1.0
        ));

        fields.put("reserve_cash_percentage", FieldDefinition.number(
            "Percentage of portfolio to keep as cash reserve (0.0 to 1.0)",
            0.2,
            false,
            0.0,
            1.0
        ));

        fields.put("max_order_fraction", FieldDefinition.number(
            "Max fraction of portfolio value a single BUY order may take (0.0 to 1.0). " +
            "Unset, a BUY the agents did not size is platform-sized to 25% of portfolio value.",
            null,
            false,
            0.0,
            1.0
        ));

        // Ticker fields
        fields.put("stock_tickers", FieldDefinition.array(
            "List of stock ticker symbols (e.g., AAPL, MSFT). Presence triggers STOCK trading mode.",
            "string",
            false
        ));

        fields.put("crypto_tickers", FieldDefinition.array(
            "List of crypto ticker symbols (e.g., BTC, ETH). Defaults to [BTC] if no tickers specified.",
            "string",
            false
        ));

        // Position limits map
        fields.put("position_limits", FieldDefinition.map(
            "Maximum position size per ticker as percentage of portfolio (e.g., {AAPL: 0.25}). "
                + "A BUY without an explicit quantity fills straight to this cap (or to all cash "
                + "above the reserve when the ticker has no entry) — it is the default order size, "
                + "not a rarely-hit ceiling.",
            "number",
            false
        ));

        // Position cooldown (optional)
        fields.put("position_cooldown", FieldDefinition.string(
            "Cooldown after a BUY/SELL before the same ticker can trade again (e.g., '14d', '2w', '1h'). Prevents whipsawing.",
            null,
            false
        ));

        // LLM model override (optional)
        fields.put("llm_model", FieldDefinition.string(
            "Pin a specific LLM model for this strategy (e.g., 'anthropic/claude-3.5-sonnet'). " +
            "When set, disables the fallback chain - if this model fails, consensus fails.",
            null,
            false
        ));

        // Per-agent LLM model overrides (optional)
        Map<String, FieldDefinition> agentModelProps = new java.util.LinkedHashMap<>();
        for (String role : List.of("TECHNICAL", "FUNDAMENTAL", "SENTIMENT", "ORCHESTRATOR")) {
            agentModelProps.put(role, FieldDefinition.string(
                "LLM model for the " + role + " agent", null, false));
        }
        fields.put("llm_models", FieldDefinition.object(
            "Per-agent LLM model overrides. Keys are agent roles (TECHNICAL, FUNDAMENTAL, SENTIMENT, ORCHESTRATOR), " +
            "values are model IDs. Takes precedence over llm_model for the specified agents.",
            false,
            agentModelProps
        ));

        fields.put("llm_temperature", FieldDefinition.number(
            "Override the LLM temperature for this strategy (0.0 to 2.0). " +
            "Lower values are more deterministic, higher values more creative.",
            null,
            false,
            0.0,
            2.0
        ));

        // Agent selection (optional - defaults to all 4 specialized agents)
        fields.put("agents", FieldDefinition.array(
            "Subset of AI agents to enable for this strategy. Valid values: TECHNICAL, FUNDAMENTAL, SENTIMENT. " +
            "ORCHESTRATOR always runs and should not be listed. Omit to use all 3 agents (default).",
            "string",
            false
        ));

        // Dual schedule objects
        fields.put("live_schedule", buildScheduleDefinition(
            "Live trading schedule. STOCK mode: timezone, trading_days, evaluation_times, market_open, market_close. " +
            "CRYPTO mode: interval (e.g., '1h', '30m', '1d')."
        ));
        fields.put("backtest_schedule", buildScheduleDefinition(
            "Backtest schedule (optional). Same format as live_schedule. " +
            "STOCK default: Wednesday 10:00. CRYPTO default: 1w interval."
        ));

        return fields;
    }

    private FieldDefinition buildScheduleDefinition(String description) {
        Map<String, FieldDefinition> props = new LinkedHashMap<>();

        props.put("interval", FieldDefinition.string(
            "Human-readable duration for CRYPTO mode (e.g., '1h', '30m', '1d', '1w'). "
                + "CRYPTO schedules with this; STOCK schedules with 'evaluation_times' instead.",
            null,
            false
        ));

        props.put("timezone", FieldDefinition.string(
            "Timezone for STOCK mode schedule (IANA format, e.g., America/New_York)",
            "America/New_York",
            false
        ));

        props.put("trading_days", FieldDefinition.array(
            "Days of the week to trade (STOCK mode)",
            "string",
            false
        ));

        // Conditionally required: the `required` flag is unconditional, but validation only demands
        // this in STOCK mode — so the constraint is spelled out here instead. Strategy authors
        // (including LLMs generating frontmatter from this schema) otherwise omit it and get a 400.
        props.put("evaluation_times", FieldDefinition.array(
            "Times to evaluate consensus in HH:MM format, e.g. [\"10:00\", \"14:00\"]. "
                + "REQUIRED and must be non-empty for STOCK mode — a stock strategy without it is "
                + "rejected. Not used in CRYPTO mode, which schedules with 'interval' instead.",
            "string",
            false
        ));

        props.put("market_open", FieldDefinition.string(
            "Market opening time in HH:MM format (STOCK mode)",
            "09:30",
            false
        ));

        props.put("market_close", FieldDefinition.string(
            "Market closing time in HH:MM format (STOCK mode)",
            "16:00",
            false
        ));

        return FieldDefinition.object(description, false, props);
    }

    private Map<String, EnumDefinition> buildEnumDefinitions() {
        Map<String, EnumDefinition> enums = new LinkedHashMap<>();

        // Build ConsensusMethod enum from actual Java enum
        List<EnumValue> consensusValues = Arrays.stream(ConsensusMethod.values())
            .map(cm -> new EnumValue(
                cm.name(),
                cm.getDisplayName(),
                cm.getDescription()
            ))
            .toList();

        enums.put("ConsensusMethod", new EnumDefinition(
            "Consensus Method",
            "Method used to reach trading decisions from multiple agent recommendations",
            consensusValues
        ));

        // Build AgentRole enum from actual Java enum (excluding ORCHESTRATOR)
        List<EnumValue> agentRoleValues = Arrays.stream(AgentRole.values())
            .filter(ar -> ar != AgentRole.ORCHESTRATOR) // ORCHESTRATOR is not a user-selectable agent
            .map(ar -> new EnumValue(
                ar.name(),
                ar.getDisplayName(),
                ar.getDescription()
            ))
            .toList();

        enums.put("AgentRole", new EnumDefinition(
            "Agent Role",
            "Specialized AI agent roles that can participate in consensus decisions",
            agentRoleValues
        ));

        // Trading days enum values
        List<EnumValue> dayValues = List.of(
            new EnumValue("MONDAY", "Monday", "Monday trading day"),
            new EnumValue("TUESDAY", "Tuesday", "Tuesday trading day"),
            new EnumValue("WEDNESDAY", "Wednesday", "Wednesday trading day"),
            new EnumValue("THURSDAY", "Thursday", "Thursday trading day"),
            new EnumValue("FRIDAY", "Friday", "Friday trading day"),
            new EnumValue("SATURDAY", "Saturday", "Saturday trading day"),
            new EnumValue("SUNDAY", "Sunday", "Sunday trading day")
        );

        enums.put("DayOfWeek", new EnumDefinition(
            "Day of Week",
            "Days of the week for trading schedule",
            dayValues
        ));

        return enums;
    }

    private List<ConditionalRule> buildConditionalRules() {
        return List.of(
            new ConditionalRule(
                "stock_requires_live_schedule",
                "stock_tickers.length > 0",
                "live_schedule",
                "STOCK trading mode requires 'live_schedule' configuration"
            ),
            new ConditionalRule(
                "live_schedule_requires_evaluation_times",
                "live_schedule != null && stock_tickers.length > 0",
                "live_schedule.evaluation_times",
                "live_schedule.evaluation_times is required for STOCK mode"
            )
        );
    }
}
