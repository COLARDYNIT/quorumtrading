package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.model.entity.Contribution;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "A single cash contribution into a LIVE simulation (recurring-investment top-up or manual injection)")
public record ContributionResponse(
        String id,
        BigDecimal amount,
        String currency,
        Instant contributionDate,
        String sourcePlanId,
        String sourceAllocationId,
        Instant createdAt
) {
    public static ContributionResponse fromDomain(Contribution c) {
        return new ContributionResponse(
                c.id().value().toString(),
                c.amount().amount(),
                c.amount().currency().name(),
                c.contributionDate(),
                c.sourcePlanId() != null ? c.sourcePlanId().toString() : null,
                c.sourceAllocationId() != null ? c.sourceAllocationId().toString() : null,
                c.createdAt());
    }
}
