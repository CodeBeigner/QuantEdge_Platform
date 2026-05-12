import { useQuery } from '@tanstack/react-query';
import { Activity, Check, X, TrendingUp, TrendingDown } from 'lucide-react';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import type { PaperGate, PaperMetrics, PaperTrade } from '@/types/paperTrading';

function TrafficLight({ pass, label, value }: { pass: boolean; label: string; value: string }) {
  return (
    <div
      className="p-4 rounded-lg"
      style={{
        background: 'var(--surface-container-low)',
        border: `1px solid ${pass ? 'rgba(0,255,136,0.4)' : 'rgba(239,68,68,0.4)'}`,
      }}
    >
      <div className="flex items-center gap-2 mb-1">
        {pass ? <Check size={16} className="text-[#00ff88]" /> : <X size={16} className="text-[#ef4444]" />}
        <span className="text-sm font-medium" style={{ color: 'var(--on-surface)' }}>{label}</span>
      </div>
      <div className="text-xl font-mono" style={{ color: pass ? 'var(--tertiary)' : 'var(--error)' }}>
        {value}
      </div>
    </div>
  );
}

function GateDashboard({ metrics, gate }: { metrics: PaperMetrics; gate: PaperGate }) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-5 gap-3">
      <TrafficLight pass={gate.sharpePass}    label="Sharpe > 1.5"    value={metrics.sharpe.toFixed(2)} />
      <TrafficLight pass={gate.drawdownPass}  label="MaxDD < 15%"      value={`${(metrics.maxDrawdownPct * 100).toFixed(1)}%`} />
      <TrafficLight pass={gate.winRatePass}   label="WinRate 55-65%"   value={`${(metrics.winRate * 100).toFixed(1)}%`} />
      <TrafficLight pass={gate.tradeCountPass} label="Trades > 50"     value={`${metrics.tradeCount}`} />
      <TrafficLight pass={gate.windowPass}    label="Window ≥ 4 weeks" value={`${metrics.windowDays}d`} />
    </div>
  );
}

function TradeRow({ trade }: { trade: PaperTrade }) {
  const isLong = trade.direction === 'LONG';
  const outcome = trade.outcome?.outcome as string | undefined;
  const pnl = trade.outcome?.realized_pnl as number | undefined;
  return (
    <tr className="border-b" style={{ borderColor: 'var(--outline-variant)' }}>
      <td className="py-2 px-3 text-sm font-mono" style={{ color: 'var(--on-surface)' }}>{trade.symbol}</td>
      <td className="py-2 px-3">
        <span className="inline-flex items-center gap-1 text-sm">
          {isLong ? <TrendingUp size={12} className="text-[#00ff88]" /> : <TrendingDown size={12} className="text-[#ef4444]" />}
          {trade.direction}
        </span>
      </td>
      <td className="py-2 px-3 text-sm" style={{ color: 'var(--on-surface-variant)' }}>{trade.strategyName}</td>
      <td className="py-2 px-3 text-sm font-mono">${trade.entryPrice.toFixed(2)}</td>
      <td className="py-2 px-3 text-sm">
        <span className={`px-2 py-0.5 rounded text-xs ${
          trade.status === 'OPEN' ? 'bg-blue-500/20 text-blue-400' : 'bg-gray-500/20 text-gray-400'
        }`}>
          {trade.status}
        </span>
      </td>
      <td className="py-2 px-3 text-sm">{outcome ?? '—'}</td>
      <td className="py-2 px-3 text-sm font-mono" style={{ color: (pnl ?? 0) >= 0 ? 'var(--tertiary)' : 'var(--error)' }}>
        {pnl !== undefined ? `${pnl >= 0 ? '+' : ''}${pnl.toFixed(2)}` : '—'}
      </td>
    </tr>
  );
}

export default function PaperTradingPage() {
  const metrics = useQuery({
    queryKey: ['paper-metrics', 28],
    queryFn: () => api.getPaperMetrics(28),
    refetchInterval: 30_000,
  });
  const trades = useQuery({
    queryKey: ['paper-trades'],
    queryFn: () => api.getPaperTrades(),
    refetchInterval: 30_000,
  });

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Paper Trading"
        subtitle="Rolling 4-week validation gate + trade history"
      >
        <Activity size={20} />
      </PageHeader>

      {metrics.data && (
        <>
          <div>
            <h3 className="text-sm font-semibold mb-3" style={{ color: 'var(--on-surface-variant)' }}>
              Validation Gate (spec §4.7)
            </h3>
            <GateDashboard metrics={metrics.data.metrics} gate={metrics.data.gate} />
          </div>

          <div
            className="p-4 rounded-lg"
            style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}
          >
            <div className="flex items-baseline justify-between mb-2">
              <span className="text-xs" style={{ color: 'var(--outline)' }}>
                {metrics.data.metrics.tradeCount} trades · {metrics.data.metrics.winningTrades} wins ·
                {' '}total P&L ${metrics.data.metrics.totalPnl.toFixed(2)}
              </span>
              <span className="text-xs font-semibold" style={{
                color: metrics.data.gate.allPass ? 'var(--tertiary)' : 'var(--outline)'
              }}>
                {metrics.data.gate.allPass ? '✅ ALL CRITERIA PASS' : '⏳ IN VALIDATION'}
              </span>
            </div>
          </div>
        </>
      )}

      {trades.data && (
        <div
          className="p-4 rounded-lg"
          style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}
        >
          <h3 className="text-sm font-semibold mb-3" style={{ color: 'var(--on-surface-variant)' }}>
            Trade History
          </h3>
          {trades.data.length === 0 ? (
            <div className="text-sm text-center py-6" style={{ color: 'var(--outline)' }}>
              No paper trades yet. The scheduler runs every 15 minutes on :00/:15/:30/:45 UTC.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead>
                  <tr className="text-xs uppercase" style={{ color: 'var(--outline)' }}>
                    <th className="py-2 px-3">Symbol</th>
                    <th className="py-2 px-3">Direction</th>
                    <th className="py-2 px-3">Strategy</th>
                    <th className="py-2 px-3">Entry</th>
                    <th className="py-2 px-3">Status</th>
                    <th className="py-2 px-3">Outcome</th>
                    <th className="py-2 px-3">P&L</th>
                  </tr>
                </thead>
                <tbody>
                  {trades.data.map(t => <TradeRow key={t.id} trade={t} />)}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
