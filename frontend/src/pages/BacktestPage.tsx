import { useMutation } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { useState } from 'react'
import {
  Area,
  AreaChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api } from '@/services/api'
import { PageHeader } from '@/components/ui/PageHeader'
import { useNotificationStore } from '@/stores/notificationStore'
import type { MultiTFBacktestResult } from '@/types'

/* ── helpers ─────────────────────────────────────────────────────── */

function fmtPct(n: number) {
  return `${n >= 0 ? '+' : ''}${n.toFixed(2)}%`
}

function fmtMoney(n: number) {
  return n.toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  })
}

function fmtNum(n: number, decimals = 2) {
  return n.toFixed(decimals)
}

/* ── strategy colors ─────────────────────────────────────────────── */

const STRATEGY_META: Record<string, { label: string; color: string }> = {
  trend_continuation: { label: 'Trend Continuation', color: '#00e479' },
  mean_reversion: { label: 'Mean Reversion', color: '#3b82f6' },
  funding_sentiment: { label: 'Funding Sentiment', color: '#f59e0b' },
}

function resolveStrategy(key: string) {
  const lower = key.toLowerCase().replace(/[\s-]+/g, '_')
  return STRATEGY_META[lower] ?? { label: key, color: '#94a3b8' }
}

/* ── shared inline style fragments ───────────────────────────────── */

const inputStyle: React.CSSProperties = {
  border: '1px solid var(--outline-variant)',
  background: 'var(--surface)',
  color: 'var(--on-surface)',
  padding: '8px 12px',
  fontFamily: 'var(--font-mono, monospace)',
  fontSize: 13,
  outline: 'none',
  width: '100%',
}

const cardBg: React.CSSProperties = {
  background: 'var(--surface-container-low)',
  border: '1px solid var(--outline-variant)',
}

/* ════════════════════════════════════════════════════════════════════
   BacktestPage
   ════════════════════════════════════════════════════════════════════ */

