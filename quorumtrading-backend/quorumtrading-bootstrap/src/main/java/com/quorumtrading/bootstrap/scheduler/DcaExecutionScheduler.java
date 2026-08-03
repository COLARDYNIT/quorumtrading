package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.application.usecase.command.ExecuteInvestmentPlanCommand;
import com.quorumtrading.application.usecase.investmentplan.ExecuteInvestmentPlanUseCase;
import com.quorumtrading.model.entity.InvestmentPlan;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

/**
 * Polls for due {@code InvestmentPlan}s and fires their executions.
 *
 * <p><b>Why daily-with-due-check instead of a true monthly cron:</b> a single
 * monthly cron risks missing executions if Fargate task is mid-deploy or
 * temporarily down on the firing day. A daily probe of {@code next_execution_at}
 * is robust to drift and handles per-plan day-of-month naturally.</p>
 *
 * <p><b>Safety:</b> disabled by default. Operators enable per environment via
 * {@code quorumtrading.scheduler.investments.enabled=true} only after smoke-testing the
 * end-to-end flow. Every plan execution is wrapped in its own try/catch so a
 * single bad plan can never poison the batch. Idempotency for retries is
 * enforced one layer down in {@code SimulationContributionService} via the
 * {@code (planId, contributionDate)} UNIQUE constraint.</p>
 *
 * <p><b>Concurrency:</b> SKIP — overlapping runs of this scheduler would
 * re-process the same due plans before the prior run finishes advancing them.</p>
 */
@ApplicationScoped
public class DcaExecutionScheduler {

    private static final Logger LOG = Logger.getLogger(DcaExecutionScheduler.class);

    private final InvestmentPlanRepository planRepository;
    private final ExecuteInvestmentPlanUseCase executeUseCase;
    private final boolean enabled;
    private final int batchSize;

    @Inject
    public DcaExecutionScheduler(
            InvestmentPlanRepository planRepository,
            ExecuteInvestmentPlanUseCase executeUseCase,
            @ConfigProperty(name = "quorumtrading.scheduler.investments.enabled", defaultValue = "false") boolean enabled,
            @ConfigProperty(name = "quorumtrading.scheduler.investments.batch-size", defaultValue = "100") int batchSize
    ) {
        this.planRepository = planRepository;
        this.executeUseCase = executeUseCase;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    /**
     * Default cadence: daily at 06:00 UTC. Override with
     * {@code quorumtrading.scheduler.investments.cron} (Quarkus cron expression).
     */
    @Scheduled(cron = "${quorumtrading.scheduler.investments.cron:0 0 6 * * ?}",
               concurrentExecution = ConcurrentExecution.SKIP,
               identity = "dca-execution")
    public void runDuePlans() {
        if (!enabled) {
            LOG.debug("DCA execution scheduler is disabled (quorumtrading.scheduler.investments.enabled=false)");
            return;
        }

        Instant now = Instant.now();
        List<InvestmentPlan> due = planRepository.findDueForExecution(now, batchSize);
        if (due.isEmpty()) {
            LOG.debug("DCA scheduler: no plans due at " + now);
            return;
        }

        LOG.infof("DCA scheduler tick: %d plan(s) due at %s", due.size(), now);
        int executed = 0;
        int failed = 0;

        for (InvestmentPlan plan : due) {
            try {
                executeUseCase.execute(new ExecuteInvestmentPlanCommand(
                        plan.getId(),
                        plan.getUserId(),
                        now,
                        true /* advanceSchedule */));
                executed++;
            } catch (Exception e) {
                failed++;
                // Don't propagate — one bad plan must not abort the whole batch.
                // ExecuteInvestmentPlanService also writes a FAILED_BROKER_ERROR
                // allocation for broker-level errors; this catch is for unexpected
                // bugs above that layer (e.g. NoSuchElementException for a deleted
                // broker account).
                LOG.errorf(e, "DCA scheduler: plan %s failed unexpectedly (user=%s)",
                        plan.getId(), plan.getUserId());
            }
        }
        LOG.infof("DCA scheduler tick complete — executed=%d failed=%d", executed, failed);
    }
}
