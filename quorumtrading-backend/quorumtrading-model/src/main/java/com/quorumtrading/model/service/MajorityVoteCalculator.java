package com.quorumtrading.model.service;

import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.enums.RecommendationType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pure domain service that aggregates individual agent decisions into a
 * majority-vote outcome. Used for the MAJORITY consensus method and as the
 * fallback when the ORCHESTRATOR_OVERRIDE orchestrator LLM call fails.
 *
 * <p><b>Voting rules:</b></p>
 * <ul>
 *   <li>SKIP agents (failed agents) never vote.</li>
 *   <li>All agents SKIP → {@link Outcome#ALL_SKIPPED} (callers record a SKIP consensus —
 *       a HOLD here would claim the engine decided when it failed to).</li>
 *   <li>Abstain rule: HOLD agents with confidence below the consensus threshold
 *       abstain (they don't count as votes). BUY/SELL votes always count
 *       regardless of confidence. Missing confidence is treated as 0.0.</li>
 *   <li>All non-SKIP agents abstained → {@link Outcome#ALL_ABSTAINED}
 *       (callers default to HOLD).</li>
 *   <li>Winner = recommendation with the most votes; a tie for the most votes
 *       → {@link Outcome#TIE} (callers default to HOLD).</li>
 *   <li>The winning agent is the highest-confidence agent that voted for the
 *       winner (missing confidence ranks as zero).</li>
 * </ul>
 *
 * <p><b>Pure / stateless.</b> No CDI annotations, no infrastructure imports.</p>
 */
public final class MajorityVoteCalculator {

    private MajorityVoteCalculator() {
    }

    /**
     * How the vote resolved. TIE and ALL_ABSTAINED are defaulted to HOLD by callers
     * (agents answered, no direction won); ALL_SKIPPED is recorded as SKIP (no agent
     * answered at all — that is a failure to decide, not a decision).
     */
    public enum Outcome {
        /** Every agent returned SKIP (or there were no decisions) — no voters at all. */
        ALL_SKIPPED,
        /** All non-SKIP agents were low-confidence HOLDs and abstained — no votes cast. */
        ALL_ABSTAINED,
        /** Two or more recommendations tied for the most votes. */
        TIE,
        /** A single recommendation won the vote. */
        WINNER
    }

    /**
     * Aggregate result of a majority vote.
     *
     * @param outcome        how the vote resolved
     * @param winner         winning recommendation ({@code null} unless outcome is {@link Outcome#WINNER})
     * @param winningAgent   highest-confidence agent that voted for the winner
     *                       ({@code null} unless outcome is {@link Outcome#WINNER})
     * @param winningVotes   number of votes the winner received (0 for non-WINNER outcomes)
     * @param totalVotes     number of agents that actually cast a vote (excludes SKIP and abstained)
     * @param abstained      one entry per low-confidence HOLD agent dropped by the gate
     * @param tieDetail      per-recommendation vote counts, e.g. {@code "BUY=1, SELL=1"}
     *                       ({@code null} unless outcome is {@link Outcome#TIE})
     */
    public record MajorityVoteResult(
            Outcome outcome,
            RecommendationType winner,
            LLMDecision winningAgent,
            int winningVotes,
            int totalVotes,
            /**
             * One entry per agent dropped by the confidence gate, naming the agent and the
             * confidence that lost it its vote.
             *
             * <p>This used to be a bare {@code int}. The resulting note read "(1 abstained)",
             * which named nobody — so establishing that it was SENTIMENT at 0.30 against a 0.60
             * threshold meant reading the agent array and comparing each confidence against the
             * strategy frontmatter by hand. The other abstention mechanism (missing news data)
             * has always named its agent and its reason; only this one did not.</p>
             */
            List<String> abstained,
            String tieDetail) {

        /** How many agents the confidence gate dropped. */
        public int abstainedCount() {
            return abstained == null ? 0 : abstained.size();
        }
    }

    /**
     * Compute the majority vote over the given agent decisions.
     *
     * @param agentDecisions     all agent decisions for a ticker (may include SKIPs)
     * @param consensusThreshold confidence threshold below which HOLD agents abstain
     * @return the aggregate vote result (never {@code null})
     */
    public static MajorityVoteResult compute(List<LLMDecision> agentDecisions, double consensusThreshold) {
        // Filter out SKIP agents (failed agents don't vote)
        List<LLMDecision> nonSkipDecisions = agentDecisions.stream()
            .filter(d -> d.getRecommendation() != RecommendationType.SKIP)
            .toList();

        if (nonSkipDecisions.isEmpty()) {
            return new MajorityVoteResult(Outcome.ALL_SKIPPED, null, null, 0, 0, List.of(), null);
        }

        // Abstain rule: HOLD agents with confidence below threshold abstain from voting.
        // BUY/SELL votes always count regardless of confidence.
        List<LLMDecision> votingDecisions = new ArrayList<>();
        List<String> abstained = new ArrayList<>();
        for (LLMDecision d : nonSkipDecisions) {
            if (d.getRecommendation() == RecommendationType.HOLD) {
                double confidence = d.getConfidenceScore() != null ? d.getConfidenceScore().value().doubleValue() : 0.0;
                if (confidence < consensusThreshold) {
                    abstained.add(String.format("%s abstained — HOLD at %.2f confidence, below the"
                        + " %.2f consensus_threshold", d.getRole(), confidence, consensusThreshold));
                    continue;
                }
            }
            votingDecisions.add(d);
        }

        if (votingDecisions.isEmpty()) {
            // All non-SKIP agents abstained — no votes cast
            return new MajorityVoteResult(Outcome.ALL_ABSTAINED, null, null, 0, 0, abstained, null);
        }

        // Count votes by recommendation type
        Map<RecommendationType, List<LLMDecision>> votesByAction = votingDecisions.stream()
            .collect(Collectors.groupingBy(LLMDecision::getRecommendation));

        // Find winner (most votes)
        RecommendationType winner = null;
        int maxVotes = 0;
        boolean hasTie = false;

        for (var entry : votesByAction.entrySet()) {
            int count = entry.getValue().size();
            if (count > maxVotes) {
                winner = entry.getKey();
                maxVotes = count;
                hasTie = false;
            } else if (count == maxVotes) {
                hasTie = true;
            }
        }

        // Tie → no winner
        if (hasTie || winner == null) {
            String tieDetail = votesByAction.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue().size())
                .collect(Collectors.joining(", "));
            return new MajorityVoteResult(Outcome.TIE, null, null, 0, votingDecisions.size(), abstained, tieDetail);
        }

        // Use the highest-confidence agent that voted for the winner
        LLMDecision winningAgent = votesByAction.get(winner).stream()
            .max(Comparator.comparing(d -> d.getConfidenceScore() != null ? d.getConfidenceScore().value() : BigDecimal.ZERO))
            .orElse(votingDecisions.get(0));

        return new MajorityVoteResult(
            Outcome.WINNER, winner, winningAgent, maxVotes, votingDecisions.size(), abstained, null);
    }
}
