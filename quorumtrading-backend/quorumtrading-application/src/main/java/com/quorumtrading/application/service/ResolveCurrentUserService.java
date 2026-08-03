package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.valueobject.UserId;

import java.util.NoSuchElementException;

/**
 * Service implementation for resolving users by email.
 * Framework-agnostic, uses constructor injection.
 */
public class ResolveCurrentUserService implements ResolveCurrentUserUseCase {

    private final UserRepository userRepository;

    public ResolveCurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserId execute(String email) {
        return resolveUser(email).getId();
    }

    @Override
    public User resolveUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or blank");
        }
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new NoSuchElementException("User not found for email: " + email));
    }
}
