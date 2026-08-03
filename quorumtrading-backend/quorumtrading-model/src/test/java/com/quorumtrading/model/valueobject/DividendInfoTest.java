package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DividendInfoTest {

    @Test
    void noDividends_shouldCreateStockWithNoDividends() {
        DividendInfo info = DividendInfo.noDividends("AAPL");

        assertThat(info.ticker()).isEqualTo("AAPL");
        assertThat(info.paysDividends()).isFalse();
        assertThat(info.assetType()).isEqualTo("STOCK");
        assertThat(info.annualYieldEstimate()).isNull();
        assertThat(info.dividendCountLastYear()).isZero();
    }

    @Test
    void crypto_shouldCreateCryptoWithNoDividends() {
        DividendInfo info = DividendInfo.crypto("BTC");

        assertThat(info.ticker()).isEqualTo("BTC");
        assertThat(info.paysDividends()).isFalse();
        assertThat(info.assetType()).isEqualTo("CRYPTO");
        assertThat(info.annualYieldEstimate()).isNull();
        assertThat(info.dividendCountLastYear()).isZero();
    }

    @Test
    void withDividends_shouldCreateStockWithYieldAndCount() {
        DividendInfo info = DividendInfo.withDividends("MSFT", new BigDecimal("0.82"), 4);

        assertThat(info.ticker()).isEqualTo("MSFT");
        assertThat(info.paysDividends()).isTrue();
        assertThat(info.assetType()).isEqualTo("STOCK");
        assertThat(info.annualYieldEstimate()).isEqualByComparingTo("0.82");
        assertThat(info.dividendCountLastYear()).isEqualTo(4);
    }

    @Test
    void unknown_shouldNotClaimTheTickerPaysNothing() {
        // This test previously asserted that an unknown lookup yields
        // paysDividends=false and a count of zero — which is the defect, written
        // down as an expectation. "We could not find out" and "it pays nothing"
        // are different answers, and only one of them may be shown as fact.
        DividendInfo stockUnknown = DividendInfo.unknown("ZZZZ", "STOCK");

        assertThat(stockUnknown.ticker()).isEqualTo("ZZZZ");
        assertThat(stockUnknown.paysDividends())
            .as("null is the only honest value when the history was never established")
            .isNull();
        assertThat(stockUnknown.dividendCountLastYear()).isNull();
        assertThat(stockUnknown.isKnown()).isFalse();
        assertThat(stockUnknown.assetType()).isEqualTo("STOCK");
        assertThat(stockUnknown.annualYieldEstimate()).isNull();

        DividendInfo cryptoUnknown = DividendInfo.unknown("BTC", "CRYPTO");

        assertThat(cryptoUnknown.assetType()).isEqualTo("CRYPTO");
    }

    @Test
    void shouldThrowWhenTickerIsNull() {
        assertThatThrownBy(() -> DividendInfo.noDividends(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ticker must not be null or blank");
    }

    @Test
    void shouldThrowWhenTickerIsBlank() {
        assertThatThrownBy(() -> DividendInfo.noDividends("  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ticker must not be null or blank");
    }

    @Test
    void shouldThrowWhenAssetTypeIsNull() {
        assertThatThrownBy(() -> new DividendInfo("AAPL", false, null, 0, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Asset type must not be null or blank");
    }

    @Test
    void shouldThrowWhenAssetTypeIsBlank() {
        assertThatThrownBy(() -> new DividendInfo("AAPL", false, null, 0, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Asset type must not be null or blank");
    }

    @Test
    void shouldThrowWhenDividendCountIsNegative() {
        assertThatThrownBy(() -> new DividendInfo("AAPL", true, new BigDecimal("1.0"), -1, "STOCK"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Dividend count must not be negative");
    }

    @org.junit.jupiter.api.Test
    void unknownIsDistinguishableFromPayingNothing() {
        // These were byte-identical records, so a failed lookup was indistinguishable
        // from a genuine no-dividend stock — and Coca-Cola came back declaring it pays
        // no dividends. Absence of evidence must not serialise as a negative claim.
        DividendInfo unknown = DividendInfo.unknown("KO", "STOCK");
        DividendInfo noDividends = DividendInfo.noDividends("TSLA");

        assertThat(unknown.isKnown()).isFalse();
        assertThat(unknown.paysDividends()).isNull();
        assertThat(unknown.dividendCountLastYear()).isNull();
        assertThat(unknown.paysDividendsKnown()).isFalse();

        assertThat(noDividends.isKnown()).isTrue();
        assertThat(noDividends.paysDividends()).isFalse();
        assertThat(noDividends.dividendCountLastYear()).isZero();

        assertThat(unknown).isNotEqualTo(noDividends);
    }

    @org.junit.jupiter.api.Test
    void payingDividendsIsStillReportedAsKnown() {
        DividendInfo payer = DividendInfo.withDividends("KO", new java.math.BigDecimal("2.9"), 4);

        assertThat(payer.isKnown()).isTrue();
        assertThat(payer.paysDividendsKnown()).isTrue();
        assertThat(payer.dividendCountLastYear()).isEqualTo(4);
    }
}
