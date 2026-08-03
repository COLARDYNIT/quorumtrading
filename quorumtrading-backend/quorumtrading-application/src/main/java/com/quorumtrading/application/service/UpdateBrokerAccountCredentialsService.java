package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.BrokerCredentialValidationException;
import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort.CredentialValidationResult;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.brokeraccount.UpdateBrokerAccountCredentialsUseCase;
import com.quorumtrading.application.usecase.command.UpdateBrokerAccountCredentialsCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.valueobject.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service implementation for updating broker account credentials.
 *
 * <p>Updates the API credentials (and optionally the display name) on an existing
 * broker account, then validates the new credentials against the broker API.</p>
 */
public class UpdateBrokerAccountCredentialsService implements UpdateBrokerAccountCredentialsUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateBrokerAccountCredentialsService.class);

    private final BrokerAccountRepository brokerAccountRepository;
    private final BrokerCredentialValidationPort credentialValidationPort;
    private final SimulationRepository simulationRepository;
    private final AuthorizationService authorizationService;

    public UpdateBrokerAccountCredentialsService(
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
    public BrokerAccount execute(UpdateBrokerAccountCredentialsCommand command) {
        LOG.info("Updating credentials for broker account {}", command.brokerAccountId().value());

        BrokerAccount account = brokerAccountRepository.findById(command.brokerAccountId())
            .orElseThrow(() -> new EntityNotFoundException(
                "Broker account not found: " + command.brokerAccountId().value()));

        authorizationService.requireEdit(command.requestingUserId(), account);

        if (command.name() != null && !command.name().isBlank()) {
            account.updateName(command.name());
        }

        account.updateCredentials(command.credentials());

        validateAndSyncCredentials(account);

        BrokerAccount saved = brokerAccountRepository.save(account);
        LOG.info("Credentials updated for broker account {}, status: {}",
            command.brokerAccountId().value(), saved.getStatus());

        return saved;
    }

    private void validateAndSyncCredentials(BrokerAccount account) {
        if (credentialValidationPort == null) {
            throw new IllegalStateException(
                "BrokerCredentialValidationPort not available. " +
                "Cannot validate credentials without validation adapter.");
        }

        CredentialValidationResult result = credentialValidationPort
            .validate(account.getBrokerType(), account.getCredentials());

        if (result.valid()) {
            LOG.debug("New credentials valid for {} account {}",
                account.getBrokerType(), account.getId().value());
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
