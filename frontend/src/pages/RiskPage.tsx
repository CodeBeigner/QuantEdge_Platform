import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid,
} from 'recharts';
import {
  Shield, AlertTriangle, TrendingDown, BarChart3, Loader2,
} from 'lucide-react';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import type { RiskMetrics, Alert } from '@/types';

function colorForValue(value: number, warnThreshold: number, dangerThreshold: number) {
  if (Math.abs(value) >= dangerThreshold) return { color: 'var(--error)' };
  if (Math.abs(value) >= warnThreshold) return { color: '#fbbf24' };
  return { color: 'var(--tertiary)' };
}

function MetricCard({ label, value, icon: Icon, warn, danger }: {
  label: string;
  value: string | number;
  icon: React.ComponentType<{ size?: number; className?: string; style?: React.CSSProperties }>;
  warn: number;
  danger: number;
}) {
  const numVal = typeof value === 'number' ? value : parseFloat(value) || 0;
  return (
    <div style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }} className="p-5">
      <div className="flex items-center justify-between mb-3">
        <span className="text-xs uppercase tracking-wider" style={{ color: 'var(--outline)' }}>{label}</span>
        <Icon size={18} style={{ color: 'var(--primary)' }} />
      </div>
      <p className="text-2xl font-bold font-mono" style={colorForValue(numVal, warn, danger)}>
        {typeof value === 'number' ? `${(value * 100).toFixed(2)}%` : value}
      </p>
    </div>
  );
}

