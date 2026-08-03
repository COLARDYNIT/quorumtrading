package com.quorumtrading.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentTypeTest {

    @Test
    void knownCryptoTickersClassifyAsCrypto() {
        assertThat(InvestmentType.fromTicker("BTC")).isEqualTo(InvestmentType.CRYPTO);
        assertThat(InvestmentType.fromTicker("ETH")).isEqualTo(InvestmentType.CRYPTO);
        assertThat(InvestmentType.fromTicker("eth")).isEqualTo(InvestmentType.CRYPTO); // case-insensitive
        assertThat(InvestmentType.fromTicker("SOL")).isEqualTo(InvestmentType.CRYPTO);
    }

    @Test
    void equitiesClassifyAsStock() {
        assertThat(InvestmentType.fromTicker("AAPL")).isEqualTo(InvestmentType.STOCK);
        assertThat(InvestmentType.fromTicker("QQQ")).isEqualTo(InvestmentType.STOCK);
        assertThat(InvestmentType.fromTicker("ARKK")).isEqualTo(InvestmentType.STOCK);
        assertThat(InvestmentType.fromTicker("TLT")).isEqualTo(InvestmentType.STOCK);
    }

    @Test
    void normalisesPairSuffixes() {
        assertThat(InvestmentType.fromTicker("BTC/USD")).isEqualTo(InvestmentType.CRYPTO);
        assertThat(InvestmentType.fromTicker("ETH-USD")).isEqualTo(InvestmentType.CRYPTO);
        assertThat(InvestmentType.fromTicker("SOL-USDT")).isEqualTo(InvestmentType.CRYPTO);
    }

    @Test
    void nullBlankOrUnknownDefaultsToStock() {
        assertThat(InvestmentType.fromTicker(null)).isEqualTo(InvestmentType.STOCK);
        assertThat(InvestmentType.fromTicker("")).isEqualTo(InvestmentType.STOCK);
        assertThat(InvestmentType.fromTicker("ZZZZ")).isEqualTo(InvestmentType.STOCK);
    }
}
