package com.quorumtrading.application.service;

import com.quorumtrading.application.event.SimulationCreatedEvent;
import com.quorumtrading.application.exception.AccountAccessDeniedException;
import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.exception.InsufficientFundsException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.brokeraccount.CreateBrokerAccountUseCase;
import com.quorumtrading.application.usecase.command.CreateBrokerAccountCommand;
import com.quorumtrading.application.usecase.command.CreateBenchmarkCommand;
import com.quorumtrading.application.usecase.dto.CreateBenchmarkResponse;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service implementation for creating a benchmark with multiple simulations.
 * <p>
 * Implements the {@link com.quorumtrading.application.usecase.CreateBenchmarkUseCase} port following hexagonal architecture.
 * This service orchestrates:
 * </p>
 * <ol>
 *   <li>Validation of all strategies</li>
 *   <li>Creation of benchmark entity</li>
 *   <li><b>When mockConfig is provided:</b> Creation of a separate mock broker account per strategy</li>
 *   <li><b>When brokerAccountId is provided:</b> Validation and capital reservation on shared account</li>
 *   <li>Creation of simulation for each strategy (linked to its broker account)</li>
 *   <li>Publishing async events for simulation execution</li>
 * </ol>
 *
 * <p><b>Mock Account Cloning:</b> When mockConfig is provided, each strategy gets its own isolated
 * mock broker account with the specified initial capital. This ensures simulations run in isolation
 * with their own capital. Account names follow the format: "[Benchmark name] - [Strategy name]"</p>
 *
 * <p><b>Paper/Real Account Sharing:</b> When brokerAccountId is provided, all strategies share
 * the same broker account with capital reserved per simulation. This validates account ownership
 * and sufficient available capital before creating simulations.</p>
 */
public class CreateBenchmarkService implements com.quorumtrading.application.usecase.CreateBenchmarkUseCase {

    private static final Logger LOG = Logger.getLogger(CreateBenchmarkService.class);

    private final StrategyRepository strategyRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final SimulationRepository simulationRepository;
    private final EventPublisher<SimulationCreatedEvent> simulationCreatedEventPublisher;
    private final CreateBrokerAccountUseCase createBrokerAccountUseCase;
    private final BrokerAccountRepository brokerAccountRepository;
    private final AuthorizationService authorizationService;

    public CreateBenchmarkService(
        StrategyRepository strategyRepository,
        BenchmarkRepository benchmarkRepository,
        SimulationRepository simulationRepository,
        EventPublisher<SimulationCreatedEvent> simulationCreatedEventPublisher,
        CreateBrokerAccountUseCase createBrokerAccountUseCase,
        BrokerAccountRepository brokerAccountRepository,
        AuthorizationService authorizationService
    ) {
        this.strategyRepository = strategyRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.simulationRepository = simulationRepository;
        this.simulationCreatedEventPublisher = simulationCreatedEventPublisher;
        this.createBrokerAccountUseCase = createBrokerAccountUseCase;
        this.brokerAccountRepository = brokerAccountRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public CreateBenchmarkResponse execute(CreateBenchmarkCommand command) {
        LOG.infof("Creating benchmark with %d strategies from %s to %s",
            command.strategyIds().size(), command.startDate(), command.endDate());

        // Step 1: Validate all strategies exist and verify ownership
        List<Strategy> strategies = new ArrayList<>();
        for (UUID strategyId : command.strategyIds()) {
            Strategy strategy = strategyRepository.findById(new StrategyId(strategyId))
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategyId));

            // Verify user owns or can view the strategy
            authorizationService.requireView(command.userId(), strategy);

            strategies.add(strategy);
        }

        LOG.infof("Validated %d strategies", strategies.size());

        // Step 2: If using existing broker account, validate ownership and capital
        BrokerAccount sharedBrokerAccount = null;
        if (command.usesExistingBrokerAccount()) {
            sharedBrokerAccount = validateAndReserveCapital(command);
        }

        // Step 3: Generate benchmark name if not provided
        String benchmarkName = command.name();
        if (benchmarkName == null) {
            benchmarkName = Benchmark.generateName(command.startDate(), command.endDate());
        }

        // Step 4: Create and persist Benchmark — skip for single-strategy (standalone simulation)
        boolean isSingleStrategy = strategies.size() == 1;
        BenchmarkId benchmarkId = null;

        if (!isSingleStrategy) {
            benchmarkId = BenchmarkId.generate();
            Benchmark benchmark = new Benchmark(
                benchmarkId,
                benchmarkName,
                command.startDate(),
                command.endDate(),
                Instant.now(),
                command.userId()
            );
            benchmarkRepository.save(benchmark);
            LOG.infof("Created benchmark %s: %s", benchmarkId.value(), benchmarkName);
        } else {
            LOG.infof("Single strategy — creating standalone simulation (no benchmark wrapper)");
        }

