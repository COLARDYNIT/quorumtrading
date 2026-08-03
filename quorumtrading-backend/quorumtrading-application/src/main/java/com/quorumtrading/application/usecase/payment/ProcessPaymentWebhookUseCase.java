package com.quorumtrading.application.usecase.payment;

import com.quorumtrading.application.port.out.PaymentPort;
import com.quorumtrading.application.port.out.PaymentTransactionRepository;
import com.quorumtrading.application.usecase.TopUpBudgetUseCase;
import com.quorumtrading.model.entity.PaymentTransaction;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Use case for processing payment provider webhooks.
 * Validates the webhook, updates the transaction status, and tops up the budget if successful.
 */
public class ProcessPaymentWebhookUseCase {

    private static final Logger LOG = Logger.getLogger(ProcessPaymentWebhookUseCase.class);

    private final PaymentPort paymentPort;
    private final PaymentTransactionRepository transactionRepository;
    private final TopUpBudgetUseCase topUpBudgetUseCase;

    public ProcessPaymentWebhookUseCase(
            PaymentPort paymentPort,
            PaymentTransactionRepository transactionRepository,
            TopUpBudgetUseCase topUpBudgetUseCase
    ) {
        this.paymentPort = paymentPort;
        this.transactionRepository = transactionRepository;
        this.topUpBudgetUseCase = topUpBudgetUseCase;
    }

    /**
     * Result of processing a webhook.
     */
    public record Result(
            String eventType,
            boolean processed,
            String message
    ) {}

    /**
     * Process a webhook from the payment provider.
     *
     * @param payload   Raw webhook payload
     * @param signature Signature header for verification
     * @return Processing result
     */
    public Result execute(String payload, String signature) {
        LOG.debug("Processing payment webhook");

        // Verify and parse the webhook
        PaymentPort.WebhookResult webhookResult;
        try {
            webhookResult = paymentPort.processWebhook(payload, signature);
        } catch (PaymentPort.PaymentException e) {
            // Rethrown, not swallowed. Returning a Result here meant the caller answered the
            // provider 200 OK for a delivery whose signature did not verify — the resource's
            // 400 branch for INVALID_SIGNATURE was unreachable, and a forged callback was
            // acknowledged as received.
            LOG.warnf("Webhook verification failed: %s", e.getMessage());
            throw e;
        }

        LOG.infof("Received webhook event: %s for transaction %s",
                webhookResult.eventType(), webhookResult.providerTxId());

        // Idempotency check BEFORE any state mutation: skip if transaction already processed
        if (webhookResult.providerTxId() != null) {
            var existingTx = transactionRepository.findByProviderTransactionId(webhookResult.providerTxId());
            if (existingTx.isPresent() && !existingTx.get().isPending()) {
                LOG.infof("Ignoring duplicate webhook for already-processed transaction: %s (status: %s)",
                        webhookResult.providerTxId(), existingTx.get().getStatus());
                return new Result(webhookResult.eventType(), true,
                        "Already processed (idempotent)");
            }
        }

        // Top up budget BEFORE marking transaction as succeeded — prevents the race
        // where updating the status first would cause the idempotency check above to
        // skip the top-up on the same webhook invocation.
        if (webhookResult.shouldTopUpBudget() && webhookResult.succeeded() && webhookResult.userId() != null) {
            try {
                // Convert cents to dollars
                BigDecimal amountUsd = BigDecimal.valueOf(webhookResult.amountCents())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                topUpBudgetUseCase.execute(webhookResult.userId(), amountUsd);

                LOG.infof("Budget topped up for user %s: $%.2f",
                        webhookResult.userId(), amountUsd);
            } catch (Exception e) {
                LOG.errorf(e, "Failed to top up budget for user %s", webhookResult.userId());
                // Don't mark transaction as succeeded if budget top-up failed.
                //
                // Thrown rather than returned: a Result made the resource answer 200, which the
                // payment provider reads as "delivered, done" and never retries — so a customer
                // who had genuinely paid simply never received their budget, and nothing retried
                // or alerted. A 5xx gets the webhook redelivered, and redelivery is safe: the
                // transaction is still pending here, so the idempotency check above lets the
                // top-up run again rather than skipping it.
                throw new PaymentPort.PaymentException(
                        "Payment succeeded but budget top-up failed: " + e.getMessage(),
                        "TOP_UP_FAILED", e);
            }
        }

        // Update transaction status AFTER budget top-up succeeded
        if (webhookResult.providerTxId() != null) {
            transactionRepository.findByProviderTransactionId(webhookResult.providerTxId())
                    .ifPresentOrElse(
                            transaction -> updateTransaction(transaction, webhookResult),
                            () -> LOG.warnf("Transaction not found for provider ID: %s", webhookResult.providerTxId())
                    );
        }

        if (webhookResult.shouldTopUpBudget() && webhookResult.succeeded()) {
            BigDecimal amountUsd = BigDecimal.valueOf(webhookResult.amountCents())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return new Result(
                    webhookResult.eventType(),
                    true,
                    "Payment succeeded, budget topped up by $" + amountUsd
            );
        }

        return new Result(
                webhookResult.eventType(),
                true,
                webhookResult.succeeded() ? "Payment processed successfully" : "Payment event processed"
        );
    }

    private void updateTransaction(PaymentTransaction transaction, PaymentPort.WebhookResult webhookResult) {
        try {
            if (webhookResult.succeeded() && transaction.isPending()) {
                transaction.markAsSucceeded();
                transactionRepository.save(transaction);
                LOG.infof("Transaction %s marked as succeeded", transaction.getId());
            } else if (!webhookResult.succeeded() && transaction.isPending()) {
                // Determine if it was canceled or failed based on event type
                if (webhookResult.eventType().contains("canceled") || webhookResult.eventType().contains("expired")) {
                    transaction.markAsCanceled();
                } else {
                    transaction.markAsFailed();
                }
                transactionRepository.save(transaction);
                LOG.infof("Transaction %s marked as %s", transaction.getId(), transaction.getStatus());
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to update transaction %s", transaction.getId());
        }
    }
}
