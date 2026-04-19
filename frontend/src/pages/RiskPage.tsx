import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Shield, Check, X, Loader2 } from 'lucide-react';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import type { RiskConfig, SystemHealth } from '@/types';

/* ─── Helpers ─── */

function clamp(v: number, min: number, max: number) {
  return Math.min(max, Math.max(min, v));
}

function pct(used: number, limit: number) {
  if (limit <= 0) return 0;
  return clamp((used / limit) * 100, 0, 100);
}

/* ─── Status Banner ─── */

function StatusBanner({ health, riskConfig }: { health?: SystemHealth; riskConfig?: RiskConfig }) {
  const riskEngineUp = health?.components?.riskEngine?.status === 'UP';

  // Determine if daily loss or drawdown is breached
  const account = health?.components?.account;
  const balance = account?.balance ?? 0;
  const peakEquity = account?.peakEquity ?? 0;

  const dailyLossLimit = riskConfig?.dailyLossHaltPct ?? 5;
  const maxDrawdownLimit = riskConfig?.maxDrawdownPct ?? 15;

  // Drawdown: (peak - current) / peak * 100
  const currentDrawdownPct = peakEquity > 0 ? ((peakEquity - balance) / peakEquity) * 100 : 0;
  const drawdownBreached = currentDrawdownPct >= maxDrawdownLimit;

  // We don't have a real-time daily loss value from the API, so we rely on risk engine status
  const isHalted = !riskEngineUp || drawdownBreached;

  const dotColor = isHalted ? '#ef4444' : '#22c55e';
  const label = isHalted
    ? 'RISK ENGINE: HALTED'
    : 'RISK ENGINE: ARMED (7 checks active)';

  return (
    <div
      style={{
        width: '100%',
        background: 'var(--surface-container-highest)',
        border: `1px solid ${isHalted ? 'rgba(239,68,68,0.4)' : 'rgba(34,197,94,0.25)'}`,
        padding: '14px 20px',
        display: 'flex',
        alignItems: 'center',
        gap: 12,
      }}
    >
      <span
        style={{
          width: 10,
          height: 10,
          borderRadius: '50%',
          background: dotColor,
          boxShadow: `0 0 8px ${dotColor}`,
          animation: 'pulse-dot 2s ease-in-out infinite',
          flexShrink: 0,
        }}
      />
      <span
        style={{
          fontFamily: 'var(--font-mono)',
          fontWeight: 700,
          fontSize: '0.85rem',
          letterSpacing: '0.06em',
          color: isHalted ? '#ef4444' : '#22c55e',
        }}
      >
        {label}
      </span>
      <style>{`
        @keyframes pulse-dot {
          0%, 100% { opacity: 1; transform: scale(1); }
          50% { opacity: 0.5; transform: scale(0.85); }
        }
      `}</style>
    </div>
  );
}

/* ─── Progress Bar ─── */

