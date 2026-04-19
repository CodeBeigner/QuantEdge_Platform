import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
  AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer,
} from 'recharts';
import { toast } from 'sonner';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import {
  Link2, FlaskConical, Play, ArrowRight, Activity, TrendingUp, TrendingDown,
} from 'lucide-react';
import type { SystemHealth, RiskConfig, Position } from '@/types';

/* ─── Placeholder equity curve ─── */
function generateEquityCurve(points: number, startVal: number) {
  const data: { date: string; equity: number }[] = [];
  let val = startVal;
  const now = Date.now();
  for (let i = 0; i < points; i++) {
    val += val * ((Math.random() - 0.42) * 0.015);
    data.push({
      date: new Date(now - (points - i) * 86_400_000).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
      equity: Math.round(val * 100) / 100,
    });
  }
  return data;
}

/* ─── Skeleton pulse box ─── */
function Skeleton({ width, height, style }: { width?: string | number; height?: string | number; style?: React.CSSProperties }) {
  return (
    <div
      className="skeleton"
      style={{
        width: width ?? '100%',
        height: height ?? 20,
        borderRadius: 4,
        ...style,
      }}
    />
  );
}

/* ─── Timeframe options ─── */
const TIMEFRAMES = ['1D', '1W', '1M', 'ALL'] as const;
type Timeframe = (typeof TIMEFRAMES)[number];

const TIMEFRAME_POINTS: Record<Timeframe, number> = { '1D': 24, '1W': 7, '1M': 30, ALL: 90 };

/* ─── Number helpers ─── */
function fmt(n: number | null | undefined, opts?: { prefix?: string; suffix?: string; decimals?: number }) {
  if (n == null || isNaN(n)) return '\u2014';
  const { prefix = '', suffix = '', decimals = 2 } = opts ?? {};
  return `${prefix}${n.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}${suffix}`;
}

function fmtUsd(n: number | null | undefined) {
  if (n == null || isNaN(n)) return '\u2014';
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 2 }).format(n);
}

/* ─── KPI Metric Card ─── */
interface MetricCardProps {
  label: string;
  value: string;
  sublabel?: string;
  color: string;
  loading?: boolean;
}

function MetricCard({ label, value, sublabel, color, loading }: MetricCardProps) {
  return (
    <div
      style={{
        background: '#1a2235',
        borderLeft: `2px solid ${color}`,
        padding: '1rem 1.25rem',
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        transition: 'background 200ms ease-out',
        minWidth: 0,
      }}
      onMouseEnter={(e) => { (e.currentTarget as HTMLDivElement).style.background = '#1e2740'; }}
      onMouseLeave={(e) => { (e.currentTarget as HTMLDivElement).style.background = '#1a2235'; }}
    >
      <div style={{
        fontFamily: 'var(--font-mono)',
        fontSize: '0.625rem',
        color: 'var(--outline)',
        textTransform: 'uppercase',
        letterSpacing: '0.1em',
      }}>
        {label}
      </div>
      {loading ? (
        <Skeleton height={28} width="70%" />
      ) : (
        <div style={{
          fontFamily: 'var(--font-mono)',
          fontSize: '1.375rem',
          fontWeight: 700,
          color,
          letterSpacing: '-0.02em',
          lineHeight: 1.2,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}>
          {value}
        </div>
      )}
      {sublabel && (
        <div style={{
          fontFamily: 'var(--font-mono)',
          fontSize: '0.625rem',
          color: 'var(--outline)',
          marginTop: 2,
        }}>
          {sublabel}
        </div>
      )}
    </div>
  );
}

/* ─── Card Section wrapper ─── */
function SectionCard({ title, meta, children, style }: {
  title: string;
  meta?: string;
  children: React.ReactNode;
  style?: React.CSSProperties;
}) {
  return (
    <div className="qe-card" style={{ padding: 0, ...style }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '1rem 1.25rem 0.75rem',
      }}>
        <div style={{
          fontFamily: 'var(--font-body)',
          fontSize: '0.75rem',
          fontWeight: 600,
          color: 'var(--on-surface)',
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
        }}>
          {title}
        </div>
        {meta && (
          <div style={{
            fontFamily: 'var(--font-mono)',
            fontSize: '0.6875rem',
            color: 'var(--outline)',
          }}>
            {meta}
          </div>
        )}
      </div>
      <div style={{ padding: '0 1.25rem 1.25rem' }}>
        {children}
      </div>
    </div>
  );
}

