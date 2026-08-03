package com.quorumtrading.adapter.marketdata.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Live (or exchange-delayed) info-prices payload from the Saxo OpenAPI
 * {@code GET /trade/v1/infoprices} endpoint.
 *
 * <p><b>MD2-live-quotes.</b> Requested with
 * {@code FieldGroups=Quote,PriceInfo,PriceInfoDetails} (SAXO_ANSWERS §3.3) so a single call
 * yields the current quote plus intraday day stats:</p>
 * <ul>
 *   <li>{@link Quote} — {@code Bid}/{@code Mid}/{@code Ask}, {@code DelayedByMinutes}
 *       (0 = real time, &gt;0 = exchange-delayed), {@code MarketState}, and the
 *       {@code PriceTypeBid}/{@code PriceTypeAsk} entitlement enums
 *       ({@code Indicative}/{@code OldIndicative}/{@code NoAccess}/{@code NoMarket}/{@code Pending}).
 *       {@code NoAccess} = the account has no entitlement for this exchange (or SIM / API market
 *       data not enabled), which the live branch maps to an empty result, not an error.</li>
 *   <li>{@link PriceInfo} — day {@code High}/{@code Low} (and {@code NetChange}/{@code PercentChange}).</li>
 *   <li>{@link PriceInfoDetails} — day {@code Open}, {@code Volume}, {@code LastTraded},
 *       {@code LastClose}.</li>
 * </ul>
 *
 * <p>Intraday O/H/L for the live PRICE event comes from {@code PriceInfo.High}/{@code Low} and
 * {@code PriceInfoDetails.Open}; not OHLC bar history (that is Charts / {@code MD3}).</p>
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} so the DTO tolerates the many Saxo
 * fields we do not consume. No business logic lives in this record.</p>
 *
 * <p><b>⚠️ TODO(MD2, confirm against a funded LIVE account):</b> the {@code PriceInfoDetails}
 * field names (esp. {@code Open}/{@code Volume}/{@code LastClose}) are best-effort against the
 * Saxo pricing docs; reconfirm casing once a real entitled response is observed.</p>
 *
 * @param uic        the instrument's numeric Saxo Uic
 * @param assetType  the Saxo asset type (e.g. {@code Stock}, {@code Etf})
 * @param quote      the bid/ask/mid quote block (nullable until the right FieldGroups are requested)
 * @param priceInfo  the day high/low/net-change block (nullable)
 * @param priceInfoDetails the day open/volume/last-traded block (nullable)
 * @param lastUpdated ISO-8601 timestamp of the last price update (nullable)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InfoPricesResponse(
        @JsonProperty("Uic") Long uic,
        @JsonProperty("AssetType") String assetType,
        @JsonProperty("Quote") Quote quote,
        @JsonProperty("PriceInfo") PriceInfo priceInfo,
        @JsonProperty("PriceInfoDetails") PriceInfoDetails priceInfoDetails,
        @JsonProperty("LastUpdated") String lastUpdated
) {

    /** Saxo {@code PriceType*} enum value meaning the account has no entitlement for this feed. */
    public static final String PRICE_TYPE_NO_ACCESS = "NoAccess";

    /**
     * The bid/ask/mid quote block.
     *
     * @param bid              best bid price (nullable)
     * @param ask              best ask price (nullable)
     * @param mid              mid price (nullable)
     * @param delayedByMinutes how many minutes the quote is delayed (0 = real-time; nullable)
     * @param marketState      exchange market state (e.g. {@code Open}, {@code Closed}; nullable)
     * @param priceTypeBid     entitlement type of the bid (e.g. {@code NoAccess}; nullable)
     * @param priceTypeAsk     entitlement type of the ask (e.g. {@code NoAccess}; nullable)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(
            @JsonProperty("Bid") BigDecimal bid,
            @JsonProperty("Ask") BigDecimal ask,
            @JsonProperty("Mid") BigDecimal mid,
            @JsonProperty("DelayedByMinutes") Integer delayedByMinutes,
            @JsonProperty("MarketState") String marketState,
            @JsonProperty("PriceTypeBid") String priceTypeBid,
            @JsonProperty("PriceTypeAsk") String priceTypeAsk
    ) {

        /**
         * @return {@code true} if either side reports {@link #PRICE_TYPE_NO_ACCESS} — i.e. the
         *     account is not entitled to this exchange's feed (no realtime/delayed data).
         */
        public boolean isNoAccess() {
            return PRICE_TYPE_NO_ACCESS.equalsIgnoreCase(priceTypeBid)
                    || PRICE_TYPE_NO_ACCESS.equalsIgnoreCase(priceTypeAsk);
        }

        /** @return {@code true} if the feed reports a real-time (0-minute) delay. */
        public boolean isRealtime() {
            return delayedByMinutes != null && delayedByMinutes == 0;
        }
    }

    /**
     * The day high/low / net-change block (Saxo {@code PriceInfo} field group).
     *
     * @param high           intraday day high (nullable)
     * @param low            intraday day low (nullable)
     * @param netChange      day net change vs previous close (nullable)
     * @param percentChange  day percent change vs previous close (nullable)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceInfo(
            @JsonProperty("High") BigDecimal high,
            @JsonProperty("Low") BigDecimal low,
            @JsonProperty("NetChange") BigDecimal netChange,
            @JsonProperty("PercentChange") BigDecimal percentChange
    ) {
    }

    /**
     * The day open / volume / last-traded block (Saxo {@code PriceInfoDetails} field group).
     *
     * @param open       intraday day open (nullable)
     * @param volume     day volume (nullable)
     * @param lastTraded last traded price (nullable)
     * @param lastClose  previous session close (nullable)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceInfoDetails(
            @JsonProperty("Open") BigDecimal open,
            @JsonProperty("Volume") BigDecimal volume,
            @JsonProperty("LastTraded") BigDecimal lastTraded,
            @JsonProperty("LastClose") BigDecimal lastClose
    ) {
    }
}
