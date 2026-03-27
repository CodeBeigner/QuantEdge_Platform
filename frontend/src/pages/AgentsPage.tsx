import { useState, useRef, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { PageHeader } from '@/components/ui/PageHeader';
import { MaterialIcon } from '@/components/ui/MaterialIcon';
import { api } from '@/services/api';
import type { TradingAgent, AgentRole, AgentLifecycleState, ChatMessage, PipelineResult } from '@/types';

const ROLES: AgentRole[] = [
  'QUANT_RESEARCHER', 'BIAS_AUDITOR', 'RISK_ANALYST', 'PORTFOLIO_CONSTRUCTOR',
  'PSYCHOLOGY_ENFORCER', 'PERFORMANCE_ATTRIBUTOR', 'MARKET_REGIME_ANALYST',
  'EXECUTION_OPTIMIZER', 'HFT_SYSTEMS_ENGINEER', 'EXECUTION_MONITOR',
];

const STATUS_STYLES: Record<string, { dot: string; label: string }> = {
  idle:        { dot: 'bg-[var(--outline)]', label: 'Idle' },
  researching: { dot: 'bg-[var(--primary)] animate-pulse', label: 'Researching' },
  trading:     { dot: 'bg-[var(--tertiary)] animate-pulse', label: 'Trading' },
  alert:       { dot: 'bg-[var(--error)] animate-pulse', label: 'Alert' },
};

const LIFECYCLE_COLORS: Record<AgentLifecycleState, string> = {
  CREATED:        'bg-[var(--outline)]/20 text-[var(--on-surface-variant)]',
  CONFIGURING:    'bg-[var(--primary)]/20 text-[var(--primary)]',
  PAPER_TRADING:  'bg-[#fbbf24]/20 text-[#fbbf24]',
  LIVE:           'bg-[var(--tertiary)]/20 text-[var(--tertiary)]',
  PAUSED:         'bg-[var(--primary)]/20 text-[var(--primary)]',
  HALTED:         'bg-[var(--error)]/20 text-[var(--error)]',
  DECOMMISSIONED: 'bg-[var(--outline)]/20 text-[var(--outline)]',
};

function deriveStatus(agent: TradingAgent): string {
  if (!agent.active) return 'idle';
  if ((agent.lastConfidence ?? 0) > 0.8) return 'trading';
  if (agent.lastRunAt) return 'researching';
  return 'idle';
}

/* ---------- Agent Card ---------- */
function AgentCard({
  agent,
  onTalk,
  onPipeline,
  onToggle,
}: {
  agent: TradingAgent;
  onTalk: () => void;
  onPipeline: () => void;
  onToggle: () => void;
}) {
  const status = deriveStatus(agent);
  const s = STATUS_STYLES[status];
  const successRate = agent.totalExecutions > 0
    ? ((agent.successfulExecutions / agent.totalExecutions) * 100).toFixed(1)
    : '—';
  const lifecycle = agent.lifecycleState ?? 'CREATED';

  return (
    <div className="agent-card border border-[var(--outline-variant)] overflow-hidden hover:border-[var(--primary)]/50 transition-colors">
      <div className="h-1.5" style={{ background: agent.personaColor ?? 'var(--primary)' }} />
      <div className="p-5 space-y-4">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div
              className="w-10 h-10 flex items-center justify-center text-sm font-bold text-white shrink-0"
              style={{ background: agent.personaColor ?? 'var(--primary)' }}
            >
              {agent.personaInitials ?? agent.name.slice(0, 2).toUpperCase()}
            </div>
            <div>
              <p className="text-[var(--on-surface)] font-medium leading-tight">
                {agent.personaName ?? agent.name}
              </p>
              <span className="text-xs px-2 py-0.5 rounded-full bg-[var(--primary)]/15 text-[var(--primary)] mt-1 inline-block">
                {agent.agentRole.replace(/_/g, ' ')}
              </span>
            </div>
          </div>
          <div className="flex items-center gap-1.5">
            <span className={`w-2 h-2 rounded-full ${s.dot}`} />
            <span className="text-xs text-[var(--outline)]">{s.label}</span>
          </div>
        </div>

        {/* Lifecycle badge */}
        <div>
          <span className={`text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full ${LIFECYCLE_COLORS[lifecycle]}`}>
            {lifecycle.replace(/_/g, ' ')}
          </span>
        </div>

        {/* Confidence bar */}
        <div>
          <div className="flex justify-between text-xs text-[var(--on-surface-variant)] mb-1">
            <span>Confidence</span>
            <span>{agent.lastConfidence != null ? `${(agent.lastConfidence * 100).toFixed(0)}%` : '—'}</span>
          </div>
          <div className="h-1.5 bg-[var(--surface)] rounded-full overflow-hidden">
            <div
              className="h-full rounded-full bg-gradient-to-r from-[var(--primary)] to-[var(--tertiary)] transition-all"
              style={{ width: `${(agent.lastConfidence ?? 0) * 100}%` }}
            />
          </div>
        </div>

        {/* Stats */}
        <div className="flex gap-4 text-xs">
          <div>
            <span className="text-[var(--outline)]">Executions</span>
            <p className="text-[var(--on-surface)] font-mono font-medium">{agent.totalExecutions}</p>
          </div>
          <div>
            <span className="text-[var(--outline)]">Success</span>
            <p className="text-[var(--tertiary)] font-mono font-medium">{successRate}%</p>
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center gap-2 pt-1 border-t border-[var(--outline-variant)]">
          <button
            onClick={onTalk}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-[var(--surface)] text-[var(--on-surface-variant)] hover:text-[var(--on-surface)] hover:bg-[var(--outline-variant)] transition-colors cursor-pointer"
          >
            <MaterialIcon name="chat" size={14} /> Talk
          </button>
          <button
            onClick={onPipeline}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-[var(--surface)] text-[var(--on-surface-variant)] hover:text-[var(--on-surface)] hover:bg-[var(--outline-variant)] transition-colors cursor-pointer"
          >
            <MaterialIcon name="account_tree" size={14} /> Pipeline
          </button>
          <button
            onClick={onToggle}
            className={`ml-auto flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium transition-colors cursor-pointer ${
              agent.active
                ? 'bg-[var(--error)]/15 text-[var(--error)] hover:bg-[var(--error)]/25'
                : 'bg-[var(--tertiary)]/15 text-[var(--tertiary)] hover:bg-[var(--tertiary)]/25'
            }`}
          >
            {agent.active ? <><MaterialIcon name="stop" size={14} /> Stop</> : <><MaterialIcon name="play_arrow" size={14} /> Start</>}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ---------- Chat Drawer ---------- */
function ChatDrawer({
  agent,
  onClose,
}: {
  agent: TradingAgent;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const [input, setInput] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);

  const { data: messages = [] } = useQuery<ChatMessage[]>({
    queryKey: ['agent-chat', agent.id],
    queryFn: () => api.getAgentConversation(agent.id),
  });

  const sendMut = useMutation({
    mutationFn: (msg: string) => api.chatWithAgent(agent.id, msg),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['agent-chat', agent.id] }),
  });

  const clearMut = useMutation({
    mutationFn: () => api.clearAgentConversation(agent.id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['agent-chat', agent.id] }),
  });

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);

  const send = () => {
    const trimmed = input.trim();
    if (!trimmed) return;
    sendMut.mutate(trimmed);
    setInput('');
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="relative w-full max-w-md bg-[var(--surface-container-lowest)] border-l border-[var(--outline-variant)] flex flex-col animate-in">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-[var(--outline-variant)]">
          <div className="flex items-center gap-2">
            <div
              className="w-8 h-8 flex items-center justify-center text-xs font-bold text-white"
              style={{ background: agent.personaColor ?? 'var(--primary)' }}
            >
              {agent.personaInitials ?? agent.name.slice(0, 2).toUpperCase()}
            </div>
            <span className="text-[var(--on-surface)] font-medium">{agent.personaName ?? agent.name}</span>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => clearMut.mutate()} className="text-[var(--outline)] hover:text-[var(--error)] cursor-pointer p-1" title="Clear conversation">
              <MaterialIcon name="delete" size={16} />
            </button>
            <button onClick={onClose} className="text-[var(--outline)] hover:text-[var(--on-surface)] cursor-pointer p-1">
              <MaterialIcon name="close" size={18} />
            </button>
          </div>
        </div>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {messages.length === 0 && (
            <p className="text-sm text-[var(--outline)] text-center pt-8">No messages yet. Start a conversation!</p>
          )}
          {messages.map((m, i) => (
            <div key={i} className={`flex ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
              <div
                className={`max-w-[80%] px-3.5 py-2.5 text-sm leading-relaxed ${
                  m.role === 'user'
                    ? 'chat-bubble-user text-[var(--on-surface)]'
                    : 'chat-bubble-agent text-[var(--on-surface-variant)]'
                }`}
              >
                {m.content}
              </div>
            </div>
          ))}
          {sendMut.isPending && (
            <div className="flex justify-start">
              <div className="chat-bubble-agent px-4 py-3 text-sm text-[var(--outline)] flex items-center gap-2">
                <Loader2 size={14} className="animate-spin" /> Thinking...
              </div>
            </div>
          )}
          <div ref={bottomRef} />
        </div>

        {/* Input */}
        <div className="p-4 border-t border-[var(--outline-variant)]">
          <div className="flex gap-2">
            <input
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && !e.shiftKey && send()}
              placeholder="Message agent..."
              className="flex-1 bg-[var(--surface)] border border-[var(--outline-variant)] px-3 py-2 text-sm text-[var(--on-surface)] placeholder-[var(--outline)] outline-none focus:border-[var(--primary)]"
            />
            <button
              onClick={send}
              disabled={sendMut.isPending || !input.trim()}
              className="px-3 py-2 bg-[var(--primary)] text-white hover:bg-[var(--primary)]/80 disabled:opacity-40 transition-colors cursor-pointer"
            >
              <MaterialIcon name="send" size={16} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ---------- Pipeline Panel ---------- */
function PipelinePanel({
  agent,
  onClose,
}: {
  agent: TradingAgent;
  onClose: () => void;
}) {
  const [symbol, setSymbol] = useState('SPY');
  const [expanded, setExpanded] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => api.runAgentPipeline(agent.id, symbol),
  });

  const result = mutation.data as PipelineResult | undefined;
  const stages = result
    ? [
        { key: 'regime_analysis', label: 'Regime Analysis', data: result.regime_analysis },
        { key: 'research_decision', label: 'Research Decision', data: result.research_decision },
        { key: 'bias_audit', label: 'Bias Audit', data: result.bias_audit },
        { key: 'risk_assessment', label: 'Risk Assessment', data: result.risk_assessment },
        { key: 'psychology_check', label: 'Psychology Check', data: result.psychology_check },
      ]
    : [];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="relative bg-[var(--surface-container-lowest)] border border-[var(--outline-variant)] w-full max-w-lg max-h-[80vh] overflow-y-auto">
        <div className="flex items-center justify-between p-5 border-b border-[var(--outline-variant)]">
          <h3 className="text-[var(--on-surface)] font-semibold">Pipeline — {agent.personaName ?? agent.name}</h3>
          <button onClick={onClose} className="text-[var(--outline)] hover:text-[var(--on-surface)] cursor-pointer"><MaterialIcon name="close" size={18} /></button>
        </div>
        <div className="p-5 space-y-4">
          <div className="flex gap-2">
            <input
              value={symbol}
              onChange={e => setSymbol(e.target.value.toUpperCase())}
              placeholder="Symbol"
              className="flex-1 bg-[var(--surface)] border border-[var(--outline-variant)] px-3 py-2 text-sm text-[var(--on-surface)] placeholder-[var(--outline)] outline-none focus:border-[var(--primary)]"
            />
            <button
              onClick={() => mutation.mutate()}
              disabled={mutation.isPending}
              className="px-4 py-2 bg-[var(--primary)] text-white text-sm font-medium hover:bg-[var(--primary)]/80 disabled:opacity-40 transition-colors cursor-pointer flex items-center gap-2"
            >
              {mutation.isPending && <Loader2 size={14} className="animate-spin" />}
              Run
            </button>
          </div>

          {result && (
            <div className="space-y-2">
              <p className="text-xs text-[var(--outline)]">
                Status: <span className="text-[var(--tertiary)]">{result.pipeline_status ?? 'complete'}</span>
                {result.final_decision && (
                  <> · Decision: <span className="text-[#fbbf24] font-medium">{result.final_decision}</span></>
                )}
              </p>
              {stages.map(st => (
                <div key={st.key} className="border border-[var(--outline-variant)] overflow-hidden">
                  <button
                    onClick={() => setExpanded(expanded === st.key ? null : st.key)}
                    className="flex items-center justify-between w-full px-4 py-2.5 text-sm text-[var(--on-surface)] hover:bg-[var(--surface-container-low)] transition-colors cursor-pointer"
                  >
                    <span>{st.label}</span>
                    <MaterialIcon name="expand_more" size={16} className={`text-[var(--outline)] transition-transform ${expanded === st.key ? 'rotate-180' : ''}`} />
                  </button>
                  {expanded === st.key && st.data && (
                    <div className="px-4 py-3 border-t border-[var(--outline-variant)] text-xs text-[var(--on-surface-variant)] font-mono whitespace-pre-wrap break-all">
                      {JSON.stringify(st.data, null, 2)}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}

          {mutation.isError && (
            <p className="text-sm text-[var(--error)]">Error: {(mutation.error as Error).message}</p>
          )}
        </div>
      </div>
    </div>
  );
}

/* ---------- Create Agent Form ---------- */
function CreateAgentForm({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const [name, setName] = useState('');
  const [role, setRole] = useState<AgentRole>('QUANT_RESEARCHER');
  const [cron, setCron] = useState('0 */5 * * * *');

  const { data: strategies = [] } = useQuery({
    queryKey: ['strategies'],
    queryFn: api.getStrategies,
  });

  const [strategyId, setStrategyId] = useState<number | ''>('');

  const createMut = useMutation({
    mutationFn: () =>
      api.createAgent({
        name,
        strategyId: strategyId as number,
        cronExpression: cron,
        agentRole: role,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['agents'] });
      onClose();
    },
  });

  return (
    <div className="bg-[var(--surface-container-low)] border border-[var(--outline-variant)] p-5 space-y-4 animate-in">
      <div className="flex items-center justify-between">
        <h3 className="text-[var(--on-surface)] font-semibold">Create Agent</h3>
        <button onClick={onClose} className="text-[var(--outline)] hover:text-[var(--on-surface)] cursor-pointer"><MaterialIcon name="close" size={18} /></button>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div>
          <label className="block text-xs text-[var(--on-surface-variant)] mb-1">Agent Name</label>
          <input
            value={name}
            onChange={e => setName(e.target.value)}
            className="w-full bg-[var(--surface)] border border-[var(--outline-variant)] px-3 py-2 text-sm text-[var(--on-surface)] placeholder-[var(--outline)] outline-none focus:border-[var(--primary)]"
            placeholder="Alpha Seeker"
          />
        </div>
        <div>
          <label className="block text-xs text-[var(--on-surface-variant)] mb-1">Strategy</label>
          <select
            value={strategyId}
            onChange={e => setStrategyId(e.target.value ? Number(e.target.value) : '')}
            className="w-full bg-[var(--surface)] border border-[var(--outline-variant)] px-3 py-2 text-sm text-[var(--on-surface)] outline-none focus:border-[var(--primary)] cursor-pointer"
          >
            <option value="">Select strategy</option>
            {strategies.map(s => (
              <option key={s.id} value={s.id}>{s.name} ({s.modelType})</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs text-[var(--on-surface-variant)] mb-1">Cron Expression</label>
          <input
            value={cron}
            onChange={e => setCron(e.target.value)}
            className="w-full bg-[var(--surface)] border border-[var(--outline-variant)] px-3 py-2 text-sm text-[var(--on-surface)] font-mono placeholder-[var(--outline)] outline-none focus:border-[var(--primary)]"
          />
        </div>
        <div>
          <label className="block text-xs text-[var(--on-surface-variant)] mb-1">Agent Role</label>
          <select
            value={role}
            onChange={e => setRole(e.target.value as AgentRole)}
            className="w-full bg-[var(--surface)] border border-[var(--outline-variant)] px-3 py-2 text-sm text-[var(--on-surface)] outline-none focus:border-[var(--primary)] cursor-pointer"
          >
            {ROLES.map(r => (
              <option key={r} value={r}>{r.replace(/_/g, ' ')}</option>
            ))}
          </select>
        </div>
      </div>

      <button
        onClick={() => createMut.mutate()}
        disabled={!name || !strategyId || createMut.isPending}
        className="w-full py-2.5 bg-gradient-to-r from-[var(--tertiary)] to-[var(--primary)] text-[var(--surface)] font-semibold text-sm hover:opacity-90 disabled:opacity-40 transition-opacity cursor-pointer flex items-center justify-center gap-2"
      >
        {createMut.isPending && <Loader2 size={14} className="animate-spin" />}
        Create Agent
      </button>
      {createMut.isError && (
        <p className="text-xs text-[var(--error)]">{(createMut.error as Error).message}</p>
      )}
    </div>
  );
}

/* ---------- Main Page ---------- */
export default function AgentsPage() {
  const qc = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [chatAgent, setChatAgent] = useState<TradingAgent | null>(null);
  const [pipelineAgent, setPipelineAgent] = useState<TradingAgent | null>(null);

  const { data: agents = [], isLoading } = useQuery<TradingAgent[]>({
    queryKey: ['agents'],
    queryFn: api.getAgents,
    refetchInterval: 10_000,
  });

  const startMut = useMutation({
    mutationFn: (id: number) => api.startAgent(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['agents'] }),
  });

  const stopMut = useMutation({
    mutationFn: (id: number) => api.stopAgent(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['agents'] }),
  });

  return (
    <div className="space-y-6 max-w-[1600px] mx-auto">
      {/* Header */}
      <PageHeader title="Agent Interface" subtitle="NEURAL_LINK // DIRECT COMMUNICATION">
        <button
          onClick={() => setShowCreate(v => !v)}
          className="flex items-center gap-2 px-4 py-2 bg-[var(--primary)] text-white text-sm font-medium hover:bg-[var(--primary)]/80 transition-colors cursor-pointer"
        >
          <MaterialIcon name="add" size={16} /> New Agent
        </button>
      </PageHeader>

      {showCreate && <CreateAgentForm onClose={() => setShowCreate(false)} />}

      {/* Agent grid */}
      {isLoading ? (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-64 skeleton" />
          ))}
        </div>
      ) : agents.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-[var(--outline)]">
          <MaterialIcon name="smart_toy" size={48} className="mb-3 opacity-40" />
          <p className="text-lg">No agents yet</p>
          <p className="text-sm mt-1">Create your first trading agent to get started</p>
        </div>
      ) : (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] gap-4">
          {agents.map(agent => (
            <AgentCard
              key={agent.id}
              agent={agent}
              onTalk={() => setChatAgent(agent)}
              onPipeline={() => setPipelineAgent(agent)}
              onToggle={() => (agent.active ? stopMut : startMut).mutate(agent.id)}
            />
          ))}
        </div>
      )}

      {/* Chat drawer */}
      {chatAgent && <ChatDrawer agent={chatAgent} onClose={() => setChatAgent(null)} />}

      {/* Pipeline panel */}
      {pipelineAgent && <PipelinePanel agent={pipelineAgent} onClose={() => setPipelineAgent(null)} />}
    </div>
  );
}
