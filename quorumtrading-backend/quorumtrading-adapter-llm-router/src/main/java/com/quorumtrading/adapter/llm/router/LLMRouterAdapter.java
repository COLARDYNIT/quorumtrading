package com.quorumtrading.adapter.llm.router;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryException;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.orchestration.agents.LlmProviderNotSelectedException;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort;
import com.quorumtrading.application.port.out.LlmTelemetryPort.ProviderTelemetry;
import com.quorumtrading.model.enums.LlmProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import io.smallrye.common.annotation.Identifier;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import com.quorumtrading.model.valueobject.LLMModelInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * LLM Router that provides intelligent routing and failover across multiple LLM providers.
 * Discovers providers via CDI, manages circuit breakers, and tracks metrics — readable
 * through {@link com.quorumtrading.application.port.out.LlmTelemetryPort}, so engine
 * slowness is diagnosable before a backtest burns itself out on timeouts.
 */
@ApplicationScoped
public class LLMRouterAdapter implements LLMPort, com.quorumtrading.application.port.out.LlmTelemetryPort,
        com.quorumtrading.application.port.out.ActiveLlmEnginePort {

    private static final Logger LOG = Logger.getLogger(LLMRouterAdapter.class);

    @Inject
    @jakarta.enterprise.inject.Any
    Instance<LLMPort> allProviders;

    @ConfigProperty(name = "quorumtrading.llm.router.provider-order")
    List<String> globalProviderOrder;

    @Inject
    MetricsLogger metricsLogger;

    // LLM executor instance - may be empty if not configured in ApplicationConfig
    // Allows this adapter to work in test environments without the full bootstrap configuration
    @Inject
    @Identifier("llm-executor")
    Instance<ManagedExecutor> llmExecutorInstance;

    @ConfigProperty(name = "quorumtrading.llm.call-timeout-seconds", defaultValue = "90")
    long callTimeoutSeconds;

    /**
     * Self-host edition: when {@code true}, the static provider-order is replaced by an explicit,
     * runtime-mutable user selection — and an LLM call without a selection fails fast with an
     * actionable {@link LlmProviderNotSelectedException} instead of silently picking a provider.
     * Default {@code false}: hosted SaaS behavior is untouched.
     */
    @ConfigProperty(name = "quorumtrading.llm.selection.enabled", defaultValue = "false")
    boolean selectionEnabled;

    /** Where a human can choose a provider — embedded in the not-selected error message. */
    @ConfigProperty(name = "quorumtrading.llm.setup-url",
            defaultValue = "http://localhost:8085/api/v1/llm/setup")
    String setupUrl;

    /** The persisted runtime selection; unsatisfied in isolated adapter tests. */
    @Inject
    Instance<LlmProviderPreferencePort> preferencePort;

    /** Maps the user-facing provider choice to the adapter bean's {@code getProviderName()}. */
    private static final Map<LlmProvider, String> PROVIDER_BEAN_NAMES = Map.of(
            LlmProvider.LOCAL, "local-llm",
            LlmProvider.LOCAL_HTTP, "local-http",
            LlmProvider.OPENROUTER, "openrouter");

    private final Map<String, LLMPort> providersByName = new ConcurrentHashMap<>();
    private final Map<String, ProviderMetrics> metricsMap = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        // Discover and index providers (excluding self)
        for (LLMPort provider : allProviders) {
            // Skip self by checking instance type directly (avoid calling methods on our own proxy)
            if (provider instanceof LLMRouterAdapter) {
                continue;
            }

            String name = provider.getProviderName();
            providersByName.put(name, provider);
            metricsMap.put(name, new ProviderMetrics(name));
            LOG.infof("Registered LLM provider: %s", name);
        }

        // Inject metrics map into logger
        metricsLogger.setMetricsMap(metricsMap);

        LOG.infof("Router initialized with %d providers in order: %s",
            providersByName.size(), globalProviderOrder);
    }

    @Override
    public CompletableFuture<LLMQueryResult> queryWithUsageAsync(String prompt, LLMConfig config) {
        // Whichever provider is mid-call when the router's cap fires — so a timeout is
        // attributed to the engine that was actually answering, not lost entirely.
        java.util.concurrent.atomic.AtomicReference<ProviderMetrics> activeProvider =
                new java.util.concurrent.atomic.AtomicReference<>();

        // If LLM executor is not available (e.g., in tests), fallback to synchronous execution
        if (llmExecutorInstance.isUnsatisfied()) {
            LOG.debug("LLM executor not available, using synchronous fallback");
            return CompletableFuture.supplyAsync(() -> queryWithFallback(prompt, config, activeProvider));
        }

        // Run the fallback chain on the dedicated LLM executor with timeout
        ManagedExecutor llmExecutor = llmExecutorInstance.get();
        return llmExecutor.supplyAsync(() -> queryWithFallback(prompt, config, activeProvider))
                .toCompletableFuture()
                .orTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    Throwable cause = ex instanceof java.util.concurrent.CompletionException ? ex.getCause() : ex;
                    if (cause instanceof TimeoutException) {
                        LOG.errorf("LLM query timed out after %ds", callTimeoutSeconds);
                        ProviderMetrics active = activeProvider.get();
                        if (active != null) {
                            active.recordTimeout("router per-call cap (" + callTimeoutSeconds
                                    + "s, quorumtrading.llm.call-timeout-seconds) fired while this provider was answering");
                        }
                        throw new LLMQueryException("LLM query timeout after " + callTimeoutSeconds + "s", cause, true);
                    }
                    if (cause instanceof LLMQueryException) {
                        throw (LLMQueryException) cause;
                    }
                    LOG.error("LLM query execution failed", cause);
                    throw new LLMQueryException("LLM query execution failed", cause);
                });
    }

    @Override
    public LLMQueryResult queryWithUsage(String prompt, LLMConfig config) {
        try {
            return queryWithUsageAsync(prompt, config).join();
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof LLMQueryException) {
                throw (LLMQueryException) e.getCause();
            }
            throw new LLMQueryException("LLM query failed", e.getCause());
        }
    }

    /**
     * Internal method that tries providers in fallback order.
     * Executes in the dedicated LLM thread pool.
     */
    private LLMQueryResult queryWithFallback(String prompt, LLMConfig config,
            java.util.concurrent.atomic.AtomicReference<ProviderMetrics> activeProvider) {
        List<LLMPort> orderedProviders = getOrderedProviders(config);

        if (orderedProviders.isEmpty()) {
            throw new LLMQueryException("No LLM providers available");
        }

        for (LLMPort provider : orderedProviders) {
            String providerName = provider.getProviderName();
            ProviderMetrics metrics = metricsMap.get(providerName);
            long startTime = System.currentTimeMillis();

            if (metrics != null) {
                activeProvider.set(metrics);
                metrics.beginCall();
            }
            try {
                LOG.debugf("Trying provider: %s", providerName);
                LLMQueryResult result = provider.queryWithUsage(prompt, config);

                // Success - record metrics and return
                long elapsed = System.currentTimeMillis() - startTime;
                if (metrics != null) {
                    metrics.recordInvocation(elapsed, true);
                }
                LOG.infof("Provider %s succeeded in %dms", providerName, elapsed);

                return result;

            } catch (org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException e) {
                // Circuit breaker open - skip to next provider
                if (metrics != null) {
                    metrics.recordCircuitOpen();
                }
                LOG.infof("Provider %s circuit open, trying next", providerName);
                continue;

            } catch (Exception e) {
                // Any other error - record metrics and try next
                long elapsed = System.currentTimeMillis() - startTime;
                if (metrics != null) {
                    metrics.recordFailure(elapsed, e.getMessage());
                }
                LOG.warnf("Provider %s failed after %dms: %s", providerName, elapsed, e.getMessage());
                continue;
            } finally {
                if (metrics != null) {
                    metrics.endCall();
                }
            }
        }

        throw new LLMQueryException("All LLM providers failed or unavailable");
    }

    /**
     * Build ordered list of providers based on global config and LLMConfig hints.
     *
     * <p>With runtime selection enabled (self-host edition) this is exactly one provider — the
     * user's persisted choice — and never falls back to another: a user who picked the free local
     * engine must not silently start paying for OpenRouter because their CLI hiccuped.</p>
     */
    private List<LLMPort> getOrderedProviders(LLMConfig config) {
        if (selectionEnabled) {
            return List.of(resolveSelectedProvider());
        }

        List<String> order = new ArrayList<>(globalProviderOrder);

        // If config specifies preferred provider, move it to front
        if (config.preferredProvider() != null) {
            String preferred = config.preferredProvider();
            if (order.contains(preferred)) {
                order.remove(preferred);
                order.add(0, preferred);
                LOG.debugf("Moved preferred provider %s to front", preferred);
            }
        }

        // Convert names to provider instances, filter unavailable
        return order.stream()
            .map(providersByName::get)
            .filter(Objects::nonNull)
            .filter(LLMPort::isAvailable)
            .collect(Collectors.toList());
    }

    /**
     * Strict-selection resolution: the persisted choice, validated as currently serviceable.
     *
     * @throws LlmProviderNotSelectedException when no provider has been chosen yet — the message
     *         tells the user how to choose and what is available right now
     * @throws LLMQueryException when the chosen provider exists but cannot serve calls
     */
    private LLMPort resolveSelectedProvider() {
        LlmProvider selected = preferencePort.isResolvable()
                ? preferencePort.get().current()
                        .map(LlmProviderPreferencePort.LlmProviderPreference::provider)
                        .orElse(null)
                : null;

        if (selected == null) {
            throw new LlmProviderNotSelectedException(
                    "No LLM provider selected. Choose one before running AI features: open "
                    + setupUrl + " or use the choose_llm_provider MCP tool. "
                    + "Currently " + availabilitySummary());
        }

        LLMPort provider = providersByName.get(PROVIDER_BEAN_NAMES.get(selected));
        if (provider == null) {
            throw new LLMQueryException("Selected LLM provider " + selected
                    + " is not deployed on this instance. Re-select at " + setupUrl + ".");
        }
        if (!provider.isAvailable()) {
            String reason = switch (selected) {
                case LOCAL -> "no supported CLI tool found on this machine";
                case LOCAL_HTTP -> "no local model server responded at the configured URL";
                case OPENROUTER -> "no usable API key configured";
            };
            throw new LLMQueryException("Selected LLM provider " + selected
                    + " cannot serve calls right now (" + reason
                    + "). Fix it or choose another provider at " + setupUrl + ".");
        }
        return provider;
    }

    // ========== Active engine (ActiveLlmEnginePort) ==========

    /**
     * The engine a call issued right now would land on.
     *
     * <p>With runtime selection this is the persisted choice — no availability filter, because a
     * momentarily unreachable Ollama does not change <em>which</em> engine the user picked, and
     * silently reporting a different one is how a model default ends up scoped to an engine that
     * will never run. Without selection it is the first configured provider that is deployed,
     * preferring an available one.</p>
     */
    @Override
    public Optional<LlmProvider> activeEngine() {
        if (selectionEnabled) {
            if (!preferencePort.isResolvable()) {
                return Optional.empty();
            }
            try {
                return preferencePort.get().current()
                        .map(LlmProviderPreferencePort.LlmProviderPreference::provider);
            } catch (RuntimeException e) {
                // The selection lives in the database, and this is asked from contexts that may be
                // outside a transaction. "Cannot tell right now" is the honest answer; guessing a
                // provider here would scope model defaults to an engine nobody chose.
                LOG.debugf("Could not read the persisted LLM selection: %s", e.getMessage());
                return Optional.empty();
            }
        }

        Optional<LlmProvider> deployed = Optional.empty();
        for (String beanName : globalProviderOrder) {
            LLMPort provider = providersByName.get(beanName);
            if (provider == null) {
                continue;
            }
            Optional<LlmProvider> engine = engineForBeanName(beanName);
            if (engine.isEmpty()) {
                continue;
            }
            if (provider.isAvailable()) {
                return engine;
            }
            if (deployed.isEmpty()) {
                deployed = engine;
            }
        }
        return deployed;
    }

    private static Optional<LlmProvider> engineForBeanName(String beanName) {
        return PROVIDER_BEAN_NAMES.entrySet().stream()
                .filter(entry -> entry.getValue().equals(beanName))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private String availabilitySummary() {
        StringBuilder summary = new StringBuilder("available: ");
        boolean first = true;
        for (LlmProvider candidate : LlmProvider.values()) {
            LLMPort provider = providersByName.get(PROVIDER_BEAN_NAMES.get(candidate));
            boolean available = provider != null && provider.isAvailable();
            if (!first) {
                summary.append(", ");
            }
            summary.append(candidate.name().toLowerCase()).append('=').append(available ? "yes" : "no");
            first = false;
        }
        return summary.toString();
    }

    @Override
    public String getProviderName() {
        return "router";
    }

    @Override
    public boolean isAvailable() {
        return providersByName.values().stream().anyMatch(LLMPort::isAvailable);
    }

    // ========== Model Catalog Methods (delegated to first supporting provider) ==========

    @Override
    public boolean supportsModelListing() {
        return findModelListingProvider().isPresent();
    }

    @Override
    public List<LLMModelInfo> getAvailableModels() {
        return findModelListingProvider()
            .map(LLMPort::getAvailableModels)
            .orElse(List.of());
    }

    @Override
    public Optional<LLMModelInfo> getModelInfo(String modelId) {
        return findModelListingProvider()
            .flatMap(provider -> provider.getModelInfo(modelId));
    }

    @Override
    public void refreshModelCatalog() {
        findModelListingProvider().ifPresent(LLMPort::refreshModelCatalog);
    }

    /**
     * The authoritative catalogue's own answer.
     *
     * <p>{@link #findModelListingProvider()} returns empty both when the selected provider has no
     * catalogue and when it could not produce one, so it cannot distinguish the two on its own.
     * With runtime selection the selected provider is asked directly — it knows which of its own
     * states it is in.</p>
     */
    @Override
    public com.quorumtrading.model.valueobject.ModelListingCapability modelListingCapability() {
        if (selectionEnabled) {
            try {
                return resolveSelectedProvider().modelListingCapability();
            } catch (RuntimeException e) {
                LOG.debugf("Model-listing capability: no usable selection (%s)", e.getMessage());
                return com.quorumtrading.model.valueobject.ModelListingCapability.UNAVAILABLE;
            }
        }
        return findModelListingProvider()
                .map(LLMPort::modelListingCapability)
                .orElse(com.quorumtrading.model.valueobject.ModelListingCapability.NOT_SUPPORTED);
    }

    // ========== Telemetry (LlmTelemetryPort) ==========

    @Override
    public Optional<ProviderTelemetry> telemetryFor(LlmProvider provider) {
        ProviderMetrics metrics = metricsMap.get(PROVIDER_BEAN_NAMES.get(provider));
        return metrics == null ? Optional.empty() : Optional.of(toTelemetry(provider, metrics));
    }

    @Override
    public List<ProviderTelemetry> telemetrySnapshot() {
        List<ProviderTelemetry> snapshot = new ArrayList<>();
        for (LlmProvider provider : LlmProvider.values()) {
            telemetryFor(provider).ifPresent(snapshot::add);
        }
        return snapshot;
    }

    private static ProviderTelemetry toTelemetry(LlmProvider provider, ProviderMetrics metrics) {
        long invocations = metrics.getTotalInvocations();
        return new ProviderTelemetry(
                provider,
                invocations,
                metrics.getSuccessCount(),
                metrics.getErrorCount(),
                metrics.getTimeoutCount(),
                invocations > 0 ? Math.round(metrics.getAverageResponseTimeMs()) : null,
                metrics.getLastResponseTimeMs(),
                metrics.getInFlight(),
                metrics.getLastError(),
                metrics.getLastErrorAt(),
                metrics.getLastSuccessAt());
    }

    /**
     * Find the provider whose model catalogue is authoritative for this deployment.
     *
     * <p>With runtime selection enabled, that is the <em>selected</em> provider: its calls are
     * the only ones that will ever run, so validating a strategy's {@code llm_model} against any
     * other catalogue is answering a question nobody asked — concretely, a local deployment
     * pinning a real Ollama model was rejected against OpenRouter's list. A selected provider
     * with no catalogue (the CLI engine) yields empty, which callers treat as "cannot validate,
     * skip validation". Without selection (hosted), OpenRouter's catalogue remains preferred.</p>
     */
    private Optional<LLMPort> findModelListingProvider() {
        if (selectionEnabled) {
            try {
                LLMPort selected = resolveSelectedProvider();
                return selected.supportsModelListing() ? Optional.of(selected) : Optional.empty();
            } catch (RuntimeException e) {
                // No selection yet (or unavailable) — fall through to the static preference
                LOG.debugf("Model-listing provider: no usable selection (%s), falling back", e.getMessage());
            }
        }

        // Try openrouter first (most comprehensive catalog)
        LLMPort openrouter = providersByName.get("openrouter");
        if (openrouter != null && openrouter.supportsModelListing()) {
            return Optional.of(openrouter);
        }

        // Fallback to any provider that supports model listing
        return providersByName.values().stream()
            .filter(LLMPort::supportsModelListing)
            .findFirst();
    }
}
