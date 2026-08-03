package com.quorumtrading.adapter.llm.openrouter;

import com.quorumtrading.model.valueobject.ModelListingCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An adapter must not claim it can enumerate models while enumerating none.
 *
 * <p>This is the defect that broke the full-stack e2e run. OpenRouter's {@code supportsModelListing()}
 * is unconditionally {@code true} — correct, it does have a catalogue by design — but
 * {@code getAvailableModels()} returns an empty list whenever the key is missing or unusable. The
 * inherited default mapped that to ENUMERABLE, so on a keyless install the router asked the one
 * deployed catalogue provider whether a model existed, got "no" from an empty list, and concluded
 * that nothing at all was servable. Every consensus call then failed.</p>
 *
 * <p>An install with no OpenRouter key still has this bean deployed, which is exactly why it is the
 * provider the router falls back to for catalogue questions — so getting this wrong is not an edge
 * case, it is the default self-host shape.</p>
 */
@DisplayName("OpenRouterAdapter — enumerable only when it can actually enumerate")
class OpenRouterListingCapabilityTest {

    /** An adapter whose availability is fixed, standing in for "key configured" or not. */
    private static OpenRouterAdapter adapter(boolean available) {
        return new OpenRouterAdapter() {
            @Override
            public boolean isAvailable() {
                return available;
            }
        };
    }

    @Test
    @DisplayName("a usable key means the catalogue can be read")
    void usableKeyIsEnumerable() {
        assertThat(adapter(true).modelListingCapability()).isEqualTo(ModelListingCapability.ENUMERABLE);
    }

    @Test
    @DisplayName("no usable key is UNAVAILABLE, never ENUMERABLE-with-nothing")
    void keylessIsUnavailableNotEnumerable() {
        OpenRouterAdapter keyless = adapter(false);

        assertThat(keyless.getAvailableModels())
                .as("the empty list is what made the ENUMERABLE claim a lie")
                .isEmpty();
        assertThat(keyless.modelListingCapability())
                .as("UNAVAILABLE means 'cannot check right now', which callers handle; "
                        + "ENUMERABLE would mean 'checked, and nothing exists'")
                .isEqualTo(ModelListingCapability.UNAVAILABLE);
    }

    @Test
    @DisplayName("it still declares that it HAS a catalogue by design")
    void listingIsStillSupportedByDesign() {
        // supportsModelListing answers a question about capability, not about the current key.
        // Both answers matter and they are not the same question.
        assertThat(adapter(false).supportsModelListing()).isTrue();
    }
}
