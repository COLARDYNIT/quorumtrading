package com.quorumtrading.integration;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Routes the LLM through the canned {@code dummy} executor.
 *
 * <p>Consensus integration tests were tagged {@code @Tag("llm")} and excluded from CI wholesale,
 * because under {@code %test} the router reaches for the local {@code claude} CLI and then
 * OpenRouter — neither of which exists on a runner. That exclusion is why a foreign-key break in
 * one of them went unnoticed: the failure had nothing to do with a model, but the tag threw the
 * whole class away.</p>
 *
 * <p>These tests assert <em>wiring</em> — that a consensus round persists decisions for every
 * agent and links them correctly. A deterministic canned provider answers that question free,
 * offline and repeatably, so they can gate on every push. {@code @Tag("llm")} is then free to
 * mean what it should: "this asserts real frontier-model behaviour".</p>
 */
public class DummyLlmTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quorumtrading.llm.local.enabled", "true",
            "quorumtrading.llm.local.default-tool", "dummy",
            "quorumtrading.llm.local.tool.fallback", "dummy",
            "quorumtrading.llm.router.provider-order", "local-llm"
        );
    }
}
