package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.SocialEventJpaEntity;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.MarketEvent;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Map;

/**
 * Maps between MarketEvent domain model and SocialEventJpaEntity.
 */
@ApplicationScoped
public class SocialEventPersistenceMapper {

    /**
     * Convert a MarketEvent (SOCIAL type) to a SocialEventJpaEntity for persistence.
     */
    public SocialEventJpaEntity toJpaEntity(MarketEvent event, String contentHash) {
        SocialEventJpaEntity entity = new SocialEventJpaEntity();
        entity.symbol = event.symbol();
        entity.timestamp = event.timestamp();
        entity.source = event.source();
        entity.ingestedAt = event.ingestedAt() != null ? event.ingestedAt() : Instant.now();
        entity.contentHash = contentHash;

        Map<String, Object> data = event.rawData();
        if (data != null) {
            entity.title = toStringOrNull(data.get("title"));
            entity.author = toStringOrNull(data.get("author"));
            entity.score = toInteger(data.get("score"));
            entity.numComments = toInteger(data.get("num_comments"));
            entity.permalink = toStringOrNull(data.get("permalink"));
            entity.subreddit = toStringOrNull(data.get("subreddit"));
            entity.rawData = data;
        }

        return entity;
    }

    /**
     * Convert a SocialEventJpaEntity back to a MarketEvent domain object.
     */
    public MarketEvent toMarketEvent(SocialEventJpaEntity entity) {
        Map<String, Object> rawData = entity.rawData != null ? Map.copyOf(entity.rawData) : Map.of();
        return new MarketEvent(
            entity.id,
            entity.timestamp,
            EventType.SOCIAL,
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

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s && !s.isEmpty()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
