package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.GetConsensusWithAgentsUseCase;
import com.quorumtrading.application.usecase.QueryConsensusDecisionsUseCase;
import com.quorumtrading.application.usecase.command.GetConsensusWithAgentsCommand;
import com.quorumtrading.application.usecase.dto.ConsensusWithAgentsResponse;
import com.quorumtrading.application.usecase.query.QueryConsensusDecisionsQuery;
import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.LlmParseFailure;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.valueobject.Percentage;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class ConsensusToolsTest {

    @Inject
    ConsensusTools consensusTools;

    @InjectMock
    GetConsensusWithAgentsUseCase getConsensusWithAgentsUseCase;

    @InjectMock
    QueryConsensusDecisionsUseCase queryConsensusDecisionsUseCase;

    @InjectMock
    McpUserContext mcpUserContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID SIM_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void getConsensusWithAgents_returnsAgentBreakdown() throws Exception {
        ConsensusDecision cd = createTestConsensusDecision();
        LLMDecision ld = createTestLLMDecision();
        ConsensusWithAgentsResponse agentResponse = new ConsensusWithAgentsResponse(cd, List.of(ld));

        when(getConsensusWithAgentsUseCase.execute(any(GetConsensusWithAgentsCommand.class)))
            .thenReturn(List.of(agentResponse));

        ToolResponse response = consensusTools.getConsensusWithAgents(
            SIM_ID.toString(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(1);
        assertThat(json.get("hasMore").asBoolean()).isFalse();

        JsonNode item = json.get("items").get(0);
        assertThat(item.get("consensus")).isNotNull();
        assertThat(item.get("consensus").get("consensusMethod").asText()).isEqualTo("MAJORITY");
        assertThat(item.get("agentDecisions")).isNotNull();
        assertThat(item.get("agentDecisions").size()).isEqualTo(1);
        assertThat(item.get("agentDecisions").get(0).get("role").asText()).isEqualTo("TECHNICAL");
    }

    @Test
    void queryConsensusDecisions_returnsPaginated() throws Exception {
        ConsensusDecision cd = createTestConsensusDecision();
        when(queryConsensusDecisionsUseCase.execute(any(QueryConsensusDecisionsQuery.class)))
            .thenReturn(List.of(cd));

        ToolResponse response = consensusTools.queryConsensusDecisions(
            SIM_ID.toString(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(1);
        assertThat(json.get("hasMore").asBoolean()).isFalse();
        assertThat(json.get("items").size()).isEqualTo(1);
        assertThat(json.get("items").get(0).get("consensusMethod").asText()).isEqualTo("MAJORITY");
    }

    @Test
    void getConsensusWithAgents_onlyParseFailuresReturnsFailures() throws Exception {
        ConsensusDecision cd = createTestConsensusDecision();
        LLMDecision ld = createTestLLMDecision();
        LlmParseFailure failure = new LlmParseFailure(
            new SimulationId(SIM_ID),
            Instant.parse("2025-03-15T10:00:00Z"),
            "BTC",
            "SENTIMENT",
            "qwen/qwen3-next",
            "qwen/qwen3-next",
            0.7,
            1500,
            "prompt text",
            "garbled LLM output",
            "Failed to parse JSON",
            "com.quorumtrading.ParseException"
        );
        ConsensusWithAgentsResponse agentResponse = new ConsensusWithAgentsResponse(cd, List.of(ld), List.of(failure));

        when(getConsensusWithAgentsUseCase.execute(any(GetConsensusWithAgentsCommand.class)))
            .thenReturn(List.of(agentResponse));

        ToolResponse response = consensusTools.getConsensusWithAgents(
            SIM_ID.toString(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(true)
        );

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        JsonNode item = json.get("items").get(0);
        assertThat(item.get("parseFailures")).isNotNull();
        assertThat(item.get("parseFailures").size()).isEqualTo(1);
        JsonNode pf = item.get("parseFailures").get(0);
        assertThat(pf.get("agentRole").asText()).isEqualTo("SENTIMENT");
        assertThat(pf.get("errorMessage").asText()).isEqualTo("Failed to parse JSON");
        assertThat(pf.get("rawResponse").asText()).isEqualTo("garbled LLM output");
    }

    @Test
    void getConsensusWithAgents_returnsErrorForInvalidUuid() {
        ToolResponse response = consensusTools.getConsensusWithAgents(
            "not-a-uuid",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Invalid UUID string");
    }

    private ConsensusDecision createTestConsensusDecision() {
        ConsensusDecision cd = new ConsensusDecision(
            new SimulationId(SIM_ID),
            Instant.parse("2025-03-15T10:00:00Z"),
            ConsensusMethod.MAJORITY,
            ConsensusActionParser.parse("{\"action\":\"BUY\",\"ticker\":\"BTC\"}")
        );
        cd.setId(1L);
        cd.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
        cd.setInvestmentType(InvestmentType.CRYPTO);
        return cd;
    }

    private LLMDecision createTestLLMDecision() {
        LLMDecision ld = new LLMDecision(
            new SimulationId(SIM_ID),
            Instant.parse("2025-03-15T10:00:00Z"),
            "agent-technical-1",
            "TECHNICAL",
            "qwen/qwen3-next"
        );
        ld.setId(100L);
        ld.setTicker("BTC");
        ld.setRecommendation(RecommendationType.BUY);
        ld.setConfidenceScore(new Percentage(new BigDecimal("0.85")));
        ld.setReasoning("{\"analysis\":\"RSI oversold, MACD bullish crossover\"}");
        ld.setRecommendedAction("{\"action\":\"BUY\",\"quantity\":0.5}");
        return ld;
    }
}
