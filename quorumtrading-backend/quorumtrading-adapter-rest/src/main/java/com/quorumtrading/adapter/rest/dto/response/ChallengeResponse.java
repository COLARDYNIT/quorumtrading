package com.quorumtrading.adapter.rest.dto.response;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.math.BigDecimal;
import java.util.List;

@RegisterForReflection
public record ChallengeResponse(
    String id,
    String slug,
    String name,
    String description,
    String ticker,
    String tickerType,
    BigDecimal initialCash,
    String simulationStartDate,
    String simulationEndDate,
    String participationOpen,
    String participationClose,
    String status,
    boolean isBacktest,
    boolean acceptingEntries,
    int participantCount,
    String createdAt,
    boolean strategiesPublic,
    List<ChallengeEntryResponse> entries
) {}
