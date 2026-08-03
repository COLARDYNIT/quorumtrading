import { apiClient, ApiClientError } from '../client';
import {
  createMockStrategy,
  createMockStrategyRequest,
  createMockSimulation,
  createMockSimulationRequest,
  createMockMarketDataResponse,
  createMockMarketEvent,
  createMockLLMDecisionsResponse,
  createMockConsensusDecisionsResponse,
  createMockPortfolioSnapshotsResponse,
  createMockPortfolioSnapshot,
} from '@/app/lib/testing/factories';

// Mock fetch
global.fetch = jest.fn();

// Helper to create mock Response with headers
const createMockResponse = (data: any, status = 200, contentType = 'application/json') => ({
  ok: status >= 200 && status < 300,
  status,
  headers: {
    get: (name: string) => {
      if (name.toLowerCase() === 'content-type') return contentType;
      return null;
    }
  },
  json: async () => data,
});

describe('API Client', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('Strategies API', () => {
    it('fetches list of strategies', async () => {
      const mockStrategies = [createMockStrategy()];

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockStrategies));

      const result = await apiClient.strategies.list();

      expect(global.fetch).toHaveBeenCalledWith(
        'http://localhost:8085/api/v1/strategies',
        expect.objectContaining({
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
            'Accept': 'application/json',
          }),
        })
      );
      expect(result).toEqual(mockStrategies);
    });

    it('fetches a single strategy', async () => {
      const mockStrategy = createMockStrategy();

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockStrategy));

      const result = await apiClient.strategies.get(mockStrategy.id);

      expect(global.fetch).toHaveBeenCalledWith(
        `http://localhost:8085/api/v1/strategies/${mockStrategy.id}`,
        expect.anything()
      );
      expect(result).toEqual(mockStrategy);
    });

    it('throws ApiClientError on 404', async () => {
      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse({
        type: 'about:blank',
        title: 'Not Found',
        status: 404,
        detail: 'Strategy not found',
      }, 404));

      try {
        await apiClient.strategies.get('unknown-id');
        fail('Should have thrown an error');
      } catch (error) {
        expect(error).toBeInstanceOf(ApiClientError);
        expect((error as ApiClientError).message).toBe('Strategy not found');
      }
    });

    it('creates a new strategy', async () => {
      const newStrategy = createMockStrategyRequest();
      const mockResponse = createMockStrategy();

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockResponse, 201));

      const result = await apiClient.strategies.create(newStrategy);

      expect(global.fetch).toHaveBeenCalledWith(
        'http://localhost:8085/api/v1/strategies',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify(newStrategy),
        })
      );
      expect(result).toEqual(mockResponse);
    });

    it('updates a strategy', async () => {
      const updateData = createMockStrategyRequest({ name: 'Updated Strategy' });
      const mockResponse = createMockStrategy({ name: 'Updated Strategy', version: 2 });

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockResponse));

      const result = await apiClient.strategies.update(mockResponse.id, updateData);

      expect(global.fetch).toHaveBeenCalledWith(
        `http://localhost:8085/api/v1/strategies/${mockResponse.id}`,
        expect.objectContaining({
          method: 'PUT',
          body: JSON.stringify(updateData),
        })
      );
      expect(result).toEqual(mockResponse);
    });

    it('deletes a strategy', async () => {
      (global.fetch as jest.Mock).mockResolvedValueOnce({
        ok: true,
        status: 204,
        headers: { get: () => null },
      });

      const strategyId = '660f9511-f39c-52e5-b827-557766551111';
      const result = await apiClient.strategies.delete(strategyId);

      expect(global.fetch).toHaveBeenCalledWith(
        `http://localhost:8085/api/v1/strategies/${strategyId}`,
        expect.objectContaining({
          method: 'DELETE',
        })
      );
      expect(result).toBeNull();
    });
  });

  describe('Simulations API', () => {
    it('fetches list of simulations', async () => {
      const mockSimulations = [createMockSimulation()];

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockSimulations));

      const result = await apiClient.simulations.list();

      expect(result).toEqual(mockSimulations);
    });

    it('fetches a single simulation', async () => {
      const mockSimulation = createMockSimulation();

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockSimulation));

      const result = await apiClient.simulations.get(mockSimulation.id);

      expect(global.fetch).toHaveBeenCalledWith(
        `http://localhost:8085/api/v1/simulations/${mockSimulation.id}`,
        expect.anything()
      );
      expect(result).toEqual(mockSimulation);
    });

    it('creates a new simulation', async () => {
      const newSimulation = createMockSimulationRequest();
      const mockResponse = createMockSimulation({ status: 'PENDING', progress: 0 });

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockResponse, 201));

      const result = await apiClient.simulations.create(newSimulation);

      expect(global.fetch).toHaveBeenCalledWith(
        'http://localhost:8085/api/v1/simulations',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify(newSimulation),
        })
      );
      expect(result).toEqual(mockResponse);
    });

    it('starts a simulation', async () => {
      const mockSimulation = createMockSimulation({ status: 'SIMULATING', progress: 0 });

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockSimulation));

      const result = await apiClient.simulations.start(mockSimulation.id);

      expect(global.fetch).toHaveBeenCalledWith(
        `http://localhost:8085/api/v1/simulations/${mockSimulation.id}/start`,
        expect.objectContaining({
          method: 'POST',
        })
      );
      expect(result).toEqual(mockSimulation);
    });

    it('deletes a simulation', async () => {
      (global.fetch as jest.Mock).mockResolvedValueOnce({
        ok: true,
        status: 204,
        headers: { get: () => null },
      });

      const simulationId = '550e8400-e29b-41d4-a716-446655440000';
      const result = await apiClient.simulations.delete(simulationId);

      expect(global.fetch).toHaveBeenCalledWith(
        `http://localhost:8085/api/v1/simulations/${simulationId}`,
        expect.objectContaining({
          method: 'DELETE',
        })
      );
      expect(result).toBeNull();
    });
  });

  describe('Market Data API', () => {
    it('queries market events with filters', async () => {
      const mockResponse = createMockMarketDataResponse({
        events: [],
        total: 0,
      });

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockResponse));

      const result = await apiClient.marketData.query({
        from: '2025-01-01T00:00:00Z',
        to: '2025-01-02T00:00:00Z',
        symbol: 'BTC/USD',
        eventType: 'PRICE',
      });

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('symbol=BTC%2FUSD'),
        expect.anything()
      );
      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('eventType=PRICE'),
        expect.anything()
      );
      expect(result).toEqual(mockResponse);
    });

    it('queries market events with all parameters', async () => {
      const mockResponse = createMockMarketDataResponse({
        events: [createMockMarketEvent({ id: '123e4567-e89b-12d3-a456-426614174000' })],
        total: 1,
        from: '2025-01-01T00:00:00Z',
        to: '2025-01-02T00:00:00Z',
        limit: 10,
      });

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockResponse));

      const result = await apiClient.marketData.query({
        from: '2025-01-01T00:00:00Z',
        to: '2025-01-02T00:00:00Z',
        symbol: 'BTC/USD',
        eventType: 'PRICE',
        source: 'coinbase',
        limit: 10,
      });

      const callUrl = (global.fetch as jest.Mock).mock.calls[0][0];
      expect(callUrl).toContain('from=2025-01-01T00%3A00%3A00Z');
      expect(callUrl).toContain('to=2025-01-02T00%3A00%3A00Z');
      expect(callUrl).toContain('symbol=BTC%2FUSD');
      expect(callUrl).toContain('eventType=PRICE');
      expect(callUrl).toContain('source=coinbase');
      expect(callUrl).toContain('limit=10');
      expect(result).toEqual(mockResponse);
    });

    it('collects market data', async () => {
      const mockResponse = {
        status: 'SUCCESS',
        message: 'Market data collected successfully',
        eventsCollected: 42,
        timestamp: '2025-01-01T12:00:00Z',
      };

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockResponse));

      const result = await apiClient.marketData.collect();

      expect(global.fetch).toHaveBeenCalledWith(
        'http://localhost:8085/api/v1/market-data/collect',
        expect.objectContaining({
          method: 'POST',
        })
      );
      expect(result).toEqual(mockResponse);
    });
  });

  describe('Decisions API', () => {
    it('fetches LLM decisions for simulation', async () => {
      const mockResponse = createMockLLMDecisionsResponse();

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockResponse));

      const result = await apiClient.decisions.llm.list('550e8400-e29b-41d4-a716-446655440000');

      expect(global.fetch).toHaveBeenCalledWith(
        'http://localhost:8085/api/v1/decisions/llm?simulationId=550e8400-e29b-41d4-a716-446655440000',
        expect.anything()
      );
      expect(result).toEqual(mockResponse);
    });

    it('fetches a single LLM decision', async () => {
      const mockResponse = createMockLLMDecisionsResponse();
      const decision = mockResponse.decisions[0];

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(decision));

      const result = await apiClient.decisions.llm.get(decision.id);

      expect(global.fetch).toHaveBeenCalledWith(
        `http://localhost:8085/api/v1/decisions/llm/${decision.id}`,
        expect.anything()
      );
      expect(result).toEqual(decision);
    });

    it('fetches consensus decisions for simulation', async () => {
      const mockResponse = createMockConsensusDecisionsResponse();

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockResponse));

      const result = await apiClient.decisions.consensus.list('550e8400-e29b-41d4-a716-446655440000');

      expect(result).toEqual(mockResponse);
    });

    it('fetches a single consensus decision', async () => {
      const mockResponse = createMockConsensusDecisionsResponse();
      const decision = mockResponse.decisions[0];

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(decision));

      const result = await apiClient.decisions.consensus.get(decision.id);

      expect(global.fetch).toHaveBeenCalledWith(
        `http://localhost:8085/api/v1/decisions/consensus/${decision.id}`,
        expect.anything()
      );
      expect(result).toEqual(decision);
    });
  });

  describe('Portfolio API', () => {
    it('fetches portfolio snapshots', async () => {
      const mockResponse = createMockPortfolioSnapshotsResponse();

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockResponse));

      const result = await apiClient.portfolio.snapshots(
        '550e8400-e29b-41d4-a716-446655440000',
        '2025-01-01T00:00:00Z',
        '2025-01-02T00:00:00Z'
      );

      const callUrl = (global.fetch as jest.Mock).mock.calls[0][0];
      expect(callUrl).toContain('simulationId=550e8400-e29b-41d4-a716-446655440000');
      expect(callUrl).toContain('from=2025-01-01T00%3A00%3A00Z');
      expect(callUrl).toContain('to=2025-01-02T00%3A00%3A00Z');
      expect(result).toEqual(mockResponse);
    });

    it('fetches latest portfolio snapshot', async () => {
      const mockSnapshot = createMockPortfolioSnapshot();

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(mockSnapshot));

      const result = await apiClient.portfolio.latest('550e8400-e29b-41d4-a716-446655440000');

      expect(global.fetch).toHaveBeenCalledWith(
        'http://localhost:8085/api/v1/portfolios/snapshots/latest?simulationId=550e8400-e29b-41d4-a716-446655440000',
        expect.anything()
      );
      expect(result).toEqual(mockSnapshot);
    });
  });

  describe('Error Handling', () => {
    it('throws ApiClientError with proper properties', async () => {
      const errorResponse = {
        type: 'about:blank',
        title: 'Bad Request',
        status: 400,
        detail: 'Invalid strategy ID format',
        timestamp: '2025-01-01T12:00:00Z',
      };

      (global.fetch as jest.Mock).mockResolvedValueOnce(createMockResponse(errorResponse, 400));

      try {
        await apiClient.strategies.get('invalid');
        fail('Should have thrown an error');
      } catch (error) {
        expect(error).toBeInstanceOf(ApiClientError);
        expect((error as ApiClientError).status).toBe(400);
        expect((error as ApiClientError).problemDetail?.title).toBe('Bad Request');
        expect((error as ApiClientError).problemDetail?.detail).toBe('Invalid strategy ID format');
        expect((error as ApiClientError).problemDetail?.timestamp).toBe('2025-01-01T12:00:00Z');
      }
    });

    it('handles non-JSON error responses', async () => {
      (global.fetch as jest.Mock).mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        headers: new Headers({ 'content-type': 'text/plain' }),
        json: async () => {
          throw new Error('Not JSON');
        },
      });

      try {
        await apiClient.strategies.list();
        fail('Should have thrown an error');
      } catch (error) {
        expect(error).toBeInstanceOf(ApiClientError);
        expect((error as ApiClientError).status).toBe(500);
        expect((error as ApiClientError).message).toContain('Internal Server Error');
      }
    });
  });
});
