package com.quorumtrading.adapter.broker.alpaca;

import com.quorumtrading.adapter.broker.alpaca.client.AlpacaTradingClient;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.SimulationId;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Quarkus test proving the REAL {@code buildClient} plumbing works: a simulation linked
 * to a configured account gets a programmatically built per-account REST client (paper vs
 * live base URL, account credentials) that is distinct from the injected default client.
 *
 * <p>No network calls are made — only client construction and routing are exercised.
 * Live-API tests remain gated behind ALPACA_INTEGRATION_TESTS (see AlpacaBrokerIntegrationTest).</p>
 */
@QuarkusTest
class AlpacaPerAccountClientBuildTest {

    @Inject
    @Named("alpacaBroker")
    AlpacaBrokerAdapter proxiedAdapter;

    private final BrokerAccountId accountId = new BrokerAccountId(UUID.randomUUID());
    private final SimulationId simulationId = new SimulationId(UUID.randomUUID());

    private AlpacaBrokerAdapter adapter() {
        // Unwrap the CDI client proxy so package-private fields/methods hit the real bean
        return ClientProxy.unwrap(proxiedAdapter);
    }

    @AfterEach
    void cleanUp() {
        AlpacaBrokerAdapter adapter = adapter();
        adapter.removeAccount(accountId);
        adapter.clearPortfolio(simulationId); // also removes the simulation-to-account link
    }

    @Test
    void linkedSimulationGetsRealPerAccountClient() {
        AlpacaBrokerAdapter adapter = adapter();
        adapter.configureAccount(accountId,
            new ApiKeyCredentials("per-account-key", "per-account-secret"),
            BrokerType.ALPACA_PAPER);
        adapter.linkSimulationToAccount(simulationId, accountId);

        AlpacaTradingClient client = adapter.getEffectiveClient(simulationId);

        assertNotNull(client, "Per-account client should be built successfully");
        assertNotSame(adapter.tradingClient, client,
            "Per-account client must be distinct from the global-config default client");
        assertSame(client, adapter.getEffectiveClient(simulationId),
            "Per-account client should be cached across calls");
    }

    @Test
    void linkedSimulationWithoutCredentialsFailsClosed() {
        AlpacaBrokerAdapter adapter = adapter();
        adapter.linkSimulationToAccount(simulationId, accountId); // no configureAccount

        assertThrows(IllegalStateException.class,
            () -> adapter.getEffectiveClient(simulationId),
            "Simulation linked to an account without credentials must fail closed");
    }
}
