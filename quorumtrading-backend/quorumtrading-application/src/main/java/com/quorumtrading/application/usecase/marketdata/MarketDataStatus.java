package com.quorumtrading.application.usecase.marketdata;

import java.util.List;

/**
 * What the price-data side of the platform can actually do right now, per provider.
 *
 * <p>The counterpart to {@code LlmProviderStatus}. A user could ask "is an LLM engine selected and
 * does it answer?" from inside the product and get a live answer; the same question about price
 * data had no tool and no REST endpoint at all. The only documented route was
 * {@code docker exec … env | grep}, which an agent-driven or non-Docker user may not be able to run
 * — and which is unreliable anyway, since a shell-exported key silently overrides {@code .env}.</p>
 *
 * @param cryptoUsable whether at least one enabled, credentialed provider can serve crypto candles
 * @param stockUsable  whether at least one enabled, credentialed provider can serve stock candles
 * @param providers    every discovered provider, enabled or not
 */
public record MarketDataStatus(
    boolean cryptoUsable,
    boolean stockUsable,
    List<ProviderStatus> providers
) {

    /**
     * One-line human summary of what works and what is missing. Lives here — not in a
     * driving adapter — so the MCP tool and the REST endpoint render the exact same
     * diagnosis instead of drifting apart.
     */
    public String hint() {
        if (cryptoUsable && stockUsable) {
            return "Both crypto and stock candles have a usable provider.";
        }
        if (cryptoUsable) {
            return "Crypto candles are served (keyless). No usable stock provider — stock backfills "
                + "will return nothing until one is enabled and given a key.";
        }
        if (stockUsable) {
            return "Stock candles are served. No usable crypto provider on this deployment.";
        }
        return "No price provider is usable, so every backfill will return nothing. This is a "
            + "configuration problem, not a coverage problem — do not go looking for a different ticker.";
    }

    /**
     * One provider's state.
     *
     * <p>{@code enabled} and {@code credentialed} are deliberately separate: Saxo holds the
     * distinction on its own (off by default in every profile until broker approval, whatever
     * credentials exist), and the keyless crypto sources are the mirror image — credentialed by
     * definition, still switchable. Collapsing the two is what let a failed backfill report
     * "Tried 3 configured provider(s): alpaca, tiingo, saxo" on a container where Saxo was off.</p>
     *
     * @param name         provider name as it appears in error messages and in {@code sources}
     * @param enabled      switched on for this deployment
     * @param credentialed holds whatever credentials it needs (always true for keyless providers)
     * @param assetClasses which of CRYPTO / STOCK it advertises
     * @param features     the capability names it advertises, sorted
     * @param probe        result of a live call, or {@code null} when not probed
     */
    public record ProviderStatus(
        String name,
        boolean enabled,
        boolean credentialed,
        List<String> assetClasses,
        List<String> features,
        Probe probe
    ) {
        /** Whether this provider could serve a request right now, before any live call. */
        public boolean usable() {
            return enabled && credentialed;
        }

        /** What this provider's state means for the operator — shared by MCP and REST. */
        public String detail() {
            if (!enabled) {
                return "Switched off for this deployment — it is never called and never counted among "
                    + "the providers a failed backfill says it tried.";
            }
            if (!credentialed) {
                return "Switched on but holding no credentials, so it is skipped rather than called. "
                    + "Set its API key and restart the backend.";
            }
            return "Enabled and credentialed.";
        }
    }

    /**
     * The outcome of actually calling the provider — the part {@code get_llm_status} gets right
     * and configuration alone can never tell you.
     *
     * @param symbol  the symbol asked for
     * @param ok      whether the call returned at least one bar
     * @param detail  a human-readable outcome, including the failure when there was one
     */
    public record Probe(String symbol, boolean ok, String detail) {}
}
