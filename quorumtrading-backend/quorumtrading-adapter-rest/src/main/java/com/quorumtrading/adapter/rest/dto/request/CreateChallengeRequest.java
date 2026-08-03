package com.quorumtrading.adapter.rest.dto.request;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.math.BigDecimal;

@RegisterForReflection
public record CreateChallengeRequest(
    String name,
    String description,
    String ticker,
    String investmentType,
    BigDecimal initialCash,
    String simulationStartDate,
    String simulationEndDate,
    String participationOpen,
    String participationClose,
    boolean strategiesPublic
) {}
