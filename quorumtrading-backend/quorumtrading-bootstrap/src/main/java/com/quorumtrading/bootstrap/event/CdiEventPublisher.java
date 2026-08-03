package com.quorumtrading.bootstrap.event;

import com.quorumtrading.application.port.out.EventPublisher;
import jakarta.enterprise.event.Event;

/**
 * CDI adapter implementation of EventPublisher.
 * <p>
 * Bridges the application layer's EventPublisher port to CDI's Event mechanism.
 * This keeps the application layer free from framework dependencies while still
 * enabling real-time event broadcasting via CDI observers.
 * </p>
 *
 * @param <T> The event type to publish
 */
public class CdiEventPublisher<T> implements EventPublisher<T> {

    private final Event<T> cdiEvent;

    public CdiEventPublisher(Event<T> cdiEvent) {
        this.cdiEvent = cdiEvent;
    }

    @Override
    public void publish(T event) {
        cdiEvent.fire(event);
    }
}
