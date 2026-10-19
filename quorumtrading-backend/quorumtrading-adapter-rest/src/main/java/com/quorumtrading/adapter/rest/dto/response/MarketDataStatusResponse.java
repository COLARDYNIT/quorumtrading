package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for the deployment's market-data provider status — the REST rendering of
 * {@code GetMarketDataStatusUseCase}, the same use case behind the MCP tool
 * {@code get_market_data_status}.
 *
 * <p>Exists so the Data Availability page can say the one thing a keyless install needs
 * to hear: stocks have no usable provider until an API key is set, while crypto works
 * with zero keys. Before this endpoint, that state was backend-only knowledge and the
 * page silently rendered failed stock backfills as coverage problems.</p>
 */
@Schema(description = "Which market-data providers this deployment has and whether each is usable")
public record MarketDataStatusResponse(
        @Schema(description = "Whether at least one enabled, credentialed provider can serve crypto candles",
                example = "true")
        boolean cryptoUsable,

        @Schema(description = "Whether at least one enabled, credentialed provider can serve stock candles",
                example = "false")
        boolean stockUsable,

        @Schema(description = "Every discovered candle-serving provider, enabled or not")
        List<Provider> providers,

        @Schema(description = "One-line human summary of what works and what is missing")
        String hint
) {

    @Schema(description = "One provider's state")
    public record Provider(
            @Schema(description = "Provider name as it appears in backfill messages", example = "alpaca")
            String name,

            @Schema(description = "Switched on for this deployment", example = "true")
            boolean enabled,

            @Schema(description = "Holds whatever credentials it needs (always true for keyless providers)",
                    example = "false")
            boolean credentialed,

            @Schema(description = "Enabled AND credentialed — could serve a request right now", example = "false")
            boolean usable,

            @Schema(description = "Asset classes it advertises", example = "[\"STOCK\"]")
            List<String> assetClasses,

            @Schema(description = "What its current state means for the operator")
            String detail
    ) {}
}