        // Step 5: Create simulations for each strategy
        List<SimulationId> simulationIds = new ArrayList<>();
        boolean usesMockBroker = command.usesMockBroker();
        boolean usesExistingBrokerAccount = command.usesExistingBrokerAccount();

        if (usesMockBroker) {
            LOG.infof("Using mock broker configuration - creating isolated accounts per strategy");
        } else if (usesExistingBrokerAccount) {
            LOG.infof("Using existing broker account %s - all strategies share with capital %s per simulation",
                command.brokerAccountId().value(), command.capitalPerStrategy());
        }

        for (Strategy strategy : strategies) {
            SimulationId simulationId = SimulationId.generate();
            String simulationName = strategy.getName() + " - " + benchmarkName;

            // Determine broker account and initial cash
            BrokerAccountId simBrokerAccountId = null;
            Money initialCash;
            Money tradingLimit = null;

            if (usesMockBroker) {
                BrokerAccount mockAccount = createMockBrokerAccountForStrategy(
                    command,
                    benchmarkName,
                    strategy
                );
                simBrokerAccountId = mockAccount.getId();
                initialCash = command.mockConfig().initialCapital();
                tradingLimit = initialCash;

                LOG.debugf("Created mock broker account %s for strategy %s with capital %s",
                    simBrokerAccountId.value(), strategy.getName(), initialCash);
            } else if (usesExistingBrokerAccount) {
                simBrokerAccountId = command.brokerAccountId();
                initialCash = command.capitalPerStrategy();
                tradingLimit = command.capitalPerStrategy();

                LOG.debugf("Using shared broker account %s for strategy %s with trading limit %s",
                    simBrokerAccountId.value(), strategy.getName(), tradingLimit);
            } else {
                initialCash = new Money(new BigDecimal("100000.00"), Money.Currency.USD);
            }

            Simulation simulation = new Simulation(
                simulationId,
                simulationName,
                strategy.getId(),
                command.startDate(),
                command.endDate(),
                initialCash,
                benchmarkId,
                simBrokerAccountId,
                tradingLimit
            );
            // Benchmark simulations inherit ownership from the strategy they run.
            simulation.setOwnerUserId(strategy.getOwnerId());

            simulationRepository.save(simulation);
            simulationIds.add(simulationId);

            LOG.debugf("Created simulation %s: %s (brokerAccount=%s)",
                simulationId.value(), simulationName, simBrokerAccountId);
        }

        LOG.infof("Created %d simulation(s)%s", simulationIds.size(),
            benchmarkId != null ? " for benchmark " + benchmarkId.value() : " (standalone)");

        // Step 6: Publish events for simulation execution
        for (SimulationId simulationId : simulationIds) {
            simulationCreatedEventPublisher.publish(new SimulationCreatedEvent(simulationId));
            LOG.debugf("Published SimulationCreatedEvent for simulation %s", simulationId.value());
        }

        // Step 7: Return response
        return new CreateBenchmarkResponse(
            benchmarkId != null ? benchmarkId.value() : null,
            simulationIds.stream().map(SimulationId::value).toList(),
            benchmarkName
        );
    }

    private BrokerAccount validateAndReserveCapital(CreateBenchmarkCommand command) {
        BrokerAccountId accountId = command.brokerAccountId();

        BrokerAccount account = brokerAccountRepository.findById(accountId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Broker account not found: " + accountId.value()));

        if (!account.getUserId().equals(command.userId())) {
            throw new AccountAccessDeniedException(accountId, command.userId());
        }

        Money totalRequired = command.getTotalCapitalRequired();
        Money available = account.getAvailableBalance();

        LOG.debugf("Checking capital for account %s: required=%s, available=%s",
            accountId.value(), totalRequired, available);

        if (totalRequired.compareTo(available) > 0) {
            throw new InsufficientFundsException(totalRequired, available);
        }

        account.reserve(totalRequired);
        brokerAccountRepository.save(account);

        LOG.infof("Reserved %s capital on account %s for %d simulations",
            totalRequired, accountId.value(), command.getStrategyCount());

        return account;
    }

    private BrokerAccount createMockBrokerAccountForStrategy(
            CreateBenchmarkCommand command,
            String benchmarkName,
            Strategy strategy) {

        String accountName = benchmarkName + " - " + strategy.getName();

        // Inline: one account per benchmark member simulation, created here rather than chosen by
        // the user. Without the flag these escaped the inline lifecycle entirely — they stayed in
        // list_broker_accounts and were left orphaned and ACTIVE when their simulation was deleted.
        CreateBrokerAccountCommand accountCommand = CreateBrokerAccountCommand.forInlineMock(
            command.userId(),
            accountName,
            command.mockConfig()
        );

        return createBrokerAccountUseCase.execute(accountCommand);
    }
}
