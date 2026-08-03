package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Command for creating a new broker account.
 *
 * <p><b>Required Fields by Broker Type:</b></p>
 * <ul>
 *   <li><b>MOCK:</b> mockConfig required, credentials must be null</li>
 *   <li><b>ALPACA_PAPER/ALPACA_LIVE:</b> credentials required ({@code ApiKeyCredentials}),
 *       mockConfig must be null; validated against the broker API before persisting</li>
 *   <li><b>SAXO_SIM/SAXO_LIVE:</b> credentials required ({@code SaxoOAuthCredentials}),
 *       mockConfig must be null. Saxo credentials are <em>not</em> validated by a paste-key
 *       API call — they are proven by the OAuth consent flow. Build these via
 *       {@link #forSaxoSim} / {@link #forSaxoLive} (or the OAuth onboarding service's
 *       {@code createFromOAuth} entry point), never through the paste-key path.</li>
 * </ul>
 *
 * <p><b>confirmLive guard (decision D8):</b> any broker whose {@link BrokerType#isLive()} is
 * {@code true} (today {@code ALPACA_LIVE} and {@code SAXO_LIVE}) requires {@code confirmLive=true}
 * for the create service to proceed. This is a server-side guard so a live account can never be
 * created without an explicit acknowledgement, regardless of which client (REST/MCP) issues the
 * command. Non-live broker types ignore the flag.</p>
 *
 * @param userId      the user who owns this broker account
 * @param name        display name for the broker account
 * @param brokerType  the type of broker (MOCK, ALPACA_PAPER, ALPACA_LIVE, SAXO_SIM, SAXO_LIVE)
 * @param credentials credential material for real brokers (null for MOCK)
 * @param mockConfig  configuration for mock broker (null for real brokers)
 * @param confirmLive explicit acknowledgement required for any {@link BrokerType#isLive()} broker
 * @param inline      MOCK only: the account exists to back one simulation, so it is hidden from
 *                    account listings and deleted with that simulation
 */
public record CreateBrokerAccountCommand(
    UserId userId,
    String name,
    BrokerType brokerType,
    BrokerCredentials credentials,
    MockBrokerConfig mockConfig,
    boolean confirmLive,
    boolean inline
) {

    /**
     * Backward-compatible constructor for callers that predate the inline flag.
     * Creates a user-owned (non-inline) account.
     */
    public CreateBrokerAccountCommand(
        UserId userId,
        String name,
        BrokerType brokerType,
        BrokerCredentials credentials,
        MockBrokerConfig mockConfig,
        boolean confirmLive
    ) {
        this(userId, name, brokerType, credentials, mockConfig, confirmLive, false);
    }

    /**
     * Creates a command for a mock broker account the user owns directly.
     *
     * @param userId     the user who owns this account
     * @param name       display name for the account
     * @param mockConfig mock broker configuration with costs and initial capital
     * @return a new CreateBrokerAccountCommand for MOCK type
     */
    public static CreateBrokerAccountCommand forMock(UserId userId, String name, MockBrokerConfig mockConfig) {
        return new CreateBrokerAccountCommand(userId, name, BrokerType.MOCK, null, mockConfig, false, false);
    }

    /**
     * Creates a command for a mock broker account owned by a single simulation.
     *
     * <p>Used when a simulation is created without a broker the user chose — the account is an
     * implementation detail of running that backtest, so it is hidden from listings and deleted
     * with the simulation instead of accumulating one orphan per run.</p>
     *
     * @param userId     the user the account is attributed to
     * @param name       display name for the account
     * @param mockConfig mock broker configuration with costs and initial capital
     * @return a new CreateBrokerAccountCommand for an inline MOCK account
     */
    public static CreateBrokerAccountCommand forInlineMock(UserId userId, String name, MockBrokerConfig mockConfig) {
        return new CreateBrokerAccountCommand(userId, name, BrokerType.MOCK, null, mockConfig, false, true);
    }

    /**
     * Creates a command for Alpaca paper trading account.
     *
     * @param userId      the user who owns this account
     * @param name        display name for the account
     * @param credentials Alpaca API credentials
     * @return a new CreateBrokerAccountCommand for ALPACA_PAPER type
     */
    public static CreateBrokerAccountCommand forAlpacaPaper(UserId userId, String name, BrokerCredentials credentials) {
        return new CreateBrokerAccountCommand(userId, name, BrokerType.ALPACA_PAPER, credentials, null, false);
    }

    /**
     * Creates a command for Alpaca live trading account.
     *
     * <p>This 3-arg overload preserves the pre-D8 behaviour: {@code confirmLive} is set to
     * {@code true} so existing Alpaca live-create callers keep working unchanged. The migration
     * of the Alpaca REST/UI consumers to pass an explicit acknowledgement is owned by
     * {@code RF8-alpaca-live-confirm} (Wave 4); do not retrofit those callers here.</p>
     *
     * @param userId      the user who owns this account
     * @param name        display name for the account
     * @param credentials Alpaca API credentials
     * @return a new CreateBrokerAccountCommand for ALPACA_LIVE type with {@code confirmLive=true}
     */
    public static CreateBrokerAccountCommand forAlpacaLive(UserId userId, String name, BrokerCredentials credentials) {
        return new CreateBrokerAccountCommand(userId, name, BrokerType.ALPACA_LIVE, credentials, null, true);
    }

    /**
     * Creates a command for a Saxo simulation (paper) account.
     *
     * <p>Carries {@link SaxoOAuthCredentials}; SAXO_SIM is not a live broker so the confirmLive
     * guard does not apply. These credentials are proven by the OAuth consent flow, not by a
     * paste-key validation call — see {@link CreateBrokerAccountCommand} class docs.</p>
     *
     * @param userId      the user who owns this account
     * @param name        display name for the account
     * @param credentials Saxo OAuth credentials targeting {@code SaxoEnvironment.SIM}
     * @return a new CreateBrokerAccountCommand for SAXO_SIM type
     */
    public static CreateBrokerAccountCommand forSaxoSim(UserId userId, String name, SaxoOAuthCredentials credentials) {
        return new CreateBrokerAccountCommand(userId, name, BrokerType.SAXO_SIM, credentials, null, false);
    }

    /**
     * Creates a command for a Saxo live trading account.
     *
     * <p>Carries {@link SaxoOAuthCredentials}. SAXO_LIVE is a live broker, so {@link BrokerType#isLive()}
     * is {@code true} and the create service rejects the command unless {@code confirmLive} is
     * {@code true} (decision D8).</p>
     *
     * @param userId      the user who owns this account
     * @param name        display name for the account
     * @param credentials Saxo OAuth credentials targeting {@code SaxoEnvironment.LIVE}
     * @param confirmLive explicit acknowledgement that this is a real-money account
     * @return a new CreateBrokerAccountCommand for SAXO_LIVE type
     */
    public static CreateBrokerAccountCommand forSaxoLive(
            UserId userId, String name, SaxoOAuthCredentials credentials, boolean confirmLive) {
        return new CreateBrokerAccountCommand(userId, name, BrokerType.SAXO_LIVE, credentials, null, confirmLive);
    }

    /**
     * Whether this command targets a live (real-money) broker, per decision D8. Drives the
     * server-side {@code confirmLive} guard in the create service.
     *
     * @return {@code true} if {@link BrokerType#isLive()} for this command's broker type
     */
    public boolean targetsLiveBroker() {
        return brokerType != null && brokerType.isLive();
    }

    /**
     * Material required to build a Saxo account directly from a completed OAuth consent flow,
     * bypassing the paste-key credential-validation path. Used by the OAuth onboarding service's
     * {@code createFromOAuth} entry point, which has already proven the credentials and fetched
     * the account balance from Saxo during the consent callback.
     *
     * @param userId      the user who owns this account
     * @param name        display name for the account
     * @param brokerType  the Saxo broker type (SAXO_SIM or SAXO_LIVE)
     * @param credentials the OAuth credentials minted by the consent flow
     * @param balance     the cash balance fetched from Saxo during onboarding (account currency)
     * @param equity      total equity (cash + positions), or {@code null} if unavailable
     * @param confirmLive explicit live acknowledgement (required for SAXO_LIVE)
     */
    public record OAuthAccountCreation(
        UserId userId,
        String name,
        BrokerType brokerType,
        SaxoOAuthCredentials credentials,
        Money balance,
        Money equity,
        boolean confirmLive
    ) {}
}
