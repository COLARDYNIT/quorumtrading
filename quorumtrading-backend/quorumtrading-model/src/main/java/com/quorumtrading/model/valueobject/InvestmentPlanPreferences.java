package com.quorumtrading.model.valueobject;

import java.util.List;

/**
 * User-supplied preferences that constrain the AI picker each tick.
 *
 * <p>All fields are advisory inputs to the picker except {@code allowedTickers}
 * / {@code blockedTickers}, which are enforced as a hard filter by the
 * application service after the picker returns (we don't trust the LLM to
 * respect ticker policy).</p>
 *
 * @param riskTolerance         conservative | moderate | aggressive — free-form, passed to picker as context
 * @param allowedTickers        if non-empty, restrict eligible strategies to ones trading these tickers
 * @param blockedTickers        strategies trading any of these tickers are rejected
 * @param maxStrategiesActive   cap on number of distinct LIVE simulations the plan can spawn (null = unlimited)
 * @param preferExistingHoldings when true, the picker is asked to top up an existing simulation if possible
 */
public record InvestmentPlanPreferences(
        String riskTolerance,
        List<String> allowedTickers,
        List<String> blockedTickers,
        Integer maxStrategiesActive,
        boolean preferExistingHoldings
) {
    public static final InvestmentPlanPreferences EMPTY =
            new InvestmentPlanPreferences(null, List.of(), List.of(), null, true);

    public InvestmentPlanPreferences {
        allowedTickers = allowedTickers == null ? List.of() : List.copyOf(allowedTickers);
        blockedTickers = blockedTickers == null ? List.of() : List.copyOf(blockedTickers);
        if (maxStrategiesActive != null && maxStrategiesActive < 1) {
            throw new IllegalArgumentException("maxStrategiesActive must be >= 1 when set");
        }
    }

    public boolean isTickerAllowed(String symbol) {
        if (symbol == null) return false;
        String upper = symbol.toUpperCase();
        if (!blockedTickers.isEmpty() && blockedTickers.stream().anyMatch(t -> t.equalsIgnoreCase(upper))) {
            return false;
        }
        if (allowedTickers.isEmpty()) return true;
        return allowedTickers.stream().anyMatch(t -> t.equalsIgnoreCase(upper));
    }
}
