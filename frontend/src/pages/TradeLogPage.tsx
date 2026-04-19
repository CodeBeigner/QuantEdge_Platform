import { useQuery } from '@tanstack/react-query'
import {
  BookOpen,
  ChevronDown,
  ChevronRight,
  Lightbulb,
  Search,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '@/services/api'
import { PageHeader } from '@/components/ui/PageHeader'
import type { TradeLog } from '@/types'

/* ─── Constants ─── */

type OutcomeFilter = 'all' | 'wins' | 'losses'

const STRATEGY_OPTIONS = [
  'All Strategies',
  'Trend Continuation',
  'Mean Reversion',
  'Funding Sentiment',
] as const

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

function strategyMatchesFilter(name: string, filter: string): boolean {
  if (filter === 'All Strategies') return true
  return name.toLowerCase().includes(filter.toLowerCase().split(' ')[0].toLowerCase())
}

function riskRewardStr(log: TradeLog): string {
  if (!log.entryPrice || !log.stopLossPrice || !log.takeProfitPrice) return '\u2014'
  const risk = Math.abs(log.entryPrice - log.stopLossPrice)
  if (risk === 0) return '\u2014'
  const reward = Math.abs(log.takeProfitPrice - log.entryPrice)
  return `1:${(reward / risk).toFixed(1)}`
}

function pnlDisplay(log: TradeLog): { text: string; color: string } {
  if (!log.outcome) return { text: 'Open', color: 'var(--on-surface-variant)' }
  const pnl = log.outcome.pnl
  const sign = pnl >= 0 ? '+' : ''
  return {
    text: `${sign}$${pnl.toFixed(2)}`,
    color: pnl >= 0 ? '#00e479' : '#ffb4ab',
  }
}

function rMultipleDisplay(log: TradeLog): string {
  if (!log.outcome) return '\u2014'
  const r = log.outcome.rMultiple
  const sign = r >= 0 ? '+' : ''
  return `${sign}${r.toFixed(2)}R`
}

function isWin(log: TradeLog): boolean {
  return log.outcome?.result === 'WIN'
}

function isLoss(log: TradeLog): boolean {
  return log.outcome?.result === 'LOSS'
}

/* ─── Component ─── */

export default function TradeLogPage() {
  const navigate = useNavigate()

  const [outcomeFilter, setOutcomeFilter] = useState<OutcomeFilter>('all')
  const [strategyFilter, setStrategyFilter] = useState('All Strategies')
  const [searchText, setSearchText] = useState('')
  const [expandedTradeId, setExpandedTradeId] = useState<string | null>(null)

  const { data: tradeLogs = [] } = useQuery<TradeLog[]>({
    queryKey: ['tradeLogs'],
    queryFn: () => api.getTradeLogs(),
    refetchInterval: 30_000,
  })

  /* ─── Filtered trades ─── */

  const filteredTrades = useMemo(() => {
    return tradeLogs.filter((log) => {
      // Outcome filter
      if (outcomeFilter === 'wins' && !isWin(log)) return false
      if (outcomeFilter === 'losses' && !isLoss(log)) return false

      // Strategy filter
      if (!strategyMatchesFilter(log.strategyName, strategyFilter)) return false

      // Search filter
      if (searchText.trim()) {
        const q = searchText.trim().toLowerCase()
        if (!log.tradeId.toLowerCase().includes(q)) return false
      }

      return true
    })
  }, [tradeLogs, outcomeFilter, strategyFilter, searchText])

  /* ─── Summary stats ─── */

  const summary = useMemo(() => {
    const total = filteredTrades.length
    const closedWithOutcome = filteredTrades.filter((t) => t.outcome)
    const wins = closedWithOutcome.filter((t) => t.outcome?.result === 'WIN').length
    const winRate = closedWithOutcome.length > 0
      ? ((wins / closedWithOutcome.length) * 100).toFixed(1)
      : '0.0'
    const totalPnl = closedWithOutcome.reduce((sum, t) => sum + (t.outcome?.pnl ?? 0), 0)
    return { total, winRate, totalPnl }
  }, [filteredTrades])

  /* ─── Toggle button style helper ─── */

  function toggleBtnStyle(active: boolean): React.CSSProperties {
    return {
      padding: '6px 16px',
      fontSize: 12,
      fontFamily: 'var(--font-mono)',
      fontWeight: 600,
      letterSpacing: '0.04em',
      border: 'none',
      cursor: 'pointer',
      background: active ? 'var(--primary)' : 'var(--surface-container-low)',
      color: active ? 'var(--on-primary)' : 'var(--on-surface-variant)',
      transition: 'background 150ms ease-out, color 150ms ease-out',
    }
  }

  /* ─── Render ─── */

  return (
    <div style={{ color: 'var(--on-surface)' }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <PageHeader title="Trade Log" subtitle="LEARN WHILE EARNING" />

        {/* ── Filter Bar ── */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 12,
            marginBottom: 16,
          }}
        >
          {/* Outcome toggle group */}
          <div
            style={{
              display: 'flex',
              borderRadius: 6,
              overflow: 'hidden',
              border: '1px solid var(--outline-variant)',
            }}
          >
            {([
              { key: 'all' as const, label: 'All' },
              { key: 'wins' as const, label: 'Wins' },
              { key: 'losses' as const, label: 'Losses' },
            ]).map(({ key, label }) => (
              <button
                key={key}
                type="button"
                onClick={() => setOutcomeFilter(key)}
                style={toggleBtnStyle(outcomeFilter === key)}
              >
                {label}
              </button>
            ))}
          </div>

          {/* Strategy dropdown */}
          <select
            value={strategyFilter}
            onChange={(e) => setStrategyFilter(e.target.value)}
            style={{
              padding: '6px 10px',
              fontSize: 12,
              fontFamily: 'var(--font-mono)',
              background: 'var(--surface-container-low)',
              color: 'var(--on-surface)',
              border: '1px solid var(--outline-variant)',
              borderRadius: 6,
              outline: 'none',
              cursor: 'pointer',
            }}
          >
            {STRATEGY_OPTIONS.map((opt) => (
              <option key={opt} value={opt}>{opt}</option>
            ))}
          </select>

          {/* Search input */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '5px 10px',
              background: 'var(--surface-container-low)',
              border: '1px solid var(--outline-variant)',
              borderRadius: 6,
              flex: '1 1 200px',
              maxWidth: 300,
            }}
          >
            <Search style={{ width: 14, height: 14, color: 'var(--outline)', flexShrink: 0 }} />
            <input
              type="text"
              placeholder="Search by trade ID..."
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              style={{
                background: 'transparent',
                border: 'none',
                outline: 'none',
                color: 'var(--on-surface)',
                fontSize: 12,
                fontFamily: 'var(--font-mono)',
                width: '100%',
              }}
            />
          </div>
        </div>

        {/* ── Summary Row ── */}
        <div
          style={{
            fontSize: 13,
            fontFamily: 'var(--font-mono)',
            color: 'var(--on-surface-variant)',
            marginBottom: 20,
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            flexWrap: 'wrap',
          }}
        >
          <span>{summary.total} trade{summary.total !== 1 ? 's' : ''}</span>
          <span style={{ color: 'var(--outline)' }}>|</span>
          <span>{summary.winRate}% win rate</span>
          <span style={{ color: 'var(--outline)' }}>|</span>
          <span
            style={{
              color: summary.totalPnl >= 0 ? '#00e479' : '#ffb4ab',
              fontWeight: 600,
            }}
          >
            {summary.totalPnl >= 0 ? '+' : ''}${summary.totalPnl.toFixed(2)} total P&L
          </span>
        </div>

        {/* ── Main Content ── */}
        {filteredTrades.length === 0 ? (
          /* ── Empty State ── */
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '80px 24px',
              border: '1px dashed var(--outline-variant)',
              borderRadius: 8,
              background: 'rgba(26,34,52,0.3)',
              textAlign: 'center',
            }}
          >
            <BookOpen
              style={{
                width: 48,
                height: 48,
                color: 'var(--outline)',
                marginBottom: 16,
              }}
            />
            <h3
              style={{
                fontSize: 16,
                fontFamily: 'var(--font-body)',
                fontWeight: 600,
                color: 'var(--on-surface)',
                marginBottom: 8,
              }}
            >
              No trades yet
            </h3>
            <p
              style={{
                fontSize: 13,
                fontFamily: 'var(--font-mono)',
                color: 'var(--outline)',
                lineHeight: 1.6,
                maxWidth: 420,
                marginBottom: 20,
              }}
            >
              Your trade history and educational explanations will appear here
              once the strategies start generating signals.
            </p>
            <button
              type="button"
              onClick={() => navigate('/strategies')}
              style={{
                padding: '8px 20px',
                fontSize: 12,
                fontFamily: 'var(--font-mono)',
                fontWeight: 600,
                background: 'var(--primary)',
                color: 'var(--on-primary)',
                border: 'none',
                borderRadius: 6,
                cursor: 'pointer',
                transition: 'opacity 150ms ease-out',
              }}
              onMouseEnter={(e) => { e.currentTarget.style.opacity = '0.85' }}
              onMouseLeave={(e) => { e.currentTarget.style.opacity = '1' }}
            >
              Go to Strategies
            </button>
          </div>
        ) : (
          /* ── Accordion Trade Cards ── */
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {filteredTrades.map((log) => (
              <TradeCard
                key={log.tradeId}
                log={log}
                isExpanded={expandedTradeId === log.tradeId}
                onToggle={() =>
                  setExpandedTradeId(
                    expandedTradeId === log.tradeId ? null : log.tradeId
                  )
                }
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

/* ─── Trade Card Sub-component ─── */

function TradeCard({
  log,
  isExpanded,
  onToggle,
}: {
  log: TradeLog
  isExpanded: boolean
  onToggle: () => void
}) {
  const isLong = log.direction === 'BUY'
  const win = isWin(log)
  const borderColor = log.outcome ? (win ? '#00e479' : '#ffb4ab') : 'var(--outline-variant)'
  const { text: pnlText, color: pnlColor } = pnlDisplay(log)
  const confidencePct = (log.confidence >= 0 && log.confidence <= 1)
    ? log.confidence * 100
    : log.confidence

  const hasExplanation =
    log.explanation?.bias ||
    log.explanation?.zone ||
    log.explanation?.entryTrigger ||
    log.explanation?.fundingContext ||
    log.explanation?.riskCalc ||
    log.explanation?.lesson

  return (
    <div
      style={{
        borderLeft: `3px solid ${borderColor}`,
        background: 'var(--surface-container-low)',
        borderRadius: 6,
        border: `1px solid var(--outline-variant)`,
        borderLeftWidth: 3,
        borderLeftColor: borderColor,
        overflow: 'hidden',
        transition: 'box-shadow 150ms ease-out',
      }}
      onMouseEnter={(e) => {
        if (!isExpanded) {
          e.currentTarget.style.boxShadow = '0 2px 12px rgba(0,0,0,0.2)'
        }
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.boxShadow = 'none'
      }}
    >
      {/* ── Collapsed Header Row ── */}
      <div
        onClick={onToggle}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          padding: '12px 16px',
          cursor: 'pointer',
          flexWrap: 'wrap',
          transition: 'background 100ms ease-out',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.background = 'rgba(173,198,255,0.04)'
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.background = 'transparent'
        }}
      >
        {/* Expand chevron */}
        <div style={{ flexShrink: 0, display: 'flex', alignItems: 'center' }}>
          {isExpanded
            ? <ChevronDown style={{ width: 16, height: 16, color: 'var(--outline)' }} />
            : <ChevronRight style={{ width: 16, height: 16, color: 'var(--outline)' }} />
          }
        </div>

        {/* Trade ID */}
        <span
          style={{
            fontSize: 11,
            fontFamily: 'var(--font-mono)',
            color: 'var(--outline)',
            flexShrink: 0,
            minWidth: 80,
          }}
        >
          {log.tradeId}
        </span>

        {/* Pair + Direction badge */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
          <span
            style={{
              fontSize: 13,
              fontFamily: 'var(--font-mono)',
              fontWeight: 600,
              color: 'var(--on-surface)',
            }}
          >
            {log.symbol}
          </span>
          <span
            style={{
              display: 'inline-block',
              padding: '2px 8px',
              borderRadius: 4,
              fontSize: 10,
              fontFamily: 'var(--font-mono)',
              fontWeight: 700,
              letterSpacing: '0.04em',
              background: isLong ? 'rgba(0,228,121,0.15)' : 'rgba(255,180,171,0.15)',
              color: isLong ? '#00e479' : '#ffb4ab',
              border: `1px solid ${isLong ? 'rgba(0,228,121,0.3)' : 'rgba(255,180,171,0.3)'}`,
            }}
          >
            {isLong ? 'LONG' : 'SHORT'}
          </span>
        </div>

        {/* P&L (large) */}
        <span
          style={{
            fontSize: 16,
            fontFamily: 'var(--font-display)',
            fontWeight: 700,
            color: pnlColor,
            flexShrink: 0,
            marginLeft: 'auto',
          }}
        >
          {pnlText}
        </span>

        {/* Strategy name */}
        <span
          style={{
            fontSize: 11,
            fontFamily: 'var(--font-mono)',
            color: 'var(--on-surface-variant)',
            flexShrink: 0,
          }}
        >
          {log.strategyName}
        </span>

        {/* Confidence bar */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexShrink: 0 }}>
          <div
            style={{
              width: 40,
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
                width: `${Math.min(100, Math.max(0, confidencePct))}%`,
                background: 'var(--primary)',
              }}
            />
          </div>
          <span
            style={{
              fontSize: 10,
              fontFamily: 'var(--font-mono)',
              color: 'var(--outline)',
            }}
          >
            {confidencePct.toFixed(0)}%
          </span>
        </div>

        {/* R-Multiple */}
        <span
          style={{
            fontSize: 12,
            fontFamily: 'var(--font-mono)',
            fontWeight: 600,
            color: log.outcome
              ? (log.outcome.rMultiple >= 0 ? '#00e479' : '#ffb4ab')
              : 'var(--outline)',
            flexShrink: 0,
          }}
        >
          {rMultipleDisplay(log)}
        </span>
      </div>

      {/* ── Expanded Content ── */}
      <div
        style={{
          maxHeight: isExpanded ? 1200 : 0,
          overflow: 'hidden',
          transition: 'max-height 300ms ease-out',
        }}
      >
        <div
          style={{
            padding: '0 16px 16px 40px',
            borderTop: '1px solid var(--outline-variant)',
          }}
        >
          {/* Explanation sections */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
              gap: 10,
              marginTop: 14,
            }}
          >
            {log.explanation?.bias && (
              <ExplanationBlock title="Bias (4H)" text={log.explanation.bias} />
            )}
            {log.explanation?.zone && (
              <ExplanationBlock title="Zone (1H)" text={log.explanation.zone} />
            )}
            {log.explanation?.entryTrigger && (
              <ExplanationBlock title="Entry Trigger (15M)" text={log.explanation.entryTrigger} />
            )}
            {log.explanation?.fundingContext && (
              <ExplanationBlock title="Funding Context" text={log.explanation.fundingContext} />
            )}
            {log.explanation?.riskCalc && (
              <ExplanationBlock title="Risk Calculation" text={log.explanation.riskCalc} />
            )}
            {log.explanation?.lesson && (
              <LessonBlock text={log.explanation.lesson} />
            )}
            {!hasExplanation && (
              <span
                style={{
                  fontSize: 12,
                  fontFamily: 'var(--font-mono)',
                  color: 'var(--outline)',
                  gridColumn: '1 / -1',
                }}
              >
                No explanation data available for this trade.
              </span>
            )}
          </div>

          {/* Outcome section */}
          {log.outcome && (
            <div
              style={{
                marginTop: 16,
                padding: '12px 14px',
                background: 'rgba(10,15,28,0.5)',
                borderRadius: 6,
                border: '1px solid var(--outline-variant)',
              }}
            >
              <div
                style={{
                  fontSize: 10,
                  fontFamily: 'var(--font-mono)',
                  fontWeight: 600,
                  color: 'var(--outline)',
                  textTransform: 'uppercase',
                  letterSpacing: '0.1em',
                  marginBottom: 10,
                }}
              >
                Outcome
              </div>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))',
                  gap: 12,
                }}
              >
                <OutcomeStat
                  label="Exit Price"
                  value={`$${log.outcome.exitPrice.toFixed(2)}`}
                />
                <OutcomeStat
                  label="Final P&L"
                  value={`${log.outcome.pnl >= 0 ? '+' : ''}$${log.outcome.pnl.toFixed(2)}`}
                  color={log.outcome.pnl >= 0 ? '#00e479' : '#ffb4ab'}
                />
                <OutcomeStat
                  label="R-Multiple"
                  value={`${log.outcome.rMultiple >= 0 ? '+' : ''}${log.outcome.rMultiple.toFixed(2)}R`}
                  color={log.outcome.rMultiple >= 0 ? '#00e479' : '#ffb4ab'}
                />
                <OutcomeStat label="R:R" value={riskRewardStr(log)} />
              </div>

              {log.outcome.postTradeLesson && (
                <div
                  style={{
                    marginTop: 12,
                    padding: '10px 12px',
                    background: 'rgba(93,130,255,0.08)',
                    borderRadius: 4,
                    border: '1px solid rgba(93,130,255,0.15)',
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 8,
                  }}
                >
                  <Lightbulb
                    style={{
                      width: 16,
                      height: 16,
                      color: '#adc6ff',
                      flexShrink: 0,
                      marginTop: 1,
                    }}
                  />
                  <div>
                    <div
                      style={{
                        fontSize: 10,
                        fontFamily: 'var(--font-mono)',
                        fontWeight: 600,
                        color: '#adc6ff',
                        textTransform: 'uppercase',
                        letterSpacing: '0.1em',
                        marginBottom: 4,
                      }}
                    >
                      Post-Trade Lesson
                    </div>
                    <div
                      style={{
                        fontSize: 12,
                        fontFamily: 'var(--font-body)',
                        color: 'var(--on-surface-variant)',
                        lineHeight: 1.6,
                      }}
                    >
                      {log.outcome.postTradeLesson}
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Timestamp footer */}
          <div
            style={{
              marginTop: 12,
              fontSize: 11,
              fontFamily: 'var(--font-mono)',
              color: 'var(--outline)',
              display: 'flex',
              gap: 16,
              flexWrap: 'wrap',
            }}
          >
            <span>Opened: {formatTime(log.openedAt)}</span>
            {log.closedAt && <span>Closed: {formatTime(log.closedAt)}</span>}
            <span>Leverage: {log.effectiveLeverage.toFixed(1)}x</span>
            <span>Size: {log.positionSize}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

/* ─── Explanation Block ─── */

function ExplanationBlock({ title, text }: { title: string; text: string }) {
  return (
    <div
      style={{
        padding: '10px 12px',
        background: 'var(--surface-container-lowest)',
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
      <div
        style={{
          fontSize: 12,
          fontFamily: 'var(--font-body)',
          color: 'var(--on-surface-variant)',
          lineHeight: 1.6,
        }}
      >
        {text}
      </div>
    </div>
  )
}

/* ─── Lesson Block (highlighted) ─── */

function LessonBlock({ text }: { text: string }) {
  return (
    <div
      style={{
        padding: '10px 12px',
        background: 'rgba(93,130,255,0.08)',
        borderRadius: 4,
        border: '1px solid rgba(93,130,255,0.15)',
        display: 'flex',
        alignItems: 'flex-start',
        gap: 8,
      }}
    >
      <Lightbulb
        style={{
          width: 16,
          height: 16,
          color: '#adc6ff',
          flexShrink: 0,
          marginTop: 1,
        }}
      />
      <div>
        <div
          style={{
            fontSize: 9,
            fontFamily: 'var(--font-mono)',
            fontWeight: 600,
            color: '#adc6ff',
            textTransform: 'uppercase',
            letterSpacing: '0.1em',
            marginBottom: 4,
          }}
        >
          Lesson
        </div>
        <div
          style={{
            fontSize: 12,
            fontFamily: 'var(--font-body)',
            color: 'var(--on-surface-variant)',
            lineHeight: 1.6,
          }}
        >
          {text}
        </div>
      </div>
    </div>
  )
}

/* ─── Outcome Stat ─── */

function OutcomeStat({
  label,
  value,
  color,
}: {
  label: string
  value: string
  color?: string
}) {
  return (
    <div>
      <div
        style={{
          fontSize: 10,
          fontFamily: 'var(--font-mono)',
          color: 'var(--outline)',
          textTransform: 'uppercase',
          letterSpacing: '0.08em',
          marginBottom: 2,
        }}
      >
        {label}
      </div>
      <div
        style={{
          fontSize: 14,
          fontFamily: 'var(--font-display)',
          fontWeight: 700,
          color: color ?? 'var(--on-surface)',
        }}
      >
        {value}
      </div>
    </div>
  )
}
