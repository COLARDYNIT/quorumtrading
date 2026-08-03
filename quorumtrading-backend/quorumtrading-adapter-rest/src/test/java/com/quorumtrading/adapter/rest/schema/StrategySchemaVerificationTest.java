package com.quorumtrading.adapter.rest.schema;

import com.quorumtrading.adapter.rest.dto.response.StrategySchemaResponse;
import com.quorumtrading.adapter.rest.dto.response.StrategySchemaResponse.*;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that StrategySchemaProvider stays in sync with StrategyConfigParser.
 *
 * <p>This test is CRITICAL for maintaining frontend/backend validation consistency.
 * If this test fails, it means the schema exposed to the frontend no longer matches
 * the actual backend validation rules.</p>
 *
 * <p><b>When to update:</b></p>
 * <ul>
 *   <li>Adding new YAML fields to StrategyConfigParser → Add to schema + update this test</li>
 *   <li>Changing field defaults → Update schema + verify this test passes</li>
 *   <li>Adding enum values → Update this test to verify enum is in sync</li>
 *   <li>Changing validation rules → Update schema conditionalRules + this test</li>
 * </ul>
 */
class StrategySchemaVerificationTest {

    private StrategySchemaProvider schemaProvider;
    private StrategyConfigParser parser;
    private StrategySchemaResponse schema;

    @BeforeEach
    void setUp() {
        schemaProvider = new StrategySchemaProvider();
        parser = new StrategyConfigParser();
        schema = schemaProvider.getSchema();
    }

    @Nested
    @DisplayName("ConsensusMethod Enum Sync")
    class ConsensusMethodEnumSync {

