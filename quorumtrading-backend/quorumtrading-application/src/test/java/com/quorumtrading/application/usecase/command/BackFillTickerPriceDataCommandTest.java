package com.quorumtrading.application.usecase.command;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Asset-class validation on backfill.
 *
 * <p>A STOCK-typed backfill of "BTC" fetched the NYSE Arca bitcoin ETF (which really does trade
 * under that ticker) and stored it under {@code symbol='BTC'}, alongside genuine crypto bars.
 * Price rows are keyed by bare symbol, so this silently corrupted the shared series and
 * re-valued unrelated live simulations at ETF prices.
 */
class BackFillTickerPriceDataCommandTest {

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-20T00:00:00Z");

    private static BackFillTickerPriceDataCommand command(String ticker, String assetType) {
        return new BackFillTickerPriceDataCommand(ticker, assetType, FROM, TO, null);
    }

    @Test
    void rejectsAKnownCryptoRequestedAsStock() {
        assertThatThrownBy(() -> command("BTC", "STOCK").validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("BTC")
            .hasMessageContaining("CRYPTO");
    }

    @Test
    void rejectsAKnownCryptoRequestedAsStockRegardlessOfPairNotation() {
        assertThatThrownBy(() -> command("btc/usd", "STOCK").validate())
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("SOLUSD", "STOCK").validate())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsAKnownCryptoRequestedAsCrypto() {
        assertThatCode(() -> command("BTC", "CRYPTO").validate()).doesNotThrowAnyException();
    }

    @Test
    void allowsAnOrdinaryStock() {
        assertThatCode(() -> command("AAPL", "STOCK").validate()).doesNotThrowAnyException();
    }

    /**
     * The classifier is a fixed list and falls back to STOCK for anything it does not recognise,
     * so the converse check would reject every coin not on that list. Only the direction we can
     * be certain about is enforced.
     */
    @Test
    void allowsAnUnrecognisedTickerRequestedAsCrypto() {
        assertThatCode(() -> command("PEPE", "CRYPTO").validate()).doesNotThrowAnyException();
    }

    @Test
    void stillRejectsTheBasics() {
        assertThatThrownBy(() -> command("", "CRYPTO").validate())
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("BTC", "BOGUS").validate())
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BackFillTickerPriceDataCommand("BTC", "CRYPTO", TO, FROM, null).validate())
            .isInstanceOf(IllegalArgumentException.class);
    }
}
