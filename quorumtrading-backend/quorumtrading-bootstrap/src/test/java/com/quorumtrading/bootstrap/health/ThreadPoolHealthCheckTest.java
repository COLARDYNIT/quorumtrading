package com.quorumtrading.bootstrap.health;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ThreadPoolHealthCheckTest {

    @Test
    void shouldReportThreadPoolMetricsInReadinessCheck() {
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks.find { it.name == 'ThreadPool health check' }.status", equalTo("UP"))
            .body("checks.find { it.name == 'ThreadPool health check' }.data.'jvm.threads.live'", greaterThan(0))
            .body("checks.find { it.name == 'ThreadPool health check' }.data.'jvm.threads.peak'", greaterThan(0))
            .body("checks.find { it.name == 'ThreadPool health check' }.data.'jvm.threads.daemon'", greaterThan(0));
    }

    @Test
    void shouldReportHealthyWhenQueuesNotSaturated() {
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("checks.find { it.name == 'ThreadPool health check' }.status", equalTo("UP"));
    }
}
