import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer,
  ScatterChart, Scatter, XAxis, YAxis, CartesianGrid,
} from 'recharts';
import { Activity, Loader2, TrendingUp, TrendingDown, BarChart3 } from 'lucide-react';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import type { MLPrediction } from '@/types';

const PIE_COLORS = ['#00ff88', '#3b82f6', '#a78bfa', '#fbbf24', '#ef4444', '#22d3ee', '#f97316'];

function SignalBadge({ signal }: { signal: string }) {
  const s = signal.toUpperCase();
  const style = s === 'BUY' ? { background: 'rgba(0,255,136,0.15)', color: 'var(--tertiary)' }
    : s === 'SELL' ? { background: 'rgba(239,68,68,0.15)', color: 'var(--error)' }
    : { background: 'rgba(251,191,36,0.15)', color: '#fbbf24' };
  return <span className="px-3 py-1 rounded-full text-sm font-bold" style={style}>{s}</span>;
}

function ConfidenceBar({ value }: { value: number }) {
  const pct = (value * 100).toFixed(1);
  const color = value >= 0.7 ? 'from-[#00ff88] to-[#3b82f6]' : value >= 0.4 ? 'from-[#fbbf24] to-[#f97316]' : 'from-[#ef4444] to-[#f97316]';
  return (
    <div>
      <div className="flex justify-between text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>
        <span>Confidence</span>
        <span>{pct}%</span>
      </div>
      <div className="h-2 rounded-full overflow-hidden" style={{ background: 'var(--surface)' }}>
        <div className={`h-full rounded-full bg-gradient-to-r ${color} transition-all`} style={{ width: `${value * 100}%` }} />
      </div>
    </div>
  );
}

