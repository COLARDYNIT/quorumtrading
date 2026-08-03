package com.quorumtrading.adapter.broker.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Envelope for the Saxo OpenAPI open-positions endpoint
 * ({@code GET /port/v1/positions?ClientKey=…&AccountKey=…}).
 *
 * <p><b>SB5-portfolio-balance-validate.</b> Saxo wraps the position list in a {@code Data}
 * array (the same envelope shape as accounts/open-orders). Each row is a
 * {@link SaxoPositionResponse} flattening the handful of fields the adapter needs to
 * reconstruct a {@link com.quorumtrading.model.entity.Position} keyed by the resolved
 * {@link com.quorumtrading.model.valueobject.Symbol}: {@code Uic}, {@code AssetType},
 * open {@code Amount}, {@code OpenPrice} (cost basis), and the current valuation.</p>
 *
 * <p><b>⚠️ TODO(SB5, unconfirmed):</b> SAXO_ANSWERS §2.8 lists {@code /positions} <em>vs</em>
 * {@code /netpositions} as the canonical equity-holding view; this adapter reads
 * {@code /positions} (one row per open lot) as the documented best-effort. If Saxo returns
 * several lots for one instrument, the adapter aggregates by {@code (Uic, AssetType)} so the
 * resulting {@link com.quorumtrading.model.entity.Position} is a single weighted-average row.
 * Confirm whether {@code /netpositions} (already-aggregated) is preferable once a funded SIM
 * account is available.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaxoPositionsResponse {

    /** The position-list envelope. TODO(SB5): confirm Saxo wraps positions in {@code Data}. */
    @JsonProperty("Data")
    private List<SaxoPositionResponse> data;

    public List<SaxoPositionResponse> getData() {
        return data;
    }

    public void setData(List<SaxoPositionResponse> data) {
        this.data = data;
    }
}
