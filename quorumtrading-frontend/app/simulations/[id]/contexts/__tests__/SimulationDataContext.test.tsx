import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SimulationDataProvider, useSimulationData } from '../SimulationDataContext';
import * as apiHooks from '@/app/lib/api/hooks';
import * as websocketHooks from '@/app/lib/websocket/hooks';
import type { StreamMessage, StreamMessageType } from '@/app/lib/types/websocket';

// Mock API hooks
jest.mock('@/app/lib/api/hooks');
jest.mock('@/app/lib/websocket/hooks');

// A stable spy, not a fresh jest.fn() per render: the previous factory returned a new object on
// every call, so nothing the context did to setActiveConsensus was observable to a test.
const mockSetActiveConsensus = jest.fn();
const mockRefetchConsensusDecisions = jest.fn();
jest.mock('@/app/contexts/TimeNavigationContext', () => ({
  useTimeNavigation: () => ({
    state: { mode: 'live', selectedTimestamp: null, activeConsensusId: null },
    setActiveConsensus: mockSetActiveConsensus,
  }),
}));

const mockUseSimulation = apiHooks.useSimulation as jest.MockedFunction<typeof apiHooks.useSimulation>;
const mockUseStrategy = apiHooks.useStrategy as jest.MockedFunction<typeof apiHooks.useStrategy>;
const mockUsePortfolioSnapshots = apiHooks.usePortfolioSnapshots as jest.MockedFunction<typeof apiHooks.usePortfolioSnapshots>;
const mockUseConsensusDecisions = apiHooks.useConsensusDecisions as jest.MockedFunction<typeof apiHooks.useConsensusDecisions>;
const mockUseLLMDecisions = apiHooks.useLLMDecisions as jest.MockedFunction<typeof apiHooks.useLLMDecisions>;
const mockUseMarketData = apiHooks.useMarketData as jest.MockedFunction<typeof apiHooks.useMarketData>;
const mockUseMarketDataContext = apiHooks.useMarketDataContext as jest.MockedFunction<typeof apiHooks.useMarketDataContext>;
// useConsensusWithAgents is no longer used by the context (replaced by lightweight useConsensusDecisions)
const mockUseSimulationStream = websocketHooks.useSimulationStream as jest.MockedFunction<typeof websocketHooks.useSimulationStream>;
const mockUseWebSocket = websocketHooks.useWebSocket as jest.MockedFunction<typeof websocketHooks.useWebSocket>;
const mockUseStreamMessage = websocketHooks.useStreamMessage as jest.MockedFunction<typeof websocketHooks.useStreamMessage>;

