package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.AssetClass;
import com.quorumtrading.model.enums.EventCategory;
import com.quorumtrading.model.enums.MarketSentiment;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A historical market event used for backtesting period selection and AI-driven discovery.
 * Reference data — not user-created, uses long id.
 */
public record HistoricalEvent(
        Long id,
        String slug,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        EventCategory category,
        AssetClass assetClass,
        MarketSentiment sentiment,
        String description,
        String impactSummary,
        List<String> suggestedAssets,
        List<String> keywords
) {

    public HistoricalEvent {
        Objects.requireNonNull(slug, "slug must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        Objects.requireNonNull(sentiment, "sentiment must not be null");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        suggestedAssets = suggestedAssets != null ? List.copyOf(suggestedAssets) : List.of();
        keywords = keywords != null ? List.copyOf(keywords) : List.of();
    }

    public boolean matchesKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return false;
        String lower = keyword.toLowerCase();
        return keywords.stream().anyMatch(k -> k.toLowerCase().contains(lower))
                || name.toLowerCase().contains(lower)
                || slug.toLowerCase().contains(lower);
    }
}
