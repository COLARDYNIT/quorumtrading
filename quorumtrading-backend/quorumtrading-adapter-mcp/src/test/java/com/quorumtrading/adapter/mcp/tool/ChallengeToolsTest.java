package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.port.out.ChallengeRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.BuildChallengeLeaderboardUseCase;
import com.quorumtrading.application.usecase.JoinChallengeUseCase;
import com.quorumtrading.application.usecase.command.JoinChallengeCommand;
import com.quorumtrading.application.usecase.dto.ChallengeLeaderboard;
import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The three challenge MCP tools, which had no test.
 *
 * <p>{@code join_challenge} is the one that matters: it creates a simulation on the caller's
 * behalf and, by its own description, <em>replaces</em> an existing entry. An agent calling it
 * with a mistyped id must not have that silently attributed to someone else's account or a
 * different challenge, so the ids and the calling user have to arrive intact.</p>
 */
@QuarkusTest
@DisplayName("ChallengeTools")
class ChallengeToolsTest {

    @Inject ChallengeTools tools;

    @InjectMock ChallengeRepository challengeRepository;
    @InjectMock SimulationRepository simulationRepository;
    @InjectMock JoinChallengeUseCase joinChallengeUseCase;
    @InjectMock BuildChallengeLeaderboardUseCase buildChallengeLeaderboardUseCase;
    @InjectMock McpUserContext userContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UserId USER_ID = UserId.generate();
    private static final UUID CHALLENGE_ID = UUID.randomUUID();
    private static final UUID STRATEGY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(userContext.getUserId()).thenReturn(USER_ID);
    }

    private JsonNode json(ToolResponse response) throws Exception {
        assertThat(response.isError()).isFalse();
        return objectMapper.readTree(response.firstContent().asText().text());
    }

    @Test
    @DisplayName("an empty board lists cleanly rather than erroring")
    void shouldListAnEmptyBoard() throws Exception {
        when(challengeRepository.findAllSorted(anyInt())).thenReturn(List.of());

        JsonNode result = json(tools.listChallenges(Optional.empty(), Optional.empty()));

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("a listed challenge carries its web-app page")
    void shouldListAChallengeWithItsPage() throws Exception {
        when(challengeRepository.findAllSorted(anyInt())).thenReturn(List.of(challenge()));
        when(simulationRepository.findByChallengeId(any(ChallengeId.class))).thenReturn(List.of());

        JsonNode result = json(tools.listChallenges(Optional.empty(), Optional.empty()));

        JsonNode row = result.get("items").get(0);
        assertThat(row.get("id").asText()).isEqualTo(CHALLENGE_ID.toString());
        assertThat(row.get("uiUrl").asText())
            .isEqualTo("http://localhost:3000/challenges/" + CHALLENGE_ID);
    }

    @Test
    @DisplayName("challenge detail carries its web-app page")
    void shouldGetAChallengeWithItsPage() throws Exception {
        when(challengeRepository.findById(any(ChallengeId.class))).thenReturn(Optional.of(challenge()));
        when(buildChallengeLeaderboardUseCase.execute(any()))
            .thenReturn(new ChallengeLeaderboard(0, List.of()));

        JsonNode result = json(tools.getChallenge(CHALLENGE_ID.toString()));

        assertThat(result.get("id").asText()).isEqualTo(CHALLENGE_ID.toString());
        assertThat(result.get("uiUrl").asText())
            .isEqualTo("http://localhost:3000/challenges/" + CHALLENGE_ID);
    }

    private static Challenge challenge() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        return new Challenge(new ChallengeId(CHALLENGE_ID), "Test Challenge", "desc",
            new Symbol("BTC", InvestmentType.CRYPTO), Money.usd(BigDecimal.valueOf(1000)),
            start, start.plus(365, ChronoUnit.DAYS),
            start.minus(7, ChronoUnit.DAYS), start,
            start.minus(7, ChronoUnit.DAYS), USER_ID, true);
    }

    @Test
    @DisplayName("the result count is capped at 50 however large a limit is asked for")
    void shouldCapTheLimit() {
        when(challengeRepository.findAllSorted(anyInt())).thenReturn(List.of());

        tools.listChallenges(Optional.empty(), Optional.of(10_000));

        verify(challengeRepository).findAllSorted(50);
    }

    @Test
    @DisplayName("omitting the limit uses the documented default of 20")
    void shouldDefaultTheLimit() {
        when(challengeRepository.findAllSorted(anyInt())).thenReturn(List.of());

        tools.listChallenges(Optional.empty(), Optional.empty());

        verify(challengeRepository).findAllSorted(20);
    }

    @Test
    @DisplayName("an unknown challenge is an error, not an empty success")
    void shouldErrorOnUnknownChallenge() {
        when(challengeRepository.findById(any(ChallengeId.class))).thenReturn(Optional.empty());

        ToolResponse response = tools.getChallenge(CHALLENGE_ID.toString());

        assertThat(response.isError()).isTrue();
    }

    @Test
    @DisplayName("a malformed challenge id errors without touching the repository")
    void shouldRejectAMalformedChallengeId() {
        ToolResponse response = tools.getChallenge("not-a-uuid");

        assertThat(response.isError()).isTrue();
        verify(challengeRepository, never()).findById(any());
    }

    @Test
    @DisplayName("joining carries both ids and the calling user, and reports the simulation created")
    void shouldJoinAChallenge() throws Exception {
        SimulationId created = new SimulationId(UUID.randomUUID());
        when(joinChallengeUseCase.execute(any(JoinChallengeCommand.class))).thenReturn(created);

        JsonNode result = json(tools.joinChallenge(CHALLENGE_ID.toString(), STRATEGY_ID.toString()));

        assertThat(result.get("simulationId").asText()).isEqualTo(created.value().toString());
        assertThat(result.get("challengeId").asText()).isEqualTo(CHALLENGE_ID.toString());
        assertThat(result.get("uiUrl").asText())
            .isEqualTo("http://localhost:3000/simulations/" + created.value());

        ArgumentCaptor<JoinChallengeCommand> command = ArgumentCaptor.forClass(JoinChallengeCommand.class);
        verify(joinChallengeUseCase).execute(command.capture());
        assertThat(command.getValue().challengeId().value()).isEqualTo(CHALLENGE_ID);
        assertThat(command.getValue().strategyId()).isEqualTo(new StrategyId(STRATEGY_ID));
        assertThat(command.getValue().requestingUserId())
                .as("a challenge entry must be attributed to the caller, never to anyone else")
                .isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("a malformed strategy id is refused before a simulation is created")
    void shouldRejectAMalformedStrategyId() {
        ToolResponse response = tools.joinChallenge(CHALLENGE_ID.toString(), "not-a-uuid");

        assertThat(response.isError()).isTrue();
        verify(joinChallengeUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("a refused join surfaces as an error rather than a fabricated success")
    void shouldSurfaceARefusedJoin() {
        when(joinChallengeUseCase.execute(any(JoinChallengeCommand.class)))
                .thenThrow(new IllegalStateException("Challenge has already ended"));

        ToolResponse response = tools.joinChallenge(CHALLENGE_ID.toString(), STRATEGY_ID.toString());

        assertThat(response.isError()).isTrue();
        assertThat(response.firstContent().asText().text()).contains("already ended");
    }
}
