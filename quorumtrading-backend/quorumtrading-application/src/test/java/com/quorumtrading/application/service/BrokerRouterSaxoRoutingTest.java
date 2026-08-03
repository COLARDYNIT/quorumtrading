package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BrokerCapabilities;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link BrokerRouter} dispatches SAXO_SIM / SAXO_LIVE accounts to the
 * {@code @Named("saxoBroker")} Instance, throws a descriptive {@link IllegalStateException}
 * when that Instance is unsatisfied (adapter not yet a bean), and leaves the legacy
 * Alpaca / Mock routing byte-identical.
 *
 * <p>{@code BrokerRouter} uses field injection on package-private {@code Instance<BrokerPort>}
 * fields, so this same-package test wires CDI-style {@link Instance} mocks directly onto the
 * fields rather than through a container.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrokerRouterSaxoRoutingTest {

    private BrokerRouter router;

    private BrokerPort mockBrokerBean;
    private BrokerPort alpacaBrokerBean;
    private BrokerPort saxoBrokerBean;

    private Instance<BrokerPort> mockBrokerInstance;
    private Instance<BrokerPort> alpacaBrokerInstance;
    private Instance<BrokerPort> saxoBrokerInstance;

    private BrokerAccountRepository brokerAccountRepository;
    private Instance<BrokerAccountRepository> brokerAccountRepositoryInstance;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        router = new BrokerRouter();
        // Routing tests assume the Saxo integration is on; the off-state hard gate is
        // covered by BrokerRouterSaxoGateTest.
        router.saxoEnabled = true;

        mockBrokerBean = mock(BrokerPort.class);
        alpacaBrokerBean = mock(BrokerPort.class);
        saxoBrokerBean = mock(BrokerPort.class);

        mockBrokerInstance = satisfiedInstance(mockBrokerBean);
        alpacaBrokerInstance = satisfiedInstance(alpacaBrokerBean);
        saxoBrokerInstance = satisfiedInstance(saxoBrokerBean);

        router.mockBroker = mockBrokerInstance;
        router.alpacaBroker = alpacaBrokerInstance;
        router.saxoBroker = saxoBrokerInstance;

        router.simulationRepository = mock(SimulationRepository.class);

        brokerAccountRepository = mock(BrokerAccountRepository.class);
        brokerAccountRepositoryInstance = (Instance<BrokerAccountRepository>) mock(Instance.class);
        when(brokerAccountRepositoryInstance.isUnsatisfied()).thenReturn(false);
        when(brokerAccountRepositoryInstance.get()).thenReturn(brokerAccountRepository);
        router.brokerAccountRepository = brokerAccountRepositoryInstance;
    }

    @SuppressWarnings("unchecked")
    private Instance<BrokerPort> satisfiedInstance(BrokerPort bean) {
        Instance<BrokerPort> instance = (Instance<BrokerPort>) mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(false);
        when(instance.isResolvable()).thenReturn(true);
        when(instance.get()).thenReturn(bean);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private Instance<BrokerPort> unsatisfiedInstance() {
        Instance<BrokerPort> instance = (Instance<BrokerPort>) mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(true);
        when(instance.isResolvable()).thenReturn(false);
        return instance;
    }

    private BrokerAccount accountOfType(BrokerType type) {
        BrokerAccountId id = BrokerAccountId.generate();
        BrokerAccount account = mock(BrokerAccount.class);
        when(account.getId()).thenReturn(id);
        when(account.getBrokerType()).thenReturn(type);
        when(brokerAccountRepository.findById(id)).thenReturn(Optional.of(account));
        return account;
    }

    // ========== SAXO routes to the saxo bean ==========

    @Test
    void saxoSimAccountRoutesToSaxoBean() {
        BrokerAccount account = accountOfType(BrokerType.SAXO_SIM);

        BrokerPort resolved = router.getBrokerForAccount(account.getId());

        assertThat(resolved).isSameAs(saxoBrokerBean);
    }

    @Test
    void saxoLiveAccountRoutesToSaxoBean() {
        BrokerAccount account = accountOfType(BrokerType.SAXO_LIVE);

        BrokerPort resolved = router.getBrokerForAccount(account.getId());

        assertThat(resolved).isSameAs(saxoBrokerBean);
    }

    // ========== Unsatisfied saxo Instance → descriptive ISE ==========

    @Test
    void saxoAccountWithUnsatisfiedSaxoBeanThrowsDescriptiveIse() {
        router.saxoBroker = unsatisfiedInstance();
        BrokerAccount account = accountOfType(BrokerType.SAXO_SIM);

        assertThatThrownBy(() -> router.getBrokerForAccount(account.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SaxoBrokerAdapter not available")
            .hasMessageContaining("quorumtrading-adapter-broker-saxo")
            .hasMessageContaining("saxoBroker");
    }

    // ========== Account-aware overrides delegate via the saxo bean ==========

    @Test
    void capabilitiesForSaxoAccountDelegateToSaxoBean() {
        BrokerAccount account = accountOfType(BrokerType.SAXO_LIVE);
        when(saxoBrokerBean.capabilities()).thenReturn(BrokerCapabilities.wholeShareEquities());

        BrokerCapabilities caps = router.capabilities(account.getId());

        assertThat(caps).isEqualTo(BrokerCapabilities.wholeShareEquities());
    }

    @Test
    void capabilitiesForAccountAliasMatchesCapabilities() {
        BrokerAccount account = accountOfType(BrokerType.SAXO_LIVE);
        when(saxoBrokerBean.capabilities()).thenReturn(BrokerCapabilities.wholeShareEquities());

        assertThat(router.capabilitiesForAccount(account.getId()))
            .isEqualTo(router.capabilities(account.getId()));
    }

    // ========== Legacy Alpaca / Mock routing unchanged ==========

    @Test
    void alpacaPaperAccountStillRoutesToAlpacaBean() {
        BrokerAccount account = accountOfType(BrokerType.ALPACA_PAPER);

        BrokerPort resolved = router.getBrokerForAccount(account.getId());

        assertThat(resolved).isSameAs(alpacaBrokerBean);
    }

    @Test
    void alpacaLiveAccountStillRoutesToAlpacaBean() {
        BrokerAccount account = accountOfType(BrokerType.ALPACA_LIVE);

        BrokerPort resolved = router.getBrokerForAccount(account.getId());

        assertThat(resolved).isSameAs(alpacaBrokerBean);
    }

    @Test
    void mockAccountStillRoutesToMockBean() {
        BrokerAccount account = accountOfType(BrokerType.MOCK);

        BrokerPort resolved = router.getBrokerForAccount(account.getId());

        assertThat(resolved).isSameAs(mockBrokerBean);
    }
}
