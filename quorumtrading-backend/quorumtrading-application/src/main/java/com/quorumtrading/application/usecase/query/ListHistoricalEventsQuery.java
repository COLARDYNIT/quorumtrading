package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.enums.AssetClass;
import com.quorumtrading.model.enums.EventCategory;
import com.quorumtrading.model.enums.MarketSentiment;

import java.util.Optional;

public record ListHistoricalEventsQuery(
        Optional<EventCategory> category,
        Optional<AssetClass> assetClass,
        Optional<MarketSentiment> sentiment
) {
    public ListHistoricalEventsQuery() {
        this(Optional.empty(), Optional.empty(), Optional.empty());
    }
}