/* ─── Risk Budget progress bar ─── */
function RiskBar({ label, value, max, fill, unit = '%' }: {
  label: string;
  value: number;
  max: number;
  fill: string;
  unit?: string;
}) {
  const pct = max > 0 ? Math.min((value / max) * 100, 100) : 0;
  return (
    <div style={{ marginBottom: '0.75rem' }}>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        fontFamily: 'var(--font-mono)',
        fontSize: '0.6875rem',
        marginBottom: 4,
      }}>
        <span style={{ color: 'var(--outline)' }}>{label}</span>
        <span style={{ color: 'var(--on-surface)' }}>
          {fmt(value, { decimals: 1 })}{unit} / {fmt(max, { decimals: 1 })}{unit}
        </span>
      </div>
      <div style={{
        height: 4,
        background: 'var(--surface-container-highest)',
        borderRadius: 2,
        overflow: 'hidden',
      }}>
        <div style={{
          height: '100%',
          width: `${pct}%`,
          background: fill,
          borderRadius: 2,
          transition: 'width 400ms ease-out',
        }} />
      </div>
    </div>
  );
}

/* ─── Custom Recharts Tooltip ─── */
function ChartTooltip({ active, payload, label }: { active?: boolean; payload?: { value: number }[]; label?: string }) {
  if (!active || !payload?.length) return null;
  return (
    <div style={{
      background: 'var(--surface-container-high)',
      border: '1px solid var(--outline-variant)',
      padding: '0.5rem 0.75rem',
      fontFamily: 'var(--font-mono)',
      fontSize: '0.75rem',
    }}>
      <div style={{ color: 'var(--outline)', marginBottom: 2 }}>{label}</div>
      <div style={{ color: '#00e479', fontWeight: 600 }}>{fmtUsd(payload[0].value)}</div>
    </div>
  );
}

/* ════════════════════════════════════════════════════════════════════
   DASHBOARD PAGE
   ════════════════════════════════════════════════════════════════════ */

