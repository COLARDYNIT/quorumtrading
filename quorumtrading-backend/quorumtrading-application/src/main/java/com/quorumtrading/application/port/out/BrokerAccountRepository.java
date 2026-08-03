package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for BrokerAccount entities.
 * Implemented by persistence adapters to provide data access.
 *
 * <p>This port defines the contract for broker account persistence operations.
 * The implementation handles mapping between domain entities and JPA entities,
 * including credential encryption and MockBrokerConfig JSON serialization.</p>
 */
public interface BrokerAccountRepository {

    /**
     * Saves a broker account (creates or updates).
     *
     * @param account the broker account to save
     * @return the saved broker account with updated timestamps
     */
    BrokerAccount save(BrokerAccount account);

    /**
     * Finds a broker account by its ID.
     *
     * @param id the broker account ID
     * @return an Optional containing the account if found, empty otherwise
     */
    Optional<BrokerAccount> findById(BrokerAccountId id);

    /**
     * Batch-finds broker accounts by id in a single query (avoids N+1 lookups
     * when mapping simulation lists). Unknown ids are simply absent from the
     * result; order is unspecified. An empty or null collection yields an
     * empty list.
     *
     * @param ids the broker account ids to load
     * @return the broker accounts found for the given ids
     */
    List<BrokerAccount> findByIds(Collection<BrokerAccountId> ids);

    /**
     * Finds all broker accounts belonging to a user.
     *
     * @param userId the user's ID
     * @return a list of broker accounts owned by the user
     */
    List<BrokerAccount> findByUserId(UserId userId);

    /**
     * Deletes a broker account by its ID.
     *
     * @param id the broker account ID to delete
     */
    void delete(BrokerAccountId id);

    /**
     * Checks if a broker account exists by its ID.
     *
     * @param id the broker account ID
     * @return true if the account exists, false otherwise
     */
    boolean existsById(BrokerAccountId id);

    /**
     * Finds all broker accounts with a given status.
     *
     * @param status the account status to filter by
     * @return a list of broker accounts with the given status
     */
    List<BrokerAccount> findAllByStatus(AccountStatus status);
}