function PredictionDisplay({ prediction, title }: { prediction: MLPrediction; title: string }) {
  const features = prediction.features ? Object.entries(prediction.features).sort((a, b) => Math.abs(b[1]) - Math.abs(a[1])) : [];

  return (
    <div className="p-5 space-y-4" style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}>
      <h3 className="font-semibold" style={{ color: 'var(--on-surface)' }}>{title}</h3>
      <div className="flex items-center gap-4">
        <SignalBadge signal={prediction.signal} />
        {prediction.ensemble && <span className="text-xs px-2 py-0.5 rounded-full" style={{ background: 'rgba(167,139,250,0.15)', color: 'var(--primary)' }}>Ensemble</span>}
        {prediction.model_used && <span className="text-xs" style={{ color: 'var(--outline)' }}>{prediction.model_used}</span>}
      </div>
      <ConfidenceBar value={prediction.confidence} />
      <div className="flex gap-6 text-sm">
        <div className="flex items-center gap-1.5">
          <TrendingUp size={14} style={{ color: 'var(--tertiary)' }} />
          <span style={{ color: 'var(--on-surface-variant)' }}>Up:</span>
          <span className="font-mono" style={{ color: 'var(--on-surface)' }}>{(prediction.direction_prob.up * 100).toFixed(1)}%</span>
        </div>
        <div className="flex items-center gap-1.5">
          <TrendingDown size={14} style={{ color: 'var(--error)' }} />
          <span style={{ color: 'var(--on-surface-variant)' }}>Down:</span>
          <span className="font-mono" style={{ color: 'var(--on-surface)' }}>{(prediction.direction_prob.down * 100).toFixed(1)}%</span>
        </div>
      </div>
      {prediction.model_accuracy != null && (
        <div className="flex items-center gap-2 text-sm">
          <BarChart3 size={14} style={{ color: 'var(--primary)' }} />
          <span style={{ color: 'var(--on-surface-variant)' }}>Model accuracy:</span>
          <span className="font-mono" style={{ color: 'var(--on-surface)' }}>{(prediction.model_accuracy * 100).toFixed(1)}%</span>
        </div>
      )}
      {features.length > 0 && (
        <div>
          <p className="text-xs mb-2" style={{ color: 'var(--outline)' }}>Feature Importance</p>
          <div className="space-y-1.5 max-h-40 overflow-y-auto">
            {features.slice(0, 10).map(([name, val]) => (
              <div key={name} className="flex items-center gap-2">
                <span className="text-xs w-32 truncate shrink-0" title={name} style={{ color: 'var(--on-surface-variant)' }}>{name}</span>
                <div className="flex-1 h-1.5 rounded-full overflow-hidden" style={{ background: 'var(--surface)' }}>
                  <div
                    className="h-full rounded-full"
                    style={{ background: 'var(--primary)', width: `${Math.min(Math.abs(val) * 100, 100)}%` }}
                  />
                </div>
                <span className="text-[10px] font-mono w-12 text-right" style={{ color: 'var(--outline)' }}>{val.toFixed(3)}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default function MLPage() {
  const [symbol, setSymbol] = useState('SPY');
  const [optSymbols, setOptSymbols] = useState<string[]>(['SPY', 'QQQ', 'IWM']);
  const [optInput, setOptInput] = useState('');

  const { data: symbols = [] } = useQuery<string[]>({
    queryKey: ['symbols'],
    queryFn: api.getSymbols,
  });

  const { data: health } = useQuery<Record<string, unknown>>({
    queryKey: ['ml-health'],
    queryFn: api.mlHealth,
    refetchInterval: 30_000,
  });

  const trainXgb = useMutation({ mutationFn: (s: string) => api.mlTrain(s) });
  const trainLstm = useMutation({ mutationFn: (s: string) => api.mlTrainLstm(s) });
  const predict = useMutation({ mutationFn: (s: string) => api.mlPredict(s) });
  const predictEns = useMutation({ mutationFn: (s: string) => api.mlPredictEnsemble(s) });
  const optimize = useMutation({ mutationFn: (syms: string[]) => api.mlOptimize(syms) });

  const isHealthy = health && (health as Record<string, unknown>).status !== 'DOWN';

  const allocations = optimize.data
    ? Object.entries(optimize.data as Record<string, unknown>)
        .filter(([k]) => !['efficient_frontier', 'expected_return', 'expected_risk'].includes(k))
        .map(([name, weight]) => ({ name, weight: Number(weight) || 0 }))
        .filter(a => a.weight > 0.001)
    : [];

  const pieData = allocations.map(a => ({ name: a.name, value: +(a.weight * 100).toFixed(2) }));

  const frontier = optimize.data
    ? ((optimize.data as Record<string, unknown>).efficient_frontier as { risk: number; return: number }[] | undefined) ?? []
    : [];

  const addOptSymbol = () => {
    const s = optInput.trim().toUpperCase();
    if (s && !optSymbols.includes(s)) setOptSymbols(prev => [...prev, s]);
    setOptInput('');
  };

  return (
    <div className="space-y-6 max-w-[1400px] mx-auto">
      <PageHeader title="ML Engine" subtitle="XGBOOST // LSTM // ENSEMBLE PREDICTIONS">
        <div className="flex items-center gap-2">
          <span className={`w-2.5 h-2.5 rounded-full ${isHealthy ? 'animate-pulse' : ''}`} style={{ background: isHealthy ? 'var(--tertiary)' : 'var(--error)' }} />
          <span className="text-sm" style={{ color: 'var(--on-surface-variant)' }}>{isHealthy ? 'ML Service Online' : 'ML Service Offline'}</span>
        </div>
      </PageHeader>

      {/* Symbol selector + actions */}
      <div className="p-5 space-y-4" style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}>
        <div className="flex items-end gap-3 flex-wrap">
          <div>
            <label className="block text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>Symbol</label>
            <select
              value={symbol}
              onChange={e => setSymbol(e.target.value)}
              className="px-3 py-2 text-sm outline-none cursor-pointer"
              style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)', color: 'var(--on-surface)' }}
            >
              {symbols.length === 0 && <option value="SPY">SPY</option>}
              {symbols.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
          <div className="flex gap-2 flex-wrap">
            <ActionBtn label="Train XGBoost" loading={trainXgb.isPending} onClick={() => trainXgb.mutate(symbol)} />
            <ActionBtn label="Train LSTM" loading={trainLstm.isPending} onClick={() => trainLstm.mutate(symbol)} />
            <ActionBtn label="Predict" loading={predict.isPending} onClick={() => predict.mutate(symbol)} color="green" />
            <ActionBtn label="Predict Ensemble" loading={predictEns.isPending} onClick={() => predictEns.mutate(symbol)} color="purple" />
          </div>
        </div>

        {/* Training results */}
        {(trainXgb.data || trainLstm.data) && (
          <div className="grid sm:grid-cols-2 gap-4">
            {trainXgb.data && <ModelInfoCard title="XGBoost" data={trainXgb.data as Record<string, unknown>} />}
            {trainLstm.data && <ModelInfoCard title="LSTM" data={trainLstm.data as Record<string, unknown>} />}
          </div>
        )}
      </div>

      {/* Prediction results */}
      <div className="grid sm:grid-cols-2 gap-4">
        {predict.data && <PredictionDisplay prediction={predict.data} title="XGBoost Prediction" />}
        {predictEns.data && <PredictionDisplay prediction={predictEns.data} title="Ensemble Prediction" />}
      </div>

      {/* Portfolio Optimization */}
      <div className="p-5 space-y-4" style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}>
        <h2 className="font-semibold" style={{ color: 'var(--on-surface)' }}>Portfolio Optimization</h2>

        <div className="flex items-end gap-3 flex-wrap">
          <div>
            <label className="block text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>Add Symbol</label>
            <div className="flex gap-2">
              <input
                value={optInput}
                onChange={e => setOptInput(e.target.value.toUpperCase())}
                onKeyDown={e => e.key === 'Enter' && addOptSymbol()}
                placeholder="AAPL"
                className="px-3 py-2 text-sm outline-none w-28"
                style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)', color: 'var(--on-surface)' }}
              />
              <button onClick={addOptSymbol} className="px-3 py-2 text-sm transition-colors cursor-pointer" style={{ background: 'var(--outline-variant)', color: 'var(--on-surface)' }}>Add</button>
            </div>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {optSymbols.map(s => (
              <span key={s} className="flex items-center gap-1 text-xs px-2 py-1 rounded-full" style={{ background: 'rgba(59,130,246,0.15)', color: 'var(--primary)' }}>
                {s}
                <button onClick={() => setOptSymbols(prev => prev.filter(x => x !== s))} className="hover:text-white cursor-pointer">&times;</button>
              </span>
            ))}
          </div>
          <button
            onClick={() => optimize.mutate(optSymbols)}
            disabled={optSymbols.length < 2 || optimize.isPending}
            className="px-4 py-2 bg-gradient-to-r from-[#a78bfa] to-[#3b82f6] text-white text-sm font-medium hover:opacity-90 disabled:opacity-40 transition-opacity cursor-pointer flex items-center gap-2"
          >
            {optimize.isPending && <Loader2 size={14} className="animate-spin" />}
            Optimize
          </button>
        </div>

        {optimize.isError && <p className="text-sm" style={{ color: 'var(--error)' }}>{(optimize.error as Error).message}</p>}

        {allocations.length > 0 && (
          <div className="grid lg:grid-cols-2 gap-6">
            {/* Allocation pie */}
            <div>
              <p className="text-xs mb-3" style={{ color: 'var(--outline)' }}>Optimal Allocation</p>
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={pieData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={90} label={({ name, value }) => `${name} ${value}%`}>
                      {pieData.map((_, i) => <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />)}
                    </Pie>
                    <Tooltip contentStyle={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)', borderRadius: 8, color: 'var(--on-surface)', fontSize: 12 }} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* Efficient frontier */}
            {frontier.length > 0 && (
              <div>
                <p className="text-xs mb-3" style={{ color: 'var(--outline)' }}>Efficient Frontier</p>
                <div className="h-64">
                  <ResponsiveContainer width="100%" height="100%">
                    <ScatterChart>
                      <CartesianGrid strokeDasharray="3 3" stroke="var(--outline-variant)" />
                      <XAxis dataKey="risk" name="Risk" tick={{ fill: '#64748b', fontSize: 11 }} label={{ value: 'Risk', fill: '#64748b', fontSize: 11, position: 'bottom' }} />
                      <YAxis dataKey="return" name="Return" tick={{ fill: '#64748b', fontSize: 11 }} label={{ value: 'Return', fill: '#64748b', fontSize: 11, angle: -90, position: 'left' }} />
                      <Tooltip contentStyle={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)', borderRadius: 8, color: 'var(--on-surface)', fontSize: 12 }} />
                      <Scatter data={frontier} fill="#a78bfa" />
                    </ScatterChart>
                  </ResponsiveContainer>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function ActionBtn({ label, loading, onClick, color = 'blue' }: { label: string; loading: boolean; onClick: () => void; color?: string }) {
  const style = color === 'green' ? { background: 'rgba(0,255,136,0.15)', color: 'var(--tertiary)' }
    : color === 'purple' ? { background: 'rgba(167,139,250,0.15)', color: 'var(--primary)' }
    : { background: 'rgba(59,130,246,0.15)', color: 'var(--primary)' };
  return (
    <button onClick={onClick} disabled={loading} className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium transition-colors disabled:opacity-40 cursor-pointer" style={style}>
      {loading ? <Loader2 size={14} className="animate-spin" /> : <Activity size={14} />}
      {label}
    </button>
  );
}

function ModelInfoCard({ title, data }: { title: string; data: Record<string, unknown> }) {
  return (
    <div className="p-4 space-y-2" style={{ background: 'var(--surface)' }}>
      <h4 className="text-sm font-medium" style={{ color: 'var(--on-surface)' }}>{title} — Training Result</h4>
      <div className="grid grid-cols-2 gap-2">
        {Object.entries(data).map(([k, v]) => (
          <div key={k}>
            <span className="text-[10px]" style={{ color: 'var(--outline)' }}>{k.replace(/_/g, ' ')}</span>
            <p className="text-xs font-mono" style={{ color: 'var(--on-surface)' }}>
              {typeof v === 'number' ? v.toFixed(4) : String(v)}
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}
