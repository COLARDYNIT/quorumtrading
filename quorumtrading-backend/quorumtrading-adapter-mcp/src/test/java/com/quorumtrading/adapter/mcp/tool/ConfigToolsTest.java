package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.port.out.SystemModelDefaultsPort;
import com.quorumtrading.application.usecase.GetAvailableModelsUseCase;
import com.quorumtrading.application.usecase.UpdateModelPreferencesUseCase;
import com.quorumtrading.model.service.PlatformMarkup;
import com.quorumtrading.model.valueobject.*;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class ConfigToolsTest {

    @BeforeEach
    void configureMarkup() {
        // Pricing assertions below expect billed = raw * 1.50
        PlatformMarkup.configure(new BigDecimal("1.50"));
    }

    @AfterEach
    void resetMarkup() {
        PlatformMarkup.reset();
    }

    @Inject
    ConfigTools configTools;

    @InjectMock
    UpdateModelPreferencesUseCase updateModelPreferencesUseCase;

    @InjectMock
    GetAvailableModelsUseCase getAvailableModelsUseCase;

    @InjectMock
    SystemModelDefaultsPort systemModelDefaultsPort;

    @InjectMock
    com.quorumtrading.application.usecase.GetEffectiveModelsUseCase getEffectiveModelsUseCase;

    @InjectMock
    McpUserContext mcpUserContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID USER_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Test
    void getModelPreferences_returnsAllContextsWithUserOverrideAndDefaults() throws Exception {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));

        // The tool renders the EFFECTIVE-model read model rather than re-deriving an answer from
        // config plus the DB, so it can no longer disagree with the settings page or llm_decisions.
        when(getEffectiveModelsUseCase.execute(any(UserId.class))).thenReturn(
            java.util.Arrays.stream(TradingContext.values())
                .map(context -> new com.quorumtrading.application.usecase.GetEffectiveModelsUseCase.EffectiveModel(
                    context,
                    context == TradingContext.RESEARCH
                        ? com.quorumtrading.model.valueobject.ModelResolution.served(
                            "qwen/qwen3-8b",
                            com.quorumtrading.model.valueobject.ResolutionTier.USER_GLOBAL, null)
                        : com.quorumtrading.model.valueobject.ModelResolution.unresolved(null)))
                .toList());
        when(systemModelDefaultsPort.getAllDefaults()).thenReturn(Map.of(
            TradingContext.RESEARCH, "qwen/qwen3-next-80b-a3b-instruct"
        ));

        ToolResponse response = configTools.getModelPreferences();

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.isArray()).isTrue();
        assertThat(json.size()).isEqualTo(TradingContext.values().length);

        // Find the RESEARCH entry
        JsonNode researchEntry = findByContext(json, "RESEARCH");
        assertThat(researchEntry).isNotNull();
        assertThat(researchEntry.get("selectedModel").asText()).isEqualTo("qwen/qwen3-8b");
        assertThat(researchEntry.get("source").asText()).isEqualTo("user_override");
        assertThat(researchEntry.get("systemDefault").asText()).isEqualTo("qwen/qwen3-next-80b-a3b-instruct");
        assertThat(researchEntry.get("contextDescription").asText()).isNotBlank();

        // Find LIVE_TRADING entry (no user override, no system default — null fields omitted)
        JsonNode liveEntry = findByContext(json, "LIVE_TRADING");
        assertThat(liveEntry).isNotNull();
        assertThat(liveEntry.has("selectedModel")).isFalse();
        // Nothing resolved at any tier reads as 'unresolved' rather than being dressed up as a
        // system default that does not exist.
        assertThat(liveEntry.get("source").asText()).isEqualTo("unresolved");
        assertThat(liveEntry.has("systemDefault")).isFalse();
    }

    @Test
    void getModelPreferences_returnsAuthErrorWhenNoToken() {
        when(mcpUserContext.getUserId()).thenThrow(new SecurityException("Missing or invalid Authorization header"));

        ToolResponse response = configTools.getModelPreferences();

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Authentication required");
        assertThat(errorText).contains("quorumtrading.com/settings/api-tokens");
    }

    @Test
    void getAvailableModels_returnsFilteredModelsForContextWithTiers() throws Exception {
        LLMModelInfo model = LLMModelInfo.of(
            "anthropic/claude-sonnet-4-20250514",
            "Claude Sonnet 4",
            LLMPricing.of(new BigDecimal("0.000003"), new BigDecimal("0.000015"))
        );
        var evaluation = new com.quorumtrading.model.valueobject.ModelEvaluation(
            com.quorumtrading.model.enums.ModelTier.EXPERIMENTAL, "Untested model"
        );
        var categorized = new com.quorumtrading.model.valueobject.CategorizedModel(model, evaluation);
        when(getAvailableModelsUseCase.executeWithTiers(TradingContext.RESEARCH)).thenReturn(List.of(categorized));

        ToolResponse response = configTools.getAvailableModels(Optional.of("RESEARCH"), Optional.empty(), Optional.empty());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.has("items")).isTrue();
        assertThat(json.get("count").asInt()).isEqualTo(1);

        JsonNode item = json.get("items").get(0);
        assertThat(item.get("id").asText()).isEqualTo("anthropic/claude-sonnet-4-20250514");
        assertThat(item.get("name").asText()).isEqualTo("Claude Sonnet 4");
        assertThat(item.get("provider").asText()).isEqualTo("anthropic");
        assertThat(item.get("promptCostPerMillion").asText()).isEqualTo("4.5000");
        assertThat(item.get("completionCostPerMillion").asText()).isEqualTo("22.5000");
        assertThat(item.get("isFree").asBoolean()).isFalse();
        assertThat(item.get("supportsWebSearch").asBoolean()).isFalse();
        assertThat(item.get("tier").asText()).isEqualTo("EXPERIMENTAL");
        assertThat(item.get("tierReason").asText()).isEqualTo("Untested model");
    }

    @Test
    void getAvailableModels_returnsAllModelsWhenNoContext() throws Exception {
        LLMModelInfo model1 = LLMModelInfo.of("openai/gpt-4o", "GPT-4o", LLMPricing.FREE);
        LLMModelInfo model2 = LLMModelInfo.of("anthropic/claude-sonnet-4-20250514", "Claude Sonnet 4", LLMPricing.FREE);
        when(getAvailableModelsUseCase.execute()).thenReturn(List.of(model1, model2));

        ToolResponse response = configTools.getAvailableModels(Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.has("items")).isTrue();
        assertThat(json.get("count").asInt()).isEqualTo(2);

        // Without context, tier/tierReason should be null (omitted by NON_NULL)
        JsonNode item = json.get("items").get(0);
        assertThat(item.get("name").asText()).isEqualTo("GPT-4o");
        assertThat(item.has("tier")).isFalse();
        assertThat(item.has("tierReason")).isFalse();
    }

    @Test
    void getAvailableModels_returnsAuthErrorWhenNoToken() {
        when(getAvailableModelsUseCase.execute()).thenThrow(new SecurityException("Missing or invalid Authorization header"));

        ToolResponse response = configTools.getAvailableModels(Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Authentication required");
    }

    @Test
    void updateModelPreferences_updatesAndReturnsPreferences() throws Exception {
        UserId userId = new UserId(USER_ID);
        when(mcpUserContext.getUserId()).thenReturn(userId);

        ModelPreferences updatedPrefs = ModelPreferences.ofContextMap(Map.of(
            TradingContext.LIVE_TRADING, ModelId.of("anthropic/claude-sonnet-4.6")
        ));
        when(updateModelPreferencesUseCase.execute(eq(userId), any(Map.class), any(Map.class))).thenReturn(updatedPrefs);
        when(updateModelPreferencesUseCase.getPreferences(userId)).thenReturn(updatedPrefs);
        when(systemModelDefaultsPort.getAllDefaults()).thenReturn(Map.of());

        ToolResponse response = configTools.updateModelPreferences(
            Optional.of("anthropic/claude-sonnet-4.6"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        assertThat(response.isError()).isFalse();
        verify(updateModelPreferencesUseCase).execute(eq(userId), any(Map.class), any(Map.class));
    }

    @Test
    void updateModelPreferences_rejectsEmptyUpdate() {
        ToolResponse response = configTools.updateModelPreferences(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("No preferences provided");
    }

    @Test
    void updateModelPreferences_returnsErrorForInvalidModel() {
        UserId userId = new UserId(USER_ID);
        when(mcpUserContext.getUserId()).thenReturn(userId);
        when(updateModelPreferencesUseCase.execute(eq(userId), any(Map.class), any(Map.class)))
            .thenThrow(new IllegalArgumentException("Model not found: fake/model"));

        ToolResponse response = configTools.updateModelPreferences(
            Optional.of("fake/model"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Model not found");
        assertThat(errorText).contains("fake/model");
    }

    @Test
    void getAvailableModels_respectsLimitParam() throws Exception {
        LLMModelInfo m1 = LLMModelInfo.of("openai/gpt-4o", "GPT-4o", LLMPricing.FREE);
        LLMModelInfo m2 = LLMModelInfo.of("anthropic/claude-sonnet-4-20250514", "Claude Sonnet 4", LLMPricing.FREE);
        LLMModelInfo m3 = LLMModelInfo.of("google/gemini-2.0-flash", "Gemini Flash", LLMPricing.FREE);
        when(getAvailableModelsUseCase.execute()).thenReturn(List.of(m1, m2, m3));

        ToolResponse response = configTools.getAvailableModels(Optional.empty(), Optional.of(2), Optional.empty());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(2);
        assertThat(json.get("items").size()).isEqualTo(2);
    }

    @Test
    void getAvailableModels_filtersbyProvider() throws Exception {
        LLMModelInfo m1 = LLMModelInfo.of("openai/gpt-4o", "GPT-4o", LLMPricing.FREE);
        LLMModelInfo m2 = LLMModelInfo.of("anthropic/claude-sonnet-4-20250514", "Claude Sonnet 4", LLMPricing.FREE);
        LLMModelInfo m3 = LLMModelInfo.of("anthropic/claude-haiku-3.5", "Claude Haiku 3.5", LLMPricing.FREE);
        when(getAvailableModelsUseCase.execute()).thenReturn(List.of(m1, m2, m3));

        ToolResponse response = configTools.getAvailableModels(Optional.empty(), Optional.empty(), Optional.of("anthropic"));

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(2);
        JsonNode items = json.get("items");
        assertThat(items.get(0).get("provider").asText()).isEqualTo("anthropic");
        assertThat(items.get(1).get("provider").asText()).isEqualTo("anthropic");
    }

    @Test
    void getAvailableModels_providerFilterIsCaseInsensitive() throws Exception {
        LLMModelInfo m1 = LLMModelInfo.of("openai/gpt-4o", "GPT-4o", LLMPricing.FREE);
        LLMModelInfo m2 = LLMModelInfo.of("anthropic/claude-sonnet-4-20250514", "Claude Sonnet 4", LLMPricing.FREE);
        when(getAvailableModelsUseCase.execute()).thenReturn(List.of(m1, m2));

        ToolResponse response = configTools.getAvailableModels(Optional.empty(), Optional.empty(), Optional.of("OPENAI"));

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(1);
        assertThat(json.get("items").get(0).get("provider").asText()).isEqualTo("openai");
    }

    private JsonNode findByContext(JsonNode array, String context) {
        for (JsonNode node : array) {
            if (context.equals(node.get("context").asText())) {
                return node;
            }
        }
        return null;
    }
}
