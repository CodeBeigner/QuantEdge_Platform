import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Brain,
  ChevronDown,
  ChevronUp,
  Loader2,
  Play,
  Plus,
  Trash2,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { api } from '@/services/api'
import { PageHeader } from '@/components/ui/PageHeader'
import type { ExecutionResult, Strategy } from '@/types'

const MODEL_TYPES = [
  'MOMENTUM',
  'VOLATILITY',
  'MACRO',
  'CORRELATION',
  'REGIME',
] as const satisfies readonly Strategy['modelType'][]

const MODEL_BADGE: Record<Strategy['modelType'], React.CSSProperties> = {
  MOMENTUM: { background: 'rgba(0,255,136,0.15)', color: 'var(--tertiary)', borderColor: 'rgba(0,255,136,0.4)' },
  VOLATILITY: { background: 'rgba(59,130,246,0.15)', color: 'var(--primary)', borderColor: 'rgba(59,130,246,0.4)' },
  MACRO: { background: 'rgba(251,191,36,0.15)', color: '#fbbf24', borderColor: 'rgba(251,191,36,0.4)' },
  CORRELATION: { background: 'rgba(167,139,250,0.15)', color: 'var(--primary)', borderColor: 'rgba(167,139,250,0.4)' },
  REGIME: { background: 'rgba(34,211,238,0.15)', color: '#22d3ee', borderColor: 'rgba(34,211,238,0.4)' },
}

function signalBadgeStyle(signal: string): React.CSSProperties {
  const s = signal.toUpperCase()
  if (s === 'BUY' || s.includes('BUY'))
    return { background: 'rgba(0,255,136,0.15)', color: 'var(--tertiary)', borderColor: 'rgba(0,255,136,0.4)' }
  if (s === 'SELL' || s.includes('SELL'))
    return { background: 'rgba(239,68,68,0.15)', color: 'var(--error)', borderColor: 'rgba(239,68,68,0.4)' }
  return { background: 'rgba(251,191,36,0.15)', color: '#fbbf24', borderColor: 'rgba(251,191,36,0.4)' }
}

function formatDate(iso: string) {
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return iso
  }
}

