package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.application.port.out.MarketDataPortDiscovery;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.usecase.command.BackFillTickerPriceDataCommand;
import com.quorumtrading.application.usecase.BackFillResult;
import com.quorumtrading.model.enums.Granularity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * What a failed backfill tells the user.
 *
 * <p>The message for a stock that returned no bars was unconditional: *"Stocks need a market-data
 * provider — set ALPACA_API_KEY/ALPACA_API_SECRET or TIINGO_API_TOKEN and restart."* It fired even
 * when three providers were configured and serving other tickers that same second, so a user
 * bringing a ticker those providers do not cover — a European listing, say — was sent to re-check
 * their .env and restart their stack for a problem that had nothing to do with credentials.</p>
 *
 * <p>The service already knows which it is: {@code adapters} is the list of providers that were
 * actually discovered and tried. If it is non-empty, credentials are not the story.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BackFillTickerPriceDataService — failure messages")
class BackFillTickerPriceDataServiceTest {

    @Mock private MarketDataPortDiscovery marketDataPortDiscovery;
    @Mock private MarketEventRepository marketEventRepository;
    @Mock private MarketDataPort configuredProvider;
    @Mock private MarketDataPort quietProvider;

    private BackFillTickerPriceDataService service;

    @BeforeEach
    void setUp() {
        service = new BackFillTickerPriceDataService(marketDataPortDiscovery, marketEventRepository);
        // Mockito does not run default interface methods, so isConfigured() would answer false
        // for every mock and route each of these tests down the missing-credentials path. These
        // tests are all about a CONFIGURED provider that returned nothing; the keyless case has
        // its own test below and stubs it false explicitly.
        lenient().when(configuredProvider.isConfigured()).thenReturn(true);
        lenient().when(quietProvider.isConfigured()).thenReturn(true);
    }

    private BackFillTickerPriceDataCommand stockCommand(String ticker) {
        return new BackFillTickerPriceDataCommand(
                ticker, "STOCK",
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-05T00:00:00Z"),
                Granularity.ONE_DAY);
    }

