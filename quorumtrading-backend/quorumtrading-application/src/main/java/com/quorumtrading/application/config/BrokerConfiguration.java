package com.quorumtrading.application.config;

import com.quorumtrading.application.port.out.BrokerPort;

/**
 * CDI configuration marker for broker adapters.
 *
 * Broker adapters should be annotated with @Named to be injectable by the BrokerRouter.
 * The actual implementations are provided by adapter modules (mock, Alpaca, etc.)
 *
 * Expected named beans:
 * - "mockBroker" → provided by quorumtrading-adapter-broker-mock module
 * - "alpacaBroker" → provided by quorumtrading-adapter-broker-alpaca module (optional)
 *
 * Example in adapter module:
 * @ApplicationScoped
 * @Named("mockBroker")
 * public class MockBrokerAdapter implements BrokerPort { ... }
 *
 * Example injection in BrokerRouter:
 * @Inject @Named("mockBroker")
 * BrokerPort mockBroker;
 */
public final class BrokerConfiguration {
    private BrokerConfiguration() {
        // Utility class
    }
}
