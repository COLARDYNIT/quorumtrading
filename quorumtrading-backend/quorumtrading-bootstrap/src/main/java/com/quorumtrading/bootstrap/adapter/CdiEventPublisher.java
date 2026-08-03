package com.quorumtrading.bootstrap.adapter;

import com.quorumtrading.application.port.out.EventPublisher;
import jakarta.enterprise.event.Event;

/**
 * CDI adapter that implements EventPublisher port using CDI's Event mechanism.
 * <p>
 * This adapter bridges the gap between the framework-agnostic application layer
 * and Quarkus's CDI event system. The application layer publishes events through
 * the EventPublisher port, and this adapter translates those calls to CDI events
 * that can be observed by @ObservesAsync handlers.
 * </p>
 *
 * @param <T> The event type to publish
 */
public class CdiEventPublisher<T> implements EventPublisher<T> {

    private final Event<T> cdiEvent;

    /**
     * Creates a new CDI event publisher.
     *
     * @param cdiEvent The CDI Event instance to delegate to
     */
    public CdiEventPublisher(Event<T> cdiEvent) {
        this.cdiEvent = cdiEvent;
    }

    @Override
    public void publish(T event) {
        // Use fireAsync for non-blocking event delivery
        // Observers with @ObservesAsync will receive the event asynchronously
        cdiEvent.fireAsync(event);
    }
}
