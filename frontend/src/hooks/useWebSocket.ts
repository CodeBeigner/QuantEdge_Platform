import { useEffect, useRef, useState, useCallback } from 'react';
import type { WebSocketPrice } from '@/types';

export function useWebSocket() {
  const [prices, setPrices] = useState<Record<string, WebSocketPrice>>({});
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<any>(null);

  useEffect(() => {
    let stompClient: any = null;

    const connect = async () => {
      try {
        const { Client } = await import('@stomp/stompjs');
        const SockJS = (await import('sockjs-client')).default;

        stompClient = new Client({
          webSocketFactory: () => new SockJS('/ws'),
          reconnectDelay: 5000,
          onConnect: () => {
            setConnected(true);
            stompClient.subscribe('/topic/prices', (msg: any) => {
              try {
                const tick = JSON.parse(msg.body);
                setPrices((prev) => ({ ...prev, [tick.symbol]: tick }));
              } catch { /* ignore parse errors */ }
            });
            stompClient.subscribe('/topic/agents', (msg: any) => {
              try {
                const status = JSON.parse(msg.body);
                window.dispatchEvent(new CustomEvent('agent:status', { detail: status }));
              } catch { /* ignore */ }
            });
          },
          onDisconnect: () => setConnected(false),
          onStompError: () => setConnected(false),
        });

        stompClient.activate();
        clientRef.current = stompClient;
      } catch {
        // WebSocket libraries not available
      }
    };

    connect();
    return () => {
      if (stompClient) stompClient.deactivate();
    };
  }, []);

  return { prices, connected };
}
