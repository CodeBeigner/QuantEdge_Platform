/**
 * Delta Exchange API Client
 *
 * Routes through Vite dev proxy to avoid CORS.
 * /delta-testnet -> https://cdn-ind.testnet.deltaex.org
 * /delta-prod    -> https://api.india.delta.exchange
 */

// Proxy paths (Vite dev server rewrites these to the real Delta servers)
const TESTNET_PROXY = '/delta-testnet';
const PRODUCTION_PROXY = '/delta-prod';

// Real base URLs (for WebSocket — CORS doesn't apply to WS)
const TESTNET_WS = 'wss://cdn-ind.testnet.deltaex.org/v2/ws';
const PRODUCTION_WS = 'wss://api.india.delta.exchange/v2/ws';

// Real API paths (needed for HMAC signature calculation)
const TESTNET_REAL = 'https://cdn-ind.testnet.deltaex.org';
const PRODUCTION_REAL = 'https://api.india.delta.exchange';

function getConfig() {
  try {
    return JSON.parse(localStorage.getItem('delta_exchange_config') || '{}');
  } catch {
    return {};
  }
}

function getProxyBase(): string {
  const cfg = getConfig();
  return cfg.useTestnet !== false ? TESTNET_PROXY : PRODUCTION_PROXY;
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
  const proxyBase = getProxyBase();
  const queryString = params ? '?' + new URLSearchParams(params).toString() : '';
  // Fetch through Vite proxy
  const url = `${proxyBase}${path}${queryString}`;
  const bodyStr = body ? JSON.stringify(body) : '';

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  if (auth && cfg.apiKey && cfg.apiSecret) {
    const timestamp = String(Math.floor(Date.now() / 1000));
    // HMAC signature must use the REAL API path, not the proxy path
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
    throw new Error(err.error?.code || err.error?.message || err.message || `Delta Exchange error (${res.status})`);
  }

  const data = await res.json();
  if (data.success === false) {
    throw new Error(data.error?.code || data.error?.message || 'Delta Exchange request failed');
  }
  return data.result ?? data;
}

// ─── Types ───

export interface DeltaAsset {
  id: number;
  symbol: string;
  name: string;
  precision: number;
  minimum_precision: number;
}

export interface DeltaProduct {
  id: number;
  symbol: string;
  description: string;
  underlying_asset: { symbol: string; name?: string };
  quoting_asset: { symbol: string };
  settling_asset?: { symbol: string };
  tick_size: string;
  contract_value: string;
  contract_type: string;
  product_type?: string;
  state: string;
  max_leverage_notional?: string;
  initial_margin?: string;
  maintenance_margin?: string;
  impact_size?: number;
  launch_time?: string;
  settlement_time?: string | null;
  funding_method?: string;
  annualized_funding?: string;
  trading_status?: string;
  position_size_limit?: number;
  price_band?: { upper_limit: string; lower_limit: string };
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
  turnover_usd?: string;
  change_24h: string;
  funding_rate: string;
  oi?: string;
  oi_value?: string;
  oi_value_usd?: string;
  spot_price?: string;
  timestamp?: number;
  greeks?: {
    delta?: string;
    gamma?: string;
    theta?: string;
    vega?: string;
    rho?: string;
    iv?: string;
  };
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
  reduce_only?: boolean;
  time_in_force?: string;
  average_fill_price?: string;
  paid_commission?: string;
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
  adl_level?: number;
  auto_topup?: boolean;
  leverage?: string;
}

export interface DeltaBalance {
  asset_symbol: string;
  available_balance: string;
  balance: string;
  position_margin: string;
  order_margin: string;
  commission: string;
  available_balance_for_robo?: string;
}

export interface DeltaTrade {
  id?: number;
  price: string;
  size: number;
  side: 'buy' | 'sell';
  timestamp: number;
  buyer_role?: string;
  seller_role?: string;
}

export interface DeltaFill {
  id: number;
  product_id: number;
  product_symbol: string;
  side: 'buy' | 'sell';
  size: number;
  fill_type: string;
  price: string;
  role: string;
  commission: string;
  created_at: string;
  order_id?: number;
}

export interface DeltaOHLC {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
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

  // ─── Public Endpoints ───

  getAssets: () =>
    deltaRequest<DeltaAsset[]>('GET', '/v2/assets'),

  getProducts: (params?: Record<string, string>) =>
    deltaRequest<DeltaProduct[]>('GET', '/v2/products', params),

  getProductBySymbol: (symbol: string) =>
    deltaRequest<DeltaProduct>('GET', `/v2/products/${symbol}`),

  getTickers: (params?: Record<string, string>) =>
    deltaRequest<DeltaTicker[]>('GET', '/v2/tickers', params),

  getTicker: (symbol: string) =>
    deltaRequest<DeltaTicker>('GET', `/v2/tickers/${symbol}`),

  getOrderBook: (symbol: string, depth = 20) =>
    deltaRequest<DeltaOrderBook>('GET', `/v2/l2orderbook/${symbol}`, {
      depth: String(depth),
    }),

  getRecentTrades: (symbol: string) =>
    deltaRequest<DeltaTrade[]>('GET', `/v2/trades/${symbol}`),

  getOHLCCandles: (symbol: string, resolution: string, start?: number, end?: number) => {
    const params: Record<string, string> = {
      symbol,
      resolution,
    };
    if (start) params.start = String(start);
    if (end) params.end = String(end);
    return deltaRequest<DeltaOHLC[]>('GET', '/v2/history/candles', params);
  },

  // ─── Authenticated Endpoints ───

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

  getUserFills: (params?: Record<string, string>) =>
    deltaRequest<DeltaFill[]>('GET', '/v2/fills', params, undefined, true),

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

  getLeverage: (productId: number) =>
    deltaRequest<{ leverage: number }>('GET', `/v2/products/${productId}/orders/leverage`, undefined, undefined, true),
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
  const base = cfg.useTestnet !== false ? TESTNET_WS : PRODUCTION_WS;

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