        @Test
        @DisplayName("Schema contains all ConsensusMethod enum values")
        void schemaContainsAllConsensusMethodValues() {
            // Get enum values from schema
            EnumDefinition consensusMethodDef = schema.enums().get("ConsensusMethod");
            assertNotNull(consensusMethodDef, "ConsensusMethod enum must be defined in schema");

            Set<String> schemaValues = consensusMethodDef.values().stream()
                .map(EnumValue::value)
                .collect(Collectors.toSet());

            // Get actual enum values from Java
            Set<String> javaValues = Arrays.stream(ConsensusMethod.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

            // Verify they match exactly
            assertEquals(javaValues, schemaValues,
                "Schema ConsensusMethod values must match Java enum. " +
                "Missing in schema: " + difference(javaValues, schemaValues) + ", " +
                "Extra in schema: " + difference(schemaValues, javaValues));
        }

        @Test
        @DisplayName("Schema ConsensusMethod displayNames match Java enum")
        void schemaConsensusMethodDisplayNamesMatch() {
            EnumDefinition consensusMethodDef = schema.enums().get("ConsensusMethod");

            for (ConsensusMethod method : ConsensusMethod.values()) {
                EnumValue schemaValue = consensusMethodDef.values().stream()
                    .filter(v -> v.value().equals(method.name()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing enum value: " + method.name()));

                assertEquals(method.getDisplayName(), schemaValue.displayName(),
                    "DisplayName mismatch for " + method.name());
                assertEquals(method.getDescription(), schemaValue.description(),
                    "Description mismatch for " + method.name());
            }
        }
    }

    @Nested
    @DisplayName("Field Definitions Sync")
    class FieldDefinitionsSync {

        @Test
        @DisplayName("Schema contains all required fields from parser")
        void schemaContainsAllParserFields() {
            Map<String, FieldDefinition> fields = schema.fields();

            // Fields parsed by StrategyConfigParser (from source code analysis)
            String[] expectedFields = {
                "version",
                "consensus_method",
                "consensus_threshold",
                "reserve_cash_percentage",
                "max_order_fraction",
                "position_limits",
                "stock_tickers",
                "crypto_tickers",
                "live_schedule",
                "backtest_schedule",
                "llm_model",
                "llm_temperature",
                "agents"
            };

            for (String field : expectedFields) {
                assertTrue(fields.containsKey(field),
                    "Schema missing field '" + field + "' that parser expects");
            }
        }

        @Test
        @DisplayName("Default values match parser defaults")
        void defaultValuesMatchParser() {
            Map<String, FieldDefinition> fields = schema.fields();

            // Verify defaults match StrategyConfigParser.parseYamlFrontmatter()
            assertEquals("v1.0", fields.get("version").defaultValue());
            assertEquals("ORCHESTRATOR_OVERRIDE", fields.get("consensus_method").defaultValue());
            assertEquals(0.6, fields.get("consensus_threshold").defaultValue());
            assertEquals(0.2, fields.get("reserve_cash_percentage").defaultValue());
        }

        @Test
        @DisplayName("Number range constraints are valid")
        void numberRangeConstraintsAreValid() {
            Map<String, FieldDefinition> fields = schema.fields();

            // consensus_threshold: 0.0 to 1.0
            FieldDefinition threshold = fields.get("consensus_threshold");
            assertEquals(0.0, threshold.min());
            assertEquals(1.0, threshold.max());

            // reserve_cash_percentage: 0.0 to 1.0
            FieldDefinition reserve = fields.get("reserve_cash_percentage");
            assertEquals(0.0, reserve.min());
            assertEquals(1.0, reserve.max());
        }

        @Test
        @DisplayName("live_schedule nested fields are defined")
        void liveScheduleNestedFieldsDefined() {
            FieldDefinition liveSchedule = schema.fields().get("live_schedule");
            assertNotNull(liveSchedule.properties(), "live_schedule should have nested properties");

            Map<String, FieldDefinition> props = liveSchedule.properties();
            assertTrue(props.containsKey("interval"), "live_schedule missing interval");
            assertTrue(props.containsKey("timezone"), "live_schedule missing timezone");
            assertTrue(props.containsKey("trading_days"), "live_schedule missing trading_days");
            assertTrue(props.containsKey("evaluation_times"), "live_schedule missing evaluation_times");
            assertTrue(props.containsKey("market_open"), "live_schedule missing market_open");
            assertTrue(props.containsKey("market_close"), "live_schedule missing market_close");
        }

        @Test
        @DisplayName("Removed legacy schedule fields are absent from schema")
        void legacyScheduleFieldsAbsent() {
            assertFalse(schema.fields().containsKey("market_hours"),
                "Schema must not expose removed legacy field market_hours");
            assertFalse(schema.fields().containsKey("evaluation_interval_seconds"),
                "Schema must not expose removed legacy field evaluation_interval_seconds");
        }
    }

    @Nested
    @DisplayName("Conditional Rules Sync")
    class ConditionalRulesSync {

        @Test
        @DisplayName("Schema includes stock_requires_live_schedule rule")
        void schemaIncludesStockMarketHoursRule() {
            boolean hasRule = schema.conditionalRules().stream()
                .anyMatch(r -> r.ruleId().equals("stock_requires_live_schedule"));

            assertTrue(hasRule,
                "Schema must include rule: STOCK trading mode requires live_schedule");
        }

        @Test
        @DisplayName("Schema includes live_schedule_requires_evaluation_times rule")
        void schemaIncludesEvaluationTimesRule() {
            boolean hasRule = schema.conditionalRules().stream()
                .anyMatch(r -> r.ruleId().equals("live_schedule_requires_evaluation_times"));

            assertTrue(hasRule,
                "Schema must include rule: live_schedule requires evaluation_times");
        }
    }

    @Nested
    @DisplayName("Parser Validation Consistency")
    class ParserValidationConsistency {

        @Test
        @DisplayName("Parser accepts valid CRYPTO mode config")
        void parserAcceptsValidCryptoConfig() {
            String validCrypto = """
                ---
                version: v1.0
                consensus_method: MAJORITY
                consensus_threshold: 0.7
                live_schedule:
                  interval: 600
                crypto_tickers:
                  - BTC
                  - ETH
                ---
                # Trading Strategy
                Buy when momentum is positive.
                """;

            // Should not throw
            assertDoesNotThrow(() -> parser.parse(validCrypto));
        }

        @Test
        @DisplayName("Parser accepts valid STOCK mode config with live_schedule")
        void parserAcceptsValidStockConfig() {
            String validStock = """
                ---
                version: v1.0
                consensus_method: WEIGHTED
                stock_tickers:
                  - AAPL
                  - MSFT
                live_schedule:
                  timezone: America/New_York
                  evaluation_times:
                    - "10:00"
                    - "14:00"
                ---
                # Stock Trading Strategy
                Trade tech stocks.
                """;

            // Should not throw
            assertDoesNotThrow(() -> parser.parse(validStock));
        }

        @Test
        @DisplayName("Parser rejects STOCK mode without live_schedule")
        void parserRejectsStockWithoutLiveSchedule() {
            String invalidStock = """
                ---
                stock_tickers:
                  - AAPL
                ---
                # Strategy
                """;

            // Should throw because live_schedule is required for STOCK mode
            Exception ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(invalidStock));

            assertTrue(ex.getMessage().contains("live_schedule"),
                "Error should mention live_schedule requirement");
        }

        @Test
        @DisplayName("Parser rejects live_schedule without evaluation_times")
        void parserRejectsLiveScheduleWithoutEvaluationTimes() {
            String invalidConfig = """
                ---
                stock_tickers:
                  - AAPL
                live_schedule:
                  timezone: America/New_York
                ---
                # Strategy
                """;

            // Should throw because evaluation_times is required
            Exception ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(invalidConfig));

            assertTrue(ex.getMessage().contains("evaluation_times"),
                "Error should mention evaluation_times requirement");
        }

        @Test
        @DisplayName("Parser rejects removed legacy schedule fields")
        void parserRejectsLegacyScheduleFields() {
            String legacyConfig = """
                ---
                stock_tickers:
                  - AAPL
                market_hours:
                  timezone: America/New_York
                  evaluation_times:
                    - "10:00"
                ---
                # Strategy
                """;

            Exception ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(legacyConfig));

            assertTrue(ex.getMessage().contains("no longer supported"),
                "Error should point at the removed legacy format, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Parser accepts agents field with valid roles")
        void parserAcceptsAgentsField() {
            String config = """
                ---
                consensus_method: ORCHESTRATOR_OVERRIDE
                crypto_tickers:
                  - BTC
                agents:
                  - TECHNICAL
                  - FUNDAMENTAL
                ---
                # Strategy
                Trade carefully.
                """;

            StrategyConfig parsed = parser.parse(config);
            assertEquals(Set.of(AgentRole.TECHNICAL, AgentRole.FUNDAMENTAL), parsed.getEnabledAgents());
        }

        @Test
        @DisplayName("Parser rejects empty agents list")
        void parserRejectsEmptyAgentsList() {
            String config = """
                ---
                crypto_tickers:
                  - BTC
                agents: []
                ---
                # Strategy
                """;

            Exception ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(config));
            assertTrue(ex.getMessage().contains("agents list cannot be empty"),
                "Error should mention empty agents list, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Parser silently skips ORCHESTRATOR in agents list")
        void parserSkipsOrchestratorInAgentsList() {
            String config = """
                ---
                crypto_tickers:
                  - BTC
                agents:
                  - TECHNICAL
                  - ORCHESTRATOR
                ---
                # Strategy
                """;

            StrategyConfig parsed = parser.parse(config);
            assertEquals(Set.of(AgentRole.TECHNICAL), parsed.getEnabledAgents(),
                "ORCHESTRATOR should be silently skipped");
        }

        @Test
        @DisplayName("Parser rejects an unknown agent name")
        void parserRejectsUnknownAgentName() {
            String config = """
                ---
                crypto_tickers:
                  - BTC
                agents:
                  - TECHNICAL
                  - INVALID_AGENT
                ---
                # Strategy
                """;

            // Previously skipped in silence, which meant a typo'd agent simply never voted and the
            // author had no way to notice. Rejected now, consistently with `agents: []`.
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(config));
            assertTrue(thrown.getMessage().contains("INVALID_AGENT"),
                "Error should name the offending role: " + thrown.getMessage());
        }

