package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.GetHistoricalEventUseCase;
import com.quorumtrading.application.usecase.ListHistoricalEventsUseCase;
import com.quorumtrading.application.usecase.query.ListHistoricalEventsQuery;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class HistoricalEventToolsTest {

    @Inject
    HistoricalEventTools historicalEventTools;

    @InjectMock
    ListHistoricalEventsUseCase listHistoricalEventsUseCase;

    @InjectMock
    GetHistoricalEventUseCase getHistoricalEventUseCase;

    @InjectMock
    McpUserContext mcpUserContext;

    /**
     * The unfiltered call — which is the point of a list tool, and which this test has always
     * exercised. It passed the whole time while being <em>unreachable over MCP</em>: the three
     * filters were declared as bare {@code String}, so the framework published them as required
     * and rejected the call with "Missing required argument: category" before the method ran.
     * A green unit test on a path the published schema forbids is how that survived.
     */
    @Test
    void listHistoricalEvents_withNoFilters_executesUseCase() {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(UUID.randomUUID()));
        when(listHistoricalEventsUseCase.execute(any(ListHistoricalEventsQuery.class))).thenReturn(List.of());

        ToolResponse response = historicalEventTools.list_historical_events(
                Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(response.isError()).isFalse();
        verify(listHistoricalEventsUseCase).execute(any(ListHistoricalEventsQuery.class));
    }

    /** A blank filter string must be treated as absent, not parsed as an enum value. */
    @Test
    void listHistoricalEvents_withBlankFilters_treatsThemAsAbsent() {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(UUID.randomUUID()));
        when(listHistoricalEventsUseCase.execute(any(ListHistoricalEventsQuery.class))).thenReturn(List.of());

        ToolResponse response = historicalEventTools.list_historical_events(
                Optional.of("  "), Optional.of(""), Optional.empty());

        assertThat(response.isError()).isFalse();
    }

    @Test
    void listHistoricalEvents_withoutValidToken_returnsAuthError() {
        when(mcpUserContext.getUserId()).thenThrow(new SecurityException("Missing or invalid Authorization header"));

        ToolResponse response = historicalEventTools.list_historical_events(Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(response.isError()).isTrue();
        assertThat(response.firstContent().asText().text()).contains("Authentication required");
        verifyNoInteractions(listHistoricalEventsUseCase);
    }

    @Test
    void getHistoricalEvent_withoutValidToken_returnsAuthError() {
        when(mcpUserContext.getUserId()).thenThrow(new SecurityException("Missing or invalid Authorization header"));

        ToolResponse response = historicalEventTools.get_historical_event("covid-crash");

        assertThat(response.isError()).isTrue();
        assertThat(response.firstContent().asText().text()).contains("Authentication required");
        verifyNoInteractions(getHistoricalEventUseCase);
    }
}
