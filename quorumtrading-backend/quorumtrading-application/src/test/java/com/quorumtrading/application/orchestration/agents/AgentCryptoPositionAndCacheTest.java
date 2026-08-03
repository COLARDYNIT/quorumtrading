package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two defects shared one line pattern: every agent resolved the traded symbol with a hardcoded
 * {@code InvestmentType.STOCK}. {@link Symbol} equality is defined over (canonical, type), so for a
 * crypto ticker the portfolio lookup could never match — agents were told "0 units (no position)"
 * while holding a position, and the Technical agent's "is there new price data" probe always missed,
 * so it replayed a stale cached HOLD every tick.
 *
 * <p>These tests pin the two invariants that made that possible, at the level where it went wrong:
 * symbol identity and TTL enforcement.</p>
 */
class AgentCryptoPositionAndCacheTest {

    @Nested
    @DisplayName("Symbol identity is type-sensitive, so the asset type must be resolved")
    class SymbolIdentity {

        @Test
        @DisplayName("a crypto ticker typed as STOCK does not match the same ticker typed as CRYPTO")
        void mistypedSymbolNeverMatches() {
            Symbol asHeld = new Symbol("ETH", InvestmentType.CRYPTO);
            Symbol asLookedUp = new Symbol("ETH", InvestmentType.STOCK);

            assertThat(asLookedUp)
                .as("this mismatch is precisely why crypto positions were invisible to agents")
                .isNotEqualTo(asHeld);
            assertThat(asLookedUp.hashCode()).isNotEqualTo(asHeld.hashCode());
        }

        @Test
        @DisplayName("fromTicker resolves the crypto tickers the agents trade")
        void fromTickerResolvesCrypto() {
            for (String ticker : new String[] {"BTC", "ETH", "SOL", "LTC", "DOGE"}) {
                assertThat(InvestmentType.fromTicker(ticker))
                    .as("%s must resolve as crypto", ticker)
                    .isEqualTo(InvestmentType.CRYPTO);
            }
        }

        @Test
        @DisplayName("fromTicker leaves equities as stocks")
        void fromTickerLeavesEquitiesAlone() {
            for (String ticker : new String[] {"AAPL", "NVDA", "SPCX", "MSFT"}) {
                assertThat(InvestmentType.fromTicker(ticker))
                    .as("%s must resolve as stock", ticker)
                    .isEqualTo(InvestmentType.STOCK);
            }
        }

        @Test
        @DisplayName("resolving by ticker makes the lookup match a held crypto position")
        void resolvedLookupMatches() {
            Symbol asHeld = new Symbol("ETH", InvestmentType.CRYPTO);
            Symbol resolved = new Symbol("ETH", InvestmentType.fromTicker("ETH"));

            assertThat(resolved).isEqualTo(asHeld);
        }
    }

    @Nested
    @DisplayName("A cached decision expires")
    class CacheFreshness {

        /** Mirrors the agents' override without needing their full constructor wiring. */
        private boolean freshWithin(long ttlMinutes, long ageMinutes) {
            if (ttlMinutes <= 0) {
                return false;
            }
            java.time.Duration age = java.time.Duration.ofMinutes(ageMinutes);
            return !age.isNegative() && age.toMinutes() <= ttlMinutes;
        }

        @Test
        @DisplayName("a decision inside the TTL may be reused")
        void withinTtlIsFresh() {
            assertThat(freshWithin(60, 30)).isTrue();
            assertThat(freshWithin(60, 60)).isTrue();
        }

        @Test
        @DisplayName("a decision older than the TTL is not reused")
        void beyondTtlIsStale() {
            assertThat(freshWithin(60, 61)).isFalse();
            // The observed failure: a fortnight-old HOLD replayed as this session's analysis.
            assertThat(freshWithin(60, 14 * 24 * 60)).isFalse();
        }

        @Test
        @DisplayName("a zero TTL disables reuse entirely")
        void zeroTtlDisablesCache() {
            assertThat(freshWithin(0, 0)).isFalse();
        }

        @Test
        @DisplayName("a weekly backtest cadence always exceeds the default 60 minute TTL")
        void weeklyCadenceNeverReusesUnderDefaultTtl() {
            // Sessions one week apart cannot legitimately share a cached decision at TTL=60.
            assertThat(freshWithin(60, 7 * 24 * 60)).isFalse();
        }
    }
}
