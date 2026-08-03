package com.quorumtrading.adapter.marketdata.saxo.client;

import com.quorumtrading.application.port.out.BrokerTokenProviderPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Test-only CDI producer for {@link BrokerTokenProviderPort}.
 *
 * <p>The real provider lands in Wave 4/5 ({@code OA2-refresh-service}); in this isolated
 * adapter test it is unsatisfied, so the {@link SaxoAuthHeadersFactory} would emit no bearer.
 * This stub returns a fixed access token for any account so the WireMock harness can assert
 * the {@code Authorization: Bearer ...} header is produced by the real factory.</p>
 */
@ApplicationScoped
public class TestTokenProviderProducer {

    /** The fixed access token the stub provider returns; asserted in the WireMock test. */
    public static final String TEST_ACCESS_TOKEN = "saxo-access-token-abc123";

    @Produces
    @ApplicationScoped
    public BrokerTokenProviderPort brokerTokenProviderPort() {
        return brokerAccountId -> TEST_ACCESS_TOKEN;
    }
}
