package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.HistoricalEventJpaEntity;
import com.quorumtrading.model.entity.HistoricalEvent;
import com.quorumtrading.model.enums.AssetClass;
import com.quorumtrading.model.enums.EventCategory;
import com.quorumtrading.model.enums.MarketSentiment;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class HistoricalEventPersistenceMapper {

    public HistoricalEvent toDomain(HistoricalEventJpaEntity entity) {
        return new HistoricalEvent(
                entity.id,
                entity.slug,
                entity.name,
                entity.startDate,
                entity.endDate,
                EventCategory.valueOf(entity.category),
                AssetClass.valueOf(entity.assetClass),
                MarketSentiment.valueOf(entity.sentiment),
                entity.description,
                entity.impactSummary,
                csvToList(entity.suggestedAssets),
                csvToList(entity.keywords)
        );
    }

    private List<String> csvToList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
