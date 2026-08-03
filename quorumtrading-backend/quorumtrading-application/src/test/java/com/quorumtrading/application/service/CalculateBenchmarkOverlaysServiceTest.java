package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.BackFillTickerPriceDataUseCase;
import com.quorumtrading.application.usecase.command.CalculateBenchmarkOverlaysCommand;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static com.quorumtrading.application.service.TestConstants.TEST_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalculateBenchmarkOverlaysServiceTest {

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private HistoricalPricePort historicalPricePort;

    @Mock
    private BackFillTickerPriceDataUseCase backFillTickerPriceDataUseCase;

    private CalculateBenchmarkOverlaysService service;

    private static final BenchmarkId BENCHMARK_ID = BenchmarkId.generate();
    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-06-30T23:59:59Z");

    @BeforeEach
    void setUp() {
        BuyAndHoldOverlayCalculator calculator = new BuyAndHoldOverlayCalculator(
            historicalPricePort, backFillTickerPriceDataUseCase);
        service = new CalculateBenchmarkOverlaysService(
            benchmarkRepository, simulationRepository, new AuthorizationService(), calculator);
    }

    @Test
    void shouldComputeBuyAndHoldOverlayForSingleSymbol() {
        Benchmark benchmark = new Benchmark(BENCHMARK_ID, "Test", START, END, Instant.now(), TEST_USER_ID);
        when(benchmarkRepository.findById(BENCHMARK_ID)).thenReturn(Optional.of(benchmark));

        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);
        List<PriceBar> bars = List.of(
            new PriceBar(START, null, null, null, new BigDecimal("400.00"), null),
            new PriceBar(START.plus(30, ChronoUnit.DAYS), null, null, null, new BigDecimal("420.00"), null),
            new PriceBar(END, null, null, null, new BigDecimal("440.00"), null)
        );
        when(historicalPricePort.getHistoricalPrices(eq(spy), eq(START), eq(END), eq("1d"))).thenReturn(bars);

        CalculateBenchmarkOverlaysCommand cmd = new CalculateBenchmarkOverlaysCommand(
            BENCHMARK_ID, List.of(spy), Money.usd(100_000), TEST_USER_ID);

        List<OverlayResult> results = service.execute(cmd);

        assertThat(results).hasSize(1);
        OverlayResult result = results.get(0);
        assertThat(result.symbol()).isEqualTo("SPY");
        assertThat(result.assetType()).isEqualTo("STOCK");
        assertThat(result.status()).isEqualTo(OverlayResult.OverlayStatus.READY);
        assertThat(result.dataPoints()).hasSize(3);

        // First data point should equal initial capital
        assertThat(result.dataPoints().get(0).portfolioValue())
            .isEqualByComparingTo(new BigDecimal("100000.00"));

        // Last data point: 100000 / 400 * 440 = 110000
        assertThat(result.dataPoints().get(2).portfolioValue())
            .isEqualByComparingTo(new BigDecimal("110000.00"));

        // Return: (110000 - 100000) / 100000 * 100 = 10.0000
        assertThat(result.returnPercent()).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void shouldReturnNoDataWhenNoPriceBarsExist() {
        Benchmark benchmark = new Benchmark(BENCHMARK_ID, "Test", START, END, Instant.now(), TEST_USER_ID);
        when(benchmarkRepository.findById(BENCHMARK_ID)).thenReturn(Optional.of(benchmark));

        Symbol unknown = new Symbol("XYZ", InvestmentType.STOCK);
        when(historicalPricePort.getHistoricalPrices(eq(unknown), eq(START), eq(END), eq("1d")))
            .thenReturn(List.of());
        when(backFillTickerPriceDataUseCase.execute(any())).thenThrow(new RuntimeException("No data"));

        CalculateBenchmarkOverlaysCommand cmd = new CalculateBenchmarkOverlaysCommand(
            BENCHMARK_ID, List.of(unknown), Money.usd(100_000), TEST_USER_ID);

        List<OverlayResult> results = service.execute(cmd);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(OverlayResult.OverlayStatus.NO_DATA);
        assertThat(results.get(0).errorMessage()).contains("XYZ");
    }

    @Test
    void shouldAutoBackfillWhenNoPriceDataExists() {
        Benchmark benchmark = new Benchmark(BENCHMARK_ID, "Test", START, END, Instant.now(), TEST_USER_ID);
        when(benchmarkRepository.findById(BENCHMARK_ID)).thenReturn(Optional.of(benchmark));

        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);

        // First call: empty, second call after backfill: data present
        when(historicalPricePort.getHistoricalPrices(eq(spy), eq(START), eq(END), eq("1d")))
            .thenReturn(List.of())
            .thenReturn(List.of(
                new PriceBar(START, null, null, null, new BigDecimal("400.00"), null),
                new PriceBar(END, null, null, null, new BigDecimal("400.00"), null)
            ));

        CalculateBenchmarkOverlaysCommand cmd = new CalculateBenchmarkOverlaysCommand(
            BENCHMARK_ID, List.of(spy), Money.usd(100_000), TEST_USER_ID);

        List<OverlayResult> results = service.execute(cmd);

        verify(backFillTickerPriceDataUseCase).execute(any());
        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(OverlayResult.OverlayStatus.READY);
    }

    @Test
    void shouldDefaultToFirstSimulationInitialCashWhenCapitalNotProvided() {
        Benchmark benchmark = new Benchmark(BENCHMARK_ID, "Test", START, END, Instant.now(), TEST_USER_ID);
        when(benchmarkRepository.findById(BENCHMARK_ID)).thenReturn(Optional.of(benchmark));

        Simulation sim = mock(Simulation.class);
        when(sim.getInitialCash()).thenReturn(Money.usd(50_000));
        when(simulationRepository.findByBenchmarkId(BENCHMARK_ID)).thenReturn(List.of(sim));

        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);
        when(historicalPricePort.getHistoricalPrices(eq(spy), eq(START), eq(END), eq("1d")))
            .thenReturn(List.of(
                new PriceBar(START, null, null, null, new BigDecimal("400.00"), null),
                new PriceBar(END, null, null, null, new BigDecimal("400.00"), null)
            ));

        CalculateBenchmarkOverlaysCommand cmd = new CalculateBenchmarkOverlaysCommand(
            BENCHMARK_ID, List.of(spy), null, TEST_USER_ID);

        List<OverlayResult> results = service.execute(cmd);

        assertThat(results).hasSize(1);
        // With 50k capital and flat price: should be 50000.00
        assertThat(results.get(0).dataPoints().get(0).portfolioValue())
            .isEqualByComparingTo(new BigDecimal("50000.00"));
    }

    @Test
    void shouldComputeMultipleOverlays() {
        Benchmark benchmark = new Benchmark(BENCHMARK_ID, "Test", START, END, Instant.now(), TEST_USER_ID);
        when(benchmarkRepository.findById(BENCHMARK_ID)).thenReturn(Optional.of(benchmark));

        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);

        when(historicalPricePort.getHistoricalPrices(eq(spy), eq(START), eq(END), eq("1d")))
            .thenReturn(List.of(
                new PriceBar(START, null, null, null, new BigDecimal("400.00"), null),
                new PriceBar(END, null, null, null, new BigDecimal("440.00"), null)
            ));
        when(historicalPricePort.getHistoricalPrices(eq(btc), eq(START), eq(END), eq("1d")))
            .thenReturn(List.of(
                new PriceBar(START, null, null, null, new BigDecimal("40000.00"), null),
                new PriceBar(END, null, null, null, new BigDecimal("60000.00"), null)
            ));

        CalculateBenchmarkOverlaysCommand cmd = new CalculateBenchmarkOverlaysCommand(
            BENCHMARK_ID, List.of(spy, btc), Money.usd(100_000), TEST_USER_ID);

        List<OverlayResult> results = service.execute(cmd);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).symbol()).isEqualTo("SPY");
        assertThat(results.get(0).returnPercent()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(results.get(1).symbol()).isEqualTo("BTC");
        assertThat(results.get(1).returnPercent()).isEqualByComparingTo(new BigDecimal("50.0000"));
    }

    @Test
    void shouldBackfillWhenFirstBarStartsSignificantlyAfterStartDate() {
        Benchmark benchmark = new Benchmark(BENCHMARK_ID, "Test", START, END, Instant.now(), TEST_USER_ID);
        when(benchmarkRepository.findById(BENCHMARK_ID)).thenReturn(Optional.of(benchmark));

        Symbol vti = new Symbol("VTI", InvestmentType.STOCK);
        Instant lateStart = START.plus(60, ChronoUnit.DAYS); // Data starts ~March instead of Jan

        // First call: partial data starting 60 days after startDate
        // Second call (after backfill): full data from startDate
        when(historicalPricePort.getHistoricalPrices(eq(vti), eq(START), eq(END), eq("1d")))
            .thenReturn(List.of(
                new PriceBar(lateStart, null, null, null, new BigDecimal("200.00"), null),
                new PriceBar(END, null, null, null, new BigDecimal("220.00"), null)
            ))
            .thenReturn(List.of(
                new PriceBar(START, null, null, null, new BigDecimal("190.00"), null),
                new PriceBar(lateStart, null, null, null, new BigDecimal("200.00"), null),
                new PriceBar(END, null, null, null, new BigDecimal("220.00"), null)
            ));

        CalculateBenchmarkOverlaysCommand cmd = new CalculateBenchmarkOverlaysCommand(
            BENCHMARK_ID, List.of(vti), Money.usd(100_000), TEST_USER_ID);

        List<OverlayResult> results = service.execute(cmd);

        verify(backFillTickerPriceDataUseCase).execute(any());
        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(OverlayResult.OverlayStatus.READY);
        assertThat(results.get(0).dataPoints()).hasSize(3);
        // First data point should start at Jan 1st (startDate), not March
        assertThat(results.get(0).dataPoints().get(0).timestamp()).isEqualTo(START);
    }

    @Test
    void shouldNotBackfillWhenGapIsBelowThreshold() {
        Benchmark benchmark = new Benchmark(BENCHMARK_ID, "Test", START, END, Instant.now(), TEST_USER_ID);
        when(benchmarkRepository.findById(BENCHMARK_ID)).thenReturn(Optional.of(benchmark));

        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);
        // First bar is 3 days after start (weekend gap) — below the 5-day threshold
        Instant slightlyLate = START.plus(3, ChronoUnit.DAYS);

        when(historicalPricePort.getHistoricalPrices(eq(spy), eq(START), eq(END), eq("1d")))
            .thenReturn(List.of(
                new PriceBar(slightlyLate, null, null, null, new BigDecimal("400.00"), null),
                new PriceBar(END, null, null, null, new BigDecimal("440.00"), null)
            ));

        CalculateBenchmarkOverlaysCommand cmd = new CalculateBenchmarkOverlaysCommand(
            BENCHMARK_ID, List.of(spy), Money.usd(100_000), TEST_USER_ID);

        List<OverlayResult> results = service.execute(cmd);

        verifyNoInteractions(backFillTickerPriceDataUseCase);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(OverlayResult.OverlayStatus.READY);
    }

    @Test
    void shouldThrowWhenBenchmarkNotFound() {
        when(benchmarkRepository.findById(BENCHMARK_ID)).thenReturn(Optional.empty());

        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);
        CalculateBenchmarkOverlaysCommand cmd = new CalculateBenchmarkOverlaysCommand(
            BENCHMARK_ID, List.of(spy), Money.usd(100_000), TEST_USER_ID);

        assertThatThrownBy(() -> service.execute(cmd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Benchmark not found");
    }
}
