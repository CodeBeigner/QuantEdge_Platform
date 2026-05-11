import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Activity, Loader2, TrendingUp, TrendingDown, Pause, AlertCircle } from 'lucide-react';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import type {
  MetaPrediction, MetaTrainResult,
  FlowPrediction, FlowTrainResult,
} from '@/types';

const DEFAULT_SYMBOLS = ['BTCUSDT', 'ETHUSDT'];

function DirectionBadge({ direction }: { direction: -1 | 0 | 1 }) {
  if (direction === 1) {
    return (
      <span
        className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-bold"
        style={{ background: 'rgba(0,255,136,0.15)', color: 'var(--tertiary)' }}
      >
        <TrendingUp size={14} /> LONG
      </span>
    );
  }
  if (direction === -1) {
    return (
      <span
        className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-bold"
        style={{ background: 'rgba(239,68,68,0.15)', color: 'var(--error)' }}
      >
        <TrendingDown size={14} /> SHORT
      </span>
    );
  }
  return (
    <span
      className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-bold"
      style={{ background: 'rgba(251,191,36,0.15)', color: '#fbbf24' }}
    >
      <Pause size={14} /> FLAT
    </span>
  );
}

function ProbBar({ label, value }: { label: string; value: number }) {
  const pct = (value * 100).toFixed(1);
  const color =
    value >= 0.7 ? 'from-[#00ff88] to-[#3b82f6]' :
    value >= 0.4 ? 'from-[#fbbf24] to-[#f97316]' :
                   'from-[#ef4444] to-[#f97316]';
  return (
    <div>
      <div className="flex justify-between text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>
        <span>{label}</span>
        <span>{pct}%</span>
      </div>
      <div className="h-2 rounded-full overflow-hidden" style={{ background: 'var(--surface)' }}>
        <div
          className={`h-full rounded-full bg-gradient-to-r ${color} transition-all`}
          style={{ width: `${Math.min(100, Math.max(0, value * 100))}%` }}
        />
      </div>
    </div>
  );
}

function ErrorCallout({ error, message }: { error: string; message?: string }) {
  return (
    <div
      className="flex items-start gap-2 p-3 rounded text-sm"
      style={{ background: 'rgba(239,68,68,0.1)', color: 'var(--error)' }}
    >
      <AlertCircle size={16} className="shrink-0 mt-0.5" />
      <div>
        <div className="font-semibold">{error}</div>
        {message && <div className="text-xs opacity-80">{message}</div>}
      </div>
    </div>
  );
}

function MetaPanel({ symbol }: { symbol: string }) {
  const [primary, setPrimary] = useState<'LONG' | 'SHORT'>('LONG');
  const [entryPrice, setEntryPrice] = useState<string>('');

  const train = useMutation<MetaTrainResult>({
    mutationFn: () => api.mlTrainMeta(symbol),
  });
  const predict = useMutation<MetaPrediction>({
    mutationFn: () => api.mlPredictMeta(symbol, primary, Number(entryPrice) || 0),
  });

  const pred = predict.data;
  const trained = train.data;

  return (
    <div
      className="p-5 space-y-4 rounded-lg"
      style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}
    >
      <div className="flex items-baseline justify-between">
        <h3 className="font-semibold" style={{ color: 'var(--on-surface)' }}>
          Meta Filter — {symbol}
        </h3>
        <span className="text-xs" style={{ color: 'var(--outline)' }}>XGBoost · Triple-Barrier</span>
      </div>

      <div className="flex gap-2">
        <select
          value={primary}
          onChange={(e) => setPrimary(e.target.value as 'LONG' | 'SHORT')}
          className="px-3 py-2 rounded text-sm"
          style={{ background: 'var(--surface)', color: 'var(--on-surface)', border: '1px solid var(--outline-variant)' }}
        >
          <option value="LONG">LONG signal</option>
          <option value="SHORT">SHORT signal</option>
        </select>
        <input
          type="number"
          value={entryPrice}
          onChange={(e) => setEntryPrice(e.target.value)}
          placeholder="Entry price (optional)"
          className="flex-1 px-3 py-2 rounded text-sm"
          style={{ background: 'var(--surface)', color: 'var(--on-surface)', border: '1px solid var(--outline-variant)' }}
        />
      </div>

      <div className="flex gap-2">
        <button
          onClick={() => predict.mutate()}
          disabled={predict.isPending}
          className="flex-1 px-4 py-2 rounded text-sm font-medium transition-colors"
          style={{ background: 'var(--primary)', color: 'var(--on-primary)' }}
        >
          {predict.isPending ? <Loader2 size={14} className="inline animate-spin mr-1" /> : null}
          Predict
        </button>
        <button
          onClick={() => train.mutate()}
          disabled={train.isPending}
          className="px-4 py-2 rounded text-sm"
          style={{ background: 'var(--surface)', color: 'var(--on-surface-variant)', border: '1px solid var(--outline-variant)' }}
        >
          {train.isPending ? <Loader2 size={14} className="inline animate-spin mr-1" /> : null}
          Train
        </button>
      </div>

      {pred?.error && <ErrorCallout error={pred.error} message={pred.message} />}

      {pred && !pred.error && (
        <div className="space-y-3">
          <div className="flex items-center gap-3">
            <DirectionBadge direction={pred.direction as -1 | 0 | 1} />
            <span className="text-xs" style={{ color: 'var(--outline)' }}>
              on {pred.primary_signal} signal
            </span>
          </div>
          <ProbBar label="Meta probability" value={pred.meta_prob} />
        </div>
      )}

      {trained?.error && <ErrorCallout error={trained.error} message={trained.message} />}

      {trained && !trained.error && (
        <div
          className="text-xs space-y-1 p-2 rounded"
          style={{ background: 'var(--surface)', color: 'var(--on-surface-variant)' }}
        >
          <div>Trained on {trained.n_train} samples</div>
          {trained.n_dropped_timeout !== undefined && (
            <div>Dropped {trained.n_dropped_timeout} timeout rows</div>
          )}
          {trained.train_accuracy !== undefined && (
            <div>Train accuracy: {(trained.train_accuracy * 100).toFixed(1)}%</div>
          )}
        </div>
      )}
    </div>
  );
}

