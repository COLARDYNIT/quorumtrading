package com.quorumtrading.adapter.mcp.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiLinksTest {

    private UiLinks uiLinks;

    @BeforeEach
    void setUp() {
        uiLinks = new UiLinks();
        uiLinks.frontendBaseUrl = "http://localhost:3000";
        uiLinks.frontendEnabled = true;
    }

    @Test
    void buildsDetailPageUrlsForEveryEntityWithAPage() {
        assertThat(uiLinks.simulation("s-1")).isEqualTo("http://localhost:3000/simulations/s-1");
        assertThat(uiLinks.strategy("st-1")).isEqualTo("http://localhost:3000/strategies/st-1");
        assertThat(uiLinks.investmentPlan("p-1")).isEqualTo("http://localhost:3000/investment-plans/p-1");
        assertThat(uiLinks.benchmark("b-1")).isEqualTo("http://localhost:3000/benchmarks/b-1");
        assertThat(uiLinks.challenge("c-1")).isEqualTo("http://localhost:3000/challenges/c-1");
        assertThat(uiLinks.marketResearch("r-1")).isEqualTo("http://localhost:3000/research/r-1");
    }

    /**
     * FRONTEND_URL is operator-supplied; both "https://host" and "https://host/" must
     * produce a link with exactly one slash — a double slash 404s on some reverse proxies.
     */
    @Test
    void toleratesATrailingSlashOnTheConfiguredBaseUrl() {
        uiLinks.frontendBaseUrl = "https://quorumtrading.com/";

        assertThat(uiLinks.simulation("s-1")).isEqualTo("https://quorumtrading.com/simulations/s-1");
    }

    /**
     * A headless instance (QT_FRONTEND_ENABLED=false) has no page to link to. Advertising
     * one would send the user to a connection error, so the builders must return null —
     * which the DTOs then omit from the JSON entirely.
     */
    @Test
    void returnsNullWhenTheFrontendIsDisabled() {
        uiLinks.frontendEnabled = false;

        assertThat(uiLinks.simulation("s-1")).isNull();
        assertThat(uiLinks.strategy("st-1")).isNull();
    }

    @Test
    void returnsNullForANullId() {
        assertThat(uiLinks.simulation(null)).isNull();
    }
}
