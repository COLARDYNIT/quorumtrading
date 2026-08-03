package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.valueobject.StrategyId;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Output port for selecting a strategy each time a recurring investment plan fires.
 *
 * <p>Implementations span the spectrum from a no-LLM heuristic (Phase 3) to an
 * orchestrator LLM call (Phase 3.1+). All must respect plan preferences — but
 * <em>final</em> ticker policy enforcement is the application service's
 * responsibility, since we don't trust the LLM to enforce hard constraints.</p>
 */
public interface StrategyPickerPort {

    /**
     * Pick a strategy for this plan's next contribution.
     *
     * @param plan the plan that triggered this pick
     * @return an outcome describing the chosen strategy + reasoning, or
     *         {@link Outcome#none(String)} when nothing eligible was found
     */
    Outcome pick(InvestmentPlan plan);

    /**
     * Picker outcome: either a chosen strategy with reasoning, or a "no eligible
     * strategy" signal carrying the explanation the user will see.
     */
    record Outcome(
            Optional<StrategyId> chosenStrategyId,
            String reasoning,
            BigDecimal llmCostUsd
    ) {
        public static Outcome chose(StrategyId strategyId, String reasoning, BigDecimal llmCostUsd) {
            return new Outcome(Optional.of(strategyId), reasoning,
                    llmCostUsd == null ? BigDecimal.ZERO : llmCostUsd);
        }

        public static Outcome none(String reasoning) {
            return new Outcome(Optional.empty(), reasoning, BigDecimal.ZERO);
        }

        public boolean isChoice() {
            return chosenStrategyId.isPresent();
        }
    }
}
