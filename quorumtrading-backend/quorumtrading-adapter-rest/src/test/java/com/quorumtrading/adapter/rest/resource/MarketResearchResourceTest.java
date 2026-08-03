package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.command.CreateMarketResearchCommand;
import com.quorumtrading.application.usecase.dto.MarketResearchDetails;
import com.quorumtrading.application.usecase.dto.MarketResearchSummary;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.quorumtrading.adapter.rest.resource.TestConstants.TEST_USER_ID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

/**
 * Integration tests for MarketResearchResource.
 * Uses mocked use cases to test REST layer in isolation.
 */
@QuarkusTest
class MarketResearchResourceTest {

    private static final String TEST_EMAIL = "test@example.com";

    @InjectMock
    CreateMarketResearchUseCase createMarketResearchUseCase;

    @InjectMock
    GetMarketResearchDetailsUseCase getMarketResearchDetailsUseCase;

    @InjectMock
    PauseMarketResearchUseCase pauseMarketResearchUseCase;

    @InjectMock
    ResumeMarketResearchUseCase resumeMarketResearchUseCase;

    @InjectMock
    ExecuteMarketResearchUseCase executeMarketResearchUseCase;

    @InjectMock
    ListMarketResearchUseCase listMarketResearchUseCase;

    @InjectMock
    DeleteMarketResearchUseCase deleteMarketResearchUseCase;

    @InjectMock
    DeleteResearchExecutionUseCase deleteResearchExecutionUseCase;

    @InjectMock
    ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    @BeforeEach
    void setUp() {
        // Mock user resolution to return test user ID
        when(resolveCurrentUserUseCase.execute(TEST_EMAIL)).thenReturn(TEST_USER_ID);
    }

    @Test
    void shouldCreateMarketResearch() {
        // Given - mock research entity
        MarketResearch mockResearch = createMockMarketResearch();
        when(createMarketResearchUseCase.execute(any(CreateMarketResearchCommand.class)))
            .thenReturn(mockResearch);

        String requestBody = """
            {
              "name": "Bitcoin Weekly Scanner",
              "description": "# Research Instructions\\n\\nAnalyze Bitcoin",
              "targetAssets": ["BTC"],
              "interval": {
                "amount": 7,
                "unit": "DAYS"
              },
              "autoBacktest": true,
              "backtestConfig": {
                "backtestLength": "P30D",
                "periodStart": null,
                "periodEnd": null
              }
            }
            """;

        // When/Then - POST creates research
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post("/api/v1/market-research")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("Bitcoin Weekly Scanner"))
            .body("status", equalTo("ACTIVE"))
            .body("autoBacktest", equalTo(true))
            .body("interval.amount", equalTo(7))
            .body("interval.unit", equalTo("DAYS"))
            .body("backtestConfig.backtestLength", equalTo("PT720H"));

