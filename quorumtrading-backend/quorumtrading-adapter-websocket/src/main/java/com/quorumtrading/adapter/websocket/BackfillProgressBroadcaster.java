package com.quorumtrading.adapter.websocket;

import com.quorumtrading.adapter.websocket.dto.BackfillProgressMessage;
import com.quorumtrading.application.event.BackfillJobCreatedEvent;
import com.quorumtrading.application.event.BackfillProgressEvent;
import com.quorumtrading.application.event.BackfillStatusChangedEvent;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.marketdata.BackfillJob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Observes backfill progress events and broadcasts them to all connected WebSocket clients.
 *
 * Unlike simulation-specific events, backfill progress is global and broadcasted to all
 * active sessions. This allows the frontend to show real-time updates for market data
 * availability improvements.
 *
 * Acts as a bridge between application layer events and WebSocket infrastructure:
 * BackfillExecutor → fires CDI event → BackfillProgressBroadcaster observes →
 *   → converts to DTO →
 *     → queries ChannelManager for all active sessions →
 *       → broadcasts to all WebSocket sessions
 */
@ApplicationScoped
public class BackfillProgressBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(BackfillProgressBroadcaster.class);

    @Inject
    ChannelManager channelManager;

    @Inject
    StreamEndpoint streamEndpoint;

    @Inject
    BackfillJobRepository backfillJobRepository;

    /**
     * Observes backfill job created events and broadcasts PENDING status immediately.
     * This allows the frontend to show the job as soon as it's created.
     */
    public void onBackfillJobCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) BackfillJobCreatedEvent event) {
        log.info("Received BackfillJobCreatedEvent: jobId={}, totalGaps={}",
            event.jobId(), event.totalGaps());

        String linkedSimulationId = lookupLinkedSimulationId(event.jobId());

        BackfillProgressMessage message = BackfillProgressMessage.created(
            event.jobId().toString(),
            event.totalGaps(),
            linkedSimulationId
        );

        broadcastToAll(message);
    }

    /**
     * Observes backfill status change events and broadcasts appropriate messages.
     * Handles STARTED, COMPLETED, and FAILED status changes.
     */
    public void onBackfillStatusChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) BackfillStatusChangedEvent event) {
        log.info("Received BackfillStatusChangedEvent: jobId={}, oldStatus={}, newStatus={}",
            event.jobId(), event.oldStatus(), event.newStatus());

        String linkedSimulationId = lookupLinkedSimulationId(event.jobId());
        BackfillProgressMessage message = null;

        // Create appropriate message based on status change
        if (event.newStatus() == BackfillStatus.IN_PROGRESS && event.oldStatus() == BackfillStatus.PENDING) {
            message = BackfillProgressMessage.started(event.jobId().toString(), linkedSimulationId);
        } else if (event.newStatus() == BackfillStatus.COMPLETED) {
            // totalRecords carries ROWS FETCHED (not gap count); errorMessage carries the
            // honest completion note (gaps with no data / no provider), if any.
            int recordsFetched = event.totalRecords() != null ? event.totalRecords() : 0;
            message = BackfillProgressMessage.completed(
                event.jobId().toString(), recordsFetched, event.errorMessage(), linkedSimulationId);
        } else if (event.newStatus() == BackfillStatus.FAILED) {
            String errorMessage = event.errorMessage() != null ? event.errorMessage() : "Unknown error";
            message = BackfillProgressMessage.failed(event.jobId().toString(), errorMessage, linkedSimulationId);
        }

        if (message != null) {
            broadcastToAll(message);
        }
    }

    /**
     * Observes backfill progress events and broadcasts progress updates.
     */
    public void onBackfillProgress(@Observes(during = TransactionPhase.AFTER_SUCCESS) BackfillProgressEvent event) {
        log.info("Received BackfillProgressEvent: jobId={}, progress={}%, processed={}/{}",
            event.jobId(), event.progressPercentage(), event.processedRecords(), event.totalRecords());

        String linkedSimulationId = lookupLinkedSimulationId(event.jobId());
        BackfillProgressMessage message;

        // Use the new method with gap details if available
        if (event.currentGap() != null || !event.allGapProgress().isEmpty()) {
            BackfillProgressMessage.GapProgressDTO currentGapDTO = event.currentGap() != null
                ? BackfillProgressMessage.GapProgressDTO.from(event.currentGap())
                : null;

            java.util.List<BackfillProgressMessage.GapProgressDTO> gapDetailsDTOs = event.allGapProgress().stream()
                .map(BackfillProgressMessage.GapProgressDTO::from)
                .toList();

            message = BackfillProgressMessage.progressWithGaps(
                event.jobId().toString(),
                event.progressPercentage(),
                event.processedRecords(),
                event.totalRecords(),
                currentGapDTO,
                gapDetailsDTOs,
                linkedSimulationId
            );
        } else {
            // Fall back to legacy method
            message = BackfillProgressMessage.progress(
                event.jobId().toString(),
                event.progressPercentage(),
                event.processedRecords(),
                event.totalRecords(),
                linkedSimulationId
            );
        }

        broadcastToAll(message);
    }

    /**
     * Look up the linked simulation ID for a backfill job.
     *
     * @param jobId The backfill job ID
     * @return The linked simulation ID as a string, or null if not linked
     */
    private String lookupLinkedSimulationId(UUID jobId) {
        try {
            return backfillJobRepository.findById(jobId)
                .flatMap(BackfillJob::getLinkedSimulationId)
                .map(simId -> simId.value().toString())
                .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to lookup linked simulation for backfill job {}: {}", jobId, e.getMessage());
            return null;
        }
    }

    /**
     * Broadcasts a backfill progress message to all active WebSocket sessions.
     *
     * @param message The backfill progress message to broadcast
     */
    private void broadcastToAll(BackfillProgressMessage message) {
        try {
            // Get all active WebSocket sessions (not just those subscribed to simulations)
            // Use StreamEndpoint's session tracking for global broadcasts
            Set<String> sessionIds = streamEndpoint.getAllSessionIds();

            log.info("Broadcasting backfill progress to {} sessions: jobId={}, eventType={}",
                sessionIds.size(), message.getJobId(), message.getEventType());

            if (sessionIds.isEmpty()) {
                log.debug("No active sessions to broadcast backfill progress");
                return;
            }

            // Broadcast to all sessions
            streamEndpoint.broadcast(sessionIds, message);

        } catch (Exception e) {
            log.error("Failed to broadcast backfill progress: {}", e.getMessage(), e);
        }
    }
}
