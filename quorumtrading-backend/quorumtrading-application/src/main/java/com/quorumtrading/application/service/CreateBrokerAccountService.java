package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.BrokerCredentialValidationException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort.CredentialValidationResult;
import com.quorumtrading.application.usecase.brokeraccount.CreateBrokerAccountUseCase;
import com.quorumtrading.application.usecase.command.CreateBrokerAccountCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service implementation for creating broker accounts.
 */
public class CreateBrokerAccountService implements CreateBrokerAccountUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CreateBrokerAccountService.class);

    private final BrokerAccountRepository brokerAccountRepository;
    private final BrokerCredentialValidationPort credentialValidationPort;

    public CreateBrokerAccountService(
            BrokerAccountRepository brokerAccountRepository,
            BrokerCredentialValidationPort credentialValidationPort) {
        this.brokerAccountRepository = brokerAccountRepository;
        this.credentialValidationPort = credentialValidationPort;
    }

    @Override
    public BrokerAccount execute(CreateBrokerAccountCommand command) {
        LOG.info("Creating broker account '{}' of type {} for user {}",
            command.name(), command.brokerType(), command.userId().value());

        // Validate command based on broker type
        validateCommand(command);

        BrokerAccount account;

        if (command.brokerType() == BrokerType.MOCK) {
            // Mock accounts don't need credential validation
            account = BrokerAccount.createMock(
                command.userId(),
                command.name(),
                command.mockConfig(),
                command.inline()
            );
            LOG.debug("Created mock broker account with initial capital: {}",
                command.mockConfig().initialCapital());
        } else {
            // Real brokers need credential validation
            if (credentialValidationPort == null) {
                throw new IllegalStateException(
                    "BrokerCredentialValidationPort not available. " +
                    "Cannot create real broker account without credential validation.");
            }

            CredentialValidationResult validationResult = credentialValidationPort
                .validate(command.brokerType(), command.credentials());

            if (!validationResult.valid()) {
                String errorMessage = validationResult.errorMessage().orElse("Unknown error");
                LOG.warn("Credential validation failed for {}: {}", command.brokerType(), errorMessage);
                throw new BrokerCredentialValidationException(command.brokerType(), errorMessage);
            }

            // Create account with validated balance and equity
            Money balance = validationResult.balance().orElseThrow(() ->
                new IllegalStateException("Validation succeeded but no balance returned"));
            Money equity = validationResult.equity().orElse(null);
            account = BrokerAccount.createReal(
                command.userId(),
                command.name(),
                command.brokerType(),
                command.credentials(),
                balance,
                equity
            );
            LOG.debug("Created {} broker account with balance: {}, equity: {}",
                command.brokerType(), balance, equity);
        }

        // Persist the account
        BrokerAccount saved = brokerAccountRepository.save(account);
        LOG.info("Saved broker account {} for user {}", saved.getId().value(), command.userId().value());

        return saved;
    }

    @Override
    public BrokerAccount createFromOAuth(CreateBrokerAccountCommand.OAuthAccountCreation request) {
        LOG.info("Creating Saxo broker account '{}' of type {} for user {} from OAuth consent flow",
            request.name(), request.brokerType(), request.userId().value());

        validateOAuthRequest(request);

        // OAuth accounts are validated by the consent flow, NOT by a paste-key credential check.
        // The balance was fetched from Saxo during the callback and is supplied on the request,
        // so we bypass the BrokerCredentialValidationPort entirely.
        BrokerAccount account = BrokerAccount.createReal(
            request.userId(),
            request.name(),
            request.brokerType(),
            request.credentials(),
            request.balance(),
            request.equity()
        );
        LOG.debug("Built {} OAuth broker account with balance: {}, equity: {} (skipped paste-key validation)",
            request.brokerType(), request.balance(), request.equity());

        BrokerAccount saved = brokerAccountRepository.save(account);
        LOG.info("Saved Saxo broker account {} for user {}", saved.getId().value(), request.userId().value());

        return saved;
    }

    private void validateOAuthRequest(CreateBrokerAccountCommand.OAuthAccountCreation request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Account name cannot be blank");
        }
        BrokerType brokerType = request.brokerType();
        if (brokerType == null || !brokerType.isSaxo()) {
            throw new IllegalArgumentException(
                "createFromOAuth only supports Saxo broker types (SAXO_SIM/SAXO_LIVE), got " + brokerType);
        }
        SaxoOAuthCredentials credentials = request.credentials();
        if (credentials == null) {
            throw new IllegalArgumentException("OAuth credentials are required for Saxo account creation");
        }
        if (request.balance() == null) {
            throw new IllegalArgumentException("Balance is required when creating a Saxo account from OAuth");
        }
        // D8: any live broker requires explicit confirmation, OAuth path included.
        requireLiveConfirmation(brokerType, request.confirmLive());
    }

    /**
     * Enforces decision D8 server-side: any broker whose {@link BrokerType#isLive()} is {@code true}
     * (today ALPACA_LIVE and SAXO_LIVE) cannot be created unless the caller explicitly acknowledged
     * the live (real-money) nature of the account via {@code confirmLive}. Non-live brokers ignore
     * the flag.
     *
     * @param brokerType  the broker type being created
     * @param confirmLive whether the caller acknowledged a live account
     * @throws IllegalArgumentException if a live broker is created without confirmation
     */
    private void requireLiveConfirmation(BrokerType brokerType, boolean confirmLive) {
        if (brokerType.isLive() && !confirmLive) {
            throw new IllegalArgumentException(
                "Creating a live (real-money) " + brokerType + " account requires confirmLive=true");
        }
    }

    private void validateCommand(CreateBrokerAccountCommand command) {
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("Account name cannot be blank");
        }

        if (command.brokerType() == BrokerType.MOCK) {
            if (command.mockConfig() == null) {
                throw new IllegalArgumentException("MockBrokerConfig is required for MOCK broker type");
            }
            if (command.credentials() != null) {
                throw new IllegalArgumentException("Credentials must be null for MOCK broker type");
            }
        } else {
            // Saxo accounts must be created via createFromOAuth (consent flow), not the
            // paste-key validation path: their credentials are proven by the OAuth handshake.
            if (command.brokerType().isSaxo()) {
                throw new IllegalArgumentException(
                    "Saxo broker accounts (" + command.brokerType() + ") must be created via the OAuth "
                        + "onboarding flow (createFromOAuth), not the paste-key validation path");
            }
            if (command.credentials() == null) {
                throw new IllegalArgumentException("Credentials are required for real broker types");
            }
            if (command.mockConfig() != null) {
                throw new IllegalArgumentException("MockBrokerConfig must be null for real broker types");
            }
            // D8: any live broker (ALPACA_LIVE / SAXO_LIVE) must be explicitly confirmed server-side.
            requireLiveConfirmation(command.brokerType(), command.confirmLive());
        }
    }
}
