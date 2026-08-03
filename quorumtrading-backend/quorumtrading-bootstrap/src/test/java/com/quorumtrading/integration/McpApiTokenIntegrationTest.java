package com.quorumtrading.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end integration test for MCP API token authentication.
 *
 * Tests the full flow: create token via REST → use token against MCP → verify auth works.
 * Uses real services (no mocks) with the test PostgreSQL database.
 *
 * The MCP Streamable HTTP transport requires a session handshake:
 * 1. POST /mcp with "initialize" → 200 + Mcp-Session-Id header
 * 2. POST /mcp with "notifications/initialized" + Mcp-Session-Id → 202
 * 3. POST /mcp with "tools/call" etc. + Mcp-Session-Id → 200
 *
 * NOT @Transactional — HTTP calls commit independently.
 */
@QuarkusTest
class McpApiTokenIntegrationTest {

    @Inject
    IntegrationTestHelper helper;

    private static final String MCP_PATH = "/mcp";

    // ======================== MCP Session Helpers ========================

    private String uniqueEmail() {
        return "mcp-test-" + UUID.randomUUID().toString().substring(0, 8) + "@quorumtrading.local";
    }

    private String createTokenViaRest(String email) {
        return given()
                .cookie("dev_session", email)
                .contentType(ContentType.JSON)
                .body("{\"name\": \"MCP Test Token\"}")
                .when()
                .post("/api/v1/settings/api-tokens")
                .then()
                .statusCode(201)
                .extract().path("rawToken");
    }

