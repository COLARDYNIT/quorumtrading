package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AssetInfoTest {

    @Test
    void shouldCreateTradableAsset() {
        AssetInfo info = AssetInfo.tradable("AAPL", "Apple Inc.", "NASDAQ", "us_equity");

        assertThat(info.ticker()).isEqualTo("AAPL");
        assertThat(info.name()).isEqualTo("Apple Inc.");
        assertThat(info.exchange()).isEqualTo("NASDAQ");
        assertThat(info.tradable()).isTrue();
        assertThat(info.status()).isEqualTo("active");
        assertThat(info.assetClass()).isEqualTo("us_equity");
        assertThat(info.isFound()).isTrue();
    }

    @Test
    void shouldCreateNotFoundAsset() {
        AssetInfo info = AssetInfo.notFound("XYZFAKE");

        assertThat(info.ticker()).isEqualTo("XYZFAKE");
        assertThat(info.name()).isNull();
        assertThat(info.exchange()).isNull();
        assertThat(info.tradable()).isFalse();
        assertThat(info.status()).isEqualTo("not_found");
        assertThat(info.assetClass()).isNull();
        assertThat(info.isFound()).isFalse();
    }

    @Test
    void shouldCreateNotTradableAsset() {
        AssetInfo info = AssetInfo.notTradable("DIDI", "DiDi Global", "NYSE", "inactive", "us_equity");

        assertThat(info.ticker()).isEqualTo("DIDI");
        assertThat(info.name()).isEqualTo("DiDi Global");
        assertThat(info.exchange()).isEqualTo("NYSE");
        assertThat(info.tradable()).isFalse();
        assertThat(info.status()).isEqualTo("inactive");
        assertThat(info.assetClass()).isEqualTo("us_equity");
        assertThat(info.isFound()).isTrue();
    }
}
