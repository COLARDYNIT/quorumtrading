package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.CreateChallengeRequest;
import com.quorumtrading.adapter.rest.dto.request.JoinChallengeRequest;
import com.quorumtrading.adapter.rest.dto.response.ChallengeResponse;
import com.quorumtrading.adapter.rest.dto.response.ChallengeEntryResponse;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.ChallengeRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.BuildChallengeLeaderboardUseCase;
import com.quorumtrading.application.usecase.CreateChallengeUseCase;
import com.quorumtrading.application.usecase.DeleteChallengeUseCase;
import com.quorumtrading.application.usecase.JoinChallengeUseCase;
import com.quorumtrading.application.usecase.dto.ChallengeLeaderboard;
import com.quorumtrading.application.usecase.query.BuildChallengeLeaderboardQuery;
import com.quorumtrading.application.usecase.command.CreateChallengeCommand;
import com.quorumtrading.application.usecase.command.DeleteChallengeCommand;
import com.quorumtrading.application.usecase.command.JoinChallengeCommand;
import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.ChallengeStatus;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.SymbolParser;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/v1/challenges")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Challenges", description = "Challenge management for gamification")
@PermitAll
public class ChallengeResource {

    private static final Logger LOG = Logger.getLogger(ChallengeResource.class);

    @Inject CreateChallengeUseCase createChallengeUseCase;
    @Inject BuildChallengeLeaderboardUseCase buildChallengeLeaderboardUseCase;
    @Inject JoinChallengeUseCase joinChallengeUseCase;
    @Inject DeleteChallengeUseCase deleteChallengeUseCase;
    @Inject ChallengeRepository challengeRepository;
    @Inject SimulationRepository simulationRepository;
    @Inject StrategyRepository strategyRepository;
    @Inject UserRepository userRepository;
    @Inject PortfolioRepository portfolioRepository;
    @Inject CurrentUserProvider currentUser;
    @Inject com.quorumtrading.application.service.BuyAndHoldOverlayCalculator buyAndHoldCalculator;

    // ===== Endpoints =====

