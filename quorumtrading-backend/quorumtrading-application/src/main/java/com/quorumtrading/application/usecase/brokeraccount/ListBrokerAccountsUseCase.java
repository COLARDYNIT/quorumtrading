package com.quorumtrading.application.usecase.brokeraccount;

import com.quorumtrading.application.usecase.query.ListBrokerAccountsQuery;
import com.quorumtrading.model.entity.BrokerAccount;

import java.util.List;

/**
 * Use case for listing broker accounts belonging to a user.
 *
 * <p>Returns all broker accounts (MOCK, ALPACA_PAPER, ALPACA_LIVE) owned by the specified user,
 * ordered by creation date (newest first).</p>
 */
public interface ListBrokerAccountsUseCase {

    /**
     * Execute the list broker accounts use case.
     *
     * @param query the query containing the user ID
     * @return list of broker accounts belonging to the user
     */
    List<BrokerAccount> execute(ListBrokerAccountsQuery query);
}
