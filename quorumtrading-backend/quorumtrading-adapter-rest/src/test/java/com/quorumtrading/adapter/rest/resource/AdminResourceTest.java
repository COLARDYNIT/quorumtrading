package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AdminResource.
 * Uses mocked repositories to test REST layer in isolation.
 */
@QuarkusTest
class AdminResourceTest {

    @InjectMock
    UserRepository userRepository;

    @InjectMock
    MarketResearchRepository marketResearchRepository;

    @InjectMock
    StrategyRepository strategyRepository;

    @InjectMock
    SimulationRepository simulationRepository;

    private User createAdminUser(String email) {
        User user = User.create(new UserId(UUID.randomUUID()), email, email, null, OAuthProvider.GOOGLE);
        user.promoteToAdmin();
        return user;
    }

    private User createRegularUser(String email) {
        return User.create(new UserId(UUID.randomUUID()), email, email, null, OAuthProvider.GOOGLE);
    }

    @Test
    @TestSecurity(user = "admin@quorumtrading.com")
    void shouldReturnPlatformStatsForAdmin() {
        when(userRepository.findByEmail("admin@quorumtrading.com"))
            .thenReturn(Optional.of(createAdminUser("admin@quorumtrading.com")));
        when(userRepository.count()).thenReturn(150L);
        when(marketResearchRepository.count()).thenReturn(45L);
        when(strategyRepository.count()).thenReturn(78L);
        when(simulationRepository.count()).thenReturn(234L);

        given()
            .when().get("/api/v1/admin/stats")
            .then()
            .statusCode(200)
            .body("userCount", equalTo(150))
            .body("researchCount", equalTo(45))
            .body("strategyCount", equalTo(78))
            .body("simulationCount", equalTo(234));
    }

    @Test
    @TestSecurity(user = "admin@quorumtrading.com")
    void shouldReturnZeroStatsWhenEmpty() {
        when(userRepository.findByEmail("admin@quorumtrading.com"))
            .thenReturn(Optional.of(createAdminUser("admin@quorumtrading.com")));
        when(userRepository.count()).thenReturn(0L);
        when(marketResearchRepository.count()).thenReturn(0L);
        when(strategyRepository.count()).thenReturn(0L);
        when(simulationRepository.count()).thenReturn(0L);

        given()
            .when().get("/api/v1/admin/stats")
            .then()
            .statusCode(200)
            .body("userCount", equalTo(0))
            .body("researchCount", equalTo(0))
            .body("strategyCount", equalTo(0))
            .body("simulationCount", equalTo(0));
    }

    @Test
    void shouldRejectUnauthenticatedAccess() {
        given()
            .when().get("/api/v1/admin/stats")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "regular@quorumtrading.com")
    void shouldRejectNonAdminAccess() {
        when(userRepository.findByEmail("regular@quorumtrading.com"))
            .thenReturn(Optional.of(createRegularUser("regular@quorumtrading.com")));

        given()
            .when().get("/api/v1/admin/stats")
            .then()
            .statusCode(403);
    }
}
