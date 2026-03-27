import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import {
  Users, GitBranch, Activity, PieChart, ClipboardList,
  MonitorCheck, Loader2, ChevronDown, CheckCircle2, Circle, AlertTriangle,
} from 'lucide-react';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import type { TradingAgent, PipelineResult } from '@/types';

type SubTab = 'roster' | 'pipeline' | 'signal-ic' | 'attribution' | 'audit' | 'exec-monitor';

const TABS: { key: SubTab; label: string; icon: React.ComponentType<{ size?: number; className?: string }> }[] = [
  { key: 'roster',       label: 'Agent Roster',  icon: Users },
  { key: 'pipeline',     label: 'Pipeline',      icon: GitBranch },
  { key: 'signal-ic',    label: 'Signal IC',     icon: Activity },
  { key: 'attribution',  label: 'Attribution',   icon: PieChart },
  { key: 'audit',        label: 'System Audit',  icon: ClipboardList },
  { key: 'exec-monitor', label: 'Exec Monitor',  icon: MonitorCheck },
];

const PIPELINE_STAGES = [
  { key: 'regime_analysis', label: 'Regime Analysis' },
  { key: 'research_decision', label: 'Research Decision' },
  { key: 'bias_audit', label: 'Bias Audit' },
  { key: 'risk_assessment', label: 'Risk Assessment' },
  { key: 'psychology_check', label: 'Psychology Check' },
];

