package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.usecase.brokeraccount.DeleteBrokerAccountUseCase;
import com.quorumtrading.application.usecase.command.DeleteBrokerAccountCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service implementation for deleting broker accounts.
 *
 * <p>Validates that the account can be safely deleted before removing it.</p>
 */
public class DeleteBrokerAccountService implements DeleteBrokerAccountUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteBrokerAccountService.class);

    private final BrokerAccountRepository brokerAccountRepository;
    private final AuthorizationService authorizationService;

    public DeleteBrokerAccountService(BrokerAccountRepository brokerAccountRepository, AuthorizationService authorizationService) {
        this.brokerAccountRepository = brokerAccountRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public void execute(DeleteBrokerAccountCommand command) {
        LOG.info("Deleting broker account {}", command.brokerAccountId().value());

        BrokerAccount account = brokerAccountRepository.findById(command.brokerAccountId())
            .orElseThrow(() -> new EntityNotFoundException(
                "Broker account not found: " + command.brokerAccountId().value()));

        authorizationService.requireDelete(command.requestingUserId(), account);

        // Validate account can be deleted
        validateDeletion(account);

        brokerAccountRepository.delete(command.brokerAccountId());
        LOG.info("Deleted broker account {}", command.brokerAccountId().value());
    }

    private void validateDeletion(BrokerAccount account) {
        // Cannot delete account with reserved capital
        if (account.getReservedBalance().isPositive()) {
            throw new IllegalStateException(
                "Cannot delete broker account with reserved capital. " +
                "Reserved balance: " + account.getReservedBalance().amount() + " " +
                account.getReservedBalance().currency());
        }
    }
}
