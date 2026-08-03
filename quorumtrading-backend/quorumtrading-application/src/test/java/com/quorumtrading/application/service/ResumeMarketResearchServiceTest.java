package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.ResearchInterval;
import com.quorumtrading.model.valueobject.ResearchStatus;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.quorumtrading.application.service.TestConstants.TEST_USER_ID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for ResumeMarketResearchService.
 * Following TDD methodology: write tests first, then implement.
 */
class ResumeMarketResearchServiceTest {

    @Mock
    private MarketResearchRepository repository;

    @Mock
    private AuthorizationService authorizationService;

    private ResumeMarketResearchService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ResumeMarketResearchService(repository, authorizationService);
    }

    @Test
    void shouldSuccessfullyResumePausedResearch() {
        // Given
        UUID marketResearchId = UUID.randomUUID();
        MarketResearch pausedResearch = new MarketResearch(
            "Test Research",
            "# Instructions",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(1, ResearchInterval.Unit.DAYS),
            false,
            null,
            TEST_USER_ID
        );
        // Pause it first
        pausedResearch.pause();

        when(repository.findById(marketResearchId)).thenReturn(Optional.of(pausedResearch));
        when(repository.save(any(MarketResearch.class))).thenReturn(pausedResearch);

        // When
        service.execute(marketResearchId, TEST_USER_ID);

        // Then
        assertThat(pausedResearch.getStatus()).isEqualTo(ResearchStatus.ACTIVE);
        verify(repository, times(1)).findById(marketResearchId);
        verify(repository, times(1)).save(pausedResearch);
    }

    @Test
    void shouldThrowEntityNotFoundExceptionWhenResearchNotFound() {
        // Given
        UUID marketResearchId = UUID.randomUUID();
        when(repository.findById(marketResearchId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.execute(marketResearchId, TEST_USER_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Market research not found");

        verify(repository, times(1)).findById(marketResearchId);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldChangeStatusToActive() {
        // Given
        UUID marketResearchId = UUID.randomUUID();
        MarketResearch pausedResearch = new MarketResearch(
            "Test Research",
            "# Instructions",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(1, ResearchInterval.Unit.DAYS),
            false,
            null,
            TEST_USER_ID
        );
        pausedResearch.pause();

        ArgumentCaptor<MarketResearch> captor = ArgumentCaptor.forClass(MarketResearch.class);
        when(repository.findById(marketResearchId)).thenReturn(Optional.of(pausedResearch));
        when(repository.save(captor.capture())).thenReturn(pausedResearch);

        // When
        service.execute(marketResearchId, TEST_USER_ID);

        // Then
        MarketResearch savedResearch = captor.getValue();
        assertThat(savedResearch.getStatus()).isEqualTo(ResearchStatus.ACTIVE);
    }

    @Test
    void shouldPersistChangesToRepository() {
        // Given
        UUID marketResearchId = UUID.randomUUID();
        MarketResearch pausedResearch = new MarketResearch(
            "Test Research",
            "# Instructions",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(1, ResearchInterval.Unit.DAYS),
            false,
            null,
            TEST_USER_ID
        );
        pausedResearch.pause();

        when(repository.findById(marketResearchId)).thenReturn(Optional.of(pausedResearch));
        when(repository.save(any(MarketResearch.class))).thenReturn(pausedResearch);

        // When
        service.execute(marketResearchId, TEST_USER_ID);

        // Then
        verify(repository, times(1)).save(pausedResearch);
    }

    @Test
    void shouldThrowExceptionWhenIdIsNull() {
        // When / Then
        assertThatThrownBy(() -> service.execute(null, TEST_USER_ID))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Market research ID cannot be null");

        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }
}
