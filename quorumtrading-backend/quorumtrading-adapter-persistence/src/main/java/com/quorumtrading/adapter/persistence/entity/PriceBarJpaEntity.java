package com.quorumtrading.adapter.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_bars")
public class PriceBarJpaEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 20)
    public String symbol;

    /**
     * Asset class of the instrument this bar belongs to (CRYPTO / STOCK). Nullable: rows written
     * before V127 carry no type, and readers must keep accepting them or historical prices
     * vanish from every backtest. (V127)
     */
    @Column(name = "investment_type", length = 10)
    public String investmentType;

    @Column(nullable = false)
    public Instant timestamp;

    @Column(nullable = false, length = 100)
    public String source;

    @Column(precision = 20, scale = 8)
    public BigDecimal open;

    @Column(precision = 20, scale = 8)
    public BigDecimal high;

    @Column(precision = 20, scale = 8)
    public BigDecimal low;

    @Column(nullable = false, precision = 20, scale = 8)
    public BigDecimal close;

    @Column(precision = 20, scale = 4)
    public BigDecimal volume;

    @Column(name = "ingested_at", nullable = false)
    public Instant ingestedAt;

    @Column(name = "content_hash", length = 64)
    public String contentHash;

    public PriceBarJpaEntity() {}
}
