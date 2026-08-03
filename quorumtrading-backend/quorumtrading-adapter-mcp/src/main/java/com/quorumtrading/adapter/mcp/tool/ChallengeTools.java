package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.adapter.mcp.dto.McpPaginatedResponse;
import com.quorumtrading.application.port.out.ChallengeRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.BuildChallengeLeaderboardUseCase;
import com.quorumtrading.application.usecase.JoinChallengeUseCase;
import com.quorumtrading.application.usecase.command.JoinChallengeCommand;
import com.quorumtrading.application.usecase.dto.ChallengeLeaderboard;
import com.quorumtrading.application.usecase.query.BuildChallengeLeaderboardQuery;
import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Singleton
@ActivateRequestContext
public class ChallengeTools extends McpToolBase {

    @Inject ChallengeRepository challengeRepository;
    @Inject SimulationRepository simulationRepository;
    @Inject StrategyRepository strategyRepository;
    @Inject PortfolioRepository portfolioRepository;
    @Inject JoinChallengeUseCase joinChallengeUseCase;
    @Inject BuildChallengeLeaderboardUseCase buildChallengeLeaderboardUseCase;
    @Inject McpUserContext userContext;
    @Inject com.quorumtrading.adapter.mcp.mapper.UiLinks uiLinks;

    // list_challenges and get_challenge deliberately do not resolve a user: challenges are a
    // public leaderboard, and /mcp is open at the HTTP layer, so they are reachable without a
    // token by design. Every other tool that omits getUserId() is a bug -- keep this note so
    // the omission reads as intentional. Entries expose participant display names and costs,
    // which is the point of a leaderboard; do not widen them to private data.
    @Tool(name = "list_challenges", description = "List all challenges. Filter by status: UPCOMING, "
        + "ACTIVE, COMPLETED. Shows name, ticker, dates and participant count; topPerformerStrategy "
        + "and topPerformerValue appear only on challenges that already have entries, so a challenge "
        + "with participantCount 0 carries neither.")
    public ToolResponse listChallenges(
        @ToolArg(description = "Filter by status: UPCOMING, ACTIVE, COMPLETED (optional)") Optional<String> status,
        @ToolArg(description = "Maximum number of results (default 20, max 50)") Optional<Integer> limit
    ) {
        if (!isFeatureEnabled(Feature.CHALLENGES)) {
            return featureDisabled(Feature.CHALLENGES);
        }
        return execute(() -> {
            int effectiveLimit = parseLimit(limit, 20, 50);
            List<Challenge> challenges = challengeRepository.findAllSorted(effectiveLimit);

            if (status.isPresent()) {
                var filter = parseEnum(com.quorumtrading.model.enums.ChallengeStatus.class,
                    status.get(), "status");
                challenges = challenges.stream()
                    .filter(c -> c.getStatus() == filter)
                    .toList();
            }

            List<Map<String, Object>> dtos = challenges.stream()
                .map(c -> {
                    List<Simulation> entries = simulationRepository.findByChallengeId(c.getChallengeId());
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", c.getChallengeId().value().toString());
                    String listUiUrl = uiLinks.challenge(c.getChallengeId().value().toString());
                    if (listUiUrl != null) {
                        dto.put("uiUrl", listUiUrl);
                    }
                    dto.put("name", c.getName());
                    dto.put("ticker", c.getTicker().ticker());
                    dto.put("initialCash", c.getInitialCash().amount());
                    dto.put("startDate", c.getSimulationStartDate().toString());
                    dto.put("endDate", c.getSimulationEndDate().toString());
                    dto.put("status", c.getStatus().name());
                    dto.put("acceptingEntries", c.isAcceptingEntries());
                    dto.put("strategiesPublic", c.isStrategiesPublic());
                    dto.put("participantCount", entries.size());

                    // Top performer
                    if (!entries.isEmpty()) {
                        entries.stream()
                            .map(sim -> {
                                BigDecimal val = portfolioRepository.findLatestBySimulationId(sim.getSimulationId())
                                    .map(snap -> snap.getTotalValue().amount())
                                    .orElse(sim.getInitialCash().amount());
                                return Map.entry(sim, val);
                            })
                            .max(Comparator.comparing(Map.Entry::getValue))
                            .ifPresent(top -> {
                                dto.put("topPerformerValue", top.getValue());
                                String stratName = strategyRepository.findById(top.getKey().getStrategyId())
                                    .map(Strategy::getName).orElse("Unknown");
                                dto.put("topPerformerStrategy", stratName);
                            });
                    }
                    return dto;
                })
                .toList();

            return toJson(new McpPaginatedResponse<>(dtos, dtos.size(), false));
        });
    }

