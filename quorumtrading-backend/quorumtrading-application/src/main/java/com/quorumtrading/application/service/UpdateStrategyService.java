package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.UpdateStrategyUseCase;
import com.quorumtrading.application.usecase.command.UpdateStrategyCommand;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service implementation for updating strategies.
 * Framework-agnostic, uses constructor injection.
 *
 * <p><b>Ticker Change Validation:</b></p>
 * When a strategy has at least one simulation, the set of tickers (stock_tickers, crypto_tickers)
 * cannot be altered. This prevents orphaned positions in running simulations. The only allowed
 * change is modifying position_limits (including setting to 0 to disable a ticker).
 */
public class UpdateStrategyService implements UpdateStrategyUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateStrategyService.class);

    private final StrategyRepository strategyRepository;
    private final SimulationRepository simulationRepository;
    private final LLMPort llmPort;
    private final TickerValidationHelper tickerValidationHelper;
    private final ScheduleVenueValidator scheduleVenueValidator = new ScheduleVenueValidator();
    private final StrategyConfigParser configParser;
    private final AuthorizationService authorizationService;

    /** Optional pin validator; see {@link CreateStrategyService} for why it may be absent. */
    private final ModelPinValidator modelPinValidator;

    public UpdateStrategyService(StrategyRepository strategyRepository, SimulationRepository simulationRepository, LLMPort llmPort, BrokerPort brokerPort, AuthorizationService authorizationService) {
        this(strategyRepository, simulationRepository, llmPort, brokerPort, authorizationService, null);
    }

    public UpdateStrategyService(StrategyRepository strategyRepository, SimulationRepository simulationRepository, LLMPort llmPort, BrokerPort brokerPort, AuthorizationService authorizationService, ModelPinValidator modelPinValidator) {
        this.strategyRepository = strategyRepository;
        this.simulationRepository = simulationRepository;
        this.llmPort = llmPort;
        this.tickerValidationHelper = new TickerValidationHelper(brokerPort);
        this.configParser = new StrategyConfigParser();
        this.authorizationService = authorizationService;
        this.modelPinValidator = modelPinValidator;
    }

    @Override
    public void execute(UpdateStrategyCommand command) {
        boolean hasNameChange = command.name() != null;
        boolean hasDescriptionChange = command.description() != null;
        boolean hasContentChange = command.content() != null;
        if (!hasNameChange && !hasDescriptionChange && !hasContentChange) {
            throw new IllegalArgumentException("At least one of 'name', 'description', or 'content' must be provided for update");
        }

        Strategy existing = strategyRepository.findById(command.strategyId())
            .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + command.strategyId()));

        // Authorization check
        authorizationService.requireEdit(command.requestingUserId(), existing);

        // Update content if provided
        if (hasContentChange) {
            List<Simulation> simulations = simulationRepository.findByStrategyId(command.strategyId());

            if (!simulations.isEmpty()) {
                validateTickersNotChanged(existing.getContent(), command.content(), simulations.size());
            } else {
                validateTickersWithBroker(command.content());
            }

            // Runs on BOTH branches: tickers are frozen once simulations exist, but the
            // schedule is not, so an edit can still move a US-listed strategy onto a session
            // that exchange never has.
            validateScheduleAgainstVenue(command.content());

            validateLlmModel(command.content());
            existing.updateContent(command.content());
        }

        // Update name if provided
        if (hasNameChange) {
            existing.updateName(command.name());
        }

        // Update description if provided
        if (hasDescriptionChange) {
            existing.updateDescription(command.description());
        }

        strategyRepository.save(existing);
    }

    /**
     * Validates that the set of tickers (stock + crypto) has not changed between old and new content.
     * Throws IllegalStateException if tickers are added or removed.
     *
     * @param oldContent     current strategy markdown content
     * @param newContent     new strategy markdown content
     * @param simulationCount number of simulations (for error message)
     */
    private void validateTickersNotChanged(String oldContent, String newContent, int simulationCount) {
        try {
            StrategyConfig oldConfig = configParser.parse(oldContent);
            StrategyConfig newConfig = configParser.parse(newContent);

            Set<String> oldStockTickers = extractTickerStrings(oldConfig.getStockTickers());
            Set<String> newStockTickers = extractTickerStrings(newConfig.getStockTickers());

            Set<String> oldCryptoTickers = extractTickerStrings(oldConfig.getCryptoTickers());
            Set<String> newCryptoTickers = extractTickerStrings(newConfig.getCryptoTickers());

            // Check if tickers changed
            if (!oldStockTickers.equals(newStockTickers) || !oldCryptoTickers.equals(newCryptoTickers)) {
                // Build detailed error message
                Set<String> addedStocks = newStockTickers.stream()
                    .filter(t -> !oldStockTickers.contains(t))
                    .collect(Collectors.toSet());
                Set<String> removedStocks = oldStockTickers.stream()
                    .filter(t -> !newStockTickers.contains(t))
                    .collect(Collectors.toSet());
                Set<String> addedCrypto = newCryptoTickers.stream()
                    .filter(t -> !oldCryptoTickers.contains(t))
                    .collect(Collectors.toSet());
                Set<String> removedCrypto = oldCryptoTickers.stream()
                    .filter(t -> !newCryptoTickers.contains(t))
                    .collect(Collectors.toSet());

                StringBuilder message = new StringBuilder();
                message.append("Cannot modify tickers for a strategy with ").append(simulationCount)
                    .append(" simulation(s). ");

                if (!addedStocks.isEmpty()) {
                    message.append("Added stocks: ").append(addedStocks).append(". ");
                }
                if (!removedStocks.isEmpty()) {
                    message.append("Removed stocks: ").append(removedStocks).append(". ");
                }
                if (!addedCrypto.isEmpty()) {
                    message.append("Added crypto: ").append(addedCrypto).append(". ");
                }
                if (!removedCrypto.isEmpty()) {
                    message.append("Removed crypto: ").append(removedCrypto).append(". ");
                }
                message.append("To disable a ticker, set its position_limit to 0 instead. ")
                    .append("To change tickers, create a new strategy.");

                LOG.warn("Rejected strategy update due to ticker changes: {}", message);
                throw new IllegalStateException(message.toString());
            }

            LOG.debug("Strategy ticker validation passed - stock tickers: {}, crypto tickers: {}",
                oldStockTickers, oldCryptoTickers);

        } catch (IllegalStateException e) {
            // Re-throw our validation exception
            throw e;
        } catch (Exception e) {
            // If parsing fails, log warning but allow update (legacy strategies may have invalid format)
            LOG.warn("Could not parse strategy content for ticker validation, allowing update: {}", e.getMessage());
        }
    }

    /**
     * Validate that the LLM model specified in the strategy (if any) exists in the model catalog.
     */
    private void validateLlmModel(String content) {
        if (modelPinValidator != null) {
            // Parse failures are the parser's business and are reported elsewhere; a strategy that
            // does not parse has no pins to check.
            StrategyConfig parsed;
            try {
                parsed = configParser.parse(content);
            } catch (Exception e) {
                LOG.warn("Could not parse strategy to validate LLM model pins: {}", e.getMessage());
                return;
            }
            modelPinValidator.validateStrategyPins(parsed);
            return;
        }

        try {
            StrategyConfig config = configParser.parse(content);

            String llmModel = config.getLlmModel();
            if (llmModel == null || llmModel.isBlank()) {
                return;
            }

            if (llmPort != null && llmPort.supportsModelListing()) {
                llmPort.getModelInfo(llmModel).orElseThrow(() ->
                    new IllegalArgumentException(CreateStrategyService.unknownModelMessage(llmModel))
                );
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Could not validate LLM model in strategy, allowing update: {}", e.getMessage());
        }
    }

    /**
     * Validate the declared trading session against the venue the tickers trade on.
     * Hard failure — it is pure arithmetic, so unlike the broker check there is nothing
     * here that can be "unavailable" and warrant a soft pass.
     */
    private void validateScheduleAgainstVenue(String content) {
        StrategyConfig config = configParser.parse(content);
        scheduleVenueValidator.validate(config);
    }

    /**
     * Validate tickers with the broker when tickers can change (no simulations).
     */
    private void validateTickersWithBroker(String content) {
        try {
            StrategyConfig config = configParser.parse(content);
            tickerValidationHelper.validateTickers(config);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Could not validate tickers with broker, allowing update: {}", e.getMessage());
        }
    }

    private Set<String> extractTickerStrings(Set<Symbol> symbols) {
        if (symbols == null) {
            return Set.of();
        }
        return symbols.stream()
            .map(Symbol::ticker)
            .collect(Collectors.toSet());
    }
}
