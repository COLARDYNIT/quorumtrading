package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.marketdata.GetMarketDataStatusUseCase;
import com.quorumtrading.application.usecase.marketdata.MarketDataStatus;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The market-data counterpart to {@code get_llm_status}, and deliberately its own class rather than
 * another method on {@code MarketDataTools}.
 *
 * <p>This is a question about the <em>deployment</em> — which providers exist and whether they work
 * — not about an instrument, which is what every other market-data tool asks. Keeping it separate
 * also mirrors {@code LlmProviderTools}, the tool it is modelled on.</p>
 */
@Singleton
@ActivateRequestContext
public class MarketDataStatusTools extends McpToolBase {

    @Inject
    GetMarketDataStatusUseCase getMarketDataStatusUseCase;

    @Inject
    McpUserContext userContext;

    @Tool(name = "get_market_data_status", description = "Show which price-data providers this deployment has, whether each is switched on, whether it holds credentials, and — unless you pass probe=false — what a live call to it returns right now. "
        + "The market-data counterpart to get_llm_status. Use it when a backfill returns nothing, to tell 'no key configured' apart from 'this provider does not carry that symbol'.")
    public ToolResponse getMarketDataStatus(
        @ToolArg(description = "Make a live call to each usable provider (default true). Configuration alone cannot tell a valid key from a revoked one.", required = false) Optional<Boolean> probe
    ) {
        return execute(() -> {
            userContext.getUserId(); // auth gate: deployment-wide state, but a valid API token is required
            MarketDataStatus status = getMarketDataStatusUseCase.get(probe.orElse(true));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cryptoUsable", status.cryptoUsable());
            payload.put("stockUsable", status.stockUsable());
            payload.put("providers", status.providers().stream().map(MarketDataStatusTools::providerPayload).toList());
            payload.put("hint", statusHint(status));
            return toJson(payload);
        });
    }

    private static Map<String, Object> providerPayload(MarketDataStatus.ProviderStatus provider) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", provider.name());
        entry.put("enabled", provider.enabled());
        entry.put("credentialed", provider.credentialed());
        entry.put("usable", provider.usable());
        entry.put("assetClasses", provider.assetClasses());
        entry.put("features", provider.features());
        entry.put("detail", providerDetail(provider));
        if (provider.probe() != null) {
            entry.put("probe", Map.of(
                "symbol", provider.probe().symbol(),
                "ok", provider.probe().ok(),
                "detail", provider.probe().detail()));
        }
        return entry;
    }

    private static String providerDetail(MarketDataStatus.ProviderStatus provider) {
        if (!provider.enabled()) {
            return "Switched off for this deployment — it is never called and never counted among "
                + "the providers a failed backfill says it tried.";
        }
        if (!provider.credentialed()) {
            return "Switched on but holding no credentials, so it is skipped rather than called. "
                + "Set its API key and restart the backend.";
        }
        return "Enabled and credentialed.";
    }

    private static String statusHint(MarketDataStatus status) {
        if (status.cryptoUsable() && status.stockUsable()) {
            return "Both crypto and stock candles have a usable provider.";
        }
        if (status.cryptoUsable()) {
            return "Crypto candles are served (keyless). No usable stock provider — stock backfills "
                + "will return nothing until one is enabled and given a key.";
        }
        if (status.stockUsable()) {
            return "Stock candles are served. No usable crypto provider on this deployment.";
        }
        return "No price provider is usable, so every backfill will return nothing. This is a "
            + "configuration problem, not a coverage problem — do not go looking for a different ticker.";
    }
}
