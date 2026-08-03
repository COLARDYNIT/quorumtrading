package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class DataCoverageMetricsTest {

    @Test
    void shouldCreateValidMetrics() {
        DataCoverageMetrics metrics = new DataCoverageMetrics(
            new BigDecimal("85.5"),
            Map.of(EventType.PRICE, new BigDecimal("100")),
            Map.of(new Symbol("BTC", InvestmentType.CRYPTO), new BigDecimal("90"))
        );

        assertThat(metrics.overallPercentage()).isEqualTo(new BigDecimal("85.5"));
        assertThat(metrics.isComplete()).isFalse();
    }

    @Test
    void shouldIdentifyCompleteData() {
        DataCoverageMetrics metrics = new DataCoverageMetrics(
            new BigDecimal("100"),
            Map.of(EventType.PRICE, new BigDecimal("100")),
            Map.of(new Symbol("BTC", InvestmentType.CRYPTO), new BigDecimal("100"))
        );

        assertThat(metrics.isComplete()).isTrue();
    }
}