        verify(createMarketResearchUseCase, times(1)).execute(any(CreateMarketResearchCommand.class));
    }

    @Test
    void shouldParseBareTargetAssetAsStock() {
        // Given - the shared "TICKER[:TYPE]" convention: bare ticker → STOCK default
        MarketResearch mockResearch = createMockMarketResearch();
        when(createMarketResearchUseCase.execute(any(CreateMarketResearchCommand.class)))
            .thenReturn(mockResearch);

        String requestBody = """
            {
              "name": "Stock Scanner",
              "description": "# Research\\n\\nAnalyze stocks",
              "targetAssets": ["AAPL"],
              "interval": { "amount": 7, "unit": "DAYS" },
              "autoBacktest": false
            }
            """;

        // When
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post("/api/v1/market-research")
            .then()
            .statusCode(201);

        // Then - command carries a STOCK symbol, not the old CRYPTO default
        ArgumentCaptor<CreateMarketResearchCommand> captor =
            ArgumentCaptor.forClass(CreateMarketResearchCommand.class);
        verify(createMarketResearchUseCase).execute(captor.capture());
        assertEquals(
            Set.of(new Symbol("AAPL", InvestmentType.STOCK)),
            captor.getValue().targetAssets());
    }

    @Test
    void shouldParseExplicitCryptoTypeSuffix() {
        // Given - "TICKER:TYPE" form selects the type explicitly
        MarketResearch mockResearch = createMockMarketResearch();
        when(createMarketResearchUseCase.execute(any(CreateMarketResearchCommand.class)))
            .thenReturn(mockResearch);

        String requestBody = """
            {
              "name": "Crypto Scanner",
              "description": "# Research\\n\\nAnalyze crypto",
              "targetAssets": ["BTC:CRYPTO", "AAPL:STOCK"],
              "interval": { "amount": 7, "unit": "DAYS" },
              "autoBacktest": false
            }
            """;

        // When
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post("/api/v1/market-research")
            .then()
            .statusCode(201);

        // Then
        ArgumentCaptor<CreateMarketResearchCommand> captor =
            ArgumentCaptor.forClass(CreateMarketResearchCommand.class);
        verify(createMarketResearchUseCase).execute(captor.capture());
        assertEquals(
            Set.of(
                new Symbol("BTC", InvestmentType.CRYPTO),
                new Symbol("AAPL", InvestmentType.STOCK)),
            captor.getValue().targetAssets());
    }

    @Test
    void shouldRejectInvalidAssetTypeSuffixWith400ProblemDetails() {
        // Given - a type suffix that is not a valid InvestmentType
        String requestBody = """
            {
              "name": "Broken Scanner",
              "description": "# Research",
              "targetAssets": ["AAPL:EQUITY"],
              "interval": { "amount": 7, "unit": "DAYS" },
              "autoBacktest": false
            }
            """;

        // When/Then - 400 with ProblemDetails body naming the bad type and valid values
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post("/api/v1/market-research")
            .then()
            .statusCode(400)
            .body("title", equalTo("Validation Error"))
            .body("status", equalTo(400))
            .body("detail", containsString("EQUITY"))
            .body("detail", containsString("STOCK"))
            .body("detail", containsString("CRYPTO"));

        verify(createMarketResearchUseCase, never()).execute(any());
    }

    @Test
    void shouldSerializeCryptoTargetAssetWithTypeSuffix() {
        // Given - a research whose target asset is crypto (mock uses BTC:CRYPTO)
        MarketResearch mockResearch = createMockMarketResearch();
        when(createMarketResearchUseCase.execute(any(CreateMarketResearchCommand.class)))
            .thenReturn(mockResearch);

        String requestBody = """
            {
              "name": "Bitcoin Weekly Scanner",
              "description": "# Research",
              "targetAssets": ["BTC:CRYPTO"],
              "interval": { "amount": 7, "unit": "DAYS" },
              "autoBacktest": false
            }
            """;

        // When/Then - response emits "BTC:CRYPTO" so an edit round-trip keeps the type
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post("/api/v1/market-research")
            .then()
            .statusCode(201)
            .body("targetAssets", org.hamcrest.Matchers.contains("BTC:CRYPTO"));
    }

    @Test
    void shouldRejectInvalidCreateRequest() {
        // Given - invalid request (missing name)
        String requestBody = """
            {
              "description": "# Research",
              "targetAssets": ["BTC"],
              "interval": {
                "amount": 7,
                "unit": "DAYS"
              },
              "autoBacktest": false
            }
            """;

        // When/Then - POST returns 400 Bad Request
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post("/api/v1/market-research")
            .then()
            .statusCode(400);

        verify(createMarketResearchUseCase, never()).execute(any());
    }

    @Test
    void shouldListAllMarketResearch() {
        // Given - mock use case returns list with summaries
        MarketResearch mockResearch = createMockMarketResearch();
        MarketResearchSummary summary = new MarketResearchSummary(mockResearch, 2);
        when(listMarketResearchUseCase.execute(any())).thenReturn(List.of(summary));

        // When/Then - GET returns list
        given()
            .cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/market-research")
            .then()
            .statusCode(200)
            .body("$.size()", equalTo(1))
            .body("[0].id", notNullValue())
            .body("[0].name", equalTo("Bitcoin Weekly Scanner"))
            .body("[0].status", equalTo("ACTIVE"));

        verify(listMarketResearchUseCase, times(1)).execute(any());
    }

    @Test
    void shouldReturnEmptyListWhenNoResearch() {
        // Given - mock returns empty list
        when(listMarketResearchUseCase.execute(any())).thenReturn(Collections.emptyList());

        // When/Then - GET returns empty list
        given()
            .cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/market-research")
            .then()
            .statusCode(200)
            .body("$.size()", equalTo(0));
    }

    @Test
    void shouldGetMarketResearchDetails() {
        // Given - mock details DTO
        MarketResearch mockResearch = createMockMarketResearch();
        ResearchExecution mockExecution = createMockExecution(mockResearch.getId());
        MarketResearchDetails mockDetails = new MarketResearchDetails(
            mockResearch,
            List.of(mockExecution),
            Optional.of(mockExecution)
        );
        when(getMarketResearchDetailsUseCase.execute(any(UUID.class), any()))
            .thenReturn(mockDetails);

        // When/Then - GET returns details
        given()
            .cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/market-research/" + mockResearch.getId())
            .then()
            .statusCode(200)
            .body("research.id", equalTo(mockResearch.getId().toString()))
            .body("research.name", equalTo("Bitcoin Weekly Scanner"))
            .body("executions.size()", equalTo(1))
            .body("latestExecution", notNullValue())
            .body("totalExecutions", equalTo(1))
            .body("successfulExecutions", equalTo(1));

        verify(getMarketResearchDetailsUseCase, times(1)).execute(any(UUID.class), any());
    }

    @Test
    void shouldReturn404WhenResearchNotFound() {
        // Given - use case throws EntityNotFoundException
        UUID nonExistentId = UUID.randomUUID();
        when(getMarketResearchDetailsUseCase.execute(any(UUID.class), any()))
            .thenThrow(new EntityNotFoundException("Market research not found"));

        // When/Then - GET returns 404
        given()
            .cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/market-research/" + nonExistentId)
            .then()
            .statusCode(404);
    }

    @Test
    void shouldPauseMarketResearch() {
        // Given - mock research ID
        UUID researchId = UUID.randomUUID();
        doNothing().when(pauseMarketResearchUseCase).execute(any(UUID.class), any());

        // When/Then - POST pause returns 204
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType(ContentType.JSON)
            .when().post("/api/v1/market-research/" + researchId + "/pause")
            .then()
            .statusCode(204);

        verify(pauseMarketResearchUseCase, times(1)).execute(any(UUID.class), any());
    }

    @Test
    void shouldReturn404WhenPausingNonExistentResearch() {
        // Given - use case throws EntityNotFoundException
        UUID nonExistentId = UUID.randomUUID();
        doThrow(new EntityNotFoundException("Market research not found"))
            .when(pauseMarketResearchUseCase).execute(any(UUID.class), any());

        // When/Then - POST pause returns 404
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType(ContentType.JSON)
            .when().post("/api/v1/market-research/" + nonExistentId + "/pause")
            .then()
            .statusCode(404);
    }

    @Test
    void shouldResumeMarketResearch() {
        // Given - mock research ID
        UUID researchId = UUID.randomUUID();
        doNothing().when(resumeMarketResearchUseCase).execute(any(UUID.class), any());

        // When/Then - POST resume returns 204
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType(ContentType.JSON)
            .when().post("/api/v1/market-research/" + researchId + "/resume")
            .then()
            .statusCode(204);

        verify(resumeMarketResearchUseCase, times(1)).execute(any(UUID.class), any());
    }

    @Test
    void shouldExecuteMarketResearch() {
        // Given - mock execution ID (async execution returns ID immediately)
        UUID researchId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        when(executeMarketResearchUseCase.execute(any(UUID.class), any()))
            .thenReturn(executionId);

        // When/Then - POST execute returns 202 Accepted with execution ID
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType(ContentType.JSON)
            .when().post("/api/v1/market-research/" + researchId + "/execute")
            .then()
            .statusCode(202)
            .body("executionId", equalTo(executionId.toString()))
            .body("researchId", equalTo(researchId.toString()))
            .body("message", containsString("Execution started"));

        verify(executeMarketResearchUseCase, times(1)).execute(any(UUID.class), any());
    }

    @Test
    void shouldDeleteMarketResearch() {
        // Given - mock research ID
        UUID researchId = UUID.randomUUID();
        doNothing().when(deleteMarketResearchUseCase).execute(any(UUID.class), any());

        // When/Then - DELETE returns 204
        given()
            .cookie("dev_session", TEST_EMAIL)
            .when().delete("/api/v1/market-research/" + researchId)
            .then()
            .statusCode(204);

        verify(deleteMarketResearchUseCase, times(1)).execute(any(UUID.class), any());
    }

    // Helper methods to create mock domain objects

    private MarketResearch createMockMarketResearch() {
        return new MarketResearch(
            "Bitcoin Weekly Scanner",
            "# Research Instructions\n\nAnalyze Bitcoin",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS),
            true,
            new BacktestConfiguration(
                Duration.ofDays(30),
                null,
                null
            ),
            TEST_USER_ID
        );
    }

    private ResearchExecution createMockExecution(UUID marketResearchId) {
        ResearchExecution execution = new ResearchExecution(
            marketResearchId,
            ResearchOutcome.STRATEGY_CREATED,
            "Strong bullish signals detected",
            new ViabilityScore(BigDecimal.valueOf(85.5)),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        execution.addAgentDecision(
            ResearchAgentType.NEWS_SENTIMENT_ANALYST,
            "BUY - Strong momentum",
            "RSI oversold, MACD bullish crossover"
        );
        return execution;
    }
}
