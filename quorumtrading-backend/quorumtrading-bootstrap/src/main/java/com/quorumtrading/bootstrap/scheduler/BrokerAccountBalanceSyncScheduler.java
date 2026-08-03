package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort.CredentialValidationResult;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.valueobject.Money;

import java.math.BigDecimal;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Periodically syncs broker account balances from external brokers (e.g., Alpaca).
 * Runs every 15 minutes for all active, non-mock accounts.
 */
@ApplicationScoped
public class BrokerAccountBalanceSyncScheduler {

    private static final Logger LOG = Logger.getLogger(BrokerAccountBalanceSyncScheduler.class);

    @Inject
    BrokerAccountRepository brokerAccountRepository;

    @Inject
    Instance<BrokerCredentialValidationPort> credentialValidationPort;

    @Inject
    SimulationRepository simulationRepository;

    @Scheduled(every = "15m", concurrentExecution = ConcurrentExecution.SKIP)
    public void syncBrokerAccountBalances() {
        if (credentialValidationPort.isUnsatisfied()) {
            LOG.debug("No BrokerCredentialValidationPort available, skipping balance sync");
            return;
        }

        List<BrokerAccount> activeAccounts = brokerAccountRepository.findAllByStatus(AccountStatus.ACTIVE);
        List<BrokerAccount> realAccounts = activeAccounts.stream()
            .filter(a -> !a.isMock())
            .toList();

        if (realAccounts.isEmpty()) {
            return;
        }

        LOG.infof("Syncing balances for %d active broker account(s)", realAccounts.size());
        int synced = 0;

        for (BrokerAccount account : realAccounts) {
            try {
                // Select the validator that supports this account's broker type. Iterating the
                // Instance avoids AmbiguousResolutionException once a second validation port
                // (e.g. Saxo) is on the classpath, and dispatches correctly per broker family.
                BrokerCredentialValidationPort validationPort =
                    selectValidationPort(account.getBrokerType());
                if (validationPort == null) {
                    LOG.warnf("No credential validator supports broker type %s for account %s, skipping",
                        account.getBrokerType(), account.getId().value());
                    continue;
                }

                CredentialValidationResult result = validationPort.validate(
                    account.getBrokerType(), account.getCredentials());

                if (result.valid() && result.balance().isPresent()) {
                    account.syncBalance(result.balance().get());
                    result.equity().ifPresent(account::syncEquity);
                    recalculateReservedBalance(account);
                    account.markActive();
                    brokerAccountRepository.save(account);
                    synced++;
                } else if (!result.valid()) {
                    LOG.warnf("Credentials invalid during balance sync for account %s, marking inactive",
                        account.getId().value());
                    account.markInvalidCredentials();
                    brokerAccountRepository.save(account);
                }
            } catch (Exception e) {
                LOG.errorf(e, "Failed to sync balance for broker account %s", account.getId().value());
            }
        }

        if (synced > 0) {
            LOG.infof("Synced balances for %d broker account(s)", synced);
        }
    }

    /**
     * Returns the credential validation port that supports the given broker type, or {@code null}
     * if none is available. Iterates the CDI {@code Instance} rather than calling {@code get()} so
     * it is safe with multiple validation ports on the classpath.
     */
    private BrokerCredentialValidationPort selectValidationPort(
            com.quorumtrading.model.enums.BrokerType brokerType) {
        for (BrokerCredentialValidationPort port : credentialValidationPort) {
            if (port.supports(brokerType)) {
                return port;
            }
        }
        return null;
    }

    private void recalculateReservedBalance(BrokerAccount account) {
        java.util.List<Simulation> activeSimulations = simulationRepository.findActiveByBrokerAccountId(account.getId());
        Money correctReserved = activeSimulations.stream()
            .filter(sim -> sim.getTradingLimit() != null)
            .map(Simulation::getTradingLimit)
            .reduce(Money.usd(BigDecimal.ZERO), Money::add);

        if (!correctReserved.equals(account.getReservedBalance())) {
            LOG.infof("Correcting reserved balance for account %s: %s -> %s (based on %d active simulation(s))",
                account.getId().value(), account.getReservedBalance(), correctReserved, activeSimulations.size());
            account.syncReservedBalance(correctReserved);
        }
    }
}
