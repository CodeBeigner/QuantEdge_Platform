import type {
  User, MarketPrice, Strategy, ExecutionResult, BacktestResult,
  TradingAgent, Order, Position, Portfolio, RiskMetrics, Alert,
  FirmProfile, MLPrediction, PipelineResult, ChatMessage,
  RiskConfig, SystemHealth, SystemVersion, DeltaConnectionStatus, TradeLog, MultiTFBacktestResult,
} from '@/types';

const API_BASE = '/api/v1';

class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

function getToken(): string | null {
  return localStorage.getItem('quantedge_token');
}

function headers(withAuth = true): HeadersInit {
  const h: Record<string, string> = { 'Content-Type': 'application/json' };
  if (withAuth) {
    const token = getToken();
    if (token) h['Authorization'] = `Bearer ${token}`;
  }
  return h;
}

async function request<T>(url: string, options: RequestInit = {}, retries = 3): Promise<T> {
  let lastErr: Error | null = null;
  for (let attempt = 0; attempt < retries; attempt++) {
    try {
      const res = await fetch(url, options);
      if (res.status === 401) {
        const body = await res.json().catch(() => ({}));
        const message = body.error || body.message || 'Session expired';
        const hadToken = !!getToken();
        if (hadToken) {
          localStorage.removeItem('quantedge_token');
          localStorage.removeItem('quantedge_user');
          window.dispatchEvent(new Event('auth:expired'));
        }
        throw new ApiError(401, message);
      }
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new ApiError(res.status, body.error || body.message || `Request failed (${res.status})`);
      }
      if (res.status === 204) return undefined as T;
      return await res.json();
    } catch (err) {
      lastErr = err as Error;
      if (err instanceof ApiError) throw err;
      if (attempt < retries - 1) {
        await new Promise(r => setTimeout(r, Math.pow(2, attempt) * 1000));
      }
    }
  }
  throw lastErr;
}

function get<T>(path: string) {
  return request<T>(`${API_BASE}${path}`, { headers: headers() });
}

