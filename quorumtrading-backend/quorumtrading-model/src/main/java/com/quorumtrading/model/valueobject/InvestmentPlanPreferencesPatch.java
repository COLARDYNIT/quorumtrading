package com.quorumtrading.model.valueobject;

import java.util.List;
import java.util.Optional;

/**
 * A partial change to {@link InvestmentPlanPreferences}: each field is independently
 * "specified" or "left alone".
 *
 * <p>Update tools and endpoints advertise partial-update semantics ("pass only the fields you
 * want to change"), but the preferences used to travel as a whole {@code InvestmentPlanPreferences}
 * value built from whichever arguments the caller happened to supply. Every field the caller
 * omitted therefore arrived as null/empty and overwrote what was stored. Editing a plan's amount
 * and block-list silently erased its allow-list — which widens the picker from a single ticker to
 * the user's entire strategy pool, changing what the plan buys with real money.</p>
 *
 * <p>Clearing a value is deliberately distinct from leaving it alone, and only where a caller can
 * express it: an empty list clears {@code allowedTickers}/{@code blockedTickers}, and a blank
 * string clears {@code riskTolerance}. {@code maxStrategiesActive} has no clearing sentinel
 * because the domain rejects values below 1 — pass a new cap, or omit it to keep the current one.</p>
 *
 * @param riskTolerance          present to set (blank clears); absent leaves the stored hint alone
 * @param allowedTickers         present to set (empty list clears); absent leaves the stored list alone
 * @param blockedTickers         present to set (empty list clears); absent leaves the stored list alone
 * @param maxStrategiesActive    present to set; absent leaves the stored cap alone
 * @param preferExistingHoldings present to set; absent leaves the stored flag alone
 */
public record InvestmentPlanPreferencesPatch(
        Optional<String> riskTolerance,
        Optional<List<String>> allowedTickers,
        Optional<List<String>> blockedTickers,
        Optional<Integer> maxStrategiesActive,
        Optional<Boolean> preferExistingHoldings
) {
    /** A patch that changes nothing. */
    public static final InvestmentPlanPreferencesPatch EMPTY = new InvestmentPlanPreferencesPatch(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public InvestmentPlanPreferencesPatch {
        riskTolerance = riskTolerance == null ? Optional.empty() : riskTolerance;
        allowedTickers = allowedTickers == null ? Optional.empty() : allowedTickers;
        blockedTickers = blockedTickers == null ? Optional.empty() : blockedTickers;
        maxStrategiesActive = maxStrategiesActive == null ? Optional.empty() : maxStrategiesActive;
        preferExistingHoldings = preferExistingHoldings == null ? Optional.empty() : preferExistingHoldings;
    }

    /** True when no field was specified, so applying this patch is a no-op. */
    public boolean isEmpty() {
        return riskTolerance.isEmpty() && allowedTickers.isEmpty() && blockedTickers.isEmpty()
                && maxStrategiesActive.isEmpty() && preferExistingHoldings.isEmpty();
    }

    /**
     * Merges this patch onto {@code base}, field by field. Fields this patch does not specify are
     * carried over from {@code base} unchanged.
     *
     * @param base the currently stored preferences; null is treated as {@link InvestmentPlanPreferences#EMPTY}
     */
    public InvestmentPlanPreferences applyTo(InvestmentPlanPreferences base) {
        InvestmentPlanPreferences current = base == null ? InvestmentPlanPreferences.EMPTY : base;

        // Not riskTolerance.map(...): a mapper returning null yields an empty Optional, so a blank
        // value would fall through to orElse and quietly keep the stored hint instead of clearing it.
        String mergedRiskTolerance = current.riskTolerance();
        if (riskTolerance.isPresent()) {
            String supplied = riskTolerance.get();
            mergedRiskTolerance = supplied.isBlank() ? null : supplied;
        }

        return new InvestmentPlanPreferences(
                mergedRiskTolerance,
                allowedTickers.orElse(current.allowedTickers()),
                blockedTickers.orElse(current.blockedTickers()),
                maxStrategiesActive.orElse(current.maxStrategiesActive()),
                preferExistingHoldings.orElse(current.preferExistingHoldings()));
    }

    /** A patch that sets both ticker lists and leaves every other preference alone. */
    public static InvestmentPlanPreferencesPatch ofTickerPolicy(
            List<String> allowed, List<String> blocked) {
        return new InvestmentPlanPreferencesPatch(
                Optional.empty(),
                Optional.of(allowed == null ? List.of() : allowed),
                Optional.of(blocked == null ? List.of() : blocked),
                Optional.empty(), Optional.empty());
    }
}
