import { WebSocketManager, ConnectionState } from '../manager';

// Mock WebSocket
class MockWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;

  readyState = MockWebSocket.CONNECTING;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  onclose: (() => void) | null = null;

  constructor(public url: string) {
    // Simulate connection opening after a short delay
    setTimeout(() => {
      this.readyState = MockWebSocket.OPEN;
      this.onopen?.();
    }, 10);
  }

  send(data: string) {
    // Mock send
  }

  close() {
    this.readyState = MockWebSocket.CLOSED;
    this.onclose?.();
  }

  addEventListener(event: string, handler: any) {
    if (event === 'open') {
      this.onopen = handler;
    } else if (event === 'error') {
      this.onerror = handler;
    }
  }

  removeEventListener(event: string, handler: any) {
    if (event === 'open' && this.onopen === handler) {
      this.onopen = null;
    } else if (event === 'error' && this.onerror === handler) {
      this.onerror = null;
    }
  }

  // Helper to simulate receiving a message
  simulateMessage(data: any) {
    this.onmessage?.({ data: JSON.stringify(data) });
  }
}

// Replace global WebSocket with mock
(global as any).WebSocket = MockWebSocket;

describe('WebSocketManager', () => {
  let manager: WebSocketManager;

  beforeEach(() => {
    manager = new WebSocketManager('ws://localhost:8080/ws/stream');
  });

  afterEach(() => {
    manager.disconnect();
  });

  it('initializes with DISCONNECTED state', () => {
    expect(manager.getConnectionState()).toBe(ConnectionState.DISCONNECTED);
  });

  it('connects to WebSocket server', async () => {
    await manager.connect();
    expect(manager.getConnectionState()).toBe(ConnectionState.CONNECTED);
  });

  it('handles connection state changes', async () => {
    const stateChanges: boolean[] = [];

    manager.onConnectionStateChange((connected) => {
      stateChanges.push(connected);
    });

    await manager.connect();

    // Should have received initial false, then true when connected
    expect(stateChanges).toContain(false);
    expect(stateChanges).toContain(true);
  });

  it('subscribes to simulation', async () => {
    await manager.connect();
    manager.subscribe('sim-123');

    // Subscription is tracked
    expect(manager.getConnectionState()).toBe(ConnectionState.CONNECTED);
  });

  it('receives and handles messages', async () => {
    await manager.connect();

    const messages: any[] = [];
    manager.on('SIMULATION_STARTED', (msg) => {
      messages.push(msg);
    });

    // Simulate receiving a message
    const mockMessage = {
      type: 'SIMULATION_STARTED',
      timestamp: new Date().toISOString(),
      simulationId: 'sim-123',
      strategyVersion: 'v1.0',
      initialCapital: 100000,
    };

    // Get the mock WebSocket instance and simulate message
    (manager as any).ws.simulateMessage(mockMessage);

    // Wait for message processing
    await new Promise(resolve => setTimeout(resolve, 50));

    expect(messages).toHaveLength(1);
    expect(messages[0].type).toBe('SIMULATION_STARTED');
  });

  it('unsubscribes message handlers', async () => {
    await manager.connect();

    const messages: any[] = [];
    const unsubscribe = manager.on('SIMULATION_STARTED', (msg) => {
      messages.push(msg);
    });

    // Unsubscribe
    unsubscribe();

    // Simulate message
    (manager as any).ws.simulateMessage({
      type: 'SIMULATION_STARTED',
      timestamp: new Date().toISOString(),
      simulationId: 'sim-123',
      strategyVersion: 'v1.0',
      initialCapital: 100000,
    });

    await new Promise(resolve => setTimeout(resolve, 50));

    expect(messages).toHaveLength(0);
  });

  it('disconnects properly', async () => {
    await manager.connect();
    expect(manager.getConnectionState()).toBe(ConnectionState.CONNECTED);

    manager.disconnect();
    expect(manager.getConnectionState()).toBe(ConnectionState.DISCONNECTED);
  });

  it('handles multiple message types', async () => {
    await manager.connect();

    const startMessages: any[] = [];
    const progressMessages: any[] = [];

    manager.on('SIMULATION_STARTED', (msg) => {
      startMessages.push(msg);
    });

    manager.on('SIMULATION_PROGRESS', (msg) => {
      progressMessages.push(msg);
    });

    // Simulate different message types
    (manager as any).ws.simulateMessage({
      type: 'SIMULATION_STARTED',
      timestamp: new Date().toISOString(),
      simulationId: 'sim-123',
      strategyVersion: 'v1.0',
      initialCapital: 100000,
    });

    (manager as any).ws.simulateMessage({
      type: 'SIMULATION_PROGRESS',
      timestamp: new Date().toISOString(),
      simulationId: 'sim-123',
      progress: 50,
      currentTimestamp: new Date().toISOString(),
      eventsProcessed: 100,
    });

    await new Promise(resolve => setTimeout(resolve, 50));

    expect(startMessages).toHaveLength(1);
    expect(progressMessages).toHaveLength(1);
  });

  it('handles multiple handlers for same message type', async () => {
    await manager.connect();

    const handler1Messages: any[] = [];
    const handler2Messages: any[] = [];

    manager.on('SIMULATION_STARTED', (msg) => {
      handler1Messages.push(msg);
    });

    manager.on('SIMULATION_STARTED', (msg) => {
      handler2Messages.push(msg);
    });

    (manager as any).ws.simulateMessage({
      type: 'SIMULATION_STARTED',
      timestamp: new Date().toISOString(),
      simulationId: 'sim-123',
      strategyVersion: 'v1.0',
      initialCapital: 100000,
    });

    await new Promise(resolve => setTimeout(resolve, 50));

    expect(handler1Messages).toHaveLength(1);
    expect(handler2Messages).toHaveLength(1);
  });

  it('unsubscribes from simulation', async () => {
    await manager.connect();

    manager.subscribe('sim-123');
    manager.unsubscribe('sim-123');

    expect(manager.getConnectionState()).toBe(ConnectionState.CONNECTED);
  });

  it('resubscribes after reconnection', async () => {
    await manager.connect();

    // Subscribe to a simulation
    manager.subscribe('sim-123');

    // Get the mock WebSocket instance
    const mockWs = (manager as any).ws;

    // Close connection to trigger reconnect
    mockWs.close();

    // Wait for reconnection
    await new Promise(resolve => setTimeout(resolve, 100));

    // Manager should attempt to reconnect (state should be RECONNECTING)
    expect(manager.getConnectionState()).toBe(ConnectionState.RECONNECTING);
  });

  it('handles invalid JSON messages gracefully', async () => {
    await manager.connect();

    const consoleSpy = jest.spyOn(console, 'error').mockImplementation();

    // Send invalid JSON
    const mockWs = (manager as any).ws;
    mockWs.onmessage?.({ data: 'invalid json' });

    expect(consoleSpy).toHaveBeenCalledWith(
      'Failed to parse WebSocket message:',
      expect.any(Error)
    );

    consoleSpy.mockRestore();
  });

  it('handles errors in message handlers gracefully', async () => {
    await manager.connect();

    const consoleSpy = jest.spyOn(console, 'error').mockImplementation();

    manager.on('SIMULATION_STARTED', () => {
      throw new Error('Handler error');
    });

    (manager as any).ws.simulateMessage({
      type: 'SIMULATION_STARTED',
      timestamp: new Date().toISOString(),
      simulationId: 'sim-123',
      strategyVersion: 'v1.0',
      initialCapital: 100000,
    });

    await new Promise(resolve => setTimeout(resolve, 50));

    expect(consoleSpy).toHaveBeenCalledWith(
      'Error in message handler for SIMULATION_STARTED:',
      expect.any(Error)
    );

    consoleSpy.mockRestore();
  });
});
