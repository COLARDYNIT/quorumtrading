package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.model.enums.ModelTier;
import com.quorumtrading.model.service.ModelFilter;
import com.quorumtrading.model.valueobject.CategorizedModel;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.ModelEvaluation;
import com.quorumtrading.model.valueobject.TradingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAvailableModelsUseCaseTest {

    @Mock
    private LLMPort llmPort;

    @Mock
    private ModelFilter modelFilter;

    private GetAvailableModelsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetAvailableModelsUseCase(llmPort, modelFilter);
    }

    @Test
    void executeWithTiers_filtersToRequestedTiers() {
        var models = List.of(
            createModel("recommended-model", "Anthropic"),
            createModel("proven-model", "OpenAI"),
            createModel("budget-model", "Meta")
        );

        when(llmPort.supportsModelListing()).thenReturn(true);
        when(llmPort.getAvailableModels()).thenReturn(models);
        when(modelFilter.filterForContext(any(), any())).thenReturn(models);
        when(modelFilter.evaluateModel(models.get(0), TradingContext.CHAT))
            .thenReturn(new ModelEvaluation(ModelTier.RECOMMENDED, "top pick"));
        when(modelFilter.evaluateModel(models.get(1), TradingContext.CHAT))
            .thenReturn(new ModelEvaluation(ModelTier.PROVEN, "solid choice"));
        when(modelFilter.evaluateModel(models.get(2), TradingContext.CHAT))
            .thenReturn(new ModelEvaluation(ModelTier.BUDGET, "cheap"));

        Set<ModelTier> tiers = EnumSet.of(ModelTier.RECOMMENDED, ModelTier.PROVEN);
        List<CategorizedModel> result = useCase.executeWithTiers(TradingContext.CHAT, tiers);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(cm -> cm.evaluation().tier())
            .containsExactly(ModelTier.RECOMMENDED, ModelTier.PROVEN);
    }

    @Test
    void executeWithTiers_nullTiersReturnsAll() {
        var models = List.of(
            createModel("model-a", "Anthropic"),
            createModel("model-b", "OpenAI")
        );

        when(llmPort.supportsModelListing()).thenReturn(true);
        when(llmPort.getAvailableModels()).thenReturn(models);
        when(modelFilter.filterForContext(any(), any())).thenReturn(models);
        when(modelFilter.evaluateModel(models.get(0), TradingContext.CHAT))
            .thenReturn(new ModelEvaluation(ModelTier.RECOMMENDED, "top pick"));
        when(modelFilter.evaluateModel(models.get(1), TradingContext.CHAT))
            .thenReturn(new ModelEvaluation(ModelTier.BUDGET, "cheap"));

        List<CategorizedModel> result = useCase.executeWithTiers(TradingContext.CHAT, null);

        assertThat(result).hasSize(2);
    }

    @Test
    void executeWithTiers_emptyTiersReturnsAll() {
        var models = List.of(
            createModel("model-a", "Anthropic"),
            createModel("model-b", "OpenAI")
        );

        when(llmPort.supportsModelListing()).thenReturn(true);
        when(llmPort.getAvailableModels()).thenReturn(models);
        when(modelFilter.filterForContext(any(), any())).thenReturn(models);
        when(modelFilter.evaluateModel(models.get(0), TradingContext.CHAT))
            .thenReturn(new ModelEvaluation(ModelTier.RECOMMENDED, "top pick"));
        when(modelFilter.evaluateModel(models.get(1), TradingContext.CHAT))
            .thenReturn(new ModelEvaluation(ModelTier.BUDGET, "cheap"));

        List<CategorizedModel> result = useCase.executeWithTiers(TradingContext.CHAT, Set.of());

        assertThat(result).hasSize(2);
    }

    private LLMModelInfo createModel(String id, String provider) {
        return new LLMModelInfo(
            id,
            id,
            "Test model",
            128000,
            4096,
            new LLMPricing(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO),
            List.of("text"),
            provider,
            Instant.now(),
            false,
            null
        );
    }
}