function FlowPanel({ symbol }: { symbol: string }) {
  const train = useMutation<FlowTrainResult>({
    mutationFn: () => api.mlTrainFlow(symbol),
  });
  const predict = useMutation<FlowPrediction>({
    mutationFn: () => api.mlPredictFlow(symbol, 200),
  });

  const pred = predict.data;
  const trained = train.data;

  return (
    <div
      className="p-5 space-y-4 rounded-lg"
      style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}
    >
      <div className="flex items-baseline justify-between">
        <h3 className="font-semibold" style={{ color: 'var(--on-surface)' }}>
          Order Flow — {symbol}
        </h3>
        <span className="text-xs" style={{ color: 'var(--outline)' }}>LightGBM · Fallback features</span>
      </div>

      <div className="flex gap-2">
        <button
          onClick={() => predict.mutate()}
          disabled={predict.isPending}
          className="flex-1 px-4 py-2 rounded text-sm font-medium transition-colors"
          style={{ background: 'var(--primary)', color: 'var(--on-primary)' }}
        >
          {predict.isPending ? <Loader2 size={14} className="inline animate-spin mr-1" /> : null}
          Predict
        </button>
        <button
          onClick={() => train.mutate()}
          disabled={train.isPending}
          className="px-4 py-2 rounded text-sm"
          style={{ background: 'var(--surface)', color: 'var(--on-surface-variant)', border: '1px solid var(--outline-variant)' }}
        >
          {train.isPending ? <Loader2 size={14} className="inline animate-spin mr-1" /> : null}
          Train
        </button>
      </div>

      {pred?.error && <ErrorCallout error={pred.error} message={pred.message} />}

      {pred && !pred.error && (
        <div className="space-y-3">
          <div className="flex items-center gap-3">
            <DirectionBadge direction={pred.direction as -1 | 0 | 1} />
            <span className="text-xs" style={{ color: 'var(--outline)' }}>
              {pred.direction === 0 ? 'below confidence threshold' : `score ${(pred.flow_score * 100).toFixed(1)}%`}
            </span>
          </div>
          <div className="space-y-2">
            <ProbBar label="Long" value={pred.probs.long} />
            <ProbBar label="Flat" value={pred.probs.flat} />
            <ProbBar label="Short" value={pred.probs.short} />
          </div>
        </div>
      )}

      {trained?.error && <ErrorCallout error={trained.error} message={trained.message} />}

      {trained && !trained.error && (
        <div
          className="text-xs space-y-1 p-2 rounded"
          style={{ background: 'var(--surface)', color: 'var(--on-surface-variant)' }}
        >
          <div>Trained on {trained.n_train} samples (fwd = {trained.forward_bars} bars)</div>
          {trained.train_accuracy !== undefined && (
            <div>Train accuracy: {(trained.train_accuracy * 100).toFixed(1)}%</div>
          )}
        </div>
      )}
    </div>
  );
}

export default function MLPage() {
  const [symbol, setSymbol] = useState<string>(DEFAULT_SYMBOLS[0]);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="ML Intelligence"
        subtitle="Triple-barrier meta-labeler + order-flow model"
      >
        <div className="flex items-center gap-2">
          <Activity size={20} style={{ color: 'var(--primary)' }} />
        </div>
      </PageHeader>

      <div className="flex items-center gap-3">
        <label className="text-sm" style={{ color: 'var(--on-surface-variant)' }}>Symbol</label>
        <select
          value={symbol}
          onChange={(e) => setSymbol(e.target.value)}
          className="px-3 py-2 rounded text-sm"
          style={{ background: 'var(--surface)', color: 'var(--on-surface)', border: '1px solid var(--outline-variant)' }}
        >
          {DEFAULT_SYMBOLS.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <MetaPanel key={`meta-${symbol}`} symbol={symbol} />
        <FlowPanel key={`flow-${symbol}`} symbol={symbol} />
      </div>
    </div>
  );
}
