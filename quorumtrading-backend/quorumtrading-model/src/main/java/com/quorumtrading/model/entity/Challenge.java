package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.ChallengeStatus;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.ComplianceResult;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Domain entity representing a trading challenge for gamification.
 *
 * <p>A challenge has two distinct date ranges:</p>
 * <ul>
 *   <li><b>Simulation period</b> ({@code simulationStartDate}/{@code simulationEndDate}):
 *       When the trading simulation runs. Can be historical (backtest challenge)
 *       or future/present (live challenge).</li>
 *   <li><b>Participation window</b> ({@code participationOpen}/{@code participationClose}):
 *       When users can join the challenge. Status is derived from this window.</li>
 * </ul>
 *
 * <p><b>Challenge types (derived, not stored):</b></p>
 * <ul>
 *   <li><b>Backtest:</b> simulationEndDate is before participationOpen — simulations replay historical data</li>
 *   <li><b>Live:</b> simulation period overlaps with participation window</li>
 * </ul>
 *
 * @see Simulation
 */
public class Challenge implements AggregateRoot {

    private ChallengeId challengeId;
    private String slug;
    private String name;
    private String description;
    private Symbol ticker;
    private Money initialCash;
    private Instant simulationStartDate;
    private Instant simulationEndDate;
    private Instant participationOpen;
    private Instant participationClose;
    private Instant createdAt;
    private UserId createdBy;
    private boolean strategiesPublic;

    /** No-arg constructor for reconstitution only. */
    private Challenge() {}

    public Challenge(
        ChallengeId challengeId,
        String name,
        String description,
        Symbol ticker,
        Money initialCash,
        Instant simulationStartDate,
        Instant simulationEndDate,
        Instant participationOpen,
        Instant participationClose,
        Instant createdAt,
        UserId createdBy,
        boolean strategiesPublic
    ) {
        if (challengeId == null) throw new IllegalArgumentException("ChallengeId cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Challenge name cannot be null or blank");
        if (ticker == null) throw new IllegalArgumentException("Ticker cannot be null");
        if (initialCash == null || initialCash.amount().signum() <= 0) throw new IllegalArgumentException("Initial cash must be positive");
        if (simulationStartDate == null) throw new IllegalArgumentException("Simulation start date cannot be null");
        if (simulationEndDate == null) throw new IllegalArgumentException("Simulation end date cannot be null");
        if (!simulationEndDate.isAfter(simulationStartDate)) throw new IllegalArgumentException("Simulation end date must be after start date");
        if (participationOpen == null) throw new IllegalArgumentException("Participation open date cannot be null");
        if (participationClose == null) throw new IllegalArgumentException("Participation close date cannot be null");
        if (!participationClose.isAfter(participationOpen)) throw new IllegalArgumentException("Participation close must be after open");
        if (createdAt == null) throw new IllegalArgumentException("Created timestamp cannot be null");
        if (createdBy == null) throw new IllegalArgumentException("CreatedBy cannot be null");

        this.challengeId = challengeId;
        this.slug = slugify(name);
        this.name = name;
        this.description = description;
        this.ticker = ticker;
        this.initialCash = initialCash;
        this.simulationStartDate = simulationStartDate;
        this.simulationEndDate = simulationEndDate;
        this.participationOpen = participationOpen;
        this.participationClose = participationClose;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.strategiesPublic = strategiesPublic;
    }

    public static Challenge reconstitute(
        ChallengeId challengeId, String slug, String name, String description, Symbol ticker, Money initialCash,
        Instant simulationStartDate, Instant simulationEndDate,
        Instant participationOpen, Instant participationClose,
        Instant createdAt, UserId createdBy, boolean strategiesPublic
    ) {
        Challenge c = new Challenge();
        c.challengeId = challengeId;
        c.slug = slug;
        c.name = name;
        c.description = description;
        c.ticker = ticker;
        c.initialCash = initialCash;
        c.simulationStartDate = simulationStartDate;
        c.simulationEndDate = simulationEndDate;
        c.participationOpen = participationOpen;
        c.participationClose = participationClose;
        c.createdAt = createdAt;
        c.createdBy = createdBy;
        c.strategiesPublic = strategiesPublic;
        return c;
    }

