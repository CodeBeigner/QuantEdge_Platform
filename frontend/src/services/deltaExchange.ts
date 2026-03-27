/**
 * Delta Exchange API Client
 *
 * For MVP, calls Delta Exchange testnet directly from frontend.
 * Production should proxy through the Spring Boot backend to keep API secrets safe.
 */

const TESTNET_BASE = 'https://cdn-ind.testnet.deltaex.org';
const PRODUCTION_BASE = 'https://api.india.delta.exchange';

function getConfig() {
  try {
    return JSON.parse(localStorage.getItem('delta_exchange_config') || '{}');
  } catch {
    return {};
  }
}

function getBaseUrl(): string {
  const cfg = getConfig();
  return cfg.useTestnet !== false ? TESTNET_BASE : PRODUCTION_BASE;
}

async function generateSignature(
  method: string,
  path: string,
  queryString: string,
  body: string,
  timestamp: string,
  secret: string,
): Promise<string> {
  const message = method + timestamp + path + queryString + body;
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const sig = await crypto.subtle.sign('HMAC', key, encoder.encode(message));
  return Array.from(new Uint8Array(sig))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}

async function deltaRequest<T>(
  method: string,
  path: string,
  params?: Record<string, string>,
  body?: unknown,
  auth = false,
): Promise<T> {
  const cfg = getConfig();
  const base = getBaseUrl();
  const queryString = params ? '?' + new URLSearchParams(params).toString() : '';
  const url = `${base}${path}${queryString}`;
  const bodyStr = body ? JSON.stringify(body) : '';

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'User-Agent': 'QuantEdge/1.0',
  };

  if (auth && cfg.apiKey && cfg.apiSecret) {
    const timestamp = String(Math.floor(Date.now() / 1000));
    const signature = await generateSignature(
      method,
      path,
      queryString,
      bodyStr,
      timestamp,
      cfg.apiSecret,
    );
    headers['api-key'] = cfg.apiKey;
    headers['signature'] = signature;
    headers['timestamp'] = timestamp;
  }

  const res = await fetch(url, {
    method,
    headers,
    body: method !== 'GET' && bodyStr ? bodyStr : undefined,
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error?.message || err.message || `Delta Exchange error (${res.status})`);
  }

  const data = await res.json();
  if (data.success === false) {
    throw new Error(data.error?.message || 'Delta Exchange request failed');
  }
  return data.result ?? data;
}

// ─── Public Endpoints (no auth) ───

export interface DeltaProduct {
  id: number;
  symbol: string;
  description: string;
  underlying_asset: { symbol: string };
  quoting_asset: { symbol: string };
  tick_size: string;
  contract_value: string;
  contract_type: string;
  state: string;
}

export interface DeltaTicker {
  symbol: string;
  product_id: number;
  mark_price: string;
  last_price: string;
  open: string;
  high: string;
  low: string;
  close: string;
  volume: number;
  turnover: string;
  change_24h: string;
  funding_rate: string;
}

export interface OrderBookLevel {
  price: string;
  size: number;
}

export interface DeltaOrderBook {
  buy: OrderBookLevel[];
  sell: OrderBookLevel[];
  symbol: string;
  last_updated_at: number;
}

export interface DeltaOrder {
  id: number;
  product_id: number;
  product_symbol: string;
  side: 'buy' | 'sell';
  size: number;
  unfilled_size: number;
  order_type: string;
  limit_price: string;
  stop_price: string;
  state: string;
  created_at: string;
  bracket_stop_loss_price?: string;
  bracket_take_profit_price?: string;
  client_order_id?: string;
}

export interface DeltaPosition {
  product_id: number;
  product_symbol: string;
  size: number;
  entry_price: string;
  margin: string;
  liquidation_price: string;
  realized_pnl: string;
  unrealized_pnl: string;
}

export interface DeltaBalance {
  asset_symbol: string;
  available_balance: string;
  balance: string;
  position_margin: string;
  order_margin: string;
}

// ─── API Methods ───

