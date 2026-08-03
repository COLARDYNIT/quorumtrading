package com.quorumtrading.adapter.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "news_events")
public class NewsEventJpaEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(length = 20)
    public String symbol;

    @Column(nullable = false)
    public Instant timestamp;

    @Column(nullable = false, length = 100)
    public String source;

    public String headline;

    public String summary;

    public String url;

    @Column(length = 100)
    public String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    public Map<String, Object> rawData;

    @Column(name = "ingested_at", nullable = false)
    public Instant ingestedAt;

    @Column(name = "content_hash", length = 64)
    public String contentHash;

    public NewsEventJpaEntity() {}
}