/* ---------- Agent Roster ---------- */
function AgentRoster() {
  const { data: agents = [], isLoading, error } = useQuery<TradingAgent[]>({
    queryKey: ['agents'],
    queryFn: api.getAgents,
  });

  if (isLoading) return <div className="space-y-2">{Array.from({ length: 4 }).map((_, i) => <div key={i} className="h-12 skeleton" />)}</div>;
  if (error) return (
    <div className="flex items-center gap-2 py-4" style={{ color: 'var(--error)' }}>
      <AlertTriangle size={16} />
      <span className="text-sm">Failed to load agents: {(error as Error).message}</span>
    </div>
  );

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs uppercase tracking-wider" style={{ color: 'var(--outline)', borderBottom: '1px solid var(--outline-variant)' }}>
            <th className="pb-3 pr-4">Agent</th>
            <th className="pb-3 pr-4">Role</th>
            <th className="pb-3 pr-4">Executions</th>
            <th className="pb-3 pr-4">Success</th>
            <th className="pb-3 pr-4">Confidence</th>
            <th className="pb-3">Status</th>
          </tr>
        </thead>
        <tbody className="divide-y" style={{ '--tw-divide-opacity': '1', borderColor: 'var(--outline-variant)' } as React.CSSProperties}>
          {agents.map(a => {
            const rate = a.totalExecutions > 0 ? ((a.successfulExecutions / a.totalExecutions) * 100).toFixed(1) : '—';
            return (
              <tr key={a.id} className="hover:opacity-80">
                <td className="py-3 pr-4">
                  <div className="flex items-center gap-2">
                    <div
                      className="w-7 h-7 rounded-md flex items-center justify-center text-[10px] font-bold text-white shrink-0"
                      style={{ background: a.personaColor ?? 'var(--primary)' }}
                    >
                      {a.personaInitials ?? a.name.slice(0, 2).toUpperCase()}
                    </div>
                    <span className="font-medium" style={{ color: 'var(--on-surface)' }}>{a.personaName ?? a.name}</span>
                  </div>
                </td>
                <td className="py-3 pr-4" style={{ color: 'var(--on-surface-variant)' }}>{a.agentRole.replace(/_/g, ' ')}</td>
                <td className="py-3 pr-4 font-mono" style={{ color: 'var(--on-surface)' }}>{a.totalExecutions}</td>
                <td className="py-3 pr-4 font-mono" style={{ color: 'var(--tertiary)' }}>{rate}%</td>
                <td className="py-3 pr-4 font-mono" style={{ color: 'var(--on-surface)' }}>
                  {a.lastConfidence != null ? `${(a.lastConfidence * 100).toFixed(0)}%` : '—'}
                </td>
                <td className="py-3">
                  <span
                    className="inline-flex items-center gap-1.5 text-xs px-2 py-0.5 rounded-full"
                    style={{
                      background: a.active ? 'rgba(0,255,136,0.15)' : 'rgba(100,116,139,0.15)',
                      color: a.active ? 'var(--tertiary)' : 'var(--outline)',
                    }}
                  >
                    <span
                      className="w-1.5 h-1.5 rounded-full"
                      style={{ background: a.active ? 'var(--tertiary)' : 'var(--outline)' }}
                    />
                    {a.active ? 'Active' : 'Inactive'}
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {agents.length === 0 && <p className="text-sm text-center py-8" style={{ color: 'var(--outline)' }}>No agents found</p>}
    </div>
  );
}

/* ---------- Pipeline ---------- */
function PipelineTab() {
  const [agentId, setAgentId] = useState<number | ''>('');
  const [symbol, setSymbol] = useState('SPY');
  const [expanded, setExpanded] = useState<string | null>(null);

  const { data: agents = [] } = useQuery<TradingAgent[]>({ queryKey: ['agents'], queryFn: api.getAgents });
  const { data: symbols = [] } = useQuery<string[]>({ queryKey: ['symbols'], queryFn: api.getSymbols });

  const mutation = useMutation({
    mutationFn: () => api.runAgentPipeline(agentId as number, symbol),
  });

  const result = mutation.data as PipelineResult | undefined;

  return (
    <div className="space-y-4">
      <div className="flex gap-3 flex-wrap items-end">
        <div>
          <label className="block text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>Agent</label>
          <select value={agentId} onChange={e => setAgentId(e.target.value ? Number(e.target.value) : '')} className="px-3 py-2 text-sm outline-none cursor-pointer" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)', color: 'var(--on-surface)' }}>
            <option value="">Select agent</option>
            {agents.map(a => <option key={a.id} value={a.id}>{a.personaName ?? a.name}</option>)}
          </select>
        </div>
        <div>
          <label className="block text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>Symbol</label>
          <select value={symbol} onChange={e => setSymbol(e.target.value)} className="px-3 py-2 text-sm outline-none cursor-pointer" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)', color: 'var(--on-surface)' }}>
            {symbols.length === 0 && <option value="SPY">SPY</option>}
            {symbols.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>
        <button
          onClick={() => mutation.mutate()}
          disabled={!agentId || mutation.isPending}
          className="px-4 py-2 text-white text-sm font-medium disabled:opacity-40 transition-colors cursor-pointer flex items-center gap-2"
          style={{ background: 'var(--primary)' }}
        >
          {mutation.isPending && <Loader2 size={14} className="animate-spin" />}
          Run Pipeline
        </button>
      </div>

      {mutation.isError && <p className="text-sm" style={{ color: 'var(--error)' }}>{(mutation.error as Error).message}</p>}

      {/* 5-stage stepper */}
      {result && (
        <div className="space-y-0">
          <div className="flex items-center gap-2 mb-3">
            <span className="text-xs" style={{ color: 'var(--outline)' }}>Decision:</span>
            <span className="text-sm font-semibold" style={{ color: '#fbbf24' }}>{result.final_decision ?? 'N/A'}</span>
            <span
              className="text-xs px-2 py-0.5 rounded-full"
              style={{
                background: result.pipeline_complete ? 'rgba(0,255,136,0.15)' : 'rgba(251,191,36,0.15)',
                color: result.pipeline_complete ? 'var(--tertiary)' : '#fbbf24',
              }}
            >
              {result.pipeline_complete ? 'Complete' : 'Partial'}
            </span>
          </div>

          {PIPELINE_STAGES.map((stage, idx) => {
            const data = result[stage.key as keyof PipelineResult] as Record<string, unknown> | undefined;
            const hasData = !!data;
            const isExpanded = expanded === stage.key;
            return (
              <div key={stage.key}>
                {/* Connector */}
                {idx > 0 && <div className="ml-3.5 h-4 w-px" style={{ background: 'var(--outline-variant)' }} />}
                {/* Stage */}
                <button
                  onClick={() => setExpanded(isExpanded ? null : stage.key)}
                  disabled={!hasData}
                  className="flex items-center gap-3 w-full text-left px-2 py-2 transition-colors cursor-pointer disabled:cursor-default disabled:opacity-50"
                >
                  {hasData ? <CheckCircle2 size={16} style={{ color: 'var(--tertiary)' }} className="shrink-0" /> : <Circle size={16} style={{ color: 'var(--outline)' }} className="shrink-0" />}
                  <span className="text-sm font-medium" style={{ color: hasData ? 'var(--on-surface)' : 'var(--outline)' }}>{stage.label}</span>
                  {hasData && <ChevronDown size={14} className={`ml-auto transition-transform ${isExpanded ? 'rotate-180' : ''}`} style={{ color: 'var(--outline)' }} />}
                </button>
                {isExpanded && data && (
                  <div className="ml-9 mt-1 mb-2 p-4 space-y-2 animate-in" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)' }}>
                    {renderStageData(data)}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function renderStageData(data: Record<string, unknown>) {
  const verdict = data.verdict ?? data.decision ?? data.recommendation;
  const confidence = data.confidence as number | undefined;
  const reasoning = data.reasoning ?? data.explanation ?? data.analysis;

  return (
    <>
      {verdict && (
        <div className="flex items-center gap-2">
          <span className="text-xs" style={{ color: 'var(--outline)' }}>Verdict:</span>
          <span className="text-sm font-semibold" style={{ color: '#fbbf24' }}>{String(verdict)}</span>
        </div>
      )}
      {confidence != null && (
        <div className="flex items-center gap-2">
          <span className="text-xs" style={{ color: 'var(--outline)' }}>Confidence:</span>
          <span className="text-sm font-mono" style={{ color: 'var(--on-surface)' }}>{(Number(confidence) * 100).toFixed(0)}%</span>
        </div>
      )}
      {reasoning && (
        <div>
          <span className="text-xs" style={{ color: 'var(--outline)' }}>Reasoning:</span>
          <p className="text-sm mt-0.5 leading-relaxed" style={{ color: 'var(--on-surface-variant)' }}>{String(reasoning)}</p>
        </div>
      )}
      {Object.entries(data)
        .filter(([k]) => !['verdict', 'decision', 'recommendation', 'confidence', 'reasoning', 'explanation', 'analysis'].includes(k))
        .map(([k, v]) => (
          <div key={k}>
            <span className="text-xs" style={{ color: 'var(--outline)' }}>{k.replace(/_/g, ' ')}:</span>
            <p className="text-xs font-mono mt-0.5" style={{ color: 'var(--on-surface-variant)' }}>
              {typeof v === 'object' ? JSON.stringify(v, null, 2) : String(v)}
            </p>
          </div>
        ))}
    </>
  );
}

/* ---------- Signal IC ---------- */
function SignalICTab() {
  const [strategyId, setStrategyId] = useState<number | ''>('');
  const [window, setWindow] = useState(20);

  const { data: strategies = [] } = useQuery({ queryKey: ['strategies'], queryFn: api.getStrategies });

  const { data: icData, isLoading, refetch } = useQuery<Record<string, unknown>>({
    queryKey: ['signal-ic', strategyId, window],
    queryFn: () => api.getSignalIC(strategyId as number, window),
    enabled: false,
  });

  const ic = icData as Record<string, unknown> | undefined;
  const icValue = ic ? Number(ic.ic ?? ic.information_coefficient ?? 0) : null;

  const icQuality = icValue != null
    ? Math.abs(icValue) > 0.1 ? { label: 'Strong', bg: 'rgba(0,255,136,0.15)', color: 'var(--tertiary)' }
      : Math.abs(icValue) > 0.05 ? { label: 'Moderate', bg: 'rgba(251,191,36,0.15)', color: '#fbbf24' }
      : { label: 'Weak', bg: 'rgba(239,68,68,0.15)', color: 'var(--error)' }
    : null;

  return (
    <div className="space-y-4">
      <div className="flex gap-3 flex-wrap items-end">
        <div>
          <label className="block text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>Strategy</label>
          <select value={strategyId} onChange={e => setStrategyId(e.target.value ? Number(e.target.value) : '')} className="px-3 py-2 text-sm outline-none cursor-pointer" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)', color: 'var(--on-surface)' }}>
            <option value="">Select strategy</option>
            {strategies.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
        <div>
          <label className="block text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>Window</label>
          <input type="number" value={window} onChange={e => setWindow(Number(e.target.value))} className="px-3 py-2 text-sm outline-none w-20 font-mono" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)', color: 'var(--on-surface)' }} />
        </div>
        <button
          onClick={() => refetch()}
          disabled={!strategyId || isLoading}
          className="px-4 py-2 text-white text-sm font-medium disabled:opacity-40 transition-colors cursor-pointer flex items-center gap-2"
          style={{ background: 'var(--primary)' }}
        >
          {isLoading && <Loader2 size={14} className="animate-spin" />}
          Compute IC
        </button>
      </div>

      {ic && (
        <div className="p-5 space-y-3" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)' }}>
          <div className="flex items-center gap-4">
            <div>
              <span className="text-xs" style={{ color: 'var(--outline)' }}>Information Coefficient</span>
              <p className="text-3xl font-bold font-mono" style={{ color: 'var(--on-surface)' }}>{icValue?.toFixed(4) ?? '—'}</p>
            </div>
            {icQuality && <span className="text-xs font-semibold px-3 py-1 rounded-full" style={{ background: icQuality.bg, color: icQuality.color }}>{icQuality.label}</span>}
          </div>
          {ic.significance != null && (
            <div className="flex items-center gap-2">
              <span className="text-xs" style={{ color: 'var(--outline)' }}>Significant:</span>
              <span className="text-sm font-medium" style={{ color: ic.significance ? 'var(--tertiary)' : 'var(--error)' }}>
                {ic.significance ? 'Yes' : 'No'}
              </span>
            </div>
          )}
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            {Object.entries(ic)
              .filter(([k]) => !['ic', 'information_coefficient', 'significance'].includes(k))
              .map(([k, v]) => (
                <div key={k} className="p-2" style={{ background: 'var(--surface-container-low)' }}>
                  <span className="text-[10px]" style={{ color: 'var(--outline)' }}>{k.replace(/_/g, ' ')}</span>
                  <p className="text-xs font-mono" style={{ color: 'var(--on-surface)' }}>{typeof v === 'number' ? v.toFixed(4) : String(v)}</p>
                </div>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}

/* ---------- Attribution ---------- */
function AttributionTab() {
  const [agentId, setAgentId] = useState<number | ''>('');
  const [symbol, setSymbol] = useState('SPY');

  const { data: agents = [] } = useQuery<TradingAgent[]>({ queryKey: ['agents'], queryFn: api.getAgents });

  const mutation = useMutation({
    mutationFn: () => api.runAttribution(agentId as number, symbol),
  });

  const result = mutation.data as Record<string, unknown> | undefined;

  return (
    <div className="space-y-4">
      <div className="flex gap-3 flex-wrap items-end">
        <div>
          <label className="block text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>Agent</label>
          <select value={agentId} onChange={e => setAgentId(e.target.value ? Number(e.target.value) : '')} className="px-3 py-2 text-sm outline-none cursor-pointer" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)', color: 'var(--on-surface)' }}>
            <option value="">Select agent</option>
            {agents.map(a => <option key={a.id} value={a.id}>{a.personaName ?? a.name}</option>)}
          </select>
        </div>
        <div>
          <label className="block text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>Symbol</label>
          <input value={symbol} onChange={e => setSymbol(e.target.value.toUpperCase())} className="px-3 py-2 text-sm outline-none w-24 font-mono" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)', color: 'var(--on-surface)' }} />
        </div>
        <button
          onClick={() => mutation.mutate()}
          disabled={!agentId || mutation.isPending}
          className="px-4 py-2 text-white text-sm font-medium disabled:opacity-40 transition-colors cursor-pointer flex items-center gap-2"
          style={{ background: 'var(--primary)' }}
        >
          {mutation.isPending && <Loader2 size={14} className="animate-spin" />}
          Run Attribution
        </button>
      </div>

      {mutation.isError && <p className="text-sm" style={{ color: 'var(--error)' }}>{(mutation.error as Error).message}</p>}

      {result && (
        <div className="p-5" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)' }}>
          <h3 className="font-semibold mb-3" style={{ color: 'var(--on-surface)' }}>P&L Decomposition</h3>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            {Object.entries(result).map(([k, v]) => (
              <div key={k} className="p-3" style={{ background: 'var(--surface-container-low)' }}>
                <span className="text-[10px]" style={{ color: 'var(--outline)' }}>{k.replace(/_/g, ' ')}</span>
                <p className="text-sm font-mono mt-0.5" style={{ color: typeof v === 'number' && v < 0 ? 'var(--error)' : 'var(--on-surface)' }}>
                  {typeof v === 'number' ? v.toFixed(4) : String(v)}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/* ---------- System Audit ---------- */
function SystemAuditTab() {
  const [symbol, setSymbol] = useState('SPY');
  const mutation = useMutation({ mutationFn: () => api.runSystemAudit(symbol) });
  const result = mutation.data as Record<string, unknown> | undefined;

  return (
    <div className="space-y-4">
      <div className="flex gap-3 items-end">
        <div>
          <label className="block text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>Symbol</label>
          <input value={symbol} onChange={e => setSymbol(e.target.value.toUpperCase())} className="px-3 py-2 text-sm outline-none w-24 font-mono" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)', color: 'var(--on-surface)' }} />
        </div>
        <button
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
          className="px-4 py-2 text-white text-sm font-medium disabled:opacity-40 transition-colors cursor-pointer flex items-center gap-2"
          style={{ background: 'var(--primary)' }}
        >
          {mutation.isPending && <Loader2 size={14} className="animate-spin" />}
          Run Audit
        </button>
      </div>

      {mutation.isError && <p className="text-sm" style={{ color: 'var(--error)' }}>{(mutation.error as Error).message}</p>}

      {result && (
        <div className="p-5 space-y-3" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)' }}>
          {Object.entries(result).map(([section, val]) => (
            <div key={section} className="p-4" style={{ background: 'var(--surface-container-low)' }}>
              <h4 className="text-sm font-medium mb-2 capitalize" style={{ color: 'var(--on-surface)' }}>{section.replace(/_/g, ' ')}</h4>
              {typeof val === 'object' && val !== null ? (
                <div className="grid grid-cols-2 gap-2">
                  {Object.entries(val as Record<string, unknown>).map(([k, v]) => (
                    <div key={k}>
                      <span className="text-[10px]" style={{ color: 'var(--outline)' }}>{k.replace(/_/g, ' ')}</span>
                      <p className="text-xs font-mono" style={{ color: 'var(--on-surface-variant)' }}>{typeof v === 'number' ? v.toFixed(4) : String(v)}</p>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-xs font-mono" style={{ color: 'var(--on-surface-variant)' }}>{String(val)}</p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ---------- Exec Monitor ---------- */
function ExecMonitorTab() {
  const [symbol, setSymbol] = useState('SPY');
  const mutation = useMutation({ mutationFn: () => api.runExecutionMonitor(symbol) });
  const result = mutation.data as Record<string, unknown> | undefined;

  const circuitLevel = result?.circuit_breaker_level as number | undefined;

  const levelColor = (level: number) =>
    level >= 3 ? 'var(--error)' : level >= 2 ? '#f97316' : level >= 1 ? '#fbbf24' : 'var(--tertiary)';

  return (
    <div className="space-y-4">
      <div className="flex gap-3 items-end">
        <div>
          <label className="block text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>Symbol</label>
          <input value={symbol} onChange={e => setSymbol(e.target.value.toUpperCase())} className="px-3 py-2 text-sm outline-none w-24 font-mono" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)', color: 'var(--on-surface)' }} />
        </div>
        <button
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
          className="px-4 py-2 text-white text-sm font-medium disabled:opacity-40 transition-colors cursor-pointer flex items-center gap-2"
          style={{ background: 'var(--primary)' }}
        >
          {mutation.isPending && <Loader2 size={14} className="animate-spin" />}
          Run Monitor
        </button>
      </div>

      {mutation.isError && <p className="text-sm" style={{ color: 'var(--error)' }}>{(mutation.error as Error).message}</p>}

      {result && (
        <div className="space-y-4">
          {/* Circuit breaker visualization */}
          {circuitLevel != null && (
            <div className="p-5" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)' }}>
              <div className="flex items-center justify-between mb-3">
                <h4 className="text-sm font-medium" style={{ color: 'var(--on-surface)' }}>Circuit Breaker Level</h4>
                <div className="flex items-center gap-1.5">
                  <AlertTriangle size={14} style={{ color: circuitLevel >= 2 ? 'var(--error)' : 'var(--outline)' }} />
                  <span className="text-sm font-mono" style={{ color: 'var(--on-surface)' }}>Level {circuitLevel}</span>
                </div>
              </div>
              <div className="flex gap-2">
                {[0, 1, 2, 3].map(level => (
                  <div
                    key={level}
                    className="flex-1 h-8 flex items-center justify-center text-xs font-bold transition-colors"
                    style={{
                      background: level <= circuitLevel ? levelColor(level) : 'var(--surface-container-low)',
                      color: level <= circuitLevel ? 'white' : 'var(--outline)',
                    }}
                  >
                    L{level}
                  </div>
                ))}
              </div>
              <div className="flex justify-between text-[10px] mt-1.5" style={{ color: 'var(--outline)' }}>
                <span>Normal</span>
                <span>Caution</span>
                <span>Warning</span>
                <span>Halt</span>
              </div>
            </div>
          )}

          {/* Other data */}
          <div className="p-5" style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)' }}>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              {Object.entries(result)
                .filter(([k]) => k !== 'circuit_breaker_level')
                .map(([k, v]) => (
                  <div key={k} className="p-3" style={{ background: 'var(--surface-container-low)' }}>
                    <span className="text-[10px]" style={{ color: 'var(--outline)' }}>{k.replace(/_/g, ' ')}</span>
                    <p className="text-xs font-mono mt-0.5" style={{ color: 'var(--on-surface)' }}>
                      {typeof v === 'number' ? v.toFixed(4) : typeof v === 'object' ? JSON.stringify(v) : String(v)}
                    </p>
                  </div>
                ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/* ---------- Main ---------- */
export default function AIIntelPage() {
  const [activeTab, setActiveTab] = useState<SubTab>('roster');

  const tabContent: Record<SubTab, React.ReactNode> = {
    'roster':       <AgentRoster />,
    'pipeline':     <PipelineTab />,
    'signal-ic':    <SignalICTab />,
    'attribution':  <AttributionTab />,
    'audit':        <SystemAuditTab />,
    'exec-monitor': <ExecMonitorTab />,
  };

  return (
    <div className="space-y-6 max-w-[1400px] mx-auto">
      <PageHeader title="AI Intelligence" subtitle="AGENT ANALYTICS // SIGNAL PROCESSING" />

      {/* Tabs */}
      <div className="flex gap-1 p-1 overflow-x-auto" style={{ background: 'var(--surface)' }}>
        {TABS.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => setActiveTab(key)}
            className={`qe-tab flex items-center gap-2 px-4 py-2 text-sm font-medium whitespace-nowrap transition-colors cursor-pointer ${
              activeTab === key ? 'active' : ''
            }`}
            style={activeTab === key
              ? { background: 'var(--surface-container-low)', color: 'var(--on-surface)' }
              : { color: 'var(--outline)' }
            }
          >
            <Icon size={16} />
            {label}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="p-5" style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}>
        {tabContent[activeTab]}
      </div>
    </div>
  );
}
