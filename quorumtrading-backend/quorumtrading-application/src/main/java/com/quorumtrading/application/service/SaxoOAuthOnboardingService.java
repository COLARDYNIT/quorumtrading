package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.OAuthStateStorePort;
import com.quorumtrading.application.port.out.OAuthTokens;
import com.quorumtrading.application.port.out.SaxoOAuthClientPort;
import com.quorumtrading.application.usecase.brokeraccount.CreateBrokerAccountUseCase;
import com.quorumtrading.application.usecase.command.CreateBrokerAccountCommand;
import com.quorumtrading.application.usecase.saxo.CompleteSaxoOnboardingUseCase;
import com.quorumtrading.application.usecase.saxo.OnboardingStart;
import com.quorumtrading.application.usecase.saxo.OnboardingState;
import com.quorumtrading.application.usecase.saxo.StartSaxoOnboardingUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Saxo OAuth onboarding service (WP {@code OA1-onboarding-service}).
 *
 * <p>Drives the interactive authorization-code flow that links a user's Saxo login to a
 * {@link BrokerAccount}, implementing both halves of the seam:</p>
 * <ul>
 *   <li>{@link #start(StartSaxoOnboardingUseCase.Command) start} — mints a single-use {@code state},
 *       stashes the originating {@link OnboardingState} under that state with a TTL via
 *       {@link OAuthStateStorePort}, and returns the Saxo authorize URL the REST edge redirects to.
 *       No broker account exists yet.</li>
 *   <li>{@link #complete(CompleteSaxoOnboardingUseCase.Command) complete} — atomically consumes the
 *       state ({@link OAuthStateStorePort#takeOnce}); a forged, expired, or already-consumed state
 *       is rejected. It then exchanges the authorization code for the initial rotating token pair
 *       via {@link SaxoOAuthClientPort}, builds {@link SaxoOAuthCredentials}, and creates the
 *       OAuth-backed account via {@link CreateBrokerAccountUseCase#createFromOAuth}, which enforces
 *       the D8 {@code confirmLive} guard for {@code SAXO_LIVE}.</li>
 * </ul>
 *
 * <p><b>Confidential client — no PKCE.</b> Saxo's server-side OAuth app is a <em>confidential</em>
 * client that authenticates the token call with its app key/secret via HTTP&nbsp;Basic
 * (docs/operations/SAXO_ANSWERS.md §1.5). PKCE is the public/native-app variant and is deliberately
 * <b>not</b> used here: the authorize URL carries only {@code response_type=code}, {@code client_id},
 * {@code redirect_uri}, and the single-use {@code state}; no {@code code_challenge} is sent and no
 * code verifier is stashed in the state envelope. CSRF protection comes from the opaque single-use
 * {@code state} (minted here, consumed exactly once on callback).</p>
 *
 * <p><b>Balance.</b> The real Saxo {@code /port/v1/balances} fetch is deferred ({@code SB5}); this
 * service seeds the new account with a zero EUR balance and lets the broker balance sync reconcile
 * the real figure. EUR is the v1 Saxo settlement currency (plan §10, decision D7/CUR1).</p>
 *
 * <p>Per project convention this is a POJO wired in the bootstrap {@code ApplicationConfig}, not a
 * CDI-scoped bean.</p>
 */
public class SaxoOAuthOnboardingService
        implements StartSaxoOnboardingUseCase, CompleteSaxoOnboardingUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(SaxoOAuthOnboardingService.class);

    /** Number of random bytes backing the opaque single-use state. */
    private static final int STATE_BYTES = 32;
    /** Envelope format marker; bumped if the serialized layout ever changes. */
    private static final String ENVELOPE_VERSION = "v2";
    /** Field separator in the serialized state envelope. */
    private static final char FIELD_SEP = '|';

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final SaxoOAuthClientPort oauthClient;
    private final OAuthStateStorePort stateStore;
    private final CreateBrokerAccountUseCase createBrokerAccount;
    private final String appKey;
    private final String appSecret;
    private final String authorizeBaseUrl;
    private final Duration stateTtl;
    private final SecureRandom random;

    /**
     * @param oauthClient        the low-level Saxo OAuth2 token-endpoint port (non-null)
     * @param stateStore         single-use OAuth state storage with TTL (non-null)
     * @param createBrokerAccount the broker-account creation use case ({@code createFromOAuth}) (non-null)
     * @param appKey             the Saxo OAuth app key used as {@code client_id} in the authorize URL
     *                           and persisted on the credentials (non-blank)
     * @param appSecret          the Saxo OAuth app secret persisted on the credentials (non-blank)
     * @param authorizeBaseUrl   the Saxo authorization endpoint base URL the consent redirect targets
     *                           (non-blank)
     * @param stateTtl           how long an onboarding state remains valid before it expires
     *                           (non-null, positive)
     */
    public SaxoOAuthOnboardingService(
            SaxoOAuthClientPort oauthClient,
            OAuthStateStorePort stateStore,
            CreateBrokerAccountUseCase createBrokerAccount,
            String appKey,
            String appSecret,
            String authorizeBaseUrl,
            Duration stateTtl) {
        this(oauthClient, stateStore, createBrokerAccount, appKey, appSecret, authorizeBaseUrl,
                stateTtl, new SecureRandom());
    }

    /** Visible-for-testing constructor pinning the {@link SecureRandom} for deterministic state. */
    SaxoOAuthOnboardingService(
            SaxoOAuthClientPort oauthClient,
            OAuthStateStorePort stateStore,
            CreateBrokerAccountUseCase createBrokerAccount,
            String appKey,
            String appSecret,
            String authorizeBaseUrl,
            Duration stateTtl,
            SecureRandom random) {
        this.oauthClient = Objects.requireNonNull(oauthClient, "oauthClient cannot be null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore cannot be null");
        this.createBrokerAccount = Objects.requireNonNull(createBrokerAccount, "createBrokerAccount cannot be null");
        this.appKey = requireNonBlank(appKey, "appKey");
        this.appSecret = requireNonBlank(appSecret, "appSecret");
        this.authorizeBaseUrl = requireNonBlank(authorizeBaseUrl, "authorizeBaseUrl");
        this.stateTtl = Objects.requireNonNull(stateTtl, "stateTtl cannot be null");
        if (stateTtl.isZero() || stateTtl.isNegative()) {
            throw new IllegalArgumentException("stateTtl must be positive, was: " + stateTtl);
        }
        this.random = Objects.requireNonNull(random, "random cannot be null");
    }

    // --- StartSaxoOnboardingUseCase -----------------------------------------------------------

    @Override
    public OnboardingStart start(StartSaxoOnboardingUseCase.Command command) {
        Objects.requireNonNull(command, "command cannot be null");

        // Confidential flow: mint only the opaque single-use state (no PKCE). The state value is
        // opaque to the store; we serialize the onboarding context under it.
        String state = randomUrlSafe(STATE_BYTES);

        OnboardingState onboardingState = new OnboardingState(
                command.userId(),
                command.brokerType(),
                command.environment(),
                command.redirectUri(),
                command.confirmLive());

        stateStore.put(state, serialize(onboardingState), stateTtl);

        URI authorizeUrl = buildAuthorizeUrl(command, state);

        LOG.info("Started Saxo onboarding for user {} (brokerType={}, environment={}); minted single-use state",
                command.userId().value(), command.brokerType(), command.environment());

        return new OnboardingStart(authorizeUrl, state);
    }

    // --- CompleteSaxoOnboardingUseCase --------------------------------------------------------

    @Override
    public BrokerAccount complete(CompleteSaxoOnboardingUseCase.Command command) {
        Objects.requireNonNull(command, "command cannot be null");

        // Single-use consume: a forged, expired, or already-consumed state yields empty and is rejected.
        Optional<String> stored = stateStore.takeOnce(command.state());
        if (stored.isEmpty()) {
            LOG.warn("Rejected Saxo onboarding callback: state unknown, expired, or already consumed");
            throw new IllegalArgumentException(
                    "OAuth state is unknown, expired, or already consumed");
        }

        OnboardingState onboardingState = deserialize(stored.get());

        OAuthTokens tokens;
        try {
            tokens = oauthClient.exchangeAuthorizationCode(
                    command.authorizationCode(),
                    onboardingState.redirectUri(),
                    onboardingState.environment());
        } catch (IllegalArgumentException e) {
            LOG.warn("Saxo authorization-code exchange rejected for user {} (environment={})",
                    onboardingState.userId().value(), onboardingState.environment());
            throw e;
        }

        SaxoOAuthCredentials credentials = new SaxoOAuthCredentials(
                appKey,
                appSecret,
                tokens.refreshToken(),
                tokens.accessToken(),
                tokens.expiry(),
                onboardingState.environment());

        // SB5 (deferred) fetches the real Saxo balance; seed zero EUR and let the balance sync
        // reconcile it. EUR is the v1 Saxo settlement currency.
        Money seedBalance = Money.zero(Money.Currency.EUR);

        CreateBrokerAccountCommand.OAuthAccountCreation creation =
                new CreateBrokerAccountCommand.OAuthAccountCreation(
                        onboardingState.userId(),
                        defaultAccountName(onboardingState),
                        onboardingState.brokerType(),
                        credentials,
                        seedBalance,
                        null,
                        onboardingState.confirmLive());

        // createFromOAuth enforces the D8 confirmLive guard for SAXO_LIVE.
        BrokerAccount account = createBrokerAccount.createFromOAuth(creation);

        LOG.info("Completed Saxo onboarding for user {}: created account {} (brokerType={}, environment={})",
                onboardingState.userId().value(), account.getId().value(),
                onboardingState.brokerType(), onboardingState.environment());

        return account;
    }

    // --- helpers ------------------------------------------------------------------------------

    private URI buildAuthorizeUrl(StartSaxoOnboardingUseCase.Command command, String state) {
        // Confidential authorization-code flow (SAXO_ANSWERS §1.5): client_id + redirect_uri +
        // response_type=code + the opaque single-use state. No PKCE code_challenge — that is the
        // public/native-app variant and is not used for our server-side confidential client.
        StringBuilder url = new StringBuilder(authorizeBaseUrl);
        url.append(authorizeBaseUrl.indexOf('?') >= 0 ? '&' : '?');
        url.append("response_type=code");
        appendParam(url, "client_id", appKey);
        appendParam(url, "redirect_uri", command.redirectUri());
        appendParam(url, "state", state);
        return URI.create(url.toString());
    }

    private static void appendParam(StringBuilder url, String name, String value) {
        url.append('&')
           .append(name)
           .append('=')
           .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static String defaultAccountName(OnboardingState state) {
        return state.brokerType() == BrokerType.SAXO_LIVE ? "Saxo (Live)" : "Saxo (Sim)";
    }

    /** Generates a URL-safe, unpadded base64 string from {@code numBytes} of secure randomness. */
    private String randomUrlSafe(int numBytes) {
        byte[] bytes = new byte[numBytes];
        random.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    // --- state envelope (de)serialization -----------------------------------------------------
    //
    // The OAuthStateStorePort value is an opaque String, so the onboarding context is encoded into
    // a single line. The free-text field (redirectUri) is base64url-encoded so the FIELD_SEP
    // separator can never collide with field content. Confidential flow → no PKCE verifier here.
    //   v2|userId|brokerType|environment|b64(redirectUri)|confirmLive

    private static String serialize(OnboardingState state) {
        return ENVELOPE_VERSION + FIELD_SEP
                + state.userId().value() + FIELD_SEP
                + state.brokerType().name() + FIELD_SEP
                + state.environment().name() + FIELD_SEP
                + b64(state.redirectUri()) + FIELD_SEP
                + state.confirmLive();
    }

    private static OnboardingState deserialize(String value) {
        String[] parts = value.split("\\" + FIELD_SEP, -1);
        if (parts.length != 6 || !ENVELOPE_VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Malformed onboarding state envelope");
        }
        try {
            return new OnboardingState(
                    new UserId(UUID.fromString(parts[1])),
                    BrokerType.valueOf(parts[2]),
                    SaxoEnvironment.valueOf(parts[3]),
                    unb64(parts[4]),
                    Boolean.parseBoolean(parts[5]));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Malformed onboarding state envelope", e);
        }
    }

    private static String b64(String raw) {
        return URL_ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String encoded) {
        return new String(URL_DECODER.decode(encoded), StandardCharsets.UTF_8);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
        return value;
    }
}
