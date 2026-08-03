package com.quorumtrading.adapter.broker.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Envelope for the Saxo Reference Data instrument-search endpoint
 * ({@code GET /ref/v1/instruments?Keywords=…&AssetTypes=…&ExchangeId=…}), which wraps
 * the hit list in a {@code Data} array (the same envelope shape Saxo uses for
 * {@code /port/v1/accounts/me}).
 *
 * <p><b>SB3-uic-resolver.</b> The scaffold's {@code searchInstruments} returned a bare
 * {@code List<SaxoInstrumentSummary>}; per SAXO_ANSWERS §2.3 the real search response is
 * {@code { "Data": [ … ] }}, so the {@code SaxoReferenceDataClient} now deserialises into
 * this envelope and the resolver reads {@link #getData()}.</p>
 *
 * <p><b>⚠️ TODO(SB3, unconfirmed):</b> the exact envelope key is best-effort against the
 * Saxo docs ({@code Data}); a {@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps
 * paging/metadata fields ({@code __next}, {@code MaxRows}) from breaking deserialisation.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaxoInstrumentSearchResponse {

    /** The instrument-search hit list. Saxo wraps results under {@code Data}. */
    @JsonProperty("Data")
    private List<SaxoInstrumentSummary> data;

    public List<SaxoInstrumentSummary> getData() {
        return data;
    }

    public void setData(List<SaxoInstrumentSummary> data) {
        this.data = data;
    }
}
