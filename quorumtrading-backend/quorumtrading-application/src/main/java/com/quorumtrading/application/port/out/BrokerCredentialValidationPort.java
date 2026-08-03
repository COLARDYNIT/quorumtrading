package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.Money;

import java.util.Optional;

/**
 * Port for validating broker credentials and fetching account information.
 *
 * <p>This port is used during broker account creation to validate that provided
 * API credentials are valid before persisting the account.</p>
 *
 * <p><b>Implementation Notes:</b></p>
 * <ul>
 *   <li>Real broker adapters (Alpaca) call the broker's API to validate credentials</li>
 *   <li>Mock broker adapter returns validation failure (MOCK accounts don't use credentials)</li>
 * </ul>
 */
public interface BrokerCredentialValidationPort {

    /**
     * Validates broker credentials by calling the broker's API.
     *
     * <p>This method should:</p>
     * <ol>
     *   <li>Connect to the broker's API using the provided credentials</li>
     *   <li>Make a lightweight API call (e.g., get account info) to verify credentials</li>
     *   <li>Return the validation result with account balance if successful</li>
     * </ol>
     *
     * @param brokerType  the type of broker to validate against
     * @param credentials the API credentials to validate
     * @return the validation result containing success status and account info if valid
     */
    CredentialValidationResult validate(BrokerType brokerType, BrokerCredentials credentials);

    /**
     * Reports whether this validator can validate credentials for the given broker type.
     *
     * <p>When more than one {@link BrokerCredentialValidationPort} implementation is on the
     * classpath (e.g. Alpaca + Saxo), the bootstrap selector uses this predicate to dispatch
     * over an ambiguous {@code Instance<BrokerCredentialValidationPort>} rather than calling
     * {@code Instance.get()} (which throws {@code AmbiguousResolutionException} on more than one
     * bean). Each implementation must return {@code true} only for the broker families it knows
     * how to validate.</p>
     *
     * @param brokerType the broker type a credential validation is being requested for
     * @return {@code true} if this implementation can validate the given broker type
     */
    boolean supports(BrokerType brokerType);

    /**
     * Result of a credential validation attempt.
     *
     * @param valid   true if credentials are valid, false otherwise
     * @param balance the account cash balance if validation succeeded (empty if failed)
     * @param equity  the account equity (cash + positions) if available (empty if failed or unavailable)
     * @param errorMessage error description if validation failed (empty if succeeded)
     */
    record CredentialValidationResult(
        boolean valid,
        Optional<Money> balance,
        Optional<Money> equity,
        Optional<String> errorMessage
    ) {
        /**
         * Creates a successful validation result with balance and equity.
         *
         * @param balance the cash balance fetched from the broker
         * @param equity  the total equity (cash + positions market value)
         * @return a successful validation result
         */
        public static CredentialValidationResult success(Money balance, Money equity) {
            return new CredentialValidationResult(true, Optional.of(balance), Optional.ofNullable(equity), Optional.empty());
        }

        /**
         * Creates a successful validation result with balance only (no equity).
         *
         * @param balance the account balance fetched from the broker
         * @return a successful validation result
         */
        public static CredentialValidationResult success(Money balance) {
            return new CredentialValidationResult(true, Optional.of(balance), Optional.empty(), Optional.empty());
        }

        /**
         * Creates a failed validation result.
         *
         * @param errorMessage description of why validation failed
         * @return a failed validation result
         */
        public static CredentialValidationResult failure(String errorMessage) {
            return new CredentialValidationResult(false, Optional.empty(), Optional.empty(), Optional.of(errorMessage));
        }
    }
}
