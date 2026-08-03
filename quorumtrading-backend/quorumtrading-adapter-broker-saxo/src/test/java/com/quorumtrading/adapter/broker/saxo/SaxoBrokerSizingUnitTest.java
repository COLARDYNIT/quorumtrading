package com.quorumtrading.adapter.broker.saxo;

import com.quorumtrading.adapter.broker.saxo.SaxoUicResolver.ResolvedInstrument;
import com.quorumtrading.model.enums.AssetType;
import com.quorumtrading.model.enums.TradeStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure (no Quarkus boot) unit test for the SB4 whole-share sizing guard and the Saxo→domain status
 * mapping on {@link SaxoBrokerAdapter}. Fast: asserts the order-acceptance gate and status mapping
 * directly on the static helpers.
 */
class SaxoBrokerSizingUnitTest {

    private static final Symbol RHM = Symbol.parse("RHM:XETR");

    private static ResolvedInstrument instrument(BigDecimal minTrade, BigDecimal lot) {
        return new ResolvedInstrument(
                210840L, AssetType.STOCK, "XETR", RHM.exchange(), Money.Currency.EUR,
                true, minTrade, lot, null, false, "Rheinmetall AG");
    }

    @Test
    void acceptsAPositiveWholeShareAmount() {
        Optional<String> err = SaxoBrokerAdapter.validateWholeShareAmount(
                new BigDecimal("3"), instrument(BigDecimal.ONE, BigDecimal.ONE), RHM);
        assertThat(err).isEmpty();
    }

    @Test
    void rejectsAFractionalAmount() {
        Optional<String> err = SaxoBrokerAdapter.validateWholeShareAmount(
                new BigDecimal("2.5"), instrument(BigDecimal.ONE, BigDecimal.ONE), RHM);
        assertThat(err).hasValueSatisfying(m -> assertThat(m).contains("whole-share"));
    }

    @Test
    void rejectsZeroOrNegativeAmount() {
        assertThat(SaxoBrokerAdapter.validateWholeShareAmount(
                BigDecimal.ZERO, instrument(BigDecimal.ONE, BigDecimal.ONE), RHM)).isPresent();
        assertThat(SaxoBrokerAdapter.validateWholeShareAmount(
                new BigDecimal("-1"), instrument(BigDecimal.ONE, BigDecimal.ONE), RHM)).isPresent();
    }

    @Test
    void rejectsBelowMinimumTradeSize() {
        Optional<String> err = SaxoBrokerAdapter.validateWholeShareAmount(
                new BigDecimal("3"), instrument(new BigDecimal("10"), BigDecimal.ONE), RHM);
        assertThat(err).hasValueSatisfying(m -> assertThat(m).contains("MinimumTradeSize"));
    }

    @Test
    void rejectsAmountNotAWholeMultipleOfLotSize() {
        Optional<String> err = SaxoBrokerAdapter.validateWholeShareAmount(
                new BigDecimal("7"), instrument(BigDecimal.ONE, new BigDecimal("5")), RHM);
        assertThat(err).hasValueSatisfying(m -> assertThat(m).contains("LotSize"));
    }

    @Test
    void acceptsAmountOnTheLotBoundary() {
        Optional<String> err = SaxoBrokerAdapter.validateWholeShareAmount(
                new BigDecimal("10"), instrument(BigDecimal.ONE, new BigDecimal("5")), RHM);
        assertThat(err).isEmpty();
    }

    @Test
    void mapsSaxoStatusStrings() {
        assertThat(SaxoBrokerAdapter.mapSaxoStatus("Filled", null, null)).isEqualTo(TradeStatus.EXECUTED);
        assertThat(SaxoBrokerAdapter.mapSaxoStatus("PartiallyFilled", null, null))
                .isEqualTo(TradeStatus.PARTIAL_FILL);
        assertThat(SaxoBrokerAdapter.mapSaxoStatus("Cancelled", null, null)).isEqualTo(TradeStatus.REJECTED);
        assertThat(SaxoBrokerAdapter.mapSaxoStatus("Working", null, null)).isEqualTo(TradeStatus.PENDING);
    }

    @Test
    void infersExecutedFromFillWhenStatusAbsent() {
        Money price = Money.of(new BigDecimal("100.00"), Money.Currency.EUR);
        assertThat(SaxoBrokerAdapter.mapSaxoStatus(null, price, new BigDecimal("2")))
                .isEqualTo(TradeStatus.EXECUTED);
        assertThat(SaxoBrokerAdapter.mapSaxoStatus(null, null, null)).isEqualTo(TradeStatus.PENDING);
    }
}