export function StrategiesPage() {
  const queryClient = useQueryClient()
  const [formOpen, setFormOpen] = useState(false)
  const [name, setName] = useState('')
  const [symbol, setSymbol] = useState('')
  const [modelType, setModelType] = useState<Strategy['modelType']>('MOMENTUM')
  const [executionById, setExecutionById] = useState<
    Record<number, ExecutionResult | undefined>
  >({})

  const { data: strategies = [], isLoading: strategiesLoading } = useQuery({
    queryKey: ['strategies'],
    queryFn: () => api.getStrategies(),
  })

  const { data: symbols = [], isLoading: symbolsLoading } = useQuery({
    queryKey: ['symbols'],
    queryFn: () => api.getSymbols(),
  })

  const symbolOptions = useMemo(() => {
    const list = [...symbols].sort()
    return list
  }, [symbols])

  const createMutation = useMutation({
    mutationFn: () =>
      api.createStrategy({
        name: name.trim(),
        symbol: symbol.trim(),
        modelType,
        parameters: {},
        active: true,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['strategies'] })
      setName('')
      setSymbol('')
      setModelType('MOMENTUM')
      setFormOpen(false)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.deleteStrategy(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['strategies'] })
      setExecutionById(prev => {
        const next = { ...prev }
        delete next[id]
        return next
      })
    },
  })

  const executeMutation = useMutation({
    mutationFn: (id: number) => api.executeStrategy(id),
    onSuccess: (result, id) => {
      setExecutionById(prev => ({ ...prev, [id]: result }))
    },
  })

  function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim() || !symbol.trim()) return
    createMutation.mutate()
  }

  return (
    <div style={{ color: 'var(--on-surface)' }}>
      <div className="mx-auto max-w-5xl">
        <PageHeader title="Strategies" subtitle="ALPHA GENERATION // MODEL EXECUTION">
          <button
            type="button"
            onClick={() => setFormOpen(o => !o)}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium transition"
            style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface-container-low)', color: 'var(--on-surface)' }}
          >
            <Plus className="h-4 w-4" style={{ color: 'var(--tertiary)' }} />
            New Strategy
            {formOpen ? (
              <ChevronUp className="h-4 w-4" style={{ color: 'var(--on-surface-variant)' }} />
            ) : (
              <ChevronDown className="h-4 w-4" style={{ color: 'var(--on-surface-variant)' }} />
            )}
          </button>
        </PageHeader>

        {formOpen && (
          <form
            onSubmit={handleCreate}
            className="mb-8 p-6 shadow-lg"
            style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface-container-low)' }}
          >
            <h2 className="mb-4 text-lg font-medium" style={{ color: 'var(--on-surface)' }}>
              Create strategy
            </h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="flex flex-col gap-1.5 text-sm">
                <span style={{ color: 'var(--on-surface-variant)' }}>Name</span>
                <input
                  value={name}
                  onChange={e => setName(e.target.value)}
                  className="px-3 py-2 font-mono outline-none"
                  style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface)', color: 'var(--on-surface)' }}
                  placeholder="My alpha"
                  required
                />
              </label>
              <label className="flex flex-col gap-1.5 text-sm">
                <span style={{ color: 'var(--on-surface-variant)' }}>Symbol</span>
                <select
                  value={symbol}
                  onChange={e => setSymbol(e.target.value)}
                  disabled={symbolsLoading}
                  className="px-3 py-2 font-mono outline-none disabled:opacity-50"
                  style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface)', color: 'var(--on-surface)' }}
                  required
                >
                  <option value="">
                    {symbolsLoading ? 'Loading...' : 'Select symbol'}
                  </option>
                  {symbolOptions.map(s => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-1.5 text-sm sm:col-span-2">
                <span style={{ color: 'var(--on-surface-variant)' }}>Model type</span>
                <select
                  value={modelType}
                  onChange={e =>
                    setModelType(e.target.value as Strategy['modelType'])
                  }
                  className="px-3 py-2 font-mono outline-none"
                  style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface)', color: 'var(--on-surface)' }}
                >
                  {MODEL_TYPES.map(t => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            {createMutation.isError && (
              <p className="mt-3 text-sm" style={{ color: 'var(--error)' }}>
                {(createMutation.error as Error).message}
              </p>
            )}
            <div className="mt-4 flex gap-3">
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="inline-flex items-center gap-2 bg-gradient-to-r from-[#00ff88] to-[#3b82f6] px-4 py-2 text-sm font-semibold disabled:opacity-50"
                style={{ color: 'var(--surface)' }}
              >
                {createMutation.isPending && (
                  <Loader2 className="h-4 w-4 animate-spin" />
                )}
                Submit
              </button>
              <button
                type="button"
                onClick={() => setFormOpen(false)}
                className="px-4 py-2 text-sm"
                style={{ border: '1px solid var(--outline-variant)', color: 'var(--on-surface-variant)' }}
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        {strategiesLoading ? (
          <div className="flex justify-center py-16" style={{ color: 'var(--on-surface-variant)' }}>
            <Loader2 className="h-8 w-8 animate-spin" style={{ color: 'var(--tertiary)' }} />
          </div>
        ) : strategies.length === 0 ? (
          <p className="py-12 text-center" style={{ border: '1px dashed var(--outline-variant)', background: 'rgba(26,34,52,0.5)', color: 'var(--outline)' }}>
            No strategies yet. Create one to get started.
          </p>
        ) : (
          <ul className="space-y-4">
            {strategies.map(s => {
              const exec = executionById[s.id]
              const signal = exec?.action ?? 'HOLD'
              return (
                <li key={s.id}>
                  <article className="overflow-hidden" style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface-container-low)' }}>
                    <div className="flex flex-wrap items-start justify-between gap-3 p-4" style={{ borderBottom: '1px solid var(--outline-variant)' }}>
                      <div>
                        <h3 className="text-lg font-semibold" style={{ color: 'var(--on-surface)' }}>
                          {s.name}
                        </h3>
                        <span
                          className="mt-2 inline-block rounded-md font-mono text-xs font-medium px-2 py-0.5"
                          style={{ ...MODEL_BADGE[s.modelType], borderWidth: '1px', borderStyle: 'solid' }}
                        >
                          {s.modelType}
                        </span>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => executeMutation.mutate(s.id)}
                          disabled={executeMutation.isPending}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium transition disabled:opacity-50"
                          style={{ border: '1px solid rgba(0,255,136,0.6)', color: 'var(--tertiary)', background: 'transparent' }}
                        >
                          {executeMutation.isPending &&
                          executeMutation.variables === s.id ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            <Play className="h-4 w-4" />
                          )}
                          Execute
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            if (
                              confirm(
                                `Delete strategy "${s.name}"? This cannot be undone.`,
                              )
                            )
                              deleteMutation.mutate(s.id)
                          }}
                          disabled={deleteMutation.isPending}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium transition disabled:opacity-50"
                          style={{ border: '1px solid rgba(239,68,68,0.6)', color: 'var(--error)', background: 'transparent' }}
                        >
                          <Trash2 className="h-4 w-4" />
                          Delete
                        </button>
                      </div>
                    </div>
                    <div className="grid gap-2 p-4 font-mono text-sm sm:grid-cols-2" style={{ color: 'var(--on-surface-variant)' }}>
                      <div>
                        <span style={{ color: 'var(--outline)' }}>Symbol</span>{' '}
                        <span style={{ color: 'var(--on-surface)' }}>{s.symbol}</span>
                      </div>
                      <div>
                        <span style={{ color: 'var(--outline)' }}>Created</span>{' '}
                        <span style={{ color: 'var(--on-surface)' }}>
                          {formatDate(s.createdAt)}
                        </span>
                      </div>
                    </div>

                    {executeMutation.isError &&
                      executeMutation.variables === s.id && (
                        <p className="px-4 py-3 text-sm" style={{ borderTop: '1px solid var(--outline-variant)', color: 'var(--error)' }}>
                          {(executeMutation.error as Error).message}
                        </p>
                      )}

                    {exec && (
                      <div className="p-4" style={{ borderTop: '1px solid var(--outline-variant)', background: 'rgba(10,15,28,0.6)' }}>
                        <p className="mb-3 text-xs font-medium uppercase tracking-wider" style={{ color: 'var(--outline)' }}>
                          Execution result
                        </p>
                        <div className="mb-3 flex flex-wrap items-center gap-3">
                          <span
                            className="rounded-md font-mono text-sm font-semibold px-2.5 py-1"
                            style={{ ...signalBadgeStyle(signal), borderWidth: '1px', borderStyle: 'solid' }}
                          >
                            {signal}
                          </span>
                          <span className="text-sm" style={{ color: 'var(--on-surface-variant)' }}>
                            Confidence
                          </span>
                          {(() => {
                            const confPct =
                              exec.confidence >= 0 && exec.confidence <= 1
                                ? exec.confidence * 100
                                : exec.confidence
                            const w = Math.min(100, Math.max(0, confPct))
                            return (
                              <>
                                <div className="h-2 flex-1 min-w-[120px] max-w-xs overflow-hidden rounded-full" style={{ background: 'var(--outline-variant)' }}>
                                  <div
                                    className="h-full rounded-full transition-all"
                                    style={{ background: 'var(--primary)', width: `${w}%` }}
                                  />
                                </div>
                                <span className="font-mono text-sm" style={{ color: 'var(--on-surface)' }}>
                                  {w.toFixed(1)}%
                                </span>
                              </>
                            )
                          })()}
                        </div>
                        <p className="mb-4 text-sm leading-relaxed" style={{ color: 'var(--on-surface-variant)' }}>
                          {exec.reasoning}
                        </p>
                        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                          <div className="p-2 font-mono text-xs" style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface-container-low)' }}>
                            <span style={{ color: 'var(--outline)' }}>Qty</span>{' '}
                            <span style={{ color: 'var(--on-surface)' }}>
                              {exec.quantity}
                            </span>
                          </div>
                          <div className="p-2 font-mono text-xs" style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface-container-low)' }}>
                            <span style={{ color: 'var(--outline)' }}>Price</span>{' '}
                            <span style={{ color: 'var(--on-surface)' }}>
                              {exec.price}
                            </span>
                          </div>
                          <div className="p-2 font-mono text-xs" style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface-container-low)' }}>
                            <span style={{ color: 'var(--outline)' }}>Success</span>{' '}
                            <span
                              style={{ color: exec.success ? 'var(--tertiary)' : 'var(--error)' }}
                            >
                              {String(exec.success)}
                            </span>
                          </div>
                          {exec.metadata &&
                            Object.entries(exec.metadata).map(([k, v]) => (
                              <div
                                key={k}
                                className="p-2 font-mono text-xs sm:col-span-2 lg:col-span-3"
                                style={{ border: '1px solid var(--outline-variant)', background: 'var(--surface-container-low)' }}
                              >
                                <span style={{ color: 'var(--outline)' }}>{k}</span>
                                <pre className="mt-1 overflow-x-auto whitespace-pre-wrap break-all" style={{ color: 'var(--on-surface-variant)' }}>
                                  {typeof v === 'object'
                                    ? JSON.stringify(v, null, 2)
                                    : String(v)}
                                </pre>
                              </div>
                            ))}
                        </div>
                      </div>
                    )}
                  </article>
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </div>
  )
}

export default StrategiesPage
