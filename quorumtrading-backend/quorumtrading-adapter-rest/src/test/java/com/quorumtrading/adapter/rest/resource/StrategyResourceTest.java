package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.model.entity.User;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.quorumtrading.adapter.rest.resource.TestConstants.TEST_USER_ID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StrategyResource.
 * Uses mocked use cases to test REST layer in isolation.
 */
@QuarkusTest
class StrategyResourceTest {

    private static final String TEST_EMAIL = "test@quorumtrading.local";

    @InjectMock
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User testUser = User.reconstitute(
            TEST_USER_ID, TEST_EMAIL, "Test User", null,
            com.quorumtrading.model.enums.UserRole.USER,
            Instant.now(), Instant.now(), List.of(), true, false
        );
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
    }

    @InjectMock
    CreateStrategyUseCase createStrategyUseCase;

    @InjectMock
    ListStrategiesUseCase listStrategiesUseCase;

    @InjectMock
    GetStrategyUseCase getStrategyUseCase;

    @InjectMock
    UpdateStrategyUseCase updateStrategyUseCase;

    @InjectMock
    DeleteStrategyUseCase deleteStrategyUseCase;

    @Test
    void shouldReturnEmptyListWhenNoStrategies() {
        // Given - mock returns empty list
        when(listStrategiesUseCase.execute(any())).thenReturn(Collections.emptyList());

        // When/Then - endpoint returns empty list
        given()
            .cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/strategies")
            .then()
            .statusCode(200)
            .body("$.size()", is(0));
    }
}
