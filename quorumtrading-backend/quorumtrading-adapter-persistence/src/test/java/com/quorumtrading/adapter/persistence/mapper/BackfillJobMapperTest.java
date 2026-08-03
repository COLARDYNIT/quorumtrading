package com.quorumtrading.adapter.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.persistence.entity.BackfillJobJpaEntity;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.marketdata.MarketDataGap;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BackfillJobMapperTest {

    private BackfillJobMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new BackfillJobMapper();
        Field f = BackfillJobMapper.class.getDeclaredField("objectMapper");
        f.setAccessible(true);
        f.set(mapper, new ObjectMapper());
    }

    @Test
    void cryptoAssetClassSurvivesPersistReloadRoundTrip() {
        // Regression: gaps used to be rehydrated as STOCK regardless of the original
        // asset class, so crypto backfills ran via the equity (weekday-only) path and
        // silently dropped crypto weekends.
        BackfillJob job = new BackfillJob(List.of(
            new MarketDataGap(new Symbol("BTC", InvestmentType.CRYPTO), EventType.PRICE,
                TimeRange.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
        ));

        BackfillJobJpaEntity entity = mapper.toEntity(job);
        BackfillJob reloaded = mapper.toDomain(entity);

        assertThat(reloaded.getGaps())
            .isNotEmpty()
            .allSatisfy(g -> {
                assertThat(g.symbol().ticker()).isEqualTo("BTC");
                assertThat(g.symbol().type()).isEqualTo(InvestmentType.CRYPTO);
            });
    }

    @Test
    void equityAssetClassIsPreserved() {
        BackfillJob job = new BackfillJob(List.of(
            new MarketDataGap(new Symbol("QQQ", InvestmentType.STOCK), EventType.PRICE,
                TimeRange.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
        ));

        BackfillJob reloaded = mapper.toDomain(mapper.toEntity(job));

        assertThat(reloaded.getGaps())
            .allSatisfy(g -> assertThat(g.symbol().type()).isEqualTo(InvestmentType.STOCK));
    }
}
