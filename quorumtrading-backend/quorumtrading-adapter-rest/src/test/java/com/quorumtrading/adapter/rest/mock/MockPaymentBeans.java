package com.quorumtrading.adapter.rest.mock;

import com.quorumtrading.application.port.out.PaymentPort;
import com.quorumtrading.application.port.out.PaymentTransactionRepository;
import com.quorumtrading.application.usecase.TopUpBudgetUseCase;
import com.quorumtrading.application.usecase.payment.CreateCheckoutSessionUseCase;
import com.quorumtrading.application.usecase.payment.ProcessPaymentWebhookUseCase;
import com.quorumtrading.model.entity.PaymentTransaction;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.Mock;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mock payment beans for testing the REST adapter in isolation.
 * These mocks are only active during tests.
 */
@ApplicationScoped
public class MockPaymentBeans {

    @Produces
    @Mock
    @ApplicationScoped
    @Alternative
    @Priority(1)
    public PaymentPort mockPaymentPort() {
        PaymentPort mockPort = mock(PaymentPort.class);
        when(mockPort.isConfigured()).thenReturn(false);
        when(mockPort.getProviderName()).thenReturn("mock");
        when(mockPort.createCheckoutSession(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new PaymentPort.CheckoutSessionResult("cs_test_123", "https://checkout.stripe.com/test", "secret_123", 1000L, "usd"));
        when(mockPort.processWebhook(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new PaymentPort.WebhookResult("checkout.session.completed", "cs_test_123", null, 1000L, "usd", true, false));
        return mockPort;
    }

    @Produces
    @Mock
    @Alternative
    @Priority(1)
    public PaymentTransactionRepository mockPaymentTransactionRepository() {
        return new PaymentTransactionRepository() {
            @Override
            public PaymentTransaction save(PaymentTransaction transaction) {
                return transaction;
            }

            @Override
            public Optional<PaymentTransaction> findById(UUID id) {
                return Optional.empty();
            }

            @Override
            public Optional<PaymentTransaction> findByProviderTransactionId(String providerTransactionId) {
                return Optional.empty();
            }

            @Override
            public List<PaymentTransaction> findByUserId(UserId userId) {
                return List.of();
            }

            @Override
            public List<PaymentTransaction> findSuccessfulByUserId(UserId userId) {
                return List.of();
            }

            @Override
            public long countSuccessful() {
                return 0;
            }

            @Override
            public int deleteByUserId(UserId userId) {
                return 0;
            }
        };
    }

    @Produces
    @Mock
    @Alternative
    @Priority(1)
    public CreateCheckoutSessionUseCase mockCreateCheckoutSessionUseCase(PaymentPort paymentPort, PaymentTransactionRepository txRepo) {
        return new CreateCheckoutSessionUseCase(paymentPort, txRepo);
    }

    @Produces
    @Mock
    @Alternative
    @Priority(1)
    public ProcessPaymentWebhookUseCase mockProcessPaymentWebhookUseCase(PaymentPort paymentPort, PaymentTransactionRepository txRepo, TopUpBudgetUseCase topUpUseCase) {
        return new ProcessPaymentWebhookUseCase(paymentPort, txRepo, topUpUseCase);
    }
}
