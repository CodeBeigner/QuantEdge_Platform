export interface PaperMetrics {
  tradeCount: number;
  winRate: number;
  sharpe: number;
  maxDrawdownPct: number;
  totalPnl: number;
  winningTrades: number;
  windowDays: number;
}

export interface PaperGate {
  sharpePass: boolean;
  drawdownPass: boolean;
  winRatePass: boolean;
  tradeCountPass: boolean;
  windowPass: boolean;
  allPass: boolean;
}

export interface PaperMetricsResponse {
  metrics: PaperMetrics;
  gate: PaperGate;
  criteria: Record<string, string>;
}

export interface PaperTrade {
  id: number;
  tradeId: string;
  symbol: string;
  direction: 'LONG' | 'SHORT';
  strategyName: string;
  entryPrice: number;
  stopLossPrice: number;
  takeProfitPrice: number;
  positionSize: number;
  effectiveLeverage: number;
  confidence: number;
  explanation: Record<string, unknown>;
  outcome: Record<string, unknown> | null;
  status: 'OPEN' | 'CLOSED';
  openedAt: string;
  closedAt: string | null;
}
