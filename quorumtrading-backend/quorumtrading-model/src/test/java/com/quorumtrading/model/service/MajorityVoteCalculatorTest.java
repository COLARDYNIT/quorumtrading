package com.quorumtrading.model.service;

import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.service.MajorityVoteCalculator.MajorityVoteResult;
import com.quorumtrading.model.service.MajorityVoteCalculator.Outcome;
import com.quorumtrading.model.valueobject.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MajorityVoteCalculator} — the pure domain service behind
 * MAJORITY consensus and the ORCHESTRATOR_OVERRIDE failure fallback.
 *
 * <p>Rules under test (must match historical ReachConsensusService behavior exactly):</p>
 * <ul>
 *   <li>SKIP agents never vote</li>
 *   <li>All agents SKIP → ALL_SKIPPED (caller defaults to HOLD)</li>
 *   <li>HOLD votes below the consensus threshold abstain; BUY/SELL always count</li>
 *   <li>All non-SKIP agents abstained → ALL_ABSTAINED (caller defaults to HOLD)</li>
 *   <li>Winner = recommendation with most votes; tie → TIE (caller defaults to HOLD)</li>
 *   <li>Winning agent = highest-confidence agent among the winners</li>
 * </ul>
 */
class MajorityVoteCalculatorTest {

    private static final double THRESHOLD = 0.6;

    private static LLMDecision decision(RecommendationType recommendation, Double confidence) {
        LLMDecision d = new LLMDecision();
        d.recordRecommendation(recommendation);
        if (confidence != null) {
            d.recordConfidenceScore(new Percentage(BigDecimal.valueOf(confidence)));
        }
        return d;
    }

    // ---------- clear majority ----------

    @Test
    void clearMajorityBuyWins() {
        LLMDecision buyLow = decision(RecommendationType.BUY, 0.55);
        LLMDecision buyHigh = decision(RecommendationType.BUY, 0.9);
        LLMDecision sell = decision(RecommendationType.SELL, 0.99);

        MajorityVoteResult result = MajorityVoteCalculator.compute(
                List.of(buyLow, buyHigh, sell), THRESHOLD);

        assertEquals(Outcome.WINNER, result.outcome());
        assertEquals(RecommendationType.BUY, result.winner());
        assertEquals(2, result.winningVotes());
        assertEquals(3, result.totalVotes());
        assertEquals(0, result.abstainedCount());
        assertSame(buyHigh, result.winningAgent(), "highest-confidence winning agent must be selected");
    }

    @Test
    void clearMajoritySellWins() {
        LLMDecision sell1 = decision(RecommendationType.SELL, 0.7);
        LLMDecision sell2 = decision(RecommendationType.SELL, 0.8);
        LLMDecision buy = decision(RecommendationType.BUY, 0.95);

        MajorityVoteResult result = MajorityVoteCalculator.compute(
                List.of(sell1, sell2, buy), THRESHOLD);

        assertEquals(Outcome.WINNER, result.outcome());
        assertEquals(RecommendationType.SELL, result.winner());
        assertEquals(2, result.winningVotes());
        assertEquals(3, result.totalVotes());
        assertSame(sell2, result.winningAgent());
    }

    @Test
    void unanimousSingleVoteWins() {
        LLMDecision buy = decision(RecommendationType.BUY, 0.1);

        MajorityVoteResult result = MajorityVoteCalculator.compute(List.of(buy), THRESHOLD);

        assertEquals(Outcome.WINNER, result.outcome());
        assertEquals(RecommendationType.BUY, result.winner());
        assertEquals(1, result.winningVotes());
        assertEquals(1, result.totalVotes());
        assertSame(buy, result.winningAgent());
    }

    // ---------- tie ----------

    @Test
    void tieDefaultsToHold() {
        LLMDecision buy = decision(RecommendationType.BUY, 0.9);
        LLMDecision sell = decision(RecommendationType.SELL, 0.9);

        MajorityVoteResult result = MajorityVoteCalculator.compute(List.of(buy, sell), THRESHOLD);

        assertEquals(Outcome.TIE, result.outcome());
        assertNull(result.winner());
        assertNull(result.winningAgent());
        assertEquals(2, result.totalVotes());
        assertNotNull(result.tieDetail());
        assertTrue(result.tieDetail().contains("BUY=1"), "tie detail should list BUY votes: " + result.tieDetail());
        assertTrue(result.tieDetail().contains("SELL=1"), "tie detail should list SELL votes: " + result.tieDetail());
    }

