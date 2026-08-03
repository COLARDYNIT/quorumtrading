package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.GetSimulationUseCase;
import com.quorumtrading.application.usecase.QueryPortfolioSnapshotsUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationQuery;
import com.quorumtrading.application.usecase.query.QueryPortfolioSnapshotsQuery;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code query_portfolio_snapshots}, which had no test.
 *
 * <p>Two things here are load-bearing and neither fails loudly if broken. The query carries the
 * caller's id so ownership is checked <em>before</em> the read — an earlier version used the
 * overload that leaves it null and validated afterwards, which is a data leak with a tidy-looking
 * response. And {@code resolution} decides whether an agent sees one point per day or every
 * snapshot; accepted-and-ignored, it would just draw the wrong series.</p>
 */
@QuarkusTest
@DisplayName("PortfolioTools")
class PortfolioToolsTest {

    @Inject PortfolioTools tools;

    @InjectMock QueryPortfolioSnapshotsUseCase queryPortfolioSnapshotsUseCase;
    // The tool also reads the simulation for initialCash context; unstubbed it returns null and
    // every call fails on the NPE rather than on anything the test is about.
    @InjectMock GetSimulationUseCase getSimulationUseCase;
    @InjectMock McpUserContext mcpUserContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UserId USER_ID = UserId.generate();
    private static final String SIM_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        when(mcpUserContext.getUserId()).thenReturn(USER_ID);
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(
                Simulation.reconstitute(
                        new SimulationId(UUID.fromString(SIM_ID)), "Test", StrategyId.generate(),
                        Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-08T00:00:00Z"),
                        Money.usd(new BigDecimal("10000.00")), null, null, null));
    }

    private PortfolioSnapshot snapshot(Instant at) {
        return new PortfolioSnapshot(
                new SimulationId(UUID.fromString(SIM_ID)), at, Map.of(),
                Money.usd(new BigDecimal("10000.00")), Money.usd(new BigDecimal("10000.00")));
    }

    private JsonNode json(ToolResponse response) throws Exception {
        assertThat(response.isError()).isFalse();
        return objectMapper.readTree(response.firstContent().asText().text());
    }

    private QueryPortfolioSnapshotsQuery capturedQuery() {
        ArgumentCaptor<QueryPortfolioSnapshotsQuery> captor =
                ArgumentCaptor.forClass(QueryPortfolioSnapshotsQuery.class);
        verify(queryPortfolioSnapshotsUseCase).execute(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("the caller's id travels with the query, so ownership is checked before the read")
    void shouldAuthorizeBeforeReading() throws Exception {
        when(queryPortfolioSnapshotsUseCase.execute(any())).thenReturn(List.of());

        json(tools.queryPortfolioSnapshots(SIM_ID, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty()));

        assertThat(capturedQuery().requestingUserId())
                .as("a null requesting user here means the snapshots are fetched unchecked")
                .isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("snapshots come back with a count")
    void shouldReturnSnapshots() throws Exception {
        when(queryPortfolioSnapshotsUseCase.execute(any()))
                .thenReturn(List.of(snapshot(Instant.parse("2026-06-01T00:00:00Z"))));

        JsonNode result = json(tools.queryPortfolioSnapshots(SIM_ID, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty()));

        assertThat(result.get("count").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("resolution defaults to daily, and 'all' is passed through")
    void shouldHandleResolution() {
        when(queryPortfolioSnapshotsUseCase.execute(any())).thenReturn(List.of());

        tools.queryPortfolioSnapshots(SIM_ID, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
        assertThat(capturedQuery().resolution()).isEqualTo("daily");

        org.mockito.Mockito.reset(queryPortfolioSnapshotsUseCase);
        when(queryPortfolioSnapshotsUseCase.execute(any())).thenReturn(List.of());

        tools.queryPortfolioSnapshots(SIM_ID, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of("all"));
        assertThat(capturedQuery().resolution()).isEqualTo("all");
    }

    @Test
    @DisplayName("an unrecognised resolution errors rather than silently falling back to daily")
    void shouldRejectAnUnknownResolution() {
        ToolResponse response = tools.queryPortfolioSnapshots(SIM_ID, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of("hourly"));

        assertThat(response.isError()).isTrue();
        verify(queryPortfolioSnapshotsUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("the limit is capped at 200 however large a number is asked for")
    void shouldCapTheLimit() {
        when(queryPortfolioSnapshotsUseCase.execute(any())).thenReturn(List.of());

        tools.queryPortfolioSnapshots(SIM_ID, Optional.empty(), Optional.empty(),
                Optional.of(100000), Optional.empty());

        // Fetches one extra to detect a further page, so the cap shows as 201.
        assertThat(capturedQuery().limit()).isEqualTo(201);
    }

    @Test
    @DisplayName("hasMore is reported when another page exists, and the extra row is not returned")
    void shouldPaginate() throws Exception {
        List<PortfolioSnapshot> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(snapshot(Instant.parse("2026-06-01T00:00:00Z").plusSeconds(i * 3600L)));
        }
        when(queryPortfolioSnapshotsUseCase.execute(any())).thenReturn(six);

        JsonNode result = json(tools.queryPortfolioSnapshots(SIM_ID, Optional.empty(),
                Optional.empty(), Optional.of(5), Optional.empty()));

        assertThat(result.get("count").asInt()).isEqualTo(5);
        assertThat(result.get("hasMore").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a malformed simulation id errors without reaching the use case")
    void shouldRejectAMalformedSimulationId() {
        ToolResponse response = tools.queryPortfolioSnapshots("not-a-uuid", Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(response.isError()).isTrue();
        verify(queryPortfolioSnapshotsUseCase, never()).execute(any());
    }
}
