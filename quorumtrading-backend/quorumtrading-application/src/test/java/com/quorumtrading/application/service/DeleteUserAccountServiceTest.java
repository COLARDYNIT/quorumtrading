package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.DeletedAccountHashRepository;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.PaymentTransactionRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DeleteUserAccountServiceTest {

    private UserRepository userRepository;
    private StrategyRepository strategyRepository;
    private MarketResearchRepository marketResearchRepository;
    private PaymentTransactionRepository paymentTransactionRepository;
    private DeletedAccountHashRepository deletedAccountHashRepository;
    private DeleteUserAccountService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        strategyRepository = mock(StrategyRepository.class);
        marketResearchRepository = mock(MarketResearchRepository.class);
        paymentTransactionRepository = mock(PaymentTransactionRepository.class);
        deletedAccountHashRepository = mock(DeletedAccountHashRepository.class);
        service = new DeleteUserAccountService(
            userRepository, strategyRepository, marketResearchRepository,
            paymentTransactionRepository, deletedAccountHashRepository
        );
    }

    @Test
    void shouldStoreEmailHashBeforeDeletion() {
        UserId userId = UserId.generate();
        User user = User.create(userId, "delete-me@example.com", "Delete Me", null, OAuthProvider.GOOGLE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(strategyRepository.findByOwnerId(userId)).thenReturn(List.of());
        when(marketResearchRepository.findByOwnerId(userId)).thenReturn(List.of());
        when(paymentTransactionRepository.deleteByUserId(userId)).thenReturn(0);
        when(userRepository.deleteById(userId)).thenReturn(true);

        service.execute(userId);

        // Verify email hash was stored
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> dateCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(deletedAccountHashRepository).save(hashCaptor.capture(), dateCaptor.capture());

        String expectedHash = DeleteUserAccountService.sha256("delete-me@example.com");
        assertThat(hashCaptor.getValue()).isEqualTo(expectedHash);
        assertThat(dateCaptor.getValue()).isEqualTo(user.getCreatedAt());
    }

    @Test
    void shouldStoreEmailHashBeforeAnyDeletion() {
        UserId userId = UserId.generate();
        User user = User.create(userId, "order-test@example.com", "Order Test", null, OAuthProvider.GOOGLE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(strategyRepository.findByOwnerId(userId)).thenReturn(List.of());
        when(marketResearchRepository.findByOwnerId(userId)).thenReturn(List.of());
        when(paymentTransactionRepository.deleteByUserId(userId)).thenReturn(0);
        when(userRepository.deleteById(userId)).thenReturn(true);

        service.execute(userId);

        // Verify hash save happened (before delete)
        var inOrder = inOrder(deletedAccountHashRepository, strategyRepository, userRepository);
        inOrder.verify(deletedAccountHashRepository).save(anyString(), any(Instant.class));
        inOrder.verify(userRepository).deleteById(userId);
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        UserId userId = UserId.generate();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(userId))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("User not found");

        verify(deletedAccountHashRepository, never()).save(anyString(), any(Instant.class));
    }

    @Test
    void shouldProduceDeterministicSha256Hash() {
        String hash1 = DeleteUserAccountService.sha256("test@example.com");
        String hash2 = DeleteUserAccountService.sha256("test@example.com");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    void shouldProduceCaseInsensitiveSha256Hash() {
        String hashLower = DeleteUserAccountService.sha256("test@example.com");
        String hashUpper = DeleteUserAccountService.sha256("TEST@EXAMPLE.COM");
        assertThat(hashLower).isEqualTo(hashUpper);
    }
}
