package com.quorumtrading.model.marketdata;

/**
 * Analyst buy/hold/sell recommendation consensus for a stock.
 */
public record AnalystConsensus(
    String period,
    int strongBuy,
    int buy,
    int hold,
    int sell,
    int strongSell
) {

    public int totalAnalysts() {
        return strongBuy + buy + hold + sell + strongSell;
    }
}
