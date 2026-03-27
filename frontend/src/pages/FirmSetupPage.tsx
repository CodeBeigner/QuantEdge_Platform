import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Building2,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Wallet,
  Shield,
  CheckCircle2,
} from 'lucide-react';
import { api } from '@/services/api';
import type { FirmProfile } from '@/types';

const FIRM_TYPES = [
  {
    value: 'QUANT_FUND' as const,
    label: 'Quantitative Hedge Fund',
    emoji: '📈',
  },
  {
    value: 'PROP_TRADING' as const,
    label: 'Proprietary Trading',
    emoji: '⚡',
  },
  {
    value: 'ASSET_MANAGEMENT' as const,
    label: 'Asset Management',
    emoji: '💼',
  },
  {
    value: 'RESEARCH_LAB' as const,
    label: 'Research Lab',
    emoji: '🔬',
  },
] as const;

const RISK_LEVELS = [
  { value: 'CONSERVATIVE' as const, label: 'Conservative', hint: 'Capital preservation' },
  { value: 'MODERATE' as const, label: 'Moderate', hint: 'Balanced approach' },
  { value: 'AGGRESSIVE' as const, label: 'Aggressive', hint: 'Higher risk / return' },
] as const;

type FirmType = (typeof FIRM_TYPES)[number]['value'];
type RiskAppetite = (typeof RISK_LEVELS)[number]['value'];

const STEPS = 3;

function StepDots({ step }: { step: number }) {
  return (
    <div className="mb-8 flex items-center justify-center gap-3">
      {Array.from({ length: STEPS }, (_, i) => {
        const n = i + 1;
        const done = n < step;
        const active = n === step;
        return (
          <span
            key={n}
            className={`h-2.5 w-2.5 rounded-full transition-all ${
              done
                ? 'bg-[#3b82f6]'
                : active
                  ? 'animate-pulse-glow scale-125 bg-[#00ff88] shadow-[0_0_14px_rgba(0,255,136,0.55)]'
                  : 'bg-[#64748b]/50'
            }`}
            title={`Step ${n}`}
          />
        );
      })}
    </div>
  );
}

