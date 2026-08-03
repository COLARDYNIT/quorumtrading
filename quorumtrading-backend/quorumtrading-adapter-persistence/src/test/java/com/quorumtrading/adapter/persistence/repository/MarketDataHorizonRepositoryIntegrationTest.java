package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.MarketDataHorizonRepository;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.GranularityBucket;
import com.quorumtrading.model.enums.HorizonSource;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.DataHorizon;
import com.quorumtrading.model.valueobject.Symbol;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link MarketDataHorizonRepository} against PostgreSQL
 * {@code quorumtrading_test}. Infra-gated like the other persistence integration
 * tests (requires a running database; skipped when infra is absent).
 */
@QuarkusTest
class MarketDataHorizonRepositoryIntegrationTest {

    @Inject
    MarketDataHorizonRepository repository;

    private static final String PROVIDER = "probe";

    @Test
    @DisplayName("upsert then find round-trips a horizon with data")
    void upsertAndFindWithData() {
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        DataHorizon horizon = new DataHorizon(PROVIDER, btc, EventType.PRICE, GranularityBucket.DAILY,
                Optional.of(LocalDate.of(2024, 7, 31)), Instant.now(), HorizonSource.PROBE);

        repository.upsert(horizon);

        Optional<DataHorizon> found = repository.find(PROVIDER, btc, EventType.PRICE, GranularityBucket.DAILY);
        assertThat(found).isPresent();
        assertThat(found.get().earliestAvailable()).contains(LocalDate.of(2024, 7, 31));
        assertThat(found.get().hasData()).isTrue();
        assertThat(found.get().symbol().type()).isEqualTo(InvestmentType.CRYPTO);
    }

    @Test
    @DisplayName("upsert overwrites the existing row for the same key")
    void upsertOverwrites() {
        Symbol eth = new Symbol("ETH", InvestmentType.CRYPTO);
        repository.upsert(new DataHorizon(PROVIDER, eth, EventType.PRICE, GranularityBucket.DAILY,
                Optional.of(LocalDate.of(2024, 1, 1)), Instant.now(), HorizonSource.PROBE));
        repository.upsert(new DataHorizon(PROVIDER, eth, EventType.PRICE, GranularityBucket.DAILY,
                Optional.of(LocalDate.of(2023, 1, 1)), Instant.now(), HorizonSource.OBSERVED_FETCH));

        Optional<DataHorizon> found = repository.find(PROVIDER, eth, EventType.PRICE, GranularityBucket.DAILY);
        assertThat(found).isPresent();
        assertThat(found.get().earliestAvailable()).contains(LocalDate.of(2023, 1, 1));
        assertThat(found.get().source()).isEqualTo(HorizonSource.OBSERVED_FETCH);
    }

    @Test
    @DisplayName("no-data horizon persists with empty earliestAvailable")
    void upsertNoDataRow() {
        Symbol sol = new Symbol("SOL", InvestmentType.CRYPTO);
        repository.upsert(new DataHorizon(PROVIDER, sol, EventType.NEWS, GranularityBucket.DAILY,
                Optional.empty(), Instant.now(), HorizonSource.PROBE));

        Optional<DataHorizon> found = repository.find(PROVIDER, sol, EventType.NEWS, GranularityBucket.DAILY);
        assertThat(found).isPresent();
        assertThat(found.get().hasData()).isFalse();
        assertThat(found.get().earliestAvailable()).isEmpty();
    }

    @Test
    @DisplayName("findForSymbols returns horizons for multiple symbols at one type/bucket")
    void findForSymbols() {
        Symbol aapl = new Symbol("AAPL", InvestmentType.STOCK);
        Symbol msft = new Symbol("MSFT", InvestmentType.STOCK);
        repository.upsert(new DataHorizon(PROVIDER, aapl, EventType.PRICE, GranularityBucket.DAILY,
                Optional.of(LocalDate.of(2000, 1, 1)), Instant.now(), HorizonSource.PROBE));
        repository.upsert(new DataHorizon(PROVIDER, msft, EventType.PRICE, GranularityBucket.DAILY,
                Optional.of(LocalDate.of(1999, 1, 1)), Instant.now(), HorizonSource.PROBE));

        List<DataHorizon> found = repository.findForSymbols(
                Set.of(aapl, msft), EventType.PRICE, GranularityBucket.DAILY);

        assertThat(found).extracting(h -> h.symbol().ticker())
                .contains("AAPL", "MSFT");
    }
}