    // Getters
    public ChallengeId getChallengeId() { return challengeId; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Symbol getTicker() { return ticker; }
    public Money getInitialCash() { return initialCash; }
    public Instant getSimulationStartDate() { return simulationStartDate; }
    public Instant getSimulationEndDate() { return simulationEndDate; }
    public Instant getParticipationOpen() { return participationOpen; }
    public Instant getParticipationClose() { return participationClose; }
    public Instant getCreatedAt() { return createdAt; }
    public UserId getCreatedBy() { return createdBy; }
    public boolean isStrategiesPublic() { return strategiesPublic; }

    // Business methods

    /**
     * Status derived from the participation window.
     */
    public ChallengeStatus getStatus() {
        Instant now = Instant.now();
        if (now.isBefore(participationOpen)) return ChallengeStatus.UPCOMING;
        if (now.isAfter(participationClose)) return ChallengeStatus.COMPLETED;
        return ChallengeStatus.ACTIVE;
    }

    /**
     * Whether users can currently submit entries.
     */
    public boolean isAcceptingEntries() {
        Instant now = Instant.now();
        return !now.isBefore(participationOpen) && !now.isAfter(participationClose);
    }

    /**
     * Backtest challenge: simulation period is entirely in the past relative to participation window.
     */
    public boolean isBacktest() {
        return simulationEndDate.isBefore(participationOpen);
    }

    /**
     * Live challenge: simulation period overlaps with or extends beyond participation window.
     */
    public boolean isLive() {
        return !isBacktest();
    }

    @Override
    public String toString() {
        return "Challenge{" +
                "challengeId=" + challengeId +
                ", name='" + name + '\'' +
                ", ticker=" + ticker +
                ", initialCash=" + initialCash +
                ", simStart=" + simulationStartDate +
                ", simEnd=" + simulationEndDate +
                ", partOpen=" + participationOpen +
                ", partClose=" + participationClose +
                ", strategiesPublic=" + strategiesPublic +
                '}';
    }

    /**
     * Check if a strategy configuration complies with this challenge's requirements.
     *
     * <p><b>Extensibility:</b> New challenge criteria (max tickers, required agents,
     * consensus method, position limits, etc.) are added here as additional checks.
     * All consumers — join flow, eligible strategies endpoint, frontend — benefit
     * from the same validation.</p>
     *
     * @param config the parsed strategy configuration
     * @return compliance result with structured violations
     */
    public ComplianceResult checkCompliance(StrategyConfig config) {
        List<String> violations = new ArrayList<>();

        // Requirement: strategy must trade the challenge ticker
        Set<String> allTickers = new HashSet<>();
        if (config.getStockTickers() != null) {
            config.getStockTickers().forEach(s -> allTickers.add(s.ticker()));
        }
        if (config.getCryptoTickers() != null) {
            config.getCryptoTickers().forEach(s -> allTickers.add(s.ticker()));
        }

        if (!allTickers.contains(ticker.ticker())) {
            violations.add("Strategy must trade " + ticker.ticker()
                + " (currently trades: " + String.join(", ", allTickers) + ")");
        }

        // Future requirements added here:
        // - Max tickers: if (allTickers.size() > maxTickers) ...
        // - Required consensus method: if (!config.getConsensusMethod().equals(...)) ...
        // - Required agents: if (!config.getAgents().containsAll(requiredAgents)) ...
        // - Asset type restriction: if (ticker.type() == CRYPTO && !config.getCryptoTickers().contains(...)) ...

        return violations.isEmpty() ? ComplianceResult.pass() : ComplianceResult.fail(violations);
    }

    /**
     * Convert a challenge name to a URL-safe slug.
     * "COVID Crash MSFT Survivor" → "covid-crash-msft-survivor"
     */
    public static String slugify(String name) {
        return name.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Challenge that = (Challenge) o;
        return challengeId.equals(that.challengeId);
    }

    @Override
    public int hashCode() {
        return challengeId.hashCode();
    }
}
