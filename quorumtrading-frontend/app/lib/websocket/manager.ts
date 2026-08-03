import type { StreamMessage, SubscriptionRequest, ResearchSubscriptionRequest, StreamMessageType } from '../types/websocket';

type MessageHandler = (message: StreamMessage) => void;
type ConnectionStateHandler = (connected: boolean) => void;

export enum ConnectionState {
  DISCONNECTED = 'DISCONNECTED',
  CONNECTING = 'CONNECTING',
  CONNECTED = 'CONNECTED',
  RECONNECTING = 'RECONNECTING',
}

export class WebSocketManager {
  private ws: WebSocket | null = null;
  private url: string;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000; // Start with 1 second
  private reconnectTimeout: NodeJS.Timeout | null = null;
  private connectionState: ConnectionState = ConnectionState.DISCONNECTED;

  // Message handlers by type
  private messageHandlers: Map<StreamMessageType, Set<MessageHandler>> = new Map();

  // Connection state handlers
  private connectionStateHandlers: Set<ConnectionStateHandler> = new Set();

  // Active simulation subscriptions
  private subscriptions: Set<string> = new Set();

  // Active research subscriptions
  private researchSubscriptions: Set<string> = new Set();

  constructor(url?: string) {
    this.url = url || process.env.NEXT_PUBLIC_WS_URL || 'ws://localhost:8085/ws/stream';
  }

  /**
   * Connect to WebSocket server
   */
  connect(): Promise<void> {
    if (this.ws?.readyState === WebSocket.OPEN) {
      return Promise.resolve();
    }

    if (this.ws?.readyState === WebSocket.CONNECTING) {
      return new Promise((resolve, reject) => {
        const onOpen = () => {
          cleanup();
          resolve();
        };
        const onError = () => {
          cleanup();
          reject(new Error('WebSocket connection failed'));
        };
        const cleanup = () => {
          this.ws?.removeEventListener('open', onOpen);
          this.ws?.removeEventListener('error', onError);
        };
        this.ws?.addEventListener('open', onOpen);
        this.ws?.addEventListener('error', onError);
      });
    }

    return new Promise((resolve, reject) => {
      this.setConnectionState(ConnectionState.CONNECTING);

      try {
        this.ws = new WebSocket(this.url);

        this.ws.onopen = () => {
          this.reconnectAttempts = 0;
          this.reconnectDelay = 1000;
          this.setConnectionState(ConnectionState.CONNECTED);

          // Resubscribe to all active simulation subscriptions
          this.subscriptions.forEach(simulationId => {
            this.send({ action: 'subscribe', simulationId });
          });

          // Resubscribe to all active research subscriptions
          this.researchSubscriptions.forEach(researchId => {
            this.sendResearch({ action: 'subscribeResearch', researchId });
          });

          resolve();
        };

        this.ws.onmessage = (event) => {
          try {
            const message: StreamMessage = JSON.parse(event.data);
            this.handleMessage(message);
          } catch (error) {
            console.error('Failed to parse WebSocket message:', error);
          }
        };

        this.ws.onerror = (event) => {
          console.error('WebSocket error:', event);
          reject(new Error('WebSocket connection error'));
        };

        this.ws.onclose = () => {
          this.setConnectionState(ConnectionState.DISCONNECTED);
          this.attemptReconnect();
        };
      } catch (error) {
        this.setConnectionState(ConnectionState.DISCONNECTED);
        reject(error);
      }
    });
  }

  /**
   * Disconnect from WebSocket server
   */
  disconnect(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }

    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }

    this.subscriptions.clear();
    this.researchSubscriptions.clear();
    this.setConnectionState(ConnectionState.DISCONNECTED);
  }

  /**
   * Subscribe to simulation updates
   */
  subscribe(simulationId: string): void {
    this.subscriptions.add(simulationId);

    if (this.connectionState === ConnectionState.CONNECTED) {
      this.send({ action: 'subscribe', simulationId });
    }
  }

  /**
   * Unsubscribe from simulation updates
   */
  unsubscribe(simulationId: string): void {
    this.subscriptions.delete(simulationId);

    if (this.connectionState === ConnectionState.CONNECTED) {
      this.send({ action: 'unsubscribe', simulationId });
    }
  }

  /**
   * Subscribe to research execution updates
   */
  subscribeToResearch(researchId: string): void {
    this.researchSubscriptions.add(researchId);

    if (this.connectionState === ConnectionState.CONNECTED) {
      this.sendResearch({ action: 'subscribeResearch', researchId });
    }
  }

  /**
   * Unsubscribe from research execution updates
   */
  unsubscribeFromResearch(researchId: string): void {
    this.researchSubscriptions.delete(researchId);

    if (this.connectionState === ConnectionState.CONNECTED) {
      this.sendResearch({ action: 'unsubscribeResearch', researchId });
    }
  }

  /**
   * Add message handler for specific message type
   */
  on(type: StreamMessageType, handler: MessageHandler): () => void {
    if (!this.messageHandlers.has(type)) {
      this.messageHandlers.set(type, new Set());
    }

    this.messageHandlers.get(type)!.add(handler);

    // Return unsubscribe function
    return () => {
      this.messageHandlers.get(type)?.delete(handler);
    };
  }

  /**
   * Add connection state change handler
   */
  onConnectionStateChange(handler: ConnectionStateHandler): () => void {
    this.connectionStateHandlers.add(handler);

    // Immediately call with current state
    handler(this.connectionState === ConnectionState.CONNECTED);

    // Return unsubscribe function
    return () => {
      this.connectionStateHandlers.delete(handler);
    };
  }

  /**
   * Get current connection state
   */
  getConnectionState(): ConnectionState {
    return this.connectionState;
  }

  /**
   * Send custom action to server
   */
  sendAction(action: string, payload: Record<string, any> = {}): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ action, ...payload }));
    } else {
      console.warn('WebSocket not connected, cannot send action:', action);
    }
  }

  /**
   * Send simulation subscription message to server
   */
  private send(message: SubscriptionRequest): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    }
  }

  /**
   * Send research subscription message to server
   */
  private sendResearch(message: ResearchSubscriptionRequest): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    }
  }

  /**
   * Handle incoming message
   */
  private handleMessage(message: StreamMessage): void {
    const handlers = this.messageHandlers.get(message.type);

    if (handlers) {
      handlers.forEach(handler => {
        try {
          handler(message);
        } catch (error) {
          console.error(`Error in message handler for ${message.type}:`, error);
        }
      });
    }
  }

  /**
   * Set connection state and notify handlers
   */
  private setConnectionState(state: ConnectionState): void {
    this.connectionState = state;
    const connected = state === ConnectionState.CONNECTED;

    this.connectionStateHandlers.forEach(handler => {
      try {
        handler(connected);
      } catch (error) {
        console.error('Error in connection state handler:', error);
      }
    });
  }

  /**
   * Attempt to reconnect with exponential backoff
   */
  private attemptReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('Max reconnection attempts reached');
      return;
    }

    this.reconnectAttempts++;
    this.setConnectionState(ConnectionState.RECONNECTING);

    // Exponential backoff: 1s, 2s, 4s, 8s, 16s
    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1);

    console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);

    this.reconnectTimeout = setTimeout(() => {
      this.connect().catch(error => {
        console.error('Reconnection failed:', error);
      });
    }, delay);
  }
}

// Singleton instance
let wsManager: WebSocketManager | null = null;

export function getWebSocketManager(): WebSocketManager {
  if (!wsManager) {
    wsManager = new WebSocketManager();
  }
  return wsManager;
}
