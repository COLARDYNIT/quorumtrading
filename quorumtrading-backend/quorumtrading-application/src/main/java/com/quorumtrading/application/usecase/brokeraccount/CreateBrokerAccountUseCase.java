package com.quorumtrading.application.usecase.brokeraccount;

import com.quorumtrading.application.usecase.command.CreateBrokerAccountCommand;
import com.quorumtrading.model.entity.BrokerAccount;

/**
 * Use case for creating a new broker account.
 *
 * <p><b>Creation Flow by Broker Type:</b></p>
 * <ul>
 *   <li><b>MOCK:</b> Creates account directly with MockBrokerConfig - no external validation needed</li>
 *   <li><b>ALPACA_PAPER/ALPACA_LIVE:</b> Validates credentials via broker API before saving</li>
 *   <li><b>SAXO_SIM/SAXO_LIVE:</b> Built from a completed OAuth consent flow via
 *       {@link #createFromOAuth}; the paste-key validation path is skipped because the OAuth
 *       handshake itself proves the credentials.</li>
 * </ul>
 *
 * <p><b>Validation Steps for Paste-Key Brokers (Alpaca):</b></p>
 * <ol>
 *   <li>Validate credentials format (non-empty API key and secret)</li>
 *   <li>Call broker API to verify credentials are valid</li>
 *   <li>Fetch current account balance from broker</li>
 *   <li>Create and persist the BrokerAccount</li>
 * </ol>
 *
 * <p><b>confirmLive guard (decision D8):</b> creating any {@link com.quorumtrading.model.enums.BrokerType#isLive()}
 * broker (ALPACA_LIVE / SAXO_LIVE) requires {@code confirmLive=true}, enforced server-side here so a
 * live account cannot be created without an explicit acknowledgement.</p>
 */
public interface CreateBrokerAccountUseCase {

    /**
     * Execute the create broker account use case.
     *
     * @param command the command containing broker account creation parameters
     * @return the newly created BrokerAccount
     * @throws IllegalArgumentException if validation fails (missing required fields, invalid broker type config,
     *         or a live broker without {@code confirmLive})
     * @throws com.quorumtrading.application.exception.BrokerCredentialValidationException if credential validation fails
     */
    BrokerAccount execute(CreateBrokerAccountCommand command);

    /**
     * Creates a Saxo broker account directly from a completed OAuth consent flow.
     *
     * <p>Unlike {@link #execute}, this entry point <strong>skips the paste-key credential
     * validation</strong>: a Saxo account's credentials are proven by the OAuth handshake, not by
     * a key/secret API check. The balance has already been fetched from Saxo during the consent
     * callback and is supplied on the request. The D8 {@code confirmLive} guard still applies for
     * {@code SAXO_LIVE}.</p>
     *
     * @param request the resolved OAuth account material (credentials, balance, confirmLive)
     * @return the newly created and persisted Saxo BrokerAccount
     * @throws IllegalArgumentException if the request is invalid (non-Saxo broker type, missing
     *         credentials/balance, or a live broker without {@code confirmLive})
     */
    BrokerAccount createFromOAuth(CreateBrokerAccountCommand.OAuthAccountCreation request);
}