describe('SimulationDataContext', () => {
  let queryClient: QueryClient;

  // The context subscribes to the socket through useStreamMessage, which is mocked away at module
  // level. Recording the handler it registers per message type is all that is needed to drive the
  // real handlers — the "complex callback mocking" this used to be skipped for is these ten lines.
  // Only the newest handler per type is kept, matching the real hook: its useEffect unsubscribes
  // the previous one whenever its deps change.
  let streamHandlers: Partial<Record<StreamMessageType, (message: any) => void>>;

  const emit = (type: StreamMessageType, message: Partial<StreamMessage> & Record<string, unknown>) => {
    const handler = streamHandlers[type];
    if (!handler) {
      throw new Error(`No handler registered for ${type} — the context stopped subscribing to it`);
    }
    act(() => handler({ type, simulationId: 'sim-123', timestamp: '2025-11-01T12:00:00Z', ...message }));
  };

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });

    // Reset all mocks
    jest.clearAllMocks();

    // Default mock implementations
    mockUseSimulation.mockReturnValue({
      data: {
        id: 'sim-123',
        name: 'Test Simulation',
        strategyId: 'strat-456',
        status: 'SIMULATING',
        progress: 50,
        startDate: '2025-11-01T00:00:00Z',
        endDate: '2025-11-30T23:59:59Z',
        initialCash: { amount: 10000, currency: 'USD' },
        paused: false,
        createdAt: '2025-11-01T00:00:00Z',
        updatedAt: '2025-11-01T12:00:00Z',
      },
      isLoading: false,
      error: null,
    } as any);

    mockUseStrategy.mockReturnValue({
      data: {
        id: 'strat-456',
        name: 'Test Strategy',
        markdownContent: `---
version: v1.0
consensus_method: CONFIDENCE_WEIGHTED
consensus_threshold: 0.7
evaluation_interval_seconds: 600
stock_tickers:
  - TSLA
  - NVDA
crypto_tickers:
  - BTC
  - ETH
---
# Test Strategy
Strategy rules here...`,
        createdAt: '2025-11-01T00:00:00Z',
        updatedAt: '2025-11-01T00:00:00Z',
      },
      isLoading: false,
      error: null,
    } as any);

    mockUsePortfolioSnapshots.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
    } as any);

    // refetch matters: the context calls it from the LLM_DECISION and TRADE_EXECUTION handlers to
    // refresh the timeline. The default mock omitted it, and the `as any` cast hid that — the
    // handlers would have thrown "refetchConsensusHistory is not a function" the moment anything
    // actually exercised them, which nothing did while those tests were skipped.
    mockUseConsensusDecisions.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: mockRefetchConsensusDecisions,
    } as any);

    mockUseLLMDecisions.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
    } as any);

    mockUseMarketData.mockReturnValue({
      data: { events: [] },
      isLoading: false,
      error: null,
    } as any);

    mockUseMarketDataContext.mockReturnValue({
      data: { tickers: {} },
      isLoading: false,
      error: null,
    } as any);

    // useConsensusWithAgents no longer used by context

    mockUseSimulationStream.mockReturnValue(undefined);

    mockUseWebSocket.mockReturnValue({
      connectionState: 'CONNECTED' as any,
      isConnected: true,
      isConnecting: false,
      isReconnecting: false,
    });

    streamHandlers = {};
    mockUseStreamMessage.mockImplementation((type, handler) => {
      streamHandlers[type] = handler as (message: any) => void;
    });
  });

  afterEach(() => {
    queryClient.clear();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <SimulationDataProvider simulationId="sim-123">
        {children}
      </SimulationDataProvider>
    </QueryClientProvider>
  );

  describe('Context Provider', () => {
    it('throws error when useSimulationData is called outside provider', () => {
      // Suppress console.error for this test
      const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation();

      expect(() => {
        renderHook(() => useSimulationData());
      }).toThrow('useSimulationData must be used within SimulationDataProvider');

      consoleErrorSpy.mockRestore();
    });

    it('provides context value when used within provider', () => {
      const { result } = renderHook(() => useSimulationData(), { wrapper });

      expect(result.current).toBeDefined();
      expect(result.current.simulation).toBeDefined();
      expect(result.current.strategy).toBeDefined();
    });
  });

  describe('Data Fetching', () => {
    it('fetches and provides simulation data', async () => {
      const { result } = renderHook(() => useSimulationData(), { wrapper });

      await waitFor(() => {
        expect(result.current.simulation).toEqual({
          id: 'sim-123',
          name: 'Test Simulation',
          strategyId: 'strat-456',
          status: 'SIMULATING',
          progress: 50,
          startDate: '2025-11-01T00:00:00Z',
          endDate: '2025-11-30T23:59:59Z',
          initialCash: { amount: 10000, currency: 'USD' },
          paused: false,
          createdAt: '2025-11-01T00:00:00Z',
          updatedAt: '2025-11-01T12:00:00Z',
        });
      });
    });

    it('fetches and provides strategy data', async () => {
      const { result } = renderHook(() => useSimulationData(), { wrapper });

      await waitFor(() => {
        expect(result.current.strategy).toBeDefined();
        expect(result.current.strategy?.name).toBe('Test Strategy');
      });
    });

    it('handles loading state', () => {
      mockUseSimulation.mockReturnValue({
        data: null,
        isLoading: true,
        error: null,
      } as any);

      const { result } = renderHook(() => useSimulationData(), { wrapper });

      expect(result.current.isLoading).toBe(true);
      expect(result.current.simulation).toBeNull();
    });

    it('handles error state', () => {
      const error = new Error('Failed to fetch simulation');
      mockUseSimulation.mockReturnValue({
        data: null,
        isLoading: false,
        error,
      } as any);

      const { result } = renderHook(() => useSimulationData(), { wrapper });

      expect(result.current.error).toBe(error);
      expect(result.current.simulation).toBeNull();
    });
  });

  describe('Strategy Parsing', () => {
    it('parses evaluation interval from strategy markdown', async () => {
      const { result } = renderHook(() => useSimulationData(), { wrapper });

      await waitFor(() => {
        // evaluationIntervalHours is at root level of context, not in consensusInfo
        expect(result.current.evaluationIntervalHours).toBe(600 / 3600); // 600 seconds = 0.1667 hours
      });
    });

    it('parses configured tickers from strategy markdown', async () => {
      const { result } = renderHook(() => useSimulationData(), { wrapper });

      await waitFor(() => {
        // configuredTickers is at root level of context
        expect(result.current.configuredTickers.crypto).toEqual(['BTC', 'ETH']);
        expect(result.current.configuredTickers.stock).toEqual(['TSLA', 'NVDA']);
      });
    });

    it('handles strategy with no tickers', async () => {
      mockUseStrategy.mockReturnValue({
        data: {
          id: 'strat-456',
          name: 'Test Strategy',
          markdownContent: `---
version: v1.0
evaluation_interval_seconds: 600
---
# Strategy`,
          createdAt: '2025-11-01T00:00:00Z',
          updatedAt: '2025-11-01T00:00:00Z',
        },
        isLoading: false,
        error: null,
      } as any);

      const { result } = renderHook(() => useSimulationData(), { wrapper });

      await waitFor(() => {
        // When no tickers specified, return empty arrays
        expect(result.current.configuredTickers.crypto).toEqual([]);
        expect(result.current.configuredTickers.stock).toEqual([]);
      });
    });

    it('returns null for evaluation interval when not specified', async () => {
      mockUseStrategy.mockReturnValue({
        data: {
          id: 'strat-456',
          name: 'Test Strategy',
          markdownContent: `---
version: v1.0
---
# Strategy`,
          createdAt: '2025-11-01T00:00:00Z',
          updatedAt: '2025-11-01T00:00:00Z',
        },
        isLoading: false,
        error: null,
      } as any);

      const { result } = renderHook(() => useSimulationData(), { wrapper });

      await waitFor(() => {
        // When no evaluation_interval_seconds, returns null (no default anymore)
        expect(result.current.evaluationIntervalHours).toBeNull();
      });
    });
  });

  describe('Portfolio Data Processing', () => {
    it('computes portfolio data from snapshots', async () => {
      mockUsePortfolioSnapshots.mockReturnValue({
        data: [
          {
            id: 'snap-1',
            simulationId: 'sim-123',
            timestamp: '2025-11-01T00:00:00Z',
            positions: { BTC: { symbol: 'BTC', quantity: 0.5, averagePrice: 50000, currentPrice: 50000 } },
            totalValue: 35000,
            cashBalance: 10000,
          },
          {
            id: 'snap-2',
            simulationId: 'sim-123',
            timestamp: '2025-11-01T06:00:00Z',
            positions: { BTC: { symbol: 'BTC', quantity: 0.5, averagePrice: 50000, currentPrice: 52000 } },
            totalValue: 36000,
            cashBalance: 10000,
          },
        ],
        isLoading: false,
        error: null,
      } as any);

      const { result } = renderHook(() => useSimulationData(), { wrapper });

      await waitFor(() => {
        expect(result.current.portfolioData).toBeDefined();
        expect(result.current.portfolioData?.valueHistory).toHaveLength(2);
        // valueHistory is reversed for charting (oldest first), so:
        // snap-1 (00:00, 35000) becomes [0], snap-2 (06:00, 36000) becomes [1]
        // But actual order depends on API response order - testing both values exist
        const values = result.current.portfolioData?.valueHistory.map(h => h.total).sort();
        expect(values).toEqual([35000, 36000]);
      });
    });

    it('overlays latest market prices on portfolio positions', async () => {
      mockUsePortfolioSnapshots.mockReturnValue({
        data: [
          {
            id: 'snap-1',
            simulationId: 'sim-123',
            timestamp: '2025-11-01T00:00:00Z',
            positions: { BTC: { symbol: 'BTC', quantity: 0.5, averagePrice: 50000, currentPrice: 50000 } },
            totalValue: 35000,
            cashBalance: 10000,
          },
        ],
        isLoading: false,
        error: null,
      } as any);

      mockUseMarketDataContext.mockReturnValue({
        data: {
          tickers: {
            BTC: {
              timeframes: {
                '1m': {
                  events: [
                    {
                      id: 'event-1',
                      symbol: 'BTC',
                      eventType: 'PRICE',
                      timestamp: '2025-11-01T12:00:00Z',
                      rawData: { price: 55000 },
                    },
                  ],
                },
              },
            },
          },
        },
        isLoading: false,
        error: null,
      } as any);

      const { result } = renderHook(() => useSimulationData(), { wrapper });

      await waitFor(() => {
        const btcMarketEvents = result.current.marketEvents.crypto.find(t => t.symbol === 'BTC');
        expect(btcMarketEvents).toBeDefined();
        expect(btcMarketEvents?.events[0].price).toBe(55000);
      });
    });

    it('handles empty portfolio snapshots', async () => {
      mockUsePortfolioSnapshots.mockReturnValue({
        data: [],
        isLoading: false,
        error: null,
      } as any);

      const { result } = renderHook(() => useSimulationData(), { wrapper });

      await waitFor(() => {
        expect(result.current.portfolioData).toBeNull();
      });
    });
  });

  describe('WebSocket Message Handling', () => {
    it('handles CONSENSUS_STARTED message', () => {
      const { result } = renderHook(() => useSimulationData(), { wrapper });
      expect(result.current.isProcessingConsensus).toBe(false);

      emit('CONSENSUS_STARTED', {
        consensusRequestId: 'req-1',
        decisionTimestamp: '2025-11-01T12:00:00Z',
      });

      expect(result.current.isProcessingConsensus).toBe(true);
      expect(mockSetActiveConsensus).toHaveBeenCalledWith('req-1');
      expect(result.current.consensusRoundProgress).toEqual({});
    });

    it('handles LLM_DECISION message', () => {
      const { result } = renderHook(() => useSimulationData(), { wrapper });

      emit('CONSENSUS_STARTED', {
        consensusRequestId: 'req-1',
        decisionTimestamp: '2025-11-01T12:00:00Z',
      });
      emit('LLM_DECISION', {
        decisionId: 1,
        agentType: 'TECHNICAL',
        reasoning: 'Momentum is positive',
        suggestedAction: 'BUY',
        confidence: 0.8,
        ticker: 'BTC',
      });

      // Analyst decisions land in agentDecisions and advance the round-progress banner...
      expect(result.current.agentDecisions.TECHNICAL).toMatchObject({ suggestedAction: 'BUY' });
      expect(result.current.consensusRoundProgress.TECHNICAL).toBeDefined();
      // ...but the round is not over until the orchestrator speaks
      expect(result.current.isProcessingConsensus).toBe(true);
      expect(result.current.orchestratorDecisionsByTicker).toEqual({});

      emit('LLM_DECISION', {
        decisionId: 2,
        agentType: 'ORCHESTRATOR',
        reasoning: 'Agents agree',
        suggestedAction: 'BUY',
        confidence: 0.9,
        ticker: 'BTC',
      });

      // The orchestrator is the final agent: it is filed per ticker, not into agentDecisions,
      // and it clears the processing flag.
      expect(result.current.orchestratorDecisionsByTicker.BTC).toMatchObject({ confidence: 0.9 });
      expect(result.current.agentDecisions.ORCHESTRATOR).toBeUndefined();
      expect(result.current.isProcessingConsensus).toBe(false);
    });

    it('handles TRADE_EXECUTION message', () => {
      const { result } = renderHook(() => useSimulationData(), { wrapper });

      emit('TRADE_EXECUTION', {
        consensusDecisionId: 1,
        action: 'BUY',
        symbol: 'BTC',
        quantity: 0.5,
        executionPrice: 50000,
        success: true,
        message: 'BUY executed',
        portfolioSnapshotId: 10,
      });
      // HOLD is a decision, not a trade — it must not appear in the executed-trades feed
      emit('TRADE_EXECUTION', {
        consensusDecisionId: 2,
        action: 'HOLD',
        symbol: 'BTC',
        quantity: 0,
        executionPrice: 0,
        success: true,
        message: 'No trade',
        portfolioSnapshotId: null,
      });

      expect(result.current.tradeExecutions).toHaveLength(1);
      expect(result.current.tradeExecutions[0]).toMatchObject({ action: 'BUY', symbol: 'BTC' });

      // Both trades refetch the timeline regardless of action — this handler is what keeps
      // MAJORITY consensus decisions visible, since LLM_DECISION only fires for
      // ORCHESTRATOR_OVERRIDE. Two useConsensusDecisions call sites × 2 messages = 4.
      expect(mockRefetchConsensusDecisions).toHaveBeenCalledTimes(4);
    });

    it('handles PORTFOLIO_UPDATE message', () => {
      const { result } = renderHook(() => useSimulationData(), { wrapper });

      emit('PORTFOLIO_UPDATE', {
        cashBalance: 5000,
        totalValue: 30000,
        positions: {
          BTC: { quantity: 0.5, averagePrice: 40000, currentPrice: 50000 },
        },
      });

      expect(result.current.livePortfolioData).toMatchObject({ cash: 5000, totalValue: 30000 });

      // Every configured ticker gets a holding row, whether or not a position is held —
      // the strategy fixture configures BTC, ETH, TSLA and NVDA.
      const holdings = result.current.livePortfolioData!.holdings;
      expect(holdings.map(h => h.symbol).sort()).toEqual(['BTC', 'ETH', 'NVDA', 'TSLA']);

      const btc = holdings.find(h => h.symbol === 'BTC')!;
      expect(btc).toMatchObject({ amount: 0.5, buyPrice: 40000, currentPrice: 50000, type: 'crypto' });
      expect(btc.change).toBeCloseTo(0.25); // (50000 - 40000) / 40000

      // No position → zeroed row, and no divide-by-zero in the change calculation
      expect(holdings.find(h => h.symbol === 'ETH')).toMatchObject({ amount: 0, change: 0 });
      expect(holdings.find(h => h.symbol === 'TSLA')).toMatchObject({ type: 'stock' });

      expect(result.current.livePortfolioData!.valueHistory).toHaveLength(1);
      expect(result.current.livePortfolioData!.valueHistory[0]).toMatchObject({
        cash: 5000,
        total: 30000,
      });
    });

    it('handles ERROR message', () => {
      const { result } = renderHook(() => useSimulationData(), { wrapper });

      emit('CONSENSUS_STARTED', {
        consensusRequestId: 'req-1',
        decisionTimestamp: '2025-11-01T12:00:00Z',
      });
      expect(result.current.isProcessingConsensus).toBe(true);

      // An unrelated error must not cancel a running consensus round
      emit('ERROR', { error: 'websocket hiccup' });
      expect(result.current.isProcessingConsensus).toBe(true);

      // CONSENSUS_FAILED does — otherwise the banner spins forever
      emit('ERROR', { error: 'consensus blew up', code: 'CONSENSUS_FAILED' });
      expect(result.current.isProcessingConsensus).toBe(false);
    });
  });

  describe('Market Events Processing', () => {
    it('groups market events by crypto and stocks', async () => {
      mockUseMarketDataContext.mockReturnValue({
        data: {
          tickers: {
            BTC: {
              timeframes: {
                '1m': {
                  events: [
                    {
                      id: 'event-1',
                      symbol: 'BTC',
                      eventType: 'PRICE',
                      timestamp: '2025-11-01T12:00:00Z',
                      rawData: { price: 55000 },
                    },
                  ],
                },
              },
            },
            TSLA: {
              timeframes: {
                '1m': {
                  events: [
                    {
                      id: 'event-2',
                      symbol: 'TSLA',
                      eventType: 'PRICE',
                      timestamp: '2025-11-01T12:00:00Z',
                      rawData: { price: 250 },
                    },
                  ],
                },
              },
            },
          },
        },
        isLoading: false,
        error: null,
      } as any);

      const { result } = renderHook(() => useSimulationData(), { wrapper });

      await waitFor(() => {
        expect(result.current.marketEvents.crypto).toHaveLength(1);
        expect(result.current.marketEvents.stocks).toHaveLength(1);
        expect(result.current.marketEvents.crypto[0].symbol).toBe('BTC');
        expect(result.current.marketEvents.stocks[0].symbol).toBe('TSLA');
      });
    });

    it('sorts events by timestamp descending within each ticker', async () => {
      mockUseMarketDataContext.mockReturnValue({
        data: {
          tickers: {
            BTC: {
              timeframes: {
                '1m': {
                  events: [
                    {
                      id: 'event-1',
                      symbol: 'BTC',
                      eventType: 'PRICE',
                      timestamp: '2025-11-01T10:00:00Z',
                      rawData: { price: 54000 },
                    },
                    {
                      id: 'event-2',
                      symbol: 'BTC',
                      eventType: 'PRICE',
                      timestamp: '2025-11-01T12:00:00Z',
                      rawData: { price: 55000 },
                    },
                  ],
                },
              },
            },
          },
        },
        isLoading: false,
        error: null,
      } as any);

      const { result } = renderHook(() => useSimulationData(), { wrapper });

      await waitFor(() => {
        const btcEvents = result.current.marketEvents.crypto.find(t => t.symbol === 'BTC');
        expect(btcEvents?.events[0].price).toBe(55000); // Latest first
        expect(btcEvents?.events[1].price).toBe(54000);
      });
    });
  });

  describe('Consensus History', () => {
    it('builds consensus history from lightweight useConsensusDecisions', async () => {
      // consensusHistory now comes from useConsensusDecisions (lightweight, no agent data)
      mockUseConsensusDecisions.mockReturnValue({
        data: [
          {
            id: 'consensus-1',
            simulationId: 'sim-123',
            symbol: 'BTC',
            finalAction: '{"action":"BUY","symbol":"BTC"}',
            decisionTimestamp: '2025-11-01T12:00:00Z',
            executed: true,
          },
        ],
        isLoading: false,
        error: null,
        refetch: jest.fn(),
      } as any);

      const { result } = renderHook(() => useSimulationData(), { wrapper });

      await waitFor(() => {
        expect(result.current.consensusHistory).toBeDefined();
        expect(result.current.consensusHistory).toHaveLength(1);
        expect(result.current.consensusHistory![0].symbol).toBe('BTC');
      });
    });
  });
});