    @Test
    void threeWayTieDefaultsToHold() {
        LLMDecision buy = decision(RecommendationType.BUY, 0.9);
        LLMDecision sell = decision(RecommendationType.SELL, 0.9);
        LLMDecision hold = decision(RecommendationType.HOLD, 0.9); // above threshold, votes

        MajorityVoteResult result = MajorityVoteCalculator.compute(List.of(buy, sell, hold), THRESHOLD);

        assertEquals(Outcome.TIE, result.outcome());
        assertEquals(3, result.totalVotes());
    }

    // ---------- SKIP filtering ----------

    @Test
    void allAgentsSkipDefaultsToHold() {
        LLMDecision skip1 = decision(RecommendationType.SKIP, 0.0);
        LLMDecision skip2 = decision(RecommendationType.SKIP, null);

        MajorityVoteResult result = MajorityVoteCalculator.compute(List.of(skip1, skip2), THRESHOLD);

        assertEquals(Outcome.ALL_SKIPPED, result.outcome());
        assertNull(result.winner());
        assertEquals(0, result.totalVotes());
    }

    @Test
    void emptyDecisionListIsAllSkipped() {
        MajorityVoteResult result = MajorityVoteCalculator.compute(List.of(), THRESHOLD);

        assertEquals(Outcome.ALL_SKIPPED, result.outcome());
    }

    @Test
    void skipAgentsDoNotVoteButOthersDo() {
        LLMDecision skip = decision(RecommendationType.SKIP, 0.9);
        LLMDecision buy = decision(RecommendationType.BUY, 0.5);
        LLMDecision sell1 = decision(RecommendationType.SELL, 0.6);
        LLMDecision sell2 = decision(RecommendationType.SELL, 0.7);

        MajorityVoteResult result = MajorityVoteCalculator.compute(
                List.of(skip, buy, sell1, sell2), THRESHOLD);

        assertEquals(Outcome.WINNER, result.outcome());
        assertEquals(RecommendationType.SELL, result.winner());
        assertEquals(2, result.winningVotes());
        assertEquals(3, result.totalVotes(), "SKIP agent must not count towards total votes");
        assertSame(sell2, result.winningAgent());
    }

    // ---------- HOLD abstention ----------

    @Test
    void lowConfidenceHoldAbstains() {
        LLMDecision weakHold = decision(RecommendationType.HOLD, 0.3); // below 0.6 → abstains
        LLMDecision buy = decision(RecommendationType.BUY, 0.5);

        MajorityVoteResult result = MajorityVoteCalculator.compute(List.of(weakHold, buy), THRESHOLD);

        assertEquals(Outcome.WINNER, result.outcome());
        assertEquals(RecommendationType.BUY, result.winner());
        assertEquals(1, result.winningVotes());
        assertEquals(1, result.totalVotes());
        assertEquals(1, result.abstainedCount());
    }

    @Test
    void holdAtThresholdCountsAsVote() {
        LLMDecision holdAtThreshold = decision(RecommendationType.HOLD, 0.6); // == threshold → votes
        LLMDecision hold2 = decision(RecommendationType.HOLD, 0.8);
        LLMDecision buy = decision(RecommendationType.BUY, 0.9);

        MajorityVoteResult result = MajorityVoteCalculator.compute(
                List.of(holdAtThreshold, hold2, buy), THRESHOLD);

        assertEquals(Outcome.WINNER, result.outcome());
        assertEquals(RecommendationType.HOLD, result.winner());
        assertEquals(2, result.winningVotes());
        assertEquals(3, result.totalVotes());
        assertEquals(0, result.abstainedCount());
    }

    @Test
    void holdWithNullConfidenceAbstains() {
        LLMDecision nullConfidenceHold = decision(RecommendationType.HOLD, null); // treated as 0.0
        LLMDecision sell = decision(RecommendationType.SELL, 0.4);

        MajorityVoteResult result = MajorityVoteCalculator.compute(
                List.of(nullConfidenceHold, sell), THRESHOLD);

        assertEquals(Outcome.WINNER, result.outcome());
        assertEquals(RecommendationType.SELL, result.winner());
        assertEquals(1, result.abstainedCount());
    }

