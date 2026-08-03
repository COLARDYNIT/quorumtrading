package com.quorumtrading.adapter.rest.dto.response;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST response for dividend information query.
 */
@RegisterForReflection
public record DividendInfoResponse(
    List<DividendInfoItem> items
) {

    @RegisterForReflection
    public record DividendInfoItem(
        String ticker,
        boolean paysDividends,
        BigDecimal annualYieldEstimate,
        int dividendCountLastYear,
        String assetType
    ) {}
}
