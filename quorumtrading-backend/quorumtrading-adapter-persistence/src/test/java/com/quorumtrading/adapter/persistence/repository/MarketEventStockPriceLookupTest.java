package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.PriceBarJpaEntity;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.MarketEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An equity's price bars must reach a caller that asks for the whole picture.
 *
 * <p>When no event type is specified the repository fans out per symbol, and that
 * loop used to pass every ticker as the CRYPTO argument with an empty STOCK set.
 * The PRICE branch then filters on {@code investmentType = 'CRYPTO' OR IS NULL},
 * and since migration V127 stamps that column, every stock bar written after it
 * was excluded — silently, and only for equities.</p>
 *
 * <p>The consequence was not a missing field on a report. The consensus context
 * is built from these events, so the Technical agent received no price statistics
 * for any stock, said so in its own reasoning ("Exact last close isn't itemized"),
 * inferred a price from the Bollinger midline, and traded on the inference. Crypto
 * was unaffected, which disguised it as an equities-only quirk.</p>
 */
@QuarkusTest
class MarketEventStockPriceLookupTest {

    private static final String STOCK_SYMBOL = "SPLKT";
    private static final String CRYPTO_SYMBOL = "CPLKT";

    @Inject
    MarketEventRepositoryImpl repository;

    @BeforeEach
    @Transactional
    void setup() {
        PriceBarJpaEntity.delete("symbol = ?1", STOCK_SYMBOL);
        PriceBarJpaEntity.delete("symbol = ?1", CRYPTO_SYMBOL);
    }

    @Test
    @Transactional
    @DisplayName("a stock's price bars are returned when no event type is specified")
    void stockPriceBarsSurviveTheUnfilteredLookup() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        insertBar(STOCK_SYMBOL, "STOCK", now.minus(1, ChronoUnit.HOURS));

        List<MarketEvent> events = repository.findByTickersAndTimeRange(
            Set.of(), Set.of(STOCK_SYMBOL),
            now.minus(2, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
            null, 50);

        assertThat(events)
            .as("without price events the agents have no close price and infer one instead")
            .anyMatch(e -> e.eventType() == EventType.PRICE && STOCK_SYMBOL.equals(e.symbol()));
    }

    @Test
    @Transactional
    @DisplayName("crypto price bars still come back, and the two universes stay separate")
    void cryptoIsUnaffectedAndClassesDoNotLeak() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        insertBar(CRYPTO_SYMBOL, "CRYPTO", now.minus(1, ChronoUnit.HOURS));
        insertBar(STOCK_SYMBOL, "STOCK", now.minus(1, ChronoUnit.HOURS));

        List<MarketEvent> cryptoOnly = repository.findByTickersAndTimeRange(
            Set.of(CRYPTO_SYMBOL), Set.of(),
            now.minus(2, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
            null, 50);

        assertThat(cryptoOnly)
            .anyMatch(e -> e.eventType() == EventType.PRICE && CRYPTO_SYMBOL.equals(e.symbol()));
        assertThat(cryptoOnly)
            .as("asking for a coin must not surface an equity's bars — the separation the "
                + "PRICE branch exists to enforce")
            .noneMatch(e -> STOCK_SYMBOL.equals(e.symbol()));
    }

    @Test
    @Transactional
    @DisplayName("a mixed request returns both universes")
    void mixedRequestReturnsBoth() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        insertBar(CRYPTO_SYMBOL, "CRYPTO", now.minus(1, ChronoUnit.HOURS));
        insertBar(STOCK_SYMBOL, "STOCK", now.minus(1, ChronoUnit.HOURS));

        List<MarketEvent> events = repository.findByTickersAndTimeRange(
            Set.of(CRYPTO_SYMBOL), Set.of(STOCK_SYMBOL),
            now.minus(2, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
            null, 50);

        assertThat(events).anyMatch(e -> CRYPTO_SYMBOL.equals(e.symbol()));
        assertThat(events).anyMatch(e -> STOCK_SYMBOL.equals(e.symbol()));
    }

    private void insertBar(String symbol, String investmentType, Instant timestamp) {
        PriceBarJpaEntity entity = new PriceBarJpaEntity();
        entity.timestamp = timestamp;
        entity.source = "test";
        entity.symbol = symbol;
        entity.investmentType = investmentType;
        entity.ingestedAt = Instant.now();
        entity.open = new BigDecimal("100.00");
        entity.high = new BigDecimal("105.00");
        entity.low = new BigDecimal("99.00");
        entity.close = new BigDecimal("103.00");
        entity.volume = new BigDecimal("50000");
        entity.persist();
    }
}
