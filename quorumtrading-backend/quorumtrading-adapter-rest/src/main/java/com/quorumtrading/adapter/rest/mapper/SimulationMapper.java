package com.quorumtrading.adapter.rest.mapper;

import com.quorumtrading.adapter.rest.dto.response.ContributionResponse;
import com.quorumtrading.adapter.rest.dto.response.SimulationResponse;
import com.quorumtrading.adapter.rest.dto.response.StrategyLineageResponse;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.CostPreset;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.service.PerformanceCalculator;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.StrategyId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mapper for converting between Simulation domain objects and REST DTOs.
 *
 * Maps application layer Simulation record to SimulationResponse DTO.
 */
@ApplicationScoped
public class SimulationMapper {

    @Inject
    BrokerAccountRepository brokerAccountRepository;

    @Inject
    StrategyRepository strategyRepository;

    @ConfigProperty(name = "quorumtrading.broker.stock.type", defaultValue = "mock")
    String stockBrokerType;

    /**
     * Convert domain Simulation to response DTO.
     *
     * <p>Performs per-item repository lookups for the linked broker account
     * and strategy — fine for detail endpoints. For lists, use
     * {@link #toResponseList(List)} which batch-loads both.</p>
     *
     * @param simulation the domain simulation object
     * @return the response DTO
     */
    public SimulationResponse toResponse(Simulation simulation) {
        BrokerAccount account = simulation.getBrokerAccountId() != null
            ? brokerAccountRepository.findById(simulation.getBrokerAccountId()).orElse(null)
            : null;
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId()).orElse(null);
        return toResponse(simulation, account, strategy);
    }

    /**
     * Convert domain Simulation to response DTO using pre-resolved related
     * entities (no repository access).
     *
     * @param simulation the domain simulation object
     * @param account    the simulation's broker account, or null when the
     *                   simulation has none (legacy MOCK routing) or the
     *                   account row was not found
     * @param strategy   the simulation's strategy, or null when not found
     */
    private SimulationResponse toResponse(Simulation simulation, BrokerAccount account, Strategy strategy) {
        // Extract broker info if simulation has a broker account
        String brokerType = null;
        String brokerPreset = null;
        BigDecimal commissionRate = null;
        BigDecimal cryptoCommissionRate = null;
        BigDecimal slippageBase = null;

        if (simulation.getBrokerAccountId() != null) {
            // Per-account routing: simulation has a linked broker account
            if (account != null) {
                brokerType = account.getBrokerType().name();

                // For mock brokers, extract cost configuration
                if (account.getBrokerType() == BrokerType.MOCK && account.getMockConfig() != null) {
                    MockBrokerConfig config = account.getMockConfig();
                    CostPreset preset = config.preset();

                    // If preset was stored as CUSTOM, try to detect which preset it matches
                    if (preset == CostPreset.CUSTOM) {
                        preset = config.detectPreset();
                    }
                    brokerPreset = preset.name();

                    // Only include detailed costs for CUSTOM preset
                    if (preset == CostPreset.CUSTOM) {
                        commissionRate = config.commissionRate();
                        cryptoCommissionRate = config.cryptoCommissionRate();
                        slippageBase = config.slippageBase();
                    }
                }
            }
        } else {
            // Legacy routing: simulations without broker account always use MOCK
            // The global stockBrokerType config only applies when a broker account is explicitly linked
            brokerType = BrokerType.MOCK.name();
        }

        StrategyLineageResponse lineage = strategy != null ? toLineage(strategy) : null;

        return new SimulationResponse(
            simulation.getSimulationId().value().toString(),
            simulation.getStrategyId().value().toString(),
            simulation.getStartDate(),
            simulation.getEndDate(),
            simulation.getStatus().toString(),
            simulation.getName(),
            simulation.getProgress(),
            simulation.isPaused(),
            simulation.isPausedForExternalReason(),
            simulation.getExternalPauseReason(),
            simulation.getCreatedAt(),
            simulation.getStartedAt(),
            simulation.getCompletedAt(),
            simulation.getFailureReason(),
            simulation.getBenchmarkId() != null ? simulation.getBenchmarkId().value().toString() : null,
            brokerType,
            brokerPreset,
            commissionRate,
            cryptoCommissionRate,
            slippageBase,
            simulation.getBackfillJobId() != null ? simulation.getBackfillJobId().toString() : null,
            simulation.getSkippedEvaluationCount(),
            simulation.getTotalEvaluationCount(),
            simulation.getInitialCash() != null ? simulation.getInitialCash().amount() : null,
            null,  // totalInvested — populated by enriched overload
            null,  // twrPercent
            null,  // mwrPercent
            null,  // maxDrawdownPercent
            null,  // contributions
            lineage,
            simulation.isNotifyDecisions()
        );
    }

    private StrategyLineageResponse toLineage(Strategy strategy) {
        String origin = strategy.hasParent() ? "PUBLIC" : "OWN";
        String parentId = strategy.getParentStrategyId() != null
            ? strategy.getParentStrategyId().value().toString()
            : null;
        return new StrategyLineageResponse(origin, parentId, strategy.getGeneration());
    }

    /**
     * Convert list of domain Simulations to list of response DTOs.
     *
     * <p>Batch-loads the referenced strategies and broker accounts with one
     * IN-query each instead of two lookups per simulation — the dashboard
     * polls this list every few seconds, so per-item lookups were an N+1.</p>
     *
     * @param simulations the list of domain simulations
     * @return the list of response DTOs
     */
    public List<SimulationResponse> toResponseList(List<Simulation> simulations) {
        if (simulations == null || simulations.isEmpty()) {
            return List.of();
        }

        Set<StrategyId> strategyIds = simulations.stream()
            .map(Simulation::getStrategyId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<BrokerAccountId> brokerAccountIds = simulations.stream()
            .map(Simulation::getBrokerAccountId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Map<StrategyId, Strategy> strategiesById = strategyRepository.findByIds(strategyIds)
            .stream()
            .collect(Collectors.toMap(Strategy::getId, Function.identity()));
        Map<BrokerAccountId, BrokerAccount> accountsById = brokerAccountRepository.findByIds(brokerAccountIds)
            .stream()
            .collect(Collectors.toMap(BrokerAccount::getId, Function.identity()));

        return simulations.stream()
            .map(sim -> toResponse(
                sim,
                sim.getBrokerAccountId() != null ? accountsById.get(sim.getBrokerAccountId()) : null,
                strategiesById.get(sim.getStrategyId())))
            .collect(Collectors.toList());
    }

    /**
     * Enriched response for the simulation detail endpoint — includes
     * contribution history, total invested basis, and time/money-weighted
     * returns. Pass the snapshots and current portfolio value to drive TWR/MWR;
     * pass nulls when either is unavailable (the perf fields then come back null).
     *
     * <p>The MWR as-of instant is derived from the simulation itself, not supplied by the caller
     * — see {@link PerformanceCalculator#resolveMwrAsOf}. It used to be a parameter, and the one
     * caller passed {@code Instant.now()}, which annualised a finished run over a horizon that
     * grew on every page load.</p>
     *
     * @param simulation     the simulation
     * @param contributions  all contributions for this simulation (may be empty)
     * @param snapshots      portfolio snapshots, used for TWR sub-period chaining
     * @param currentValue   the portfolio's value at the last snapshot (drives MWR)
     */
    public SimulationResponse toResponseWithContributions(
            Simulation simulation,
            List<Contribution> contributions,
            List<PortfolioSnapshot> snapshots,
            Money currentValue) {
        SimulationResponse base = toResponse(simulation);
        List<ContributionResponse> contributionResponses = contributions == null
                ? List.of()
                : contributions.stream().map(ContributionResponse::fromDomain).toList();

        BigDecimal totalInvested = simulation.getInitialCash() == null
                ? null
                : PerformanceCalculator.totalInvested(simulation.getInitialCash(), contributions).amount();
        BigDecimal twrPercent = PerformanceCalculator.asPercent(
                PerformanceCalculator.computeTwr(snapshots, contributions));
        // MWR is annualised, so it has to be measured on the simulation's own time axis, not on
        // how long the run took to execute. Using startedAt -> now measured wall-clock: a backtest
        // covering seven simulated months but finishing in fifteen minutes annualised its return
        // over ~0.002 years and reported a nonsensical -99%.
        Instant mwrStart = simulation.getStartDate() != null
                ? simulation.getStartDate()
                : simulation.getStartedAt();
        Instant mwrAsOf = PerformanceCalculator.resolveMwrAsOf(snapshots, simulation.getEndDate());
        BigDecimal mwrPercent = PerformanceCalculator.asPercent(
                PerformanceCalculator.computeMwr(
                        simulation.getInitialCash(),
                        mwrStart,
                        contributions,
                        currentValue,
                        mwrAsOf));
        BigDecimal maxDrawdownPercent = PerformanceCalculator.asPercent(
                PerformanceCalculator.computeMaxDrawdown(snapshots, contributions));

        return new SimulationResponse(
                base.id(), base.strategyId(), base.startDate(), base.endDate(),
                base.status(), base.name(), base.progress(), base.paused(),
                base.pausedForExternalReason(), base.externalPauseReason(),
                base.createdAt(), base.startedAt(), base.completedAt(), base.failureReason(),
                base.benchmarkId(), base.brokerType(), base.brokerPreset(),
                base.commissionRate(), base.cryptoCommissionRate(), base.slippageBase(),
                base.backfillJobId(), base.skippedEvaluationCount(), base.totalEvaluationCount(),
                base.initialCash(),
                totalInvested, twrPercent, mwrPercent, maxDrawdownPercent, contributionResponses,
                base.strategyLineage(),
                base.notifyDecisions());
    }
}