    @Tool(name = "get_challenge", description = "Get detailed information about a specific challenge including ranked entries with LLM cost and execution time.")
    public ToolResponse getChallenge(
        @ToolArg(description = "The challenge UUID") String challengeId
    ) {
        if (!isFeatureEnabled(Feature.CHALLENGES)) {
            return featureDisabled(Feature.CHALLENGES);
        }
        return execute(() -> {
            ChallengeId id = new ChallengeId(UUID.fromString(challengeId));
            Challenge challenge = challengeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));

            // Ranking, return maths and visibility all live in the application layer: this tool
            // and the REST leaderboard used to compute entries independently and disagreed,
            // MCP counting DCA contributions as profit. /mcp is open at the HTTP layer, so this
            // tool asks anonymously and therefore never sees anyone's in-progress backtest.
            ChallengeLeaderboard leaderboard = buildChallengeLeaderboardUseCase.execute(
                BuildChallengeLeaderboardQuery.anonymous(id));

            List<Map<String, Object>> entries = new ArrayList<>();
            for (ChallengeLeaderboard.Entry e : leaderboard.entries()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("simulationId", e.simulationId().value().toString());
                entry.put("simulationName", e.simulationName());
                entry.put("strategyName", e.strategyName());
                entry.put("ownerDisplayName", e.ownerDisplayName());
                entry.put("currentValue", e.currentValue().amount());
                entry.put("returnPercent", e.returnPercent() != null
                    ? e.returnPercent().setScale(2, RoundingMode.HALF_UP).toPlainString()
                    : null);
                entry.put("status", e.status().name());
                entry.put("llmCostUsd", e.llmCostUsd());
                entry.put("executionTimeSeconds", e.executionTimeSeconds());
                entry.put("rank", e.rank());
                entries.add(entry);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", challenge.getChallengeId().value().toString());
            String detailUiUrl = uiLinks.challenge(challenge.getChallengeId().value().toString());
            if (detailUiUrl != null) {
                result.put("uiUrl", detailUiUrl);
            }
            result.put("name", challenge.getName());
            result.put("description", challenge.getDescription());
            result.put("ticker", challenge.getTicker().ticker());
            result.put("initialCash", challenge.getInitialCash().amount());
            result.put("simulationStartDate", challenge.getSimulationStartDate().toString());
            result.put("simulationEndDate", challenge.getSimulationEndDate().toString());
            result.put("participationOpen", challenge.getParticipationOpen().toString());
            result.put("participationClose", challenge.getParticipationClose().toString());
            result.put("isBacktest", challenge.isBacktest());
            result.put("status", challenge.getStatus().name());
            result.put("acceptingEntries", challenge.isAcceptingEntries());
            result.put("strategiesPublic", challenge.isStrategiesPublic());
            result.put("participantCount", leaderboard.totalParticipants());
            result.put("entries", entries);

            return toJson(result);
        });
    }

    @Tool(name = "join_challenge", description = "Join a challenge with a strategy. A simulation is "
        + "created automatically with the challenge's constraints (ticker, dates, initial cash). If "
        + "you already have an entry, the old one is replaced. "
        + "SELF-HOSTED: challenges are gated by the 'challenges' feature flag, off by default "
        + "because a leaderboard needs other participants. With it on, a local install still "
        + "ships only closed historical challenges and cannot open new ones, so joining still "
        + "fails — use benchmarks (compose_benchmark + get_benchmark_overlays) to compare your "
        + "own strategies instead.")
    public ToolResponse joinChallenge(
        @ToolArg(description = "The challenge UUID to join") String challengeId,
        @ToolArg(description = "The strategy UUID to use for this challenge") String strategyId
    ) {
        if (!isFeatureEnabled(Feature.CHALLENGES)) {
            return featureDisabled(Feature.CHALLENGES);
        }
        return execute(() -> {
            UserId userId = userContext.getUserId();

            JoinChallengeCommand command = new JoinChallengeCommand(
                new ChallengeId(UUID.fromString(challengeId)),
                new StrategyId(UUID.fromString(strategyId)),
                userId
            );
            SimulationId simulationId = joinChallengeUseCase.execute(command);

            logger.info("MCP: User {} joined challenge {} with strategy {}, simulation {}",
                userId, challengeId, strategyId, simulationId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Successfully joined challenge. A simulation has been created and will start automatically.");
            response.put("challengeId", challengeId);
            response.put("strategyId", strategyId);
            response.put("simulationId", simulationId.value().toString());
            // The entry the user will want to watch is the simulation this join created.
            String simulationUiUrl = uiLinks.simulation(simulationId.value().toString());
            if (simulationUiUrl != null) {
                response.put("uiUrl", simulationUiUrl);
            }
            return toJson(response);
        });
    }
}
