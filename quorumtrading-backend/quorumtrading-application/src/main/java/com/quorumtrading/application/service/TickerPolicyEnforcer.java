package com.quorumtrading.application.service;

import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.Symbol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Hard filter that prevents an investment plan from funding strategies that
 * violate its ticker policy. Applied before the picker sees the candidate list,
 * and again as a defense-in-depth check after the picker returns.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>If neither allowed nor blocked list is set, every strategy passes — the
 *       parser isn't even invoked.</li>
 *   <li>If a policy is set and the strategy's tickers cannot be parsed, the
 *       strategy is rejected. We err on the side of safety: an unverifiable
 *       strategy is not fundable under an explicit policy.</li>
 *   <li>A strategy is rejected as soon as one of its tickers fails
 *       {@link InvestmentPlanPreferences#isTickerAllowed(String)} (strict).</li>
 *   <li>If a policy is set and the strategy declares no tickers at all, it is
 *       rejected — we cannot certify a tickerless strategy against an allowlist.</li>
 * </ul>
 */
public final class TickerPolicyEnforcer {

    private static final StrategyConfigParser PARSER = new StrategyConfigParser();

    private TickerPolicyEnforcer() {}

    public record Decision(boolean allowed, String reason) {
        public static Decision allow() { return new Decision(true, ""); }
        public static Decision reject(String reason) { return new Decision(false, reason); }
    }

    public static Decision evaluate(Strategy strategy, InvestmentPlanPreferences prefs) {
        boolean policyActive = !prefs.allowedTickers().isEmpty() || !prefs.blockedTickers().isEmpty();
        if (!policyActive) {
            return Decision.allow();
        }

        Set<String> tickers;
        try {
            tickers = extractTickers(strategy);
        } catch (RuntimeException e) {
            return Decision.reject("Strategy markdown unparseable, cannot verify against ticker policy: "
                    + e.getMessage());
        }

        if (tickers.isEmpty()) {
            return Decision.reject("Strategy declares no tickers, cannot certify against ticker policy");
        }

        List<String> violations = new ArrayList<>();
        for (String ticker : tickers) {
            if (!prefs.isTickerAllowed(ticker)) {
                violations.add(ticker);
            }
        }
        if (!violations.isEmpty()) {
            return Decision.reject("Strategy trades disallowed tickers: " + String.join(", ", violations));
        }
        return Decision.allow();
    }

    private static Set<String> extractTickers(Strategy strategy) {
        String content = strategy.getContent();
        if (content == null || content.isBlank()) {
            return Collections.emptySet();
        }
        StrategyConfig config = PARSER.parse(content);
        Set<String> tickers = new LinkedHashSet<>();
        Set<Symbol> stocks = config.getStockTickers();
        Set<Symbol> crypto = config.getCryptoTickers();
        if (stocks != null) stocks.forEach(s -> tickers.add(s.ticker()));
        if (crypto != null) crypto.forEach(s -> tickers.add(s.ticker()));
        return tickers;
    }
}
