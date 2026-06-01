import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import { Play, FlaskConical, Zap, TrendingUp, Activity, BarChart3 } from 'lucide-react';
import type { TradingAgentResponse, MultiTFBacktestResult } from '@/types';

function fmtUsd(n: number | null | undefined) {
  if (n == null || isNaN(n)) return '\u2014';
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 2 }).format(n);
}

function fmtPct(n: number | null | undefined) {
  if (n == null || isNaN(n)) return '\u2014';
  return `${n >= 0 ? '+' : ''}${n.toFixed(2)}%`;
}

function Skeleton({ height = 20, style }: { height?: number | string; style?: React.CSSProperties }) {
  return <div className="skeleton" style={{ width: '100%', height, borderRadius: 4, ...style }} />;
}

const AGENT_COLORS: Record<string, string> = {
  'Alpha Seeker': '#00e479',
  'Risk Sentinel': '#ef4444',
  'Market Scout': '#3b82f6',
};

export default function DashboardPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [selectedAgent, setSelectedAgent] = useState<number | null>(null);
  const [backtestResults, setBacktestResults] = useState<Record<number, MultiTFBacktestResult>>({});
  const [backtesting, setBacktesting] = useState<Record<number, boolean>>({});

  const { data: agents = [], isLoading: agentsLoading } = useQuery<TradingAgentResponse[]>({
    queryKey: ['agents'],
    queryFn: () => api.getAgents(),
    refetchInterval: 10_000,
  });

  const { data: positions = [] } = useQuery({
    queryKey: ['positions'],
    queryFn: () => api.getPositions(),
    refetchInterval: 10_000,
  });

  const { data: riskStatus } = useQuery({
    queryKey: ['riskStatus'],
    queryFn: () => api.getRiskStatus(),
    refetchInterval: 10_000,
  });

  const startAgent = useMutation({
    mutationFn: (id: number) => api.startAgent(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['agents'] }); toast.success('Agent started'); },
    onError: () => toast.error('Failed to start agent'),
  });

  const runBacktest = async (agent: TradingAgentResponse) => {
    setBacktesting(prev => ({ ...prev, [agent.id]: true }));
    try {
      const result = await api.runMultiTFBacktest({
        symbol: 'BTCUSDT',
        initialCapital: 500,
        slippageBps: 5,
        startDate: '2025-03-01',
        endDate: '2025-06-01',
      });
      setBacktestResults(prev => ({ ...prev, [agent.id]: result }));
      toast.success(`Backtest complete: ${result.totalTrades} trades, ${fmtPct(result.totalReturnPct)} return`);
    } catch {
      toast.error('Backtest failed');
    } finally {
      setBacktesting(prev => ({ ...prev, [agent.id]: false }));
    }
  };

  const agentsToShow = agents.length > 0 ? agents.slice(0, 6) : [];

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto' }}>
      <PageHeader title="AI Trading Platform" subtitle="SELECT AGENT → BACKTEST → PAPER TRADE → GO LIVE" />

      {/* ── Agent Pipeline Section ── */}
      <div style={{ marginBottom: '1.5rem' }}>
        <div style={{
          fontFamily: 'var(--font-body)',
          fontSize: '0.75rem',
          fontWeight: 600,
          color: 'var(--on-surface)',
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          marginBottom: '0.75rem',
        }}>
          Trading Agents
        </div>

        {agentsLoading ? (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))', gap: '0.75rem' }}>
            {[1, 2, 3].map(i => <Skeleton key={i} height={180} />)}
          </div>
        ) : agentsToShow.length === 0 ? (
          <div className="qe-card" style={{
            textAlign: 'center',
            padding: '3rem 2rem',
            color: 'var(--outline)',
          }}>
            <Activity size={40} style={{ marginBottom: '1rem', opacity: 0.5 }} />
            <div style={{ fontFamily: 'var(--font-display)', fontSize: '1rem', marginBottom: '0.5rem', color: 'var(--on-surface)' }}>
              No Trading Agents Yet
            </div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', marginBottom: '1rem' }}>
              Create your first AI trading agent to start the pipeline
            </div>
            <button className="btn-primary" onClick={() => navigate('/backtest')}>
              Create Agent
            </button>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))', gap: '0.75rem' }}>
            {agentsToShow.map(agent => {
              const color = AGENT_COLORS[agent.name] || '#00e479';
              const result = backtestResults[agent.id];
              const isBacktesting = backtesting[agent.id];
              const isActive = agent.active;
              const isPaper = agent.lifecycleState === 'PAPER_TRADING';
              const isLive = agent.lifecycleState === 'LIVE';

              return (
                <div
                  key={agent.id}
                  className="qe-card"
                  onClick={() => setSelectedAgent(selectedAgent === agent.id ? null : agent.id)}
                  style={{
                    padding: 0,
                    cursor: 'pointer',
                    border: selectedAgent === agent.id ? `1px solid ${color}` : '1px solid var(--outline-variant)',
                    transition: 'border-color 200ms',
                  }}
                >
                  {/* Agent Header */}
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.75rem',
                    padding: '1rem 1.25rem',
                    background: `linear-gradient(135deg, ${color}15, transparent)`,
                  }}>
                    <div style={{
                      width: 40,
                      height: 40,
                      borderRadius: 8,
                      background: color,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontFamily: 'var(--font-display)',
                      fontWeight: 700,
                      fontSize: '0.875rem',
                      color: '#0d1117',
                      flexShrink: 0,
                    }}>
                      {agent.personaInitials || agent.name.charAt(0)}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{
                        fontFamily: 'var(--font-display)',
                        fontSize: '0.9375rem',
                        fontWeight: 600,
                        color: 'var(--on-surface)',
                      }}>
                        {agent.name}
                      </div>
                      <div style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: '0.625rem',
                        color: 'var(--outline)',
                        marginTop: 2,
                        textTransform: 'uppercase',
                        letterSpacing: '0.05em',
                      }}>
                        {agent.agentRole?.replace(/_/g, ' ')} · {agent.lifecycleState}
                      </div>
                    </div>
                    <div style={{
                      width: 8,
                      height: 8,
                      borderRadius: '50%',
                      background: isLive ? '#00e479' : isPaper ? '#fbbf24' : isActive ? '#3b82f6' : 'var(--outline)',
                      flexShrink: 0,
                    }} />
                  </div>

                  {/* Agent Action Buttons */}
                  <div style={{
                    display: 'flex',
                    gap: '0.5rem',
                    padding: '0.75rem 1.25rem',
                    borderTop: '1px solid var(--outline-variant)',
                  }}>
                    <button
                      className="btn-primary"
                      style={{ flex: 1, padding: '0.5rem 0.75rem', fontSize: '0.6875rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4 }}
                      onClick={(e) => { e.stopPropagation(); runBacktest(agent); }}
                      disabled={isBacktesting}
                    >
                      <FlaskConical size={12} />
                      {isBacktesting ? 'Running...' : 'Backtest'}
                    </button>
                    <button
                      className="btn-secondary"
                      style={{ flex: 1, padding: '0.5rem 0.75rem', fontSize: '0.6875rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4 }}
                      onClick={(e) => { e.stopPropagation(); startAgent.mutate(agent.id); }}
                    >
                      <Play size={12} />
                      Paper Trade
                    </button>
                    <button
                      className="btn-secondary"
                      style={{ flex: 1, padding: '0.5rem 0.75rem', fontSize: '0.6875rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4 }}
                      onClick={(e) => { e.stopPropagation(); navigate('/trade'); }}
                    >
                      <Zap size={12} />
                      Go Live
                    </button>
                  </div>

                  {/* Backtest Results (shown when expanded) */}
                  {selectedAgent === agent.id && result && (
                    <div style={{
                      padding: '0.75rem 1.25rem',
                      borderTop: '1px solid var(--outline-variant)',
                      background: 'rgba(0,0,0,0.2)',
                    }}>
                      <div style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: '0.625rem',
                        color: 'var(--outline)',
                        textTransform: 'uppercase',
                        marginBottom: '0.5rem',
                      }}>
                        Backtest Results
                      </div>
                      <div style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(4, 1fr)',
                        gap: '0.5rem',
                      }}>
                        <MiniStat label="Return" value={fmtPct(result.totalReturnPct)} color={result.totalReturnPct >= 0 ? '#00e479' : 'var(--error)'} />
                        <MiniStat label="Win Rate" value={`${result.winRate.toFixed(1)}%`} color="#00e479" />
                        <MiniStat label="Trades" value={String(result.totalTrades)} color="var(--primary)" />
                        <MiniStat label="Max DD" value={`${result.maxDrawdownPct.toFixed(1)}%`} color="var(--error)" />
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* ── Quick Stats Bar ── */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(4, 1fr)',
        gap: '0.5rem',
        marginBottom: '1.5rem',
      }}>
        <QuickStat
          icon={<BarChart3 size={14} />}
          label="Agents"
          value={String(agents.length)}
          color="var(--primary)"
        />
        <QuickStat
          icon={<Activity size={14} />}
          label="Active"
          value={String(agents.filter(a => a.active).length)}
          color="#00e479"
        />
        <QuickStat
          icon={<TrendingUp size={14} />}
          label="Positions"
          value={String(positions.length)}
          color="#3b82f6"
        />
        <QuickStat
          icon={<Zap size={14} />}
          label="Kill Switch"
          value={riskStatus?.killSwitchActive ? 'ON' : 'OFF'}
          color={riskStatus?.killSwitchActive ? 'var(--error)' : '#00e479'}
        />
      </div>

      {/* ── Pipeline Flow Guide ── */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '1rem',
        padding: '1.25rem',
        background: 'var(--surface-container-low)',
        borderRadius: 8,
        border: '1px solid var(--outline-variant)',
        flexWrap: 'wrap',
      }}>
        {[
          { step: 1, label: 'Select Agent', desc: 'Choose an AI trading agent above', icon: <Activity size={14} /> },
          { step: 2, label: 'Backtest', desc: 'Validate strategy on historical data', icon: <FlaskConical size={14} /> },
          { step: 3, label: 'Paper Trade', desc: 'Simulate with virtual money', icon: <Play size={14} /> },
          { step: 4, label: 'Go Live', desc: 'Execute on real markets', icon: <Zap size={14} /> },
        ].map((s, i) => (
          <div key={s.step} style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            fontFamily: 'var(--font-mono)',
          }}>
            <div style={{
              width: 28,
              height: 28,
              borderRadius: '50%',
              background: 'var(--primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '0.75rem',
              fontWeight: 700,
              color: '#0d1117',
              flexShrink: 0,
            }}>
              {s.step}
            </div>
            <div>
              <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--on-surface)' }}>{s.label}</div>
              <div style={{ fontSize: '0.625rem', color: 'var(--outline)' }}>{s.desc}</div>
            </div>
            {i < 3 && (
              <div style={{ color: 'var(--outline)', fontSize: '1.25rem', margin: '0 0.25rem' }}>→</div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

/* ─── Mini Stat for backtest results ─── */
function MiniStat({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div style={{
        fontFamily: 'var(--font-mono)',
        fontSize: '0.5625rem',
        color: 'var(--outline)',
        textTransform: 'uppercase',
        marginBottom: 2,
      }}>
        {label}
      </div>
      <div style={{
        fontFamily: 'var(--font-mono)',
        fontSize: '0.875rem',
        fontWeight: 700,
        color,
      }}>
        {value}
      </div>
    </div>
  );
}

/* ─── Quick Stat ─── */
function QuickStat({ icon, label, value, color }: { icon: React.ReactNode; label: string; value: string; color: string }) {
  return (
    <div style={{
      padding: '0.75rem 1rem',
      background: '#1a2235',
      borderLeft: `2px solid ${color}`,
      display: 'flex',
      alignItems: 'center',
      gap: '0.5rem',
    }}>
      <div style={{ color, flexShrink: 0 }}>{icon}</div>
      <div style={{ minWidth: 0 }}>
        <div style={{
          fontFamily: 'var(--font-mono)',
          fontSize: '0.5625rem',
          color: 'var(--outline)',
          textTransform: 'uppercase',
        }}>
          {label}
        </div>
        <div style={{
          fontFamily: 'var(--font-mono)',
          fontSize: '1.125rem',
          fontWeight: 700,
          color,
        }}>
          {value}
        </div>
      </div>
    </div>
  );
}
