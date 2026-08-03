package com.quorumtrading.application.usecase.payment;

import com.quorumtrading.application.port.out.PaymentPort;
import com.quorumtrading.application.port.out.PaymentTransactionRepository;
import com.quorumtrading.application.usecase.TopUpBudgetUseCase;
import com.quorumtrading.model.entity.PaymentTransaction;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProcessPaymentWebhookUseCaseTest {

    private PaymentPort paymentPort;
    private PaymentTransactionRepository transactionRepository;
    private TopUpBudgetUseCase topUpBudgetUseCase;
    private ProcessPaymentWebhookUseCase useCase;

    private static final UserId USER_ID = new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final String PROVIDER_TX_ID = "cs_live_abc123";

    @BeforeEach
    void setUp() {
        paymentPort = mock(PaymentPort.class);
        transactionRepository = mock(PaymentTransactionRepository.class);
        topUpBudgetUseCase = mock(TopUpBudgetUseCase.class);
        useCase = new ProcessPaymentWebhookUseCase(paymentPort, transactionRepository, topUpBudgetUseCase);
    }

    /**
     * This is the critical test — the original bug was that the transaction was marked
     * as SUCCEEDED before the budget top-up, so the idempotency check (which re-loaded
     * the transaction) would see it as already processed and skip the top-up entirely.
     */
    @Test
    void successfulPayment_topsUpBudget_andMarksTransactionSucceeded() {
        // Arrange
        PaymentPort.WebhookResult webhookResult = new PaymentPort.WebhookResult(
                "checkout.session.completed", PROVIDER_TX_ID, USER_ID,
                500L, "usd", true, true
        );
        when(paymentPort.processWebhook(any(), any())).thenReturn(webhookResult);

        PaymentTransaction pendingTx = PaymentTransaction.createPending(
                USER_ID, "stripe", PROVIDER_TX_ID, new BigDecimal("5.00"), "usd", "Top-up"
        );
        when(transactionRepository.findByProviderTransactionId(PROVIDER_TX_ID))
                .thenReturn(Optional.of(pendingTx));

        // Act
        ProcessPaymentWebhookUseCase.Result result = useCase.execute("payload", "sig");

        // Assert: budget was topped up
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(topUpBudgetUseCase).execute(eq(USER_ID), amountCaptor.capture());
        assertThat(amountCaptor.getValue()).isEqualByComparingTo("5.00");

        // Assert: transaction was saved (marked as succeeded)
        verify(transactionRepository, atLeastOnce()).save(any(PaymentTransaction.class));

        assertThat(result.processed()).isTrue();
        assertThat(result.message()).contains("topped up");
    }

    @Test
    void duplicateWebhook_skipsTopUp() {
        // Arrange: transaction already succeeded
        PaymentPort.WebhookResult webhookResult = new PaymentPort.WebhookResult(
                "checkout.session.completed", PROVIDER_TX_ID, USER_ID,
                500L, "usd", true, true
        );
        when(paymentPort.processWebhook(any(), any())).thenReturn(webhookResult);

        PaymentTransaction succeededTx = PaymentTransaction.createPending(
                USER_ID, "stripe", PROVIDER_TX_ID, new BigDecimal("5.00"), "usd", "Top-up"
        );
        succeededTx.markAsSucceeded();
        when(transactionRepository.findByProviderTransactionId(PROVIDER_TX_ID))
                .thenReturn(Optional.of(succeededTx));

        // Act
        ProcessPaymentWebhookUseCase.Result result = useCase.execute("payload", "sig");

        // Assert: budget was NOT topped up (idempotency)
        verify(topUpBudgetUseCase, never()).execute(any(), any());
        assertThat(result.processed()).isTrue();
        assertThat(result.message()).contains("Already processed");
    }

    @Test
    void budgetTopUpFailure_doesNotMarkTransactionSucceeded() {
        // Arrange
        PaymentPort.WebhookResult webhookResult = new PaymentPort.WebhookResult(
                "checkout.session.completed", PROVIDER_TX_ID, USER_ID,
                500L, "usd", true, true
        );
        when(paymentPort.processWebhook(any(), any())).thenReturn(webhookResult);

        PaymentTransaction pendingTx = PaymentTransaction.createPending(
                USER_ID, "stripe", PROVIDER_TX_ID, new BigDecimal("5.00"), "usd", "Top-up"
        );
        when(transactionRepository.findByProviderTransactionId(PROVIDER_TX_ID))
                .thenReturn(Optional.of(pendingTx));

        when(topUpBudgetUseCase.execute(any(), any()))
                .thenThrow(new RuntimeException("DB connection failed"));

        // Act + assert: thrown, not returned as a "not processed" result.
        //
        // Returning a Result made the REST layer answer the provider 200 OK, which it reads as
        // "delivered, done" — so a customer who had paid never received their budget and nothing
        // ever retried. Throwing produces a 5xx and a redelivery.
        assertThatThrownBy(() -> useCase.execute("payload", "sig"))
                .isInstanceOf(PaymentPort.PaymentException.class)
                .hasMessageContaining("budget top-up failed");

        // Transaction must still be PENDING, so the redelivered webhook retries the top-up
        // instead of being skipped as already handled.
        verify(transactionRepository, never()).save(any());
        assertThat(pendingTx.isPending()).isTrue();
    }

    @Test
    void webhookVerificationFailure_isThrownSoTheCallerCanAnswer400() {
        when(paymentPort.processWebhook(any(), any()))
                .thenThrow(new PaymentPort.PaymentException("Invalid signature", "INVALID_SIGNATURE"));

        // Swallowing this returned 200 OK for a delivery whose signature did not verify, which
        // left the REST layer's INVALID_SIGNATURE branch unreachable and acknowledged forgeries.
        assertThatThrownBy(() -> useCase.execute("payload", "bad-sig"))
                .isInstanceOf(PaymentPort.PaymentException.class)
                .hasMessageContaining("Invalid signature");

        verify(topUpBudgetUseCase, never()).execute(any(), any());
    }

    @Test
    void nonTopUpEvent_processesWithoutBudgetChange() {
        PaymentPort.WebhookResult webhookResult = new PaymentPort.WebhookResult(
                "checkout.session.expired", PROVIDER_TX_ID, null,
                null, null, false, false
        );
        when(paymentPort.processWebhook(any(), any())).thenReturn(webhookResult);

        PaymentTransaction pendingTx = PaymentTransaction.createPending(
                USER_ID, "stripe", PROVIDER_TX_ID, new BigDecimal("5.00"), "usd", "Top-up"
        );
        when(transactionRepository.findByProviderTransactionId(PROVIDER_TX_ID))
                .thenReturn(Optional.of(pendingTx));

        ProcessPaymentWebhookUseCase.Result result = useCase.execute("payload", "sig");

        assertThat(result.processed()).isTrue();
        verify(topUpBudgetUseCase, never()).execute(any(), any());
    }
}