export default function RiskPage() {
  const [symbol, setSymbol] = useState('SPY');

  const { data: symbols = [] } = useQuery<string[]>({
    queryKey: ['symbols'],
    queryFn: api.getSymbols,
  });

  const { data: risk, isLoading: riskLoading, error: riskError } = useQuery<RiskMetrics>({
    queryKey: ['risk-var', symbol],
    queryFn: () => api.getVaR(symbol),
    enabled: !!symbol,
  });

  const { data: positionLimits } = useQuery<Record<string, unknown>>({
    queryKey: ['risk-positions'],
    queryFn: api.checkPositionLimits,
  });

  const { data: portfolioRisk } = useQuery<Record<string, unknown>>({
    queryKey: ['risk-portfolio'],
    queryFn: api.getPortfolioRisk,
  });

  const { data: alerts = [] } = useQuery<Alert[]>({
    queryKey: ['alerts'],
    queryFn: api.getAlerts,
  });

  const riskAlerts = alerts.filter(a => a.type?.toLowerCase().includes('risk') || a.severity === 'HIGH' || a.severity === 'CRITICAL');

  const distributionData = (risk?.dailyReturns ?? []).map((r, i) => ({
    index: i,
    return: +(r * 100).toFixed(4),
  }));

  const posLimitsOk = positionLimits && !(positionLimits as Record<string, unknown>).breaches;

  const severityColor: Record<string, { bg: string; color: string }> = {
    LOW: { bg: 'rgba(var(--primary-rgb, 59,130,246), 0.15)', color: 'var(--primary)' },
    MEDIUM: { bg: 'rgba(251,191,36,0.15)', color: '#fbbf24' },
    HIGH: { bg: 'rgba(249,115,22,0.15)', color: '#f97316' },
    CRITICAL: { bg: 'rgba(var(--error-rgb, 239,68,68), 0.15)', color: 'var(--error)' },
  };

  return (
    <div className="space-y-6 max-w-[1400px] mx-auto">
      <PageHeader title="Risk Management" subtitle="VAR // CVAR // DRAWDOWN MONITORING" />

      {/* Symbol selector */}
      <div>
        <label className="block text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>Symbol</label>
        <select
          value={symbol}
          onChange={e => setSymbol(e.target.value)}
          className="px-3 py-2 text-sm outline-none cursor-pointer"
          style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)', color: 'var(--on-surface)' }}
        >
          {symbols.length === 0 && <option value="SPY">SPY</option>}
          {symbols.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>

      {/* Metric cards */}
      {riskLoading ? (
        <div className="flex items-center gap-2" style={{ color: 'var(--outline)' }}><Loader2 size={18} className="animate-spin" /> Loading risk metrics...</div>
      ) : riskError ? (
        <div className="p-5" style={{ background: 'var(--surface-container-low)', border: '1px solid rgba(var(--error-rgb, 239,68,68), 0.3)' }}>
          <div className="flex items-center gap-2" style={{ color: 'var(--error)' }}>
            <AlertTriangle size={18} />
            <span className="text-sm font-medium">Failed to load risk metrics</span>
          </div>
          <p className="text-xs mt-2" style={{ color: 'var(--on-surface-variant)' }}>{(riskError as Error).message}</p>
        </div>
      ) : risk ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <MetricCard label="VaR (95%)" value={risk.var95} icon={BarChart3} warn={0.02} danger={0.05} />
          <MetricCard label="CVaR (95%)" value={risk.cvar95} icon={TrendingDown} warn={0.03} danger={0.06} />
          <MetricCard label="Max Drawdown" value={risk.maxDrawdown} icon={TrendingDown} warn={0.1} danger={0.2} />
          <div className="p-5" style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}>
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs uppercase tracking-wider" style={{ color: 'var(--outline)' }}>Position Limits</span>
              <Shield size={18} style={{ color: 'var(--primary)' }} />
            </div>
            <p className="text-2xl font-bold" style={{ color: posLimitsOk ? 'var(--tertiary)' : 'var(--error)' }}>
              {posLimitsOk ? 'Within Limits' : 'Breached'}
            </p>
          </div>
        </div>
      ) : null}

      {/* VaR Distribution */}
      {distributionData.length > 0 && (
        <div className="p-5" style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}>
          <h2 className="font-semibold mb-4" style={{ color: 'var(--on-surface)' }}>Daily Returns Distribution</h2>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={distributionData}>
                <defs>
                  <linearGradient id="returnGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#3b82f6" stopOpacity={0.3} />
                    <stop offset="100%" stopColor="#3b82f6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--outline-variant)" />
                <XAxis dataKey="index" tick={{ fill: '#64748b', fontSize: 11 }} />
                <YAxis tick={{ fill: '#64748b', fontSize: 11 }} tickFormatter={v => `${v}%`} />
                <Tooltip
                  contentStyle={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)', borderRadius: 8, color: 'var(--on-surface)', fontSize: 12 }}
                  labelFormatter={l => `Day ${l}`}
                  formatter={(v: unknown) => [`${Number(v).toFixed(4)}%`, 'Return']}
                />
                <Area type="monotone" dataKey="return" stroke="#3b82f6" fill="url(#returnGrad)" strokeWidth={1.5} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Portfolio risk summary */}
      {portfolioRisk && (
        <div className="p-5" style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}>
          <h2 className="font-semibold mb-3" style={{ color: 'var(--on-surface)' }}>Portfolio Risk Summary</h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            {Object.entries(portfolioRisk).map(([k, v]) => (
              <div key={k} className="p-3" style={{ background: 'var(--surface)' }}>
                <span className="text-xs" style={{ color: 'var(--outline)' }}>{k.replace(/_/g, ' ')}</span>
                <p className="text-sm font-mono mt-0.5" style={{ color: 'var(--on-surface)' }}>
                  {typeof v === 'number' ? v.toFixed(4) : String(v)}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Risk alerts */}
      {riskAlerts.length > 0 && (
        <div className="p-5" style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}>
          <div className="flex items-center gap-2 mb-4">
            <AlertTriangle size={18} style={{ color: '#fbbf24' }} />
            <h2 className="font-semibold" style={{ color: 'var(--on-surface)' }}>Risk Alerts</h2>
          </div>
          <div className="space-y-2">
            {riskAlerts.map(a => (
              <div key={a.id} className="flex items-start gap-3 p-3" style={{ background: 'var(--surface)' }}>
                <span
                  className="text-[10px] font-bold uppercase px-2 py-0.5 rounded-full shrink-0 mt-0.5"
                  style={{ background: severityColor[a.severity]?.bg, color: severityColor[a.severity]?.color }}
                >
                  {a.severity}
                </span>
                <div className="flex-1 min-w-0">
                  <p className="text-sm" style={{ color: 'var(--on-surface)' }}>{a.message}</p>
                  <p className="text-xs mt-0.5" style={{ color: 'var(--outline)' }}>{new Date(a.createdAt).toLocaleString()}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
