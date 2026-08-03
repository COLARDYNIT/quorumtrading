package com.quorumtrading.adapter.broker.saxo.client;

import com.quorumtrading.application.port.out.BrokerTokenProviderPort;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Adds the Saxo OpenAPI {@code Authorization: Bearer <access-token>} header to all
 * Saxo broker REST-client requests, resolving the bearer for the <em>linked</em> broker
 * account per decision <b>D10</b>.
 *
 * <p>Unlike Alpaca (static API key + secret headers from config), Saxo uses per-account OAuth2
 * bearer tokens with a ~20-minute TTL and a rotating refresh token. A
 * {@link ClientHeadersFactory} has no per-request account context, so the account is supplied
 * out-of-band by the {@link com.quorumtrading.adapter.broker.saxo.SaxoBrokerAdapter}, which
 * pins the active {@link BrokerAccountId} on the {@code @RequestScoped} {@link SaxoRequestTokenHolder}
 * before issuing the call. This factory reads the holder and resolves a currently-valid bearer
 * via {@link BrokerTokenProviderPort} (which hides the whole refresh lifecycle).</p>
 *
 * <p>{@link BrokerTokenProviderPort} is injected via {@link Instance} so this module compiles
 * and boots even before the real provider impl is on the classpath (Wave 4 / {@code OA2}); until
 * then the provider is unsatisfied and this factory emits no bearer header. Likewise, if no
 * account is pinned on the holder (no Saxo operation is in flight) no header is added — a
 * caller that already attached an {@code Authorization} header keeps it untouched.</p>
 */
@ApplicationScoped
public class SaxoAuthHeadersFactory implements ClientHeadersFactory {

    private static final Logger LOG = Logger.getLogger(SaxoAuthHeadersFactory.class);

    static final String AUTHORIZATION_HEADER = "Authorization";
    static final String BEARER_PREFIX = "Bearer ";

    /**
     * Supplies a currently-valid bearer access token for the account being called.
     * Optional at scaffold time — the real impl lands in Wave 4 (OA2); until then this
     * {@code Instance} is unsatisfied and no bearer header is added.
     */
    @Inject
    Instance<BrokerTokenProviderPort> tokenProvider;

    /**
     * Request-scoped holder carrying the {@link BrokerAccountId} the in-flight Saxo call must
     * authenticate as (set by {@code SaxoBrokerAdapter} per D10). Injected via {@link Instance}
     * so an inactive request context (no Saxo operation in flight) is tolerated rather than
     * throwing a {@code ContextNotActiveException}.
     */
    @Inject
    Instance<SaxoRequestTokenHolder> tokenHolder;

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
                                                  MultivaluedMap<String, String> clientOutgoingHeaders) {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();

        // If a caller already attached a bearer (e.g. a programmatic per-account client),
        // propagate it untouched.
        if (clientOutgoingHeaders != null && clientOutgoingHeaders.containsKey(AUTHORIZATION_HEADER)) {
            LOG.debug("Saxo broker request already carries an Authorization header; leaving it untouched.");
            return headers;
        }

        if (!tokenProvider.isResolvable()) {
            LOG.debug("Saxo token provider not yet on the classpath (Wave 4 / OA2); no bearer header added.");
            return headers;
        }

        Optional<BrokerAccountId> account = resolveActiveAccount();
        if (account.isEmpty()) {
            LOG.debug("No Saxo broker account pinned on the request-scoped holder; no bearer header added "
                    + "(SaxoBrokerAdapter pins the account before each Saxo call per D10).");
            return headers;
        }

        String token = tokenProvider.get().getValidAccessToken(account.get());
        if (token != null && !token.isBlank()) {
            headers.add(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
        } else {
            LOG.warnf("Saxo token provider returned a blank access token for account %s; no bearer header added.",
                    account.get().value());
        }
        return headers;
    }

    /**
     * Reads the active account off the request-scoped holder, tolerating an inactive context.
     */
    private Optional<BrokerAccountId> resolveActiveAccount() {
        if (!tokenHolder.isResolvable()) {
            return Optional.empty();
        }
        try {
            return tokenHolder.get().getAccountId();
        } catch (RuntimeException ctxNotActive) {
            // No active request context (no Saxo operation in flight on this thread).
            LOG.debugf("Saxo request-token holder not active when building auth headers: %s",
                    ctxNotActive.getMessage());
            return Optional.empty();
        }
    }
}
