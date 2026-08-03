package com.quorumtrading.adapter.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "historical_events")
public class HistoricalEventJpaEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 80)
    public String slug;

    @Column(nullable = false, length = 120)
    public String name;

    @Column(name = "start_date", nullable = false)
    public LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    public LocalDate endDate;

    @Column(nullable = false, length = 30)
    public String category;

    @Column(name = "asset_class", nullable = false, length = 20)
    public String assetClass;

    @Column(nullable = false, length = 20)
    public String sentiment;

    @Column(nullable = false, length = 500)
    public String description;

    @Column(name = "impact_summary", nullable = false, length = 200)
    public String impactSummary;

    @Column(name = "suggested_assets", length = 200)
    public String suggestedAssets;

    @Column(length = 500)
    public String keywords;

    public HistoricalEventJpaEntity() {}
}
