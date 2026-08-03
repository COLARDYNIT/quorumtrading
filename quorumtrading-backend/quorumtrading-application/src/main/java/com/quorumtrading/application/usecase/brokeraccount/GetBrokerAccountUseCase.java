package com.quorumtrading.application.usecase.brokeraccount;

import com.quorumtrading.application.usecase.query.GetBrokerAccountQuery;
import com.quorumtrading.model.entity.BrokerAccount;

/**
 * Use case for retrieving a single broker account by ID.
 */
public interface GetBrokerAccountUseCase {

    /**
     * Execute the get broker account use case.
     *
     * @param query the query containing the broker account ID
     * @return the broker account if found
     * @throws com.quorumtrading.application.exception.EntityNotFoundException if broker account not found
     */
    BrokerAccount execute(GetBrokerAccountQuery query);
}
