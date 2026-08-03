package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.DeletedAccountHashRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.command.AuthenticateUserCommand;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthenticationServiceTest {

    private UserRepository userRepository;
    private UserBudgetRepository userBudgetRepository;
    private DeletedAccountHashRepository deletedAccountHashRepository;
    private AuthenticationService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userBudgetRepository = mock(UserBudgetRepository.class);
        deletedAccountHashRepository = mock(DeletedAccountHashRepository.class);
        authService = new AuthenticationService(userRepository, userBudgetRepository, deletedAccountHashRepository, 200);
    }

    @Test
    void shouldCreateNewUserWhenEmailNotFound() {
        AuthenticateUserCommand command = new AuthenticateUserCommand(
            "new@example.com", "New User", "https://avatar.url",
            OAuthProvider.GOOGLE, "google-sub-123"
        );

        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userBudgetRepository.save(any(UserBudget.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deletedAccountHashRepository.existsByEmailHash(anyString())).thenReturn(false);

        User result = authService.execute(command);

        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getDisplayName()).isEqualTo("New User");
        assertThat(result.hasProvider(OAuthProvider.GOOGLE)).isTrue();
        assertThat(result.getLastLoginAt()).isNotNull();
    }

    @Test
    void shouldUpdateExistingUserWhenEmailFound() {
        User existingUser = User.create(
            UserId.generate(), "existing@example.com", "Old Name", null, OAuthProvider.GOOGLE
        );

        AuthenticateUserCommand command = new AuthenticateUserCommand(
            "existing@example.com", "Updated Name", "https://new-avatar.url",
            OAuthProvider.GOOGLE, "google-sub-456"
        );

        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.execute(command);

        assertThat(result.getEmail()).isEqualTo("existing@example.com");
        assertThat(result.getLastLoginAt()).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldLinkNewProviderToExistingUser() {
        User existingUser = User.create(
            UserId.generate(), "multi@example.com", "Multi User", null, OAuthProvider.GOOGLE
        );

        AuthenticateUserCommand command = new AuthenticateUserCommand(
            "multi@example.com", "Multi User", null,
            OAuthProvider.APPLE, "apple-sub-789"
        );

        when(userRepository.findByEmail("multi@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.execute(command);

        assertThat(result.hasProvider(OAuthProvider.GOOGLE)).isTrue();
        assertThat(result.hasProvider(OAuthProvider.APPLE)).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenCommandIsNull() {
        assertThatThrownBy(() -> authService.execute(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Command cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenUserRepositoryIsNull() {
        assertThatThrownBy(() -> new AuthenticationService(null, userBudgetRepository, deletedAccountHashRepository, 200))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("UserRepository cannot be null");
    }

    @Test
    void shouldCreateWelcomeBudgetForNewUser() {
        AuthenticateUserCommand command = new AuthenticateUserCommand(
            "new@example.com", "New User", null,
            OAuthProvider.GOOGLE, "google-sub-123"
        );

        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userBudgetRepository.save(any(UserBudget.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deletedAccountHashRepository.existsByEmailHash(anyString())).thenReturn(false);

        User result = authService.execute(command);

        // Budget should have been created with $2.00 limit
        ArgumentCaptor<UserBudget> budgetCaptor = ArgumentCaptor.forClass(UserBudget.class);
        verify(userBudgetRepository).save(budgetCaptor.capture());
        UserBudget budget = budgetCaptor.getValue();
        assertThat(budget.getLimit()).isPresent();
        assertThat(budget.getLimit().get().amount()).isEqualByComparingTo("2.00");
        assertThat(budget.getUserId()).isEqualTo(result.getId());

        // User should be marked as welcome budget credited
        assertThat(result.isWelcomeBudgetCredited()).isTrue();
    }

    @Test
    void shouldNotCreateWelcomeBudgetForExistingUser() {
        User existingUser = User.create(
            UserId.generate(), "existing@example.com", "Existing User", null, OAuthProvider.GOOGLE
        );

        AuthenticateUserCommand command = new AuthenticateUserCommand(
            "existing@example.com", "Existing User", null,
            OAuthProvider.GOOGLE, "google-sub-456"
        );

        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.execute(command);

        verify(userBudgetRepository, never()).save(any(UserBudget.class));
    }

    @Test
    void shouldNotCreateWelcomeBudgetWhenCentsIsZero() {
        AuthenticationService disabledService = new AuthenticationService(userRepository, userBudgetRepository, deletedAccountHashRepository, 0);

        AuthenticateUserCommand command = new AuthenticateUserCommand(
            "new@example.com", "New User", null,
            OAuthProvider.GOOGLE, "google-sub-123"
        );

        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deletedAccountHashRepository.existsByEmailHash(anyString())).thenReturn(false);

        User result = disabledService.execute(command);

        verify(userBudgetRepository, never()).save(any(UserBudget.class));
        assertThat(result.isWelcomeBudgetCredited()).isFalse();
    }

    @Test
    void shouldNotCreditWelcomeBudgetForReturningDeletedUser() {
        AuthenticateUserCommand command = new AuthenticateUserCommand(
            "returning@example.com", "Returning User", null,
            OAuthProvider.GOOGLE, "google-sub-returning"
        );

        when(userRepository.findByEmail("returning@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deletedAccountHashRepository.existsByEmailHash(anyString())).thenReturn(true);

        User result = authService.execute(command);

        assertThat(result.isReturningUser()).isTrue();
        assertThat(result.isWelcomeBudgetCredited()).isFalse();
        verify(userBudgetRepository, never()).save(any(UserBudget.class));
    }

    @Test
    void shouldCreditWelcomeBudgetForGenuineNewUser() {
        AuthenticateUserCommand command = new AuthenticateUserCommand(
            "genuinenew@example.com", "Genuine New", null,
            OAuthProvider.GOOGLE, "google-sub-genuine"
        );

        when(userRepository.findByEmail("genuinenew@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userBudgetRepository.save(any(UserBudget.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deletedAccountHashRepository.existsByEmailHash(anyString())).thenReturn(false);

        User result = authService.execute(command);

        assertThat(result.isReturningUser()).isFalse();
        assertThat(result.isWelcomeBudgetCredited()).isTrue();
        verify(userBudgetRepository).save(any(UserBudget.class));
    }
}
