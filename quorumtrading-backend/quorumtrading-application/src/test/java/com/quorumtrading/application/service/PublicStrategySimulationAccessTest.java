package com.quorumtrading.application.service;

import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * The policy for a <em>public</em> strategy's simulations.
 *
 * <p>Publishing a strategy shares its backtests and nothing more. Two invariants matter enough
 * to pin explicitly, because both were violated in practice:</p>
 * <ol>
 *   <li>A non-owner never gets write access, however public the strategy is. {@code restart-from}
 *       once authorised its destructive path with {@code requireView}, which meant publishing a
 *       strategy let any authenticated user delete its simulations' decisions and snapshots.</li>
 *   <li>A simulation backed by a real broker account is owner-only even for reads — a paper or
 *       live run exposes actual positions and cash movements.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicStrategySimulationAccessTest {

    private final AuthorizationService authz = new AuthorizationService();

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId STRANGER = new UserId(UUID.randomUUID());

    private Strategy strategy(boolean isPublic) {
        Strategy s = mock(Strategy.class);
        when(s.getOwnerId()).thenReturn(OWNER);
        when(s.isPublicVisible()).thenReturn(isPublic);
        return s;
    }

    private Simulation simulation(BrokerAccountId accountId, boolean bounded) {
        Simulation sim = mock(Simulation.class);
        when(sim.getOwnerUserId()).thenReturn(OWNER);
        when(sim.getBrokerAccountId()).thenReturn(accountId);
        when(sim.isBacktest()).thenReturn(bounded);
        return sim;
    }

    private BrokerAccount account(boolean isMockAccount) {
        BrokerAccount a = mock(BrokerAccount.class);
        when(a.isMock()).thenReturn(isMockAccount);
        return a;
    }

    @Test
    void publicStrategyLetsAStrangerReadAMockBackedRun() {
        Strategy pub = strategy(true);
        Simulation sim = simulation(new BrokerAccountId(UUID.randomUUID()), true);

        assertThat(authz.canView(STRANGER, sim, pub, account(true))).isTrue();
    }

    @Test
    void publicStrategyDoesNotExposeARealBrokerRun() {
        Strategy pub = strategy(true);
        // A paper/live run can carry an end date, so isBacktest() is true here — proving the
        // date is not a safe stand-in for "no real money".
        Simulation sim = simulation(new BrokerAccountId(UUID.randomUUID()), true);

        assertThat(authz.canView(STRANGER, sim, pub, account(false))).isFalse();
    }

    @Test
    void unresolvedBrokerAccountCountsAsReal() {
        Strategy pub = strategy(true);
        Simulation sim = simulation(new BrokerAccountId(UUID.randomUUID()), true);

        // Fail closed: we could not prove it is mock-backed.
        assertThat(authz.canView(STRANGER, sim, pub, null)).isFalse();
    }

    @Test
    void legacyAccountlessBacktestUnderAPublicStrategyIsReadable() {
        Strategy pub = strategy(true);
        Simulation sim = simulation(null, true);

        assertThat(authz.canView(STRANGER, sim, pub, null)).isTrue();
    }

    @Test
    void publicStrategyNeverGrantsWriteOnItsSimulations() {
        Strategy pub = strategy(true);
        Simulation sim = simulation(null, true);

        assertThat(authz.canView(STRANGER, sim, pub, account(true))).isTrue();
        assertThat(authz.canEdit(STRANGER, sim, pub)).isFalse();
        assertThat(authz.canDelete(STRANGER, sim, pub)).isFalse();
    }

    @Test
    void privateStrategyExposesNothingToAStranger() {
        Strategy priv = strategy(false);
        Simulation sim = simulation(null, true);

        assertThat(authz.canView(STRANGER, sim, priv, account(true))).isFalse();
        assertThat(authz.canEdit(STRANGER, sim, priv)).isFalse();
    }

    @Test
    void ownerRetainsFullAccessToARealBrokerRun() {
        Strategy priv = strategy(false);
        Simulation sim = simulation(new BrokerAccountId(UUID.randomUUID()), false);

        assertThat(authz.canView(OWNER, sim, priv, account(false))).isTrue();
        assertThat(authz.canEdit(OWNER, sim, priv)).isTrue();
        assertThat(authz.canDelete(OWNER, sim, priv)).isTrue();
    }

    @Test
    void writeRightsFollowTheSimulationOwnerNotTheStrategyOwner() {
        // A strategy that changed hands must not hand write rights over historical runs to the
        // new strategy owner.
        UserId newStrategyOwner = new UserId(UUID.randomUUID());
        Strategy s = mock(Strategy.class);
        when(s.getOwnerId()).thenReturn(newStrategyOwner);
        when(s.isPublicVisible()).thenReturn(false);

        Simulation sim = mock(Simulation.class);
        when(sim.getOwnerUserId()).thenReturn(OWNER);

        assertThat(authz.canEdit(OWNER, sim, s)).isTrue();
        assertThat(authz.canEdit(newStrategyOwner, sim, s)).isFalse();
    }

    @Test
    void systemPrincipalStillBypasses() {
        Strategy priv = strategy(false);
        Simulation sim = simulation(new BrokerAccountId(UUID.randomUUID()), false);

        assertThat(authz.canView(UserId.SYSTEM, sim, priv, account(false))).isTrue();
        assertThat(authz.canEdit(UserId.SYSTEM, sim, priv)).isTrue();
    }
}
