package com.quorumtrading.application.port.out;

/**
 * Port interface for publishing domain events.
 * <p>
 * This abstraction keeps the application layer free from framework dependencies.
 * Adapters implement this interface using their specific event mechanism
 * (e.g., CDI events, Spring events, message queue, etc.).
 * </p>
 *
 * @param <T> The event type to publish
 */
public interface EventPublisher<T> {

    /**
     * Publish an event to all registered observers/listeners.
     *
     * @param event The event to publish
     */
    void publish(T event);
}