    @POST
    @Operation(summary = "Create a new challenge (admin only)")
    @Authenticated
    public Response createChallenge(CreateChallengeRequest request) {
        try {
            UserId userId = currentUser.getCurrentUserIdOptional()
                .orElseThrow(() -> new NotAuthorizedException("Authentication required"));

            InvestmentType type = request.investmentType() != null
                ? InvestmentType.valueOf(request.investmentType().toUpperCase())
                : InvestmentType.STOCK;

            CreateChallengeCommand command = new CreateChallengeCommand(
                request.name(),
                request.description(),
                SymbolParser.parse(request.ticker(), type),
                new Money(request.initialCash(), Money.Currency.USD),
                Instant.parse(request.simulationStartDate()),
                Instant.parse(request.simulationEndDate()),
                Instant.parse(request.participationOpen()),
                Instant.parse(request.participationClose()),
                userId,
                request.strategiesPublic()
            );

            Challenge challenge = createChallengeUseCase.execute(command);
            ChallengeResponse dto = toChallengeResponse(challenge);
            return Response.status(Response.Status.CREATED).entity(dto).build();

        } catch (AccessDeniedException e) {
            throw new ForbiddenException("Only admins can create challenges");
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @GET
    @Operation(summary = "List all challenges")
    public Response listChallenges(@QueryParam("status") String statusFilter) {
        List<Challenge> challenges = challengeRepository.findAll();

        if (statusFilter != null && !statusFilter.isBlank()) {
            ChallengeStatus filter = ChallengeStatus.valueOf(statusFilter.toUpperCase());
            challenges = challenges.stream()
                .filter(c -> c.getStatus() == filter)
                .collect(Collectors.toList());
        }

        List<ChallengeResponse> response = challenges.stream()
            .map(this::toChallengeResponse)
            .collect(Collectors.toList());

        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get challenge detail with ranked entries")
    public Response getChallenge(@PathParam("id") String id) {
        Challenge challenge = resolveChallenge(id);

        return Response.ok(toChallengeResponse(challenge)).build();
    }

    @GET
    @Path("/{id}/eligible-strategies")
    @Operation(summary = "List user's strategies with compliance status for this challenge")
    @Authenticated
    public Response getEligibleStrategies(@PathParam("id") String id) {
        Challenge challenge = resolveChallenge(id);
        UserId userId = currentUser.getCurrentUserIdOptional()
            .orElseThrow(() -> new NotAuthorizedException("Authentication required"));

        List<Strategy> strategies = strategyRepository.findByOwnerId(userId);
        var parser = new com.quorumtrading.model.orchestration.StrategyConfigParser();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Strategy strategy : strategies) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", strategy.getId().value().toString());
            entry.put("name", strategy.getName());

            try {
                var config = parser.parse(strategy.getContent());
                var compliance = challenge.checkCompliance(config);
                entry.put("compliant", compliance.compliant());
                entry.put("violations", compliance.violations());
            } catch (Exception e) {
                entry.put("compliant", false);
                entry.put("violations", List.of("Could not parse strategy configuration"));
            }
            result.add(entry);
        }

        // Sort: compliant first, then by name
        result.sort((a, b) -> {
            boolean aOk = (boolean) a.get("compliant");
            boolean bOk = (boolean) b.get("compliant");
            if (aOk != bOk) return aOk ? -1 : 1;
            return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
        });

        return Response.ok(result).build();
    }

    @POST
    @Path("/{id}/join")
    @Operation(summary = "Join a challenge with a simulation")
    @Authenticated
    public Response joinChallenge(@PathParam("id") String id, JoinChallengeRequest request) {
        try {
            Challenge challenge = resolveChallenge(id);
            UserId userId = currentUser.getCurrentUserIdOptional()
                .orElseThrow(() -> new NotAuthorizedException("Authentication required"));

            JoinChallengeCommand command = new JoinChallengeCommand(
                challenge.getChallengeId(),
                new StrategyId(UUID.fromString(request.strategyId())),
                userId
            );
            SimulationId simulationId = joinChallengeUseCase.execute(command);

            return Response.ok(Map.of(
                "message", "Successfully joined challenge",
                "simulationId", simulationId.value().toString()
            )).build();

        } catch (NoSuchElementException e) {
            throw new NotFoundException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        } catch (AccessDeniedException e) {
            throw new ForbiddenException(e.getMessage());
        }
        // IllegalStateException intentionally propagates to the global mapper (409 Conflict)
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a challenge (admin only)")
    @Authenticated
    public Response deleteChallenge(@PathParam("id") String id) {
        try {
            Challenge challenge = resolveChallenge(id);
            UserId userId = currentUser.getCurrentUserIdOptional()
                .orElseThrow(() -> new NotAuthorizedException("Authentication required"));

            deleteChallengeUseCase.execute(new DeleteChallengeCommand(
                challenge.getChallengeId(), userId));
            return Response.noContent().build();

        } catch (NoSuchElementException e) {
            throw new NotFoundException("Challenge not found: " + id);
        } catch (AccessDeniedException e) {
            throw new ForbiddenException("Only admins can delete challenges");
        }
    }

    // ===== Chart Data (reuses benchmark pattern) =====

    @GET
    @Path("/{id}/chart-data")
    @Operation(summary = "Get compact chart data for challenge entries, including buy & hold baseline")
    public Response getChallengeChartData(@PathParam("id") String id) {
        try {
            Challenge challenge = resolveChallenge(id);
            ChallengeId challengeId = challenge.getChallengeId();

            List<Simulation> simulations = simulationRepository.findByChallengeId(challengeId).stream()
                .filter(s -> !"FAILED".equals(s.getStatus().name()) && !"PENDING".equals(s.getStatus().name()))
                .toList();

            // Build compact chart data
            List<Map<String, Object>> simulationInfos = new ArrayList<>();
            List<List<Object>> allPoints = new ArrayList<>();

            Set<Long> allTimestamps = new TreeSet<>();
            Map<SimulationId, List<PortfolioSnapshot>> snapshotsBySimulation = new LinkedHashMap<>();

            for (Simulation sim : simulations) {
                List<PortfolioSnapshot> snapshots = portfolioRepository.findAllBySimulationId(sim.getSimulationId());
                snapshotsBySimulation.put(sim.getSimulationId(), snapshots);
                snapshots.forEach(s -> allTimestamps.add(s.getTimestamp().toEpochMilli()));

                String strategyName = strategyRepository.findById(sim.getStrategyId())
                    .map(Strategy::getName).orElse("Unknown");
                String ownerName = getOwnerDisplayName(sim);

                Map<String, Object> info = new LinkedHashMap<>();
                info.put("id", sim.getSimulationId().value().toString());
                info.put("name", sim.getName());
                info.put("strategyName", strategyName);
                info.put("ownerDisplayName", ownerName);
                info.put("status", sim.getStatus().name());
                info.put("startValue", sim.getInitialCash().amount());
                simulationInfos.add(info);
            }

            // Compute buy & hold overlay for the challenge ticker
            Map<String, Object> buyAndHold = null;
            com.quorumtrading.model.valueobject.OverlayResult bhOverlay = null;
            try {
                bhOverlay = buyAndHoldCalculator.computeOverlay(
                    challenge.getTicker(),
                    challenge.getSimulationStartDate(),
                    challenge.getSimulationEndDate(),
                    challenge.getInitialCash().amount(),
                    "1Day"
                );
                if (bhOverlay.status() == com.quorumtrading.model.valueobject.OverlayResult.OverlayStatus.READY
                        && bhOverlay.dataPoints() != null && !bhOverlay.dataPoints().isEmpty()) {
                    bhOverlay.dataPoints().forEach(dp -> allTimestamps.add(dp.timestamp().toEpochMilli()));

                    buyAndHold = new LinkedHashMap<>();
                    buyAndHold.put("ticker", challenge.getTicker().ticker());
                    buyAndHold.put("returnPercent", bhOverlay.returnPercent());
                    buyAndHold.put("finalValue", bhOverlay.dataPoints().get(bhOverlay.dataPoints().size() - 1).portfolioValue());
                }
            } catch (Exception e) {
                LOG.warnf("Failed to compute buy & hold overlay for challenge %s ticker %s: %s",
                    id, challenge.getTicker().ticker(), e.getMessage());
            }

            // Build columnar data
            List<Long> timestamps = new ArrayList<>(allTimestamps);
            allPoints.add(timestamps.stream().map(t -> (Object) t).collect(Collectors.toList()));

            // Simulation columns
            for (Simulation sim : simulations) {
                List<PortfolioSnapshot> snapshots = snapshotsBySimulation.get(sim.getSimulationId());
                Map<Long, BigDecimal> valueByTimestamp = new HashMap<>();
                snapshots.forEach(s -> valueByTimestamp.put(
                    s.getTimestamp().toEpochMilli(), s.getTotalValue().amount()));

                List<Object> column = new ArrayList<>();
                BigDecimal lastValue = null;
                for (Long ts : timestamps) {
                    BigDecimal val = valueByTimestamp.get(ts);
                    if (val != null) lastValue = val;
                    column.add(lastValue);
                }
                allPoints.add(column);
            }

            // Buy & hold column (last in points array)
            if (buyAndHold != null && bhOverlay != null) {
                Map<Long, BigDecimal> bhByTimestamp = new HashMap<>();
                bhOverlay.dataPoints().forEach(dp -> bhByTimestamp.put(
                    dp.timestamp().toEpochMilli(), dp.portfolioValue()));

                List<Object> bhColumn = new ArrayList<>();
                BigDecimal lastBh = null;
                for (Long ts : timestamps) {
                    BigDecimal val = bhByTimestamp.get(ts);
                    if (val != null) lastBh = val;
                    bhColumn.add(lastBh);
                }
                allPoints.add(bhColumn);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("simulations", simulationInfos);
            result.put("points", allPoints);
            result.put("buyAndHold", buyAndHold); // null if no data
            return Response.ok(result).build();

        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid UUID format: " + id);
        }
    }

    // ===== Mapping helpers =====

    /** How many ranked entries the challenge page shows before pinning the viewer's own. */
    private static final int LEADERBOARD_SIZE = 5;

    private ChallengeResponse toChallengeResponse(Challenge challenge) {
        UserId currentUserId = currentUser.getCurrentUserIdOptional().orElse(null);
        ChallengeLeaderboard leaderboard = buildChallengeLeaderboardUseCase.execute(
            new BuildChallengeLeaderboardQuery(challenge.getChallengeId(), currentUserId));
        List<ChallengeEntryResponse> entries = topEntriesPlusOwn(leaderboard);

        return new ChallengeResponse(
            challenge.getChallengeId().value().toString(),
            challenge.getSlug(),
            challenge.getName(),
            challenge.getDescription(),
            challenge.getTicker().ticker(),
            challenge.getTicker().type().name(),
            challenge.getInitialCash().amount(),
            challenge.getSimulationStartDate().toString(),
            challenge.getSimulationEndDate().toString(),
            challenge.getParticipationOpen().toString(),
            challenge.getParticipationClose().toString(),
            challenge.getStatus().name(),
            challenge.isBacktest(),
            challenge.isAcceptingEntries(),
            leaderboard.totalParticipants(), // includes entries not shown (pending, others' in-progress)
            challenge.getCreatedAt().toString(),
            challenge.isStrategiesPublic(),
            entries
        );
    }

    /**
     * The leaderboard head, plus the viewer's own entry pinned on the end when it ranked below
     * the cut — so you can always see where you stand without paging.
     */
    private List<ChallengeEntryResponse> topEntriesPlusOwn(ChallengeLeaderboard leaderboard) {
        List<ChallengeLeaderboard.Entry> ranked = leaderboard.entries();
        if (ranked.size() <= LEADERBOARD_SIZE) {
            return ranked.stream().map(this::toEntryResponse).toList();
        }

        List<ChallengeLeaderboard.Entry> shown =
            new ArrayList<>(ranked.subList(0, LEADERBOARD_SIZE));
        if (shown.stream().noneMatch(ChallengeLeaderboard.Entry::ownedByViewer)) {
            ranked.stream()
                .filter(ChallengeLeaderboard.Entry::ownedByViewer)
                .findFirst()
                .ifPresent(shown::add);
        }
        return shown.stream().map(this::toEntryResponse).toList();
    }

    private ChallengeEntryResponse toEntryResponse(ChallengeLeaderboard.Entry entry) {
        String returnPct = entry.returnPercent() != null
            ? entry.returnPercent().setScale(2, RoundingMode.HALF_UP).toPlainString()
            : null;
        String llmCost = entry.llmCostUsd() != null
            ? entry.llmCostUsd().setScale(4, RoundingMode.HALF_UP).toPlainString()
            : null;

        return new ChallengeEntryResponse(
            entry.rank(),
            entry.simulationId().value().toString(),
            entry.simulationName(),
            entry.strategyName(),
            entry.ownerDisplayName(),
            entry.currentValue().amount(),
            returnPct,
            entry.status().name(),
            llmCost,
            entry.executionTimeSeconds()
        );
    }

    private String getOwnerDisplayName(Simulation sim) {
        return strategyRepository.findById(sim.getStrategyId())
            .flatMap(strategy -> userRepository.findById(strategy.getOwnerId()))
            .map(User::getDisplayName)
            .orElse("Anonymous");
    }

    /**
     * Resolve a challenge by UUID or slug.
     */
    private Challenge resolveChallenge(String id) {
        try {
            ChallengeId challengeId = new ChallengeId(UUID.fromString(id));
            return challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Challenge not found: " + id));
        } catch (IllegalArgumentException e) {
            // Not a UUID — try slug lookup
            return challengeRepository.findBySlug(id)
                .orElseThrow(() -> new NotFoundException("Challenge not found: " + id));
        }
    }
}
