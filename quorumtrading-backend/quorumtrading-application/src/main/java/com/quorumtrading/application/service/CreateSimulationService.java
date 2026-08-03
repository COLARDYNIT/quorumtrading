package com.quorumtrading.application.service;

import com.quorumtrading.application.event.SimulationCreatedEvent;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Service implementation for creating simulations.
 * Framework-agnostic, uses constructor injection.
 *
 * <p><b>Broker Account Support:</b></p>
 * <ul>
 *   <li><b>Existing account:</b> Validates balance and sets brokerAccountId + tradingLimit on simulation</li>
 *   <li><b>Inline mock broker:</b> Creates a new mock BrokerAccount from MockBrokerConfig</li>
 *   <li><b>Legacy mode:</b> No broker account, uses global broker routing (backward compatible)</li>
 * </ul>
 *
 */
public class CreateSimulationService implements CreateSimulationUseCase {

    private static final Logger LOG = Logger.getLogger(CreateSimulationService.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final PortfolioRepository portfolioRepository;
    private final BrokerAccountRepository brokerAccountRepository;
    private final EventPublisher<SimulationCreatedEvent> simulationCreatedEventPublisher;
    private final AuthorizationService authorizationService;
    private final ScheduleVenueValidator scheduleVenueValidator = new ScheduleVenueValidator();
    private final StrategyConfigParser configParser = new StrategyConfigParser();

    public CreateSimulationService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            PortfolioRepository portfolioRepository,
            BrokerAccountRepository brokerAccountRepository,
            EventPublisher<SimulationCreatedEvent> simulationCreatedEventPublisher,
            AuthorizationService authorizationService) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.portfolioRepository = portfolioRepository;
        this.brokerAccountRepository = brokerAccountRepository;
        this.simulationCreatedEventPublisher = simulationCreatedEventPublisher;
        this.authorizationService = authorizationService;
    }

    @Override
    public SimulationId execute(CreateSimulationCommand command) {
        // Business validation 1: Strategy must exist
        Strategy strategy = strategyRepository.findById(command.strategyId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Strategy not found: " + command.strategyId() + ". " +
                "Cannot create simulation for non-existent strategy."
            ));

        // Authorization check: user must own the strategy to create simulations for it
        authorizationService.requireEdit(command.requestingUserId(), strategy);

        // Business validation 1b: the strategy's schedule must be runnable on the venue its
        // tickers trade on. Checking this only at create/update time left the defect fully
        // reachable through any strategy stored BEFORE that check existed: a new simulation
        // off a grandfathered strategy was accepted in silence and fired every decision with
        // the exchange shut. Validating here catches the strategy on the way into a run,
        // which is the last moment before it can place an order.
        validateScheduleAgainstVenue(strategy);

        // Business validation 2: End date must be after start date when set.
        // A null endDate signals "LIVE forever" per the Simulation entity contract —
        // skip the comparison rather than NPE on null.isAfter(). The DCA executor
        // and any other LIVE-mode caller rely on this.
        if (command.endDate() != null && !command.endDate().isAfter(command.startDate())) {
            throw new IllegalArgumentException(
                "End date must be after start date. " +
                "Start: " + command.startDate() + ", End: " + command.endDate()
            );
        }

        // Business validation 2b: Start date must not be before data availability cutoff
        // Tiingo provides 30+ years of daily stock data; earliest reliable data starts ~1993.
        // Alpaca IEX feed covers ~5.5 years; Tiingo fills the gap for older backtests.
        Instant dataAvailabilityCutoff = Instant.parse("1993-01-29T00:00:00Z");
        if (command.startDate().isBefore(dataAvailabilityCutoff)) {
            throw new IllegalArgumentException(
                "Start date cannot be before January 29, 1993. " +
                "Historical market data is not available before this date. " +
                "Please choose a start date of January 29, 1993 or later."
            );
        }

        // Business validation 3: Initial cash must be positive
        if (command.initialCash().amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Initial cash must be positive. " +
                "Provided: " + command.initialCash().amount() + " " + command.initialCash().currency()
            );
        }

        // ========== BROKER ACCOUNT HANDLING ==========
        BrokerAccountId brokerAccountId = null;
        Money tradingLimit = null;

        if (command.mockBrokerConfig() != null) {
            // Option 1: Create inline mock broker account
            BrokerAccountResult result = createInlineMockBroker(command.mockBrokerConfig(), command.requestingUserId());
            brokerAccountId = result.accountId;
            tradingLimit = result.tradingLimit;
            LOG.infof("Created inline mock broker account %s with trading limit %s",
                brokerAccountId.value().toString().substring(0, 8), tradingLimit);

        } else if (command.brokerAccountId() != null) {
            // Option 2: Use existing broker account
            brokerAccountId = command.brokerAccountId();
            tradingLimit = command.tradingLimit();
            validateBrokerAccount(brokerAccountId, tradingLimit,
                command.requestingUserId(), command.confirmLive());
            LOG.infof("Using existing broker account %s with trading limit %s",
                brokerAccountId.value().toString().substring(0, 8), tradingLimit);

        } else {
            // Option 3: Legacy mode - no broker account, uses global broker routing
            LOG.debugf("No broker account specified, using legacy global broker routing");
        }

        // Use the user's requested initial cash for all simulation types.
        // For broker-backed simulations, the broker account balance is already validated
        // in validateBrokerAccount() above — the user's allocation is intentional.
        Money initialCash = command.initialCash();

        // Use provided name or generate one if null
        String simulationName = command.name();
        if (simulationName == null || simulationName.isBlank()) {
            simulationName = "Simulation-" + UUID.randomUUID().toString().substring(0, 8);
        }

        // Create simulation with broker account info
        Simulation simulation = new Simulation(
            new SimulationId(UUID.randomUUID()),
            simulationName,
            command.strategyId(),
            command.startDate(),
            command.endDate(),
            initialCash,
            null,  // benchmarkId
            brokerAccountId,
            tradingLimit
        );
        // Assign owner — DB enforces NOT NULL. Prefer the requesting user; for
        // system-created sims (no requestingUserId) fall back to the strategy owner.
        UserId owner = command.requestingUserId() != null
                ? command.requestingUserId()
                : strategy.getOwnerId();
        simulation.setOwnerUserId(owner);

        Simulation saved = simulationRepository.save(simulation);

        // Create initial portfolio snapshot with starting cash balance
        PortfolioSnapshot initialSnapshot = new PortfolioSnapshot(
            saved.getSimulationId(),
            command.startDate(),
            java.util.Map.of(), // Empty positions - no trades yet
            initialCash, // totalValue = initialCash
            initialCash  // cashAvailable = initialCash
        );
        portfolioRepository.save(initialSnapshot);

        // Publish event to trigger backfill orchestration
        SimulationId simulationId = saved.getSimulationId();
        simulationCreatedEventPublisher.publish(new SimulationCreatedEvent(simulationId));
        LOG.debugf("Published SimulationCreatedEvent for simulation %s to trigger backfill orchestration", simulationId.value());

        return simulationId;
    }

    /**
     * Rejects a run whose strategy schedule cannot fire on the venue its tickers trade on.
     *
     * <p>Names the strategy in the message: unlike create/update, the user here did not just
     * author the schedule — it may be one of several stored strategies, quite possibly written
     * before this rule existed — so "fix your schedule" is useless without saying which one.</p>
     *
     * <p>A strategy that no longer parses is passed through untouched. Turning a parse failure
     * into a schedule complaint would describe a problem the user does not have; the existing
     * parse-failure path reports that accurately.</p>
     */
    private void validateScheduleAgainstVenue(Strategy strategy) {
        StrategyConfig config;
        try {
            config = configParser.parse(strategy.getContent());
        } catch (Exception e) {
            LOG.debugf("Skipping schedule validation for strategy \"%s\" — it does not parse: %s",
                strategy.getName(), e.getMessage());
            return;
        }

        try {
            scheduleVenueValidator.validate(config);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Strategy \"" + strategy.getName() + "\" cannot run as scheduled. " + e.getMessage(), e);
        }
    }

    /**
     * Validates that the broker account exists and has sufficient available balance for the trading limit.
     *
     * @param brokerAccountId the broker account ID to validate
     * @param tradingLimit the trading limit to validate against available balance
     * @throws IllegalArgumentException if account not found or insufficient balance
     */
    private void validateBrokerAccount(BrokerAccountId brokerAccountId, Money tradingLimit,
                                       UserId requestingUserId, boolean confirmLive) {
        if (tradingLimit == null) {
            throw new IllegalArgumentException(
                "Trading limit is required when using a broker account");
        }

        if (tradingLimit.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Trading limit must be positive. Provided: " + tradingLimit.format());
        }

        BrokerAccount account = brokerAccountRepository.findById(brokerAccountId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Broker account not found: " + brokerAccountId.value()));

        // The strategy is authorization-checked above; the broker account was not, so anyone who
        // learned another user's account UUID could ring-fence that account's capital behind their
        // own simulation. InvestmentPlanService has always checked this — the paths disagreed.
        if (requestingUserId != null && !account.getUserId().equals(requestingUserId)) {
            throw new SecurityException(
                "Broker account does not belong to user: " + requestingUserId.value());
        }

        // isLive() rather than an ALPACA_LIVE equality check, so SAXO_LIVE is covered too. This is
        // the same gate create_investment_plan applies; without it, the path that opens a real
        // position immediately was the *less* guarded of the two.
        if (account.getBrokerType().isLive() && !confirmLive) {
            throw new IllegalStateException(
                "Broker account " + brokerAccountId.value() + " is " + account.getBrokerType()
                    + "; pass confirmLive=true to acknowledge that this simulation will trade "
                    + "REAL money. State the broker, strategy, symbol and cash to the user and get "
                    + "an explicit yes before setting it.");
        }

        Money availableBalance = account.getAvailableBalance();
        if (tradingLimit.compareTo(availableBalance) > 0) {
            throw new IllegalArgumentException(
                String.format("Insufficient available balance in broker account %s. " +
                    "Requested: %s, Available: %s",
                    brokerAccountId.value().toString().substring(0, 8),
                    tradingLimit.format(),
                    availableBalance.format()));
        }

        LOG.debugf("Broker account %s validated: requested %s, available %s",
            brokerAccountId.value().toString().substring(0, 8),
            tradingLimit, availableBalance);
    }

    /**
     * Creates an inline mock broker account for a simulation.
     * Used when mockBrokerConfig is provided in the command.
     *
     * @param config the mock broker configuration
     * @return the created broker account ID and trading limit
     */
    private BrokerAccountResult createInlineMockBroker(MockBrokerConfig config, UserId requestingUserId) {
        // Create a mock broker account owned by the requesting user
        UserId ownerId = requestingUserId != null
            ? requestingUserId
            : new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        // Marked inline: this account exists only to back the simulation being created, so it
        // is hidden from account listings and deleted along with that simulation. Without the
        // flag these accumulate one per backtest and outlive the simulations that are their
        // only reason to exist.
        BrokerAccount mockAccount = BrokerAccount.createMock(
            ownerId,
            "Inline Mock Broker - " + UUID.randomUUID().toString().substring(0, 8),
            config,
            true
        );

        BrokerAccount saved = brokerAccountRepository.save(mockAccount);

        // The trading limit is the initial capital for inline mock brokers
        return new BrokerAccountResult(saved.getId(), config.initialCapital());
    }

    /**
     * Result holder for broker account creation.
     */
    private record BrokerAccountResult(BrokerAccountId accountId, Money tradingLimit) {}

}
