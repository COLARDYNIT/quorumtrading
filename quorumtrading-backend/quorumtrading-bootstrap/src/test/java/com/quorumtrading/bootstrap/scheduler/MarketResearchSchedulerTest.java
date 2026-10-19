package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.exception.BudgetDepletedException;
import com.quorumtrading.application.exception.DailySpendLimitExceededException;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.ResearchInterval;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;
import java.math.BigDecimal;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
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
    @Test
    @DisplayName("a depleted budget is a budget skip, not a failure, and the batch carries on")
    void depletedBudgetIsASkipNotAFailure() {
        MarketResearch depleted = research();
        MarketResearch healthy = research();
        when(evaluateFeature.isEnabled(any(), any())).thenReturn(true);
        when(repository.findDueForExecution(any())).thenReturn(List.of(depleted, healthy));
        when(executeUseCase.execute(depleted.getId(), depleted.getOwnerId()))
            .thenThrow(new BudgetDepletedException(depleted.getOwnerId()));

        MarketResearchScheduler scheduler = scheduler(true);

        assertThat(scheduler.runOne(depleted)).isEqualTo(MarketResearchScheduler.RunOutcome.SKIPPED_BUDGET);
        assertThat(scheduler.runOne(healthy)).isEqualTo(MarketResearchScheduler.RunOutcome.EXECUTED);
        scheduler.checkAndExecuteDueResearch();
        verify(executeUseCase, org.mockito.Mockito.times(2)).execute(healthy.getId(), healthy.getOwnerId());
    }

    @Test
    @DisplayName("a spent daily cap is a budget skip too")
    void dailyCapIsASkip() {
        MarketResearch capped = research();
        when(executeUseCase.execute(capped.getId(), capped.getOwnerId()))
            .thenThrow(new DailySpendLimitExceededException(capped.getOwnerId(), BigDecimal.ONE, BigDecimal.TEN));

        assertThat(scheduler(true).runOne(capped)).isEqualTo(MarketResearchScheduler.RunOutcome.SKIPPED_BUDGET);
    }

    @Test
    @DisplayName("any other exception is still a failure")
    void otherExceptionsAreFailures() {
        MarketResearch broken = research();
        when(executeUseCase.execute(broken.getId(), broken.getOwnerId()))
            .thenThrow(new IllegalStateException("boom"));

        assertThat(scheduler(true).runOne(broken)).isEqualTo(MarketResearchScheduler.RunOutcome.FAILED);
    }

    private static MarketResearch research() {
        return new MarketResearch("Research", "# x", Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(1, ResearchInterval.Unit.DAYS), false, null, UserId.generate());
    }
}
