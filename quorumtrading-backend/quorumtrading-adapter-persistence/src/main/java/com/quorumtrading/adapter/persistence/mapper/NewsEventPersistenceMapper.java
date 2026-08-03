package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.NewsEventJpaEntity;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.MarketEvent;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Map;

/**
 * Maps between MarketEvent domain model and NewsEventJpaEntity.
 */
@ApplicationScoped
public class NewsEventPersistenceMapper {

    /**
     * Convert a MarketEvent (NEWS type) to a NewsEventJpaEntity for persistence.
     */
    public NewsEventJpaEntity toJpaEntity(MarketEvent event, String contentHash) {
        NewsEventJpaEntity entity = new NewsEventJpaEntity();
        entity.symbol = event.symbol();
        entity.timestamp = event.timestamp();
        entity.source = event.source();
        entity.ingestedAt = event.ingestedAt() != null ? event.ingestedAt() : Instant.now();
        entity.contentHash = contentHash;

        Map<String, Object> data = event.rawData();
        if (data != null) {
            entity.headline = toStringOrNull(data.get("headline"));
            entity.summary = toStringOrNull(data.get("summary"));
            entity.url = toStringOrNull(data.get("url"));
            if (entity.url == null) {
                entity.url = toStringOrNull(data.get("link"));
            }
            entity.category = toStringOrNull(data.get("category"));
            entity.rawData = data;
        }

        return entity;
    }

    /**
     * Convert a NewsEventJpaEntity back to a MarketEvent domain object.
     */
    public MarketEvent toMarketEvent(NewsEventJpaEntity entity) {
        Map<String, Object> rawData = entity.rawData != null ? Map.copyOf(entity.rawData) : Map.of();
        return new MarketEvent(
            entity.id,
            entity.timestamp,
            EventType.NEWS,
            entity.source,
            entity.symbol,
            rawData,
            entity.ingestedAt,
            null,
            entity.contentHash
        );
    }

    private String toStringOrNull(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
