package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.usecase.ExecuteMarketResearchUseCase;
import com.quorumtrading.application.usecase.command.CreateMarketResearchCommand;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;
import java.util.UUID;

import static com.quorumtrading.application.service.TestConstants.TEST_USER_ID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for CreateMarketResearchService.
 * Following TDD methodology: write tests first, then implement.
 */
class CreateMarketResearchServiceTest {

    @Mock
    private MarketResearchRepository repository;

    @Mock
    private ExecuteMarketResearchUseCase executeMarketResearchUseCase;

    private CreateMarketResearchService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CreateMarketResearchService(repository, executeMarketResearchUseCase);
    }

    @Test
    void shouldCreateMarketResearchWithAllFields() {
        // Given
        Set<Symbol> targetAssets = Set.of(
            new Symbol("BTC", InvestmentType.CRYPTO),
            new Symbol("ETH", InvestmentType.CRYPTO)
        );
        ResearchInterval interval = new ResearchInterval(1, ResearchInterval.Unit.DAYS);
        BacktestConfiguration backtestConfig = new BacktestConfiguration(
            java.time.Duration.ofDays(30),
            java.time.Instant.parse("2025-01-01T00:00:00Z"),
            java.time.Instant.parse("2025-01-31T23:59:59Z")
        );

        CreateMarketResearchCommand command = new CreateMarketResearchCommand(
            "Daily Crypto Research",
            "# Research Instructions\nAnalyze BTC and ETH daily",
            targetAssets,
            interval,
            true,
            backtestConfig,
            TEST_USER_ID
        );

        MarketResearch savedResearch = new MarketResearch(
            command.name(),
            command.markdownInstructions(),
            command.targetAssets(),
            command.interval(),
            command.enableAutoBacktest(),
            command.backtestConfiguration(),
            command.ownerId()
        );

        when(repository.save(any(MarketResearch.class))).thenReturn(savedResearch);

        // When
        MarketResearch result = service.execute(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Daily Crypto Research");
        assertThat(result.getMarkdownInstructions()).isEqualTo("# Research Instructions\nAnalyze BTC and ETH daily");
        assertThat(result.getTargetAssets()).hasSize(2);
        assertThat(result.getInterval()).isEqualTo(interval);
        assertThat(result.isEnableAutoBacktest()).isTrue();
        assertThat(result.getBacktestConfig()).isNotNull();

        verify(repository, times(1)).save(any(MarketResearch.class));
    }

    @Test
    void shouldCreateMarketResearchWithMinimalFields() {
        // Given - no auto-backtest
        Set<Symbol> targetAssets = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        ResearchInterval interval = new ResearchInterval(7, ResearchInterval.Unit.DAYS);

        CreateMarketResearchCommand command = new CreateMarketResearchCommand(
            "Weekly BTC Research",
            "# Weekly Analysis\nCheck BTC trends",
            targetAssets,
            interval,
            false,
            null,
            TEST_USER_ID
        );

        MarketResearch savedResearch = new MarketResearch(
            command.name(),
            command.markdownInstructions(),
            command.targetAssets(),
            command.interval(),
            command.enableAutoBacktest(),
            command.backtestConfiguration(),
            command.ownerId()
        );

        when(repository.save(any(MarketResearch.class))).thenReturn(savedResearch);

        // When
        MarketResearch result = service.execute(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Weekly BTC Research");
        assertThat(result.isEnableAutoBacktest()).isFalse();
        assertThat(result.getBacktestConfig()).isNull();

        verify(repository, times(1)).save(any(MarketResearch.class));
    }

    @Test
    void shouldThrowExceptionWhenCommandIsNull() {
        // When / Then
        assertThatThrownBy(() -> service.execute(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Command cannot be null");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenNameIsNull() {
        // Given
        Set<Symbol> targetAssets = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        ResearchInterval interval = new ResearchInterval(1, ResearchInterval.Unit.DAYS);

        // When / Then
        assertThatThrownBy(() -> new CreateMarketResearchCommand(
            null,
            "# Instructions",
            targetAssets,
            interval,
            false,
            null,
            TEST_USER_ID
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Name cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenNameIsBlank() {
        // Given
        Set<Symbol> targetAssets = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        ResearchInterval interval = new ResearchInterval(1, ResearchInterval.Unit.DAYS);

        // When / Then
        assertThatThrownBy(() -> new CreateMarketResearchCommand(
            "   ",
            "# Instructions",
            targetAssets,
            interval,
            false,
            null,
            TEST_USER_ID
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Name cannot be blank");
    }

    @Test
    void shouldAcceptNullTargetAssets() {
        // Given - Target assets are optional (LLM can discover from instructions)
        ResearchInterval interval = new ResearchInterval(1, ResearchInterval.Unit.DAYS);

        // When
        CreateMarketResearchCommand command = new CreateMarketResearchCommand(
            "Research",
            "# Instructions",
            null,
            interval,
            false,
            null,
            TEST_USER_ID
        );

        // Then - null defaults to empty set
        assertThat(command.targetAssets()).isEmpty();
    }

    @Test
    void shouldAcceptEmptyTargetAssets() {
        // Given - Target assets are optional (LLM can discover from instructions)
        ResearchInterval interval = new ResearchInterval(1, ResearchInterval.Unit.DAYS);

        // When
        CreateMarketResearchCommand command = new CreateMarketResearchCommand(
            "Research",
            "# Instructions",
            Set.of(),
            interval,
            false,
            null,
            TEST_USER_ID
        );

        // Then
        assertThat(command.targetAssets()).isEmpty();
    }

    @Test
    void shouldThrowExceptionWhenAutoBacktestEnabledWithoutConfiguration() {
        // Given
        Set<Symbol> targetAssets = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        ResearchInterval interval = new ResearchInterval(1, ResearchInterval.Unit.DAYS);

        // When / Then
        assertThatThrownBy(() -> new CreateMarketResearchCommand(
            "Research",
            "# Instructions",
            targetAssets,
            interval,
            true,
            null,
            TEST_USER_ID
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Backtest configuration required when auto-backtest is enabled");
    }

    @Test
    void shouldPersistToRepository() {
        // Given
        Set<Symbol> targetAssets = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        ResearchInterval interval = new ResearchInterval(1, ResearchInterval.Unit.DAYS);

        CreateMarketResearchCommand command = new CreateMarketResearchCommand(
            "Research",
            "# Instructions",
            targetAssets,
            interval,
            false,
            null,
            TEST_USER_ID
        );

        ArgumentCaptor<MarketResearch> captor = ArgumentCaptor.forClass(MarketResearch.class);
        MarketResearch savedResearch = new MarketResearch(
            command.name(),
            command.markdownInstructions(),
            command.targetAssets(),
            command.interval(),
            command.enableAutoBacktest(),
            command.backtestConfiguration(),
            command.ownerId()
        );
        when(repository.save(captor.capture())).thenReturn(savedResearch);

        // When
        service.execute(command);

        // Then
        MarketResearch capturedResearch = captor.getValue();
        assertThat(capturedResearch).isNotNull();
        assertThat(capturedResearch.getName()).isEqualTo("Research");
        assertThat(capturedResearch.getMarkdownInstructions()).isEqualTo("# Instructions");
        assertThat(capturedResearch.getTargetAssets()).isEqualTo(targetAssets);
        assertThat(capturedResearch.getInterval()).isEqualTo(interval);
    }

    @Test
    void shouldReturnPersistedEntityWithGeneratedId() {
        // Given
        Set<Symbol> targetAssets = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        ResearchInterval interval = new ResearchInterval(1, ResearchInterval.Unit.DAYS);

        CreateMarketResearchCommand command = new CreateMarketResearchCommand(
            "Research",
            "# Instructions",
            targetAssets,
            interval,
            false,
            null,
            TEST_USER_ID
        );

        MarketResearch savedResearch = new MarketResearch(
            command.name(),
            command.markdownInstructions(),
            command.targetAssets(),
            command.interval(),
            command.enableAutoBacktest(),
            command.backtestConfiguration(),
            command.ownerId()
        );

        when(repository.save(any(MarketResearch.class))).thenReturn(savedResearch);

        // When
        MarketResearch result = service.execute(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ResearchStatus.ACTIVE);
    }

    @Test
    void shouldTriggerImmediateExecutionAfterCreation() {
        // Given
        Set<Symbol> targetAssets = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        ResearchInterval interval = new ResearchInterval(1, ResearchInterval.Unit.DAYS);

        CreateMarketResearchCommand command = new CreateMarketResearchCommand(
            "Research",
            "# Instructions",
            targetAssets,
            interval,
            false,
            null,
            TEST_USER_ID
        );

        MarketResearch savedResearch = new MarketResearch(
            command.name(),
            command.markdownInstructions(),
            command.targetAssets(),
            command.interval(),
            command.enableAutoBacktest(),
            command.backtestConfiguration(),
            command.ownerId()
        );

        when(repository.save(any(MarketResearch.class))).thenReturn(savedResearch);
        when(executeMarketResearchUseCase.execute(any(UUID.class), any(UserId.class))).thenReturn(UUID.randomUUID());

        // When
        service.execute(command);

        // Then - verify immediate execution is triggered
        verify(executeMarketResearchUseCase, times(1)).execute(savedResearch.getId(), command.ownerId());
    }

    @Test
    void shouldSucceedCreationEvenWhenImmediateExecutionFails() {
        // Given
        Set<Symbol> targetAssets = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        ResearchInterval interval = new ResearchInterval(1, ResearchInterval.Unit.DAYS);

        CreateMarketResearchCommand command = new CreateMarketResearchCommand(
            "Research",
            "# Instructions",
            targetAssets,
            interval,
            false,
            null,
            TEST_USER_ID
        );

        MarketResearch savedResearch = new MarketResearch(
            command.name(),
            command.markdownInstructions(),
            command.targetAssets(),
            command.interval(),
            command.enableAutoBacktest(),
            command.backtestConfiguration(),
            command.ownerId()
        );

        when(repository.save(any(MarketResearch.class))).thenReturn(savedResearch);
        // Simulate execution failure
        when(executeMarketResearchUseCase.execute(any(UUID.class), any(UserId.class)))
            .thenThrow(new RuntimeException("Execution service unavailable"));

        // When
        MarketResearch result = service.execute(command);

        // Then - creation should still succeed despite execution failure
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Research");
        verify(repository, times(1)).save(any(MarketResearch.class));
        verify(executeMarketResearchUseCase, times(1)).execute(savedResearch.getId(), command.ownerId());
    }
}
