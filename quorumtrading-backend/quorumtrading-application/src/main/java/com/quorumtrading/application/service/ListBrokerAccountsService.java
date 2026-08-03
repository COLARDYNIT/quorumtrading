package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.usecase.brokeraccount.ListBrokerAccountsUseCase;
import com.quorumtrading.application.usecase.query.ListBrokerAccountsQuery;
import com.quorumtrading.model.entity.BrokerAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Service implementation for listing broker accounts belonging to a user.
 */
public class ListBrokerAccountsService implements ListBrokerAccountsUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ListBrokerAccountsService.class);

    private final BrokerAccountRepository brokerAccountRepository;

    public ListBrokerAccountsService(BrokerAccountRepository brokerAccountRepository) {
        this.brokerAccountRepository = brokerAccountRepository;
    }

    @Override
    public List<BrokerAccount> execute(ListBrokerAccountsQuery query) {
        LOG.debug("Listing broker accounts for user {}", query.userId().value());

        // Inline accounts are an implementation detail of running a backtest with no broker —
        // the user never created them and cannot meaningfully act on them. Listing them buried
        // real accounts under one "Inline Mock Broker - <id>" row per backtest ever run.
        List<BrokerAccount> accounts = brokerAccountRepository.findByUserId(query.userId())
            .stream()
            .filter(account -> !account.isInline())
            .toList();

        LOG.debug("Found {} broker accounts for user {}", accounts.size(), query.userId().value());
        return accounts;
    }
}
