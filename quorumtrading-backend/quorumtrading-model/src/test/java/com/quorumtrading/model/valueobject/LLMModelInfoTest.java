package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMModelInfo Value Object Tests")
class LLMModelInfoTest {

    @Test
    @DisplayName("should create model with all fields")
    void shouldCreateModelWithAllFields() {
        LLMPricing pricing = LLMPricing.of(
            new BigDecimal("0.000005"),
            new BigDecimal("0.000015")
        );
        Instant created = Instant.now();

        LLMModelInfo model = new LLMModelInfo(
            "openai/gpt-4o",
            "GPT-4o",
            "Latest multimodal model",
            128000,
            4096,
            pricing,
            List.of("text", "image"),
            "openai",
            created,
            false,
            null
        );

        assertEquals("openai/gpt-4o", model.id());
        assertEquals("GPT-4o", model.name());
        assertEquals("Latest multimodal model", model.description());
        assertEquals(128000, model.contextLength());
        assertEquals(4096, model.maxOutputTokens());
        assertEquals(pricing, model.pricing());
        assertEquals(List.of("text", "image"), model.modalities());
        assertEquals("openai", model.provider());
        assertEquals(created, model.createdAt());
    }

    @Test
    @DisplayName("should throw exception for null id")
    void shouldThrowExceptionForNullId() {
        assertThrows(IllegalArgumentException.class, () ->
            new LLMModelInfo(null, "Name", null, 0, 0, null, null, null, null, false, null)
        );
    }

    @Test
    @DisplayName("should throw exception for blank id")
    void shouldThrowExceptionForBlankId() {
        assertThrows(IllegalArgumentException.class, () ->
            new LLMModelInfo("  ", "Name", null, 0, 0, null, null, null, null, false, null)
        );
    }

    @Test
    @DisplayName("should use id as name when name is null")
    void shouldUseIdAsNameWhenNameIsNull() {
        LLMModelInfo model = new LLMModelInfo(
            "openai/gpt-4o", null, null, 0, 0, null, null, null, null, false, null
        );

        assertEquals("openai/gpt-4o", model.name());
    }

    @Test
    @DisplayName("should use id as name when name is blank")
    void shouldUseIdAsNameWhenNameIsBlank() {
        LLMModelInfo model = new LLMModelInfo(
            "openai/gpt-4o", "  ", null, 0, 0, null, null, null, null, false, null
        );

        assertEquals("openai/gpt-4o", model.name());
    }

    @Test
    @DisplayName("should default pricing to FREE when null")
    void shouldDefaultPricingToFreeWhenNull() {
        LLMModelInfo model = new LLMModelInfo(
            "model-id", "Name", null, 0, 0, null, null, null, null, false, null
        );

        assertEquals(LLMPricing.FREE, model.pricing());
    }

    @Test
    @DisplayName("should default modalities to text when null")
    void shouldDefaultModalitiesToTextWhenNull() {
        LLMModelInfo model = new LLMModelInfo(
            "model-id", "Name", null, 0, 0, null, null, null, null, false, null
        );

        assertEquals(List.of("text"), model.modalities());
    }

    @Test
    @DisplayName("should normalize negative context length to zero")
    void shouldNormalizeNegativeContextLengthToZero() {
        LLMModelInfo model = new LLMModelInfo(
            "model-id", "Name", null, -100, 0, null, null, null, null, false, null
        );

        assertEquals(0, model.contextLength());
    }

    @Test
    @DisplayName("should normalize negative max output tokens to zero")
    void shouldNormalizeNegativeMaxOutputTokensToZero() {
        LLMModelInfo model = new LLMModelInfo(
            "model-id", "Name", null, 0, -50, null, null, null, null, false, null
        );

        assertEquals(0, model.maxOutputTokens());
    }

    @Test
    @DisplayName("of() factory with minimal fields should work")
    void ofFactoryWithMinimalFieldsShouldWork() {
        LLMPricing pricing = LLMPricing.of(
            new BigDecimal("0.00001"),
            new BigDecimal("0.00003")
        );

        LLMModelInfo model = LLMModelInfo.of("openai/gpt-4o", "GPT-4o", pricing);

        assertEquals("openai/gpt-4o", model.id());
        assertEquals("GPT-4o", model.name());
        assertEquals(pricing, model.pricing());
        assertEquals("openai", model.provider()); // extracted from id
        assertNull(model.description());
        assertEquals(0, model.contextLength());
        assertEquals(List.of("text"), model.modalities());
    }

    @Test
    @DisplayName("of() factory with common fields should work")
    void ofFactoryWithCommonFieldsShouldWork() {
        LLMPricing pricing = LLMPricing.FREE;

        LLMModelInfo model = LLMModelInfo.of(
            "anthropic/claude-3.5-sonnet",
            "Claude 3.5 Sonnet",
            "Fast and capable model",
            200000,
            pricing,
            List.of("text", "image")
        );

        assertEquals("anthropic/claude-3.5-sonnet", model.id());
        assertEquals("Claude 3.5 Sonnet", model.name());
        assertEquals("Fast and capable model", model.description());
        assertEquals(200000, model.contextLength());
        assertEquals(pricing, model.pricing());
        assertEquals(List.of("text", "image"), model.modalities());
        assertEquals("anthropic", model.provider());
    }

