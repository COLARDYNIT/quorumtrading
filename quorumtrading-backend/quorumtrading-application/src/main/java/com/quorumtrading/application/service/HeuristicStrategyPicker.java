package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.StrategyPickerPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.Strategy.StrategyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Phase-3 stand-in for the AI strategy picker. Picks the user's most-recently
 * updated {@code ACTIVE} strategy, falling back to nothing when the user has no
 * active strategies. Cheap, deterministic, and lets the rest of the recurring-
 * investment plumbing be exercised end-to-end without an LLM in the loop.
 *
 * <p>The real LLM-backed picker is a Phase 3.1 work item — it will live behind
 * the same {@link StrategyPickerPort} so swapping implementations is trivial.</p>
 */
public class HeuristicStrategyPicker implements StrategyPickerPort {

    private static final Logger logger = LoggerFactory.getLogger(HeuristicStrategyPicker.class);

    private final StrategyRepository strategyRepository;

    public HeuristicStrategyPicker(StrategyRepository strategyRepository) {
        this.strategyRepository = strategyRepository;
    }

    @Override
    public Outcome pick(InvestmentPlan plan) {
        List<Strategy> ownStrategies = strategyRepository.findByOwnerId(plan.getUserId());
        List<Strategy> active = ownStrategies.stream()
                .filter(s -> s.getStatus() == StrategyStatus.ACTIVE)
                .sorted(Comparator.comparing(Strategy::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        if (active.isEmpty()) {
            return Outcome.none("No active strategies owned by user — create one before scheduling investments.");
        }

        List<Strategy> eligible = new ArrayList<>();
        List<String> rejections = new ArrayList<>();
        for (Strategy s : active) {
            TickerPolicyEnforcer.Decision decision =
                    TickerPolicyEnforcer.evaluate(s, plan.getPreferences());
            if (decision.allowed()) {
                eligible.add(s);
            } else {
                rejections.add(s.getName() + ": " + decision.reason());
            }
        }

        if (eligible.isEmpty()) {
            return Outcome.none("All " + active.size()
                    + " active strategies were filtered by ticker policy — "
                    + summarize(rejections));
        }

        Strategy chosen = eligible.getFirst();
        String reasoning = "Heuristic picker: most-recently-updated active strategy in user's pool ("
                + chosen.getName() + ").";
        logger.info("Heuristic picker chose strategy {} for plan {} (user={})",
                chosen.getId(), plan.getId(), plan.getUserId());
        return Outcome.chose(chosen.getId(), reasoning, BigDecimal.ZERO);
    }

    /**
     * How many individual rejections to name before summarising the rest.
     *
     * <p>Enough to show the pattern — a user seeing three strategies rejected for the same
     * blocked ticker has learned what they need — without the length scaling with the pool.</p>
     */
    private static final int MAX_LISTED_REJECTIONS = 3;

    /**
     * Render the rejection list at a size that fits a notification body.
     *
     * <p>Every rejection used to be joined in full. The result is the allocation's
     * {@code pickerReasoning} and {@code failureReason} and is copied into the notification the
     * user actually reads, so a 76-strategy pool produced a 13KB wall of text — and it grew with
     * the pool, hitting long-standing users hardest. The count is the number that matters; a few
     * examples make it actionable; the remainder is noise. Truncation is stated, because a list
     * that was silently cut reads as a complete one.</p>
     */
    private static String summarize(List<String> rejections) {
        if (rejections.size() <= MAX_LISTED_REJECTIONS) {
            return String.join("; ", rejections);
        }
        return String.join("; ", rejections.subList(0, MAX_LISTED_REJECTIONS))
                + "; and " + (rejections.size() - MAX_LISTED_REJECTIONS) + " more.";
    }
}
