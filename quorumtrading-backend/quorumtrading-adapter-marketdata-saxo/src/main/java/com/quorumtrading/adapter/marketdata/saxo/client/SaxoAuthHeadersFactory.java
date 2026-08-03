package com.quorumtrading.adapter.marketdata.saxo.client;

import com.quorumtrading.application.port.out.BrokerTokenProviderPort;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Adds the Saxo OpenAPI {@code Authorization: Bearer <access-token>} header to all
 * {@link SaxoOpenApiClient} market-data requests.
 *
 * <p><b>Scaffold (MD1).</b> Saxo market-data (InfoPrices / Charts) is authenticated the
 * same way as the trading surface: a per-account OAuth2 bearer with a ~20-minute TTL and a
 * rotating refresh token. The currently-valid access token comes from
 * {@link BrokerTokenProviderPort}, which hides the entire refresh lifecycle. That port is
 * injected via {@link Instance} so this module compiles and the app boots even though the
 * real provider impl only arrives in Wave 4/5 ({@code OA2-refresh-service}); until then the
 * provider is unsatisfied and this factory emits no bearer header.</p>
 *
 * <p><b>Note on account context.</b> A {@code ClientHeadersFactory} has no per-request
 * account context, so the per-account token cannot be resolved here generically against the
 * <em>linked</em> {@code BrokerAccountId}. The live/charts branches of the adapter
 * ({@code MD2}/{@code MD3}) resolve the per-account token and attach the bearer themselves
 * (e.g. via a {@code RestClientBuilder} per account or a request-scoped holder), so an
 * already-present {@code Authorization} header is left untouched. As a scaffold convenience —
 * and so the WireMock harness can assert the bearer flows through the real factory — an
 * optional default account id ({@code quorumtrading.marketdata.saxo.default-account-id},
 * unset by default) lets this factory resolve a token via {@link BrokerTokenProviderPort}
 * when no per-request bearer was attached. In production the per-account path (MD2/MD3) is
 * used; the default-account fallback stays unset.</p>
 *
 * <p>This factory is a deliberate per-adapter copy of the broker-saxo
 * {@code SaxoAuthHeadersFactory} — adapters must not depend on each other (hexagonal
 * layering, enforced by ArchUnit).</p>
 */
@ApplicationScoped
public class SaxoAuthHeadersFactory implements ClientHeadersFactory {

    private static final Logger LOG = Logger.getLogger(SaxoAuthHeadersFactory.class);

    static final String AUTHORIZATION_HEADER = "Authorization";
    static final String BEARER_PREFIX = "Bearer ";

    /**
     * Supplies a currently-valid bearer access token for the account being called.
     * Optional at scaffold time — the real impl lands in Wave 4/5 (OA2); until then this
     * {@code Instance} is unsatisfied and no bearer header is added.
     */
    @Inject
    Instance<BrokerTokenProviderPort> tokenProvider;

    /**
     * Optional default broker-account id used only as a scaffold fallback to resolve a token
     * when no per-request bearer was attached. Unset in production (MD2/MD3 resolve the token
     * per linked account). {@code CFG1} owns the real config surface.
     */
    @Inject
    @ConfigProperty(name = "quorumtrading.marketdata.saxo.default-account-id")
    Optional<String> defaultAccountId;

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
                                                  MultivaluedMap<String, String> clientOutgoingHeaders) {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();

        // If a caller already attached a bearer for the linked account (MD2/MD3 per-account
        // resolution path), propagate it untouched.
        if (clientOutgoingHeaders != null && clientOutgoingHeaders.containsKey(AUTHORIZATION_HEADER)) {
            LOG.debug("Saxo market-data request already carries an Authorization header; leaving it untouched.");
            return headers;
        }

        // A ClientHeadersFactory has no per-request BrokerAccountId. MD2/MD3 resolve the
        // per-account token and attach the bearer for the linked BrokerAccountId. The
        // optional default-account fallback below exists only so a token can be resolved
        // without per-request account context (scaffold/test path); unset in production.
        if (!tokenProvider.isResolvable()) {
            LOG.debug("Saxo token provider not yet on the classpath (Wave 4/5 / OA2); no bearer header added by scaffold factory.");
            return headers;
        }

        Optional<BrokerAccountId> account = resolveDefaultAccountId();
        if (account.isEmpty()) {
            LOG.debug("Saxo token provider present but no default account configured; per-account bearer resolution wired in MD2/MD3 (not in the headers factory).");
            return headers;
        }

        String token = tokenProvider.get().getValidAccessToken(account.get());
        if (token != null && !token.isBlank()) {
            headers.add(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
        }
        return headers;
    }

    private Optional<BrokerAccountId> resolveDefaultAccountId() {
        if (defaultAccountId == null || defaultAccountId.isEmpty() || defaultAccountId.get().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BrokerAccountId(UUID.fromString(defaultAccountId.get().trim())));
        } catch (IllegalArgumentException e) {
            LOG.warnf("Configured quorumtrading.marketdata.saxo.default-account-id is not a valid UUID: %s", defaultAccountId.get());
            return Optional.empty();
        }
    }
}
