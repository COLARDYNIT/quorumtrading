package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.command.CreateMarketResearchCommand;
import com.quorumtrading.application.usecase.dto.MarketResearchDetails;
import com.quorumtrading.application.usecase.dto.MarketResearchSummary;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.*;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class MarketResearchToolsTest {

    @Inject
    MarketResearchTools marketResearchTools;

    @InjectMock
    ListMarketResearchUseCase listMarketResearchUseCase;

    @InjectMock
    GetMarketResearchDetailsUseCase getMarketResearchDetailsUseCase;

    @InjectMock
    CreateMarketResearchUseCase createMarketResearchUseCase;

    @InjectMock
    ExecuteMarketResearchUseCase executeMarketResearchUseCase;

    @InjectMock
    PauseMarketResearchUseCase pauseMarketResearchUseCase;

    @InjectMock
    ResumeMarketResearchUseCase resumeMarketResearchUseCase;

    @InjectMock
    DeleteMarketResearchUseCase deleteMarketResearchUseCase;

    @InjectMock
    ResearchExecutionRepository researchExecutionRepository;

    @InjectMock
    MarketResearchRepository marketResearchRepository;

    @InjectMock
    McpUserContext mcpUserContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID RESEARCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EXECUTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void listMarketResearch_returnsSummaries() throws Exception {
        MarketResearch research = createTestResearch();
        MarketResearchSummary summary = new MarketResearchSummary(research, 3);
        when(listMarketResearchUseCase.execute(any())).thenReturn(List.of(summary));
        when(researchExecutionRepository.findByMarketResearchId(any())).thenReturn(List.of());
        when(researchExecutionRepository.findLatestByMarketResearchId(any())).thenReturn(Optional.empty());

        ToolResponse response = marketResearchTools.listMarketResearch();

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(1);
        assertThat(json.get("hasMore").asBoolean()).isFalse();

        JsonNode item = json.get("items").get(0);
        assertThat(item.get("id").asText()).isNotBlank();
        assertThat(item.get("name").asText()).isEqualTo("Test Research");
        assertThat(item.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(item.get("strategyCount").asInt()).isEqualTo(3);
        assertThat(item.has("instructions")).isTrue();
        assertThat(item.get("totalExecutions").asInt()).isEqualTo(0);
    }

    @Test
    void getMarketResearchDetails_returnsDetailsWithExecutions() throws Exception {
        MarketResearch research = createTestResearch();
        UUID researchId = research.getId();
        MarketResearchDetails details = new MarketResearchDetails(research, List.of(), Optional.empty());
        when(getMarketResearchDetailsUseCase.execute(any(UUID.class), any())).thenReturn(details);

        ToolResponse response = marketResearchTools.getMarketResearchDetails(
            researchId.toString(), Optional.of(true), Optional.empty(), Optional.empty());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("research")).isNotNull();
        assertThat(json.get("research").get("id").asText()).isEqualTo(researchId.toString());
        assertThat(json.get("totalExecutions").asInt()).isEqualTo(0);
        assertThat(json.get("executions").size()).isEqualTo(0);
    }

    @Test
    void createMarketResearch_returnsIdAndMessage() throws Exception {
        MarketResearch research = createTestResearch();
        when(mcpUserContext.getUserId()).thenReturn(new UserId(OWNER_ID));
        when(createMarketResearchUseCase.execute(any(CreateMarketResearchCommand.class))).thenReturn(research);

        ToolResponse response = marketResearchTools.createMarketResearch(
            "New Research",
            "## Instructions\nAnalyze market",
            Optional.of("BTC:CRYPTO,AAPL:STOCK"),
            7,
            "DAYS",
            Optional.of(true),
            Optional.of(90),
            Optional.empty(),
            Optional.empty()
        );

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isNotBlank();
        assertThat(json.get("message").asText()).contains("created");
        assertThat(json.get("message").asText()).contains("targets:");

        verify(createMarketResearchUseCase).execute(any(CreateMarketResearchCommand.class));
    }

    @Test
    void createMarketResearch_returnsAuthErrorWhenNoToken() {
        when(mcpUserContext.getUserId()).thenThrow(new SecurityException("Missing or invalid Authorization header"));

        ToolResponse response = marketResearchTools.createMarketResearch(
            "New Research",
            "## Instructions",
            Optional.empty(),
            7,
            "DAYS",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Authentication required");
        assertThat(errorText).contains("quorumtrading.com/settings/api-tokens");
        assertThat(errorText).contains("claude mcp add");
    }

    @Test
    void executeMarketResearch_returnsExecutionId() throws Exception {
        MarketResearch research = createTestResearch();
        when(marketResearchRepository.findById(any())).thenReturn(Optional.of(research));
        when(executeMarketResearchUseCase.execute(any(UUID.class), any())).thenReturn(EXECUTION_ID);

        ToolResponse response = marketResearchTools.executeMarketResearch(RESEARCH_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isEqualTo(EXECUTION_ID.toString());
        assertThat(json.get("message").asText()).contains("execution");
        assertThat(json.get("message").asText()).contains("Test Research");
    }

    @Test
    void pauseMarketResearch_returnsConfirmation() throws Exception {
        MarketResearch research = createTestResearch();
        when(marketResearchRepository.findById(any())).thenReturn(Optional.of(research));
        doNothing().when(pauseMarketResearchUseCase).execute(any(UUID.class), any());

        ToolResponse response = marketResearchTools.pauseMarketResearch(RESEARCH_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isEqualTo(RESEARCH_ID.toString());
        assertThat(json.get("message").asText()).contains("paused");
        assertThat(json.get("message").asText()).contains("Test Research");
        verify(pauseMarketResearchUseCase).execute(any(UUID.class), any());
    }

    @Test
    void resumeMarketResearch_returnsConfirmation() throws Exception {
        MarketResearch research = createTestResearch();
        when(marketResearchRepository.findById(any())).thenReturn(Optional.of(research));
        doNothing().when(resumeMarketResearchUseCase).execute(any(UUID.class), any());

        ToolResponse response = marketResearchTools.resumeMarketResearch(RESEARCH_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isEqualTo(RESEARCH_ID.toString());
        assertThat(json.get("message").asText()).contains("resumed");
        assertThat(json.get("message").asText()).contains("Test Research");
        verify(resumeMarketResearchUseCase).execute(any(UUID.class), any());
    }

    @Test
    void deleteMarketResearch_returnsConfirmation() throws Exception {
        doNothing().when(deleteMarketResearchUseCase).execute(any(UUID.class), any());

        ToolResponse response = marketResearchTools.deleteMarketResearch(RESEARCH_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isEqualTo(RESEARCH_ID.toString());
        assertThat(json.get("message").asText()).contains("deleted");

        verify(deleteMarketResearchUseCase).execute(any(UUID.class), any());
    }

    @Test
    void executeMarketResearch_returnsErrorForInvalidUuid() {
        ToolResponse response = marketResearchTools.executeMarketResearch("not-a-uuid");

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Invalid UUID string");
    }

    private MarketResearch createTestResearch() {
        return new MarketResearch(
            "Test Research",
            "## Instructions\nAnalyze market trends",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS),
            false,
            null,
            new UserId(OWNER_ID)
        );
    }
}
