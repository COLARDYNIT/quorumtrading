package com.quorumtrading.application.service;

import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.event.ResearchExecutionStartedEvent;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.usecase.ExecuteMarketResearchUseCase;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;
import org.jboss.logging.Logger;

import java.math.BigDecimal;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Service for executing market research asynchronously.
 * <p>
 * This service validates the research configuration and immediately returns an
 * execution ID. The actual research execution happens asynchronously via the
 * MarketResearchOrchestrator, with progress events published at each stage.
 * </p>
 * <p>
 * Framework-agnostic, uses constructor injection.
 * </p>
 */
public class ExecuteMarketResearchService implements ExecuteMarketResearchUseCase {

    private static final Logger LOG = Logger.getLogger(ExecuteMarketResearchService.class);

    private final MarketResearchRepository repository;
    private final MarketResearchOrchestrator orchestrator;
    private final EventPublisher<ResearchExecutionStartedEvent> executionStartedPublisher;
    private final EventPublisher<ResearchExecutionCompleted> executionCompletedPublisher;
    private final ExecutorService executorService;
    private final RecordLLMUsageUseCase recordLLMUsageUseCase;
    private final AuthorizationService authorizationService;

    public ExecuteMarketResearchService(
        MarketResearchRepository repository,
        MarketResearchOrchestrator orchestrator,
        EventPublisher<ResearchExecutionStartedEvent> executionStartedPublisher,
        EventPublisher<ResearchExecutionCompleted> executionCompletedPublisher,
        ExecutorService executorService,
        RecordLLMUsageUseCase recordLLMUsageUseCase,
        AuthorizationService authorizationService
    ) {
        this.repository = repository;
        this.orchestrator = orchestrator;
        this.executionStartedPublisher = executionStartedPublisher;
        this.executionCompletedPublisher = executionCompletedPublisher;
        this.executorService = executorService;
        this.recordLLMUsageUseCase = recordLLMUsageUseCase;
        this.authorizationService = authorizationService;
    }

    @Override
    public UUID execute(UUID marketResearchId, UserId requestingUserId) {
        LOG.infof("Starting async market research execution: %s", marketResearchId);

        // 1. Find research (synchronous validation)
        MarketResearch research = repository.findById(marketResearchId)
            .orElseThrow(() -> new NoSuchElementException("Market research not found: " + marketResearchId));

        // 1b. Authorization check
        authorizationService.requireEdit(requestingUserId, research);

        // 2. Validate status (synchronous validation)
        if (!research.getStatus().isActive()) {
            throw new IllegalStateException("Cannot execute inactive research: " + marketResearchId + " (status: " + research.getStatus() + ")");
        }

        // 2b. Budget pre-check: Fail fast if user's AI budget is depleted
        // This prevents initiating LLM calls that would incur costs when user cannot pay
        if (research.getOwnerId() != null) {
            recordLLMUsageUseCase.requireAvailableBudget(research.getOwnerId());
            LOG.debugf("Budget pre-check passed for user %s", research.getOwnerId());
        }

        // 3. Generate execution ID upfront (before async execution)
        UUID executionId = UUID.randomUUID();

        // 4. Fire execution started event immediately
        executionStartedPublisher.publish(
            ResearchExecutionStartedEvent.of(marketResearchId, executionId)
        );
        LOG.infof("Published ResearchExecutionStartedEvent for research %s, execution %s", marketResearchId, executionId);

        // 5. Spawn async task for actual execution
        executorService.submit(() -> executeAsync(research, executionId));

        // 6. Return execution ID immediately (caller doesn't wait for completion)
        return executionId;
    }

    /**
     * Execute the research asynchronously.
     * This method runs in a separate thread and publishes events during execution.
     */
    private void executeAsync(MarketResearch research, UUID executionId) {
        try {
            // Reload entity in async context to get fresh transactional state
            // The entity loaded in the original thread may be detached in this async context
            MarketResearch freshResearch = repository.findById(research.getId())
                .orElseThrow(() -> new IllegalStateException("Research not found: " + research.getId()));

            LOG.infof("Async execution started for research %s, execution %s", freshResearch.getId(), executionId);

            // No caller-supplied model override. Passing the user's GLOBAL preference down as an
            // override made it win over everything, which is exactly what made per-agent research
            // preferences inert. The orchestrator resolves each research agent through the shared
            // chain instead, where the global preference is tier 4 and a per-agent one at tier 3
            // can actually beat it.
            ResearchExecution execution = orchestrator.executeResearch(freshResearch, executionId);

            // Record LLM usage to user's budget
            recordUsageForExecution(freshResearch, execution);

            // Mark research as executed
            freshResearch.markAsExecuted();
            repository.save(freshResearch);

            LOG.infof("Market research execution complete: %s (outcome: %s)", execution.getId(), execution.getOutcome());

        } catch (Exception e) {
            LOG.errorf(e, "Async execution failed for research %s, execution %s", research.getId(), executionId);
            // Publish failure event so WebSocket clients are notified
            // The orchestrator handles its own failure cases, but if save() fails after orchestrator
            // succeeds, we need to ensure clients are still notified
            ResearchExecutionCompleted failureEvent = ResearchExecutionCompleted.of(
                executionId,
                research.getId(),
                null,  // no strategy created on failure
                false  // don't trigger backtest on failure
            );
            executionCompletedPublisher.publish(failureEvent);
        }
    }

    /**
     * Record LLM usage cost to the user's budget.
     * This ensures market research costs are tracked in the budget alongside consensus decision costs.
     *
     * @param research  The market research configuration (contains owner ID)
     * @param execution The completed execution (contains LLM usage/cost)
     */
    private void recordUsageForExecution(MarketResearch research, ResearchExecution execution) {
        if (research.getOwnerId() == null) {
            LOG.warnf("Cannot record usage for research without owner ID: %s", research.getId());
            return;
        }

        try {
            if (execution.getLLMUsage() != null && execution.getLLMUsage().billedCost() != null) {
                BigDecimal billedCost = execution.getLLMUsage().billedCost();
                // Tokens, not cost: on a free engine a research execution would otherwise leave no
                // ledger row at all, so deleting the research would erase every trace it ran.
                if (execution.getLLMUsage().totalTokens() > 0) {
                    recordLLMUsageUseCase.recordUsage(research.getOwnerId(), billedCost,
                        execution.getLLMUsage().rawCost(), execution.getLLMUsage().totalTokens(),
                        com.quorumtrading.model.valueobject.UsageSource.RESEARCH);
                    LOG.debugf("Recorded LLM usage of $%.6f for user %s (research execution %s)",
                        billedCost, research.getOwnerId(), execution.getId());
                }
            }
        } catch (Exception e) {
            // Don't fail execution if usage recording fails — the model has already answered and
            // the provider has already charged. But the spend ledger is the only record of that
            // charge, so a failure to write it is an error, not a warning.
            LOG.errorf(e, "Failed to record LLM usage for user %s (research %s)",
                research.getOwnerId(), research.getId());
        }
    }
}
