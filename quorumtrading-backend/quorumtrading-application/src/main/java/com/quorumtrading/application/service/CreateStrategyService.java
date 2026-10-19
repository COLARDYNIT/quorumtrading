package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CreateStrategyUseCase;
import com.quorumtrading.application.usecase.command.CreateStrategyCommand;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.StrategyId;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service implementation for creating strategies.
 * Framework-agnostic, uses constructor injection.
 *
 * <p>Fail-fast validation: Parsing and validation errors are thrown immediately,
 * not swallowed. Invalid strategies cannot be saved.</p>
 */
public class CreateStrategyService implements CreateStrategyUseCase {

    private static final Logger LOG = Logger.getLogger(CreateStrategyService.class.getName());

    private final StrategyRepository strategyRepository;
    private final LLMPort llmPort;
    private final TickerValidationHelper tickerValidationHelper;
    private final ScheduleVenueValidator scheduleVenueValidator = new ScheduleVenueValidator();
    private final StrategyConfigParser parser = new StrategyConfigParser();

    /**
     * Optional pin validator. When absent (lightweight tests, deployments with no discoverable
     * engine) the legacy soft check still runs, so a strategy can always be created.
     */
    private final ModelPinValidator modelPinValidator;

    public CreateStrategyService(StrategyRepository strategyRepository, BrokerPort brokerPort, LLMPort llmPort) {
        this(strategyRepository, brokerPort, llmPort, null);
    }

    public CreateStrategyService(StrategyRepository strategyRepository, BrokerPort brokerPort, LLMPort llmPort,
                                 ModelPinValidator modelPinValidator) {
        this.strategyRepository = strategyRepository;
        this.llmPort = llmPort;
        this.tickerValidationHelper = new TickerValidationHelper(brokerPort);
        this.modelPinValidator = modelPinValidator;
    }

    @Override
    public StrategyId execute(CreateStrategyCommand command) {
        // FAIL-FAST: Parse and validate strategy content FIRST
        // Throws StrategyValidationException or IllegalArgumentException if invalid
        StrategyConfig config = parser.parse(command.content());

        // Validate tickers via broker (soft failure if broker unavailable)
        validateTickersWithBroker(config);

        // Validate the declared session against the venue those tickers trade on (hard failure —
        // pure arithmetic, nothing external to be unavailable)
        scheduleVenueValidator.validate(config);

        // Validate LLM model if specified (soft failure if LLM catalog unavailable)
        validateLlmModel(config);

        // All validation passed - create and save (strategies start ACTIVE)
        Strategy strategy = new Strategy(
            new StrategyId(UUID.randomUUID()),
            command.name(),
            command.content(),
            command.ownerId()
        );

        if (command.description() != null) {
            strategy.updateDescription(command.description());
        }

        Strategy saved = strategyRepository.save(strategy);
        return saved.getId();
    }

    /**
     * Validate that all tickers in the strategy are tradable via the broker.
     * Delegates to TickerValidationHelper for rich error messages.
     */
    private void validateTickersWithBroker(StrategyConfig config) {
        tickerValidationHelper.validateTickers(config);
    }

    /**
     * Validate that the LLM model specified in the strategy (if any) exists in the model catalog.
     * This is a soft validation - if the LLM service is unavailable, we log a warning
     * but don't block strategy creation.
     *
     * @param config The parsed strategy config
     * @throws IllegalArgumentException if model is invalid and LLM service is available
     */
    /**
     * The rejection must say what catalogue it checked and how to pin a local model — the old
     * text suggested 'anthropic/claude-3.5-sonnet' unconditionally, which on a local deployment
     * reads as "local models cannot be pinned" and sent users hunting through env vars.
     */
    static String unknownModelMessage(String llmModel) {
        return "Unknown LLM model: " + llmModel + ". The active LLM engine does not advertise this id. "
                + "List valid ids with get_available_models (models come from the engine you selected — "
                + "OpenRouter ids like 'anthropic/claude-sonnet-5.5' only exist there; a local server "
                + "advertises its own, e.g. 'llama3.2'). On a local engine you can also omit llm_model "
                + "entirely (the server's default is used) or pin one process-wide with QT_LLM_LOCALHTTP_MODEL.";
    }

    /**
     * Validate every model this strategy pins — the global {@code llm_model} <em>and</em> each
     * per-agent {@code llm_models} entry.
     *
     * <p>Per-agent pins are tier 1, the highest precedence in the resolution chain, and used to
     * reach no validation at all: a bad one was accepted at save time and then misrouted silently
     * on every run. Validating only the lower-precedence global pin was the wrong half.</p>
     */
    private void validateLlmModel(StrategyConfig config) {
        if (modelPinValidator != null) {
            modelPinValidator.validateStrategyPins(config);
            return;
        }

        String llmModel = config.getLlmModel();
        if (llmModel == null || llmModel.isBlank()) {
            return; // No model override, nothing to validate
        }

        try {
            if (llmPort != null && llmPort.supportsModelListing()) {
                llmPort.getModelInfo(llmModel).orElseThrow(() ->
                    new IllegalArgumentException(unknownModelMessage(llmModel))
                );
            }
        } catch (IllegalArgumentException e) {
            throw e; // Re-throw validation errors
        } catch (Throwable e) {
            // LLM service unavailable or infrastructure error - log warning but allow creation
            LOG.log(Level.WARNING, "Could not validate LLM model: {0}", e.getMessage());
        }
    }
}
