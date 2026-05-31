import { useEffect, useState, useCallback } from 'react';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import { MaterialIcon } from '@/components/ui/MaterialIcon';

interface RiskStatus {
  kill_switch_active: boolean;
  kill_switch_healthy: boolean;
  live_trading: boolean;
  config: Record<string, number>;
}

interface PortfolioState {
  cash: number;
  equity: number;
  buying_power: number;
  total_exposure: number;
  position_count: number;
  positions: Array<{
    asset: string;
    size: number;
    entry_price: number;
    current_price: number;
    unrealized_pnl: number;
  }>;
}

interface SignalEntry {
  asset: string;
  direction: string;
  probability: number;
  confidence: number;
  passed: boolean;
  failures: string[];
}

export default function RiskDashboardPage() {
  const [status, setStatus] = useState<RiskStatus | null>(null);
  const [portfolio, setPortfolio] = useState<PortfolioState | null>(null);
  const [signals, setSignals] = useState<SignalEntry[]>([]);
  const [opportunities, setOpportunities] = useState<Array<{
    asset: string; asset_type: string; price: number; change_24h_pct: number;
    volume_24h: number; signal_strength: number; reasons: string[];
  }>>([]);
  const [budget, setBudget] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const [s, p, sig, opps, b] = await Promise.all([
        api.getRiskStatus(),
        api.getRiskPortfolio(),
        api.getRiskSignals(),
        api.getRiskOpportunities(),
        api.getBudgetStatus(),
      ]);
      setStatus(s as unknown as RiskStatus);
      setPortfolio(p as unknown as PortfolioState);
      setSignals((sig as unknown as { signals: SignalEntry[] }).signals ?? []);
      setOpportunities((opps as unknown as Record<string, unknown>)?.opportunities as unknown as Array<{
        asset: string; asset_type: string; price: number; change_24h_pct: number;
        volume_24h: number; signal_strength: number; reasons: string[];
      }> ?? []);
      setBudget(b as unknown as Record<string, unknown>);
      setError(null);
    } catch (err) {
      setError('Risk API unreachable — start with: python3 services/api.py');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
    const iv = setInterval(fetchData, 3000);
    return () => clearInterval(iv);
  }, [fetchData]);

  const modeBanner = status?.live_trading
    ? { text: 'LIVE TRADING ACTIVE', color: '#c9522e', bg: 'rgba(201,82,46,0.12)' }
    : { text: 'PAPER TRADING MODE', color: '#478be6', bg: 'rgba(71,139,230,0.08)' };

  return (
    <div style={{ padding: '1.5rem', maxWidth: 1200, margin: '0 auto' }}>
      <PageHeader title="Risk Dashboard" subtitle="Pillar C — Decision & Risk Layer" />

      {/* Mode Banner */}
      {status && (
        <div style={{
          padding: '0.5rem 1rem', borderRadius: 8, marginBottom: '1.5rem',
          background: modeBanner.bg, color: modeBanner.color,
          fontSize: '0.75rem', fontWeight: 600, textTransform: 'uppercase',
          letterSpacing: '0.08em', display: 'flex', alignItems: 'center', gap: '0.5rem',
        }}>
          <MaterialIcon name={status.live_trading ? 'warning' : 'science'} size={16} />
          {modeBanner.text}
        </div>
      )}

      {budget && (
        <div style={{
          display: 'flex', gap: '1rem', marginBottom: '1rem',
          fontSize: '0.75rem', color: 'var(--outline)',
          background: 'var(--surface-container-low)', borderRadius: 8, padding: '0.5rem 1rem',
        }}>
          <span>💰 LLM Budget: <strong style={{color: 'var(--on-surface)'}}>${(budget.total_spent_usd as number)?.toFixed(4)}</strong> / <strong>${(budget.daily_budget_usd as number)?.toFixed(2)}</strong></span>
          <span style={{color: 'var(--outline)'}}>|</span>
          <span>Remaining: <strong style={{color: (budget.remaining_usd as number) > 0 ? '#00e479' : '#c9522e'}}>${(budget.remaining_usd as number)?.toFixed(4)}</strong></span>
        </div>
      )}

      {error && (
        <div style={{
          padding: '1rem', borderRadius: 8, marginBottom: '1.5rem',
          background: 'rgba(201,82,46,0.1)', color: '#c9522e',
          fontSize: '0.8125rem',
        }}>
          {error}
        </div>
      )}

      {loading && (
        <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--outline)' }}>
          Loading risk data...
        </div>
      )}

      {status && portfolio && (
        <>
          {/* Top Row: Kill Switch + Portfolio */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1rem' }}>
            {/* Kill Switch */}
            <div style={{
              background: 'var(--surface-container-low)', borderRadius: 12, padding: '1.25rem',
              border: status.kill_switch_active ? '1px solid #c9522e' : '1px solid transparent',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.75rem' }}>
                <MaterialIcon
                  name={status.kill_switch_active ? 'dangerous' : 'check_circle'}
                  size={24}
                  style={{ color: status.kill_switch_active ? '#c9522e' : '#00e479' }}
                />
                <h3 style={{ fontSize: '1rem', fontWeight: 600, margin: 0 }}>
                  Kill Switch: {status.kill_switch_active ? 'ACTIVE — HALTED' : 'Inactive'}
                </h3>
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--outline)' }}>
                <div>Flag directory: {status.config?.['kill_switch_dir'] ?? './flags'}</div>
                <div>Health check: {status.kill_switch_healthy ? 'OK' : 'FAILED'}</div>
              </div>
            </div>

            {/* Portfolio Snapshot */}
            <div style={{ background: 'var(--surface-container-low)', borderRadius: 12, padding: '1.25rem' }}>
              <h3 style={{ fontSize: '1rem', fontWeight: 600, margin: '0 0 0.75rem 0' }}>Portfolio</h3>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem', fontSize: '0.8125rem' }}>
                {[
                  ['Equity', `$${portfolio.equity.toLocaleString()}`],
                  ['Cash', `$${portfolio.cash.toLocaleString()}`],
                  ['Exposure', `$${portfolio.total_exposure.toLocaleString()}`],
                  ['Positions', `${portfolio.position_count}`],
                  ['Buying Power', `$${portfolio.buying_power.toLocaleString()}`],
                ].map(([label, value]) => (
                  <div key={label} style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ color: 'var(--outline)' }}>{label}</span>
                    <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 500 }}>{value}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Bottom Row: Config + Signal Feed */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '1rem' }}>
            {/* Risk Config */}
            <div style={{ background: 'var(--surface-container-low)', borderRadius: 12, padding: '1.25rem' }}>
              <h3 style={{ fontSize: '1rem', fontWeight: 600, margin: '0 0 0.75rem 0' }}>Risk Parameters</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.375rem', fontSize: '0.75rem' }}>
                {[
                  ['Min Confidence', `${(status.config.min_confidence * 100).toFixed(0)}%`],
                  ['Kelly Fraction', `${(status.config.kelly_fraction * 100).toFixed(0)}%`],
                  ['Max Position', `${(status.config.max_position_pct * 100).toFixed(0)}% NAV`],
                  ['Max Exposure', `${status.config.max_total_exposure.toFixed(1)}x NAV`],
                  ['Max Drawdown', `${(status.config.max_drawdown * 100).toFixed(0)}%`],
                  ['Daily Loss Limit', `$${status.config.daily_loss_limit.toLocaleString()}`],
                  ['Daily VaR Limit', `$${status.config.daily_var_limit.toLocaleString()}`],
                  ['Slippage Threshold', `${(status.config.slippage_threshold * 100).toFixed(1)}%`],
                ].map(([label, value]) => (
                  <div key={label} style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ color: 'var(--outline)' }}>{label}</span>
                    <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 500 }}>{value}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Signal Feed */}
            <div style={{ background: 'var(--surface-container-low)', borderRadius: 12, padding: '1.25rem' }}>
              <h3 style={{ fontSize: '1rem', fontWeight: 600, margin: '0 0 0.75rem 0' }}>Signal Feed</h3>
              {signals.length === 0 ? (
                <div style={{ color: 'var(--outline)', fontSize: '0.8125rem', textAlign: 'center', padding: '2rem' }}>
                  No signals yet. Signals appear when Pillar A (Market Intelligence) is active.
                </div>
              ) : (
                <div style={{ maxHeight: 300, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                  {signals.map((s, i) => (
                    <div key={i} style={{
                      padding: '0.5rem 0.75rem', borderRadius: 6, fontSize: '0.75rem',
                      background: s.passed ? 'rgba(0,228,121,0.06)' : 'rgba(201,82,46,0.06)',
                      border: `1px solid ${s.passed ? 'rgba(0,228,121,0.15)' : 'rgba(201,82,46,0.15)'}`,
                    }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.25rem' }}>
                        <span style={{ fontWeight: 600 }}>{s.asset} • {s.direction}</span>
                        <span style={{ color: s.passed ? '#00e479' : '#c9522e' }}>
                          {s.passed ? 'PASSED' : 'BLOCKED'}
                        </span>
                      </div>
                      <div style={{ color: 'var(--outline)', fontSize: '0.6875rem' }}>
                        p={s.probability.toFixed(2)} conf={s.confidence.toFixed(2)}
                        {!s.passed && s.failures.length > 0 && (
                          <span style={{ color: '#c9522e' }}> — {s.failures.join(', ')}</span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Opportunities */}
            <div style={{ background: 'var(--surface-container-low)', borderRadius: 12, padding: '1.25rem' }}>
              <h3 style={{ fontSize: '1rem', fontWeight: 600, margin: '0 0 0.75rem 0' }}>Scanner</h3>
              {opportunities.length === 0 ? (
                <div style={{ color: 'var(--outline)', fontSize: '0.8125rem', textAlign: 'center', padding: '2rem' }}>
                  No opportunities found. Check scanner configuration.
                </div>
              ) : (
                <div style={{ maxHeight: 300, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                  {opportunities.map((o, i) => (
                    <div key={i} style={{
                      padding: '0.5rem 0.75rem', borderRadius: 6, fontSize: '0.75rem',
                      background: 'rgba(71,139,230,0.06)', border: '1px solid rgba(71,139,230,0.12)',
                    }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.25rem' }}>
                        <span style={{ fontWeight: 600 }}>{o.asset}</span>
                        <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.6875rem' }}>
                          ${o.price?.toLocaleString()} • {o.change_24h_pct >= 0 ? '+' : ''}{o.change_24h_pct?.toFixed(1)}%
                        </span>
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--outline)', fontSize: '0.6875rem' }}>
                        <span>{o.asset_type} • Vol: {(o.volume_24h / 1e6).toFixed(0)}M</span>
                        <span style={{
                          color: o.signal_strength >= 0.7 ? '#00e479' : o.signal_strength >= 0.5 ? '#e6a817' : 'var(--outline)',
                          fontWeight: 600,
                        }}>
                          Score: {(o.signal_strength * 100).toFixed(0)}%
                        </span>
                      </div>
                      {o.reasons.filter(r => r).length > 0 && (
                        <div style={{ color: 'var(--outline)', fontSize: '0.625rem', marginTop: '0.25rem' }}>
                          {o.reasons.filter(r => r).join(' • ')}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