        @Test
        @DisplayName("An all-unknown agents list is rejected rather than silently meaning 'all three'")
        void allUnknownAgentsIsRejected() {
            String config = """
                ---
                crypto_tickers:
                  - BTC
                agents:
                  - RISK_MANAGER
                  - COMPLIANCE
                ---
                # Strategy
                """;

            // This used to yield null enabledAgents, which getEffectiveEnabledAgents() reads as
            // "use all three" — so a list of typos quietly tripled the agents per evaluation, and
            // the bill with it. The author asked for something specific and unavailable; say so.
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(config));
            assertTrue(thrown.getMessage().contains("RISK_MANAGER"),
                "Error should name the offending role: " + thrown.getMessage());
        }

        @Test
        @DisplayName("Single agent in agents list is valid")
        void singleAgentIsValid() {
            String config = """
                ---
                crypto_tickers:
                  - BTC
                agents:
                  - SENTIMENT
                ---
                # Strategy
                Use sentiment only.
                """;

            StrategyConfig parsed = parser.parse(config);
            assertEquals(Set.of(AgentRole.SENTIMENT), parsed.getEnabledAgents());
        }

        @Test
        @DisplayName("Missing agents field defaults to all 4 agents")
        void missingAgentsDefaultsToAll() {
            String config = """
                ---
                crypto_tickers:
                  - BTC
                ---
                # Strategy
                """;

            StrategyConfig parsed = parser.parse(config);
            assertNull(parsed.getEnabledAgents(), "Raw enabledAgents should be null when not specified");
            assertEquals(
                Set.of(AgentRole.TECHNICAL, AgentRole.FUNDAMENTAL, AgentRole.SENTIMENT),
                parsed.getEffectiveEnabledAgents(),
                "Effective agents should include all 3 when not specified"
            );
        }

