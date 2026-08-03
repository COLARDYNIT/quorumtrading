package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entities.
 * Implemented by persistence adapters to provide data access.
 */
public interface UserRepository {
    User save(User user);
    Optional<User> findById(UserId id);
    Optional<User> findByEmail(String email);
    Optional<User> findByProviderUserId(String provider, String providerUserId);
    boolean existsByEmail(String email);

    /**
     * Count the total number of users.
     *
     * @return total number of users
     */
    long count();

    /**
     * Delete a user by their ID.
     *
     * @param id the user's ID
     * @return true if the user was deleted, false if not found
     */
    boolean deleteById(UserId id);

    /**
     * Find the most recently created users.
     *
     * @param limit maximum number of users to return
     * @return list of users ordered by createdAt DESC
     */
    List<User> findRecent(int limit);

    /**
     * Search users by email or display name (case-insensitive).
     *
     * @param query search term
     * @param limit maximum number of results
     * @return matching users
     */
    List<User> search(String query, int limit);
}
