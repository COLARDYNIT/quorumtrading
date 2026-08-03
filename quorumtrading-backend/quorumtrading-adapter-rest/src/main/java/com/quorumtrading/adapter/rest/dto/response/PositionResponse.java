package com.quorumtrading.adapter.rest.dto.response;

/**
 * Position response DTO representing a single position in a portfolio.
 * Mapped from domain Position entity.
 */
public class PositionResponse {

    public String symbol;
    public String type;  // STOCK or CRYPTO
    public double quantity;
    public double averagePrice;  // Cost basis (what we paid)
    public Double currentPrice;  // Current market price (nullable for backward compatibility)

    public PositionResponse() {}

    public PositionResponse(String symbol, String type, double quantity, double averagePrice) {
        this(symbol, type, quantity, averagePrice, null);
    }

    public PositionResponse(String symbol, String type, double quantity, double averagePrice, Double currentPrice) {
        this.symbol = symbol;
        this.type = type;
        this.quantity = quantity;
        this.averagePrice = averagePrice;
        this.currentPrice = currentPrice;
    }
}
