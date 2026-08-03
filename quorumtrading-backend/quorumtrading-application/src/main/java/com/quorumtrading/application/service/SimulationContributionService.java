package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.RecordSimulationContributionCommand;
import com.quorumtrading.application.usecase.contribution.GetSimulationContributionsUseCase;
import com.quorumtrading.application.usecase.contribution.ContributionOutcome;
import com.quorumtrading.application.usecase.contribution.RecordSimulationContributionUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.SimulationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Application service for cash contributions into LIVE simulations.
 *
 * <p>Orchestrates the four-way write that a contribution requires:</p>
 * <ol>
 *   <li>Validate the simulation is LIVE.</li>
 *   <li>Idempotency probe by {@code (planId, date)} — return existing on duplicate.</li>
 *   <li>Bump {@code SimulationPortfolio.cashAvailable} and {@code cashReserved}.</li>
 *   <li>Bump the parent {@code BrokerAccount.reservedBalance} so available buying
 *       power stays consistent across simulations.</li>
 *   <li>Append a {@code PortfolioSnapshot} carrying the new cash, so the injected money is
 *       visible to every derived figure (returnPercent, TWR, MWR, the timeline).</li>
 *   <li>Persist the new {@code Contribution} row.</li>
 * </ol>
 *
 * <p>All five steps run in the persistence-layer transaction supplied by the
 * repository implementations (each {@code @Transactional(REQUIRED)}).</p>
 */
