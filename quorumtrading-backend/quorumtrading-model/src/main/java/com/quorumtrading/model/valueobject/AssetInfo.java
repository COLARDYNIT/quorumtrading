package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.AssetType;

/**
 * Value object representing asset information from a broker.
 * Used for ticker validation with detailed feedback.
 *
 * <p>The {@code mic}/{@code currency}/{@code uic}/{@code assetType} fields are
 * additive and nullable: they carry the venue / settlement currency / numeric
 * instrument identity for brokers that expose them (e.g. Saxo). US/Alpaca
 * results leave them null and behave exactly as before.</p>
 *
 * @param ticker     the ticker symbol
 * @param name       human-readable asset name (e.g., "Apple Inc."), null if not found
 * @param exchange   the exchange the asset trades on (e.g., "NASDAQ"), null if not found
 * @param tradable   whether the asset can be traded through this broker
 * @param status     asset status: "active", "inactive", or "not_found"
 * @param assetClass asset class: "us_equity", "crypto", or null if not found
 * @param mic        ISO 10383 market identifier code of the listing venue, nullable
 * @param currency   settlement currency, nullable (null ⇒ USD by convention)
 * @param uic        broker-specific numeric instrument id (Saxo Uic), nullable
 * @param assetType  broker-neutral instrument asset type, nullable
 */
public record AssetInfo(
    String ticker,
    String name,
    String exchange,
    boolean tradable,
    String status,
    String assetClass,
    MIC mic,
    Money.Currency currency,
    Long uic,
    AssetType assetType
) {

    public static AssetInfo notFound(String ticker) {
        return new AssetInfo(ticker, null, null, false, "not_found", null, null, null, null, null);
    }

    public static AssetInfo notTradable(String ticker, String name, String exchange, String status, String assetClass) {
        return new AssetInfo(ticker, name, exchange, false, status, assetClass, null, null, null, null);
    }

    public static AssetInfo tradable(String ticker, String name, String exchange, String assetClass) {
        return new AssetInfo(ticker, name, exchange, true, "active", assetClass, null, null, null, null);
    }

    public boolean isFound() {
        return !"not_found".equals(status);
    }
}
