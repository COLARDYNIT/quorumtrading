package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.PriceBarJpaEntity;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.PriceBar;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps between MarketEvent domain model, PriceBar value object, and PriceBarJpaEntity.
 */
@ApplicationScoped
public class PriceBarPersistenceMapper {

    /**
     * Convert a MarketEvent (PRICE type) to a PriceBarJpaEntity for persistence.
     */
    public PriceBarJpaEntity toJpaEntity(MarketEvent event, String contentHash) {
        PriceBarJpaEntity entity = new PriceBarJpaEntity();
        entity.symbol = event.symbol();
        entity.timestamp = event.timestamp();
        entity.source = event.source();
        // Stamp the asset class so a ticker that exists as both an equity and a crypto does not
        // end up sharing one price series. A MarketEvent carries only a bare ticker, so this is
        // the same heuristic the domain uses everywhere else. (V127)
        entity.investmentType =
            com.quorumtrading.model.enums.InvestmentType.fromTicker(event.symbol()).name();
        entity.ingestedAt = event.ingestedAt() != null ? event.ingestedAt() : Instant.now();
        entity.contentHash = contentHash;

        Map<String, Object> data = event.rawData();
        if (data != null) {
            entity.open = toBigDecimal(data.get("open"));
            entity.high = toBigDecimal(data.get("high"));
            entity.low = toBigDecimal(data.get("low"));
            entity.close = toBigDecimal(data.get("close"));
            if (entity.close == null) {
                entity.close = toBigDecimal(data.get("price"));
            }
            entity.volume = toBigDecimal(data.get("volume"));
        }

        return entity;
    }

    /**
     * Convert a PriceBarJpaEntity to a PriceBar value object.
     */
    public PriceBar toPriceBar(PriceBarJpaEntity entity) {
        return new PriceBar(
            entity.timestamp,
            entity.open,
            entity.high,
            entity.low,
            entity.close,
            entity.volume
        );
    }

    /**
     * Convert a PriceBarJpaEntity back to a MarketEvent domain object.
     */
    public MarketEvent toMarketEvent(PriceBarJpaEntity entity) {
        Map<String, Object> rawData = new HashMap<>();
        if (entity.open != null) rawData.put("open", entity.open);
        if (entity.high != null) rawData.put("high", entity.high);
        if (entity.low != null) rawData.put("low", entity.low);
        if (entity.close != null) {
            rawData.put("close", entity.close);
            rawData.put("price", entity.close); // backward compat: consumers read "price" key
        }
        if (entity.volume != null) rawData.put("volume", entity.volume);

        return new MarketEvent(
            entity.id,
            entity.timestamp,
            EventType.PRICE,
            entity.source,
            entity.symbol,
            Map.copyOf(rawData),
            entity.ingestedAt,
            null,
            entity.contentHash
        );
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (value instanceof String s && !s.isEmpty()) return new BigDecimal(s);
        return null;
    }
}
