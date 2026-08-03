package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BrokerCapabilitiesTest {

    @Test
    void fractionalShouldEnableBothAssetClasses() {
        BrokerCapabilities caps = BrokerCapabilities.fractional();

        assertThat(caps.fractionalEquities()).isTrue();
        assertThat(caps.fractionalCrypto()).isTrue();
    }

    @Test
    void wholeShareEquitiesShouldDisableEquitiesButAllowCrypto() {
        BrokerCapabilities caps = BrokerCapabilities.wholeShareEquities();

        assertThat(caps.fractionalEquities()).isFalse();
        assertThat(caps.fractionalCrypto()).isTrue();
    }

    @Test
    void supportsFractionalShouldReflectEquityFlagForStock() {
        assertThat(BrokerCapabilities.fractional().supportsFractional(InvestmentType.STOCK)).isTrue();
        assertThat(BrokerCapabilities.wholeShareEquities().supportsFractional(InvestmentType.STOCK)).isFalse();
    }

    @Test
    void supportsFractionalShouldReflectCryptoFlagForCrypto() {
        assertThat(BrokerCapabilities.fractional().supportsFractional(InvestmentType.CRYPTO)).isTrue();
        assertThat(BrokerCapabilities.wholeShareEquities().supportsFractional(InvestmentType.CRYPTO)).isTrue();
    }

    @Test
    void supportsFractionalShouldHonourEachFlagIndependently() {
        BrokerCapabilities equitiesOnly = new BrokerCapabilities(true, false);

        assertThat(equitiesOnly.supportsFractional(InvestmentType.STOCK)).isTrue();
        assertThat(equitiesOnly.supportsFractional(InvestmentType.CRYPTO)).isFalse();
    }

    @Test
    void supportsFractionalShouldRejectNullType() {
        assertThatThrownBy(() -> BrokerCapabilities.fractional().supportsFractional(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("InvestmentType");
    }

    @Test
    void recordEqualityShouldHoldOverBothFlags() {
        assertThat(BrokerCapabilities.fractional()).isEqualTo(new BrokerCapabilities(true, true));
        assertThat(BrokerCapabilities.wholeShareEquities()).isEqualTo(new BrokerCapabilities(false, true));
        assertThat(BrokerCapabilities.fractional()).isNotEqualTo(BrokerCapabilities.wholeShareEquities());
    }
}