        @Test
        @DisplayName("Schema agents field references AgentRole enum")
        void agentsFieldReferencesAgentRoleEnum() {
            FieldDefinition agentsField = schema.fields().get("agents");
            assertNotNull(agentsField, "Schema must contain 'agents' field");
            assertEquals("array", agentsField.type(), "agents field should be of type array");

            // Verify AgentRole enum is defined in schema
            EnumDefinition agentRoleDef = schema.enums().get("AgentRole");
            assertNotNull(agentRoleDef, "AgentRole enum must be defined in schema");

            // Verify enum contains the 4 specialized agent roles (not ORCHESTRATOR)
            Set<String> schemaValues = agentRoleDef.values().stream()
                .map(EnumValue::value)
                .collect(Collectors.toSet());
            assertEquals(
                Set.of("TECHNICAL", "FUNDAMENTAL", "SENTIMENT"),
                schemaValues,
                "AgentRole enum should contain all 3 specialized agent roles"
            );
        }

        @Test
        @DisplayName("All schema ConsensusMethod values are parseable")
        void allSchemaConsensusMethodsAreParseable() {
            for (ConsensusMethod method : ConsensusMethod.values()) {
                String config = String.format("""
                    ---
                    consensus_method: %s
                    crypto_tickers:
                      - BTC
                    ---
                    # Strategy
                    """, method.name());

                assertDoesNotThrow(() -> parser.parse(config),
                    "Parser should accept consensus_method: " + method.name());
            }
        }
    }

    // Helper to find set difference
    private Set<String> difference(Set<String> a, Set<String> b) {
        return a.stream()
            .filter(x -> !b.contains(x))
            .collect(Collectors.toSet());
    }
}
