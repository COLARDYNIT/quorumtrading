package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.CostPreset;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class MockBrokerConfigTest {

    @Test
    void shouldCreateFromPreset() {
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);

        MockBrokerConfig config = MockBrokerConfig.fromPreset(initialCapital, CostPreset.TYPICAL_RETAIL);

        assertThat(config.initialCapital()).isEqualTo(initialCapital);
        assertThat(config.preset()).isEqualTo(CostPreset.TYPICAL_RETAIL);
        assertThat(config.commissionRate()).isEqualByComparingTo("0");
        assertThat(config.cryptoCommissionRate()).isEqualByComparingTo("0.0025");
    }

    @Test
    void shouldCreateCustomConfig() {
        Money initialCapital = new Money(new BigDecimal("50000"), Money.Currency.USD);

        MockBrokerConfig config = MockBrokerConfig.custom(
            initialCapital,
            new BigDecimal("0.002"),
            new BigDecimal("0.003"),
            new BigDecimal("0.001"),
            new BigDecimal("0.00005"),
            new BigDecimal("1.8")
        );

        assertThat(config.preset()).isEqualTo(CostPreset.CUSTOM);
        assertThat(config.commissionRate()).isEqualByComparingTo("0.002");
        assertThat(config.cryptoCommissionRate()).isEqualByComparingTo("0.003");
        assertThat(config.slippageBase()).isEqualByComparingTo("0.001");
        assertThat(config.slippageImpactPerUnit()).isEqualByComparingTo("0.00005");
        assertThat(config.slippageVolatilityMultiplier()).isEqualByComparingTo("1.8");
    }

    @Test
    void shouldDetectMatchingPreset() {
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig config = MockBrokerConfig.custom(
            initialCapital,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ONE
        );

        assertThat(config.detectPreset()).isEqualTo(CostPreset.ZERO_FEES);
    }

    @Test
    void shouldDetectTypicalRetailPreset() {
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig config = MockBrokerConfig.custom(
            initialCapital,
            BigDecimal.ZERO,
            new BigDecimal("0.0025"),
            new BigDecimal("0.0005"),
            new BigDecimal("0.00001"),
            new BigDecimal("1.5")
        );

        assertThat(config.detectPreset()).isEqualTo(CostPreset.TYPICAL_RETAIL);
    }

    @Test
    void shouldDetectHighFrictionPreset() {
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig config = MockBrokerConfig.custom(
            initialCapital,
            new BigDecimal("0.005"),
            new BigDecimal("0.005"),
            new BigDecimal("0.002"),
            new BigDecimal("0.0001"),
            new BigDecimal("2.0")
        );

        assertThat(config.detectPreset()).isEqualTo(CostPreset.HIGH_FRICTION);
    }

    @Test
    void shouldReturnCustomWhenNoPresetMatches() {
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig config = MockBrokerConfig.custom(
            initialCapital,
            new BigDecimal("0.0015"),
            new BigDecimal("0.0020"),
            new BigDecimal("0.0003"),
            new BigDecimal("0.00002"),
            new BigDecimal("1.2")
        );

        assertThat(config.detectPreset()).isEqualTo(CostPreset.CUSTOM);
    }

    @Test
    void shouldThrowWhenUsingCustomPresetWithFromPreset() {
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);

        assertThatThrownBy(() -> MockBrokerConfig.fromPreset(initialCapital, CostPreset.CUSTOM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Use custom() factory for CUSTOM preset");
    }

    @Test
    void shouldThrowWhenInitialCapitalIsNull() {
        assertThatThrownBy(() -> MockBrokerConfig.fromPreset(null, CostPreset.TYPICAL_RETAIL))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("initialCapital cannot be null");
    }

    @Test
    void shouldThrowWhenCommissionRateIsNull() {
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);

        assertThatThrownBy(() -> MockBrokerConfig.custom(
            initialCapital,
            null,
            new BigDecimal("0.003"),
            new BigDecimal("0.001"),
            new BigDecimal("0.00005"),
            new BigDecimal("1.8")
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("commissionRate cannot be null");
    }

    @Test
    void shouldThrowWhenCryptoCommissionRateIsNull() {
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);

        assertThatThrownBy(() -> MockBrokerConfig.custom(
            initialCapital,
            new BigDecimal("0.002"),
            null,
            new BigDecimal("0.001"),
            new BigDecimal("0.00005"),
            new BigDecimal("1.8")
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("cryptoCommissionRate cannot be null");
    }

    @Test
    void shouldCreateZeroFeesFromPreset() {
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);

        MockBrokerConfig config = MockBrokerConfig.fromPreset(initialCapital, CostPreset.ZERO_FEES);

        assertThat(config.commissionRate()).isEqualByComparingTo("0");
        assertThat(config.cryptoCommissionRate()).isEqualByComparingTo("0");
        assertThat(config.slippageBase()).isEqualByComparingTo("0");
        assertThat(config.slippageImpactPerUnit()).isEqualByComparingTo("0");
        assertThat(config.slippageVolatilityMultiplier()).isEqualByComparingTo("1");
    }

    @Test
    void shouldDefaultToFractionalSharesTrue() {
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);

        MockBrokerConfig fromPreset = MockBrokerConfig.fromPreset(initialCapital, CostPreset.TYPICAL_RETAIL);
        MockBrokerConfig custom = MockBrokerConfig.custom(
            initialCapital,
            new BigDecimal("0.002"),
            new BigDecimal("0.003"),
            new BigDecimal("0.001"),
            new BigDecimal("0.00005"),
            new BigDecimal("1.8"));

        // Today's behaviour: every factory yields an Alpaca-flavoured fractional config.
        assertThat(fromPreset.fractionalShares()).isTrue();
        assertThat(custom.fractionalShares()).isTrue();
    }

    @Test
    void withFractionalSharesShouldFlipFlagAndPreserveCostParameters() {
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig fractional = MockBrokerConfig.fromPreset(initialCapital, CostPreset.HIGH_FRICTION);

        MockBrokerConfig nonFractional = fractional.withFractionalShares(false);

        assertThat(nonFractional.fractionalShares()).isFalse();
        // All other parameters are carried over unchanged.
        assertThat(nonFractional.initialCapital()).isEqualTo(fractional.initialCapital());
        assertThat(nonFractional.commissionRate()).isEqualByComparingTo(fractional.commissionRate());
        assertThat(nonFractional.cryptoCommissionRate()).isEqualByComparingTo(fractional.cryptoCommissionRate());
        assertThat(nonFractional.slippageBase()).isEqualByComparingTo(fractional.slippageBase());
        assertThat(nonFractional.slippageImpactPerUnit()).isEqualByComparingTo(fractional.slippageImpactPerUnit());
        assertThat(nonFractional.slippageVolatilityMultiplier()).isEqualByComparingTo(fractional.slippageVolatilityMultiplier());
        assertThat(nonFractional.preset()).isEqualTo(fractional.preset());
    }

    @Test
    void capabilitiesShouldReflectFractionalFlag() {
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig alpaca = MockBrokerConfig.fromPreset(initialCapital, CostPreset.ZERO_FEES);
        MockBrokerConfig saxo = alpaca.withFractionalShares(false);

        // Alpaca-flavoured: fractional on every asset class.
        assertThat(alpaca.capabilities().supportsFractional(com.quorumtrading.model.enums.InvestmentType.STOCK)).isTrue();
        assertThat(alpaca.capabilities().supportsFractional(com.quorumtrading.model.enums.InvestmentType.CRYPTO)).isTrue();
        // Saxo-flavoured: whole-share equities, fractional crypto.
        assertThat(saxo.capabilities().supportsFractional(com.quorumtrading.model.enums.InvestmentType.STOCK)).isFalse();
        assertThat(saxo.capabilities().supportsFractional(com.quorumtrading.model.enums.InvestmentType.CRYPTO)).isTrue();
    }

    @Test
    void shouldCreateHighFrictionFromPreset() {
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);

        MockBrokerConfig config = MockBrokerConfig.fromPreset(initialCapital, CostPreset.HIGH_FRICTION);

        assertThat(config.commissionRate()).isEqualByComparingTo("0.005");
        assertThat(config.cryptoCommissionRate()).isEqualByComparingTo("0.005");
        assertThat(config.slippageBase()).isEqualByComparingTo("0.002");
        assertThat(config.slippageImpactPerUnit()).isEqualByComparingTo("0.0001");
        assertThat(config.slippageVolatilityMultiplier()).isEqualByComparingTo("2.0");
    }
}
