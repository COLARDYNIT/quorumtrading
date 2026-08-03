package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.UserBudgetJpaEntity;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.enums.AgentKind;
import com.quorumtrading.model.valueobject.ModelEffort;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.ModelPreferenceKey;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How model preferences survive a trip through the {@code model_preferences} JSONB column.
 *
 * <p>Two properties carry real risk. Keys written before the agent half became {@link AgentKind}
 * must still load — no data migration was performed, so the enum's constant names ARE the
 * compatibility contract. And effort must persist without being folded into the model string, which
 * would make {@code ModelId} stop being a model id.</p>
 */
@DisplayName("UserBudgetMapper — model preferences through JSONB")
class UserBudgetPreferenceMappingTest {

    private final UserBudgetMapper mapper = new UserBudgetMapper();

    /** A budget carrying exactly these preferences, as persistence would reconstitute one. */
    private UserBudget budgetWith(ModelPreferences preferences) {
        java.time.Instant now = java.time.Instant.now();
        return UserBudget.reconstitute(
                new com.quorumtrading.model.valueobject.UserBudgetId(UUID.randomUUID()),
                new UserId(UUID.randomUUID()),
                new com.quorumtrading.model.valueobject.BudgetLimit(java.math.BigDecimal.ZERO, "USD"),
                com.quorumtrading.model.valueobject.MonthlySpend.zeroFor(java.time.YearMonth.now()),
                java.math.BigDecimal.ZERO,
                preferences,
                null,
                null,
                now,
                now);
    }

    private ModelPreferences roundTrip(ModelPreferences preferences) {
        return mapper.toDomain(mapper.toJpaEntity(budgetWith(preferences))).getModelPreferences();
    }

    @Test
    @DisplayName("a context-global preference round-trips")
    void globalPreferenceRoundTrips() {
        ModelPreferences prefs = ModelPreferences.ofContextMap(
                Map.of(TradingContext.BACKTESTING, ModelId.of("llama3.2")));

        assertThat(roundTrip(prefs).getFor(TradingContext.BACKTESTING))
                .map(ModelId::value).contains("llama3.2");
    }

    @Test
    @DisplayName("a consensus per-agent preference round-trips under its legacy key spelling")
    void consensusAgentPreferenceRoundTrips() {
        ModelPreferences prefs = ModelPreferences.EMPTY.withAgentPreference(
                TradingContext.BACKTESTING, AgentKind.TECHNICAL, ModelId.of("sonnet"));

        UserBudgetJpaEntity entity = mapper.toJpaEntity(budgetWith(prefs));

        assertThat(entity.modelPreferences)
                .as("the stored key must stay byte-identical to what pre-AgentKind rows hold")
                .containsEntry("BACKTESTING_TECHNICAL", "sonnet");
        assertThat(mapper.toDomain(entity).getModelPreferences()
                .getForAgent(TradingContext.BACKTESTING, AgentKind.TECHNICAL))
                .map(ModelId::value).contains("sonnet");
    }

    @Test
    @DisplayName("a research per-agent preference round-trips through the same grammar")
    void researchAgentPreferenceRoundTrips() {
        ModelPreferences prefs = ModelPreferences.EMPTY.withAgentPreference(
                TradingContext.RESEARCH, AgentKind.STRATEGY_DESIGNER, ModelId.of("opus"));

        UserBudgetJpaEntity entity = mapper.toJpaEntity(budgetWith(prefs));

        assertThat(entity.modelPreferences).containsEntry("RESEARCH_STRATEGY_DESIGNER", "opus");
    }

    @Test
    @DisplayName("effort persists beside the model, never inside it")
    void effortPersistsBesideTheModel() {
        ModelPreferenceKey key = ModelPreferenceKey.global(TradingContext.BACKTESTING);
        ModelPreferences prefs = ModelPreferences
                .of(Map.of(key, ModelId.of("sonnet")))
                .withEffort(key, ModelEffort.HIGH);

        UserBudgetJpaEntity entity = mapper.toJpaEntity(budgetWith(prefs));

        assertThat(entity.modelPreferences)
                .containsEntry("BACKTESTING", "sonnet")
                .containsEntry("BACKTESTING#effort", "HIGH");

        ModelPreferences loaded = mapper.toDomain(entity).getModelPreferences();
        assertThat(loaded.getFor(TradingContext.BACKTESTING)).map(ModelId::value)
                .as("the model id must come back pure — no 'sonnet:high'")
                .contains("sonnet");
        assertThat(loaded.effortFor(key)).contains(ModelEffort.HIGH);
    }

    @Test
    @DisplayName("an unreadable stored key is dropped with a warning rather than making the budget unloadable")
    void unreadableKeyIsDropped() {
        UserBudgetJpaEntity entity = mapper.toJpaEntity(
                budgetWith(ModelPreferences.ofContextMap(Map.of(TradingContext.BACKTESTING, ModelId.of("llama3.2")))));
        entity.modelPreferences = new java.util.HashMap<>(entity.modelPreferences);
        entity.modelPreferences.put("HAND_EDITED_NONSENSE", "whatever");

        ModelPreferences loaded = mapper.toDomain(entity).getModelPreferences();

        assertThat(loaded.getFor(TradingContext.BACKTESTING)).isPresent();
        assertThat(loaded.preferences()).hasSize(1);
    }

    @Test
    @DisplayName("an unknown effort level is dropped, not stored as a level nobody defined")
    void unknownEffortIsDropped() {
        UserBudgetJpaEntity entity = mapper.toJpaEntity(
                budgetWith(ModelPreferences.ofContextMap(Map.of(TradingContext.BACKTESTING, ModelId.of("llama3.2")))));
        entity.modelPreferences = new java.util.HashMap<>(entity.modelPreferences);
        entity.modelPreferences.put("BACKTESTING#effort", "ludicrous");

        ModelPreferences loaded = mapper.toDomain(entity).getModelPreferences();

        assertThat(loaded.effortFor(ModelPreferenceKey.global(TradingContext.BACKTESTING))).isEmpty();
    }

    @Test
    @DisplayName("no preferences maps to no JSON, and back")
    void emptyRoundTrips() {
        UserBudgetJpaEntity entity = mapper.toJpaEntity(budgetWith(ModelPreferences.EMPTY));

        assertThat(mapper.toDomain(entity).getModelPreferences().isEmpty()).isTrue();
    }
}