    /** A provider that is configured and reachable but has nothing for this symbol. */
    private void providerConfiguredButReturnsNothing() {
        lenient().when(configuredProvider.getProviderName()).thenReturn("alpaca");
        lenient().when(configuredProvider.fetchDataInRange(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(marketDataPortDiscovery.findAllByFeatures(any(), any(), any()))
                .thenReturn(List.of(configuredProvider));
    }

    @Test
    @DisplayName("does not blame missing API keys when a provider was configured and tried")
    void shouldNotBlameCredentialsWhenProvidersAreConfigured() {
        providerConfiguredButReturnsNothing();

        BackFillResult result = service.execute(stockCommand("RHM"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage())
                .as("telling a user to set a key they have already set sends them to re-check "
                    + "their .env and restart for a problem that is not theirs")
                .doesNotContain("ALPACA_API_KEY")
                .doesNotContain("TIINGO_API_TOKEN")
                .doesNotContain("restart");
    }

    @Test
    @DisplayName("names the providers that were tried, so the user can see it was not a key problem")
    void shouldNameTheProvidersItTried() {
        providerConfiguredButReturnsNothing();

        BackFillResult result = service.execute(stockCommand("RHM"));

        assertThat(result.errorMessage())
                .contains("alpaca")
                .contains("RHM");
    }

    @Test
    @DisplayName("points at coverage — the actual cause — rather than at credentials")
    void shouldPointAtCoverage() {
        providerConfiguredButReturnsNothing();

        BackFillResult result = service.execute(stockCommand("RHM"));

        assertThat(result.errorMessage().toLowerCase())
                .as("the three causes a user must tell apart are: the symbol does not exist, the "
                    + "market is not covered, and the data is simply not loaded yet")
                .containsAnyOf("cover", "exchange", "not available from");
    }

    @Test
    @DisplayName("no adapter supports the requested features → says so, and does not blame the ticker")
    void shouldReportWhenNoAdapterSupportsTheFeatures() {
        // This test used to claim, in its own as() clause, that it checked the credentials advice
        // — while asserting only isNotBlank(). It therefore passed for any message whatsoever,
        // including the one that never mentions credentials, and read as coverage on the exact
        // path a zero-credential install walks. The keyless case is the test below; this branch
        // is the different, rarer one where no adapter claims the feature at all.
        when(marketDataPortDiscovery.findAllByFeatures(any(), any(), any()))
                .thenReturn(List.of());

        BackFillResult result = service.execute(stockCommand("AAPL"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage())
                .as("no adapter claimed the feature, so the message must be about the platform's "
                    + "capability and must not send the user off to change their ticker")
                .contains("No market data adapter found")
                .doesNotContain("find_ticker");
    }

    @Test
    @DisplayName("providers discovered but none holding credentials → names the credentials, not the ticker")
    void keylessInstanceNamesCredentialsRatherThanBlamingCoverage() {
        // The cold-start blocker, and the one no existing test in this class could have caught:
        // discovery selects on hasFeature(), which never reads configuration, so a key-requiring
        // adapter with no key is still discovered, still tried, and returns an empty list without
        // erroring. `adapters` was therefore non-empty and the message told a fresh self-hoster
        // that AAPL was "most likely not covered" by providers that were never configured, and to
        // go and pick a different ticker. Every clause of that was false.
        lenient().when(configuredProvider.getProviderName()).thenReturn("alpaca");
        lenient().when(configuredProvider.isConfigured()).thenReturn(false);
        lenient().when(configuredProvider.fetchDataInRange(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(marketDataPortDiscovery.findAllByFeatures(any(), any(), any()))
                .thenReturn(List.of(configuredProvider));

        BackFillResult result = service.execute(stockCommand("AAPL"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage())
                .as("the one actionable fact is that no market-data credential is set")
                .contains("no market-data credentials are configured")
                .contains("alpaca")
                .contains("restart the backend")
                // Must NOT repeat any of the coverage-blaming advice.
                .doesNotContain("not covered")
                .doesNotContain("find_ticker");
        assertThat(result.errorMessage())
                .as("crypto genuinely needs no key, and that is the thing a blocked newcomer can "
                    + "act on immediately")
                .contains("Crypto needs no key");
    }

    // ---------- a provider that throws must not replace the diagnosis ----------

    private BackFillTickerPriceDataCommand cryptoCommand(String ticker) {
        return new BackFillTickerPriceDataCommand(
                ticker, "CRYPTO",
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-05T00:00:00Z"),
                Granularity.ONE_DAY);
    }

    /** Kraken and Coinbase have nothing; Alpaca rejects the symbol outright. */
    private void oneProviderThrowsTheRestReturnNothing(Exception thrown) {
        lenient().when(quietProvider.getProviderName()).thenReturn("kraken");
        lenient().when(quietProvider.fetchDataInRange(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(configuredProvider.getProviderName()).thenReturn("alpaca");
        lenient().when(configuredProvider.fetchDataInRange(any(), any(), any(), any(), any()))
                .thenThrow(thrown);
        when(marketDataPortDiscovery.findAllByFeatures(any(), any(), any()))
                .thenReturn(List.of(quietProvider, configuredProvider));
    }

    /** The literal shape of an Alpaca rejection, FQCN and all. */
    private static RuntimeException alpacaRestClientFailure() {
        return new RuntimeException(
                "alpaca returned HTTP 400, status code 400 when invoking REST Client method: "
                + "'com.quorumtrading.adapter.marketdata.alpaca.client."
                + "AlpacaMarketDataClient#getCryptoBars'");
    }

    /**
     * The service composes a genuinely good three-provider diagnosis, then discards it whenever
     * any provider happened to throw: {@code if (events.isEmpty() && lastError != null) throw
     * lastError} short-circuits to the outer catch, which returns the raw exception text. So the
     * same user mistake produced wildly different feedback depending on which provider objected
     * — BTCUSD got the diagnosis, BTC-USD got a Java class name.
     */
    @Test
    @DisplayName("a throwing provider does not replace the multi-provider diagnosis")
    void throwingProviderDoesNotSuppressTheDiagnosis() {
        oneProviderThrowsTheRestReturnNothing(alpacaRestClientFailure());

        BackFillResult result = service.execute(cryptoCommand("FAKECOIN"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage())
                .as("every provider that was tried must still be named")
                .contains("kraken")
                .contains("alpaca")
                .contains("FAKECOIN");
    }

    @Test
    @DisplayName("never leaks a Java class or method name to the user")
    void neverLeaksInternalTypeNames() {
        oneProviderThrowsTheRestReturnNothing(alpacaRestClientFailure());

        BackFillResult result = service.execute(cryptoCommand("FAKECOIN"));

        assertThat(result.errorMessage())
                .as("a REST-client FQCN tells the user nothing they can act on")
                .doesNotContain("com.quorumtrading")
                .doesNotContain("#getCryptoBars")
                .doesNotContain("REST Client method");
    }

    /**
     * A provider outage is not the same finding as an uncovered symbol, and the user's next move
     * differs (retry vs. pick another ticker). Having kept the diagnosis, say which happened.
     */
    @Test
    @DisplayName("says that a provider errored, without the stack detail")
    void reportsThatAProviderErrored() {
        oneProviderThrowsTheRestReturnNothing(alpacaRestClientFailure());

        BackFillResult result = service.execute(cryptoCommand("FAKECOIN"));

        assertThat(result.errorMessage().toLowerCase())
                .as("'nobody carries it' and 'one provider fell over' need different next steps")
                .containsAnyOf("errored", "error", "rejected", "failed");
    }

    // ---------- form problems are settled before any provider is called ----------

    /**
     * Two spellings of one mistake must not produce two unrelated experiences. This used to be a
     * statement about the failure message composed after three providers had been asked; both
     * spellings are now refused up front by the shared ticker-form check, which is the same
     * guarantee arrived at earlier and without the outbound requests.
     */
    @Test
    @DisplayName("both spellings of a pair form are refused identically, before any provider is asked")
    void pairFormsAreRefusedBeforeDispatch() {
        String hyphenated = service.execute(cryptoCommand("BTC-USD")).errorMessage();
        String concatenated = service.execute(cryptoCommand("BTCUSD")).errorMessage();

        assertThat(hyphenated).contains("trading pair").contains("\"BTC\"");
        assertThat(concatenated).contains("trading pair").contains("\"BTC\"");
        org.mockito.Mockito.verifyNoInteractions(marketDataPortDiscovery);
    }

    /**
     * The crypto hint was unconditional boilerplate: FAKECOIN — correctly written, bare, no quote
     * currency — was told "write BTC, not BTC-USD or BTCUSD", advice about a separator it does not
     * have, when the real cause is that the asset does not exist.
     */
    @Test
    @DisplayName("a correctly-written unknown coin is not told to remove a separator it does not have")
    void doesNotGivePairAdviceToABareTicker() {
        oneProviderThrowsTheRestReturnNothing(alpacaRestClientFailure());

        BackFillResult result = service.execute(cryptoCommand("FAKECOIN"));

        assertThat(result.errorMessage())
                .as("the ticker has no separator, so separator advice is noise")
                .doesNotContain("not BTC-USD")
                .doesNotContain("BTCUSD");
    }

    /**
     * ASML.AS (a real company on an unsupported venue) and ZZZQQQ (nothing at all) produced
     * identical text, while the crypto path got a form hint. The stock path now says which forms
     * this platform can serve.
     */
    @Test
    @DisplayName("a failed stock backfill says which ticker forms are supported")
    void explainsStockTickerFormat() {
        providerConfiguredButReturnsNothing();

        BackFillResult result = service.execute(stockCommand("ASML.AS"));

        assertThat(result.errorMessage())
                .as("the guide forbids AAPL.US and ASML:XAMS but the error never said so")
                .contains("AAPL")
                .contains("get_market_data_status");
    }

    @Test
    @DisplayName("a provider that is discovered but unconfigured is never called")
    void unconfiguredProvidersAreNotCalled() {
        lenient().when(configuredProvider.getProviderName()).thenReturn("alpaca");
        lenient().when(configuredProvider.isConfigured()).thenReturn(false);
        when(marketDataPortDiscovery.findAllByFeatures(any(), any(), any()))
                .thenReturn(List.of(configuredProvider));

        service.execute(stockCommand("AAPL"));

        org.mockito.Mockito.verify(configuredProvider, org.mockito.Mockito.never())
                .fetchDataInRange(any(), any(), any(), any(), any());
    }
}
