package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.usecase.dto.MarketResearchDetails;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.quorumtrading.application.service.TestConstants.TEST_USER_ID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GetMarketResearchDetailsService.
 * Following TDD methodology: write tests first, then implement.
 */
class GetMarketResearchDetailsServiceTest {

    @Mock
    private MarketResearchRepository marketResearchRepository;

    @Mock
    private ResearchExecutionRepository executionRepository;

    @Mock
    private AuthorizationService authorizationService;

    private GetMarketResearchDetailsService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetMarketResearchDetailsService(marketResearchRepository, executionRepository, authorizationService);
    }

    @Test
    void shouldSuccessfullyRetrieveDetailsWithExecutions() {
        // Given
        UUID marketResearchId = UUID.randomUUID();
        MarketResearch research = new MarketResearch(
            "Test Research",
            "# Instructions",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(1, ResearchInterval.Unit.DAYS),
            false,
            null,
            TEST_USER_ID
        );

        ResearchExecution execution1 = new ResearchExecution(
            marketResearchId,
            ResearchOutcome.STRATEGY_CREATED,
            "Good opportunity",
            new ViabilityScore(new BigDecimal("8")),
            UUID.randomUUID(),
            null
        );

        ResearchExecution execution2 = new ResearchExecution(
            marketResearchId,
            ResearchOutcome.NO_OPPORTUNITY,
            "No opportunity",
            new ViabilityScore(new BigDecimal("3")),
            UUID.randomUUID(),
            null
        );

        List<ResearchExecution> executions = List.of(execution1, execution2);

        when(marketResearchRepository.findById(marketResearchId)).thenReturn(Optional.of(research));
        when(executionRepository.findByMarketResearchId(marketResearchId)).thenReturn(executions);
        when(executionRepository.findLatestByMarketResearchId(marketResearchId)).thenReturn(Optional.of(execution2));

        // When
        MarketResearchDetails result = service.execute(marketResearchId, TEST_USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.research()).isEqualTo(research);
        assertThat(result.executions()).hasSize(2);
        assertThat(result.latestExecution()).isPresent();
        assertThat(result.latestExecution().get()).isEqualTo(execution2);
        assertThat(result.totalExecutions()).isEqualTo(2);
        // STRATEGY_CREATED + NO_OPPORTUNITY = 2 successful
        assertThat(result.successfulExecutions()).isEqualTo(2);

        verify(marketResearchRepository, times(1)).findById(marketResearchId);
        verify(executionRepository, times(1)).findByMarketResearchId(marketResearchId);
        verify(executionRepository, times(1)).findLatestByMarketResearchId(marketResearchId);
    }

    @Test
    void shouldSuccessfullyRetrieveDetailsWithoutExecutions() {
        // Given
        UUID marketResearchId = UUID.randomUUID();
        MarketResearch research = new MarketResearch(
            "Test Research",
            "# Instructions",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(1, ResearchInterval.Unit.DAYS),
            false,
            null,
            TEST_USER_ID
        );

        when(marketResearchRepository.findById(marketResearchId)).thenReturn(Optional.of(research));
        when(executionRepository.findByMarketResearchId(marketResearchId)).thenReturn(List.of());
        when(executionRepository.findLatestByMarketResearchId(marketResearchId)).thenReturn(Optional.empty());

        // When
        MarketResearchDetails result = service.execute(marketResearchId, TEST_USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.research()).isEqualTo(research);
        assertThat(result.executions()).isEmpty();
        assertThat(result.latestExecution()).isEmpty();
        assertThat(result.totalExecutions()).isEqualTo(0);
        assertThat(result.successfulExecutions()).isEqualTo(0);

        verify(marketResearchRepository, times(1)).findById(marketResearchId);
        verify(executionRepository, times(1)).findByMarketResearchId(marketResearchId);
        verify(executionRepository, times(1)).findLatestByMarketResearchId(marketResearchId);
    }

    @Test
    void shouldThrowEntityNotFoundExceptionWhenResearchNotFound() {
        // Given
        UUID marketResearchId = UUID.randomUUID();
        when(marketResearchRepository.findById(marketResearchId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.execute(marketResearchId, TEST_USER_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Market research not found");

        verify(marketResearchRepository, times(1)).findById(marketResearchId);
        verify(executionRepository, never()).findByMarketResearchId(any());
        verify(executionRepository, never()).findLatestByMarketResearchId(any());
    }

    @Test
    void shouldReturnCorrectExecutionCount() {
        // Given
        UUID marketResearchId = UUID.randomUUID();
        MarketResearch research = new MarketResearch(
            "Test Research",
            "# Instructions",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(1, ResearchInterval.Unit.DAYS),
            false,
            null,
            TEST_USER_ID
        );

        List<ResearchExecution> executions = List.of(
            new ResearchExecution(marketResearchId, ResearchOutcome.STRATEGY_CREATED, "1", new ViabilityScore(new BigDecimal("7")), UUID.randomUUID(), null),
            new ResearchExecution(marketResearchId, ResearchOutcome.STRATEGY_CREATED, "2", new ViabilityScore(new BigDecimal("8")), UUID.randomUUID(), null),
            new ResearchExecution(marketResearchId, ResearchOutcome.STRATEGY_CREATED, "3", new ViabilityScore(new BigDecimal("9")), UUID.randomUUID(), null)
        );

        when(marketResearchRepository.findById(marketResearchId)).thenReturn(Optional.of(research));
        when(executionRepository.findByMarketResearchId(marketResearchId)).thenReturn(executions);
        when(executionRepository.findLatestByMarketResearchId(marketResearchId)).thenReturn(Optional.of(executions.get(2)));

        // When
        MarketResearchDetails result = service.execute(marketResearchId, TEST_USER_ID);

        // Then
        assertThat(result.totalExecutions()).isEqualTo(3);
    }

    @Test
    void shouldReturnCorrectLatestExecution() {
        // Given
        UUID marketResearchId = UUID.randomUUID();
        MarketResearch research = new MarketResearch(
            "Test Research",
            "# Instructions",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(1, ResearchInterval.Unit.DAYS),
            false,
            null,
            TEST_USER_ID
        );

        ResearchExecution latestExecution = new ResearchExecution(
            marketResearchId,
            ResearchOutcome.STRATEGY_CREATED,
            "Latest",
            new ViabilityScore(new BigDecimal("9")),
            UUID.randomUUID(),
            null
        );

        when(marketResearchRepository.findById(marketResearchId)).thenReturn(Optional.of(research));
        when(executionRepository.findByMarketResearchId(marketResearchId)).thenReturn(List.of(latestExecution));
        when(executionRepository.findLatestByMarketResearchId(marketResearchId)).thenReturn(Optional.of(latestExecution));

        // When
        MarketResearchDetails result = service.execute(marketResearchId, TEST_USER_ID);

        // Then
        assertThat(result.latestExecution()).isPresent();
        assertThat(result.latestExecution().get()).isEqualTo(latestExecution);
    }

    @Test
    void shouldCalculateSuccessfulExecutionsCorrectly() {
        // Given
        UUID marketResearchId = UUID.randomUUID();
        MarketResearch research = new MarketResearch(
            "Test Research",
            "# Instructions",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(1, ResearchInterval.Unit.DAYS),
            false,
            null,
            TEST_USER_ID
        );

        List<ResearchExecution> executions = List.of(
            new ResearchExecution(marketResearchId, ResearchOutcome.STRATEGY_CREATED, "1", new ViabilityScore(new BigDecimal("7")), UUID.randomUUID(), null),
            new ResearchExecution(marketResearchId, ResearchOutcome.NO_OPPORTUNITY, "2", new ViabilityScore(new BigDecimal("3")), UUID.randomUUID(), null),
            new ResearchExecution(marketResearchId, ResearchOutcome.STRATEGY_CREATED, "3", new ViabilityScore(new BigDecimal("8")), UUID.randomUUID(), null),
            new ResearchExecution(marketResearchId, ResearchOutcome.FAILED, "4", null, null, null)
        );

        when(marketResearchRepository.findById(marketResearchId)).thenReturn(Optional.of(research));
        when(executionRepository.findByMarketResearchId(marketResearchId)).thenReturn(executions);
        when(executionRepository.findLatestByMarketResearchId(marketResearchId)).thenReturn(Optional.of(executions.get(3)));

        // When
        MarketResearchDetails result = service.execute(marketResearchId, TEST_USER_ID);

        // Then
        assertThat(result.totalExecutions()).isEqualTo(4);
        // 2 STRATEGY_CREATED + 1 NO_OPPORTUNITY = 3 successful
        assertThat(result.successfulExecutions()).isEqualTo(3);
    }

    @Test
    void shouldThrowExceptionWhenIdIsNull() {
        // When / Then
        assertThatThrownBy(() -> service.execute(null, TEST_USER_ID))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Market research ID cannot be null");

        verify(marketResearchRepository, never()).findById(any());
        verify(executionRepository, never()).findByMarketResearchId(any());
    }
}
