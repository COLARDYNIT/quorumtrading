package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.usecase.brokeraccount.ReleaseTradingCapitalUseCase;
import com.quorumtrading.application.usecase.command.ReleaseTradingCapitalCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service implementation for releasing reserved trading capital back to a broker account.
 *
 * <p>Called when a simulation reaches a terminal state (COMPLETED, FAILED) or is deleted.
 * Releases the original trading limit back to available balance.</p>
 */
public class ReleaseTradingCapitalService implements ReleaseTradingCapitalUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ReleaseTradingCapitalService.class);

    private final BrokerAccountRepository brokerAccountRepository;

    public ReleaseTradingCapitalService(BrokerAccountRepository brokerAccountRepository) {
        this.brokerAccountRepository = brokerAccountRepository;
    }

    @Override
    public void execute(ReleaseTradingCapitalCommand command) {
        BrokerAccount account = brokerAccountRepository.findById(command.brokerAccountId())
            .orElse(null);

        if (account == null) {
            LOG.warn("Broker account {} not found when releasing capital for simulation {} — skipping release",
                command.brokerAccountId().value(), command.simulationId().value());
            return;
        }

        LOG.info("Releasing {} from broker account {} for simulation {}",
            command.amount(), command.brokerAccountId().value(), command.simulationId().value());

        account.release(command.amount());
        brokerAccountRepository.save(account);

        LOG.info("Released capital. Broker account {} now has available: {}",
            command.brokerAccountId().value(), account.getAvailableBalance());
    }
}
