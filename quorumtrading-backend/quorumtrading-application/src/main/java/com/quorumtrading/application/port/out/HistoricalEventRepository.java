package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.HistoricalEvent;
import com.quorumtrading.model.enums.AssetClass;
import com.quorumtrading.model.enums.EventCategory;
import com.quorumtrading.model.enums.MarketSentiment;

import java.util.List;
import java.util.Optional;

public interface HistoricalEventRepository {

    List<HistoricalEvent> findAll();

    Optional<HistoricalEvent> findBySlug(String slug);

    List<HistoricalEvent> findByFilters(
            Optional<EventCategory> category,
            Optional<AssetClass> assetClass,
            Optional<MarketSentiment> sentiment);

    List<HistoricalEvent> searchByKeyword(String keyword);
}
