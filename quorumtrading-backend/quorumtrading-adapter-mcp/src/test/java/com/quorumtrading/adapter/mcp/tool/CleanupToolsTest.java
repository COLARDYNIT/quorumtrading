package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.PruneUnusedUseCase;
import com.quorumtrading.application.usecase.command.PruneUnusedCommand;
import com.quorumtrading.application.usecase.result.PruneUnusedResult;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code prune_unused}, which had no test — and is the only destructive tool with no undo.
 *
 * <p>The dog-fooding sweep declined to run it: an agent could not tell from the description what it
 * would delete, and there is no dry run. That makes the flags the whole safety story, so what
 * matters here is that they are honoured exactly — {@code removeStrategies=false} must delete no
 * strategies, and an omitted flag must mean the documented default rather than whatever the
 * boxing happens to produce.</p>
 */
@QuarkusTest
@DisplayName("CleanupTools")
class CleanupToolsTest {

    @Inject CleanupTools tools;

    @InjectMock PruneUnusedUseCase pruneUnusedUseCase;
    @InjectMock McpUserContext mcpUserContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UserId USER_ID = UserId.generate();

    @BeforeEach
    void setUp() {
        when(mcpUserContext.getUserId()).thenReturn(USER_ID);
        when(pruneUnusedUseCase.execute(any(PruneUnusedCommand.class)))
                .thenReturn(new PruneUnusedResult(2, 1, java.util.List.of("Draft A", "Draft B"), java.util.List.of("R1"), false));
    }

    private JsonNode json(ToolResponse response) throws Exception {
        assertThat(response.isError()).isFalse();
        return objectMapper.readTree(response.firstContent().asText().text());
    }

    private PruneUnusedCommand capturedCommand() {
        ArgumentCaptor<PruneUnusedCommand> captor = ArgumentCaptor.forClass(PruneUnusedCommand.class);
        verify(pruneUnusedUseCase).execute(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("reports exactly what it deleted, so the caller can see the damage")
    void shouldReportWhatWasDeleted() throws Exception {
        JsonNode result = json(tools.pruneUnused(null, null, true));

        assertThat(result.get("deletedStrategies").asInt()).isEqualTo(2);
        assertThat(result.get("deletedResearch").asInt()).isEqualTo(1);
        assertThat(result.get("message").asText()).contains("2 unused strategies");
    }

    @Test
    @DisplayName("both flags default to true when omitted — the documented behaviour")
    void shouldDefaultBothFlagsToTrue() {
        tools.pruneUnused(null, null, true);

        assertThat(capturedCommand().removeUnusedStrategies()).isTrue();
        assertThat(capturedCommand().removeUnusedResearch()).isTrue();
    }

    @Test
    @DisplayName("removeStrategies=false spares strategies")
    void shouldHonourStrategiesOptOut() {
        tools.pruneUnused(false, true, true);

        assertThat(capturedCommand().removeUnusedStrategies())
                .as("a false flag on a destructive tool must be obeyed exactly")
                .isFalse();
        assertThat(capturedCommand().removeUnusedResearch()).isTrue();
    }

    @Test
    @DisplayName("removeResearch=false spares research")
    void shouldHonourResearchOptOut() {
        tools.pruneUnused(true, false, true);

        assertThat(capturedCommand().removeUnusedStrategies()).isTrue();
        assertThat(capturedCommand().removeUnusedResearch()).isFalse();
    }

    @Test
    @DisplayName("both false deletes nothing")
    void shouldDeleteNothingWhenBothOptOut() {
        tools.pruneUnused(false, false, true);

        assertThat(capturedCommand().removeUnusedStrategies()).isFalse();
        assertThat(capturedCommand().removeUnusedResearch()).isFalse();
    }

    @Test
    @DisplayName("the prune is scoped to the calling user, never the whole instance")
    void shouldScopeToTheCaller() {
        tools.pruneUnused(null, null, true);

        assertThat(capturedCommand().userId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("a no-argument call previews and deletes nothing")
    void shouldPreviewByDefault() throws Exception {
        // This tool used to delete on an empty call: both flags defaulted to true and there was no
        // dry run, so the emptiest possible request was the most destructive one. "Unused" means
        // zero simulations, which every strategy is until its first backtest, so a bare call could
        // wipe a batch of drafts the user had just written.
        when(pruneUnusedUseCase.execute(any()))
                .thenReturn(new PruneUnusedResult(2, 1, java.util.List.of("Draft A", "Draft B"),
                        java.util.List.of("R1"), true));

        JsonNode result = json(tools.pruneUnused(null, null, null));

        assertThat(capturedCommand().dryRun())
                .as("an unconfirmed prune must not delete anything")
                .isTrue();
        assertThat(result.get("dryRun").asBoolean()).isTrue();
        assertThat(result.get("wouldDeleteStrategies").asInt()).isEqualTo(2);
        assertThat(result.get("message").asText()).contains("nothing was deleted");
    }

    @Test
    @DisplayName("confirm=false is treated as a preview, not as a confirmation")
    void shouldTreatExplicitFalseAsPreview() {
        tools.pruneUnused(null, null, false);

        assertThat(capturedCommand().dryRun()).isTrue();
    }

    @Test
    @DisplayName("the preview names what would go, so the caller can recognise their own work")
    void shouldNamePreviewedItems() throws Exception {
        when(pruneUnusedUseCase.execute(any()))
                .thenReturn(new PruneUnusedResult(2, 0, java.util.List.of("Draft A", "Draft B"),
                        java.util.List.of(), true));

        JsonNode result = json(tools.pruneUnused(null, null, null));

        assertThat(result.get("strategyNames").toString()).contains("Draft A").contains("Draft B");
    }
}