function post<T>(path: string, body?: unknown) {
  return request<T>(`${API_BASE}${path}`, {
    method: 'POST',
    headers: headers(),
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

function put<T>(path: string, body?: unknown) {
  return request<T>(`${API_BASE}${path}`, {
    method: 'PUT',
    headers: headers(),
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

function del<T>(path: string) {
  return request<T>(`${API_BASE}${path}`, { method: 'DELETE', headers: headers() });
}

export const api = {
  // Auth
  register: (name: string, email: string, password: string) =>
    request<User>(`${API_BASE}/auth/register`, {
      method: 'POST',
      headers: headers(false),
      body: JSON.stringify({ name, email, password }),
    }).then(data => {
      localStorage.setItem('quantedge_token', data.token);
      localStorage.setItem('quantedge_user', JSON.stringify(data));
      return data;
    }),

  login: (email: string, password: string) =>
    request<User>(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: headers(false),
      body: JSON.stringify({ email, password }),
    }).then(data => {
      localStorage.setItem('quantedge_token', data.token);
      localStorage.setItem('quantedge_user', JSON.stringify(data));
      return data;
    }),

  logout: () => {
    localStorage.removeItem('quantedge_token');
    localStorage.removeItem('quantedge_user');
  },

  getUser: (): User | null => {
    try { return JSON.parse(localStorage.getItem('quantedge_user') || 'null'); }
    catch { return null; }
  },

  isLoggedIn: () => !!getToken(),

  // Market Data
  getSymbols: () => get<string[]>('/market-data/symbols'),
  getPrices: (symbol: string, days = 252) =>
    get<Record<string, unknown>[]>(`/market-data/prices/${symbol}?days=${days}`).then(
      data => data.map((d: Record<string, unknown>) => ({
        date: String(d.time ?? d.date ?? ''),
        open: Number(d.open ?? 0),
        high: Number(d.high ?? 0),
        low: Number(d.low ?? 0),
        close: Number(d.close ?? 0),
        volume: Number(d.volume ?? 0),
      })) as MarketPrice[]
    ),
  getPricesByRange: (symbol: string, start: string, end: string) =>
    get<Record<string, unknown>[]>(`/market-data/prices/${symbol}?start=${start}&end=${end}`).then(
      data => data.map((d: Record<string, unknown>) => ({
        date: String(d.time ?? d.date ?? ''),
        open: Number(d.open ?? 0),
        high: Number(d.high ?? 0),
        low: Number(d.low ?? 0),
        close: Number(d.close ?? 0),
        volume: Number(d.volume ?? 0),
      })) as MarketPrice[]
    ),
  getSummary: (symbol: string) => get<Record<string, unknown>>(`/market-data/summary/${symbol}`),

  // Strategies
  getStrategies: () => get<Strategy[]>('/strategies'),
  getStrategy: (id: number) => get<Strategy>(`/strategies/${id}`),
  createStrategy: (data: Partial<Strategy>) => post<Strategy>('/strategies', data),
  executeStrategy: (id: number) => post<ExecutionResult>(`/strategies/${id}/execute`),
  deleteStrategy: (id: number) => del<void>(`/strategies/${id}`),

  // Backtests
  runBacktest: (strategyId: number, startDate: string, endDate: string, initialCapital: number) =>
    post<BacktestResult>('/backtests', { strategyId, startDate, endDate, initialCapital }),
  getBacktests: (strategyId: number) => get<BacktestResult[]>(`/backtests/${strategyId}`),
  runWalkForward: (strategyId: number) => post<Record<string, unknown>>(`/backtests/${strategyId}/walk-forward`),

  // Signal Tracking
  getSignalIC: (strategyId: number, window = 20) =>
    get<Record<string, unknown>>(`/signals/ic/${strategyId}?window=${window}`),
  getSignalDrift: (strategyId: number) => get<Record<string, unknown>>(`/signals/drift/${strategyId}`),

  // Agents
  getAgents: () => get<TradingAgent[]>('/agents'),
  createAgent: (data: Partial<TradingAgent>) => post<TradingAgent>('/agents', data),
  startAgent: (id: number) => post<TradingAgent>(`/agents/${id}/start`),
  stopAgent: (id: number) => post<TradingAgent>(`/agents/${id}/stop`),
  deleteAgent: (id: number) => del<void>(`/agents/${id}`),
  runAgentPipeline: (id: number, symbol = 'SPY') =>
    post<PipelineResult>(`/agents/${id}/run-pipeline?symbol=${symbol}`),
  runAttribution: (id: number, symbol = 'SPY') =>
    post<Record<string, unknown>>(`/agents/${id}/attribution?symbol=${symbol}`),
  getAgentRoles: () => get<string[]>('/agents/roles'),
  runSystemAudit: (symbol = 'SPY') =>
    post<Record<string, unknown>>(`/agents/system-audit?symbol=${symbol}`),
  runExecutionMonitor: (symbol = 'SPY') =>
    post<Record<string, unknown>>(`/agents/execution-monitor?symbol=${symbol}`),

  // Agent Chat
  chatWithAgent: (agentId: number, message: string) =>
    post<{ response: string }>(`/agents/${agentId}/chat`, { message }),
  getAgentConversation: (agentId: number) =>
    get<ChatMessage[]>(`/agents/${agentId}/conversation`),
  clearAgentConversation: (agentId: number) =>
    del<void>(`/agents/${agentId}/conversation`),

  // CEO
  ceoBroadcast: (message: string) =>
    post<{ agentName: string; response: string }>('/agents/ceo-command', { message }),

  // Orders
  placeOrder: (data: Partial<Order>) => post<Order>('/orders', data),
  getOrders: () => get<Order[]>('/orders'),
  cancelOrder: (id: number) => post<Order>(`/orders/${id}/cancel`),
  getPositions: () => get<Position[]>('/orders/positions'),
  getPortfolio: () => get<Portfolio>('/orders/portfolio'),

  // Risk
  getVaR: (symbol: string, days = 252) => get<RiskMetrics>(`/risk/var/${symbol}?days=${days}`),
  checkPositionLimits: () => get<Record<string, unknown>>('/risk/positions'),
  getPortfolioRisk: () => get<Record<string, unknown>>('/risk/portfolio'),

  // Alerts
  getAlerts: () => get<Alert[]>('/alerts'),
  getUnacknowledgedAlerts: () => get<Alert[]>('/alerts/unacknowledged'),
  acknowledgeAlert: (id: number) => post<Alert>(`/alerts/${id}/acknowledge`),

  // ML
  mlPredict: (symbol: string) => post<MLPrediction>(`/ml/predict/${symbol}`),
  mlTrain: (symbol: string) => post<Record<string, unknown>>(`/ml/train/${symbol}`),
  mlFeatures: (symbol: string) => get<Record<string, number>[]>(`/ml/features/${symbol}`),
  mlOptimize: (symbols: string[]) => post<Record<string, unknown>>('/ml/optimize', { symbols }),
  mlSignals: () => get<Record<string, unknown>>('/ml/signals'),
  mlHealth: () => get<Record<string, unknown>>('/ml/health'),
  mlPredictEnsemble: (symbol: string) => post<MLPrediction>(`/ml/predict-ensemble/${symbol}`),
  mlTrainLstm: (symbol: string) => post<Record<string, unknown>>(`/ml/train-lstm/${symbol}`),

  // Firm
  setupFirm: (firmName: string, firmType: string, initialCapital: number, riskAppetite: string) =>
    post<FirmProfile>('/firm/setup', { firmName, firmType, initialCapital, riskAppetite }),
  getFirm: async (): Promise<FirmProfile | null> => {
    try { return await get<FirmProfile>('/firm'); }
    catch { return null; }
  },
  getFirmSetupStatus: () => get<{ isSetup: boolean }>('/firm/setup-status'),
  updateFirm: (firmName: string, riskAppetite: string) =>
    put<FirmProfile>('/firm', { firmName, riskAppetite }),

  // Health
  getHealth: async () => {
    try {
      const res = await fetch('/actuator/health');
      return await res.json();
    } catch { return { status: 'DOWN' }; }
  },

  // Market Hours
  getMarketStatus: () => get<Record<string, unknown>>('/market-hours'),
  isMarketOpen: () => get<{ isOpen: boolean }>('/market-hours/is-open'),

  // Agent Performance
  getAgentPerformance: () => get<Record<string, unknown>[]>('/agent-performance'),
  getAgentPerformanceById: (id: number) => get<Record<string, unknown>>(`/agent-performance/${id}`),
  getAggregatePerformance: () => get<Record<string, unknown>>('/agent-performance/aggregate'),
  seekConsensus: (context: string) => post<Record<string, unknown>>('/agent-performance/consensus', { context }),

  // Broker
  listBrokers: () => get<Record<string, unknown>[]>('/broker'),
  switchBroker: (broker: string) => post<Record<string, string>>('/broker/switch', { broker }),
  getBrokerAccount: () => get<Record<string, unknown>>('/broker/account'),
  reconcilePositions: () => post<Record<string, unknown>>('/broker/reconcile'),

  // ML Extended
  mlSaveModel: (symbol: string) => post<Record<string, unknown>>(`/ml/save-model/${symbol}`),
  mlLoadModel: (symbol: string) => post<Record<string, unknown>>(`/ml/load-model/${symbol}`),
  mlWalkForward: (symbol: string) => post<Record<string, unknown>>(`/ml/walk-forward/${symbol}`),
  mlOptimizeRobust: (symbols: string[]) => post<Record<string, unknown>>('/ml/optimize-robust', { symbols }),
  mlRiskParity: (symbols: string[]) => post<Record<string, unknown>>('/ml/risk-parity', { symbols }),
  mlModelInfo: (symbol: string) => get<Record<string, unknown>>(`/ml/model-info/${symbol}`),

  // Strategy Auto-Generation
  autoGenerateStrategy: (symbol: string) =>
    post<Record<string, unknown>>(`/strategies/auto-generate?symbol=${symbol}`),

  // === Phase 1-4 APIs ===

  getDeltaProducts: (testnet = true) =>
    request<any>(`${API_BASE}/delta/products?testnet=${testnet}`, { headers: headers() }),

  getDeltaTicker: (symbol: string, testnet = true) =>
    request<any>(`${API_BASE}/delta/ticker/${symbol}?testnet=${testnet}`, { headers: headers() }),

  getDeltaOrderBook: (productId: number, depth = 20, testnet = true) =>
    request<any>(`${API_BASE}/delta/orderbook/${productId}?depth=${depth}&testnet=${testnet}`, { headers: headers() }),

  saveDeltaCredentials: (apiKey: string, apiSecret: string, testnet = true) =>
    request<{ status: string; environment: string }>(`${API_BASE}/delta/credentials`, {
      method: 'POST', headers: headers(),
      body: JSON.stringify({ apiKey, apiSecret, testnet }),
    }),

  deleteDeltaCredentials: (testnet = true) =>
    request<void>(`${API_BASE}/delta/credentials?testnet=${testnet}`, {
      method: 'DELETE', headers: headers(),
    }),

  getDeltaConnectionStatus: (testnet = true) =>
    request<DeltaConnectionStatus>(`${API_BASE}/delta/connection-status?testnet=${testnet}`, { headers: headers() }),

  getRiskConfig: () =>
    request<RiskConfig>(`${API_BASE}/risk-config`, { headers: headers() }),

  updateRiskConfig: (config: Partial<RiskConfig>) =>
    request<RiskConfig>(`${API_BASE}/risk-config`, {
      method: 'PUT', headers: headers(),
      body: JSON.stringify(config),
    }),

  getSystemHealth: () =>
    request<SystemHealth>(`${API_BASE}/system/health`, { headers: headers() }),

  getSystemVersion: () =>
    request<SystemVersion>(`${API_BASE}/system/version`, { headers: headers() }),

  getTradeLogs: () =>
    request<TradeLog[]>(`${API_BASE}/trade-logs`, { headers: headers() }).catch(() => [] as TradeLog[]),

  getTradeLog: (tradeId: string) =>
    request<TradeLog>(`${API_BASE}/trade-logs/${tradeId}`, { headers: headers() }),

  runMultiTFBacktest: (config: { initialCapital?: number; slippageBps?: number }) =>
    request<MultiTFBacktestResult>(`${API_BASE}/backtests/multi-tf`, {
      method: 'POST', headers: headers(),
      body: JSON.stringify(config),
    }),
};
