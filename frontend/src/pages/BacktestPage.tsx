import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Activity, Loader2 } from 'lucide-react'
import { useMemo, useState } from 'react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api } from '@/services/api'
import { PageHeader } from '@/components/ui/PageHeader'
import type { BacktestResult } from '@/types'

function pct(n: number) {
  return `${(n * 100).toFixed(2)}%`
}

function fmtMoney(n: number) {
  return n.toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  })
}

export function BacktestPage() {
  const queryClient = useQueryClient()
  const [strategyId, setStrategyId] = useState<number | ''>('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [initialCapital, setInitialCapital] = useState('100000')
  const [lastResult, setLastResult] = useState<BacktestResult | null>(null)
  const [walkForwardResult, setWalkForwardResult] = useState<Record<
    string,
    unknown
  > | null>(null)

  const { data: strategies = [], isLoading: strategiesLoading } = useQuery({
    queryKey: ['strategies'],
    queryFn: () => api.getStrategies(),
  })

  const sid = typeof strategyId === 'number' ? strategyId : null

  const { data: history = [], isLoading: historyLoading } = useQuery({
    queryKey: ['backtests', sid],
    queryFn: () => api.getBacktests(sid!),
    enabled: sid !== null,
  })

  const sortedHistory = useMemo(
    () =>
      [...history].sort(
        (a, b) =>
          new Date(b.endDate).getTime() - new Date(a.endDate).getTime(),
      ),
    [history],
  )

  const backtestMutation = useMutation({
    mutationFn: () =>
      api.runBacktest(
        sid!,
        startDate,
        endDate,
        Number(initialCapital) || 0,
      ),
    onSuccess: data => {
      setLastResult(data)
      if (sid !== null)
        queryClient.invalidateQueries({ queryKey: ['backtests', sid] })
    },
  })

  const walkForwardMutation = useMutation({
    mutationFn: () => api.runWalkForward(sid!),
    onSuccess: data => {
      setWalkForwardResult(data)
    },
  })

  const displayResult = lastResult
  const chartData = displayResult?.equityCurve ?? []

  const canSubmit =
    sid !== null &&
    startDate &&
    endDate &&
    !backtestMutation.isPending &&
    !walkForwardMutation.isPending

  return (
    <div style={{ color: 'var(--on-surface)' }}>
      <div className="mx-auto max-w-6xl">
        <PageHeader title="Backtest" subtitle="STRATEGY SIMULATION // WALK-FORWARD ANALYSIS" />

        <section className="mb-8 p-6" style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface-container-low)' }}>
          <h2 className="mb-4 text-sm font-medium uppercase tracking-wider" style={{ color: 'var(--outline)' }}>
            Configuration
          </h2>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <label className="flex flex-col gap-1.5 text-sm">
              <span style={{ color: 'var(--on-surface-variant)' }}>Strategy</span>
              <select
                value={strategyId === '' ? '' : String(strategyId)}
                onChange={e => {
                  const v = e.target.value
                  setStrategyId(v === '' ? '' : Number(v))
                  setLastResult(null)
                  setWalkForwardResult(null)
                }}
                disabled={strategiesLoading}
                className="px-3 py-2 font-mono outline-none disabled:opacity-50"
                style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface)', color: 'var(--on-surface)' }}
              >
                <option value="">
                  {strategiesLoading ? 'Loading...' : 'Select strategy'}
                </option>
                {strategies.map(s => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({s.symbol})
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-1.5 text-sm">
              <span style={{ color: 'var(--on-surface-variant)' }}>Start date</span>
              <input
                type="date"
                value={startDate}
                onChange={e => setStartDate(e.target.value)}
                className="px-3 py-2 font-mono outline-none"
                style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface)', color: 'var(--on-surface)' }}
              />
            </label>
            <label className="flex flex-col gap-1.5 text-sm">
              <span style={{ color: 'var(--on-surface-variant)' }}>End date</span>
              <input
                type="date"
                value={endDate}
                onChange={e => setEndDate(e.target.value)}
                className="px-3 py-2 font-mono outline-none"
                style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface)', color: 'var(--on-surface)' }}
              />
            </label>
            <label className="flex flex-col gap-1.5 text-sm">
              <span style={{ color: 'var(--on-surface-variant)' }}>Initial capital</span>
              <input
                type="number"
                min={0}
                step={1000}
                value={initialCapital}
                onChange={e => setInitialCapital(e.target.value)}
                className="px-3 py-2 font-mono outline-none"
                style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface)', color: 'var(--on-surface)' }}
              />
            </label>
          </div>
          <div className="mt-4 flex flex-wrap gap-3">
            <button
              type="button"
              disabled={!canSubmit}
              onClick={() => backtestMutation.mutate()}
              className="inline-flex items-center gap-2 bg-gradient-to-r from-[#00ff88] to-[#3b82f6] px-4 py-2 text-sm font-semibold disabled:opacity-50"
              style={{ color: 'var(--surface)' }}
            >
              {backtestMutation.isPending && (
                <Loader2 className="h-4 w-4 animate-spin" />
              )}
              Run Backtest
            </button>
            <button
              type="button"
              disabled={
                sid === null ||
                walkForwardMutation.isPending ||
                backtestMutation.isPending
              }
              onClick={() => walkForwardMutation.mutate()}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium disabled:opacity-50"
              style={{ border: '1px solid rgba(34,211,238,0.6)', color: '#22d3ee', background: 'transparent' }}
            >
              {walkForwardMutation.isPending && (
                <Loader2 className="h-4 w-4 animate-spin" />
              )}
              Walk-Forward
            </button>
          </div>
          {(backtestMutation.isError || walkForwardMutation.isError) && (
            <p className="mt-3 text-sm" style={{ color: 'var(--error)' }}>
              {(backtestMutation.error as Error)?.message ||
                (walkForwardMutation.error as Error)?.message}
            </p>
          )}
        </section>

        {walkForwardResult && (
          <section className="mb-8 p-4" style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface-container-low)' }}>
            <h3 className="mb-2 text-sm font-medium" style={{ color: 'var(--on-surface-variant)' }}>
              Walk-forward output
            </h3>
            <pre className="max-h-64 overflow-auto p-3 font-mono text-xs" style={{ background: 'var(--surface)', color: 'var(--on-surface-variant)' }}>
              {JSON.stringify(walkForwardResult, null, 2)}
            </pre>
          </section>
        )}

        {displayResult && (
          <>
            <section className="mb-6">
              <h2 className="mb-4 flex items-center gap-2 text-lg font-medium" style={{ color: 'var(--on-surface)' }}>
                <Activity className="h-5 w-5" style={{ color: 'var(--tertiary)' }} />
                Results
              </h2>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {[
                  {
                    label: 'Total Return',
                    value: pct(displayResult.totalReturn),
                  },
                  {
                    label: 'Sharpe Ratio',
                    value: displayResult.sharpeRatio.toFixed(3),
                  },
                  {
                    label: 'Max Drawdown',
                    value: pct(displayResult.maxDrawdown),
                  },
                  {
                    label: 'Win Rate',
                    value: pct(displayResult.winRate),
                  },
                  {
                    label: 'Total Trades',
                    value: String(displayResult.totalTrades),
                  },
                  {
                    label: 'Final Capital',
                    value: fmtMoney(displayResult.finalCapital),
                  },
                ].map(m => (
                  <div
                    key={m.label}
                    className="p-4"
                    style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface-container-low)' }}
                  >
                    <p className="text-xs font-medium uppercase tracking-wider" style={{ color: 'var(--outline)' }}>
                      {m.label}
                    </p>
                    <p className="mt-2 font-mono text-lg" style={{ color: 'var(--on-surface)' }}>
                      {m.value}
                    </p>
                  </div>
                ))}
              </div>
            </section>

            <section className="mb-10 p-4" style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface)' }}>
              <h3 className="mb-4 text-sm font-medium" style={{ color: 'var(--on-surface-variant)' }}>
                Equity curve
              </h3>
              <div className="h-[320px] w-full">
                {chartData.length === 0 ? (
                  <p className="py-12 text-center" style={{ color: 'var(--outline)' }}>
                    No equity curve points returned.
                  </p>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart
                      data={chartData}
                      margin={{ top: 8, right: 8, left: 0, bottom: 0 }}
                    >
                      <defs>
                        <linearGradient
                          id="equityGreen"
                          x1="0"
                          y1="0"
                          x2="0"
                          y2="1"
                        >
                          <stop
                            offset="5%"
                            stopColor="#00ff88"
                            stopOpacity={0.35}
                          />
                          <stop
                            offset="95%"
                            stopColor="#00ff88"
                            stopOpacity={0}
                          />
                        </linearGradient>
                      </defs>
                      <CartesianGrid
                        strokeDasharray="3 3"
                        stroke="var(--outline-variant)"
                        vertical={false}
                      />
                      <XAxis
                        dataKey="date"
                        tick={{ fill: '#64748b', fontSize: 11 }}
                        tickLine={false}
                        axisLine={{ stroke: 'var(--outline-variant)' }}
                      />
                      <YAxis
                        tick={{ fill: '#64748b', fontSize: 11 }}
                        tickLine={false}
                        axisLine={{ stroke: 'var(--outline-variant)' }}
                        tickFormatter={v => fmtMoney(v)}
                        width={72}
                      />
                      <Tooltip
                        contentStyle={{
                          background: 'var(--surface-container-low)',
                          border: '1px solid var(--outline-variant)',
                          borderRadius: 8,
                          fontFamily: 'var(--font-mono)',
                          fontSize: 12,
                          color: 'var(--on-surface)',
                        }}
                        labelStyle={{ color: 'var(--on-surface-variant)' }}
                        formatter={value => {
                          const n =
                            typeof value === 'number'
                              ? value
                              : Number(value)
                          return [
                            fmtMoney(Number.isFinite(n) ? n : 0),
                            'Value',
                          ]
                        }}
                      />
                      <Area
                        type="monotone"
                        dataKey="value"
                        stroke="#00ff88"
                        strokeWidth={2}
                        fill="url(#equityGreen)"
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                )}
              </div>
            </section>
          </>
        )}

        <section>
          <h2 className="mb-4 text-lg font-medium" style={{ color: 'var(--on-surface)' }}>
            Historical backtests
          </h2>
          {sid === null ? (
            <p style={{ color: 'var(--outline)' }}>Select a strategy to load history.</p>
          ) : historyLoading ? (
            <div className="flex justify-center py-8">
              <Loader2 className="h-6 w-6 animate-spin" style={{ color: 'var(--tertiary)' }} />
            </div>
          ) : sortedHistory.length === 0 ? (
            <p style={{ color: 'var(--outline)' }}>No backtests recorded for this strategy.</p>
          ) : (
            <ul className="space-y-2">
              {sortedHistory.map(h => (
                <li
                  key={h.id}
                  className="px-4 py-3 font-mono text-sm"
                  style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface-container-low)', color: 'var(--on-surface-variant)' }}
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span style={{ color: 'var(--on-surface)' }}>
                      {h.startDate} → {h.endDate}
                    </span>
                    <span style={{ color: 'var(--tertiary)' }}>
                      Return {pct(h.totalReturn)}
                    </span>
                  </div>
                  <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs" style={{ color: 'var(--outline)' }}>
                    <span>Sharpe {h.sharpeRatio.toFixed(2)}</span>
                    <span>Max DD {pct(h.maxDrawdown)}</span>
                    <span>Trades {h.totalTrades}</span>
                    <span>Final {fmtMoney(h.finalCapital)}</span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}

export default BacktestPage
