package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.AssetType;
import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TickerSearchResultTest {

    @Test
    void shouldCreateFoundResult() {
        TickerSearchResult result = TickerSearchResult.found("AAPL", "Apple Inc.", "NASDAQ", InvestmentType.STOCK);

        assertThat(result.ticker()).isEqualTo("AAPL");
        assertThat(result.companyName()).isEqualTo("Apple Inc.");
        assertThat(result.exchange()).isEqualTo("NASDAQ");
        assertThat(result.investmentType()).isEqualTo(InvestmentType.STOCK);
        assertThat(result.tradableOnBroker()).isTrue();
        assertThat(result.message()).contains("AAPL").contains("NASDAQ").contains("available for trading");
    }

    @Test
    void shouldDefaultAdditiveInstrumentFieldsToNull() {
        TickerSearchResult result = TickerSearchResult.found("AAPL", "Apple Inc.", "NASDAQ", InvestmentType.STOCK);

        assertThat(result.mic()).isNull();
        assertThat(result.currency()).isNull();
        assertThat(result.uic()).isNull();
        assertThat(result.assetType()).isNull();
    }

    @Test
    void shouldCreateNotPubliclyTradedResult() {
        TickerSearchResult result = TickerSearchResult.notPubliclyTraded("SpaceX", "SpaceX is privately held.");

        assertThat(result.ticker()).isNull();
        assertThat(result.companyName()).isEqualTo("SpaceX");
        assertThat(result.exchange()).isNull();
        assertThat(result.investmentType()).isNull();
        assertThat(result.tradableOnBroker()).isFalse();
        assertThat(result.message()).contains("not publicly traded").contains("SpaceX is privately held.");
    }

    @Test
    void shouldCreateNotTradableOnBrokerResult() {
        TickerSearchResult result = TickerSearchResult.notTradableOnBroker(
            "LHA", "Deutsche Lufthansa AG", "Frankfurt Stock Exchange",
            "Only available on European exchanges.");

        assertThat(result.ticker()).isEqualTo("LHA");
        assertThat(result.companyName()).isEqualTo("Deutsche Lufthansa AG");
        assertThat(result.exchange()).isEqualTo("Frankfurt Stock Exchange");
        assertThat(result.investmentType()).isEqualTo(InvestmentType.STOCK);
        assertThat(result.tradableOnBroker()).isFalse();
        assertThat(result.message()).contains("not available for trading");
    }

    @Test
    void shouldHandleNullExchangeInNotTradableResult() {
        TickerSearchResult result = TickerSearchResult.notTradableOnBroker(
            "XYZ", "Some Corp", null, "Not found.");

        assertThat(result.message()).contains("unknown exchange");
    }

    @Test
    void shouldCreateFoundCryptoResult() {
        TickerSearchResult result = TickerSearchResult.found("BTC", "Bitcoin", null, InvestmentType.CRYPTO);

        assertThat(result.ticker()).isEqualTo("BTC");
        assertThat(result.investmentType()).isEqualTo(InvestmentType.CRYPTO);
        assertThat(result.tradableOnBroker()).isTrue();
    }

    @Test
    void shouldPreserveAdditiveFieldsThroughWithDividendInfo() {
        // result carrying enriched EU instrument fields
        TickerSearchResult eu = new TickerSearchResult(
            "RHM", "Rheinmetall AG", "XETR", InvestmentType.STOCK, true,
            "Rheinmetall trades on XETR.", null,
            new MIC("XETR"), Money.Currency.EUR, 12345L, AssetType.STOCK);

        TickerSearchResult withDiv = eu.withDividendInfo(null);

        assertThat(withDiv.mic()).isEqualTo(new MIC("XETR"));
        assertThat(withDiv.currency()).isEqualTo(Money.Currency.EUR);
        assertThat(withDiv.uic()).isEqualTo(12345L);
        assertThat(withDiv.assetType()).isEqualTo(AssetType.STOCK);
    }
}
