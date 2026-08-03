package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ChallengeRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.BuildChallengeLeaderboardUseCase;
import com.quorumtrading.application.usecase.dto.ChallengeLeaderboard;
import com.quorumtrading.application.usecase.query.BuildChallengeLeaderboardQuery;
import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.service.PerformanceCalculator;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Ranks the entries of a challenge.
 *
 * <p>Ranking metric is current portfolio value, descending — the leaderboard answers "who has
 * the most money right now", so a late entrant with a high percentage return does not outrank a
 * larger portfolio. The reported return percentage is measured against total invested
 * (seed + Σ contributions) so that DCA top-ups are not booked as profit.</p>
 */
public class BuildChallengeLeaderboardService implements BuildChallengeLeaderboardUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(BuildChallengeLeaderboardService.class);

    /** Entry states shown to every viewer. */
    private static final Set<SimulationStatus> PUBLIC_STATUSES =
        EnumSet.of(SimulationStatus.LIVE, SimulationStatus.COMPLETED);

    /** Additional states a viewer may see for their own entry, so you can watch your backtest run. */
    private static final Set<SimulationStatus> OWN_ONLY_STATUSES =
        EnumSet.of(SimulationStatus.SIMULATING);

    private final ChallengeRepository challengeRepository;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final SimulationContributionRepository contributionRepository;

    public BuildChallengeLeaderboardService(ChallengeRepository challengeRepository,
                                            SimulationRepository simulationRepository,
                                            StrategyRepository strategyRepository,
                                            UserRepository userRepository,
                                            PortfolioRepository portfolioRepository,
                                            SimulationContributionRepository contributionRepository) {
        this.challengeRepository = challengeRepository;
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.contributionRepository = contributionRepository;
    }

    @Override
    public ChallengeLeaderboard execute(BuildChallengeLeaderboardQuery query) {
        Challenge challenge = challengeRepository.findById(query.challengeId())
            .orElseThrow(() -> new NoSuchElementException(
                "Challenge not found: " + query.challengeId().value()));

        List<Simulation> allEntries = simulationRepository.findByChallengeId(challenge.getChallengeId());

        // One lookup per distinct strategy rather than three per simulation: name, owner and
        // ownership test all resolve from the same row.
        Map<StrategyId, Optional<Strategy>> strategies = new HashMap<>();
        for (Simulation sim : allEntries) {
            strategies.computeIfAbsent(sim.getStrategyId(), strategyRepository::findById);
        }
        Map<UserId, String> displayNames = new HashMap<>();

        List<ChallengeLeaderboard.Entry> visible = new ArrayList<>();
        for (Simulation sim : allEntries) {
            Optional<Strategy> strategy = strategies.getOrDefault(sim.getStrategyId(), Optional.empty());
            boolean owned = query.viewerId() != null
                && strategy.map(s -> query.viewerId().equals(s.getOwnerId())).orElse(false);
            if (!isVisible(sim.getStatus(), owned)) {
                continue;
            }
            visible.add(buildEntry(sim, strategy, owned, displayNames));
        }

        visible.sort(Comparator.comparing(
            (ChallengeLeaderboard.Entry e) -> e.currentValue().amount()).reversed());

        List<ChallengeLeaderboard.Entry> ranked = new ArrayList<>(visible.size());
        for (int i = 0; i < visible.size(); i++) {
            ranked.add(visible.get(i).withRank(i + 1));
        }

        LOG.debug("Challenge leaderboard {}: {} of {} entries visible to viewer {}",
            challenge.getChallengeId().value(), ranked.size(), allEntries.size(), query.viewerId());

        return new ChallengeLeaderboard(allEntries.size(), ranked);
    }

    private boolean isVisible(SimulationStatus status, boolean ownedByViewer) {
        if (PUBLIC_STATUSES.contains(status)) {
            return true;
        }
        return ownedByViewer && OWN_ONLY_STATUSES.contains(status);
    }

    private ChallengeLeaderboard.Entry buildEntry(Simulation sim,
                                                  Optional<Strategy> strategy,
                                                  boolean ownedByViewer,
                                                  Map<UserId, String> displayNameCache) {
        String strategyName = strategy.map(Strategy::getName).orElse("Unknown");
        String ownerName = strategy
            .map(s -> displayNameCache.computeIfAbsent(s.getOwnerId(), this::resolveDisplayName))
            .orElse("Anonymous");

        Money seed = sim.getInitialCash();
        Money currentValue = portfolioRepository.findLatestBySimulationId(sim.getSimulationId())
            .map(PortfolioSnapshot::getTotalValue)
            .orElse(seed != null ? seed : Money.ZERO_USD);

        BigDecimal returnPercent = null;
        if (seed != null) {
            List<Contribution> contributions =
                contributionRepository.findBySimulationId(sim.getSimulationId());
            returnPercent = PerformanceCalculator.simpleReturnPercent(seed, contributions, currentValue);
        }

        Long executionTimeSeconds = null;
        if (sim.getStartedAt() != null) {
            Instant endAt = sim.getCompletedAt() != null ? sim.getCompletedAt() : Instant.now();
            executionTimeSeconds = Duration.between(sim.getStartedAt(), endAt).toSeconds();
        }

        return new ChallengeLeaderboard.Entry(
            0,
            sim.getSimulationId(),
            sim.getName(),
            strategyName,
            ownerName,
            currentValue,
            returnPercent,
            sim.getStatus(),
            sim.getArchivedLLMCostUsd(),
            executionTimeSeconds,
            ownedByViewer
        );
    }

    private String resolveDisplayName(UserId ownerId) {
        return userRepository.findById(ownerId).map(User::getDisplayName).orElse("Anonymous");
    }
}
