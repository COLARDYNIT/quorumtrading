package com.quorumtrading.bootstrap.config;

import com.quorumtrading.application.port.out.BrokerCredentialValidationPort;
import com.quorumtrading.application.usecase.brokeraccount.CreateBrokerAccountUseCase;
import com.quorumtrading.model.enums.BrokerType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GATE smoke test for {@code K5-validation-selector}, updated by {@code SB5-portfolio-balance-validate}
 * now that the <b>real</b> Saxo validator ({@code SaxoBrokerAdapter} implementing
 * {@link BrokerCredentialValidationPort}, {@code supports(SAXO_*)→true}) ships on the classpath.
 *
 * <p>With both the real Alpaca adapter and the real Saxo adapter present there are TWO beans of this
 * port type. Before {@code K5}, the broker-account use-case producers in {@code ApplicationConfig}
 * called {@code Instance.get()} on this set, which throws {@code AmbiguousResolutionException} at boot
 * the moment a second validator exists. This test asserts:</p>
 * <ol>
 *   <li>the application <b>boots</b> with two validators on the classpath (no
 *       {@code AmbiguousResolutionException}) — proven by the broker-account use case being
 *       injectable and the {@code @QuarkusTest} context starting at all;</li>
 *   <li>the broker-type-dispatching selector routes to the correct implementation per
 *       {@link BrokerType} — Alpaca types to the Alpaca validator, Saxo types to the Saxo validator —
 *       which holds because the two {@code supports(BrokerType)} predicates are <b>disjoint</b>
 *       ({@code isAlpaca()} vs {@code isSaxo()}).</li>
 * </ol>
 *
 * <p>Earlier this test registered a test-only {@code StubSaxoCredentialValidationPort} to reproduce a
 * multi-validator classpath before the real Saxo validator existed. That stub is removed now that
 * {@code SB5} supplies the genuine second validator; keeping it would put two Saxo-supporting beans on
 * the classpath with overlapping predicates and make the first-match dispatch ambiguous.</p>
 */
@QuarkusTest
class BrokerCredentialValidationSelectorSmokeTest {

    @Inject
    Instance<BrokerCredentialValidationPort> validationPorts;

    /**
     * The broker-account create use case is produced by {@code ApplicationConfig} from the same
     * ambiguous {@code Instance<BrokerCredentialValidationPort>}. If wiring still called
     * {@code Instance.get()} the whole context would fail to boot — so a successful injection here
     * proves the gate: the app boots with multiple validators present.
     */
    @Inject
    CreateBrokerAccountUseCase createBrokerAccountUseCase;

    @Test
    void appBootsWithMultipleValidatorsOnClasspath() {
        // The injected use case being non-null means the CDI container resolved the ambiguous
        // Instance via the selector without throwing AmbiguousResolutionException at boot.
        assertThat(createBrokerAccountUseCase)
            .as("broker-account use case must be producible despite >1 validation port")
            .isNotNull();

        // Confirm there really are multiple validation-port beans on the classpath (real Alpaca
        // adapter + real Saxo adapter), i.e. the ambiguous condition is genuinely present.
        List<BrokerCredentialValidationPort> beans = new ArrayList<>();
        validationPorts.forEach(beans::add);
        assertThat(beans)
            .as("expected the real Alpaca validator plus the real Saxo validator")
            .hasSizeGreaterThanOrEqualTo(2);
        assertThat(beans)
            .anySatisfy(p -> assertThat(p.supports(BrokerType.ALPACA_PAPER)).isTrue())
            .anySatisfy(p -> assertThat(p.supports(BrokerType.SAXO_SIM)).isTrue());
    }

    @Test
    void selectorDispatchesAlpacaTypesToTheAlpacaValidator() {
        BrokerCredentialValidationPort selected = select(BrokerType.ALPACA_PAPER);
        assertThat(selected)
            .as("ALPACA_PAPER must dispatch to a validator that supports Alpaca")
            .isNotNull();
        assertThat(selected.supports(BrokerType.ALPACA_PAPER)).isTrue();
        // Disjoint predicates: the Alpaca-supporting validator must NOT also claim Saxo.
        assertThat(selected.supports(BrokerType.SAXO_SIM)).isFalse();
    }

    @Test
    void selectorDispatchesSaxoTypesToTheSaxoValidator() {
        BrokerCredentialValidationPort selected = select(BrokerType.SAXO_LIVE);
        assertThat(selected)
            .as("SAXO_LIVE must dispatch to a validator that supports Saxo")
            .isNotNull();
        assertThat(selected.supports(BrokerType.SAXO_LIVE)).isTrue();
        // Disjoint predicates: the Saxo-supporting validator must NOT also claim Alpaca.
        assertThat(selected.supports(BrokerType.ALPACA_LIVE)).isFalse();
    }

    /**
     * Mirrors the selector logic shipped in {@code ApplicationConfig.selectValidationPort} /
     * {@code BrokerAccountBalanceSyncScheduler.selectValidationPort}: iterate the ambiguous
     * Instance and pick the first impl whose {@code supports(brokerType)} is true. Never calls
     * {@code Instance.get()}.
     */
    private BrokerCredentialValidationPort select(BrokerType brokerType) {
        for (BrokerCredentialValidationPort port : validationPorts) {
            if (port.supports(brokerType)) {
                return port;
            }
        }
        return null;
    }
}
