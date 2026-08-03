package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.CreateStrategyCommand;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.AssetInfo;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateStrategyServiceTest {

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private BrokerPort brokerPort;

    @Mock
    private LLMPort llmPort;

    private CreateStrategyService service;

    private static final UserId OWNER_ID = new UserId(UUID.randomUUID());

    private static final String VALID_STRATEGY = """
        ---
        version: v1.0
        consensus_method: MAJORITY
        consensus_threshold: 0.66
        stock_tickers:
          - AAPL
          - MSFT
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00", "14:00"]
          market_open: "09:30"
          market_close: "16:00"
        position_limits:
          AAPL: 0.3
          MSFT: 0.3
        ---
        # Test Strategy
        Buy when RSI < 30.
        """;

    private static final String STRATEGY_WITH_UNTRADABLE_TICKER = """
        ---
        version: v1.0
        consensus_method: MAJORITY
        consensus_threshold: 0.66
        stock_tickers:
          - AAPL
          - XYZF
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00", "14:00"]
          market_open: "09:30"
          market_close: "16:00"
        position_limits:
          AAPL: 0.3
          XYZF: 0.3
        ---
        # Test Strategy
        Buy when RSI < 30.
        """;

    @BeforeEach
    void setUp() {
        service = new CreateStrategyService(strategyRepository, brokerPort, llmPort);
    }

    @Test
    void shouldCreateStrategyWithTradableTickers() {
        // Given
        when(brokerPort.getAssetInfo("AAPL"))
            .thenReturn(Optional.of(AssetInfo.tradable("AAPL", "Apple Inc.", "NASDAQ", "us_equity")));
        when(brokerPort.getAssetInfo("MSFT"))
            .thenReturn(Optional.of(AssetInfo.tradable("MSFT", "Microsoft Corp.", "NASDAQ", "us_equity")));
        when(strategyRepository.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));

        CreateStrategyCommand command = new CreateStrategyCommand("Test Strategy", VALID_STRATEGY, OWNER_ID);

        // When
        StrategyId id = service.execute(command);

        // Then
        assertThat(id).isNotNull();
        verify(strategyRepository).save(any(Strategy.class));
    }

    @Test
    void shouldRejectStrategyWithUntradableTicker() {
        // Given
        when(brokerPort.getAssetInfo("AAPL"))
            .thenReturn(Optional.of(AssetInfo.tradable("AAPL", "Apple Inc.", "NASDAQ", "us_equity")));
        when(brokerPort.getAssetInfo("XYZF"))
            .thenReturn(Optional.of(AssetInfo.notFound("XYZF")));

        CreateStrategyCommand command = new CreateStrategyCommand("Test Strategy", STRATEGY_WITH_UNTRADABLE_TICKER, OWNER_ID);

        // When/Then
        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-tradable tickers")
            .hasMessageContaining("XYZF")
            // Wording changed with the exchange-qualified fix: the message no longer names the
            // broker vendor, and no longer says "verify the ticker symbol" for a venue problem.
            .hasMessageContaining("not found in the broker's US equity universe");

        verify(strategyRepository, never()).save(any());
    }

    @Test
    void shouldRejectStrategyWithInactiveTickerAndShowExchange() {
        // Given
        when(brokerPort.getAssetInfo("AAPL"))
            .thenReturn(Optional.of(AssetInfo.tradable("AAPL", "Apple Inc.", "NASDAQ", "us_equity")));
        when(brokerPort.getAssetInfo("XYZF"))
            .thenReturn(Optional.of(AssetInfo.notTradable("XYZF", "Fake Corp", "NYSE", "inactive", "us_equity")));

        CreateStrategyCommand command = new CreateStrategyCommand("Test Strategy", STRATEGY_WITH_UNTRADABLE_TICKER, OWNER_ID);

        // When/Then
        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-tradable tickers")
            .hasMessageContaining("XYZF")
            // The message names the venue and the company the broker has on file, then
            // says plainly that trading is not permitted — without quoting a raw status
            // that would contradict the sentence containing it.
            .hasMessageContaining("NYSE")
            .hasMessageContaining("Fake Corp")
            .hasMessageContaining("does not permit");

        verify(strategyRepository, never()).save(any());
    }

    @Test
    void shouldAllowCreationWhenBrokerUnavailable() {
        // Given — broker returns empty for every ticker. getAssetInfo only comes back empty when
        // the broker API itself is unreachable/unauthenticated (a working API always answers
        // tradable/notTradable/notFound), so this must soft-pass — never fall back to
        // validateSymbols, which would hit the same broken API and hard-fail every ticker.
        when(brokerPort.getAssetInfo(anyString())).thenReturn(Optional.empty());
        when(strategyRepository.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));

        CreateStrategyCommand command = new CreateStrategyCommand("Test Strategy", VALID_STRATEGY, OWNER_ID);

        // When
        StrategyId id = service.execute(command);

        // Then — should succeed (soft validation), without consulting validateSymbols
        assertThat(id).isNotNull();
        verify(strategyRepository).save(any(Strategy.class));
        verify(brokerPort, never()).validateSymbols(any());
    }

    @Test
    void shouldAllowCreationWhenBrokerThrowsException() {
        // Given — broker throws an infrastructure exception
        when(brokerPort.getAssetInfo(anyString())).thenThrow(new RuntimeException("Broker connection failed"));
        when(strategyRepository.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));

        CreateStrategyCommand command = new CreateStrategyCommand("Test Strategy", VALID_STRATEGY, OWNER_ID);

        // When
        StrategyId id = service.execute(command);

        // Then — should succeed (soft validation)
        assertThat(id).isNotNull();
        verify(strategyRepository).save(any(Strategy.class));
    }
}
