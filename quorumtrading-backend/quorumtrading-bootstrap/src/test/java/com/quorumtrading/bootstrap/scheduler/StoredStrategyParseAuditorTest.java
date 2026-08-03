package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The boot-time audit that names strategies the tightened parser will now reject.
 *
 * <p>Tightening the parser was retroactive: a strategy stored under the lenient one — with a typo'd
 * agent role, an unrecognised consensus_method, or a frontmatter key nobody reads — used to run
 * (wrongly, and silently) and now fails. Without this audit the owner's first notice is a FAILED
 * simulation some time after an upgrade that looked clean.</p>
 *
 * <p>The audit is a diagnostic, so the load-bearing property is that it never stops the
 * application from booting, whatever it finds or however the repository misbehaves.</p>
 */
@DisplayName("StoredStrategyParseAuditor")
class StoredStrategyParseAuditorTest {

    private StrategyRepository strategyRepository;

    private StoredStrategyParseAuditor auditor;

    private static final String VALID = """
            ---
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 1h
            backtest_schedule:
              interval: 1d
            ---
            Buy the dip.
            """;

    /** Accepted by the old lenient parser (ran as all three agents), rejected now. */
    private static final String TYPO_AGENT = """
            ---
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 1h
            agents:
              - TECHNCIAL
            ---
            Buy the dip.
            """;

    @BeforeEach
    void setUp() {
        strategyRepository = mock(StrategyRepository.class);
        auditor = new StoredStrategyParseAuditor();
        auditor.strategyRepository = strategyRepository;
    }

    private Strategy strategy(String content) {
        Strategy s = mock(Strategy.class);
        when(s.getContent()).thenReturn(content);
        when(s.getName()).thenReturn("A strategy");
        when(s.getId()).thenReturn(StrategyId.generate());
        when(s.getOwnerId()).thenReturn(UserId.generate());
        return s;
    }

    @Test
    @DisplayName("a clean estate boots without complaint")
    void shouldPassWhenEveryStrategyParses() {
        Strategy first = strategy(VALID);
        Strategy second = strategy(VALID);
        when(strategyRepository.findAll()).thenReturn(List.of(first, second));

        assertThatCode(() -> auditor.onStartup(new StartupEvent())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a strategy the tightened parser rejects does not stop the boot")
    void shouldReportRatherThanFailOnABrokenStrategy() {
        Strategy good = strategy(VALID);
        Strategy broken = strategy(TYPO_AGENT);
        when(strategyRepository.findAll()).thenReturn(List.of(good, broken));

        assertThatCode(() -> auditor.onStartup(new StartupEvent())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a strategy with an unreadable owner is still reported, not thrown on")
    void shouldTolerateAMissingOwner() {
        Strategy orphan = strategy(TYPO_AGENT);
        when(orphan.getOwnerId()).thenReturn(null);
        when(strategyRepository.findAll()).thenReturn(List.of(orphan));

        assertThatCode(() -> auditor.onStartup(new StartupEvent())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no strategies at all is not an error")
    void shouldDoNothingOnAnEmptyEstate() {
        when(strategyRepository.findAll()).thenReturn(List.of());

        assertThatCode(() -> auditor.onStartup(new StartupEvent())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a diagnostic must never be the reason the application will not start")
    void shouldSwallowARepositoryFailure() {
        when(strategyRepository.findAll()).thenThrow(new IllegalStateException("database is down"));

        assertThatCode(() -> auditor.onStartup(new StartupEvent())).doesNotThrowAnyException();
    }
}
