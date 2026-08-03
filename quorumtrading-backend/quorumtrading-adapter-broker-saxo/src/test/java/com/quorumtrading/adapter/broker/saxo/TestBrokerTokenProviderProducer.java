package com.quorumtrading.adapter.broker.saxo;

import com.quorumtrading.application.port.out.BrokerTokenProviderPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Test-only CDI producer for {@link BrokerTokenProviderPort}.
 *
 * <p>The real provider lands in Wave 4 ({@code OA2-refresh-service}); in this isolated adapter
 * test it is unsatisfied, so {@link com.quorumtrading.adapter.broker.saxo.client.SaxoAuthHeadersFactory}
 * would emit no bearer. This stub returns a token <b>derived from the account id</b> so the
 * WireMock harness can assert that (a) the per-account bearer flows through the real factory onto
 * the {@code /port/v1/users/me} + {@code /port/v1/accounts/me} calls, and (b) two different
 * accounts get two different bearers (per-account isolation).</p>
 */
@ApplicationScoped
public class TestBrokerTokenProviderProducer {

    /** Prefix of the per-account token the stub returns; the account-id is appended. */
    public static final String TOKEN_PREFIX = "saxo-access-for-";

    /** The fixed access token returned for the given account id. */
    public static String tokenFor(java.util.UUID accountId) {
        return TOKEN_PREFIX + accountId;
    }

    @Produces
    @ApplicationScoped
    public BrokerTokenProviderPort brokerTokenProviderPort() {
        return brokerAccountId -> TOKEN_PREFIX + brokerAccountId.value();
    }
}
