package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.LlmProviderAvailabilityPort;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort.LlmProviderPreference;
import com.quorumtrading.application.usecase.llm.GetLlmProviderStatusUseCase;
import com.quorumtrading.application.usecase.llm.LlmProviderStatus;
import com.quorumtrading.application.usecase.llm.SelectLlmProviderUseCase;
import com.quorumtrading.application.usecase.llm.UnservablePinReport;
import com.quorumtrading.model.enums.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Backs both LLM-provider selection input-ports ({@link GetLlmProviderStatusUseCase} /
 * {@link SelectLlmProviderUseCase}) with one POJO, wired in the bootstrap {@code ApplicationConfig}.
 *
 * <p>Selection rules: the chosen provider must be able to serve calls <em>right now</em> — a
 * missing CLI or unusable key is rejected with an actionable message rather than persisted and
 * discovered at first call. For OpenRouter, a key submitted with the selection is stored
 * (encrypted) <em>before</em> the availability check so the check sees it.</p>
 */
public class LlmProviderSelectionService implements GetLlmProviderStatusUseCase, SelectLlmProviderUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(LlmProviderSelectionService.class);

    private final LlmProviderPreferencePort preferencePort;
    private final LlmProviderAvailabilityPort availabilityPort;
    /** Nullable: telemetry is additive, and older wirings/tests run without it. */
    private final com.quorumtrading.application.port.out.LlmTelemetryPort telemetryPort;

    /**
     * Nullable: sweeps stored pins after a switch. Without it a switch is silent about what it
     * just broke, which under a fail-hard policy means finding out mid-backtest.
     */
    private final RevalidateStoredModelPinsService pinRevalidator;

    public LlmProviderSelectionService(
            LlmProviderPreferencePort preferencePort,
            LlmProviderAvailabilityPort availabilityPort) {
        this(preferencePort, availabilityPort, null, null);
    }

    public LlmProviderSelectionService(
            LlmProviderPreferencePort preferencePort,
            LlmProviderAvailabilityPort availabilityPort,
            com.quorumtrading.application.port.out.LlmTelemetryPort telemetryPort) {
        this(preferencePort, availabilityPort, telemetryPort, null);
    }

    public LlmProviderSelectionService(
            LlmProviderPreferencePort preferencePort,
            LlmProviderAvailabilityPort availabilityPort,
            com.quorumtrading.application.port.out.LlmTelemetryPort telemetryPort,
            RevalidateStoredModelPinsService pinRevalidator) {
        this.preferencePort = Objects.requireNonNull(preferencePort, "preferencePort cannot be null");
        this.availabilityPort = Objects.requireNonNull(availabilityPort, "availabilityPort cannot be null");
        this.telemetryPort = telemetryPort;
        this.pinRevalidator = pinRevalidator;
    }

    @Override
    public LlmProviderStatus get() {
        Optional<LlmProviderPreference> current = preferencePort.current();
        List<LlmProviderStatus.ProviderAvailability> providers = Arrays.stream(LlmProvider.values())
                .map(p -> new LlmProviderStatus.ProviderAvailability(
                        p, availabilityPort.isAvailable(p), telemetryFor(p)))
                .toList();
        return new LlmProviderStatus(
                current.map(LlmProviderPreference::provider).orElse(null),
                current.map(LlmProviderPreference::configuredAt).orElse(null),
                providers);
    }

    private LlmProviderStatus.ProviderTelemetry telemetryFor(LlmProvider provider) {
        if (telemetryPort == null) {
            return null;
        }
        try {
            return telemetryPort.telemetryFor(provider)
                    .map(t -> new LlmProviderStatus.ProviderTelemetry(
                            t.invocations(), t.successes(), t.errors(), t.timeouts(),
                            t.avgLatencyMs(), t.lastLatencyMs(), t.inFlight(),
                            t.lastError(), t.lastErrorAt(), t.lastSuccessAt()))
                    .orElse(null);
        } catch (Exception e) {
            LOG.debug("Telemetry unavailable for {}: {}", provider, e.getMessage());
            return null;
        }
    }

    @Override
    public LlmProviderStatus select(Command command) {
        if (command.provider() == LlmProvider.OPENROUTER && hasText(command.openRouterApiKey())) {
            // Store first so the availability probe below sees the fresh key.
            preferencePort.saveOpenRouterApiKey(command.openRouterApiKey().trim());
        }

        if (!availabilityPort.isAvailable(command.provider())) {
            throw new IllegalArgumentException(unavailableMessage(command.provider()));
        }

        preferencePort.select(command.provider());
        LOG.info("LLM provider selected: {}", command.provider());
        return get().withUnservablePins(revalidateStoredPins());
    }

    /**
     * What the switch just broke. Nothing is repaired — repairing means choosing a model on the
     * user's behalf, the silent substitution this design removes — but they get told before the
     * next run rather than during it.
     */
    private UnservablePinReport revalidateStoredPins() {
        if (pinRevalidator == null) {
            return UnservablePinReport.EMPTY;
        }
        try {
            return pinRevalidator.revalidate();
        } catch (Exception e) {
            // A failed sweep must not undo a selection the user already made successfully.
            LOG.warn("Could not revalidate stored model pins after engine switch: {}", e.getMessage());
            return UnservablePinReport.EMPTY;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String unavailableMessage(LlmProvider provider) {
        return switch (provider) {
            case LOCAL -> "The local provider is not available: no supported CLI tool (claude, codex, "
                    + "gemini or qwen) was found on this machine. Install one, then select again.";
            case LOCAL_HTTP -> "No local model server responded: expected an OpenAI-compatible endpoint "
                    + "(Ollama, LM Studio, …) at the configured URL. Start it (e.g. `ollama serve`), or set "
                    + "QT_LLM_LOCALHTTP_URL to where it listens, then select again.";
            case OPENROUTER -> "OpenRouter is not available: no usable API key is configured. "
                    + "Submit a key with the selection, or set OPENROUTER_API_KEY and restart.";
        };
    }
}
