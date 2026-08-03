package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.application.usecase.query.FindTickerQuery;
import com.quorumtrading.model.enums.AssetType;
import com.quorumtrading.model.enums.ConfidenceLevel;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.ListingType;
import com.quorumtrading.model.enums.TickerResolution;
import com.quorumtrading.model.valueobject.AssetInfo;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.MIC;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.TickerSearchResult;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FindTickerServiceTest {

    @Mock
    private LLMPort llmPort;

    @Mock
    private BrokerPort brokerPort;

    @Mock
    private RecordLLMUsageUseCase recordLLMUsageUseCase;

    private FindTickerService service;

    private static final String DEFAULT_ONLINE_MODEL = "perplexity/sonar-pro:online";
    private static final UserId TEST_USER = new UserId(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new FindTickerService(llmPort, brokerPort, DEFAULT_ONLINE_MODEL, recordLLMUsageUseCase);
        // A Mockito mock answers false to every un-stubbed boolean, including the
        // port's defaulted capability methods — which would make every broker in
        // these tests look like a permissive simulation broker and quietly drain the
        // meaning from each tradability assertion below. The default posture here is
        // a real broker; the one test about simulation brokers overrides it.
        lenient().when(brokerPort.providesInstrumentUniverse()).thenReturn(true);
    }

    /** A model that can actually check the web, and whose negatives are therefore trustworthy. */
    private static LLMModelInfo webCapableModel() {
        return LLMModelInfo.ofWithWebSearch(DEFAULT_ONLINE_MODEL, "Sonar Pro", "Web search model",
            128000, LLMPricing.FREE, List.of("text"), true);
    }

    @Test
    void shouldFindTradableStock() {
        // Given
        String llmResponse = """
            {
              "ticker": "AAPL",
              "companyName": "Apple Inc.",
              "exchange": "NASDAQ",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "reasoning": "Apple Inc. is publicly traded on NASDAQ."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("AAPL"))
            .thenReturn(Optional.of(AssetInfo.tradable("AAPL", "Apple Inc.", "NASDAQ", "us_equity")));

        // When
        TickerSearchResult result = service.execute(new FindTickerQuery("Apple", TEST_USER));

        // Then
        assertThat(result.ticker()).isEqualTo("AAPL");
        assertThat(result.companyName()).isEqualTo("Apple Inc.");
        assertThat(result.exchange()).isEqualTo("NASDAQ");
        assertThat(result.investmentType()).isEqualTo(InvestmentType.STOCK);
        assertThat(result.tradableOnBroker()).isTrue();
        verify(recordLLMUsageUseCase).requireAvailableBudget(TEST_USER);
    }

    @Test
    @DisplayName("a web-capable engine may assert a company is private")
    void shouldReturnNotPubliclyTradedWhenCorroborated() {
        // Given — the engine that answered can actually check, so its negative is trustworthy.
        String llmResponse = """
            {
              "companyName": "Nothing Technology Limited",
              "investmentType": "STOCK",
              "publiclyTraded": false,
              "listings": [],
              "reasoning": "Nothing Technology is a private company."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(llmPort.getModelInfo(DEFAULT_ONLINE_MODEL)).thenReturn(Optional.of(webCapableModel()));

        // When
        TickerSearchResult result = service.execute(new FindTickerQuery("Nothing Phone Company", TEST_USER));

        // Then
        assertThat(result.ticker()).isNull();
        assertThat(result.companyName()).isEqualTo("Nothing Technology Limited");
        assertThat(result.tradableOnBroker()).isFalse();
        assertThat(result.resolution()).isEqualTo(TickerResolution.NOT_LISTED);
        assertThat(result.message()).contains("not publicly traded");
        verifyNoInteractions(brokerPort);
    }

    @Test
    @DisplayName("an engine with no web access may NOT assert a company is private")
    void shouldReportUnverifiedWhenEngineCannotCheck() {
        // Given — the SpaceX case. An engine answering from training data cannot see a
        // listing that happened after its cutoff, so "is not publicly traded" is a claim
        // it is in no position to make. Reporting it as fact is how a user gets told the
        // platform cannot trade a company it holds live prices for.
        String llmResponse = """
            {
              "companyName": "Space Exploration Technologies Corp.",
              "investmentType": "STOCK",
              "publiclyTraded": false,
              "listings": [],
              "reasoning": "SpaceX is a private company and has not conducted an IPO."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        // getModelInfo defaults to Optional.empty() ⇒ no web search capability known.

        // When
        TickerSearchResult result = service.execute(new FindTickerQuery("SpaceX", TEST_USER));

        // Then — an absence of evidence, reported as such and machine-readable.
        assertThat(result.resolution())
            .as("a negative from an engine that could not check is UNVERIFIED, not NOT_LISTED")
            .isEqualTo(TickerResolution.UNVERIFIED);
        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.message())
            .contains("Could not confirm")
            .doesNotContain("is not publicly traded");
        assertThat(result.warnings())
            .as("the user must be pointed at a way to settle it themselves")
            .anySatisfy(w -> assertThat(w).contains("backfill_price_data"));
    }

    @Test
    @DisplayName("the broker overrules an engine that calls a listed company private")
    void brokerContradictsStaleNotListedClaim() {
        // Given — the engine says private but still names a symbol, and the broker lists
        // that symbol as tradable. The broker is authoritative about its own instruments
        // and cannot be stale about them, so it wins.
        String llmResponse = """
            {
              "companyName": "Space Exploration Technologies Corp.",
              "investmentType": "STOCK",
              "publiclyTraded": false,
              "listings": [
                {"ticker": "SPCX", "exchange": "NASDAQ", "currency": "USD", "type": "PRIMARY"}
              ],
              "reasoning": "SpaceX remains private as of my knowledge cutoff."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("SPCX")).thenReturn(Optional.of(AssetInfo.tradable(
            "SPCX", "Space Exploration Technologies Corp.", "NASDAQ", "us_equity")));

        // When
        TickerSearchResult result = service.execute(new FindTickerQuery("SpaceX", TEST_USER));

        // Then
        assertThat(result.resolution()).isEqualTo(TickerResolution.RESOLVED);
        assertThat(result.ticker()).isEqualTo("SPCX");
        assertThat(result.tradableOnBroker()).isTrue();
        assertThat(result.warnings())
            .as("the contradiction must be surfaced, not silently resolved")
            .anySatisfy(w -> assertThat(w).contains("out of date"));
    }

    @Test
    void shouldReturnNotTradableOnBrokerWhenNotFound() {
        // Given
        String llmResponse = """
            {
              "ticker": "LHA",
              "companyName": "Deutsche Lufthansa AG",
              "exchange": "Frankfurt Stock Exchange",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "reasoning": "Lufthansa trades on the Frankfurt Stock Exchange."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("LHA"))
            .thenReturn(Optional.of(AssetInfo.notFound("LHA")));

        // When
        TickerSearchResult result = service.execute(new FindTickerQuery("Lufthansa", TEST_USER));

        // Then
        assertThat(result.ticker()).isEqualTo("LHA");
        assertThat(result.companyName()).isEqualTo("Deutsche Lufthansa AG");
        assertThat(result.tradableOnBroker()).isFalse();
        assertThat(result.message()).contains("not available for trading");
    }

    @Test
    void shouldReturnNotTradableWhenFoundButInactive() {
        // Given
        String llmResponse = """
            {
              "ticker": "DIDI",
              "companyName": "DiDi Global Inc.",
              "exchange": "NYSE",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "reasoning": "DiDi Global was listed on NYSE."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("DIDI"))
            .thenReturn(Optional.of(AssetInfo.notTradable("DIDI", "DiDi Global Inc.", "NYSE", "inactive", "us_equity")));

        // When
        TickerSearchResult result = service.execute(new FindTickerQuery("DiDi", TEST_USER));

        // Then
        assertThat(result.ticker()).isEqualTo("DIDI");
        assertThat(result.tradableOnBroker()).isFalse();
        assertThat(result.message()).contains("not available for trading");
    }

    @Test
    void shouldHandleBrokerUnavailable() {
        // Given
        String llmResponse = """
            {
              "ticker": "AAPL",
              "companyName": "Apple Inc.",
              "exchange": "NASDAQ",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "reasoning": "Apple Inc. is publicly traded."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("AAPL")).thenReturn(Optional.empty());

        // When
        TickerSearchResult result = service.execute(new FindTickerQuery("Apple", TEST_USER));

        // Then
        assertThat(result.ticker()).isEqualTo("AAPL");
        assertThat(result.tradableOnBroker()).isFalse();
        assertThat(result.message()).contains("broker validation unavailable");
    }

    @Test
    void shouldThrowOnLlmFailure() {
        // Given
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenThrow(new RuntimeException("Connection refused"));

        // When/Then
        assertThatThrownBy(() -> service.execute(new FindTickerQuery("Apple", TEST_USER)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("LLM query failed");
    }

    @Test
    @DisplayName("a reply cut off by the token limit is salvaged, not discarded")
    void recoversTruncatedResponse() {
        // Given — the exact failure seen live: asking for every venue makes replies
        // longer, and a small local model's output limit severs the JSON mid-object.
        // The listings that did arrive are complete and usable; failing the whole
        // search would throw away an answer that had already succeeded.
        String truncated = """
            {"companyName": "Airbus SE", "publiclyTraded": true, "investmentType": "STOCK", \
            "listings": [{"ticker": "AIR", "exchange": "Euronext Paris", "mic": "XPAR", \
            "currency": "EUR", "type": "PRIMARY"}, {"tic""";
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(truncated, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("AIR")).thenReturn(Optional.of(
            AssetInfo.tradable("AIR", "AAR Corp", "NYSE", "us_equity")));

        // When
        TickerSearchResult result = service.execute(new FindTickerQuery("Airbus", TEST_USER));

        // Then — the complete prefix was recovered and processed normally, which here
        // means the identity conflict in it was still caught.
        assertThat(result.companyName()).isEqualTo("Airbus SE");
        assertThat(result.resolution()).isEqualTo(TickerResolution.AMBIGUOUS);
        verify(brokerPort).getAssetInfo("AIR");
    }

    @Test
    @DisplayName("truncation before any complete element is still an error")
    void doesNotInventDataFromUnsalvageableResponse() {
        // Repair must recover what arrived, never guess at what did not.
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage("{\"compan", DEFAULT_ONLINE_MODEL));

        assertThatThrownBy(() -> service.execute(new FindTickerQuery("Airbus", TEST_USER)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to parse LLM ticker search response");
    }

    @Test
    void shouldThrowOnUnparseableLlmResponse() {
        // Given
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage("This is not JSON", DEFAULT_ONLINE_MODEL));

        // When/Then
        assertThatThrownBy(() -> service.execute(new FindTickerQuery("Apple", TEST_USER)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to parse LLM ticker search response");
    }

    @Test
    void shouldHandleMarkdownWrappedResponse() {
        // Given
        String llmResponse = """
            ```json
            {
              "ticker": "TSLA",
              "companyName": "Tesla, Inc.",
              "exchange": "NASDAQ",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "reasoning": "Tesla is publicly traded on NASDAQ."
            }
            ```""";
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("TSLA"))
            .thenReturn(Optional.of(AssetInfo.tradable("TSLA", "Tesla, Inc.", "NASDAQ", "us_equity")));

        // When
        TickerSearchResult result = service.execute(new FindTickerQuery("Tesla", TEST_USER));

        // Then
        assertThat(result.ticker()).isEqualTo("TSLA");
        assertThat(result.tradableOnBroker()).isTrue();
    }

    @Test
    void shouldSelectWebSearchModelWhenAvailable() {
        // Given
        LLMModelInfo webModel = LLMModelInfo.ofWithWebSearch(
            "perplexity/sonar-pro", "Sonar Pro", "Web search model",
            128000, LLMPricing.FREE, List.of("text"), true);

        when(llmPort.supportsModelListing()).thenReturn(true);
        when(llmPort.getAvailableModels()).thenReturn(List.of(webModel));

        String llmResponse = """
            {
              "ticker": "AAPL",
              "companyName": "Apple Inc.",
              "exchange": "NASDAQ",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "reasoning": "Apple is publicly traded."
            }
            """;
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, "perplexity/sonar-pro"));
        when(brokerPort.getAssetInfo("AAPL"))
            .thenReturn(Optional.of(AssetInfo.tradable("AAPL", "Apple Inc.", "NASDAQ", "us_equity")));

        // When
        service.execute(new FindTickerQuery("Apple", TEST_USER));

        // Then — verify the web search model was selected
        verify(llmPort).queryWithUsage(anyString(), argThat(config ->
            config.model().equals("perplexity/sonar-pro")));
    }

    @Test
    void shouldFallbackToDefaultModelWhenNoWebSearchModel() {
        // Given
        when(llmPort.supportsModelListing()).thenReturn(true);
        when(llmPort.getAvailableModels()).thenReturn(List.of()); // No web search models

        String llmResponse = """
            {
              "ticker": "AAPL",
              "companyName": "Apple Inc.",
              "exchange": "NASDAQ",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "reasoning": "Apple is publicly traded."
            }
            """;
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("AAPL"))
            .thenReturn(Optional.of(AssetInfo.tradable("AAPL", "Apple Inc.", "NASDAQ", "us_equity")));

        // When
        service.execute(new FindTickerQuery("Apple", TEST_USER));

        // Then — verify the default model was used
        verify(llmPort).queryWithUsage(anyString(), argThat(config ->
            config.model().equals(DEFAULT_ONLINE_MODEL)));
    }

    @Test
    void shouldHandleCryptoTicker() {
        // Given
        String llmResponse = """
            {
              "ticker": "BTC",
              "companyName": "Bitcoin",
              "exchange": null,
              "investmentType": "CRYPTO",
              "publiclyTraded": true,
              "reasoning": "Bitcoin is a cryptocurrency."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("BTC"))
            .thenReturn(Optional.of(AssetInfo.tradable("BTC", "Bitcoin", null, "crypto")));

        // When
        TickerSearchResult result = service.execute(new FindTickerQuery("Bitcoin", TEST_USER));

        // Then
        assertThat(result.ticker()).isEqualTo("BTC");
        assertThat(result.investmentType()).isEqualTo(InvestmentType.CRYPTO);
        assertThat(result.tradableOnBroker()).isTrue();
    }

    @Test
    @DisplayName("a company claimed public but with no symbol is unresolved, not private")
    void shouldHandleBlankTickerAsUnresolved() {
        // Given — the model says the company IS public but produces no symbol. That is a
        // failure to resolve, and reporting it as "not publicly traded" states the
        // opposite of what the model actually said.
        String llmResponse = """
            {
              "ticker": "",
              "companyName": "SpaceX",
              "exchange": null,
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "reasoning": "Could not determine a symbol."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));

        // When
        TickerSearchResult result = service.execute(new FindTickerQuery("SpaceX", TEST_USER));

        // Then
        assertThat(result.ticker()).isNull();
        assertThat(result.tradableOnBroker()).isFalse();
        assertThat(result.resolution()).isEqualTo(TickerResolution.UNVERIFIED);
        assertThat(result.message())
            .contains("Could not resolve a ticker")
            .doesNotContain("not publicly traded");
        verifyNoInteractions(brokerPort);
    }

    // ------------------------------------------------------------------
    // Identity verification: the ticker must belong to the company asked for.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("LVMH must not resolve to Moelis & Company just because both are 'MC'")
    void refusesTickerBelongingToADifferentCompany() {
        // Given — LVMH is quoted "MC" in Paris; "MC" on NYSE is Moelis & Company, an
        // unrelated investment bank. The broker confirms MC is tradable, which is true
        // and beside the point: it is confirming a different company.
        String llmResponse = """
            {
              "companyName": "LVMH Moët Hennessy Louis Vuitton SE",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "listings": [
                {"ticker": "MC", "exchange": "Euronext Paris", "mic": "XPAR", "currency": "EUR", "type": "PRIMARY"}
              ],
              "reasoning": "LVMH trades as MC on Euronext Paris."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("MC")).thenReturn(Optional.of(
            AssetInfo.tradable("MC", "Moelis & Company", "NYSE", "us_equity")));

        // When
        TickerSearchResult result = service.execute(new FindTickerQuery("LVMH", TEST_USER));

        // Then — fail closed. Withholding a symbol is recoverable; trading the wrong
        // company is not.
        assertThat(result.resolution()).isEqualTo(TickerResolution.AMBIGUOUS);
        assertThat(result.tradableOnBroker())
            .as("a tradable flag here would send the user into an unrelated investment bank")
            .isFalse();
        assertThat(result.ticker())
            .as("callers that read only ticker() must not be handed the wrong instrument")
            .isNull();
        assertThat(result.message()).contains("different company");
        assertThat(result.alternatives())
            .as("the rejected candidate is reported so the refusal is auditable")
            .anySatisfy(alt -> {
                assertThat(alt.ticker()).isEqualTo("MC");
                assertThat(alt.note()).contains("Moelis");
            });
    }

    @Test
    @DisplayName("Airbus must not resolve to AAR Corp — the same collision, second instance")
    void refusesSecondIdentityCollision() {
        String llmResponse = """
            {
              "companyName": "Airbus SE",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "listings": [
                {"ticker": "AIR", "exchange": "Euronext Paris", "mic": "XPAR", "currency": "EUR", "type": "PRIMARY"}
              ],
              "reasoning": "Airbus trades as AIR on Euronext Paris."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("AIR")).thenReturn(Optional.of(
            AssetInfo.tradable("AIR", "AAR Corp", "NYSE", "us_equity")));

        TickerSearchResult result = service.execute(new FindTickerQuery("Airbus", TEST_USER));

        assertThat(result.resolution()).isEqualTo(TickerResolution.AMBIGUOUS);
        assertThat(result.ticker()).isNull();
    }

    @Test
    @DisplayName("a broker that reports no company name still resolves, at reduced confidence")
    void resolvesWithoutIdentityCorroboration() {
        // Given — the mock broker behind every backtest exposes no company name. Identity
        // cannot be cross-checked, but that is not evidence of a conflict, and refusing
        // here would break the common path in order to fix the rare one.
        String llmResponse = """
            {
              "companyName": "Apple Inc.",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "listings": [
                {"ticker": "AAPL", "exchange": "NASDAQ", "currency": "USD", "type": "PRIMARY"}
              ],
              "reasoning": "Apple trades on NASDAQ."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("AAPL"))
            .thenReturn(Optional.of(AssetInfo.tradable("AAPL", null, "MOCK", "us_equity")));

        TickerSearchResult result = service.execute(new FindTickerQuery("Apple", TEST_USER));

        assertThat(result.resolution()).isEqualTo(TickerResolution.RESOLVED);
        assertThat(result.tradableOnBroker()).isTrue();
        assertThat(result.confidence())
            .as("tradable but unverified identity is not the same as fully corroborated")
            .isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(result.message()).contains("could not be cross-checked");
    }

    @Test
    @DisplayName("a listing typed CRYPTO overrides an investmentType of STOCK")
    void reconcilesContradictoryInvestmentType() {
        // Given — the model contradicts itself. This is not cosmetic: investmentType
        // becomes backfill_price_data's assetType, and requesting a coin as an equity
        // fetches whichever share collides with the symbol into the same price series.
        String llmResponse = """
            {
              "companyName": "Cardano",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "listings": [{"ticker": "ADA", "type": "CRYPTO"}],
              "reasoning": "Cardano is a cryptocurrency."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("ADA"))
            .thenReturn(Optional.of(AssetInfo.tradable("ADA", "Cardano", null, "crypto")));

        TickerSearchResult result = service.execute(new FindTickerQuery("Cardano", TEST_USER));

        assertThat(result.investmentType())
            .as("the specific listing outranks the broad label, and the caller must not be"
                + " handed a value that corrupts the price series downstream")
            .isEqualTo(InvestmentType.CRYPTO);
        assertThat(result.listingType()).isEqualTo(ListingType.CRYPTO);
    }

    @Test
    @DisplayName("a crypto result reports no equity venue")
    void cryptoReportsNoExchange() {
        // Given — brokers file crypto under an equity venue (Alpaca answers "ARCA",
        // which is NYSE Arca). Passing that through produced "Bitcoin trades on ARCA".
        String llmResponse = """
            {
              "companyName": "Bitcoin",
              "investmentType": "CRYPTO",
              "publiclyTraded": true,
              "listings": [{"ticker": "BTC", "type": "CRYPTO"}],
              "reasoning": "Bitcoin is a cryptocurrency."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("BTC"))
            .thenReturn(Optional.of(AssetInfo.tradable("BTC", "Bitcoin", "ARCA", "crypto")));

        TickerSearchResult result = service.execute(new FindTickerQuery("Bitcoin", TEST_USER));

        assertThat(result.exchange())
            .as("a coin does not list on an equities exchange; no venue beats a wrong one")
            .isNull();
        assertThat(result.message()).doesNotContain("ARCA");
    }

    @Test
    @DisplayName("venue and currency are reported on the instrument you can actually have")
    void populatesVenueAndCurrencyOnTheResolvedListing() {
        // Given — the broker supplies neither, so the resolved listing must. Otherwise
        // the one question this tool exists to answer — what am I buying, in what
        // currency — comes back null on tradable results and populated only on the
        // alternatives you cannot act on.
        String llmResponse = """
            {
              "companyName": "Apple Inc.",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "listings": [
                {"ticker": "AAPL", "exchange": "NASDAQ", "mic": "XNAS", "currency": "USD", "type": "PRIMARY"}
              ],
              "reasoning": "Apple trades on NASDAQ."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("AAPL"))
            .thenReturn(Optional.of(AssetInfo.tradable("AAPL", "Apple Inc.", "NASDAQ", "us_equity")));

        TickerSearchResult result = service.execute(new FindTickerQuery("Apple", TEST_USER));

        assertThat(result.mic()).isEqualTo(new MIC("XNAS"));
        assertThat(result.currency()).isEqualTo(Money.Currency.USD);
        // Broker-specific identity stays absent — it was never resolved against an account.
        assertThat(result.uic()).isNull();
        assertThat(result.assetType()).isNull();
    }

    @Test
    @DisplayName("a broker that rubber-stamps every symbol does not count as confirmation")
    void doesNotPresentMockBrokerAcceptanceAsVerification() {
        // Given — simulation brokers accept anything by design, so "the broker confirmed
        // this is tradable" is a false assurance the user only discovers when a real
        // account rejects the order.
        String llmResponse = """
            {
              "companyName": "Tencent Holdings Limited",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "listings": [
                {"ticker": "TCEHY", "exchange": "OTC", "currency": "USD", "type": "DEPOSITARY_RECEIPT"}
              ],
              "reasoning": "TCEHY is Tencent's US ADR."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("TCEHY")).thenReturn(Optional.of(
            AssetInfo.tradable("TCEHY", null, "MOCK", "us_equity")));
        when(brokerPort.providesInstrumentUniverse()).thenReturn(false);

        TickerSearchResult result = service.execute(new FindTickerQuery("Tencent", TEST_USER));

        assertThat(result.resolution())
            .as("the user can still proceed — this must not block backtesting")
            .isEqualTo(TickerResolution.RESOLVED);
        assertThat(result.confidence())
            .as("but nothing was corroborated, so it cannot be reported as if it had been")
            .isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.warnings())
            .anySatisfy(w -> assertThat(w).contains("not actually verified"));
    }

    @Test
    @DisplayName("crypto skips identity comparison, whose naming is not standardised")
    void cryptoSkipsIdentityCheck() {
        // Given — "Bitcoin" versus a broker's "BTC/USD" share no token and would read as
        // a conflict under equity rules, breaking every crypto resolution.
        String llmResponse = """
            {
              "companyName": "Bitcoin",
              "investmentType": "CRYPTO",
              "publiclyTraded": true,
              "listings": [{"ticker": "BTC", "type": "CRYPTO"}],
              "reasoning": "Bitcoin is a cryptocurrency."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("BTC"))
            .thenReturn(Optional.of(AssetInfo.tradable("BTC", "BTC/USD", null, "crypto")));

        TickerSearchResult result = service.execute(new FindTickerQuery("Bitcoin", TEST_USER));

        assertThat(result.resolution()).isEqualTo(TickerResolution.RESOLVED);
        assertThat(result.investmentType()).isEqualTo(InvestmentType.CRYPTO);
        assertThat(result.listingType()).isEqualTo(ListingType.CRYPTO);
        assertThat(result.message())
            .as("a check that was never meaningful for crypto must not be reported as skipped")
            .doesNotContain("company identity");
    }

    // ------------------------------------------------------------------
    // Crypto tickers collide with listed equities. The broker's equity
    // universe cannot answer a question about a coin.
    // ------------------------------------------------------------------

    private void llmReturnsCrypto(String name, String ticker) {
        String llmResponse = """
            {
              "companyName": "%s",
              "investmentType": "CRYPTO",
              "publiclyTraded": true,
              "listings": [{"ticker": "%s", "type": "CRYPTO"}],
              "reasoning": "%s is a cryptocurrency."
            }
            """.formatted(name, ticker, name);
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
    }

    /**
     * {@code getAssetInfo} resolves a bare string against the broker's US <em>equity</em>
     * universe, so asking it about SOL returns Emeren Group Ltd — a delisted NYSE solar company
     * that happens to own the symbol. Identity comparison is disabled for crypto (crypto naming
     * is not standardised), so nothing notices, and Emeren's "inactive" status was reported to
     * the user as the tradability of Solana.
     *
     * <p>{@code TickerValidationHelper} already diagnosed this exact collision and worked around
     * it by excluding crypto from broker validation entirely. The tool whose whole job is to
     * answer this question was left unfixed.</p>
     */
    @Test
    @DisplayName("a coin does not inherit a colliding equity's untradability")
    void cryptoDoesNotInheritCollidingEquityUntradability() {
        llmReturnsCrypto("Solana", "SOL");
        when(brokerPort.getAssetInfo("SOL")).thenReturn(Optional.of(AssetInfo.notTradable(
            "SOL", "Emeren Group Ltd", "New York Stock Exchange", "inactive", "us_equity")));

        TickerSearchResult result = service.execute(new FindTickerQuery("Solana", TEST_USER));

        assertThat(result.message())
            .as("'status: inactive' describes a solar company, not Solana")
            .doesNotContain("status: inactive");
        assertThat(result.message() + result.ticker())
            .as("the answer must still be about the coin the user asked for")
            .contains("SOL");
    }

    /**
     * The dangerous direction, and the one nobody reported: Interlink Electronics is an active
     * NASDAQ listing that owns LINK, so the equity lookup returns tradable=true and the tool
     * cheerfully told the user their broker trades Chainlink. Nothing distinguished "the broker
     * trades this coin" from "an unrelated equity shares its ticker".
     */
    @Test
    @DisplayName("a coin does not inherit a colliding equity's tradability")
    void cryptoDoesNotInheritCollidingEquityTradability() {
        llmReturnsCrypto("Chainlink", "LINK");
        when(brokerPort.getAssetInfo("LINK")).thenReturn(Optional.of(AssetInfo.tradable(
            "LINK", "Interlink Electronics, Inc.", "NASDAQ", "us_equity")));

        TickerSearchResult result = service.execute(new FindTickerQuery("Chainlink", TEST_USER));

        assertThat(result.tradableOnBroker())
            .as("an active equity that shares the ticker is not the broker trading the coin")
            .isFalse();
        assertThat(result.message())
            .as("the user needs to know why this is not a live-trading green light")
            .containsAnyOf("Interlink", "equity", "not a crypto");
    }

    /**
     * The fix must not break the case it is protecting: when the broker really does return a
     * crypto record, that answer is about the coin and stands.
     */
    @Test
    @DisplayName("a genuine crypto record from the broker still resolves as tradable")
    void genuineCryptoRecordStillResolves() {
        llmReturnsCrypto("Bitcoin", "BTC");
        when(brokerPort.getAssetInfo("BTC"))
            .thenReturn(Optional.of(AssetInfo.tradable("BTC", "BTC/USD", null, "crypto")));

        TickerSearchResult result = service.execute(new FindTickerQuery("Bitcoin", TEST_USER));

        assertThat(result.resolution()).isEqualTo(TickerResolution.RESOLVED);
        assertThat(result.tradableOnBroker()).isTrue();
    }

    // ------------------------------------------------------------------
    // Foreign listings: tradable, but say what is actually being bought.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Toyota resolves to its NYSE ADR, and says so")
    void labelsDepositaryReceiptAndNamesTheHomeListing() {
        // Given — Toyota's home listing is 7203 in Tokyo, which this platform's symbol
        // grammar cannot express, and TM on NYSE, which it can. Returning TM silently
        // would leave the user believing they hold the Tokyo line in yen.
        String llmResponse = """
            {
              "companyName": "Toyota Motor Corporation",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "listings": [
                {"ticker": "7203", "exchange": "Tokyo Stock Exchange", "mic": "XTKS", "currency": "JPY", "type": "PRIMARY"},
                {"ticker": "TM", "exchange": "NYSE", "mic": "XNYS", "currency": "USD", "type": "DEPOSITARY_RECEIPT"}
              ],
              "reasoning": "TM is Toyota's NYSE-listed ADR."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("TM")).thenReturn(Optional.of(
            AssetInfo.tradable("TM", "Toyota Motor Corp", "NYSE", "us_equity")));

        // When
        TickerSearchResult result = service.execute(new FindTickerQuery("Toyota", TEST_USER));

        // Then
        assertThat(result.resolution()).isEqualTo(TickerResolution.RESOLVED);
        assertThat(result.ticker()).isEqualTo("TM");
        assertThat(result.listingType()).isEqualTo(ListingType.DEPOSITARY_RECEIPT);
        assertThat(result.message())
            .as("the user must learn this is a proxy carrying FX exposure, not the Tokyo line")
            .contains("depositary receipt")
            .contains("7203")
            .contains("foreign-exchange");

        // The unusable home listing is reported rather than hidden — "Toyota's primary
        // listing is 7203, which this platform cannot express" is useful and true.
        assertThat(result.alternatives()).anySatisfy(alt -> {
            assertThat(alt.ticker()).isEqualTo("7203");
            assertThat(alt.symbolSupported()).isFalse();
            assertThat(alt.tradableHere())
                .as("a ticker the grammar cannot express is settled as untradable without"
                    + " asking the broker, and must say so in the field a consumer filters on")
                .isFalse();
            assertThat(alt.note()).contains("cannot yet express");
        });

        // A ticker the platform's own grammar rejects is never probed or offered.
        verify(brokerPort, never()).getAssetInfo("7203");
    }

    @Test
    @DisplayName("an untradable home listing falls through to the receipt behind it")
    void fallsThroughToTheListingTheBrokerCanReach() {
        // Given — the European primary line is not in a US broker's universe, but the
        // company's ADR is. Trying only the first candidate would strand the user.
        String llmResponse = """
            {
              "companyName": "Rheinmetall AG",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "listings": [
                {"ticker": "RHM", "exchange": "Xetra", "mic": "XETR", "currency": "EUR", "type": "PRIMARY"},
                {"ticker": "RNMBY", "exchange": "OTC", "currency": "USD", "type": "DEPOSITARY_RECEIPT"}
              ],
              "reasoning": "Rheinmetall trades on Xetra; RNMBY is its US ADR."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("RHM")).thenReturn(Optional.of(AssetInfo.notFound("RHM")));
        when(brokerPort.getAssetInfo("RNMBY")).thenReturn(Optional.of(
            AssetInfo.tradable("RNMBY", "Rheinmetall AG", "OTC", "us_equity")));

        TickerSearchResult result = service.execute(new FindTickerQuery("Rheinmetall", TEST_USER));

        assertThat(result.resolution()).isEqualTo(TickerResolution.RESOLVED);
        assertThat(result.ticker()).isEqualTo("RNMBY");
        assertThat(result.listingType()).isEqualTo(ListingType.DEPOSITARY_RECEIPT);
        assertThat(result.message()).contains("depositary receipt");
        verify(brokerPort).getAssetInfo("RHM");
        verify(brokerPort).getAssetInfo("RNMBY");
    }

    @Test
    @DisplayName("a receipt is not labelled with its home venue's MIC and currency")
    void doesNotBorrowHomeVenueMetadataForAForeignListing() {
        // Given — the model describes only the London line; the broker trades AZN in
        // New York. Falling back to the candidate's venue data here would stamp a
        // NYSE-traded receipt as XLON/GBP, which is a more specific falsehood than the
        // null it replaced: the user reads it as confirmation they hold the London share.
        String llmResponse = """
            {
              "companyName": "AstraZeneca PLC",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "listings": [
                {"ticker": "AZN", "exchange": "London Stock Exchange", "mic": "XLON", "currency": "EUR", "type": "PRIMARY"}
              ],
              "reasoning": "AstraZeneca's primary listing is in London."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("AZN")).thenReturn(Optional.of(
            AssetInfo.tradable("AZN", "AstraZeneca PLC", "NYSE", "us_equity")));

        TickerSearchResult result = service.execute(new FindTickerQuery("AstraZeneca", TEST_USER));

        assertThat(result.listingType()).isEqualTo(ListingType.DEPOSITARY_RECEIPT);
        assertThat(result.exchange()).isEqualTo("NYSE");
        assertThat(result.mic())
            .as("the traded instrument is in New York; tagging it XLON would misdescribe it")
            .isNull();
        assertThat(result.currency()).isNull();
    }

    @Test
    @DisplayName("an untradable crypto still reports CRYPTO, not STOCK")
    void keepsCryptoTypeOnTheUnresolvedPath() {
        // Given — the coin is not in this broker's universe. The result still feeds
        // backfill_price_data's assetType, so a hardcoded STOCK on this path reintroduces
        // exactly the corruption the reconcile exists to prevent.
        String llmResponse = """
            {
              "companyName": "Cardano",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "listings": [
                {"ticker": "ADA", "exchange": "Cryptocurrency Exchange (aggregate)", "type": "CRYPTO"}
              ],
              "reasoning": "Cardano is a cryptocurrency."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("ADA")).thenReturn(Optional.of(AssetInfo.notFound("ADA")));

        TickerSearchResult result = service.execute(new FindTickerQuery("Cardano", TEST_USER));

        assertThat(result.resolution()).isEqualTo(TickerResolution.TRADABLE_ELSEWHERE);
        assertThat(result.investmentType())
            .as("the unresolved paths must carry the reconciled type, not a hardcoded STOCK")
            .isEqualTo(InvestmentType.CRYPTO);
    }

    @Test
    @DisplayName("a home-only listing this broker cannot reach blames the broker, not the market")
    void reportsTradableElsewhereWhenNoRouteExists() {
        String llmResponse = """
            {
              "companyName": "Deutsche Lufthansa AG",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "listings": [
                {"ticker": "LHA", "exchange": "Frankfurt Stock Exchange", "mic": "XETR", "currency": "EUR", "type": "PRIMARY"}
              ],
              "reasoning": "Lufthansa trades on the Frankfurt Stock Exchange."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("LHA")).thenReturn(Optional.of(AssetInfo.notFound("LHA")));

        TickerSearchResult result = service.execute(new FindTickerQuery("Lufthansa", TEST_USER));

        assertThat(result.resolution()).isEqualTo(TickerResolution.TRADABLE_ELSEWHERE);
        assertThat(result.tradableOnBroker()).isFalse();
        assertThat(result.message()).contains("not available for trading");
    }

    @Test
    void shouldValidateAgainstAccountBrokerWhenAccountPresent() {
        // Given — a German equity resolved by an LLM, validated against a specific (Saxo) account.
        // The account-aware broker returns an exchange-qualified AssetInfo (mic=XETR, EUR, uic).
        BrokerAccountId saxoAccount = BrokerAccountId.generate();
        String llmResponse = """
            {
              "ticker": "RHM",
              "companyName": "Rheinmetall AG",
              "exchange": "Frankfurt Stock Exchange",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "reasoning": "Rheinmetall trades on Xetra."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        AssetInfo saxoInfo = new AssetInfo(
            "RHM", "Rheinmetall AG", "XETR", true, "active", "equity",
            new MIC("XETR"), Money.Currency.EUR, 12345L, AssetType.STOCK);
        when(brokerPort.getAssetInfo("RHM", saxoAccount)).thenReturn(Optional.of(saxoInfo));

        // When
        TickerSearchResult result = service.execute(
            new FindTickerQuery("Rheinmetall", TEST_USER, saxoAccount));

        // Then — exchange-qualified, tradable, venue/currency/uic propagated.
        assertThat(result.ticker()).isEqualTo("RHM");
        assertThat(result.companyName()).isEqualTo("Rheinmetall AG");
        assertThat(result.exchange()).isEqualTo("XETR");
        assertThat(result.tradableOnBroker()).isTrue();
        assertThat(result.mic()).isEqualTo(new MIC("XETR"));
        assertThat(result.currency()).isEqualTo(Money.Currency.EUR);
        assertThat(result.uic()).isEqualTo(12345L);
        assertThat(result.assetType()).isEqualTo(AssetType.STOCK);
        // The account-aware overload was used — NOT the global-default one.
        verify(brokerPort).getAssetInfo("RHM", saxoAccount);
        verify(brokerPort, never()).getAssetInfo(anyString());
    }

    @Test
    void shouldUseGlobalDefaultBrokerWhenNoAccountPresent() {
        // Given — same flow with no account: must be byte-identical to today's behavior.
        String llmResponse = """
            {
              "ticker": "AAPL",
              "companyName": "Apple Inc.",
              "exchange": "NASDAQ",
              "investmentType": "STOCK",
              "publiclyTraded": true,
              "reasoning": "Apple Inc. is publicly traded on NASDAQ."
            }
            """;
        when(llmPort.supportsModelListing()).thenReturn(false);
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(llmResponse, DEFAULT_ONLINE_MODEL));
        when(brokerPort.getAssetInfo("AAPL"))
            .thenReturn(Optional.of(AssetInfo.tradable("AAPL", "Apple Inc.", "NASDAQ", "us_equity")));

        // When — query with explicit null account (global default).
        TickerSearchResult result = service.execute(
            new FindTickerQuery("Apple", TEST_USER, null));

        // Then — global-default result, account-aware overload untouched. Venue fields
        // stay null because neither the broker nor this (flat-shaped) reply carried
        // any; they are populated when a source actually supplies them.
        assertThat(result.ticker()).isEqualTo("AAPL");
        assertThat(result.tradableOnBroker()).isTrue();
        assertThat(result.exchange()).isEqualTo("NASDAQ");
        assertThat(result.mic()).isNull();
        assertThat(result.currency()).isNull();
        assertThat(result.uic()).isNull();
        assertThat(result.assetType()).isNull();
        verify(brokerPort).getAssetInfo("AAPL");
        verify(brokerPort, never()).getAssetInfo(anyString(), any(BrokerAccountId.class));
    }
}
