package com.quorumtrading.adapter.rest.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * With {@code quorumtrading.llm.selection.enabled} at its default ({@code false} — hosted SaaS),
 * the LLM selection endpoints must behave as if they do not exist: a multi-user deployment cannot
 * allow any user to flip the platform's LLM engine.
 */
@QuarkusTest
class LlmProviderResourceDisabledTest {

    @Test
    void statusEndpointIs404WhenDisabled() {
        given().when().get("/api/v1/llm/providers").then().statusCode(404);
    }

    @Test
    void selectEndpointIs404WhenDisabled() {
        given()
            .contentType("application/json")
            .body("{\"provider\":\"LOCAL\"}")
            .when().post("/api/v1/llm/providers/select")
            .then().statusCode(404);
    }

    @Test
    void setupPageIs404WhenDisabled() {
        given().when().get("/api/v1/llm/setup").then().statusCode(404);
    }
}
