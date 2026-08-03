package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.event.BackfillStatusChangedEvent;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.service.BackfillExecutor;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.marketdata.BackfillJob;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Scheduler that processes queued backfill jobs.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Poll for PENDING and IN_PROGRESS backfill jobs every 10 seconds</li>
 *   <li>Execute jobs via BackfillExecutor</li>
 *   <li>Handle errors gracefully and mark failed jobs</li>
 *   <li>Update job status via repository</li>
 * </ul>
 *
 * <p>Design Notes:</p>
 * <ul>
 *   <li>Uses concurrentExecution = SKIP to prevent overlapping executions</li>
 *   <li>Jobs remain IN_PROGRESS if rate-limited (processed in next cycle)</li>
 *   <li>Failures are logged and job is marked FAILED with error message</li>
 *   <li>Scheduler continues processing remaining jobs on partial failures</li>
 * </ul>
 */
@ApplicationScoped
public class BackfillScheduler {

    private static final Logger LOG = Logger.getLogger(BackfillScheduler.class);

    @Inject
    BackfillJobRepository repository;

    @Inject
    BackfillExecutor executor;

    @Inject
    Event<BackfillStatusChangedEvent> statusEvent;

    /**
     * Process pending backfill jobs every 10 seconds.
     * Jobs can be PENDING (not started) or IN_PROGRESS (partially completed).
     *
     * <p>Uses concurrentExecution = SKIP to ensure the scheduler doesn't start
     * a new execution while a previous one is still running. This prevents
     * duplicate STARTED/COMPLETED events when processing takes longer than 10 seconds.</p>
     */
    @Scheduled(every = "10s", concurrentExecution = ConcurrentExecution.SKIP)
    public void processBackfillQueue() {
        try {
            List<BackfillJob> pendingJobs = repository.findPendingJobs();

            if (!pendingJobs.isEmpty()) {
                LOG.infof("Processing %d pending backfill jobs", pendingJobs.size());
                for (BackfillJob job : pendingJobs) {
                    LOG.infof("  - Job %s: status=%s, progress=%d%%, gaps=%d",
                        job.getId(), job.getStatus(), job.getProgressPercentage(),
                        job.getGaps() != null ? job.getGaps().size() : 0);
                }
            }

            for (BackfillJob job : pendingJobs) {
                processJob(job);
            }
        } catch (Exception e) {
            LOG.error("Error processing backfill queue", e);
        }
    }

    /**
     * Process a single backfill job.
     *
     * <p>If job is PENDING, transition to IN_PROGRESS before execution.
     * If job is already IN_PROGRESS, resume execution where it left off.
     * On error, mark job as FAILED with error message.</p>
     *
     * @param job the backfill job to process
     */
    private void processJob(BackfillJob job) {
        try {
            LOG.infof("Processing backfill job %s (status=%s, progress=%d%%)",
                job.getId(), job.getStatus(), job.getProgressPercentage());

            // RETRY_PENDING jobs: check if the retry time has elapsed
            if (job.getStatus() == BackfillStatus.RETRY_PENDING) {
                if (!job.isReadyForRetry()) {
                    LOG.debugf("Job %s retry scheduled for %s, not yet due — skipping",
                        job.getId(), job.getRetryAfter());
                    return;
                }
                LOG.infof("Job %s retry time reached, resuming processing", job.getId());
                job.startProcessing();
                repository.update(job);
            }

            // Mark as in progress if still pending
            if (job.getStatus() == BackfillStatus.PENDING) {
                job.startProcessing();
                repository.update(job);
                LOG.debugf("Started backfill job %s", job.getId());
            }

            // Execute backfill (executor handles progress updates)
            executor.execute(job);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to process backfill job %s", job.getId());
            BackfillStatus oldStatus = job.getStatus();
            job.markFailed(e.getMessage());
            repository.update(job);

            // Fire status changed event: any status -> FAILED
            statusEvent.fire(new BackfillStatusChangedEvent(
                job.getId(),
                oldStatus,
                BackfillStatus.FAILED,
                e.getMessage(),
                null
            ));
        }
    }
}
