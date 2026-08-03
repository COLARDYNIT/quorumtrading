package com.quorumtrading.adapter.persistence;

import com.quorumtrading.application.usecase.StartSimulationUseCase;
import com.quorumtrading.application.usecase.command.StartSimulationCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producer for beans needed by persistence adapter tests.
 * Provides ObjectMapper bean required by MarketEventRepositoryImpl.
 * Provides mock StartSimulationUseCase for SimulationCreatedEventHandler.
 */
@ApplicationScoped
public class TestBeanProducer {

    @Produces
    @ApplicationScoped
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * A real in-memory registry so the spend-ledger failure counter is actually exercised rather
     * than skipped by {@code LLMUsageRepositoryImpl}'s resolvable-guard. The assembled application
     * supplies a Prometheus registry; this module has none of its own.
     */
    @Produces
    @ApplicationScoped
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Produces
    @ApplicationScoped
    public StartSimulationUseCase startSimulationUseCase() {
        // Mock implementation for tests - does nothing
        return new StartSimulationUseCase() {
            @Override
            public void execute(StartSimulationCommand command) {
                // No-op for persistence adapter tests
            }
        };
    }
}
