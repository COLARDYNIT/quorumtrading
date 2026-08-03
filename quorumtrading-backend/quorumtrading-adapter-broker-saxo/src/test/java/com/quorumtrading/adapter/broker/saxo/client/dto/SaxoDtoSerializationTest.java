package com.quorumtrading.adapter.broker.saxo.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trivial (de)serialization smoke test for the Saxo DTO scaffold (SB1).
 *
 * <p>Plain Jackson, no CDI/Quarkus boot — verifies the {@code @JsonProperty}
 * mappings round-trip and that the response DTOs tolerate unknown Saxo fields
 * ({@code @JsonIgnoreProperties(ignoreUnknown = true)}). It does NOT assert the
 * (still-unconfirmed, TODO-flagged) Saxo {@code /port/v1} field names are correct —
 * Wave-4 work packages own that.</p>
 */
class SaxoDtoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void orderRequestSerializesWithSaxoFieldNames() throws Exception {
        SaxoOrderRequest req = new SaxoOrderRequest();
        req.setUic(211L);
        req.setAssetType("Stock");
        req.setAmount(new BigDecimal("6"));
        req.setBuySell("Buy");
        req.setOrderType("Market");
        req.setOrderDuration(new SaxoOrderRequest.SaxoOrderDuration("DayOrder"));
        req.setAccountKey("acct-key-123");
        req.setManualOrder(false);
        req.setExternalReference("qt-corr-1");

        String json = mapper.writeValueAsString(req);

        assertThat(json)
                .contains("\"Uic\":211")
                .contains("\"AssetType\":\"Stock\"")
                .contains("\"Amount\":6")
                .contains("\"BuySell\":\"Buy\"")
                .contains("\"OrderType\":\"Market\"")
                .contains("\"ManualOrder\":false")
                .contains("\"OrderDuration\":{\"DurationType\":\"DayOrder\"}")
                .contains("\"ExternalReference\":\"qt-corr-1\"")
                .contains("\"AccountKey\":\"acct-key-123\"");
    }

    @Test
    void orderResponseDeserializesPlacementOrderIdAndIgnoresUnknownFields() throws Exception {
        String json = """
                {
                  "OrderId": "S-987654",
                  "ExternalReference": "qt-corr-1",
                  "Orders": [ { "OrderId": "S-987654" } ],
                  "SomeFutureSaxoField": "ignored"
                }
                """;

        SaxoOrderResponse resp = mapper.readValue(json, SaxoOrderResponse.class);

        assertThat(resp.getOrderId()).isEqualTo("S-987654");
        assertThat(resp.getExternalReference()).isEqualTo("qt-corr-1");
        assertThat(resp.getOrders()).hasSize(1);
        assertThat(resp.getOrders().get(0).getOrderId()).isEqualTo("S-987654");
        assertThat(resp.getErrorInfo()).isNull();
    }

    @Test
    void orderResponseDeserializesErrorInfoOnRejection() throws Exception {
        String json = """
                {
                  "ErrorInfo": { "ErrorCode": "IllegalAmount", "Message": "Amount below minimum" }
                }
                """;

        SaxoOrderResponse resp = mapper.readValue(json, SaxoOrderResponse.class);

        assertThat(resp.getErrorInfo()).isNotNull();
        assertThat(resp.getErrorInfo().getErrorCode()).isEqualTo("IllegalAmount");
        assertThat(resp.getErrorInfo().describe()).isEqualTo("IllegalAmount: Amount below minimum");
    }

    @Test
    void orderResponseDeserializesStatusReadFillFields() throws Exception {
        String json = """
                {
                  "OrderId": "S-987654",
                  "Status": "Filled",
                  "FillPrice": 150.25,
                  "FilledAmount": 6
                }
                """;

        SaxoOrderResponse resp = mapper.readValue(json, SaxoOrderResponse.class);

        assertThat(resp.getStatus()).isEqualTo("Filled");
        assertThat(resp.getFillPrice()).isEqualByComparingTo("150.25");
        assertThat(resp.getFilledAmount()).isEqualByComparingTo("6");
    }

    @Test
    void balanceResponseDeserializesCurrencyAndCash() throws Exception {
        String json = """
                {
                  "CashBalance": 10000.50,
                  "TotalValue": 12500.75,
                  "CashAvailableForTrading": 9800.00,
                  "Currency": "EUR",
                  "MarginUtilizationPct": 0.0
                }
                """;

        SaxoBalanceResponse resp = mapper.readValue(json, SaxoBalanceResponse.class);

        assertThat(resp.getCashBalance()).isEqualByComparingTo("10000.50");
        assertThat(resp.getCurrency()).isEqualTo("EUR");
        assertThat(resp.getCashAvailableForTrading()).isEqualByComparingTo("9800.00");
    }

    @Test
    void accountResponseDeserializesDataEnvelope() throws Exception {
        String json = """
                {
                  "Data": [
                    {
                      "AccountKey": "key-eur-1",
                      "AccountId": "12345/678",
                      "Currency": "EUR",
                      "Active": true,
                      "LegalAssetTypes": ["Stock", "Etf"]
                    }
                  ]
                }
                """;

        SaxoAccountResponse resp = mapper.readValue(json, SaxoAccountResponse.class);

        assertThat(resp.getData()).hasSize(1);
        SaxoAccountResponse.SaxoAccount account = resp.getData().get(0);
        assertThat(account.getAccountKey()).isEqualTo("key-eur-1");
        assertThat(account.getCurrency()).isEqualTo("EUR");
        assertThat(account.getActive()).isTrue();
    }

    @Test
    void instrumentSummaryDeserializesSearchHit() throws Exception {
        String json = """
                {
                  "Identifier": 211,
                  "Symbol": "RHM:xetr",
                  "Description": "Rheinmetall AG",
                  "AssetType": "Stock",
                  "ExchangeId": "XETR",
                  "CurrencyCode": "EUR",
                  "SummaryType": "Instrument"
                }
                """;

        SaxoInstrumentSummary summary = mapper.readValue(json, SaxoInstrumentSummary.class);

        assertThat(summary.getUic()).isEqualTo(211L);
        assertThat(summary.getSymbol()).isEqualTo("RHM:xetr");
        assertThat(summary.getAssetType()).isEqualTo("Stock");
        assertThat(summary.getCurrencyCode()).isEqualTo("EUR");
    }
}