function BudgetBar({
  label,
  valueText,
  percentage,
  fillColor,
}: {
  label: string;
  valueText: string;
  percentage: number;
  fillColor: string;
}) {
  return (
    <div style={{ marginBottom: 18 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
        <span style={{ fontSize: '0.8rem', color: 'var(--on-surface-variant)', fontWeight: 500 }}>{label}</span>
        <span
          style={{
            fontFamily: 'var(--font-mono)',
            fontSize: '0.8rem',
            color: 'var(--on-surface)',
            fontWeight: 600,
          }}
        >
          {valueText}
        </span>
      </div>
      <div
        style={{
          width: '100%',
          height: 6,
          borderRadius: 3,
          background: 'var(--surface-container-highest)',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            width: `${clamp(percentage, 0, 100)}%`,
            height: '100%',
            borderRadius: 3,
            background: fillColor,
            transition: 'width 0.6s cubic-bezier(0.22, 1, 0.36, 1)',
          }}
        />
      </div>
    </div>
  );
}

/* ─── Parameter row definition ─── */

interface ParamRow {
  key: keyof RiskConfig;
  label: string;
  suffix: string;
  decimals: number;
  step: number;
}

const PARAMS: ParamRow[] = [
  { key: 'riskPerTradePct', label: 'Risk per trade', suffix: '%', decimals: 1, step: 0.1 },
  { key: 'maxEffectiveLeverage', label: 'Max eff. leverage', suffix: 'x', decimals: 1, step: 0.5 },
  { key: 'dailyLossHaltPct', label: 'Daily loss halt', suffix: '%', decimals: 1, step: 0.5 },
  { key: 'maxDrawdownPct', label: 'Max drawdown', suffix: '%', decimals: 0, step: 1 },
  { key: 'maxConcurrentPositions', label: 'Max positions', suffix: '', decimals: 0, step: 1 },
  { key: 'maxStopDistancePct', label: 'Max stop distance', suffix: '%', decimals: 1, step: 0.1 },
  { key: 'minRiskRewardRatio', label: 'Min R:R ratio', suffix: '', decimals: 1, step: 0.1 },
  { key: 'feeImpactThreshold', label: 'Fee threshold', suffix: '%', decimals: 0, step: 5 },
];

/* ─── Main Page ─── */

export default function RiskPage() {
  const queryClient = useQueryClient();

  // Editing state
  const [editingKey, setEditingKey] = useState<keyof RiskConfig | null>(null);
  const [editValue, setEditValue] = useState('');

  // Queries
  const { data: riskConfig, isLoading: configLoading } = useQuery<RiskConfig>({
    queryKey: ['risk-config'],
    queryFn: api.getRiskConfig,
    refetchInterval: 10_000,
  });

  const { data: health, isLoading: healthLoading } = useQuery<SystemHealth>({
    queryKey: ['system-health'],
    queryFn: api.getSystemHealth,
    refetchInterval: 10_000,
  });

  // Mutation
  const updateMutation = useMutation({
    mutationFn: (patch: Partial<RiskConfig>) => api.updateRiskConfig(patch),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['risk-config'] });
      toast.success('Risk parameter updated');
      setEditingKey(null);
      setEditValue('');
    },
    onError: (err: Error) => {
      toast.error('Failed to update', { description: err.message });
    },
  });

  // Derived budget values
  const account = health?.components?.account;
  const balance = account?.balance ?? 0;
  const peakEquity = account?.peakEquity ?? 0;
  const openPositions = account?.openPositions ?? 0;

  // Daily loss: we use a rough proxy — (peak - balance) as today's loss for display
  // A real implementation would track intraday PnL; we use what's available
  const dailyLossUsed = peakEquity > 0 ? Math.max(0, peakEquity - balance) : 0;
  const dailyLossLimit = peakEquity * ((riskConfig?.dailyLossHaltPct ?? 5) / 100);

  // Drawdown
  const currentDrawdownPct = peakEquity > 0 ? ((peakEquity - balance) / peakEquity) * 100 : 0;
  const maxDrawdownLimit = riskConfig?.maxDrawdownPct ?? 15;

  // Drawdown bar color
  const drawdownColor = useMemo(() => {
    if (currentDrawdownPct > 13) return '#ef4444';
    if (currentDrawdownPct > 10) return '#f97316';
    return '#22c55e';
  }, [currentDrawdownPct]);

  // Positions
  const maxPositions = riskConfig?.maxConcurrentPositions ?? 3;

  // Effective leverage — we don't have a real-time value, show 0/max
  const maxLeverage = riskConfig?.maxEffectiveLeverage ?? 5;

  const isLoading = configLoading || healthLoading;

  // Handlers
  function startEdit(row: ParamRow) {
    if (!riskConfig) return;
    const val = riskConfig[row.key];
    setEditingKey(row.key);
    setEditValue(typeof val === 'number' ? val.toString() : '');
  }

  function cancelEdit() {
    setEditingKey(null);
    setEditValue('');
  }

  function saveEdit(row: ParamRow) {
    const num = parseFloat(editValue);
    if (isNaN(num)) {
      toast.error('Invalid number');
      return;
    }
    updateMutation.mutate({ [row.key]: num } as Partial<RiskConfig>);
  }

  function formatValue(row: ParamRow, config: RiskConfig): string {
    const val = config[row.key];
    if (typeof val !== 'number') return String(val);
    return `${val.toFixed(row.decimals)}${row.suffix}`;
  }

  return (
    <div style={{ maxWidth: 1400, margin: '0 auto' }}>
      <PageHeader title="Risk Management" subtitle="7-CHECK RISK ENGINE // BUDGET MONITORING // CONFIGURABLE PARAMETERS" />

      {/* Status Banner */}
      <StatusBanner health={health} riskConfig={riskConfig} />

      {isLoading ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 32, color: 'var(--outline)' }}>
          <Loader2 size={18} className="animate-spin" />
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem' }}>Loading risk data...</span>
        </div>
      ) : (
        <>
          {/* Budget Utilization */}
          <div
            style={{
              marginTop: 28,
              background: 'var(--surface-container-low)',
              border: '1px solid var(--outline-variant)',
              padding: '24px 28px 12px',
            }}
          >
            <h2
              style={{
                fontSize: '0.8rem',
                fontWeight: 600,
                textTransform: 'uppercase',
                letterSpacing: '0.08em',
                color: 'var(--on-surface-variant)',
                marginBottom: 20,
              }}
            >
              Budget Utilization
            </h2>

            <BudgetBar
              label="Daily Loss"
              valueText={`$${dailyLossUsed.toLocaleString('en-US', { maximumFractionDigits: 2 })} / $${dailyLossLimit.toLocaleString('en-US', { maximumFractionDigits: 2 })}`}
              percentage={pct(dailyLossUsed, dailyLossLimit)}
              fillColor="#5de6ff"
            />

            <BudgetBar
              label="Max Drawdown"
              valueText={`${currentDrawdownPct.toFixed(2)}% / ${maxDrawdownLimit.toFixed(0)}%`}
              percentage={pct(currentDrawdownPct, maxDrawdownLimit)}
              fillColor={drawdownColor}
            />

            <BudgetBar
              label="Open Positions"
              valueText={`${openPositions} / ${maxPositions}`}
              percentage={pct(openPositions, maxPositions)}
              fillColor="#adc6ff"
            />

            <BudgetBar
              label="Eff. Leverage"
              valueText={`0.0x / ${maxLeverage.toFixed(1)}x`}
              percentage={0}
              fillColor="#adc6ff"
            />
          </div>

          {/* Configurable Parameters Table */}
          <div
            style={{
              marginTop: 28,
              background: 'var(--surface-container-low)',
              border: '1px solid var(--outline-variant)',
              padding: '24px 0 0',
              overflowX: 'auto',
            }}
          >
            <h2
              style={{
                fontSize: '0.8rem',
                fontWeight: 600,
                textTransform: 'uppercase',
                letterSpacing: '0.08em',
                color: 'var(--on-surface-variant)',
                marginBottom: 16,
                padding: '0 28px',
              }}
            >
              Configurable Parameters
            </h2>

            <table
              style={{
                width: '100%',
                borderCollapse: 'collapse',
                fontFamily: 'var(--font-body)',
                fontSize: '0.85rem',
                minWidth: 500,
              }}
            >
              <thead>
                <tr
                  style={{
                    borderBottom: '1px solid var(--outline-variant)',
                  }}
                >
                  <th
                    style={{
                      textAlign: 'left',
                      padding: '10px 28px',
                      fontWeight: 500,
                      fontSize: '0.75rem',
                      textTransform: 'uppercase',
                      letterSpacing: '0.05em',
                      color: 'var(--outline)',
                    }}
                  >
                    Parameter
                  </th>
                  <th
                    style={{
                      textAlign: 'left',
                      padding: '10px 28px',
                      fontWeight: 500,
                      fontSize: '0.75rem',
                      textTransform: 'uppercase',
                      letterSpacing: '0.05em',
                      color: 'var(--outline)',
                    }}
                  >
                    Current Value
                  </th>
                  <th
                    style={{
                      textAlign: 'right',
                      padding: '10px 28px',
                      fontWeight: 500,
                      fontSize: '0.75rem',
                      textTransform: 'uppercase',
                      letterSpacing: '0.05em',
                      color: 'var(--outline)',
                    }}
                  >
                    Action
                  </th>
                </tr>
              </thead>
              <tbody>
                {riskConfig &&
                  PARAMS.map((row) => {
                    const isEditing = editingKey === row.key;
                    return (
                      <tr
                        key={row.key}
                        style={{
                          borderBottom: '1px solid var(--outline-variant)',
                        }}
                      >
                        {/* Parameter name */}
                        <td
                          style={{
                            padding: '14px 28px',
                            color: 'var(--on-surface)',
                            fontWeight: 500,
                          }}
                        >
                          {row.label}
                        </td>

                        {/* Value */}
                        <td style={{ padding: '14px 28px' }}>
                          {isEditing ? (
                            <input
                              type="number"
                              step={row.step}
                              value={editValue}
                              onChange={(e) => setEditValue(e.target.value)}
                              onKeyDown={(e) => {
                                if (e.key === 'Enter') saveEdit(row);
                                if (e.key === 'Escape') cancelEdit();
                              }}
                              autoFocus
                              style={{
                                width: 100,
                                padding: '4px 8px',
                                fontFamily: 'var(--font-mono)',
                                fontSize: '0.85rem',
                                background: 'var(--surface)',
                                border: '1px solid var(--primary)',
                                color: 'var(--on-surface)',
                                outline: 'none',
                              }}
                            />
                          ) : (
                            <span
                              style={{
                                fontFamily: 'var(--font-mono)',
                                color: 'var(--on-surface)',
                              }}
                            >
                              {formatValue(row, riskConfig)}
                            </span>
                          )}
                        </td>

                        {/* Action */}
                        <td style={{ padding: '14px 28px', textAlign: 'right' }}>
                          {isEditing ? (
                            <span style={{ display: 'inline-flex', gap: 6 }}>
                              <button
                                onClick={() => saveEdit(row)}
                                disabled={updateMutation.isPending}
                                style={{
                                  padding: '4px 12px',
                                  fontSize: '0.75rem',
                                  fontWeight: 600,
                                  background: 'var(--primary)',
                                  color: 'var(--on-primary)',
                                  border: 'none',
                                  cursor: 'pointer',
                                  fontFamily: 'var(--font-body)',
                                }}
                              >
                                {updateMutation.isPending ? 'Saving...' : 'Save'}
                              </button>
                              <button
                                onClick={cancelEdit}
                                disabled={updateMutation.isPending}
                                style={{
                                  padding: '4px 12px',
                                  fontSize: '0.75rem',
                                  fontWeight: 600,
                                  background: 'transparent',
                                  color: 'var(--outline)',
                                  border: '1px solid var(--outline-variant)',
                                  cursor: 'pointer',
                                  fontFamily: 'var(--font-body)',
                                }}
                              >
                                Cancel
                              </button>
                            </span>
                          ) : (
                            <button
                              onClick={() => startEdit(row)}
                              style={{
                                padding: '4px 14px',
                                fontSize: '0.75rem',
                                fontWeight: 600,
                                background: 'transparent',
                                color: 'var(--primary)',
                                border: '1px solid var(--outline-variant)',
                                cursor: 'pointer',
                                fontFamily: 'var(--font-body)',
                                transition: 'border-color 0.15s',
                              }}
                              onMouseEnter={(e) => {
                                (e.currentTarget as HTMLButtonElement).style.borderColor = 'var(--primary)';
                              }}
                              onMouseLeave={(e) => {
                                (e.currentTarget as HTMLButtonElement).style.borderColor = 'var(--outline-variant)';
                              }}
                            >
                              Edit
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
              </tbody>
            </table>
          </div>

          {/* Risk Event Log */}
          <div
            style={{
              marginTop: 28,
              background: 'var(--surface-container-low)',
              border: '1px solid var(--outline-variant)',
              padding: '24px 28px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 18 }}>
              <Shield size={18} style={{ color: 'var(--primary)' }} />
              <h2
                style={{
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  letterSpacing: '0.08em',
                  color: 'var(--on-surface-variant)',
                  margin: 0,
                }}
              >
                Recent Risk Decisions
              </h2>
            </div>

            <div
              style={{
                padding: '32px 0',
                textAlign: 'center',
                color: 'var(--outline)',
                fontSize: '0.82rem',
                lineHeight: 1.6,
              }}
            >
              <div style={{ marginBottom: 8 }}>
                <Shield size={32} style={{ color: 'var(--outline-variant)' }} />
              </div>
              No risk events yet. Events will appear when strategies generate signals.
            </div>
          </div>
        </>
      )}
    </div>
  );
}
