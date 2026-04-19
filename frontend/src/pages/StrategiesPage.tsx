import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Check,
  ChevronDown,
  ChevronRight,
  Clock,
  Filter,
  X,
} from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { api } from '@/services/api'
import { PageHeader } from '@/components/ui/PageHeader'
import type { RiskConfig, TradeLog } from '@/types'

/* ─── Strategy Card Definitions ─── */

interface StrategyCardDef {
  id: string
  name: string
  borderColor: string
  status: 'active' | 'waiting'
  description: string
}

const STRATEGY_CARDS: StrategyCardDef[] = [
  {
    id: 'trend',
    name: 'Trend Continuation',
    borderColor: '#00e479',
    status: 'active',
    description: '4H bias \u2192 1H zones \u2192 15M entries',
  },
  {
    id: 'meanrev',
    name: 'Mean Reversion',
    borderColor: '#5de6ff',
    status: 'active',
    description: 'Bollinger/RSI extremes \u2192 VWAP targets',
  },
  {
    id: 'funding',
    name: 'Funding Sentiment',
    borderColor: '#adc6ff',
    status: 'waiting',
    description: 'Extreme funding + OI \u2192 liquidation cascades',
  },
]

/* ─── Helpers ─── */

function formatTime(iso: string): string {
  try {
    const d = new Date(iso)
    return d.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}

function riskReward(log: TradeLog): string {
  if (!log.entryPrice || !log.stopLossPrice || !log.takeProfitPrice) return '\u2014'
  const risk = Math.abs(log.entryPrice - log.stopLossPrice)
  if (risk === 0) return '\u2014'
  const reward = Math.abs(log.takeProfitPrice - log.entryPrice)
  return `1:${(reward / risk).toFixed(1)}`
}

function statusIcon(status: TradeLog['status']) {
  switch (status) {
    case 'CLOSED':
      return <Check style={{ width: 14, height: 14, color: 'var(--tertiary)' }} />
    case 'CANCELLED':
      return <X style={{ width: 14, height: 14, color: 'var(--error)' }} />
    default:
      return <Clock style={{ width: 14, height: 14, color: '#fbbf24' }} />
  }
}

function statusLabel(status: TradeLog['status']): string {
  switch (status) {
    case 'PENDING': return 'Pending'
    case 'OPEN': return 'Open'
    case 'CLOSED': return 'Executed'
    case 'CANCELLED': return 'Rejected'
    default: return status
  }
}

function strategyFilterKey(name: string): string {
  const lower = name.toLowerCase()
  if (lower.includes('trend')) return 'Trend'
  if (lower.includes('mean') || lower.includes('reversion')) return 'MeanRev'
  if (lower.includes('funding')) return 'Funding'
  return 'Other'
}

/* ─── Component ─── */

export function StrategiesPage() {
  const queryClient = useQueryClient()
  const [expandedRowId, setExpandedRowId] = useState<string | null>(null)
  const [strategyFilter, setStrategyFilter] = useState('All')
  const [statusFilter, setStatusFilter] = useState('All')

  // Fetch risk config (execution mode, etc.)
  const { data: riskConfig } = useQuery<RiskConfig>({
    queryKey: ['riskConfig'],
    queryFn: () => api.getRiskConfig(),
    refetchInterval: 10_000,
  })

  // Fetch trade logs (signals)
  const { data: tradeLogs = [] } = useQuery<TradeLog[]>({
    queryKey: ['tradeLogs'],
    queryFn: () => api.getTradeLogs(),
    refetchInterval: 10_000,
  })

  // Execution mode mutation
  const modeMutation = useMutation({
    mutationFn: (mode: RiskConfig['executionMode']) =>
      api.updateRiskConfig({ executionMode: mode }),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['riskConfig'] })
      toast.success(`Execution mode set to ${data.executionMode.replace('_', ' ')}`)
    },
    onError: (err: Error) => {
      toast.error(`Failed to update execution mode: ${err.message}`)
    },
  })

  const currentMode = riskConfig?.executionMode ?? 'HUMAN_IN_LOOP'

  // Filter signals
  const filteredLogs = tradeLogs.filter((log) => {
    if (strategyFilter !== 'All' && strategyFilterKey(log.strategyName) !== strategyFilter) return false
    if (statusFilter === 'Executed' && log.status !== 'CLOSED') return false
    if (statusFilter === 'Rejected' && log.status !== 'CANCELLED') return false
    if (statusFilter === 'Expired' && log.status !== 'PENDING') return false
    return true
  })

  // Derive per-strategy stats from tradeLogs
  function getStrategyStats(stratKey: string) {
    const logs = tradeLogs.filter((l) => strategyFilterKey(l.strategyName) === stratKey)
    const closedLogs = logs.filter((l) => l.status === 'CLOSED' && l.outcome)
    const wins = closedLogs.filter((l) => l.outcome?.result === 'WIN').length
    const winRate = closedLogs.length > 0 ? ((wins / closedLogs.length) * 100).toFixed(0) + '%' : '\u2014'
    const lastSignal = logs.length > 0 ? formatTime(logs[logs.length - 1].createdAt) : 'None'
    const avgConfidence = logs.length > 0
      ? logs.reduce((sum, l) => sum + (l.confidence ?? 0), 0) / logs.length
      : 0
    return { signals: logs.length, winRate, lastSignal, avgConfidence }
  }

  return (
    <div style={{ color: 'var(--on-surface)' }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        {/* ── Top Bar ── */}
        <PageHeader title="Strategies" subtitle="MULTI-TIMEFRAME // SIGNAL GENERATION">
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            {/* Execution mode toggle */}
            <div style={{ display: 'flex', borderRadius: 6, overflow: 'hidden', border: '1px solid var(--outline-variant)' }}>
              {(['AUTONOMOUS', 'HUMAN_IN_LOOP'] as const).map((mode) => {
                const active = currentMode === mode
                const label = mode === 'HUMAN_IN_LOOP' ? 'HUMAN IN LOOP' : 'AUTONOMOUS'
                return (
                  <button
                    key={mode}
                    type="button"
                    onClick={() => {
                      if (!active) modeMutation.mutate(mode)
                    }}
                    disabled={modeMutation.isPending}
                    style={{
                      padding: '6px 14px',
                      fontSize: 11,
                      fontFamily: 'var(--font-mono)',
                      fontWeight: 600,
                      letterSpacing: '0.05em',
                      border: 'none',
                      cursor: active ? 'default' : 'pointer',
                      background: active ? 'var(--primary)' : 'var(--surface-container-low)',
                      color: active ? 'var(--on-primary)' : 'var(--on-surface-variant)',
                      transition: 'background var(--ease-normal), color var(--ease-normal)',
                      opacity: modeMutation.isPending ? 0.6 : 1,
                    }}
                  >
                    {label}
                  </button>
                )
              })}
            </div>

            {/* Status indicator */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, fontFamily: 'var(--font-mono)', color: 'var(--on-surface-variant)' }}>
              <span
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  background: riskConfig ? '#00e479' : '#fbbf24',
                  display: 'inline-block',
                  boxShadow: riskConfig ? '0 0 6px rgba(0,228,121,0.5)' : '0 0 6px rgba(251,191,36,0.5)',
                }}
              />
              {riskConfig
                ? `Running since ${formatTime(riskConfig.updatedAt)}`
                : 'Paused'}
            </div>
          </div>
        </PageHeader>

        {/* ── Strategy Cards Grid ── */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
            gap: 16,
            marginBottom: 32,
          }}
        >
          {STRATEGY_CARDS.map((card) => {
            const stratKey = card.id === 'trend' ? 'Trend' : card.id === 'meanrev' ? 'MeanRev' : 'Funding'
            const stats = getStrategyStats(stratKey)
            return (
              <div
                key={card.id}
                style={{
                  background: '#1a2235',
                  borderRadius: 8,
                  borderTop: `3px solid ${card.borderColor}`,
                  padding: 16,
                  transition: 'box-shadow var(--ease-smooth), transform var(--ease-smooth)',
                  cursor: 'default',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.boxShadow = `0 4px 24px rgba(0,0,0,0.3), 0 0 12px ${card.borderColor}33`
                  e.currentTarget.style.transform = 'translateY(-1px)'
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.boxShadow = 'none'
                  e.currentTarget.style.transform = 'none'
                }}
              >
                {/* Card header */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                  <span style={{ fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 14, color: 'var(--on-surface)' }}>
                    {card.name}
                  </span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 11, fontFamily: 'var(--font-mono)' }}>
                    <span
                      style={{
                        width: 7,
                        height: 7,
                        borderRadius: '50%',
                        background: card.status === 'active' ? '#00e479' : '#fbbf24',
                        display: 'inline-block',
                      }}
                    />
                    <span style={{ color: card.status === 'active' ? '#00e479' : '#fbbf24' }}>
                      {card.status === 'active' ? 'Active' : 'Waiting'}
                    </span>
                  </div>
                </div>

                {/* Description */}
                <p style={{ fontSize: 12, fontFamily: 'var(--font-mono)', color: 'var(--on-surface-variant)', marginBottom: 14, lineHeight: 1.5 }}>
                  {card.description}
                </p>

                {/* Stats */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8, marginBottom: 12 }}>
                  <div>
                    <div style={{ fontSize: 10, fontFamily: 'var(--font-mono)', color: 'var(--outline)', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 2 }}>
                      Signals
                    </div>
                    <div style={{ fontSize: 16, fontFamily: 'var(--font-display)', fontWeight: 700, color: 'var(--on-surface)' }}>
                      {stats.signals}
                    </div>
                  </div>
                  <div>
                    <div style={{ fontSize: 10, fontFamily: 'var(--font-mono)', color: 'var(--outline)', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 2 }}>
                      Win Rate
                    </div>
                    <div style={{ fontSize: 16, fontFamily: 'var(--font-display)', fontWeight: 700, color: 'var(--on-surface)' }}>
                      {stats.winRate}
                    </div>
                  </div>
                  <div>
                    <div style={{ fontSize: 10, fontFamily: 'var(--font-mono)', color: 'var(--outline)', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 2 }}>
                      Last Signal
                    </div>
                    <div style={{ fontSize: 12, fontFamily: 'var(--font-mono)', fontWeight: 500, color: 'var(--on-surface-variant)', marginTop: 2 }}>
                      {stats.lastSignal}
                    </div>
                  </div>
                </div>

                {/* Confidence bar */}
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                    <span style={{ fontSize: 10, fontFamily: 'var(--font-mono)', color: 'var(--outline)', textTransform: 'uppercase', letterSpacing: '0.08em' }}>
                      Confidence
                    </span>
                    <span style={{ fontSize: 11, fontFamily: 'var(--font-mono)', color: 'var(--on-surface-variant)' }}>
                      {stats.avgConfidence > 0 ? `${(stats.avgConfidence * 100).toFixed(0)}%` : '0%'}
                    </span>
                  </div>
                  <div
                    style={{
                      height: 4,
                      borderRadius: 2,
                      background: 'var(--outline-variant)',
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        height: '100%',
                        borderRadius: 2,
                        width: `${Math.min(100, Math.max(0, stats.avgConfidence * 100))}%`,
                        background: card.borderColor,
                        transition: 'width var(--ease-smooth)',
                      }}
                    />
                  </div>
                </div>
              </div>
            )
          })}
        </div>

        {/* ── Signals Table ── */}
        <div style={{ background: 'var(--surface-container-low)', borderRadius: 8, padding: 20, border: '1px solid var(--outline-variant)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12, marginBottom: 16 }}>
            <h2 style={{ fontFamily: 'var(--font-body)', fontSize: 14, fontWeight: 600, color: 'var(--on-surface)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
              Recent Signals
            </h2>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Filter style={{ width: 14, height: 14, color: 'var(--outline)' }} />
              <select
                value={strategyFilter}
                onChange={(e) => setStrategyFilter(e.target.value)}
                style={{
                  padding: '4px 8px',
                  fontSize: 11,
                  fontFamily: 'var(--font-mono)',
                  background: 'var(--surface)',
                  color: 'var(--on-surface)',
                  border: '1px solid var(--outline-variant)',
                  borderRadius: 4,
                  outline: 'none',
                }}
              >
                <option value="All">All Strategies</option>
                <option value="Trend">Trend</option>
                <option value="MeanRev">MeanRev</option>
                <option value="Funding">Funding</option>
              </select>
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                style={{
                  padding: '4px 8px',
                  fontSize: 11,
                  fontFamily: 'var(--font-mono)',
                  background: 'var(--surface)',
                  color: 'var(--on-surface)',
                  border: '1px solid var(--outline-variant)',
                  borderRadius: 4,
                  outline: 'none',
                }}
              >
                <option value="All">All Status</option>
                <option value="Executed">Executed</option>
                <option value="Rejected">Rejected</option>
                <option value="Expired">Expired</option>
              </select>
            </div>
          </div>

          {filteredLogs.length === 0 ? (
            <div
              style={{
                padding: '48px 24px',
                textAlign: 'center',
                border: '1px dashed var(--outline-variant)',
                borderRadius: 6,
                background: 'rgba(26,34,52,0.3)',
              }}
            >
              <p style={{ fontSize: 13, color: 'var(--outline)', fontFamily: 'var(--font-mono)', lineHeight: 1.6 }}>
                No signals yet. Signals will appear here once the strategies are running on Delta Exchange.
              </p>
            </div>
          ) : (
            <div style={{ overflowX: 'auto', WebkitOverflowScrolling: 'touch' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 680 }}>
                <thead>
                  <tr>
                    {['Time', 'Pair', 'Side', 'Confidence', 'R:R', 'Status'].map((col) => (
                      <th
                        key={col}
                        style={{
                          textAlign: 'left',
                          padding: '8px 10px',
                          fontSize: 10,
                          fontFamily: 'var(--font-mono)',
                          fontWeight: 600,
                          color: 'var(--outline)',
                          textTransform: 'uppercase',
                          letterSpacing: '0.1em',
                          borderBottom: '1px solid var(--outline-variant)',
                        }}
                      >
                        {col}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filteredLogs.map((log) => {
                    const isExpanded = expandedRowId === log.tradeId
                    const confidencePct = (log.confidence >= 0 && log.confidence <= 1)
                      ? log.confidence * 100
                      : log.confidence
                    return (
                      <ExpandableRow
                        key={log.tradeId}
                        log={log}
                        isExpanded={isExpanded}
                        confidencePct={confidencePct}
                        rr={riskReward(log)}
                        onToggle={() => setExpandedRowId(isExpanded ? null : log.tradeId)}
                      />
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

/* ─── Expandable Row Sub-component ─── */

function ExpandableRow({
  log,
  isExpanded,
  confidencePct,
  rr,
  onToggle,
}: {
  log: TradeLog
  isExpanded: boolean
  confidencePct: number
  rr: string
  onToggle: () => void
}) {
  const isLong = log.direction === 'BUY'

  return (
    <>
      <tr
        onClick={onToggle}
        style={{
          cursor: 'pointer',
          borderBottom: isExpanded ? 'none' : '1px solid var(--outline-variant)',
          transition: 'background var(--ease-fast)',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.background = 'rgba(173,198,255,0.04)'
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.background = 'transparent'
        }}
      >
        <td style={{ padding: '10px', fontSize: 12, fontFamily: 'var(--font-mono)', color: 'var(--on-surface-variant)', whiteSpace: 'nowrap' }}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            {isExpanded
              ? <ChevronDown style={{ width: 13, height: 13, color: 'var(--outline)' }} />
              : <ChevronRight style={{ width: 13, height: 13, color: 'var(--outline)' }} />
            }
            {formatTime(log.createdAt)}
          </span>
        </td>
        <td style={{ padding: '10px', fontSize: 12, fontFamily: 'var(--font-mono)', fontWeight: 600, color: 'var(--on-surface)' }}>
          {log.symbol}
        </td>
        <td style={{ padding: '10px' }}>
          <span
            style={{
              display: 'inline-block',
              padding: '2px 8px',
              borderRadius: 4,
              fontSize: 11,
              fontFamily: 'var(--font-mono)',
              fontWeight: 600,
              background: isLong ? 'rgba(0,228,121,0.15)' : 'rgba(239,68,68,0.15)',
              color: isLong ? 'var(--tertiary)' : 'var(--error)',
              border: `1px solid ${isLong ? 'rgba(0,228,121,0.3)' : 'rgba(239,68,68,0.3)'}`,
            }}
          >
            {isLong ? 'LONG' : 'SHORT'}
          </span>
        </td>
        <td style={{ padding: '10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ width: 48, height: 4, borderRadius: 2, background: 'var(--outline-variant)', overflow: 'hidden' }}>
              <div
                style={{
                  height: '100%',
                  borderRadius: 2,
                  width: `${Math.min(100, Math.max(0, confidencePct))}%`,
                  background: 'var(--primary)',
                }}
              />
            </div>
            <span style={{ fontSize: 11, fontFamily: 'var(--font-mono)', color: 'var(--on-surface-variant)' }}>
              {confidencePct.toFixed(0)}%
            </span>
          </div>
        </td>
        <td style={{ padding: '10px', fontSize: 12, fontFamily: 'var(--font-mono)', color: 'var(--on-surface-variant)' }}>
          {rr}
        </td>
        <td style={{ padding: '10px' }}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 11, fontFamily: 'var(--font-mono)', color: 'var(--on-surface-variant)' }}>
            {statusIcon(log.status)}
            {statusLabel(log.status)}
          </span>
        </td>
      </tr>
      {isExpanded && (
        <tr>
          <td colSpan={6} style={{ padding: 0, borderBottom: '1px solid var(--outline-variant)' }}>
            <div
              style={{
                padding: '12px 16px 16px 32px',
                background: 'rgba(10,15,28,0.5)',
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
                gap: 10,
              }}
            >
              {log.explanation?.bias && (
                <ExplanationBlock title="Bias" text={log.explanation.bias} />
              )}
              {log.explanation?.zone && (
                <ExplanationBlock title="Zone" text={log.explanation.zone} />
              )}
              {log.explanation?.entryTrigger && (
                <ExplanationBlock title="Trigger" text={log.explanation.entryTrigger} />
              )}
              {log.explanation?.fundingContext && (
                <ExplanationBlock title="Funding" text={log.explanation.fundingContext} />
              )}
              {log.explanation?.riskCalc && (
                <ExplanationBlock title="Risk Calc" text={log.explanation.riskCalc} />
              )}
              {log.explanation?.lesson && (
                <ExplanationBlock title="Lesson" text={log.explanation.lesson} />
              )}
              {!log.explanation?.bias && !log.explanation?.zone && !log.explanation?.entryTrigger && !log.explanation?.fundingContext && !log.explanation?.lesson && (
                <span style={{ fontSize: 12, fontFamily: 'var(--font-mono)', color: 'var(--outline)' }}>
                  No explanation data available.
                </span>
              )}
            </div>
          </td>
        </tr>
      )}
    </>
  )
}

function ExplanationBlock({ title, text }: { title: string; text: string }) {
  return (
    <div
      style={{
        padding: '8px 10px',
        background: 'var(--surface-container-low)',
        borderRadius: 4,
        border: '1px solid var(--outline-variant)',
      }}
    >
      <div
        style={{
          fontSize: 9,
          fontFamily: 'var(--font-mono)',
          fontWeight: 600,
          color: 'var(--outline)',
          textTransform: 'uppercase',
          letterSpacing: '0.1em',
          marginBottom: 4,
        }}
      >
        {title}
      </div>
      <div style={{ fontSize: 12, fontFamily: 'var(--font-body)', color: 'var(--on-surface-variant)', lineHeight: 1.5 }}>
        {text}
      </div>
    </div>
  )
}

export default StrategiesPage
