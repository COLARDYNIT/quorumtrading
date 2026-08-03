package com.quorumtrading.adapter.marketdata.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Envelope for the Saxo Reference Data instrument-search endpoint
 * ({@code GET /ref/v1/instruments?Keywords=…&AssetTypes=…&ExchangeId=…}), which wraps the hit
 * list in a {@code Data} array (SAXO_ANSWERS §2.3).
 *
 * <p><b>MD2-live-quotes — module-local copy.</b> The Saxo market-data module owns its own
 * reference-data DTOs so it does not depend on the broker-saxo adapter (ArchUnit-enforced
 * hexagonal isolation). {@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps paging /
 * metadata fields from breaking deserialisation.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaxoMdInstrumentSearchResponse {

    /** The instrument-search hit list. Saxo wraps results under {@code Data}. */
    @JsonProperty("Data")
    private List<SaxoMdInstrumentSummary> data;

    public List<SaxoMdInstrumentSummary> getData() {
        return data;
    }

    public void setData(List<SaxoMdInstrumentSummary> data) {
        this.data = data;
    }
}
