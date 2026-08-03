package com.quorumtrading.adapter.websocket;

import com.quorumtrading.adapter.websocket.dto.ResearchExecutionProgressMessage;
import com.quorumtrading.application.event.ResearchAgentCompletedEvent;
import com.quorumtrading.application.event.ResearchAgentStartedEvent;
import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.event.ResearchExecutionStartedEvent;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.model.valueobject.ResearchOutcome;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Observes research execution progress events and broadcasts them to subscribed WebSocket clients.
 * <p>
 * Unlike simulation-specific events, research progress is broadcast to clients that have
 * explicitly subscribed to a specific research ID. This allows the frontend to show real-time
 * updates for market research execution progress.
 * </p>
 * <p>
 * Acts as a bridge between application layer events and WebSocket infrastructure:
 * MarketResearchOrchestrator -> fires CDI event -> ResearchProgressBroadcaster observes ->
 *   -> converts to DTO ->
 *     -> queries ChannelManager for research subscribers ->
 *       -> broadcasts to subscribed WebSocket sessions
 * </p>
 */
@ApplicationScoped
public class ResearchProgressBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ResearchProgressBroadcaster.class);

    @Inject
    ChannelManager channelManager;

    @Inject
    StreamEndpoint streamEndpoint;

    @Inject
    ResearchExecutionRepository executionRepository;

    /**
     * Observes research execution started events and broadcasts to subscribers.
     * <p>
     * This event is fired when a market research execution begins.
     * </p>
     */
    public void onResearchExecutionStarted(@ObservesAsync ResearchExecutionStartedEvent event) {
        log.info("Received ResearchExecutionStartedEvent: researchId={}, executionId={}",
            event.researchId(), event.executionId());

        ResearchExecutionProgressMessage message = ResearchExecutionProgressMessage.started(
            event.researchId(),
            event.executionId(),
            event.timestamp()
        );

        broadcastToResearchSubscribers(event.researchId().toString(), message);
    }

    /**
     * Observes research agent started events and broadcasts to subscribers.
     * <p>
     * This event is fired when a specific research agent begins its analysis.
     * </p>
     */
    public void onResearchAgentStarted(@ObservesAsync ResearchAgentStartedEvent event) {
        log.info("Received ResearchAgentStartedEvent: researchId={}, executionId={}, agentType={}",
            event.researchId(), event.executionId(), event.agentType());

        ResearchExecutionProgressMessage message = ResearchExecutionProgressMessage.agentStarted(
            event.researchId(),
            event.executionId(),
            event.agentType().name(),
            event.timestamp()
        );

        broadcastToResearchSubscribers(event.researchId().toString(), message);
    }

    /**
     * Observes research agent completed events and broadcasts to subscribers.
     * <p>
     * This event is fired when a specific research agent completes its analysis.
     * </p>
     */
    public void onResearchAgentCompleted(@ObservesAsync ResearchAgentCompletedEvent event) {
        log.info("Received ResearchAgentCompletedEvent: researchId={}, executionId={}, agentType={}, durationMs={}",
            event.researchId(), event.executionId(), event.agentType(), event.durationMs());

        ResearchExecutionProgressMessage message = ResearchExecutionProgressMessage.agentCompleted(
            event.researchId(),
            event.executionId(),
            event.agentType().name(),
            event.durationMs(),
            event.timestamp()
        );

        broadcastToResearchSubscribers(event.researchId().toString(), message);
    }

    /**
     * Observes research execution completed events and broadcasts to subscribers.
     * <p>
     * This event is fired when a market research execution completes (successfully, no opportunity, or failed).
     * Looks up the actual execution outcome from the repository to properly handle FAILED executions.
     * </p>
     */
    public void onResearchExecutionCompleted(@ObservesAsync ResearchExecutionCompleted event) {
        log.info("Received ResearchExecutionCompleted: researchId={}, executionId={}, strategyId={}",
            event.marketResearchId(), event.executionId(), event.createdStrategyId());

        // Look up the actual outcome from the repository
        var execution = executionRepository.findById(event.executionId());

        if (execution.isEmpty()) {
            log.warn("Execution not found for completed event: executionId={}", event.executionId());
            return;
        }

        ResearchOutcome actualOutcome = execution.get().getOutcome();
        String outcomeString = actualOutcome.name();

        // Send appropriate message based on outcome
        ResearchExecutionProgressMessage message;
        if (actualOutcome == ResearchOutcome.FAILED) {
            String errorMessage = execution.get().getResearcherConclusion();
            message = ResearchExecutionProgressMessage.failed(
                event.marketResearchId(),
                event.executionId(),
                errorMessage,
                event.occurredAt()
            );
        } else {
            message = ResearchExecutionProgressMessage.completed(
                event.marketResearchId(),
                event.executionId(),
                outcomeString,
                event.createdStrategyId(),
                event.occurredAt()
            );
        }

        broadcastToResearchSubscribers(event.marketResearchId().toString(), message);
    }

    /**
     * Broadcasts a research progress message to all sessions subscribed to the given research ID.
     *
     * @param researchId The research ID to match subscribers
     * @param message    The research progress message to broadcast
     */
    private void broadcastToResearchSubscribers(String researchId, ResearchExecutionProgressMessage message) {
        try {
            // Get sessions subscribed to this research ID
            Set<String> sessionIds = channelManager.getSessionsByResearchId(researchId);

            log.info("Broadcasting research progress to {} sessions: researchId={}, stage={}",
                sessionIds.size(), researchId, message.getStage());

            if (sessionIds.isEmpty()) {
                log.debug("No subscribers for research progress: researchId={}", researchId);
                return;
            }

            // Broadcast to all subscribed sessions
            streamEndpoint.broadcast(sessionIds, message);

        } catch (Exception e) {
            log.error("Failed to broadcast research progress: researchId={}, error={}",
                researchId, e.getMessage(), e);
        }
    }
}
