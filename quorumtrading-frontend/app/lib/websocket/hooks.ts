'use client';

import { useEffect, useState, useCallback } from 'react';
import { getWebSocketManager, ConnectionState } from './manager';
import type { StreamMessage, StreamMessageType } from '../types/websocket';

/**
 * Hook to manage WebSocket connection
 */
export function useWebSocket() {
  const [connectionState, setConnectionState] = useState<ConnectionState>(ConnectionState.DISCONNECTED);
  const wsManager = getWebSocketManager();

  useEffect(() => {
    // Connect on mount
    wsManager.connect().catch(error => {
      console.error('Failed to connect to WebSocket:', error);
    });

    // Subscribe to connection state changes
    const unsubscribe = wsManager.onConnectionStateChange((connected) => {
      setConnectionState(wsManager.getConnectionState());
    });

    // Only unsubscribe from state changes on unmount
    // Don't disconnect the singleton WebSocket manager
    return () => {
      unsubscribe();
    };
  }, [wsManager]);

  return {
    connectionState,
    isConnected: connectionState === ConnectionState.CONNECTED,
    isConnecting: connectionState === ConnectionState.CONNECTING,
    isReconnecting: connectionState === ConnectionState.RECONNECTING,
  };
}

/**
 * Hook to subscribe to simulation stream
 */
export function useSimulationStream(simulationId: string | null) {
  const wsManager = getWebSocketManager();

  useEffect(() => {
    if (!simulationId) return;

    wsManager.subscribe(simulationId);

    return () => {
      wsManager.unsubscribe(simulationId);
    };
  }, [simulationId, wsManager]);
}

/**
 * Hook to listen for specific message types
 */
export function useStreamMessage<T extends StreamMessage>(
  type: StreamMessageType,
  handler: (message: T) => void,
  deps: any[] = []
) {
  const wsManager = getWebSocketManager();

  useEffect(() => {
    const unsubscribe = wsManager.on(type, handler as (message: StreamMessage) => void);
    return unsubscribe;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type, wsManager, ...deps]);
}
