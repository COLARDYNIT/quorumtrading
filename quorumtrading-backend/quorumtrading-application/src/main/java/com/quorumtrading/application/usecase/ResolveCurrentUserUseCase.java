package com.quorumtrading.application.usecase;

import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Use case for resolving the current authenticated user from their email.
 * This encapsulates the common pattern of looking up users by email address
 * from authentication tokens.
 */
public interface ResolveCurrentUserUseCase {

    /**
     * Resolve a user by their email address.
     *
     * @param email the email address from the authentication token
     * @return the user's ID
     * @throws java.util.NoSuchElementException if user not found
     */
    UserId execute(String email);

    /**
     * Resolve a user entity by their email address.
     *
     * @param email the email address from the authentication token
     * @return the full User entity
     * @throws java.util.NoSuchElementException if user not found
     */
    User resolveUser(String email);
}
