package com.quorumtrading.application.usecase.payment;

import com.quorumtrading.application.port.out.PaymentPort;
import com.quorumtrading.application.port.out.PaymentTransactionRepository;
import com.quorumtrading.model.entity.PaymentTransaction;
import com.quorumtrading.model.valueobject.UserId;
import org.jboss.logging.Logger;

import java.math.BigDecimal;

/**
 * Use case for creating a payment checkout session.
 * Creates a checkout session with the payment provider and records the pending transaction.
 */
public class CreateCheckoutSessionUseCase {

    private static final Logger LOG = Logger.getLogger(CreateCheckoutSessionUseCase.class);

    private final PaymentPort paymentPort;
    private final PaymentTransactionRepository transactionRepository;

    public CreateCheckoutSessionUseCase(
            PaymentPort paymentPort,
            PaymentTransactionRepository transactionRepository
    ) {
        this.paymentPort = paymentPort;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Result of creating a checkout session.
     */
    public record Result(
            String sessionId,
            String checkoutUrl,
            String clientSecret,
            long amountCents,
            String currency
    ) {}

    /**
     * Create a checkout session for budget top-up.
     *
     * @param userId     The user requesting the top-up
     * @param amountUsd  The amount to top up in USD
     * @param successUrl URL to redirect after successful payment
     * @param cancelUrl  URL to redirect if payment is canceled
     * @return Checkout session details
     */
    public Result execute(UserId userId, BigDecimal amountUsd, String successUrl, String cancelUrl) {
        LOG.infof("Creating checkout session for user %s, amount $%.2f", userId, amountUsd);

        if (amountUsd == null || amountUsd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (!paymentPort.isConfigured()) {
            throw new PaymentPort.PaymentException(
                    "Payment provider is not configured",
                    "PROVIDER_NOT_CONFIGURED"
            );
        }

        // Create checkout session with payment provider
        PaymentPort.CheckoutSessionResult sessionResult = paymentPort.createCheckoutSession(
                userId,
                amountUsd,
                successUrl,
                cancelUrl
        );

        // Record the pending transaction
        PaymentTransaction transaction = PaymentTransaction.createPending(
                userId,
                paymentPort.getProviderName(),
                sessionResult.sessionId(),
                amountUsd,
                sessionResult.currency(),
                "Budget top-up"
        );
        transactionRepository.save(transaction);

        LOG.infof("Created checkout session %s for user %s, transaction %s",
                sessionResult.sessionId(), userId, transaction.getId());

        return new Result(
                sessionResult.sessionId(),
                sessionResult.checkoutUrl(),
                sessionResult.clientSecret(),
                sessionResult.amountCents(),
                sessionResult.currency()
        );
    }
}
