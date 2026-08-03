package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.model.enums.ModelTier;
import com.quorumtrading.model.service.PlatformMarkup;
import com.quorumtrading.model.valueobject.CategorizedModel;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.ModelEvaluation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LLMModelResponseTest {

    // $3/M prompt, $15/M completion raw
    private static final LLMModelInfo MODEL = LLMModelInfo.of(
        "anthropic/claude-sonnet-4-20250514",
        "Claude Sonnet 4",
        LLMPricing.of(new BigDecimal("0.000003"), new BigDecimal("0.000015"))
    );

    @BeforeEach
    void configureMarkup() {
        PlatformMarkup.configure(new BigDecimal("1.50"));
    }

    @AfterEach
    void resetMarkup() {
        PlatformMarkup.reset();
    }

    @Test
    void fromDomainAppliesThePlatformMarkupToPrices() {
        LLMModelResponse response = LLMModelResponse.fromDomain(MODEL);

        assertThat(response.id()).isEqualTo("anthropic/claude-sonnet-4-20250514");
        assertThat(response.promptCostPerMillion()).isEqualByComparingTo(new BigDecimal("4.5000"));
        assertThat(response.completionCostPerMillion()).isEqualByComparingTo(new BigDecimal("22.5000"));
        assertThat(response.tier()).isNull();
    }

    @Test
    void fromCategorizedAppliesThePlatformMarkupToPrices() {
        CategorizedModel categorized = new CategorizedModel(
            MODEL, new ModelEvaluation(ModelTier.EXPERIMENTAL, "Untested model"));

        LLMModelResponse response = LLMModelResponse.fromCategorized(categorized);

        assertThat(response.promptCostPerMillion()).isEqualByComparingTo(new BigDecimal("4.5000"));
        assertThat(response.completionCostPerMillion()).isEqualByComparingTo(new BigDecimal("22.5000"));
        assertThat(response.tier()).isEqualTo("EXPERIMENTAL");
        assertThat(response.tierReason()).isEqualTo("Untested model");
    }

    @Test
    void unconfiguredInstallShowsTheRawPrice() {
        PlatformMarkup.reset();

        LLMModelResponse response = LLMModelResponse.fromDomain(MODEL);

        assertThat(response.promptCostPerMillion()).isEqualByComparingTo(new BigDecimal("3.0000"));
        assertThat(response.completionCostPerMillion()).isEqualByComparingTo(new BigDecimal("15.0000"));
    }
}
