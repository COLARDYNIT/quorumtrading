package com.quorumtrading.adapter.llm.router;

import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.LlmProviderAvailabilityPort;
import com.quorumtrading.model.enums.LlmProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Answers per-provider availability for the LLM selection flow by probing the same {@link LLMPort}
 * beans the router discovers. A provider that is not deployed/enabled on this instance (e.g.
 * local-llm gated off by {@code quorumtrading.llm.local.enabled}) simply reports unavailable.
 */
@ApplicationScoped
public class LlmProviderAvailabilityAdapter implements LlmProviderAvailabilityPort {

    private static final Map<LlmProvider, String> PROVIDER_BEAN_NAMES = Map.of(
            LlmProvider.LOCAL, "local-llm",
            LlmProvider.LOCAL_HTTP, "local-http",
            LlmProvider.OPENROUTER, "openrouter");

    @Inject
    @Any
    Instance<LLMPort> allProviders;

    @Override
    public boolean isAvailable(LlmProvider provider) {
        String beanName = PROVIDER_BEAN_NAMES.get(provider);
        if (beanName == null) {
            return false;
        }
        for (LLMPort candidate : allProviders) {
            if (candidate instanceof LLMRouterAdapter) {
                continue;
            }
            if (beanName.equals(candidate.getProviderName())) {
                return candidate.isAvailable();
            }
        }
        return false;
    }
}