export const deltaApi = {
  // Check if configured
  isConfigured: () => {
    const cfg = getConfig();
    return !!(cfg.apiKey && cfg.apiSecret);
  },

  getConfig,

  saveConfig: (apiKey: string, apiSecret: string, useTestnet = true) => {
    localStorage.setItem('delta_exchange_config', JSON.stringify({ apiKey, apiSecret, useTestnet }));
  },

  clearConfig: () => {
    localStorage.removeItem('delta_exchange_config');
  },

  // Public endpoints
  getProducts: (params?: Record<string, string>) =>
    deltaRequest<DeltaProduct[]>('GET', '/v2/products', params),

  getTickers: (params?: Record<string, string>) =>
    deltaRequest<DeltaTicker[]>('GET', '/v2/tickers', params),

  getTicker: (symbol: string) =>
    deltaRequest<DeltaTicker>('GET', `/v2/tickers/${symbol}`),

  getOrderBook: (productId: number, depth = 20) =>
    deltaRequest<DeltaOrderBook>('GET', '/v2/l2orderbook', {
      product_id: String(productId),
      depth: String(depth),
    }),

  // Authenticated endpoints
  getBalances: () =>
    deltaRequest<DeltaBalance[]>('GET', '/v2/wallet/balances', undefined, undefined, true),

  getOpenOrders: (productId?: number) =>
    deltaRequest<DeltaOrder[]>('GET', '/v2/orders', productId ? {
      product_id: String(productId),
      state: 'open',
    } : { state: 'open' }, undefined, true),

  getOrderHistory: (params?: Record<string, string>) =>
    deltaRequest<DeltaOrder[]>('GET', '/v2/orders/history', params, undefined, true),

  getPositions: () =>
    deltaRequest<DeltaPosition[]>('GET', '/v2/positions/margined', undefined, undefined, true),

  placeOrder: (order: {
    product_symbol: string;
    side: 'buy' | 'sell';
    size: number;
    order_type: 'limit_order' | 'market_order';
    limit_price?: string;
    stop_price?: string;
    stop_order_type?: string;
    bracket_stop_loss_price?: string;
    bracket_stop_loss_limit_price?: string;
    bracket_take_profit_price?: string;
    bracket_take_profit_limit_price?: string;
    time_in_force?: string;
    reduce_only?: boolean;
  }) => deltaRequest<DeltaOrder>('POST', '/v2/orders', undefined, order, true),

  placeBracketOrder: (params: {
    product_id: number;
    stop_loss_order?: {
      order_type: string;
      stop_price: string;
      limit_price?: string;
    };
    take_profit_order?: {
      order_type: string;
      stop_price: string;
      limit_price?: string;
    };
    bracket_stop_trigger_method?: string;
  }) => deltaRequest<unknown>('POST', '/v2/orders/bracket', undefined, params, true),

  cancelOrder: (orderId: number, productId: number) =>
    deltaRequest<unknown>('DELETE', '/v2/orders', undefined, {
      id: orderId,
      product_id: productId,
    }, true),

  cancelAllOrders: (productId?: number) =>
    deltaRequest<unknown>('DELETE', '/v2/orders/all', undefined,
      productId ? { product_id: productId } : undefined, true),

  setLeverage: (productId: number, leverage: number) =>
    deltaRequest<unknown>('POST', `/v2/products/${productId}/orders/leverage`, undefined, {
      leverage,
    }, true),
};

// ─── WebSocket ───

export type DeltaWsHandler = (data: unknown) => void;

export function createDeltaWebSocket(
  channels: string[],
  onMessage: (channel: string, data: unknown) => void,
  onConnect?: () => void,
  onDisconnect?: () => void,
): { close: () => void } {
  const cfg = getConfig();
  const base = cfg.useTestnet !== false
    ? 'wss://cdn-ind.testnet.deltaex.org/v2/ws'
    : 'wss://api.india.delta.exchange/v2/ws';

  let ws: WebSocket | null = null;
  let reconnectTimer: ReturnType<typeof setTimeout>;
  let alive = true;

  function connect() {
    if (!alive) return;
    ws = new WebSocket(base);

    ws.onopen = () => {
      onConnect?.();
      // Subscribe to public channels
      const pubChannels = channels.filter(c => !['Orders', 'Positions', 'Margins', 'UserTrades'].includes(c));
      if (pubChannels.length > 0) {
        ws?.send(JSON.stringify({ type: 'subscribe', channels: pubChannels }));
      }
      // Subscribe to private channels if configured
      const privChannels = channels.filter(c => ['Orders', 'Positions', 'Margins', 'UserTrades'].includes(c));
      if (privChannels.length > 0 && cfg.apiKey && cfg.apiSecret) {
        const timestamp = String(Math.floor(Date.now() / 1000));
        // For private channels, we need async signature — fire and forget
        generateSignature('GET', '/v2/ws', '', '', timestamp, cfg.apiSecret).then(signature => {
          ws?.send(JSON.stringify({
            type: 'subscribe',
            channels: privChannels,
            api_key: cfg.apiKey,
            signature,
            timestamp,
          }));
        });
      }
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.type === 'update' || msg.type === 'snapshot') {
          onMessage(msg.channel || '', msg.data || msg);
        }
      } catch { /* ignore */ }
    };

    ws.onclose = () => {
      onDisconnect?.();
      if (alive) reconnectTimer = setTimeout(connect, 3000);
    };

    ws.onerror = () => {
      ws?.close();
    };
  }

  connect();

  return {
    close: () => {
      alive = false;
      clearTimeout(reconnectTimer);
      ws?.close();
    },
  };
}
