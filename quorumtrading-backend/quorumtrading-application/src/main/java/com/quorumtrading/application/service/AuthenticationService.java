package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.DeletedAccountHashRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.AuthenticateUserUseCase;
import com.quorumtrading.application.usecase.command.AuthenticateUserCommand;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.BudgetLimit;
import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Service implementation for user authentication through OAuth providers.
 * Handles both new user creation and existing user updates during authentication flow.
 * Optionally credits a welcome budget to new users.
 */
public class AuthenticationService implements AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final UserBudgetRepository userBudgetRepository;
    private final DeletedAccountHashRepository deletedAccountHashRepository;
    private final int welcomeBudgetCents;

    public AuthenticationService(UserRepository userRepository,
                                 UserBudgetRepository userBudgetRepository,
                                 DeletedAccountHashRepository deletedAccountHashRepository,
                                 int welcomeBudgetCents) {
        this.userRepository = Objects.requireNonNull(userRepository, "UserRepository cannot be null");
        this.userBudgetRepository = Objects.requireNonNull(userBudgetRepository, "UserBudgetRepository cannot be null");
        this.deletedAccountHashRepository = Objects.requireNonNull(deletedAccountHashRepository, "DeletedAccountHashRepository cannot be null");
        this.welcomeBudgetCents = welcomeBudgetCents;
    }

    /**
     * Authenticates a user through OAuth provider.
     * Creates a new user if email is not found, otherwise updates existing user
     * with new provider information and profile data.
     * New users receive a welcome budget if configured.
     *
     * @param command the authentication command containing user details
     * @return the authenticated user with updated login timestamp
     * @throws IllegalArgumentException if command is null
     */
    @Override
    public User execute(AuthenticateUserCommand command) {
        validateCommand(command);

        boolean isNewUser = userRepository.findByEmail(command.email()).isEmpty();

        User user = userRepository.findByEmail(command.email())
            .map(existing -> updateExistingUser(existing, command))
            .orElseGet(() -> createNewUser(command));

        user.recordLogin();

        if (isNewUser) {
            boolean isReturning = deletedAccountHashRepository.existsByEmailHash(sha256(command.email()));
            if (isReturning) {
                user.markAsReturningUser();
            }
        }

        User savedUser = userRepository.save(user);

        if (isNewUser && welcomeBudgetCents > 0 && !savedUser.isReturningUser()) {
            creditWelcomeBudget(savedUser);
        }

        return savedUser;
    }

    private void validateCommand(AuthenticateUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
    }

    private User updateExistingUser(User user, AuthenticateUserCommand command) {
        // Link new provider if not already linked
        if (!user.hasProvider(command.provider())) {
            user.addProvider(command.provider(), command.providerUserId());
        }

        // Update profile if provided
        if (command.displayName() != null || command.avatarUrl() != null) {
            user.updateProfile(
                command.displayName() != null ? command.displayName() : user.getDisplayName(),
                command.avatarUrl() != null ? command.avatarUrl() : user.getAvatarUrl()
            );
        }

        return user;
    }

    private User createNewUser(AuthenticateUserCommand command) {
        return User.create(
            UserId.generate(),
            command.email(),
            command.displayName(),
            command.avatarUrl(),
            command.provider()
        );
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void creditWelcomeBudget(User user) {
        BigDecimal dollars = BigDecimal.valueOf(welcomeBudgetCents).movePointLeft(2);
        UserBudget budget = UserBudget.createWithLimit(user.getId(), BudgetLimit.usd(dollars));
        userBudgetRepository.save(budget);

        user.markWelcomeBudgetCredited();
        userRepository.save(user);
    }
}