public class SimulationContributionService implements
        RecordSimulationContributionUseCase,
        GetSimulationContributionsUseCase {

    private static final Logger logger = LoggerFactory.getLogger(SimulationContributionService.class);

    private final SimulationRepository simulationRepository;
    private final SimulationPortfolioRepository simulationPortfolioRepository;
    private final BrokerAccountRepository brokerAccountRepository;
    private final SimulationContributionRepository contributionRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;
    private final PortfolioRepository portfolioRepository;

    public SimulationContributionService(
            SimulationRepository simulationRepository,
            SimulationPortfolioRepository simulationPortfolioRepository,
            BrokerAccountRepository brokerAccountRepository,
            SimulationContributionRepository contributionRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService,
            PortfolioRepository portfolioRepository) {
        this.simulationRepository = simulationRepository;
        this.simulationPortfolioRepository = simulationPortfolioRepository;
        this.brokerAccountRepository = brokerAccountRepository;
        this.contributionRepository = contributionRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
        this.portfolioRepository = portfolioRepository;
    }

    /** Contributions have no owner of their own; ownership resolves through the simulation. */
    private Strategy parentStrategy(Simulation simulation) {
        return strategyRepository.findById(simulation.getStrategyId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Strategy not found for simulation: " + simulation.getSimulationId().value()));
    }

    /** Injecting cash is a mutation — edit rights, never a public-visibility bypass. */
    private void requireEditable(Simulation simulation, com.quorumtrading.model.valueobject.UserId requestingUserId) {
        authorizationService.requireEdit(requestingUserId, simulation, parentStrategy(simulation));
    }

    /**
     * Reading the contribution history is a read. It briefly required edit rights, which made
     * GET /simulations/{id} 403 for anyone but the owner because that handler enriches its
     * response with contributions inline — an accident, not a policy.
     */
    private void requireReadable(Simulation simulation, com.quorumtrading.model.valueobject.UserId requestingUserId) {
        authorizationService.requireView(requestingUserId, simulation, parentStrategy(simulation));
    }

    @Override
    public ContributionOutcome execute(RecordSimulationContributionCommand command) {
        // Truncate to the day before probing or storing. The idempotency guarantee — and the
        // UNIQUE (source_plan_id, contribution_date) index that backs it — is meant to allow a
        // scheduler tick to be retried safely. contribution_date is a timestamptz, so storing the
        // exact Instant made every retry a distinct key: two executions seconds apart both inserted
        // and the plan funded twice. A plan contributes at most once per calendar day, so the day is
        // the correct key granularity for both the probe and the stored row.
        //
        // The truncation goes through PlanFundingDay because the plan executor's allocation-level
        // same-day guard keys on the same day. Two guards that disagreed about "today" would let a
        // second funding through, which is the exact bug they exist to prevent.
        Instant contributionDate = PlanFundingDay.startOf(command.contributionDate());

        // Idempotency probe (only meaningful when the contribution is plan-sourced).
        if (command.sourcePlanId() != null) {
            Optional<Contribution> existing = contributionRepository.findByPlanAndDate(
                    command.sourcePlanId(), contributionDate);
            if (existing.isPresent()) {
                logger.info("Contribution already exists for plan {} on {}, returning existing {}",
                        command.sourcePlanId(), contributionDate,
                        existing.get().id());
                // No money moved. The caller has to be able to say so — reporting this as a
                // funded execution is how three same-day ticks came to read as $1500 deployed.
                return ContributionOutcome.alreadyRecorded(existing.get());
            }
        }

        Simulation simulation = simulationRepository.findById(command.simulationId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Simulation not found: " + command.simulationId()));

        // This command carried no user at all, so any caller could inject cash into any LIVE
        // simulation and inflate the parent broker account's reserved balance.
        requireEditable(simulation, command.requestingUserId());

        Contribution contribution = simulation.buildContribution(
                command.amount(),
                contributionDate,
                command.sourcePlanId(),
                command.sourceAllocationId());

        // Bump the simulation portfolio's cash. If no portfolio row exists yet,
        // the simulation hasn't transitioned to LIVE in the normal flow and we
        // bail loudly — contributions require an existing portfolio to top up.
        SimulationPortfolio portfolio = simulationPortfolioRepository.findById(command.simulationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Simulation " + command.simulationId() + " has no portfolio; cannot record contribution"));
        portfolio.recordContribution(command.amount());
        simulationPortfolioRepository.save(portfolio);

        // Keep the broker account's reservedBalance in sync. This is a no-op
        // for legacy simulations that pre-date the broker account model.
        if (portfolio.getBrokerAccountId() != null) {
            Optional<BrokerAccount> brokerOpt = brokerAccountRepository.findById(portfolio.getBrokerAccountId());
            if (brokerOpt.isPresent()) {
                BrokerAccount broker = brokerOpt.get();
                broker.reserve(command.amount());
                brokerAccountRepository.save(broker);
            } else {
                logger.warn("Simulation {} references missing broker account {}; reserved balance not updated",
                        command.simulationId(), portfolio.getBrokerAccountId());
            }
        }

        appendContributionSnapshot(command.simulationId(), command.amount(), contribution.createdAt());

        Contribution saved = contributionRepository.save(contribution);
        logger.info("Recorded contribution {} on simulation {} amount={} sourcePlan={}",
                saved.id(), command.simulationId(), command.amount(), command.sourcePlanId());
        return ContributionOutcome.created(saved);
    }

    /**
     * Extend the snapshot lineage with the injected cash.
     *
     * <p>Every user-facing figure — returnPercent, TWR, MWR, the portfolio timeline — is derived
     * from {@code portfolio_snapshots}, and each new snapshot carries cash forward from the
     * previous one ({@code EvaluatePortfolioValueService}). Updating only
     * {@code SimulationPortfolio} therefore left the contribution invisible to every number the
     * user reads: a plan that had lost nothing reported -99% because {@code totalInvested} grew
     * while portfolio value did not. The money itself was never lost — the broker reservation was
     * always correct — but the reported performance was pure artifact.</p>
     *
     * <p>Positions are copied unchanged; only cash moves. A contribution is a cashflow, not a
     * trade, and {@code PerformanceCalculator} breaks its TWR sub-periods at exactly these
     * points.</p>
     *
     * <p>The snapshot is stamped strictly <em>after</em> the contribution's own instant. TWR
     * attributes a contribution to the period {@code [prev, curr)} that contains it and adds it to
     * that period's start value; if this snapshot shared or preceded the contribution's instant,
     * the cash would fall outside the period it caused and the resulting value step would be read
     * as growth — a $500 top-up into a $500 portfolio reported as +100% on a sim that never
     * traded.</p>
     */
    private void appendContributionSnapshot(SimulationId simulationId,
                                            com.quorumtrading.model.valueobject.Money amount,
                                            Instant contributionInstant) {
        Optional<PortfolioSnapshot> latest = portfolioRepository.findLatestBySimulationId(simulationId);
        if (latest.isEmpty()) {
            // Nothing to extend. The portfolio aggregate still carries the cash, so the money is
            // accounted for; the timeline simply starts at the next evaluation.
            logger.warn("No portfolio snapshot for simulation {}; contribution {} not reflected in the timeline",
                    simulationId, amount);
            return;
        }

        PortfolioSnapshot previous = latest.get();
        // Legacy rows may carry no createdAt; ordering just after the previous snapshot still
        // places the cash inside the period it belongs to.
        Instant snapshotAt = contributionInstant != null
                ? contributionInstant.plusMillis(1)
                : previous.getTimestamp().plusMillis(1);
        if (!snapshotAt.isAfter(previous.getTimestamp())) {
            // Never order behind the snapshot we are extending, or the chain reads backwards.
            snapshotAt = previous.getTimestamp().plusMillis(1);
        }
        PortfolioSnapshot contributionSnapshot = new PortfolioSnapshot(
                simulationId,
                snapshotAt,
                previous.getPositions(),
                previous.getTotalValue().add(amount),
                previous.getCashAvailable().add(amount));

        portfolioRepository.save(contributionSnapshot);
        logger.info("Appended contribution snapshot for simulation {}: cash {} -> {}, total {} -> {}",
                simulationId, previous.getCashAvailable(), contributionSnapshot.getCashAvailable(),
                previous.getTotalValue(), contributionSnapshot.getTotalValue());
    }

    @Override
    public List<Contribution> execute(SimulationId simulationId, com.quorumtrading.model.valueobject.UserId requestingUserId) {
        Simulation simulation = simulationRepository.findById(simulationId)
                .orElseThrow(() -> new NoSuchElementException("Simulation not found: " + simulationId));
        requireReadable(simulation, requestingUserId);
        return contributionRepository.findBySimulationId(simulationId);
    }
}