    /**
     * Performs the full MCP handshake and returns the session ID.
     * Steps: initialize → notifications/initialized → ready for tool calls.
     */
    private String initializeMcpSession(String bearerToken) {
        // Step 1: initialize
        Response initResponse = given()
                .header("Authorization", "Bearer " + bearerToken)
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"integration-test","version":"1.0"}}}
                        """)
                .when()
                .post(MCP_PATH)
                .then()
                .statusCode(200)
                .extract().response();

        String sessionId = initResponse.header("Mcp-Session-Id");

        // Step 2: notifications/initialized
        given()
                .header("Authorization", "Bearer " + bearerToken)
                .header("Mcp-Session-Id", sessionId)
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body("""
                        {"jsonrpc":"2.0","method":"notifications/initialized"}
                        """)
                .when()
                .post(MCP_PATH)
                .then()
                .statusCode(202);

        return sessionId;
    }

    /**
     * Performs MCP handshake with an optional Authorization header value.
     * Used for testing auth error scenarios.
     */
    private String initializeMcpSessionNoAuth(String authHeaderValue) {
        var spec = given()
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"integration-test","version":"1.0"}}}
                        """);
        if (authHeaderValue != null) {
            spec = spec.header("Authorization", authHeaderValue);
        }

        Response initResponse = spec.when()
                .post(MCP_PATH)
                .then()
                .statusCode(200)
                .extract().response();

        String sessionId = initResponse.header("Mcp-Session-Id");

        var notifSpec = given()
                .header("Mcp-Session-Id", sessionId)
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body("""
                        {"jsonrpc":"2.0","method":"notifications/initialized"}
                        """);
        if (authHeaderValue != null) {
            notifSpec = notifSpec.header("Authorization", authHeaderValue);
        }
        notifSpec.when().post(MCP_PATH).then().statusCode(202);

        return sessionId;
    }

    private String mcpToolsCall(String toolName) {
        return """
                {"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"%s","arguments":{}}}
                """.formatted(toolName);
    }

    // ======================== Tests ========================

    @Test
    @DisplayName("MCP initialize should succeed with valid API token and return session ID")
    void shouldInitializeMcpWithValidApiToken() {
        String email = uniqueEmail();
        helper.createUserWithEmail(email);
        String rawToken = createTokenViaRest(email);

        given()
                .header("Authorization", "Bearer " + rawToken)
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"integration-test","version":"1.0"}}}
                        """)
                .when()
                .post(MCP_PATH)
                .then()
                .statusCode(200)
                .header("Mcp-Session-Id", notNullValue())
                .body("result.serverInfo.name", equalTo("quorumtrading"))
                .body("result.protocolVersion", equalTo("2024-11-05"))
                .body("result.instructions", is(not(emptyOrNullString())));
    }

    @Test
    @DisplayName("MCP tools/list should return registered tools after handshake")
    void shouldListMcpToolsWithValidApiToken() {
        String email = uniqueEmail();
        helper.createUserWithEmail(email);
        String rawToken = createTokenViaRest(email);
        String sessionId = initializeMcpSession(rawToken);

        // One representative tool per tool class. The Quarkus MCP server has a
        // known landmine where a single @ToolArg with a collection-typed
        // parameter (List<T>, Map<>, Set<>, Optional<List<T>>) silently knocks
        // unrelated tool *classes* out of tools/list at registration time. The
        // class that owns the bad arg loads fine in its own unit tests — only
        // this end-to-end assertion catches the regression. See
        // [[quarkus-mcp-optional-list-bug]] and [[mcp-oauth-discovery-stubs]].
        // If you add a tool class, add a representative tool name here.
        given()
                .header("Authorization", "Bearer " + rawToken)
                .header("Mcp-Session-Id", sessionId)
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body("""
                        {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                        """)
                .when()
                .post(MCP_PATH)
                .then()
                .statusCode(200)
                .body("result.tools.size()", greaterThan(0))
                .body("result.tools.name", hasItem("list_benchmarks"))            // BenchmarkTools
                .body("result.tools.name", hasItem("list_broker_accounts"))      // BrokerAccountTools
                .body("result.tools.name", hasItem("get_challenge"))              // ChallengeTools
                .body("result.tools.name", hasItem("get_consensus_with_agents")) // ConsensusTools
                .body("result.tools.name", hasItem("list_historical_events"))    // HistoricalEventTools
                .body("result.tools.name", hasItem("list_investment_plans"))     // InvestmentPlanTools
                .body("result.tools.name", hasItem("query_market_events"))       // MarketDataTools
                .body("result.tools.name", hasItem("list_market_research"))      // MarketResearchTools
                .body("result.tools.name", hasItem("list_notifications"))        // NotificationTools
                .body("result.tools.name", hasItem("query_portfolio_snapshots")) // PortfolioTools
                .body("result.tools.name", hasItem("list_simulations"))          // SimulationTools
                .body("result.tools.name", hasItem("list_strategies"));          // StrategyTools
    }

    @Test
    @DisplayName("MCP tools/call should authenticate user and execute tool")
    void shouldCallMcpToolWithValidApiToken() {
        String email = uniqueEmail();
        helper.createUserWithEmail(email);
        String rawToken = createTokenViaRest(email);
        String sessionId = initializeMcpSession(rawToken);

        given()
                .header("Authorization", "Bearer " + rawToken)
                .header("Mcp-Session-Id", sessionId)
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body(mcpToolsCall("list_broker_accounts"))
                .when()
                .post(MCP_PATH)
                .then()
                .statusCode(200)
                .body("result.isError", is(false))
                .body("result.content.size()", greaterThan(0));
    }

    @Test
    @DisplayName("MCP tools/call without initialize should auto-init (dummy-init) and return auth error")
    void shouldAutoInitAndReturnAuthErrorWithoutInitialize() {
        // With dummy-init=true (v1.9.1+), the server auto-creates a session instead of
        // returning a protocol error. The tool executes but fails with an auth error
        // because no Authorization header is present.
        given()
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body(mcpToolsCall("list_broker_accounts"))
                .when()
                .post(MCP_PATH)
                .then()
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Authentication required"));
    }

    @Test
    @DisplayName("MCP tools/call should return descriptive auth error with invalid API token")
    void shouldReturnAuthErrorWithInvalidToken() {
        String sessionId = initializeMcpSessionNoAuth("Bearer qt_completely_invalid_token_value_here");

        given()
                .header("Authorization", "Bearer qt_completely_invalid_token_value_here")
                .header("Mcp-Session-Id", sessionId)
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body(mcpToolsCall("list_broker_accounts"))
                .when()
                .post(MCP_PATH)
                .then()
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Authentication required"))
                .body("result.content[0].text", containsString("quorumtrading.com/settings/api-tokens"))
                .body("result.content[0].text", containsString("claude mcp add"));
    }

    @Test
    @DisplayName("MCP tools/call should return auth error with revoked API token")
    void shouldReturnAuthErrorWithRevokedToken() {
        String email = uniqueEmail();
        helper.createUserWithEmail(email);

        // Create token
        String response = given()
                .cookie("dev_session", email)
                .contentType(ContentType.JSON)
                .body("{\"name\": \"Revocable Token\"}")
                .when()
                .post("/api/v1/settings/api-tokens")
                .then()
                .statusCode(201)
                .extract().asString();

        String rawToken = io.restassured.path.json.JsonPath.from(response).getString("rawToken");
        String tokenId = io.restassured.path.json.JsonPath.from(response).getString("id");

        // Initialize session while token is still valid
        String sessionId = initializeMcpSession(rawToken);

        // Revoke the token
        given()
                .cookie("dev_session", email)
                .when()
                .delete("/api/v1/settings/api-tokens/" + tokenId)
                .then()
                .statusCode(204);

        // Attempt to call tool with revoked token — should get descriptive error
        given()
                .header("Authorization", "Bearer " + rawToken)
                .header("Mcp-Session-Id", sessionId)
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body(mcpToolsCall("list_broker_accounts"))
                .when()
                .post(MCP_PATH)
                .then()
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Authentication required"));
    }

    @Test
    @DisplayName("MCP tools/call should return auth error with missing Authorization header")
    void shouldReturnAuthErrorWithMissingAuth() {
        String sessionId = initializeMcpSessionNoAuth(null);

        // tools/call without auth should return descriptive error with setup instructions
        given()
                .header("Mcp-Session-Id", sessionId)
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body(mcpToolsCall("list_broker_accounts"))
                .when()
                .post(MCP_PATH)
                .then()
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Authentication required"))
                .body("result.content[0].text", containsString("quorumtrading.com/settings/api-tokens"))
                .body("result.content[0].text", containsString("claude mcp add"));
    }

    @Test
    @DisplayName("MCP tools/call should update token last_used_at")
    void shouldUpdateLastUsedAtAfterMcpCall() throws InterruptedException {
        String email = uniqueEmail();
        helper.createUserWithEmail(email);
        String rawToken = createTokenViaRest(email);

        // Verify lastUsedAt is null initially
        given()
                .cookie("dev_session", email)
                .when()
                .get("/api/v1/settings/api-tokens")
                .then()
                .statusCode(200)
                .body("[0].lastUsedAt", nullValue());

        // Initialize MCP session and call a tool
        String sessionId = initializeMcpSession(rawToken);

        given()
                .header("Authorization", "Bearer " + rawToken)
                .header("Mcp-Session-Id", sessionId)
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body(mcpToolsCall("list_broker_accounts"))
                .when()
                .post(MCP_PATH)
                .then()
                .statusCode(200)
                .body("result.isError", is(false));

        // Wait for async ApiTokenUsedEvent processing
        Thread.sleep(500);

        // Verify lastUsedAt was updated
        given()
                .cookie("dev_session", email)
                .when()
                .get("/api/v1/settings/api-tokens")
                .then()
                .statusCode(200)
                .body("[0].lastUsedAt", notNullValue());
    }

    @Test
    @DisplayName("MCP tools/call should return auth error with malformed Authorization header")
    void shouldReturnAuthErrorWithMalformedAuthHeader() {
        String email = uniqueEmail();
        helper.createUserWithEmail(email);
        String rawToken = createTokenViaRest(email);

        // Initialize with proper auth, then call with malformed header
        String sessionId = initializeMcpSession(rawToken);

        given()
                .header("Authorization", rawToken)  // Missing "Bearer " prefix
                .header("Mcp-Session-Id", sessionId)
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body(mcpToolsCall("list_broker_accounts"))
                .when()
                .post(MCP_PATH)
                .then()
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Authentication required"));
    }
}
