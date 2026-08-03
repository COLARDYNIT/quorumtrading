package com.quorumtrading.adapter.marketdata.saxo.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quorumtrading.adapter.marketdata.saxo.client.dto.ChartsResponse;
import com.quorumtrading.adapter.marketdata.saxo.client.dto.InfoPricesResponse;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WireMock-backed test for the Saxo OpenAPI market-data REST client (MD1).
 *
 * <p>Proves the two things the WP requires, through the real Quarkus rest-client + Jackson
 * machinery:</p>
 * <ol>
 *   <li>the per-account OAuth <b>{@code Authorization: Bearer <token>}</b> header is sent on
 *       every request — produced by the real {@link SaxoAuthHeadersFactory}, which resolves
 *       the bearer from the {@link com.quorumtrading.application.port.out.BrokerTokenProviderPort}
 *       (stubbed here by {@link TestTokenProviderProducer}); and</li>
 *   <li>both {@link InfoPricesResponse} and {@link ChartsResponse} deserialize from
 *       representative Saxo JSON, including the nested {@code Quote}/{@code PriceInfo} and
 *       {@code Data}/{@code ChartInfo} blocks, tolerating unknown Saxo fields.</li>
 * </ol>
 *
 * <p>{@link SaxoOpenApiWireMockResource} starts WireMock and points the {@code saxo-openapi}
 * rest client at it (the first WireMock harness in the reactor — plan §6
 * {@code V1-wiremock-harness} / {@code MD1}).</p>
 */
@QuarkusTest
@QuarkusTestResource(value = SaxoOpenApiWireMockResource.class, restrictToAnnotatedClass = true)
class SaxoOpenApiClientWireMockTest {

    private static final String EXPECTED_BEARER = "Bearer " + TestTokenProviderProducer.TEST_ACCESS_TOKEN;

    @Inject
    @RestClient
    SaxoOpenApiClient client;

    @InjectWireMock
    WireMockServer wireMock;

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void infoPricesRequestSendsBearerAndDeserializes() {
        wireMock.stubFor(get(urlPathEqualTo("/trade/v1/infoprices"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "Uic": 211,
                                  "AssetType": "Stock",
                                  "LastUpdated": "2026-06-04T09:30:00Z",
                                  "Quote": {
                                    "Bid": 615.40,
                                    "Ask": 615.80,
                                    "Mid": 615.60,
                                    "DelayedByMinutes": 15,
                                    "MarketState": "Open",
                                    "PriceTypeBid": "Indicative",
                                    "PriceTypeAsk": "Indicative"
                                  },
                                  "PriceInfo": {
                                    "High": 620.00,
                                    "Low": 610.10
                                  },
                                  "PriceInfoDetails": {
                                    "Open": 612.00,
                                    "Volume": 1234567,
                                    "LastTraded": 615.50,
                                    "LastClose": 611.20
                                  },
                                  "SomeFutureSaxoField": "ignored"
                                }
                                """)));

        InfoPricesResponse response =
                client.getInfoPrices(211L, "Stock", "Quote,PriceInfoDetails");

        // Response deserialized (top-level + nested blocks)
        assertThat(response.uic()).isEqualTo(211L);
        assertThat(response.assetType()).isEqualTo("Stock");
        assertThat(response.lastUpdated()).isEqualTo("2026-06-04T09:30:00Z");
        assertThat(response.quote()).isNotNull();
        assertThat(response.quote().mid()).isEqualByComparingTo(new BigDecimal("615.60"));
        assertThat(response.quote().delayedByMinutes()).isEqualTo(15);
        assertThat(response.quote().marketState()).isEqualTo("Open");
        assertThat(response.priceInfo()).isNotNull();
        assertThat(response.priceInfo().high()).isEqualByComparingTo(new BigDecimal("620.00"));
        assertThat(response.priceInfo().low()).isEqualByComparingTo(new BigDecimal("610.10"));
        assertThat(response.priceInfoDetails()).isNotNull();
        assertThat(response.priceInfoDetails().open()).isEqualByComparingTo(new BigDecimal("612.00"));
        assertThat(response.priceInfoDetails().lastTraded()).isEqualByComparingTo(new BigDecimal("615.50"));

        // Bearer header sent
        wireMock.verify(getRequestedFor(urlPathEqualTo("/trade/v1/infoprices"))
                .withHeader("Authorization", equalTo(EXPECTED_BEARER)));
    }

    @Test
    void chartsRequestSendsBearerAndDeserializes() {
        wireMock.stubFor(get(urlPathEqualTo("/chart/v1/charts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "Data": [
                                    {
                                      "Time": "2026-06-02T00:00:00Z",
                                      "Open": 610.00,
                                      "High": 618.00,
                                      "Low": 608.50,
                                      "Close": 615.50,
                                      "Volume": 123456
                                    },
                                    {
                                      "Time": "2026-06-03T00:00:00Z",
                                      "Open": 615.50,
                                      "High": 622.00,
                                      "Low": 614.00,
                                      "Close": 620.10,
                                      "Volume": 98765
                                    }
                                  ],
                                  "ChartInfo": {
                                    "FirstSampleTime": "2010-01-04T00:00:00Z",
                                    "Horizon": 1440
                                  },
                                  "DataVersion": 9876
                                }
                                """)));

        ChartsResponse response =
                client.getCharts(211L, "Stock", 1440, 1200, "UpTo", "2026-06-04T00:00:00Z");

        // Response deserialized (sample list + chart-info block)
        assertThat(response.data()).hasSize(2);
        ChartsResponse.Sample first = response.data().get(0);
        assertThat(first.time()).isEqualTo("2026-06-02T00:00:00Z");
        assertThat(first.open()).isEqualByComparingTo(new BigDecimal("610.00"));
        assertThat(first.close()).isEqualByComparingTo(new BigDecimal("615.50"));
        assertThat(first.volume()).isEqualByComparingTo(new BigDecimal("123456"));
        assertThat(response.chartInfo()).isNotNull();
        assertThat(response.chartInfo().firstSampleTime()).isEqualTo("2010-01-04T00:00:00Z");
        assertThat(response.chartInfo().horizon()).isEqualTo(1440);

        // Bearer header sent
        wireMock.verify(getRequestedFor(urlPathEqualTo("/chart/v1/charts"))
                .withHeader("Authorization", equalTo(EXPECTED_BEARER)));
    }
}
