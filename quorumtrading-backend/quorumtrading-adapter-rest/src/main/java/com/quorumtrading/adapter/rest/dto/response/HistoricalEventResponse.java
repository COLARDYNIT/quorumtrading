package com.quorumtrading.adapter.rest.dto.response;

import java.util.List;

public record HistoricalEventResponse(
        Long id,
        String slug,
        String name,
        String startDate,
        String endDate,
        String category,
        String assetClass,
        String sentiment,
        String description,
        String impactSummary,
        List<String> suggestedAssets,
        List<String> keywords
) {}
