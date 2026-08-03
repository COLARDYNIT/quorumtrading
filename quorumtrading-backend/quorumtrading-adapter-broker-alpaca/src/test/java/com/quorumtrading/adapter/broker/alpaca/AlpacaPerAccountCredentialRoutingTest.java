package com.quorumtrading.adapter.broker.alpaca;

import com.quorumtrading.adapter.broker.alpaca.client.AlpacaTradingClient;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.TradeAction;
import com.quorumtrading.model.enums.TradeStatus;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for per-account credential routing in {@link AlpacaBrokerAdapter}.
 *
 * <p>These are plain JUnit tests (no Quarkus, no network). {@code buildClient} is
 * overridden with a recording fake so we can assert exactly which credentials each
 * per-account client is built with.</p>
 *
 * <p>Covers the fail-closed contract: a simulation linked to a broker account whose
 * credentials are missing must throw — never silently fall back to the globally
 * configured (operator) credentials.</p>
 */
class AlpacaPerAccountCredentialRoutingTest {

    /**
     * Adapter subclass that records buildClient invocations and returns Mockito
     * fakes instead of real REST clients (no network in unit tests).
     */
    private static class RecordingAdapter extends AlpacaBrokerAdapter {
        final List<AccountCredentialsInfo> buildRequests = new ArrayList<>();

        @Override
        AlpacaTradingClient buildClient(AccountCredentialsInfo info) {
            buildRequests.add(info);
            return mock(AlpacaTradingClient.class);
        }
    }

    private RecordingAdapter adapter;
    private AlpacaTradingClient defaultClient;

    @BeforeEach
    void setUp() {
        adapter = new RecordingAdapter();
        defaultClient = mock(AlpacaTradingClient.class);
        adapter.tradingClient = defaultClient;
    }

    @Test
    void linkedSimulationUsesClientBuiltWithAccountCredentials() {
        BrokerAccountId accountId = new BrokerAccountId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        ApiKeyCredentials credentials = new ApiKeyCredentials("account-key", "account-secret");

        adapter.configureAccount(accountId, credentials, BrokerType.ALPACA_PAPER);
        adapter.linkSimulationToAccount(simulationId, accountId);

        AlpacaTradingClient client = adapter.getEffectiveClient(simulationId);

        assertNotSame(defaultClient, client,
            "Linked simulation must NOT use the global-config default client");
        assertEquals(1, adapter.buildRequests.size(), "Client should be built exactly once");
        assertEquals(credentials, adapter.buildRequests.get(0).credentials(),
            "Client must be built with the account's own credentials");
        assertEquals(BrokerType.ALPACA_PAPER, adapter.buildRequests.get(0).brokerType(),
            "Client must be built for the account's broker type (paper vs live base URL)");
    }

    @Test
    void perAccountClientIsCachedAcrossCalls() {
        BrokerAccountId accountId = new BrokerAccountId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());

        adapter.configureAccount(accountId, new ApiKeyCredentials("k", "s"), BrokerType.ALPACA_PAPER);
        adapter.linkSimulationToAccount(simulationId, accountId);

        AlpacaTradingClient first = adapter.getEffectiveClient(simulationId);
        AlpacaTradingClient second = adapter.getEffectiveClient(simulationId);

        assertSame(first, second, "Per-account client should be cached");
        assertEquals(1, adapter.buildRequests.size(), "buildClient should only be invoked once");
    }

    @Test
    void linkedSimulationWithoutCredentialsFailsClosed() {
        BrokerAccountId accountId = new BrokerAccountId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());

        // Linked but NEVER configured — must fail closed, not fall back to global client
        adapter.linkSimulationToAccount(simulationId, accountId);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> adapter.getEffectiveClient(simulationId));

        assertTrue(ex.getMessage().contains("No credentials configured"),
            "Exception should explain the missing credentials, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(accountId.value().toString()),
            "Exception should name the broker account, got: " + ex.getMessage());
        assertEquals(0, adapter.buildRequests.size(), "No client may be built without credentials");
    }

    @Test
    void unlinkedSimulationUsesDefaultGlobalClient() {
        SimulationId unlinked = new SimulationId(UUID.randomUUID());

        AlpacaTradingClient client = adapter.getEffectiveClient(unlinked);

        assertSame(defaultClient, client,
            "Simulation with no account link uses the global-config client (backwards-compatible mode)");
        assertEquals(0, adapter.buildRequests.size());
    }

    @Test
    void reconfiguringAccountRebuildsClientWithNewCredentials() {
        BrokerAccountId accountId = new BrokerAccountId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());

        adapter.configureAccount(accountId, new ApiKeyCredentials("old-key", "old-secret"), BrokerType.ALPACA_PAPER);
        adapter.linkSimulationToAccount(simulationId, accountId);
        AlpacaTradingClient oldClient = adapter.getEffectiveClient(simulationId);

        ApiKeyCredentials newCredentials = new ApiKeyCredentials("new-key", "new-secret");
        adapter.configureAccount(accountId, newCredentials, BrokerType.ALPACA_LIVE);
        AlpacaTradingClient newClient = adapter.getEffectiveClient(simulationId);

        assertNotSame(oldClient, newClient, "Cached client must be invalidated on reconfigure");
        assertEquals(2, adapter.buildRequests.size());
        assertEquals(newCredentials, adapter.buildRequests.get(1).credentials());
        assertEquals(BrokerType.ALPACA_LIVE, adapter.buildRequests.get(1).brokerType());
    }

    @Test
    void removedAccountFailsClosedOnSubsequentCalls() {
        BrokerAccountId accountId = new BrokerAccountId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());

        adapter.configureAccount(accountId, new ApiKeyCredentials("k", "s"), BrokerType.ALPACA_PAPER);
        adapter.linkSimulationToAccount(simulationId, accountId);
        adapter.getEffectiveClient(simulationId);

        adapter.removeAccount(accountId);

        assertThrows(IllegalStateException.class, () -> adapter.getEffectiveClient(simulationId),
            "After account removal a still-linked simulation must fail closed");
    }

    @Test
    void configureAccountRejectsNonAlpacaBrokerTypes() {
        BrokerAccountId accountId = new BrokerAccountId(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
            () -> adapter.configureAccount(accountId, new ApiKeyCredentials("k", "s"), BrokerType.MOCK));
    }

    @Test
    void executeTradeFailsClosedWhenLinkedAccountHasNoCredentials() {
        BrokerAccountId accountId = new BrokerAccountId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());

        adapter.enabled = true;
        adapter.linkSimulationToAccount(simulationId, accountId); // no credentials configured

        // Crypto trades 24/7, so this bypasses the market-hours gate and reaches client resolution
        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.BUY,
            new Symbol("BTC", InvestmentType.CRYPTO),
            BigDecimal.valueOf(0.001),
            Instant.now()
        );

        TradeResult result = adapter.executeTrade(command);

        assertEquals(TradeStatus.REJECTED, result.status(),
            "Trade for a linked account without credentials must be rejected");
        assertTrue(result.rejectionReason().contains("No credentials configured"),
            "Rejection should explain the missing credentials, got: " + result.rejectionReason());
        verifyNoInteractions(defaultClient);
    }
}