export function BacktestPage() {
  const addNotification = useNotificationStore(s => s.addNotification)

  /* ── form state ────────────────────────────────────────────────── */
  const [symbol, setSymbol] = useState('BTCUSD')
  const [initialCapital, setInitialCapital] = useState(500)
  const [slippageBps, setSlippageBps] = useState(10)
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')

  /* ── result state ──────────────────────────────────────────────── */
  const [result, setResult] = useState<MultiTFBacktestResult | null>(null)

  /* ── trade log expansion ───────────────────────────────────────── */
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set())

  function toggleRow(idx: number) {
    setExpandedRows(prev => {
      const next = new Set(prev)
      if (next.has(idx)) next.delete(idx)
      else next.add(idx)
      return next
    })
  }

  /* ── mutation ──────────────────────────────────────────────────── */
  const mutation = useMutation({
    mutationFn: () =>
      api.runMultiTFBacktest({ initialCapital, slippageBps }),
    onSuccess: data => {
      if (!data || (typeof data === 'object' && Object.keys(data).length === 0)) {
        addNotification({
          type: 'info',
          title: 'Backtest Engine Ready',
          message:
            'Connect to Delta Exchange to load historical data.',
        })
        setResult(null)
        return
      }
      setResult(data)
    },
    onError: (err: Error) => {
      addNotification({
        type: 'error',
        title: 'Backtest Failed',
        message: err.message || 'Unknown error',
      })
    },
  })

  /* ── equity curve data for Recharts ────────────────────────────── */
  const equityChartData = (result?.equityCurve ?? []).map((v, i) => ({
    idx: i,
    value: v,
  }))

  /* ── per-strategy entries ──────────────────────────────────────── */
  const strategyEntries = result?.perStrategyWinRate
    ? Object.entries(result.perStrategyWinRate)
    : []

  /* ── KPI definitions ───────────────────────────────────────────── */
  const kpis = result
    ? [
        {
          label: 'Return %',
          value: fmtPct(result.totalReturnPct),
          color: result.totalReturnPct >= 0 ? '#00e479' : '#ffb4ab',
        },
        { label: 'Sharpe Ratio', value: fmtNum(result.sharpeRatio), color: '#60a5fa' },
        { label: 'Max Drawdown %', value: fmtPct(-Math.abs(result.maxDrawdownPct)), color: '#ffb4ab' },
        {
          label: 'Win Rate %',
          value: `${fmtNum(result.winRate * 100)}%`,
          color: '#00e479',
        },
        { label: 'Profit Factor', value: fmtNum(result.profitFactor), color: '#c4b5fd' },
        { label: 'Total Trades', value: String(result.totalTrades), color: '#e2e8f0' },
        { label: 'Total Fees', value: fmtMoney(result.totalFees), color: '#fbbf24' },
        { label: 'Funding Paid', value: fmtMoney(result.totalFundingPaid), color: '#fb923c' },
      ]
    : []

  /* ════════════════════════════════════════════════════════════════
     Render
     ════════════════════════════════════════════════════════════════ */

  return (
    <div style={{ color: 'var(--on-surface)' }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <PageHeader
          title="Backtest"
          subtitle="MULTI-TIMEFRAME ENGINE // 3-STRATEGY SIMULATION"
        />

        {/* ── Config Bar ─────────────────────────────────────── */}
        <section
          style={{
            ...cardBg,
            padding: 24,
            marginBottom: 32,
          }}
        >
          <h2
            style={{
              fontSize: 12,
              fontWeight: 500,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: 'var(--outline)',
              marginBottom: 16,
            }}
          >
            Configuration
          </h2>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
              gap: 16,
            }}
          >
            {/* Symbol */}
            <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <span style={{ fontSize: 12, color: 'var(--on-surface-variant)' }}>
                Symbol
              </span>
              <select
                value={symbol}
                onChange={e => setSymbol(e.target.value)}
                style={{ ...inputStyle, cursor: 'pointer' }}
              >
                <option value="BTCUSD">BTCUSD</option>
                <option value="ETHUSD">ETHUSD</option>
              </select>
            </label>

            {/* Initial Capital */}
            <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <span style={{ fontSize: 12, color: 'var(--on-surface-variant)' }}>
                Initial Capital ($)
              </span>
              <input
                type="number"
                min={0}
                step={100}
                value={initialCapital}
                onChange={e => setInitialCapital(Number(e.target.value))}
                style={inputStyle}
              />
            </label>

            {/* Slippage */}
            <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <span style={{ fontSize: 12, color: 'var(--on-surface-variant)' }}>
                Slippage (bps)
              </span>
              <input
                type="number"
                min={0}
                step={1}
                value={slippageBps}
                onChange={e => setSlippageBps(Number(e.target.value))}
                style={inputStyle}
              />
            </label>

            {/* Start Date */}
            <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <span style={{ fontSize: 12, color: 'var(--on-surface-variant)' }}>
                Start Date
              </span>
              <input
                type="date"
                value={startDate}
                onChange={e => setStartDate(e.target.value)}
                style={inputStyle}
              />
            </label>

            {/* End Date */}
            <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <span style={{ fontSize: 12, color: 'var(--on-surface-variant)' }}>
                End Date
              </span>
              <input
                type="date"
                value={endDate}
                onChange={e => setEndDate(e.target.value)}
                style={inputStyle}
              />
            </label>
          </div>

          {/* Run button */}
          <div style={{ marginTop: 20, display: 'flex', alignItems: 'center', gap: 12 }}>
            <button
              type="button"
              disabled={mutation.isPending}
              onClick={() => mutation.mutate()}
              style={{
                background: 'linear-gradient(135deg, #00e479, #3b82f6)',
                color: '#0a0a0f',
                border: 'none',
                padding: '10px 28px',
                fontWeight: 600,
                fontSize: 13,
                cursor: mutation.isPending ? 'wait' : 'pointer',
                opacity: mutation.isPending ? 0.7 : 1,
                display: 'inline-flex',
                alignItems: 'center',
                gap: 8,
                letterSpacing: '0.02em',
              }}
            >
              {mutation.isPending && (
                <Loader2
                  style={{
                    width: 16,
                    height: 16,
                    animation: 'spin 1s linear infinite',
                  }}
                />
              )}
              Run Backtest
            </button>

            {mutation.isPending && (
              <span
                style={{
                  fontSize: 12,
                  color: 'var(--on-surface-variant)',
                  fontFamily: 'var(--font-mono, monospace)',
                }}
              >
                Simulating across 3 strategies...
              </span>
            )}
          </div>

          {mutation.isError && (
            <p style={{ marginTop: 12, fontSize: 13, color: 'var(--error)' }}>
              {(mutation.error as Error)?.message}
            </p>
          )}
        </section>

        {/* ── Initial State (before any run) ─────────────────── */}
        {!result && !mutation.isPending && (
          <div
            style={{
              ...cardBg,
              padding: '64px 32px',
              textAlign: 'center',
              marginBottom: 32,
            }}
          >
            <p
              style={{
                color: 'var(--on-surface-variant)',
                fontSize: 14,
                maxWidth: 560,
                margin: '0 auto',
                lineHeight: 1.7,
              }}
            >
              Configure your backtest parameters and hit Run. The
              multi-timeframe engine will simulate all 3 strategies with
              realistic fees, slippage, and funding costs.
            </p>
          </div>
        )}

        {/* ── Loading indicator ──────────────────────────────── */}
        {mutation.isPending && (
          <div
            style={{
              ...cardBg,
              padding: '48px 32px',
              textAlign: 'center',
              marginBottom: 32,
            }}
          >
            <Loader2
              style={{
                width: 32,
                height: 32,
                color: '#00e479',
                margin: '0 auto 16px',
                animation: 'spin 1s linear infinite',
              }}
            />
            <p style={{ color: 'var(--on-surface-variant)', fontSize: 13 }}>
              Running multi-timeframe backtest...
            </p>
          </div>
        )}

        {/* ── Results ────────────────────────────────────────── */}
        {result && (
          <>
            {/* 8-KPI Row */}
            <section style={{ marginBottom: 32 }}>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
                  gap: 12,
                }}
              >
                {kpis.map(kpi => (
                  <div
                    key={kpi.label}
                    style={{
                      ...cardBg,
                      padding: '16px 20px',
                    }}
                  >
                    <p
                      style={{
                        fontSize: 11,
                        fontWeight: 500,
                        textTransform: 'uppercase',
                        letterSpacing: '0.06em',
                        color: 'var(--outline)',
                        marginBottom: 8,
                      }}
                    >
                      {kpi.label}
                    </p>
                    <p
                      style={{
                        fontFamily: 'var(--font-mono, monospace)',
                        fontSize: 22,
                        fontWeight: 600,
                        color: kpi.color,
                      }}
                    >
                      {kpi.value}
                    </p>
                  </div>
                ))}
              </div>
            </section>

            {/* Equity Curve */}
            <section
              style={{
                ...cardBg,
                padding: 24,
                marginBottom: 32,
              }}
            >
              <h3
                style={{
                  fontSize: 12,
                  fontWeight: 500,
                  letterSpacing: '0.06em',
                  textTransform: 'uppercase',
                  color: 'var(--on-surface-variant)',
                  marginBottom: 16,
                }}
              >
                Equity Curve
              </h3>
              <div style={{ width: '100%', height: 340 }}>
                {equityChartData.length === 0 ? (
                  <p
                    style={{
                      textAlign: 'center',
                      paddingTop: 64,
                      color: 'var(--outline)',
                    }}
                  >
                    No equity curve data returned.
                  </p>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart
                      data={equityChartData}
                      margin={{ top: 8, right: 8, left: 0, bottom: 0 }}
                    >
                      <defs>
                        <linearGradient
                          id="eqGreen"
                          x1="0"
                          y1="0"
                          x2="0"
                          y2="1"
                        >
                          <stop
                            offset="5%"
                            stopColor="#00e479"
                            stopOpacity={0.3}
                          />
                          <stop
                            offset="95%"
                            stopColor="#00e479"
                            stopOpacity={0}
                          />
                        </linearGradient>
                      </defs>
                      <XAxis
                        dataKey="idx"
                        tick={{ fill: '#64748b', fontSize: 11 }}
                        tickLine={false}
                        axisLine={{ stroke: 'var(--outline-variant)' }}
                      />
                      <YAxis
                        tick={{ fill: '#64748b', fontSize: 11 }}
                        tickLine={false}
                        axisLine={{ stroke: 'var(--outline-variant)' }}
                        tickFormatter={v => fmtMoney(v)}
                        width={80}
                      />
                      <Tooltip
                        contentStyle={{
                          background: 'var(--surface-container-low)',
                          border: '1px solid var(--outline-variant)',
                          fontFamily: 'var(--font-mono, monospace)',
                          fontSize: 12,
                          color: 'var(--on-surface)',
                        }}
                        labelFormatter={l => `Bar ${l}`}
                        formatter={(value: any) => [fmtMoney(Number(value)), 'Equity']}
                      />
                      <Area
                        type="monotone"
                        dataKey="value"
                        stroke="#00e479"
                        strokeWidth={2}
                        fill="url(#eqGreen)"
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                )}
              </div>
            </section>

            {/* Per-Strategy Breakdown */}
            {strategyEntries.length > 0 && (
              <section style={{ marginBottom: 32 }}>
                <h3
                  style={{
                    fontSize: 12,
                    fontWeight: 500,
                    letterSpacing: '0.06em',
                    textTransform: 'uppercase',
                    color: 'var(--on-surface-variant)',
                    marginBottom: 16,
                  }}
                >
                  Per-Strategy Breakdown
                </h3>
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
                    gap: 16,
                  }}
                >
                  {strategyEntries.map(([key, winRate]) => {
                    const meta = resolveStrategy(key)
                    return (
                      <div
                        key={key}
                        style={{
                          ...cardBg,
                          borderLeft: `3px solid ${meta.color}`,
                          padding: '20px 24px',
                        }}
                      >
                        <p
                          style={{
                            fontWeight: 600,
                            fontSize: 14,
                            color: meta.color,
                            marginBottom: 12,
                          }}
                        >
                          {meta.label}
                        </p>
                        <div
                          style={{
                            display: 'grid',
                            gridTemplateColumns: '1fr 1fr',
                            gap: '8px 20px',
                          }}
                        >
                          <div>
                            <span
                              style={{
                                fontSize: 11,
                                color: 'var(--outline)',
                                textTransform: 'uppercase',
                              }}
                            >
                              Win Rate
                            </span>
                            <p
                              style={{
                                fontFamily: 'var(--font-mono, monospace)',
                                fontSize: 18,
                                fontWeight: 600,
                                color: 'var(--on-surface)',
                              }}
                            >
                              {fmtNum(winRate * 100)}%
                            </p>
                          </div>
                          <div>
                            <span
                              style={{
                                fontSize: 11,
                                color: 'var(--outline)',
                                textTransform: 'uppercase',
                              }}
                            >
                              Status
                            </span>
                            <p
                              style={{
                                fontFamily: 'var(--font-mono, monospace)',
                                fontSize: 14,
                                color: winRate >= 0.5 ? '#00e479' : '#ffb4ab',
                                marginTop: 2,
                              }}
                            >
                              {winRate >= 0.5 ? 'Profitable' : 'Review'}
                            </p>
                          </div>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </section>
            )}

            {/* Trade Log Table */}
            <section style={{ marginBottom: 48 }}>
              <h3
                style={{
                  fontSize: 12,
                  fontWeight: 500,
                  letterSpacing: '0.06em',
                  textTransform: 'uppercase',
                  color: 'var(--on-surface-variant)',
                  marginBottom: 16,
                }}
              >
                Trade Log
              </h3>

              {(!result.tradeLog || result.tradeLog.length === 0) ? (
                <div
                  style={{
                    ...cardBg,
                    padding: '32px 24px',
                    textAlign: 'center',
                    color: 'var(--outline)',
                    fontSize: 13,
                  }}
                >
                  No trades recorded in this backtest.
                </div>
              ) : (
                <div style={{ overflowX: 'auto' }}>
                  <table
                    style={{
                      width: '100%',
                      borderCollapse: 'collapse',
                      fontFamily: 'var(--font-mono, monospace)',
                      fontSize: 12,
                    }}
                  >
                    <thead>
                      <tr
                        style={{
                          borderBottom: '1px solid var(--outline-variant)',
                          textAlign: 'left',
                        }}
                      >
                        {['#', 'Side', 'Entry', 'Exit', 'PnL', 'Strategy'].map(
                          h => (
                            <th
                              key={h}
                              style={{
                                padding: '10px 12px',
                                fontSize: 11,
                                fontWeight: 500,
                                textTransform: 'uppercase',
                                letterSpacing: '0.05em',
                                color: 'var(--outline)',
                              }}
                            >
                              {h}
                            </th>
                          ),
                        )}
                      </tr>
                    </thead>
                    <tbody>
                      {result.tradeLog.map((trade, idx) => {
                        const side = String(trade.side ?? trade.direction ?? '-')
                        const entry = Number(trade.entryPrice ?? trade.entry ?? 0)
                        const exit = Number(trade.exitPrice ?? trade.exit ?? 0)
                        const pnl = Number(trade.pnl ?? 0)
                        const strat = String(trade.strategy ?? trade.strategyName ?? '-')
                        const isExpanded = expandedRows.has(idx)

                        return (
                          <tr
                            key={idx}
                            onClick={() => toggleRow(idx)}
                            style={{
                              borderBottom: '1px solid var(--outline-variant)',
                              cursor: 'pointer',
                              background: isExpanded
                                ? 'var(--surface-container-low)'
                                : 'transparent',
                            }}
                          >
                            <td style={{ padding: '10px 12px', color: 'var(--outline)' }}>
                              {idx + 1}
                            </td>
                            <td
                              style={{
                                padding: '10px 12px',
                                color:
                                  side.toUpperCase() === 'BUY'
                                    ? '#00e479'
                                    : '#ffb4ab',
                                fontWeight: 600,
                              }}
                            >
                              {side.toUpperCase()}
                            </td>
                            <td style={{ padding: '10px 12px', color: 'var(--on-surface)' }}>
                              {fmtMoney(entry)}
                            </td>
                            <td style={{ padding: '10px 12px', color: 'var(--on-surface)' }}>
                              {fmtMoney(exit)}
                            </td>
                            <td
                              style={{
                                padding: '10px 12px',
                                color: pnl >= 0 ? '#00e479' : '#ffb4ab',
                                fontWeight: 600,
                              }}
                            >
                              {fmtMoney(pnl)}
                            </td>
                            <td style={{ padding: '10px 12px', color: 'var(--on-surface-variant)' }}>
                              {strat}
                              {isExpanded && (
                                <div
                                  style={{
                                    marginTop: 8,
                                    fontSize: 11,
                                    color: 'var(--outline)',
                                    lineHeight: 1.6,
                                  }}
                                >
                                  {Object.entries(trade)
                                    .filter(
                                      ([k]) =>
                                        ![
                                          'side',
                                          'direction',
                                          'entryPrice',
                                          'entry',
                                          'exitPrice',
                                          'exit',
                                          'pnl',
                                          'strategy',
                                          'strategyName',
                                        ].includes(k),
                                    )
                                    .map(([k, v]) => (
                                      <div key={k}>
                                        <strong style={{ color: 'var(--on-surface-variant)' }}>
                                          {k}:
                                        </strong>{' '}
                                        {String(v)}
                                      </div>
                                    ))}
                                </div>
                              )}
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          </>
        )}
      </div>

      {/* keyframe for Loader2 spin */}
      <style>{`
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  )
}

export default BacktestPage