    @Test
    void buyAndSellAlwaysCountRegardlessOfConfidence() {
        LLMDecision weakBuy1 = decision(RecommendationType.BUY, 0.01);
        LLMDecision weakBuy2 = decision(RecommendationType.BUY, null); // null confidence still votes
        LLMDecision weakSell = decision(RecommendationType.SELL, 0.02);

        MajorityVoteResult result = MajorityVoteCalculator.compute(
                List.of(weakBuy1, weakBuy2, weakSell), THRESHOLD);

        assertEquals(Outcome.WINNER, result.outcome());
        assertEquals(RecommendationType.BUY, result.winner());
        assertEquals(2, result.winningVotes());
        assertEquals(3, result.totalVotes());
        assertEquals(0, result.abstainedCount());
        assertSame(weakBuy1, result.winningAgent(), "null-confidence agent ranks as zero confidence");
    }

    // ---------- all abstained ----------

    @Test
    void allAbstainedDefaultsToHold() {
        LLMDecision weakHold1 = decision(RecommendationType.HOLD, 0.2);
        LLMDecision weakHold2 = decision(RecommendationType.HOLD, 0.5);

        MajorityVoteResult result = MajorityVoteCalculator.compute(
                List.of(weakHold1, weakHold2), THRESHOLD);

        assertEquals(Outcome.ALL_ABSTAINED, result.outcome());
        assertNull(result.winner());
        assertEquals(0, result.totalVotes());
        assertEquals(2, result.abstainedCount());
    }

    @Test
    void mixedSkipAndAbstainedDefaultsToHold() {
        LLMDecision skip = decision(RecommendationType.SKIP, null);
        LLMDecision weakHold = decision(RecommendationType.HOLD, 0.1);

        MajorityVoteResult result = MajorityVoteCalculator.compute(List.of(skip, weakHold), THRESHOLD);

        assertEquals(Outcome.ALL_ABSTAINED, result.outcome());
        assertEquals(1, result.abstainedCount(), "SKIP agents are filtered, not counted as abstained");
    }

    // ---------- mixed everything ----------

    @Test
    void mixedSkipAbstainAndVotesResolvesToMajority() {
        LLMDecision skip = decision(RecommendationType.SKIP, null);
        LLMDecision weakHold = decision(RecommendationType.HOLD, 0.3);   // abstains
        LLMDecision strongHold = decision(RecommendationType.HOLD, 0.9); // votes
        LLMDecision buy1 = decision(RecommendationType.BUY, 0.65);
        LLMDecision buy2 = decision(RecommendationType.BUY, 0.85);

        MajorityVoteResult result = MajorityVoteCalculator.compute(
                List.of(skip, weakHold, strongHold, buy1, buy2), THRESHOLD);

        assertEquals(Outcome.WINNER, result.outcome());
        assertEquals(RecommendationType.BUY, result.winner());
        assertEquals(2, result.winningVotes());
        assertEquals(3, result.totalVotes());
        assertEquals(1, result.abstainedCount());
        assertSame(buy2, result.winningAgent());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("a confidence-gated abstention names the agent and the confidence")
    void abstentionNamesTheAgentAndConfidence() {
        // The note used to read "(1 abstained)", naming nobody — so finding out it was SENTIMENT
        // at 0.30 against a 0.60 threshold meant reading the agent array and comparing each
        // confidence against the strategy frontmatter by hand. The other abstention mechanism
        // (missing news) had always named its agent and its reason.
        LLMDecision buy = decision(RecommendationType.BUY, 0.80);
        LLMDecision hold = decision(RecommendationType.HOLD, 0.30);
        hold.setRole("SENTIMENT");

        MajorityVoteResult result = MajorityVoteCalculator.compute(List.of(buy, hold), 0.60);

        assertEquals(1, result.abstainedCount());
        assertTrue(result.abstained().get(0).contains("SENTIMENT"),
            "the note must name who was dropped: " + result.abstained());
        assertTrue(result.abstained().get(0).contains("0.30"),
            "the note must give the confidence that lost the vote: " + result.abstained());
        assertTrue(result.abstained().get(0).contains("0.60"),
            "the note must give the threshold it fell below: " + result.abstained());
    }
}
