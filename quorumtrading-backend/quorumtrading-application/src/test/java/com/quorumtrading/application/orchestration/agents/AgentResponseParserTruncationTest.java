package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.LlmParseFailure;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.marketdata.MarketDataContext;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A model that hits its output-token limit stops mid-object. The fields it emitted before that
 * point are its own literal JSON, so a decision rebuilt from them is the model's own answer.
 *
 * <p>Every case here is drawn from real rows in {@code llm_parse_failures} on the default
 * self-hosted engine, where 3 of 4 captured failures carried a plainly extractable recommendation
 * and all 4 were discarded whole — costing the entire consensus round each time.
 */
class AgentResponseParserTruncationTest {

    private AgentContext context() {
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        Portfolio portfolio = new Portfolio(simulationId, Map.of(),
            new Money(new BigDecimal("10000"), Money.Currency.USD));
        Instant timestamp = Instant.parse("2026-07-16T00:00:00Z");
        return new AgentContext(
            simulationId, "BTC", portfolio, MarketDataContext.empty(timestamp),
            List.of(), Map.of(), timestamp, new StrategyConfig(), null
        );
    }

    private LLMDecision parse(String response, AtomicReference<LlmParseFailure> captured) {
        return AgentResponseParser.parseStandardResponse(
            response, 1200L, context(), "the prompt", "claude", "TECHNICAL",
            captured == null ? null : captured::set);
    }

    /**
     * The exact shape of the measured failure: a complete object whose tail was cut, leaving a
     * dangling comma and quote. Jackson reports "Unexpected end-of-input in field name". The
     * existing trailing-comma cleanup does not reach it, because a `"` sits between the comma
     * and the brace.
     */
    @Test
    void recoversTheRecommendationWhenOnlyTheObjectTailWasLost() {
        String truncated = "{\"action\":\"HOLD\",\"symbol\":\"BTC\",\"confidence\":0.6,"
            + "\"reasoning\":\"The strategy requires RSI<40 AND price above SMA-50 to trigger a BUY."
            + " Without confirmation that price has reclaimed SMA-50, initiating a BUY here would"
            + " front-run the strategy's own entry criteria, so HOLD is the disciplined choice.\",\"";

        LLMDecision decision = parse(truncated, null);

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.HOLD);
        assertThat(decision.getConfidenceScore().value()).isEqualByComparingTo("0.6");
        assertThat(decision.getTicker()).isEqualTo("BTC");
        assertThat(decision.getReasoning()).contains("disciplined choice");
    }

    /** A recovered decision is a real model answer, so it must not be labelled a fallback. */
    @Test
    void recoveredDecisionIsAttributedToTheModelNotToTheFallback() {
        String truncated = "{\"action\":\"BUY\",\"symbol\":\"BTC\",\"confidence\":0.82,"
            + "\"reasoning\":\"Momentum is positive across all timeframes.\",\"quant";

        LLMDecision decision = parse(truncated, null);

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.BUY);
        assertThat(decision.getLlmModel()).isEqualTo("claude");
        assertThat(decision.getPromptUsed()).isEqualTo("the prompt");
    }

    /** Truncation inside the narrative field must not cost the vote that preceded it. */
    @Test
    void recoversWhenTruncationFallsInsideTheReasoningString() {
        String truncated = "{\"action\":\"SELL\",\"symbol\":\"BTC\",\"confidence\":0.71,"
            + "\"reasoning\":\"Price broke below support and the histogram is rolling ove";

        LLMDecision decision = parse(truncated, null);

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.SELL);
        assertThat(decision.getConfidenceScore().value()).isEqualByComparingTo("0.71");
        assertThat(decision.getReasoning()).isNotBlank();
    }

    /**
     * Confidence gates the HOLD abstain rule, so inventing one would fabricate a vote weight.
     * If it did not arrive, the decision is not recoverable and the round is honestly skipped.
     */
    @Test
    void doesNotRecoverWhenConfidenceNeverArrived() {
        String truncated = "{\"action\":\"BUY\",\"symbol\":\"BTC\",\"reason";

        LLMDecision decision = parse(truncated, null);

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.SKIP);
    }

    /**
     * Prose is deliberately NOT salvaged. Three of the four measured failures were prose ending
     * in "the recommendation is to hold" or "...to buy", and a keyword match would read both of
     * those and "I would not recommend a buy" the same way. A wrong vote is worse than a skipped
     * one, so prose stays a skip until a corrective re-ask is designed.
     */
    @Test
    void doesNotInventAVoteFromProse() {
        String prose = "Bitcoin shows positive momentum across all timeframes with price gains of"
            + " 1.41 to 3.05 percent and a bullish MACD histogram, therefore the recommendation"
            + " is to buy.";

        LLMDecision decision = parse(prose, null);

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.SKIP);
    }

    /**
     * A salvaged response is not a lost vote, and {@code llm_parse_failures} is read as the
     * tally of lost votes. Recording one here would over-count the damage.
     */
    @Test
    void doesNotRecordAParseFailureWhenTheVoteWasRecovered() {
        String truncated = "{\"action\":\"HOLD\",\"symbol\":\"BTC\",\"confidence\":0.55,"
            + "\"reasoning\":\"Mixed signals.\",\"";
        AtomicReference<LlmParseFailure> captured = new AtomicReference<>();

        parse(truncated, captured);

        assertThat(captured.get()).isNull();
    }

    /**
     * A nested object before the cut must not derail the walk — agents do emit structured extras
     * alongside the decision fields, and the salvage skips them wholesale rather than descending.
     */
    @Test
    void recoversPastANestedObjectThatArrivedIntact() {
        String truncated = "{\"indicators\":{\"rsi\":41.2,\"macd\":-0.8},\"action\":\"HOLD\","
            + "\"symbol\":\"BTC\",\"confidence\":0.64,\"reasoning\":\"Neutral RSI.\",\"";

        LLMDecision decision = parse(truncated, null);

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.HOLD);
        assertThat(decision.getConfidenceScore().value()).isEqualByComparingTo("0.64");
    }

    /**
     * The safety property: salvage recovers the model's own values, it does not launder invalid
     * ones. An action outside the enum must stay a SKIP rather than become a trade.
     */
    @Test
    void doesNotRecoverAnActionTheDomainDoesNotRecognise() {
        String truncated = "{\"action\":\"MAYBE_BUY\",\"symbol\":\"BTC\",\"confidence\":0.9,"
            + "\"reasoning\":\"Feeling good about it.\",\"";

        LLMDecision decision = parse(truncated, null);

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.SKIP);
    }

    /** Likewise a confidence outside 0–1: models do emit a 0–100 scale, and it must not pass. */
    @Test
    void doesNotRecoverAConfidenceOutsideTheValidRange() {
        String truncated = "{\"action\":\"BUY\",\"symbol\":\"BTC\",\"confidence\":85,"
            + "\"reasoning\":\"Strong momentum.\",\"";

        LLMDecision decision = parse(truncated, null);

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.SKIP);
    }

    /** Unsalvageable output must still be captured for diagnosis, as it was before. */
    @Test
    void stillRecordsAParseFailureWhenNothingCouldBeRecovered() {
        AtomicReference<LlmParseFailure> captured = new AtomicReference<>();

        parse("I cannot provide trading advice.", captured);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().agentRole()).isEqualTo("TECHNICAL");
    }
}
