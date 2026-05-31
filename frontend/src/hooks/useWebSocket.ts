import { useEffect, useRef, useState } from 'react';
import type { WebSocketPrice } from '@/types';

export function useWebSocket() {
  const [prices, setPrices] = useState<Record<string, WebSocketPrice>>({});
  const [connected, setConnected] = useState(false);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const clientRef = useRef<any>(null);

  useEffect(() => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
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
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            stompClient.subscribe('/topic/prices', (msg: any) => {
              try {
                const tick = JSON.parse(msg.body as string);
                setPrices((prev) => ({ ...prev, [tick.symbol]: tick }));
              } catch (e) { console.error('Failed to parse price message:', e); }
            });
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            stompClient.subscribe('/topic/agents', (msg: any) => {
              try {
                const status = JSON.parse(msg.body);
                window.dispatchEvent(new CustomEvent('agent:status', { detail: status }));
              } catch (e) { console.error('Failed to parse agent message:', e); }
            });
          },
          onDisconnect: () => setConnected(false),
          onStompError: () => setConnected(false),
        });

        stompClient.activate();
        clientRef.current = stompClient;
      } catch (e) {
        console.error('WebSocket libraries not available:', e);
      }
    };

    connect();
    return () => {
      if (stompClient) stompClient.deactivate();
    };
  }, []);

  return { prices, connected };
}
