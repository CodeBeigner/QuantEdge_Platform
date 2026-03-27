export interface User {
  id: number;
  name: string;
  email: string;
  token: string;
}

export interface MarketPrice {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface Strategy {
  id: number;
  name: string;
  symbol: string;
  modelType: 'MOMENTUM' | 'VOLATILITY' | 'MACRO' | 'CORRELATION' | 'REGIME';
  parameters: Record<string, unknown>;
  active: boolean;
  createdAt: string;
}

export interface ExecutionResult {
  action: string;
  quantity: number;
  price: number;
  confidence: number;
  reasoning: string;
  success: boolean;
  metadata?: Record<string, unknown>;
}

export interface BacktestResult {
  id: number;
  strategyId: number;
  startDate: string;
  endDate: string;
  initialCapital: number;
  finalCapital: number;
  totalReturn: number;
  sharpeRatio: number;
  maxDrawdown: number;
  winRate: number;
  totalTrades: number;
  equityCurve: { date: string; value: number }[];
}

export type AgentRole =
  | 'QUANT_RESEARCHER'
  | 'BIAS_AUDITOR'
  | 'RISK_ANALYST'
  | 'PORTFOLIO_CONSTRUCTOR'
  | 'PSYCHOLOGY_ENFORCER'
  | 'PERFORMANCE_ATTRIBUTOR'
  | 'MARKET_REGIME_ANALYST'
  | 'EXECUTION_OPTIMIZER'
  | 'HFT_SYSTEMS_ENGINEER'
  | 'EXECUTION_MONITOR';

export type AgentLifecycleState =
  | 'CREATED'
  | 'CONFIGURING'
  | 'PAPER_TRADING'
  | 'LIVE'
  | 'PAUSED'
  | 'HALTED'
  | 'DECOMMISSIONED';

export interface TradingAgent {
  id: number;
  name: string;
  strategyId: number;
  cronExpression: string;
  active: boolean;
  lastRunAt: string | null;
  createdAt: string;
  updatedAt: string;
  agentRole: AgentRole;
  systemPromptPreview: string | null;
  lastReasoning: string | null;
  lastConfidence: number | null;
  totalExecutions: number;
  successfulExecutions: number;
  personaName: string | null;
  personaColor: string | null;
  personaInitials: string | null;
  lifecycleState?: AgentLifecycleState;
}

export interface Order {
  id: number;
  symbol: string;
  side: 'BUY' | 'SELL';
  type: 'MARKET' | 'LIMIT' | 'STOP' | 'STOP_LIMIT';
  quantity: number;
  price: number;
  status: 'PENDING' | 'FILLED' | 'PARTIALLY_FILLED' | 'CANCELLED' | 'REJECTED';
  filledPrice?: number;
  filledAt?: string;
  createdAt: string;
}

export interface Position {
  symbol: string;
  quantity: number;
  averageCost: number;
  currentPrice: number;
  unrealizedPnl: number;
  realizedPnl: number;
  marketValue: number;
}

export interface Portfolio {
  totalValue: number;
  cash: number;
  positions: Position[];
  totalPnl: number;
  dayPnl: number;
}

export interface RiskMetrics {
  var95: number;
  cvar95: number;
  maxDrawdown: number;
  breaches: boolean;
  dailyReturns?: number[];
}

export interface Alert {
  id: number;
  type: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  message: string;
  acknowledged: boolean;
  createdAt: string;
}

export interface FirmProfile {
  id: number;
  firmName: string;
  firmType: string;
  initialCapital: number;
  riskAppetite: string;
  createdAt: string;
}

export interface MLPrediction {
  signal: string;
  confidence: number;
  direction_prob: { up: number; down: number };
  features?: Record<string, number>;
  model_accuracy?: number;
  ensemble?: boolean;
  model_used?: string;
}

export interface PipelineResult {
  regime_analysis?: Record<string, unknown>;
  research_decision?: Record<string, unknown>;
  bias_audit?: Record<string, unknown>;
  risk_assessment?: Record<string, unknown>;
  psychology_check?: Record<string, unknown>;
  pipeline_status?: string;
  pipeline_complete?: boolean;
  final_decision?: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
}

export interface WebSocketPrice {
  symbol: string;
  price: number;
  change: number;
  changePercent: number;
  timestamp: string;
}

export interface Notification {
  id: string;
  type: 'info' | 'success' | 'warning' | 'error';
  title: string;
  message: string;
  timestamp: Date;
  read: boolean;
  action?: () => void;
}

export interface WorkspaceLayout {
  id: string;
  name: string;
  panels: { id: string; component: string; position: { x: number; y: number; w: number; h: number } }[];
}
