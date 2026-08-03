package com.quorumtrading.adapter.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Panache JPA entity for a measured provider data horizon.
 *
 * <p>Composite primary key (provider, symbol, eventType, granularityBucket) via
 * {@link Key}. {@code investmentType} is persisted so the domain Symbol can be
 * reconstructed exactly.</p>
 */
@Entity
@Table(name = "market_data_horizon")
@IdClass(MarketDataHorizonJpaEntity.Key.class)
public class MarketDataHorizonJpaEntity extends PanacheEntityBase {

    @Id
    @Column(name = "provider", nullable = false)
    public String provider;

    @Id
    @Column(name = "symbol", nullable = false)
    public String symbol;

    @Id
    @Column(name = "event_type", nullable = false)
    public String eventType;

    @Id
    @Column(name = "granularity_bucket", nullable = false)
    public String granularityBucket;

    @Column(name = "investment_type", nullable = false)
    public String investmentType;

    @Column(name = "earliest_available")
    public LocalDate earliestAvailable;

    @Column(name = "has_data", nullable = false)
    public boolean hasData;

    @Column(name = "probed_at", nullable = false)
    public Instant probedAt;

    @Column(name = "source", nullable = false)
    public String source;

    public MarketDataHorizonJpaEntity() {}

    /**
     * Composite primary key for {@link MarketDataHorizonJpaEntity}.
     */
    public static class Key implements Serializable {
        public String provider;
        public String symbol;
        public String eventType;
        public String granularityBucket;

        public Key() {}

        public Key(String provider, String symbol, String eventType, String granularityBucket) {
            this.provider = provider;
            this.symbol = symbol;
            this.eventType = eventType;
            this.granularityBucket = granularityBucket;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(provider, key.provider)
                    && Objects.equals(symbol, key.symbol)
                    && Objects.equals(eventType, key.eventType)
                    && Objects.equals(granularityBucket, key.granularityBucket);
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, symbol, eventType, granularityBucket);
        }
    }
}
