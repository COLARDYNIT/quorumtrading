package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.usecase.brokeraccount.GetBrokerAccountUseCase;
import com.quorumtrading.application.usecase.query.GetBrokerAccountQuery;
import com.quorumtrading.model.entity.BrokerAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service implementation for retrieving a single broker account by ID.
 */
public class GetBrokerAccountService implements GetBrokerAccountUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetBrokerAccountService.class);

    private final BrokerAccountRepository brokerAccountRepository;
    private final AuthorizationService authorizationService;

    public GetBrokerAccountService(BrokerAccountRepository brokerAccountRepository, AuthorizationService authorizationService) {
        this.brokerAccountRepository = brokerAccountRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public BrokerAccount execute(GetBrokerAccountQuery query) {
        LOG.debug("Getting broker account {}", query.brokerAccountId().value());

        BrokerAccount account = brokerAccountRepository.findById(query.brokerAccountId())
            .orElseThrow(() -> new EntityNotFoundException(
                "Broker account not found: " + query.brokerAccountId().value()));

        authorizationService.requireView(query.requestingUserId(), account);

        return account;
    }
}
