import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
  AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid,
} from 'recharts';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import { KpiCard } from '@/components/ui/KpiCard';
import { MaterialIcon } from '@/components/ui/MaterialIcon';
import type { TradingAgent } from '@/types';

function deriveStatus(agent: TradingAgent): 'live' | 'warning' | 'idle' {
  if (!agent.active) return 'idle';
  if ((agent.lastConfidence ?? 0) > 0.8) return 'live';
  if (agent.lastRunAt) return 'warning';
  return 'idle';
}

const STATUS_LABELS: Record<string, string> = { live: 'ACTIVE', warning: 'RESEARCHING', idle: 'IDLE' };

export default function DashboardPage() {
  const navigate = useNavigate();

  const { data: health } = useQuery({
    queryKey: ['health'],
    queryFn: () => api.getHealth(),
    staleTime: 30_000,
  });

  const { data: symbols = [], isLoading: symbolsLoading } = useQuery({
    queryKey: ['symbols'],
    queryFn: () => api.getSymbols(),
  });

  const { data: portfolio } = useQuery({
    queryKey: ['portfolio'],
    queryFn: () => api.getPortfolio(),
    retry: false,
  });

  const { data: strategies = [] } = useQuery({
    queryKey: ['strategies'],
    queryFn: () => api.getStrategies(),
    retry: false,
  });

  const { data: agents = [] } = useQuery<TradingAgent[]>({
    queryKey: ['agents'],
    queryFn: () => api.getAgents(),
    retry: false,
  });

  const statusRaw =
    typeof health === 'object' && health !== null && 'status' in health
      ? String((health as { status?: string }).status)
      : '';
  const isUp = statusRaw.toUpperCase() === 'UP';

  const activeStrategies = strategies.filter((s) => s.active).length;
  const activeAgents = agents.filter((a) => a.active).length;

  // Generate sparkline data from portfolio or synthetic
  const sparkAum = useMemo(() => Array.from({ length: 20 }, (_, i) => {
    const base = portfolio?.totalValue ?? 100000;
    return base * (1 + (Math.sin(i * 0.3) * 0.02));
  }), [portfolio]);

  const sparkPnl = useMemo(() => Array.from({ length: 20 }, () =>
    (portfolio?.dayPnl ?? 0) * (0.5 + Math.random())
  ), [portfolio]);

  const formatMoney = (n: number) =>
    new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(n);

  return (
    <div style={{ maxWidth: 1400, margin: '0 auto' }}>
      <PageHeader title="Trading Floor" subtitle="REAL-TIME AGENT ORCHESTRATION // LIVE SESSION" />

      {/* KPI Grid */}
      <div className="grid-4-responsive" style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1rem', marginBottom: '1.5rem' }}>
        <KpiCard
          label="Total AUM"
          value={portfolio ? formatMoney(portfolio.totalValue) : '—'}
          change={portfolio ? `+${((portfolio.dayPnl / (portfolio.totalValue || 1)) * 100).toFixed(2)}%` : undefined}
          positive={portfolio ? portfolio.dayPnl >= 0 : true}
          sparkData={sparkAum}
          delay={0}
        />
        <KpiCard
          label="Daily P&L"
          value={portfolio ? formatMoney(portfolio.dayPnl) : '—'}
          change={portfolio && portfolio.dayPnl >= 0 ? 'Positive session' : 'Negative session'}
          positive={portfolio ? portfolio.dayPnl >= 0 : true}
          sparkData={sparkPnl}
          delay={60}
        />
        <KpiCard
          label="Active Strategies"
          value={strategies.length ? String(activeStrategies) : '—'}
          change={`${strategies.length} total`}
          positive={true}
          delay={120}
        />
        <KpiCard
          label="System Status"
          value={isUp ? 'ONLINE' : 'DOWN'}
          change={`${activeAgents} agents active`}
          positive={isUp}
          delay={180}
        />
      </div>

      {/* Two column: Agents + Symbol Universe */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginBottom: '1.5rem' }}>
        {/* Active Agents */}
        <div>
          <div className="qe-card-header">
            <div className="qe-card-title">Active Agents</div>
            <div className="qe-card-meta">AUTONOMOUS NODES</div>
          </div>
          {agents.length === 0 ? (
            <div className="qe-card" style={{ textAlign: 'center', padding: '2rem', color: 'var(--outline)' }}>
              <MaterialIcon name="smart_toy" size={32} />
              <p style={{ marginTop: '0.5rem', fontSize: '0.875rem' }}>No agents deployed</p>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
              {agents.slice(0, 4).map((a, i) => {
                const status = deriveStatus(a);
                const successRate = a.totalExecutions > 0
                  ? ((a.successfulExecutions / a.totalExecutions) * 100).toFixed(0)
                  : '—';
                return (
                  <div
                    key={a.id}
                    className="agent-card animate-in"
                    style={{ animationDelay: `${i * 80}ms` }}
                    onClick={() => navigate('/agents')}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{
                        fontFamily: 'var(--font-mono)', fontSize: '0.75rem',
                        fontWeight: 600, color: 'var(--primary)', letterSpacing: '0.04em',
                      }}>
                        {a.personaName ?? a.name}
                      </span>
                      <span className={`status-dot ${status}`} />
                    </div>
                    <div style={{
                      fontFamily: 'var(--font-mono)', fontSize: '0.6875rem',
                      color: 'var(--outline)', marginTop: '0.25rem',
                    }}>
                      {a.agentRole.replace(/_/g, ' ')}
                    </div>
                    <div style={{
                      display: 'flex', gap: '1rem', marginTop: '0.75rem',
                      fontFamily: 'var(--font-mono)', fontSize: '0.6875rem', color: 'var(--outline)',
                    }}>
                      <span>STATUS <span style={{ color: 'var(--on-surface)', fontWeight: 500 }}>{STATUS_LABELS[status]}</span></span>
                      <span>WIN <span style={{ color: 'var(--tertiary)', fontWeight: 500 }}>{successRate}%</span></span>
                    </div>
                    <div className="progress-bar" style={{ marginTop: '0.75rem' }}>
                      <div className="progress-bar-fill" style={{ width: `${(a.lastConfidence ?? 0) * 100}%` }} />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Symbol Universe */}
        <div>
          <div className="qe-card-header">
            <div className="qe-card-title">Symbol Universe</div>
            <div className="qe-card-meta">{symbols.length} TRACKED</div>
          </div>
          {symbolsLoading ? (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="skeleton" style={{ height: 64 }} />
              ))}
            </div>
          ) : symbols.length === 0 ? (
            <div className="qe-card" style={{ color: 'var(--outline)', fontSize: '0.875rem' }}>
              No symbols returned from the API.
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
              {symbols.map((sym, i) => (
                <button
                  key={sym}
                  onClick={() => navigate(`/market?symbol=${encodeURIComponent(sym)}`)}
                  className="agent-card animate-in"
                  style={{ animationDelay: `${i * 50}ms`, textAlign: 'left' }}
                >
                  <div style={{
                    fontFamily: 'var(--font-mono)', fontSize: '0.6875rem',
                    color: 'var(--outline)', textTransform: 'uppercase', letterSpacing: '0.1em',
                  }}>
                    Tracked
                  </div>
                  <div style={{
                    fontFamily: 'var(--font-display)', fontSize: '1.25rem',
                    fontWeight: 600, color: 'var(--on-surface)', marginTop: '0.25rem',
                  }}>
                    {sym}
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Quick Nav Cards */}
      <div className="qe-card-header">
        <div className="qe-card-title">Quick Actions</div>
      </div>
      <div className="grid-4-responsive" style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '0.75rem' }}>
        {[
          { icon: 'smart_toy', label: 'Agents', path: '/agents', count: `${activeAgents} active` },
          { icon: 'psychology', label: 'Strategies', path: '/strategies', count: `${activeStrategies} running` },
          { icon: 'shield', label: 'Risk', path: '/risk', count: 'Monitor' },
          { icon: 'model_training', label: 'ML Models', path: '/ml', count: 'Train & predict' },
        ].map((item, i) => (
          <button
            key={item.path}
            onClick={() => navigate(item.path)}
            className="agent-card animate-in"
            style={{ animationDelay: `${i * 60}ms`, textAlign: 'left' }}
          >
            <MaterialIcon name={item.icon} size={24} style={{ color: 'var(--primary)' } as React.CSSProperties} />
            <div style={{
              fontFamily: 'var(--font-display)', fontSize: '1rem',
              fontWeight: 600, color: 'var(--on-surface)', marginTop: '0.5rem',
            }}>
              {item.label}
            </div>
            <div style={{
              fontFamily: 'var(--font-mono)', fontSize: '0.6875rem',
              color: 'var(--outline)', marginTop: '0.25rem',
            }}>
              {item.count}
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}