export function FirmSetupPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [firmName, setFirmName] = useState('');
  const [firmType, setFirmType] = useState<FirmType | null>(null);
  const [initialCapital, setInitialCapital] = useState('');
  const [riskAppetite, setRiskAppetite] = useState<RiskAppetite | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const capitalNum = Number.parseFloat(initialCapital.replace(/,/g, ''));
  const capitalValid = Number.isFinite(capitalNum) && capitalNum > 0;

  const canAdvance1 = firmName.trim().length > 0 && firmType !== null;
  const canAdvance2 = capitalValid && riskAppetite !== null;

  const goNext = () => {
    setError(null);
    if (step === 1 && canAdvance1) setStep(2);
    else if (step === 2 && canAdvance2) setStep(3);
  };

  const goBack = () => {
    setError(null);
    if (step > 1) setStep((s) => s - 1);
  };

  const handleSubmit = async () => {
    if (!firmType || !riskAppetite || !capitalValid) return;
    setIsSubmitting(true);
    setError(null);
    try {
      const firm: FirmProfile = await api.setupFirm(
        firmName.trim(),
        firmType,
        capitalNum,
        riskAppetite,
      );
      navigate('/dashboard', { replace: true, state: { firm } });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const firmTypeLabel = FIRM_TYPES.find((f) => f.value === firmType)?.label ?? '—';
  const riskLabel = RISK_LEVELS.find((r) => r.value === riskAppetite)?.label ?? '—';

  return (
    <div className="min-h-screen bg-[#0a0f1c] px-4 py-12 text-[#f1f5f9]">
      <div className="mx-auto max-w-xl">
        <div className="mb-6 text-center">
          <div className="mb-3 inline-flex items-center gap-2">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-gradient-to-br from-[#00ff88] to-[#3b82f6] text-sm font-bold text-[#0a0f1c]">
              Q
            </div>
            <div className="text-left">
              <p className="text-lg font-semibold text-[#f1f5f9]">Firm onboarding</p>
              <p className="text-sm text-[#64748b]">QuantEdge Firm OS</p>
            </div>
          </div>
        </div>

        <div className="bg-[#1a2234]/90 backdrop-blur-xl border border-[#2a3650] rounded-2xl p-8 shadow-2xl shadow-black/40">
          <StepDots step={step} />

          {error ? (
            <div
              role="alert"
              className="mb-6 rounded-xl border border-red-500/40 bg-red-950/60 px-4 py-3 text-sm text-red-200"
            >
              {error}
            </div>
          ) : null}

          {step === 1 ? (
            <div className="space-y-6 animate-in">
              <div>
                <label className="mb-2 flex items-center gap-2 text-sm font-medium text-[#94a3b8]">
                  <Building2 className="h-4 w-4 text-[#3b82f6]" aria-hidden />
                  Firm name
                </label>
                <input
                  type="text"
                  value={firmName}
                  onChange={(e) => setFirmName(e.target.value)}
                  placeholder="e.g. Apex Quant Partners"
                  className="w-full rounded-xl border border-[#2a3650] bg-[#111827]/80 px-4 py-3 text-[#f1f5f9] placeholder:text-[#64748b] outline-none transition focus:border-[#3b82f6] focus:ring-1 focus:ring-[#3b82f6]"
                />
              </div>
              <div>
                <p className="mb-3 text-sm font-medium text-[#94a3b8]">Firm type</p>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  {FIRM_TYPES.map((opt) => {
                    const selected = firmType === opt.value;
                    return (
                      <button
                        key={opt.value}
                        type="button"
                        onClick={() => setFirmType(opt.value)}
                        className={`flex items-start gap-3 rounded-xl border p-4 text-left transition ${
                          selected
                            ? 'border-[#00ff88]/60 bg-[#111827] shadow-[0_0_0_1px_rgba(0,255,136,0.25)]'
                            : 'border-[#2a3650] bg-[#111827]/50 hover:border-[#3b82f6]/40'
                        }`}
                      >
                        <span className="text-2xl leading-none" aria-hidden>
                          {opt.emoji}
                        </span>
                        <span className="text-sm font-medium text-[#f1f5f9]">{opt.label}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>
          ) : null}

          {step === 2 ? (
            <div className="space-y-6 animate-in">
              <div>
                <label className="mb-2 flex items-center gap-2 text-sm font-medium text-[#94a3b8]">
                  <Wallet className="h-4 w-4 text-[#3b82f6]" aria-hidden />
                  Initial capital (USD)
                </label>
                <input
                  type="number"
                  min={1}
                  step="1000"
                  value={initialCapital}
                  onChange={(e) => setInitialCapital(e.target.value)}
                  placeholder="1000000"
                  className="w-full rounded-xl border border-[#2a3650] bg-[#111827]/80 px-4 py-3 font-mono text-[#f1f5f9] placeholder:text-[#64748b] outline-none transition focus:border-[#3b82f6] focus:ring-1 focus:ring-[#3b82f6]"
                />
                {!capitalValid && initialCapital !== '' ? (
                  <p className="mt-1.5 text-xs text-red-400">Enter a positive number</p>
                ) : null}
              </div>
              <div>
                <p className="mb-3 flex items-center gap-2 text-sm font-medium text-[#94a3b8]">
                  <Shield className="h-4 w-4 text-[#3b82f6]" aria-hidden />
                  Risk appetite
                </p>
                <div className="flex flex-col gap-2">
                  {RISK_LEVELS.map((r) => {
                    const selected = riskAppetite === r.value;
                    return (
                      <button
                        key={r.value}
                        type="button"
                        onClick={() => setRiskAppetite(r.value)}
                        className={`flex w-full items-center justify-between rounded-xl border px-4 py-3 text-left transition ${
                          selected
                            ? 'border-[#00ff88]/60 bg-[#111827] shadow-[0_0_0_1px_rgba(0,255,136,0.25)]'
                            : 'border-[#2a3650] bg-[#111827]/50 hover:border-[#3b82f6]/40'
                        }`}
                      >
                        <span className="font-medium text-[#f1f5f9]">{r.label}</span>
                        <span className="text-xs text-[#64748b]">{r.hint}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>
          ) : null}

          {step === 3 ? (
            <div className="space-y-5 animate-in">
              <div className="flex items-center gap-2 text-[#94a3b8]">
                <CheckCircle2 className="h-5 w-5 text-[#00ff88]" aria-hidden />
                <span className="text-sm font-medium">Review your setup</span>
              </div>
              <dl className="space-y-3 rounded-xl border border-[#2a3650] bg-[#111827]/60 p-4">
                <div className="flex justify-between gap-4 text-sm">
                  <dt className="text-[#64748b]">Firm name</dt>
                  <dd className="text-right font-medium text-[#f1f5f9]">{firmName.trim() || '—'}</dd>
                </div>
                <div className="flex justify-between gap-4 text-sm">
                  <dt className="text-[#64748b]">Firm type</dt>
                  <dd className="text-right font-medium text-[#f1f5f9]">{firmTypeLabel}</dd>
                </div>
                <div className="flex justify-between gap-4 text-sm">
                  <dt className="text-[#64748b]">Initial capital</dt>
                  <dd className="text-right font-mono font-medium text-[#f1f5f9]">
                    {capitalValid
                      ? new Intl.NumberFormat('en-US', {
                          style: 'currency',
                          currency: 'USD',
                          maximumFractionDigits: 0,
                        }).format(capitalNum)
                      : '—'}
                  </dd>
                </div>
                <div className="flex justify-between gap-4 text-sm">
                  <dt className="text-[#64748b]">Risk appetite</dt>
                  <dd className="text-right font-medium text-[#f1f5f9]">{riskLabel}</dd>
                </div>
              </dl>
            </div>
          ) : null}

          <div className="mt-8 flex flex-wrap items-center justify-between gap-3 border-t border-[#2a3650] pt-6">
            {step > 1 ? (
              <button
                type="button"
                onClick={goBack}
                disabled={isSubmitting}
                className="inline-flex items-center gap-1 rounded-xl border border-[#2a3650] bg-transparent px-4 py-2.5 text-sm font-medium text-[#94a3b8] transition hover:border-[#3b82f6]/50 hover:text-[#f1f5f9] disabled:opacity-50"
              >
                <ChevronLeft className="h-4 w-4" aria-hidden />
                Back
              </button>
            ) : (
              <span />
            )}

            {step < 3 ? (
              <button
                type="button"
                onClick={goNext}
                disabled={(step === 1 && !canAdvance1) || (step === 2 && !canAdvance2)}
                className="ml-auto inline-flex items-center gap-1 rounded-xl bg-gradient-to-r from-[#00ff88] to-[#3b82f6] px-5 py-2.5 text-sm font-semibold text-[#0a0f1c] shadow-lg shadow-[#00ff88]/15 transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Continue
                <ChevronRight className="h-4 w-4" aria-hidden />
              </button>
            ) : (
              <button
                type="button"
                onClick={() => void handleSubmit()}
                disabled={isSubmitting || !canAdvance1 || !canAdvance2}
                className="ml-auto inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-[#00ff88] to-[#3b82f6] px-5 py-2.5 text-sm font-semibold text-[#0a0f1c] shadow-lg shadow-[#00ff88]/15 transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                    Setting up…
                  </>
                ) : (
                  'Complete setup'
                )}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
