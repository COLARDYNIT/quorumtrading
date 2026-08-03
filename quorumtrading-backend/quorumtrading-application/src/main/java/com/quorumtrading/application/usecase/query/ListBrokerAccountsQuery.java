package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.UserId;

/**
 * Query for listing broker accounts belonging to a user.
 *
 * @param userId the user whose broker accounts to list
 */
public record ListBrokerAccountsQuery(
    UserId userId
) {
}
