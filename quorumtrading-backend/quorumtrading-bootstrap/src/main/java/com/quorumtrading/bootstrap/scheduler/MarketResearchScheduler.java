package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.exception.BudgetDepletedException;
import com.quorumtrading.application.exception.DailySpendLimitExceededException;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.usecase.EvaluateFeatureUseCase;
import com.quorumtrading.application.usecase.ExecuteMarketResearchUseCase;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagContext;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job to automatically execute market research configurations that are due.
 * Runs at a configurable interval (default: every 1 hour).
 */
@ApplicationScoped
public class MarketResearchScheduler {

    private static final Logger LOG = Logger.getLogger(MarketResearchScheduler.class);

    private final MarketResearchRepository repository;
    private final ExecuteMarketResearchUseCase executeUseCase;
    private final EvaluateFeatureUseCase evaluateFeature;
    private final boolean schedulerEnabled;

    @Inject
    public MarketResearchScheduler(
        MarketResearchRepository repository,
        ExecuteMarketResearchUseCase executeUseCase,
        EvaluateFeatureUseCase evaluateFeature,
        @ConfigProperty(name = "quorumtrading.scheduler.market-research.enabled", defaultValue = "true") boolean schedulerEnabled
    ) {
        this.repository = repository;
        this.executeUseCase = executeUseCase;
        this.evaluateFeature = evaluateFeature;
        this.schedulerEnabled = schedulerEnabled;
    }

    /**
     * Check for market research configurations due for execution and execute them.
     * Runs every hour by default.
     */
    @Scheduled(every = "${quorumtrading.scheduler.market-research.interval:1h}", concurrentExecution = ConcurrentExecution.SKIP)
    public void checkAndExecuteDueResearch() {
        // Evaluated with no user: a timer acts on nobody's behalf, so a flag switched on for named
        // individuals only must not make the scheduler run for everyone. Switching the feature off
        // stops the timer as well as the REST and MCP surfaces — otherwise research would keep
        // executing, and spending, behind a feature the operator believes is off.
        if (!evaluateFeature.isEnabled(Feature.MARKET_RESEARCH, FeatureFlagContext.anonymous())) {
            LOG.debug("Market research feature is switched off; scheduler idle");
            return;
        }
        if (!schedulerEnabled) {
            LOG.debug("Market research scheduler is disabled");
            return;
        }

        LOG.info("Checking for market research due for execution...");

        Instant now = Instant.now();
        List<MarketResearch> dueResearch = repository.findDueForExecution(now);

        if (dueResearch.isEmpty()) {
            LOG.info("No market research due for execution");
            return;
        }

        LOG.infof("Found %d market research configurations due for execution", dueResearch.size());

        int successCount = 0;
        int skippedCount = 0;
        int failureCount = 0;

        for (MarketResearch research : dueResearch) {
            // Continue with next research on any outcome (don't fail entire batch)
            switch (runOne(research)) {
                case EXECUTED -> successCount++;
                case SKIPPED_BUDGET -> skippedCount++;
                case FAILED -> failureCount++;
            }
        }

        LOG.infof("Market research scheduler completed - Success: %d, Skipped (budget): %d, Failed: %d",
            successCount, skippedCount, failureCount);
    }

    /** What one due research came to on this tick. */
    enum RunOutcome { EXECUTED, SKIPPED_BUDGET, FAILED }

    /**
     * Run one due research as its owner.
     *
     * <p>A budget refusal is the user's account state, not a fault: the use case has already paused
     * (lifetime budget) or deferred (daily cap) the research so it is not picked up again, so it is
     * logged once at WARN without a stack trace instead of as an ERROR.</p>
     */
    RunOutcome runOne(MarketResearch research) {
        try {
            LOG.infof("Executing market research: %s (%s)", research.getId(), research.getName());
            // Run as the research's owner, not as "no user". Passing null used to skip the
            // ownership check entirely; it now denies.
            executeUseCase.execute(research.getId(), research.getOwnerId());
            LOG.infof("Successfully executed market research: %s", research.getId());
            return RunOutcome.EXECUTED;
        } catch (BudgetDepletedException | DailySpendLimitExceededException e) {
            LOG.warnf("Skipped market research %s (%s): %s", research.getId(), research.getName(), e.getMessage());
            return RunOutcome.SKIPPED_BUDGET;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to execute market research: %s (%s)", research.getId(), research.getName());
            return RunOutcome.FAILED;
        }
    }
}
