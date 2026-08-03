package com.quorumtrading.adapter.marketdata.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Historical chart-samples payload from the Saxo OpenAPI {@code GET /chart/v1/charts}
 * endpoint, used for backfill.
 *
 * <p><b>Scaffold (MD1).</b> Surfaces the OHLC {@link Sample} list plus the {@link ChartInfo}
 * block carrying {@code FirstSampleTime} so the paginator ({@code MD3}) can stitch windows
 * and stop at the first available sample — and so {@code earliestAvailable} is a measured
 * value, never guessed (per MEMORY "no assumed data floors").
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} so the DTO tolerates unconsumed Saxo
 * fields (e.g. {@code DataVersion}, {@code DisplayAndFormat}).</p>
 *
 * <p><b>TODO(MD3):</b> reconfirm the field names + sample value semantics — whether equity
 * samples carry {@code Open/High/Low/Close} (vs FX bid/ask candles), the {@code Time} format,
 * and the exact {@code ChartInfo} pagination fields. No business logic lives in this record.</p>
 *
 * @param data      the ordered list of OHLC samples (may be empty when the window is exhausted)
 * @param chartInfo metadata block including {@code FirstSampleTime} for pagination/earliest-available (nullable)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChartsResponse(
        @JsonProperty("Data") List<Sample> data,
        @JsonProperty("ChartInfo") ChartInfo chartInfo
) {

    /**
     * A single OHLC chart sample.
     *
     * @param time   ISO-8601 sample timestamp
     * @param open   open price
     * @param high   high price
     * @param low    low price
     * @param close  close price
     * @param volume traded volume over the sample (nullable; not always present for equities)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sample(
            @JsonProperty("Time") String time,
            @JsonProperty("Open") BigDecimal open,
            @JsonProperty("High") BigDecimal high,
            @JsonProperty("Low") BigDecimal low,
            @JsonProperty("Close") BigDecimal close,
            @JsonProperty("Volume") BigDecimal volume
    ) {
    }

    /**
     * Chart metadata used to drive pagination and the measured earliest-available date.
     *
     * @param firstSampleTime ISO-8601 timestamp of the earliest sample the venue can return (nullable)
     * @param horizon         the horizon (minutes per sample) the samples were returned at (nullable)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartInfo(
            @JsonProperty("FirstSampleTime") String firstSampleTime,
            @JsonProperty("Horizon") Integer horizon
    ) {
    }
}
