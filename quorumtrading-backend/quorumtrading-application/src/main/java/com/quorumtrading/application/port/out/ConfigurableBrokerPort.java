package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.SimulationId;

/**
 * Extension of BrokerPort that supports per-account configuration.
 * Broker adapters that support multiple accounts should implement this interface.
 *
 * <p><b>Per-Account Configuration:</b></p>
 * <p>This interface allows the BrokerRouter to configure broker adapters with
 * account-specific settings (MockBrokerConfig for mock brokers, BrokerCredentials
 * for real brokers) without directly referencing adapter classes.</p>
 *
 * <p><b>Usage:</b></p>
 * <ol>
 *   <li>{@link #configureForAccount(BrokerAccount)} - Configure adapter with account settings</li>
 *   <li>{@link #linkSimulationToAccount(SimulationId, BrokerAccountId)} - Link simulation to account</li>
 *   <li>Use standard BrokerPort methods for operations</li>
 * </ol>
 *
 * @see BrokerPort
 * @see BrokerAccount
 */
public interface ConfigurableBrokerPort extends BrokerPort {

    /**
     * Configures this broker adapter for a specific account.
     * Extracts relevant configuration from the BrokerAccount entity
     * (MockBrokerConfig for mock, BrokerCredentials for real brokers).
     *
     * @param account the broker account with configuration
     */
    void configureForAccount(BrokerAccount account);

    /**
     * Links a simulation to a broker account for routing purposes.
     * This allows BrokerPort methods that use SimulationId to route
     * to the correct account configuration.
     *
     * @param simulationId the simulation ID
     * @param accountId the broker account ID to link to
     */
    void linkSimulationToAccount(SimulationId simulationId, BrokerAccountId accountId);

    /**
     * Checks if this adapter has been configured for a specific account.
     *
     * @param accountId the broker account ID
     * @return true if account is configured
     */
    boolean isAccountConfigured(BrokerAccountId accountId);

    /**
     * Removes account configuration from this adapter.
     * Called when an account is deleted or credentials are invalidated.
     *
     * @param accountId the broker account ID to remove
     */
    void removeAccountConfiguration(BrokerAccountId accountId);
}