export default function DashboardPage() {
  const navigate = useNavigate();
  const [timeframe, setTimeframe] = useState<Timeframe>('1M');

  /* ── Data fetching ── */
  const {
    data: health,
    isLoading: healthLoading,
    isError: healthError,
  } = useQuery<SystemHealth>({
    queryKey: ['systemHealth'],
    queryFn: () => api.getSystemHealth(),
    refetchInterval: 10_000,
    retry: 2,
  });

  const {
    data: riskConfig,
    isLoading: riskLoading,
    isError: riskError,
  } = useQuery<RiskConfig>({
    queryKey: ['riskConfig'],
    queryFn: () => api.getRiskConfig(),
    refetchInterval: 10_000,
    retry: 2,
  });

  const { data: positions = [] } = useQuery<Position[]>({
    queryKey: ['positions'],
    queryFn: () => api.getPositions(),
    refetchInterval: 10_000,
    retry: 1,
  });

  const { data: tradeLogs = [] } = useQuery({
    queryKey: ['tradeLogs'],
    queryFn: () => api.getTradeLogs(),
    refetchInterval: 10_000,
    retry: 1,
  });

  /* ── Toast on errors ── */
  if (healthError) toast.error('Failed to fetch system health');
  if (riskError) toast.error('Failed to fetch risk config');

  /* ── Derived values ── */
  const acct = health?.components?.account;
  const balance = acct?.balance ?? 0;
  const peakEquity = acct?.peakEquity ?? 0;
  const openPositionCount = acct?.openPositions ?? 0;

  const maxLeverage = riskConfig?.maxEffectiveLeverage ?? 5;
  const maxDrawdownPct = riskConfig?.maxDrawdownPct ?? 15;
  const dailyLossHaltPct = riskConfig?.dailyLossHaltPct ?? 3;
  const maxPositions = riskConfig?.maxConcurrentPositions ?? 5;

  /* Mock derived metrics (from health + trade logs) */
  const closedTrades = tradeLogs.filter((t) => t.status === 'CLOSED');
  const winningTrades = closedTrades.filter((t) => t.outcome?.result === 'WIN');
  const winRate = closedTrades.length > 0 ? (winningTrades.length / closedTrades.length) * 100 : 0;

  const dailyPnl = closedTrades.reduce((sum, t) => sum + (t.outcome?.pnl ?? 0), 0);
  const currentDrawdown = peakEquity > 0 ? ((peakEquity - balance) / peakEquity) * 100 : 0;

  /* Effective leverage from open positions */
  const totalNotional = positions.reduce((sum, p) => sum + Math.abs(p.marketValue), 0);
  const effLeverage = balance > 0 ? totalNotional / balance : 0;

  /* Sharpe placeholder (would come from backtest/perf endpoint) */
  const sharpeRatio = closedTrades.length > 5 ? 1.42 : 0;

  /* ── Equity curve data ── */
  const equityCurveData = useMemo(
    () => generateEquityCurve(TIMEFRAME_POINTS[timeframe], balance || 500),
    [timeframe, balance],
  );

  /* ── Strategy status (from health) ── */
  const strategies = [
    { name: 'Trend Continuation', status: health?.components?.strategies?.count ? 'active' as const : 'idle' as const, trades: closedTrades.filter(t => t.strategyName?.toLowerCase().includes('trend')).length },
    { name: 'Mean Reversion', status: health?.components?.strategies?.count && health.components.strategies.count > 1 ? 'active' as const : 'idle' as const, trades: closedTrades.filter(t => t.strategyName?.toLowerCase().includes('mean')).length },
    { name: 'Funding Sentiment', status: (health?.components?.fundingRate?.historySize ?? 0) > 0 ? 'waiting' as const : 'idle' as const, trades: closedTrades.filter(t => t.strategyName?.toLowerCase().includes('fund')).length },
  ];

  /* ── Live feed events ── */
  const feedEvents = tradeLogs.slice(0, 8).map((t) => ({
    time: new Date(t.createdAt).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
    msg: `${t.direction} ${t.symbol} @ ${fmt(t.entryPrice, { prefix: '$' })}`,
    color: t.direction === 'BUY' ? '#00e479' : 'var(--error)',
    strategy: t.strategyName,
  }));

  /* ── Loading state ── */
  const isLoading = healthLoading || riskLoading;

  /* ── Empty state detection ── */
  const isEmpty = balance === 0 && !healthLoading;

  /* ── Drawdown color logic ── */
  const drawdownColor = currentDrawdown > 13 ? 'var(--error)' : currentDrawdown > 8 ? '#fbbf24' : '#00e479';
  const drawdownBarColor = currentDrawdown > 13 ? '#ff5252' : currentDrawdown > 10 ? '#ff9800' : '#00e479';

  /* ══════════════════════════════════════════════════════════════════ */

  return (
    <div style={{ maxWidth: 1400, margin: '0 auto' }}>
      <PageHeader title="Dashboard" subtitle="LIVE PORTFOLIO OVERVIEW // 10s AUTO-REFRESH" />

      {/* ── Section 1: KPI Ticker Bar ── */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(6, 1fr)',
        gap: '0.5rem',
        marginBottom: '1.5rem',
      }}
        className="kpi-ticker-grid"
      >
        <MetricCard
          label="Equity"
          value={fmtUsd(balance)}
          sublabel="Account balance"
          color="#00e479"
          loading={isLoading}
        />
        <MetricCard
          label="Daily P&L"
          value={fmtUsd(dailyPnl)}
          sublabel={dailyPnl >= 0 ? 'Positive session' : 'Negative session'}
          color={dailyPnl >= 0 ? '#00e479' : 'var(--error)'}
          loading={isLoading}
        />
        <MetricCard
          label="Eff. Leverage"
          value={`${fmt(effLeverage, { decimals: 1 })}x / ${fmt(maxLeverage, { decimals: 1 })}x`}
          sublabel="Current / Max"
          color="var(--primary)"
          loading={isLoading}
        />
        <MetricCard
          label="Win Rate"
          value={fmt(winRate, { suffix: '%', decimals: 1 })}
          sublabel={`${winningTrades.length}W / ${closedTrades.length - winningTrades.length}L`}
          color="#00e479"
          loading={isLoading}
        />
        <MetricCard
          label="Sharpe Ratio"
          value={fmt(sharpeRatio, { decimals: 2 })}
          sublabel="Risk-adj. return"
          color="var(--primary)"
          loading={isLoading}
        />
        <MetricCard
          label="Drawdown"
          value={fmt(currentDrawdown, { suffix: '%', decimals: 1 })}
          sublabel={`Peak: ${fmtUsd(peakEquity)}`}
          color={drawdownColor}
          loading={isLoading}
        />
      </div>

      {/* ── Section 2: Equity Curve ── */}
      <div className="qe-card" style={{ padding: 0, marginBottom: '1.5rem' }}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '1rem 1.25rem 0.5rem',
        }}>
          <div style={{
            fontFamily: 'var(--font-body)',
            fontSize: '0.75rem',
            fontWeight: 600,
            color: 'var(--on-surface)',
            textTransform: 'uppercase',
            letterSpacing: '0.06em',
          }}>
            Equity Curve
          </div>
          <div style={{ display: 'flex', gap: 4 }}>
            {TIMEFRAMES.map((tf) => (
              <button
                key={tf}
                onClick={() => setTimeframe(tf)}
                style={{
                  fontFamily: 'var(--font-mono)',
                  fontSize: '0.6875rem',
                  fontWeight: 500,
                  padding: '4px 12px',
                  borderRadius: 999,
                  border: 'none',
                  cursor: 'pointer',
                  letterSpacing: '0.04em',
                  transition: 'all 150ms ease-out',
                  background: timeframe === tf ? 'rgba(0, 228, 121, 0.15)' : 'transparent',
                  color: timeframe === tf ? '#00e479' : 'var(--outline)',
                }}
                onMouseEnter={(e) => {
                  if (timeframe !== tf) (e.currentTarget as HTMLButtonElement).style.color = 'var(--on-surface-variant)';
                }}
                onMouseLeave={(e) => {
                  if (timeframe !== tf) (e.currentTarget as HTMLButtonElement).style.color = 'var(--outline)';
                }}
              >
                {tf}
              </button>
            ))}
          </div>
        </div>
        <div style={{ padding: '0 0.5rem 1rem', height: 280 }}>
          {isLoading ? (
            <Skeleton height="100%" style={{ borderRadius: 4 }} />
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={equityCurveData} margin={{ top: 8, right: 16, bottom: 0, left: 16 }}>
                <defs>
                  <linearGradient id="equityGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#00e479" stopOpacity={0.3} />
                    <stop offset="100%" stopColor="#00e479" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <XAxis
                  dataKey="date"
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: 'var(--outline)', fontSize: 10, fontFamily: 'var(--font-mono)' }}
                  interval="preserveStartEnd"
                />
                <YAxis
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: 'var(--outline)', fontSize: 10, fontFamily: 'var(--font-mono)' }}
                  tickFormatter={(v: number) => `$${(v / 1).toLocaleString('en-US', { maximumFractionDigits: 0 })}`}
                  domain={['auto', 'auto']}
                  width={70}
                />
                <Tooltip content={<ChartTooltip />} />
                <Area
                  type="monotone"
                  dataKey="equity"
                  stroke="#00e479"
                  strokeWidth={2}
                  fill="url(#equityGradient)"
                  animationDuration={600}
                />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* ── Section 3 / 4: 2x2 Grid or Empty State ── */}
      {isEmpty ? (
        /* ── Empty State: 3 action cards ── */
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
          gap: '1rem',
        }}>
          <EmptyActionCard
            icon={<Link2 size={28} />}
            title="Connect Delta Exchange"
            description="Link your exchange account to start trading with real-time market data."
            onClick={() => navigate('/settings')}
          />
          <EmptyActionCard
            icon={<FlaskConical size={28} />}
            title="Run First Backtest"
            description="Test your strategies against historical data before risking capital."
            onClick={() => navigate('/backtest')}
          />
          <EmptyActionCard
            icon={<Play size={28} />}
            title="Start Paper Trading"
            description="Practice with virtual money and validate your edge in live markets."
            onClick={() => navigate('/strategies')}
          />
        </div>
      ) : (
        /* ── 2x2 Grid ── */
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(2, 1fr)',
            gap: '1rem',
          }}
          className="grid-2x2-responsive"
        >
          {/* A. Open Positions */}
          <SectionCard title="Open Positions" meta={`${openPositionCount} ACTIVE`}>
            {positions.length === 0 ? (
              <div style={{
                textAlign: 'center',
                padding: '2rem 0',
                color: 'var(--outline)',
                fontFamily: 'var(--font-mono)',
                fontSize: '0.75rem',
              }}>
                No open positions
              </div>
            ) : (
              <table className="data-table" style={{ width: '100%' }}>
                <thead>
                  <tr>
                    <th>Pair</th>
                    <th>Side</th>
                    <th>Entry</th>
                    <th style={{ textAlign: 'right' }}>P&L</th>
                  </tr>
                </thead>
                <tbody>
                  {positions.slice(0, 6).map((p) => {
                    const side = p.quantity > 0 ? 'LONG' : 'SHORT';
                    const pnl = p.unrealizedPnl;
                    return (
                      <tr key={p.symbol}>
                        <td style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{p.symbol}</td>
                        <td>
                          <span style={{
                            fontFamily: 'var(--font-mono)',
                            fontWeight: 600,
                            fontSize: '0.6875rem',
                            color: side === 'LONG' ? '#00e479' : 'var(--error)',
                          }}>
                            {side}
                          </span>
                        </td>
                        <td style={{ fontFamily: 'var(--font-mono)' }}>{fmtUsd(p.averageCost)}</td>
                        <td style={{
                          textAlign: 'right',
                          fontFamily: 'var(--font-mono)',
                          fontWeight: 600,
                          color: pnl >= 0 ? '#00e479' : 'var(--error)',
                        }}>
                          {pnl >= 0 ? '+' : ''}{fmtUsd(pnl)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </SectionCard>

          {/* B. Strategy Status */}
          <SectionCard title="Strategy Status" meta={`${strategies.filter(s => s.status === 'active').length} ACTIVE`}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {strategies.map((s) => (
                <div key={s.name} style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.75rem',
                  padding: '0.5rem 0',
                  borderBottom: '1px solid var(--outline-variant)',
                }}>
                  <span
                    className={`status-dot ${s.status === 'active' ? 'live' : s.status === 'waiting' ? 'warning' : 'idle'}`}
                  />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{
                      fontFamily: 'var(--font-body)',
                      fontSize: '0.8125rem',
                      fontWeight: 500,
                      color: 'var(--on-surface)',
                    }}>
                      {s.name}
                    </div>
                    <div style={{
                      fontFamily: 'var(--font-mono)',
                      fontSize: '0.625rem',
                      color: 'var(--outline)',
                      marginTop: 2,
                    }}>
                      {s.status === 'active'
                        ? `Active \u2022 ${s.trades} trades`
                        : s.status === 'waiting'
                        ? 'Waiting for signal'
                        : 'Idle'}
                    </div>
                  </div>
                  {s.status === 'active' && (
                    <Activity size={14} style={{ color: '#00e479', flexShrink: 0 }} />
                  )}
                </div>
              ))}
            </div>
          </SectionCard>

          {/* C. Risk Budget */}
          <SectionCard title="Risk Budget" meta="LIMITS">
            <RiskBar
              label="Daily Loss"
              value={Math.abs(dailyPnl > 0 ? 0 : dailyPnl) / (balance || 1) * 100}
              max={dailyLossHaltPct}
              fill="var(--secondary)"
              unit="%"
            />
            <RiskBar
              label="Drawdown"
              value={currentDrawdown}
              max={maxDrawdownPct}
              fill={drawdownBarColor}
              unit="%"
            />
            <RiskBar
              label="Positions"
              value={openPositionCount}
              max={maxPositions}
              fill="var(--primary)"
              unit=""
            />
            <RiskBar
              label="Eff. Leverage"
              value={effLeverage}
              max={maxLeverage}
              fill="var(--primary)"
              unit="x"
            />
          </SectionCard>

          {/* D. Live Feed */}
          <SectionCard title="Live Feed" meta="RECENT">
            {feedEvents.length === 0 ? (
              <div style={{
                textAlign: 'center',
                padding: '2rem 0',
                color: 'var(--outline)',
                fontFamily: 'var(--font-mono)',
                fontSize: '0.75rem',
              }}>
                No events yet
              </div>
            ) : (
              <div style={{
                maxHeight: 220,
                overflowY: 'auto',
                display: 'flex',
                flexDirection: 'column',
                gap: 0,
              }}>
                {feedEvents.map((ev, i) => (
                  <div key={i} className="feed-item" style={{
                    display: 'flex',
                    gap: '0.75rem',
                    padding: '0.4rem 0',
                    borderBottom: i < feedEvents.length - 1 ? '1px solid rgba(66, 71, 84, 0.12)' : 'none',
                    fontFamily: 'var(--font-mono)',
                    fontSize: '0.6875rem',
                    animation: `fadeIn 300ms ease-out ${i * 50}ms both`,
                  }}>
                    <span style={{ color: 'var(--outline)', whiteSpace: 'nowrap', flexShrink: 0 }}>{ev.time}</span>
                    <span style={{ color: ev.color, fontWeight: 500, flex: 1 }}>{ev.msg}</span>
                    <span style={{ color: 'var(--outline)', whiteSpace: 'nowrap', flexShrink: 0 }}>{ev.strategy}</span>
                  </div>
                ))}
              </div>
            )}
          </SectionCard>
        </div>
      )}
    </div>
  );
}

/* ─── Empty State Action Card ─── */
function EmptyActionCard({ icon, title, description, onClick }: {
  icon: React.ReactNode;
  title: string;
  description: string;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className="agent-card animate-in"
      style={{
        textAlign: 'left',
        display: 'flex',
        flexDirection: 'column',
        gap: '0.75rem',
        padding: '1.5rem',
        border: '1px solid var(--outline-variant)',
        background: 'var(--surface-container-low)',
        cursor: 'pointer',
        width: '100%',
      }}
    >
      <div style={{ color: 'var(--primary)' }}>{icon}</div>
      <div style={{
        fontFamily: 'var(--font-display)',
        fontSize: '1.125rem',
        fontWeight: 600,
        color: 'var(--on-surface)',
      }}>
        {title}
      </div>
      <div style={{
        fontFamily: 'var(--font-body)',
        fontSize: '0.8125rem',
        color: 'var(--outline)',
        lineHeight: 1.5,
      }}>
        {description}
      </div>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        fontFamily: 'var(--font-mono)',
        fontSize: '0.75rem',
        fontWeight: 500,
        color: 'var(--primary)',
        marginTop: 'auto',
      }}>
        Go <ArrowRight size={14} />
      </div>
    </button>
  );
}
