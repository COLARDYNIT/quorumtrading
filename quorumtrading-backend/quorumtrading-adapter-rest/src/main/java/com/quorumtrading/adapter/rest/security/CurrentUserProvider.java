package com.quorumtrading.adapter.rest.security;

import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Provides the current authenticated user from Quarkus SecurityIdentity.
 *
 * <p>Works with both OIDC sessions (production) and dev_session cookies (dev/test)
 * via the DevSessionAuthMechanism. Both mechanisms populate SecurityIdentity with
 * the user's email as the principal name.</p>
 *
 * <p>Replaces the duplicated getCurrentUserId()/getDevSessionEmail() boilerplate
 * that was previously copy-pasted in every REST resource.</p>
 */
@RequestScoped
public class CurrentUserProvider {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    @Inject
    UserRepository userRepository;

    /**
     * Get the current authenticated user's ID.
     *
     * @return the authenticated user's ID
     * @throws NotAuthorizedException if no user is authenticated
     * @throws NotFoundException if the authenticated email does not match any user
     */
    public UserId getCurrentUserId() {
        String email = getEmail()
                .orElseThrow(() -> new NotAuthorizedException("Authentication required"));
        try {
            return resolveCurrentUserUseCase.execute(email);
        } catch (NoSuchElementException e) {
            throw new NotFoundException("Current user not found");
        }
    }

    /**
     * Get the current user's ID if authenticated, or empty if anonymous.
     *
     * @return the user's ID, or empty if not authenticated or user not found
     */
    public Optional<UserId> getCurrentUserIdOptional() {
        return getEmail()
                .flatMap(email -> userRepository.findByEmail(email)
                        .map(User::getId));
    }

    /**
     * Get the current authenticated user's email.
     *
     * @return the email if authenticated, empty if anonymous
     */
    public Optional<String> getEmail() {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return Optional.empty();
        }
        String name = securityIdentity.getPrincipal().getName();
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(name);
    }
}
