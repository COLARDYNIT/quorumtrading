package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.BrokerIntegrationDisabledException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Disabling the Saxo integration must stop ALL routing to Saxo accounts — including accounts
 * linked while the integration was still on. {@code quorumtrading.broker.saxo.enabled=false}
 * is a hard gate at the router, not just an onboarding-visibility flag.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrokerRouterSaxoGateTest {

    @Mock Instance<BrokerPort> mockBroker;
    @Mock Instance<BrokerPort> alpacaBroker;
    @Mock Instance<BrokerPort> saxoBroker;
    @Mock Instance<BrokerAccountRepository> brokerAccountRepositoryInstance;
    @Mock BrokerAccountRepository brokerAccountRepository;

    private BrokerRouter router;
    private final BrokerAccountId saxoAccountId = BrokerAccountId.generate();
    private final BrokerAccountId alpacaAccountId = BrokerAccountId.generate();

    @BeforeEach
    void setUp() {
        router = new BrokerRouter();
        router.mockBroker = mockBroker;
        router.alpacaBroker = alpacaBroker;
        router.saxoBroker = saxoBroker;
        router.brokerAccountRepository = brokerAccountRepositoryInstance;
        router.cryptoBrokerType = "mock";
        router.stockBrokerType = "mock";

        lenient().when(brokerAccountRepositoryInstance.isUnsatisfied()).thenReturn(false);
        lenient().when(brokerAccountRepositoryInstance.get()).thenReturn(brokerAccountRepository);

        BrokerAccount saxoAccount = mock(BrokerAccount.class);
        lenient().when(saxoAccount.getBrokerType()).thenReturn(BrokerType.SAXO_SIM);
        lenient().when(saxoAccount.getId()).thenReturn(saxoAccountId);
        lenient().when(brokerAccountRepository.findById(saxoAccountId)).thenReturn(Optional.of(saxoAccount));

        BrokerAccount alpacaAccount = mock(BrokerAccount.class);
        lenient().when(alpacaAccount.getBrokerType()).thenReturn(BrokerType.ALPACA_PAPER);
        lenient().when(alpacaAccount.getId()).thenReturn(alpacaAccountId);
        lenient().when(brokerAccountRepository.findById(alpacaAccountId)).thenReturn(Optional.of(alpacaAccount));

        lenient().when(saxoBroker.isUnsatisfied()).thenReturn(false);
        lenient().when(saxoBroker.get()).thenReturn(mock(BrokerPort.class));
        lenient().when(alpacaBroker.isUnsatisfied()).thenReturn(false);
        lenient().when(alpacaBroker.get()).thenReturn(mock(BrokerPort.class));
    }

    @Test
    @DisplayName("should refuse to route to an already-linked Saxo account when the integration is disabled")
    void shouldRefuseSaxoAccountWhenIntegrationDisabled() {
        router.saxoEnabled = false;

        assertThatThrownBy(() -> router.getBrokerForAccount(saxoAccountId))
            .isInstanceOf(BrokerIntegrationDisabledException.class)
            .hasMessageContaining("Saxo integration is disabled")
            .hasMessageContaining("SAXO_ENABLED=true")
            .hasMessageContaining("SAXO_SIM");
    }

    @Test
    @DisplayName("should route to the Saxo adapter when the integration is enabled")
    void shouldRouteSaxoAccountWhenIntegrationEnabled() {
        router.saxoEnabled = true;

        assertThat(router.getBrokerForAccount(saxoAccountId)).isNotNull();
    }

    @Test
    @DisplayName("should leave non-Saxo accounts unaffected by the Saxo flag")
    void shouldNotGateAlpacaAccounts() {
        router.saxoEnabled = false;

        assertThatCode(() -> router.getBrokerForAccount(alpacaAccountId)).doesNotThrowAnyException();
    }
}
