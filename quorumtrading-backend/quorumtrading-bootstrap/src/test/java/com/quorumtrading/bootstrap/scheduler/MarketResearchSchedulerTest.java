package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.usecase.EvaluateFeatureUseCase;
import com.quorumtrading.application.usecase.ExecuteMarketResearchUseCase;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The scheduler's feature-flag guard.
 *
 * <p>This one is worth a test on its own because it is the only surface where a disabled feature
 * costs money rather than just returning an error. REST and MCP refuse in front of a user who can
 * see what happened; a timer runs unattended, and research executions call models. If the guard
 * regresses, nobody notices until the bill arrives.</p>
 */
@DisplayName("MarketResearchScheduler")
class MarketResearchSchedulerTest {

    private MarketResearchRepository repository;
    private ExecuteMarketResearchUseCase executeUseCase;
    private EvaluateFeatureUseCase evaluateFeature;

    @BeforeEach
    void setUp() {
        repository = mock(MarketResearchRepository.class);
        executeUseCase = mock(ExecuteMarketResearchUseCase.class);
        evaluateFeature = mock(EvaluateFeatureUseCase.class);
    }

    private MarketResearchScheduler scheduler(boolean schedulerEnabled) {
        return new MarketResearchScheduler(
            repository, executeUseCase, evaluateFeature, schedulerEnabled);
    }

    @Test
    @DisplayName("does not even look for due research when the feature is switched off")
    void idlesWhenFeatureIsOff() {
        when(evaluateFeature.isEnabled(any(), any())).thenReturn(false);

        scheduler(true).checkAndExecuteDueResearch();

        verify(repository, never()).findDueForExecution(any());
        verify(executeUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("evaluates the flag anonymously — a timer acts on nobody's behalf")
    void evaluatesAnonymously() {
        when(evaluateFeature.isEnabled(any(), any())).thenReturn(false);

        scheduler(true).checkAndExecuteDueResearch();

        // A flag switched on for named individuals must not make the scheduler run for everyone,
        // so the context it evaluates with has to carry no identity.
        verify(evaluateFeature).isEnabled(
            org.mockito.ArgumentMatchers.eq(Feature.MARKET_RESEARCH),
            org.mockito.ArgumentMatchers.argThat(FeatureFlagContext::isAnonymous));
    }

    @Test
    @DisplayName("runs when the feature is on and the scheduler is enabled")
    void runsWhenEnabled() {
        when(evaluateFeature.isEnabled(any(), any())).thenReturn(true);
        when(repository.findDueForExecution(any())).thenReturn(List.of());

        scheduler(true).checkAndExecuteDueResearch();

        verify(repository).findDueForExecution(any());
    }

    @Test
    @DisplayName("the scheduler's own kill switch still works with the feature on")
    void schedulerDisabledStillWins() {
        when(evaluateFeature.isEnabled(any(), any())).thenReturn(true);

        scheduler(false).checkAndExecuteDueResearch();

        verify(repository, never()).findDueForExecution(any());
    }
}