    @Test
    @DisplayName("isFree() should delegate to pricing")
    void isFreeShouldDelegateToPricing() {
        LLMModelInfo freeModel = LLMModelInfo.of("model", "Model", LLMPricing.FREE);
        assertTrue(freeModel.isFree());

        LLMModelInfo paidModel = LLMModelInfo.of("model", "Model",
            LLMPricing.of(new BigDecimal("0.001"), new BigDecimal("0.002")));
        assertFalse(paidModel.isFree());
    }

    @Test
    @DisplayName("supportsModality() should check case-insensitively")
    void supportsModalityShouldCheckCaseInsensitively() {
        LLMModelInfo model = new LLMModelInfo(
            "model-id", "Name", null, 0, 0, null,
            List.of("text", "IMAGE", "Audio"), null, null, false, null
        );

        assertTrue(model.supportsModality("text"));
        assertTrue(model.supportsModality("TEXT"));
        assertTrue(model.supportsModality("image"));
        assertTrue(model.supportsModality("Image"));
        assertTrue(model.supportsModality("audio"));
        assertTrue(model.supportsModality("AUDIO"));
        assertFalse(model.supportsModality("video"));
    }

    @Test
    @DisplayName("supportsImages() should return true for image modality")
    void supportsImagesShouldReturnTrueForImageModality() {
        LLMModelInfo withImage = new LLMModelInfo(
            "model-id", "Name", null, 0, 0, null,
            List.of("text", "image"), null, null, false, null
        );
        assertTrue(withImage.supportsImages());

        LLMModelInfo withoutImage = new LLMModelInfo(
            "model-id", "Name", null, 0, 0, null,
            List.of("text"), null, null, false, null
        );
        assertFalse(withoutImage.supportsImages());
    }

    @Test
    @DisplayName("hasMinContextLength() should check context length")
    void hasMinContextLengthShouldCheckContextLength() {
        LLMModelInfo model = new LLMModelInfo(
            "model-id", "Name", null, 128000, 0, null, null, null, null, false, null
        );

        assertTrue(model.hasMinContextLength(100000));
        assertTrue(model.hasMinContextLength(128000));
        assertFalse(model.hasMinContextLength(200000));
    }

    @Test
    @DisplayName("shortName() should strip provider prefix")
    void shortNameShouldStripProviderPrefix() {
        LLMModelInfo model = LLMModelInfo.of(
            "openai/gpt-4o",
            "openai/gpt-4o",
            LLMPricing.FREE
        );

        assertEquals("gpt-4o", model.shortName());
    }

    @Test
    @DisplayName("shortName() should return full name when no prefix")
    void shortNameShouldReturnFullNameWhenNoPrefix() {
        LLMModelInfo model = LLMModelInfo.of(
            "gpt-4o",
            "GPT-4o",
            LLMPricing.FREE
        );

        assertEquals("GPT-4o", model.shortName());
    }

    @Test
    @DisplayName("provider extraction should work for id with slash")
    void providerExtractionShouldWorkForIdWithSlash() {
        LLMModelInfo model = LLMModelInfo.of("meta-llama/llama-3.3-70b", "Llama 3.3", LLMPricing.FREE);
        assertEquals("meta-llama", model.provider());
    }

    @Test
    @DisplayName("provider extraction should return null for id without slash")
    void providerExtractionShouldReturnNullForIdWithoutSlash() {
        LLMModelInfo model = LLMModelInfo.of("gpt-4o", "GPT-4o", LLMPricing.FREE);
        assertNull(model.provider());
    }

    @Test
    @DisplayName("supportsWebSearch() should return the constructor value")
    void supportsWebSearchShouldReturnConstructorValue() {
        LLMModelInfo withWebSearch = new LLMModelInfo(
            "perplexity/sonar-online", "Sonar Online", null, 128000, 0, null,
            List.of("text"), "perplexity", null, true, null
        );
        assertTrue(withWebSearch.supportsWebSearch());

        LLMModelInfo withoutWebSearch = new LLMModelInfo(
            "openai/gpt-4o", "GPT-4o", null, 128000, 0, null,
            List.of("text", "image"), "openai", null, false, null
        );
        assertFalse(withoutWebSearch.supportsWebSearch());
    }

    @Test
    @DisplayName("factory methods should default supportsWebSearch to false")
    void factoryMethodsShouldDefaultSupportsWebSearchToFalse() {
        LLMModelInfo model = LLMModelInfo.of("openai/gpt-4o", "GPT-4o", LLMPricing.FREE);
        assertFalse(model.supportsWebSearch());
    }

    @Test
    @DisplayName("parameterSizeB should store the model size in billions")
    void parameterSizeBShouldStoreModelSizeInBillions() {
        LLMModelInfo model = new LLMModelInfo(
            "meta-llama/llama-3.3-70b", "Llama 3.3 70B", null,
            128000, 0, null, null, "meta-llama", null, false, 70
        );

        assertEquals(70, model.parameterSizeB());
    }

    @Test
    @DisplayName("parameterSizeB should allow null for unknown size")
    void parameterSizeBShouldAllowNullForUnknownSize() {
        LLMModelInfo model = new LLMModelInfo(
            "openai/gpt-4o", "GPT-4o", null,
            128000, 0, null, null, "openai", null, false, null
        );

        assertNull(model.parameterSizeB());
    }

    @Test
    @DisplayName("factory methods should default parameterSizeB to null")
    void factoryMethodsShouldDefaultParameterSizeBToNull() {
        LLMModelInfo model = LLMModelInfo.of("openai/gpt-4o", "GPT-4o", LLMPricing.FREE);
        assertNull(model.parameterSizeB());
    }
}
