package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.BrokerCredentialValidationException;
import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort.CredentialValidationResult;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.brokeraccount.ValidateBrokerAccountCredentialsUseCase;
import com.quorumtrading.application.usecase.command.ValidateBrokerAccountCredentialsCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.valueobject.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service implementation for re-validating broker account credentials.
 *
 * <p>This service validates that stored credentials are still valid by calling
 * the broker's API. For MOCK accounts, validation always succeeds as they don't
 * require real credentials.</p>
 */
public class ValidateBrokerAccountCredentialsService implements ValidateBrokerAccountCredentialsUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ValidateBrokerAccountCredentialsService.class);

    private final BrokerAccountRepository brokerAccountRepository;
    private final BrokerCredentialValidationPort credentialValidationPort;
    private final SimulationRepository simulationRepository;
    private final AuthorizationService authorizationService;

    public ValidateBrokerAccountCredentialsService(
            BrokerAccountRepository brokerAccountRepository,
            BrokerCredentialValidationPort credentialValidationPort,
            SimulationRepository simulationRepository,
            AuthorizationService authorizationService) {
        this.brokerAccountRepository = brokerAccountRepository;
        this.credentialValidationPort = credentialValidationPort;
        this.simulationRepository = simulationRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public BrokerAccount execute(ValidateBrokerAccountCredentialsCommand command) {
        LOG.info("Validating credentials for broker account {}", command.brokerAccountId().value());

        BrokerAccount account = brokerAccountRepository.findById(command.brokerAccountId())
            .orElseThrow(() -> new EntityNotFoundException(
                "Broker account not found: " + command.brokerAccountId().value()));

        // Authorize before touching the account: this reaches the broker API with the account's
        // stored credentials and mutates its status, and used to run on a bare lookup by id.
        authorizationService.requireEdit(command.requestingUserId(), account);

        if (account.isMock()) {
            // Mock accounts don't have credentials to validate
            LOG.debug("Mock account - marking as active without credential validation");
            account.markActive();
        } else {
            // Real broker accounts need credential validation
            validateRealBrokerCredentials(account);
        }

        BrokerAccount saved = brokerAccountRepository.save(account);
        LOG.info("Credential validation completed for account {}, status: {}",
            command.brokerAccountId().value(), saved.getStatus());

        return saved;
    }

    private void validateRealBrokerCredentials(BrokerAccount account) {
        if (credentialValidationPort == null) {
            throw new IllegalStateException(
                "BrokerCredentialValidationPort not available. " +
                "Cannot validate credentials without validation adapter.");
        }

        if (account.getCredentials() == null) {
            LOG.warn("Real broker account {} has no credentials", account.getId().value());
            account.markInvalidCredentials();
            throw new BrokerCredentialValidationException(
                account.getBrokerType(),
                "No credentials stored for broker account");
        }

        CredentialValidationResult result = credentialValidationPort
            .validate(account.getBrokerType(), account.getCredentials());

        if (result.valid()) {
            LOG.debug("Credentials valid for {} account {}", account.getBrokerType(), account.getId().value());
            account.markActive();
            result.balance().ifPresent(balance -> {
                LOG.info("Syncing balance for account {}: {}", account.getId().value(), balance);
                account.syncBalance(balance);
            });
            result.equity().ifPresent(equity -> {
                LOG.info("Syncing equity for account {}: {}", account.getId().value(), equity);
                account.syncEquity(equity);
            });
            recalculateReservedBalance(account);
        } else {
            String errorMessage = result.errorMessage().orElse("Unknown validation error");
            LOG.warn("Credential validation failed for {} account {}: {}",
                account.getBrokerType(), account.getId().value(), errorMessage);
            account.markInvalidCredentials();
            throw new BrokerCredentialValidationException(account.getBrokerType(), errorMessage);
        }
    }

    private void recalculateReservedBalance(BrokerAccount account) {
        List<Simulation> activeSimulations = simulationRepository.findActiveByBrokerAccountId(account.getId());
        Money correctReserved = activeSimulations.stream()
            .filter(sim -> sim.getTradingLimit() != null)
            .map(Simulation::getTradingLimit)
            .reduce(Money.usd(BigDecimal.ZERO), Money::add);

        if (!correctReserved.equals(account.getReservedBalance())) {
            LOG.info("Correcting reserved balance for account {}: {} -> {} (based on {} active simulation(s))",
                account.getId().value(), account.getReservedBalance(), correctReserved, activeSimulations.size());
            account.syncReservedBalance(correctReserved);
        }
    }
}
