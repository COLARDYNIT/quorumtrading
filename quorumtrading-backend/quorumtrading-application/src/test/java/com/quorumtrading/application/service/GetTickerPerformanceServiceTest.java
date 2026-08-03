package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.TickerPerformancePort;
import com.quorumtrading.application.port.out.TickerPerformancePort.PerformanceAggregate;
import com.quorumtrading.application.usecase.query.GetTickerPerformanceQuery;
import com.quorumtrading.application.usecase.result.TickerPerformanceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTickerPerformanceServiceTest {

    @Mock
    private TickerPerformancePort tickerPerformancePort;

    private GetTickerPerformanceService service;

    @BeforeEach
    void setUp() {
        service = new GetTickerPerformanceService(tickerPerformancePort);
    }

    @Test
    void returnsPerformance_whenDataExists() {
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-01-05T23:59:59Z");

        PerformanceAggregate agg = new PerformanceAggregate(
            new BigDecimal("100"), Instant.parse("2024-01-01T00:00:00Z"),
            new BigDecimal("110"), Instant.parse("2024-01-05T00:00:00Z"),
            new BigDecimal("120"), new BigDecimal("90"),
            5
        );
        when(tickerPerformancePort.getPerformanceAggregate(any(), any(), any())).thenReturn(Optional.of(agg));

        TickerPerformanceResult result = service.execute(new GetTickerPerformanceQuery("QQQ", start, end));

        assertThat(result.dataAvailable()).isTrue();
        assertThat(result.ticker()).isEqualTo("QQQ");
        assertThat(result.startPrice()).isEqualByComparingTo("100");
        assertThat(result.endPrice()).isEqualByComparingTo("110");
        assertThat(result.periodHigh()).isEqualByComparingTo("120");
        assertThat(result.periodLow()).isEqualByComparingTo("90");
        assertThat(result.changePercent()).isEqualByComparingTo("10.00");
        assertThat(result.changeAbsolute()).isEqualByComparingTo("10");
        assertThat(result.dataPoints()).isEqualTo(5);
        assertThat(result.message()).isNull();
    }

    @Test
    void returnsBackfillMessage_whenNoData() {
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-01-31T23:59:59Z");

        when(tickerPerformancePort.getPerformanceAggregate(any(), any(), any())).thenReturn(Optional.empty());

        TickerPerformanceResult result = service.execute(new GetTickerPerformanceQuery("ZZZZ", start, end));

        assertThat(result.dataAvailable()).isFalse();
        assertThat(result.ticker()).isEqualTo("ZZZZ");
        assertThat(result.dataPoints()).isZero();
        assertThat(result.message()).contains("backfill_price_data");
    }

    @Test
    void returnsZeroChange_whenSingleBar() {
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-01-01T23:59:59Z");

        PerformanceAggregate agg = new PerformanceAggregate(
            new BigDecimal("149"), Instant.parse("2024-01-01T00:00:00Z"),
            new BigDecimal("149"), Instant.parse("2024-01-01T00:00:00Z"),
            new BigDecimal("150"), new BigDecimal("148"),
            1
        );
        when(tickerPerformancePort.getPerformanceAggregate(any(), any(), any())).thenReturn(Optional.of(agg));

        TickerPerformanceResult result = service.execute(new GetTickerPerformanceQuery("AAPL", start, end));

        assertThat(result.dataAvailable()).isTrue();
        assertThat(result.startPrice()).isEqualByComparingTo("149");
        assertThat(result.endPrice()).isEqualByComparingTo("149");
        assertThat(result.changePercent()).isEqualByComparingTo("0.00");
        assertThat(result.changeAbsolute()).isEqualByComparingTo("0");
        assertThat(result.dataPoints()).isEqualTo(1);
    }
}
