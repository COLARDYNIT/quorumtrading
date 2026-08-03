package com.quorumtrading.adapter.mcp.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Builds web-app URLs for MCP responses.
 *
 * <p>MCP clients cannot render an entity — the web app can, but a model driving the tools
 * has no way to know the app exists, let alone where it runs. Every response describing an
 * entity that has a detail page therefore carries a {@code uiUrl}, built here from the same
 * {@code quorumtrading.frontend.base-url} the notification emails already link through, so
 * standalone, dev and hosted instances each advertise their own frontend.</p>
 *
 * <p>All builders return {@code null} when the instance runs headless
 * ({@code quorumtrading.frontend.enabled=false}) — a link to a page that does not exist is
 * worse than no link — and the {@code uiUrl} fields are omitted from the JSON rather than
 * serialized as null.</p>
 */
@ApplicationScoped
public class UiLinks {

    @ConfigProperty(name = "quorumtrading.frontend.base-url", defaultValue = "http://localhost:3000")
    String frontendBaseUrl;

    @ConfigProperty(name = "quorumtrading.frontend.enabled", defaultValue = "true")
    boolean frontendEnabled;

    public String simulation(String simulationId) {
        return link("/simulations/", simulationId);
    }

    public String strategy(String strategyId) {
        return link("/strategies/", strategyId);
    }

    public String investmentPlan(String planId) {
        return link("/investment-plans/", planId);
    }

    public String benchmark(String benchmarkId) {
        return link("/benchmarks/", benchmarkId);
    }

    public String challenge(String challengeId) {
        return link("/challenges/", challengeId);
    }

    public String marketResearch(String marketResearchId) {
        return link("/research/", marketResearchId);
    }

    private String link(String path, String id) {
        if (!frontendEnabled || id == null) {
            return null;
        }
        String base = frontendBaseUrl.endsWith("/")
            ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
            : frontendBaseUrl;
        return base + path + id;
    }
}
