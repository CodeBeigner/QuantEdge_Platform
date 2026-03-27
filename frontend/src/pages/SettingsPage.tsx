import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Building2, User, Palette, Bell, Code, Info, Loader2, Save, Check, Link2 } from 'lucide-react';
import { api } from '@/services/api';
import { deltaApi } from '@/services/deltaExchange';
import { PageHeader } from '@/components/ui/PageHeader';
import { useAuthStore } from '@/stores/authStore';
import type { FirmProfile } from '@/types';

type SectionKey = 'firm' | 'user' | 'preferences' | 'delta' | 'api' | 'about';

const SECTIONS: { key: SectionKey; label: string; icon: React.ComponentType<{ size?: number; className?: string }> }[] = [
  { key: 'firm',        label: 'Firm Profile',  icon: Building2 },
  { key: 'user',        label: 'User Profile',  icon: User },
  { key: 'preferences', label: 'Preferences',   icon: Palette },
  { key: 'delta',       label: 'Delta Exchange', icon: Link2 },
  { key: 'api',         label: 'API Config',     icon: Code },
  { key: 'about',       label: 'About',          icon: Info },
];

function FirmSection() {
  const qc = useQueryClient();
  const { data: firm, isLoading } = useQuery<FirmProfile | null>({
    queryKey: ['firm'],
    queryFn: api.getFirm,
  });

  const [editing, setEditing] = useState(false);
  const [firmName, setFirmName] = useState('');
  const [riskAppetite, setRiskAppetite] = useState('');

  useEffect(() => {
    if (firm) {
      setFirmName(firm.firmName);
      setRiskAppetite(firm.riskAppetite);
    }
  }, [firm]);

  const updateMut = useMutation({
    mutationFn: () => api.updateFirm(firmName, riskAppetite),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['firm'] });
      setEditing(false);
    },
  });

  if (isLoading) return <div className="h-32 skeleton" />;
  if (!firm) return <p className="text-sm" style={{ color: 'var(--outline)' }}>No firm profile configured. Set up your firm to get started.</p>;

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Field label="Firm Name" editing={editing} value={firmName} onChange={setFirmName} display={firm.firmName} />
        <Field label="Firm Type" editing={false} value="" onChange={() => {}} display={firm.firmType} />
        <Field label="Initial Capital" editing={false} value="" onChange={() => {}} display={`$${firm.initialCapital.toLocaleString()}`} />
        <Field label="Risk Appetite" editing={editing} value={riskAppetite} onChange={setRiskAppetite} display={firm.riskAppetite}>
          {editing && (
            <select
              value={riskAppetite}
              onChange={e => setRiskAppetite(e.target.value)}
              className="w-full px-3 py-2 text-sm outline-none cursor-pointer font-mono"
              style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)', color: 'var(--on-surface)' }}
            >
              {['CONSERVATIVE', 'MODERATE', 'AGGRESSIVE'].map(r => (
                <option key={r} value={r}>{r}</option>
              ))}
            </select>
          )}
        </Field>
      </div>
      <div className="flex items-center gap-2">
        {!editing ? (
          <button onClick={() => setEditing(true)} className="px-4 py-2 text-white text-sm font-medium transition-colors cursor-pointer" style={{ background: 'var(--primary)' }}>
            Edit Profile
          </button>
        ) : (
          <>
            <button
              onClick={() => updateMut.mutate()}
              disabled={updateMut.isPending}
              className="flex items-center gap-2 px-4 py-2 text-sm font-medium transition-colors disabled:opacity-40 cursor-pointer"
              style={{ background: 'rgba(0,255,136,0.15)', color: 'var(--tertiary)' }}
            >
              {updateMut.isPending ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
              Save
            </button>
            <button onClick={() => { setEditing(false); setFirmName(firm.firmName); setRiskAppetite(firm.riskAppetite); }} className="px-4 py-2 text-sm transition-colors cursor-pointer" style={{ background: 'var(--outline-variant)', color: 'var(--on-surface-variant)' }}>
              Cancel
            </button>
          </>
        )}
      </div>
      {updateMut.isError && <p className="text-xs" style={{ color: 'var(--error)' }}>{(updateMut.error as Error).message}</p>}
      <p className="text-xs" style={{ color: 'var(--outline)' }}>Created: {new Date(firm.createdAt).toLocaleDateString()}</p>
    </div>
  );
}

function Field({
  label, editing, value, onChange, display, children,
}: {
  label: string;
  editing: boolean;
  value: string;
  onChange: (v: string) => void;
  display: string;
  children?: React.ReactNode;
}) {
  return (
    <div>
      <label className="block text-xs mb-1" style={{ color: 'var(--outline)' }}>{label}</label>
      {children ? (
        editing ? children : <p className="text-sm px-3 py-2 font-mono" style={{ color: 'var(--on-surface)', background: 'var(--surface)' }}>{display}</p>
      ) : editing ? (
        <input
          value={value}
          onChange={e => onChange(e.target.value)}
          className="w-full px-3 py-2 text-sm outline-none font-mono"
          style={{ background: 'var(--surface)', border: '1px solid var(--outline-variant)', color: 'var(--on-surface)' }}
        />
      ) : (
        <p className="text-sm px-3 py-2 font-mono" style={{ color: 'var(--on-surface)', background: 'var(--surface)' }}>{display}</p>
      )}
    </div>
  );
}

function UserSection() {
  const { user } = useAuthStore();

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-4">
        <div className="w-14 h-14 rounded-full bg-gradient-to-br from-[#3b82f6] to-[#8b5cf6] flex items-center justify-center text-xl font-bold text-white uppercase">
          {user?.name?.charAt(0) ?? 'U'}
        </div>
        <div>
          <p className="text-lg font-medium" style={{ color: 'var(--on-surface)' }}>{user?.name ?? 'User'}</p>
          <p className="text-sm" style={{ color: 'var(--outline)' }}>{user?.email ?? 'No email'}</p>
        </div>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label className="block text-xs mb-1" style={{ color: 'var(--outline)' }}>Name</label>
          <p className="text-sm px-3 py-2 font-mono" style={{ color: 'var(--on-surface)', background: 'var(--surface)' }}>{user?.name ?? '—'}</p>
        </div>
        <div>
          <label className="block text-xs mb-1" style={{ color: 'var(--outline)' }}>Email</label>
          <p className="text-sm px-3 py-2 font-mono" style={{ color: 'var(--on-surface)', background: 'var(--surface)' }}>{user?.email ?? '—'}</p>
        </div>
      </div>
    </div>
  );
}

function PreferencesSection() {
  const [darkMode, setDarkMode] = useState(true);
  const [notifications, setNotifications] = useState({ alerts: true, trades: true, system: false });

  return (
    <div className="space-y-6">
      {/* Theme */}
      <div>
        <h3 className="text-sm font-medium mb-3" style={{ color: 'var(--on-surface)' }}>Theme</h3>
        <div className="flex items-center justify-between px-4 py-3" style={{ background: 'var(--surface)' }}>
          <div>
            <p className="text-sm" style={{ color: 'var(--on-surface)' }}>Dark Mode</p>
            <p className="text-xs" style={{ color: 'var(--outline)' }}>Coming soon — dark mode is currently the only theme</p>
          </div>
          <button
            onClick={() => setDarkMode(v => !v)}
            className="relative w-11 h-6 rounded-full transition-colors cursor-pointer"
            style={{ background: darkMode ? 'var(--tertiary)' : 'var(--outline-variant)' }}
          >
            <span className={`absolute top-0.5 w-5 h-5 rounded-full bg-white transition-transform ${darkMode ? 'left-[22px]' : 'left-0.5'}`} />
          </button>
        </div>
      </div>

      {/* Notifications */}
      <div>
        <h3 className="text-sm font-medium mb-3" style={{ color: 'var(--on-surface)' }}>Notifications</h3>
        <div className="space-y-2">
          {([['alerts', 'Alert Notifications', 'Receive alerts for risk breaches and anomalies'], ['trades', 'Trade Notifications', 'Notify on order fills and agent executions'], ['system', 'System Notifications', 'Infrastructure and health updates']] as const).map(([key, title, desc]) => (
            <div key={key} className="flex items-center justify-between px-4 py-3" style={{ background: 'var(--surface)' }}>
              <div>
                <p className="text-sm" style={{ color: 'var(--on-surface)' }}>{title}</p>
                <p className="text-xs" style={{ color: 'var(--outline)' }}>{desc}</p>
              </div>
              <button
                onClick={() => setNotifications(n => ({ ...n, [key]: !n[key] }))}
                className="relative w-11 h-6 rounded-full transition-colors cursor-pointer"
                style={{ background: notifications[key] ? 'var(--primary)' : 'var(--outline-variant)' }}
              >
                <span className={`absolute top-0.5 w-5 h-5 rounded-full bg-white transition-transform ${notifications[key] ? 'left-[22px]' : 'left-0.5'}`} />
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function DeltaExchangeSection() {
  const cfg = deltaApi.getConfig();
  const [apiKey, setApiKey] = useState(cfg.apiKey || '');
  const [apiSecret, setApiSecret] = useState(cfg.apiSecret || '');
  const [useTestnet, setUseTestnet] = useState(cfg.useTestnet !== false);
  const [saved, setSaved] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<string | null>(null);

  const handleSave = () => {
    deltaApi.saveConfig(apiKey, apiSecret, useTestnet);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const handleTest = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      deltaApi.saveConfig(apiKey, apiSecret, useTestnet);
      const balances = await deltaApi.getBalances();
      const usdt = balances.find((b: { asset_symbol: string }) => b.asset_symbol === 'USDT');
      setTestResult(`Connected! USDT Balance: ${usdt ? parseFloat(usdt.available_balance as string).toFixed(2) : '0.00'}`);
    } catch (err) {
      setTestResult(`Failed: ${(err as Error).message}`);
    } finally {
      setTesting(false);
    }
  };

  const handleClear = () => {
    deltaApi.clearConfig();
    setApiKey('');
    setApiSecret('');
    setTestResult(null);
  };

  return (
    <div className="space-y-6">
      <p className="text-sm leading-relaxed" style={{ color: 'var(--on-surface-variant)' }}>
        Connect your Delta Exchange account for live trading execution. Agents will place orders with stoploss and take-profit parameters directly on Delta Exchange.
      </p>

      {/* Environment toggle */}
      <div className="flex items-center justify-between px-4 py-3" style={{ background: 'var(--surface)' }}>
        <div>
          <p className="text-sm" style={{ color: 'var(--on-surface)' }}>Testnet Mode</p>
          <p className="text-xs" style={{ color: 'var(--outline)' }}>Use testnet for paper trading (recommended for testing)</p>
        </div>
        <button
          onClick={() => setUseTestnet(v => !v)}
          className="relative w-11 h-6 rounded-full transition-colors cursor-pointer"
          style={{ background: useTestnet ? 'var(--tertiary)' : 'var(--outline-variant)' }}
        >
          <span className={`absolute top-0.5 w-5 h-5 rounded-full bg-white transition-transform ${useTestnet ? 'left-[22px]' : 'left-0.5'}`} />
        </button>
      </div>

      {/* API Key */}
      <div>
        <label className="input-label">API Key</label>
        <input
          type="text"
          value={apiKey}
          onChange={e => setApiKey(e.target.value)}
          placeholder="Enter your Delta Exchange API key"
          className="input-terminal"
          style={{ width: '100%' }}
        />
      </div>

      {/* API Secret */}
      <div>
        <label className="input-label">API Secret</label>
        <input
          type="password"
          value={apiSecret}
          onChange={e => setApiSecret(e.target.value)}
          placeholder="Enter your API secret"
          className="input-terminal"
          style={{ width: '100%' }}
        />
      </div>

      {/* Actions */}
      <div className="flex items-center gap-3">
        <button onClick={handleSave} className="btn-primary">
          {saved ? <><Check size={14} /> Saved</> : <><Save size={14} /> Save Keys</>}
        </button>
        <button
          onClick={handleTest}
          disabled={!apiKey || !apiSecret || testing}
          className="btn-ghost"
          style={{ opacity: !apiKey || !apiSecret ? 0.4 : 1 }}
        >
          {testing ? <><Loader2 size={14} className="animate-spin" /> Testing...</> : 'Test Connection'}
        </button>
        <button onClick={handleClear} className="btn-ghost" style={{ color: 'var(--error)' }}>
          Clear
        </button>
      </div>

      {/* Test result */}
      {testResult && (
        <div className="p-3" style={{
          background: testResult.startsWith('Connected') ? 'rgba(0, 228, 121, 0.08)' : 'rgba(255, 180, 171, 0.08)',
          borderLeft: `3px solid ${testResult.startsWith('Connected') ? 'var(--tertiary)' : 'var(--error)'}`,
        }}>
          <p className="text-sm font-mono" style={{ color: testResult.startsWith('Connected') ? 'var(--tertiary)' : 'var(--error)' }}>
            {testResult}
          </p>
        </div>
      )}

      {/* Info */}
      <div className="p-4 space-y-2" style={{ background: 'var(--surface)', borderLeft: '3px solid var(--primary)' }}>
        <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--primary)' }}>Setup Guide</p>
        <ol className="text-xs space-y-1 list-decimal list-inside" style={{ color: 'var(--on-surface-variant)' }}>
          <li>Go to Delta Exchange → Account → API Keys</li>
          <li>Create a new API key with trading permissions</li>
          <li>Whitelist your IP address</li>
          <li>Copy the API key and secret here</li>
          <li>Enable Testnet mode for paper trading first</li>
        </ol>
      </div>
    </div>
  );
}

function ApiSection() {
  const { data: health } = useQuery<Record<string, unknown>>({
    queryKey: ['health'],
    queryFn: api.getHealth,
    refetchInterval: 30_000,
  });

  const isUp = health?.status === 'UP';

  return (
    <div className="space-y-4">
      <div className="p-4 space-y-3" style={{ background: 'var(--surface)' }}>
        <div className="flex items-center justify-between">
          <span className="text-sm" style={{ color: 'var(--on-surface)' }}>Backend API</span>
          <div className="flex items-center gap-2">
            <span className={`w-2 h-2 rounded-full ${isUp ? 'animate-pulse' : ''}`} style={{ background: isUp ? 'var(--tertiary)' : 'var(--error)' }} />
            <span className="text-xs" style={{ color: 'var(--on-surface-variant)' }}>{isUp ? 'Connected' : 'Disconnected'}</span>
          </div>
        </div>
        <div>
          <span className="text-xs" style={{ color: 'var(--outline)' }}>Base URL</span>
          <p className="text-sm font-mono" style={{ color: 'var(--on-surface)' }}>/api/v1</p>
        </div>
        <div>
          <span className="text-xs" style={{ color: 'var(--outline)' }}>WebSocket</span>
          <p className="text-sm font-mono" style={{ color: 'var(--on-surface)' }}>/ws</p>
        </div>
        <div>
          <span className="text-xs" style={{ color: 'var(--outline)' }}>Auth</span>
          <div className="flex items-center gap-2 mt-0.5">
            <Check size={14} style={{ color: 'var(--tertiary)' }} />
            <span className="text-sm" style={{ color: 'var(--on-surface)' }}>Bearer Token (JWT)</span>
          </div>
        </div>
      </div>
    </div>
  );
}

function AboutSection() {
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-4">
        <div className="w-12 h-12 bg-gradient-to-br from-[#00ff88] to-[#3b82f6] flex items-center justify-center text-lg font-bold" style={{ color: 'var(--surface)' }}>
          Q
        </div>
        <div>
          <h2 className="text-xl font-bold" style={{ color: 'var(--on-surface)' }}>QuantEdge Platform</h2>
          <p className="text-sm" style={{ color: 'var(--outline)' }}>AI-Powered Quantitative Trading</p>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-3">
        {[
          ['Version', '1.0.0'],
          ['Frontend', 'React 19 + TypeScript + Vite'],
          ['Backend', 'Spring Boot + PostgreSQL'],
          ['ML Engine', 'Python + XGBoost + LSTM'],
        ].map(([k, v]) => (
          <div key={k} className="p-3" style={{ background: 'var(--surface)' }}>
            <span className="text-[10px]" style={{ color: 'var(--outline)' }}>{k}</span>
            <p className="text-sm mt-0.5" style={{ color: 'var(--on-surface)' }}>{v}</p>
          </div>
        ))}
      </div>
      <p className="text-xs leading-relaxed" style={{ color: 'var(--outline)' }}>
        QuantEdge is a full-stack AI-driven quantitative trading platform featuring autonomous trading agents,
        multi-model ML predictions, comprehensive risk management, and real-time portfolio analytics.
      </p>
    </div>
  );
}

export default function SettingsPage() {
  const [active, setActive] = useState<SectionKey>('firm');

  const content: Record<SectionKey, React.ReactNode> = {
    firm:        <FirmSection />,
    user:        <UserSection />,
    preferences: <PreferencesSection />,
    delta:       <DeltaExchangeSection />,
    api:         <ApiSection />,
    about:       <AboutSection />,
  };

  return (
    <div className="max-w-[1200px] mx-auto">
      <PageHeader title="Settings" subtitle="SYSTEM CONFIGURATION" />

      <div className="flex gap-6 flex-col lg:flex-row">
        {/* Sidebar nav */}
        <nav className="lg:w-56 shrink-0 space-y-1">
          {SECTIONS.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              onClick={() => setActive(key)}
              className="flex items-center gap-3 w-full px-3 py-2.5 text-sm font-medium transition-colors cursor-pointer"
              style={active === key
                ? { background: 'var(--surface-container-low)', color: 'var(--on-surface)' }
                : { color: 'var(--outline)' }
              }
            >
              <Icon size={18} />
              {label}
            </button>
          ))}
        </nav>

        {/* Content */}
        <div className="flex-1 p-6" style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}>
          <h2 className="text-lg font-semibold mb-5" style={{ color: 'var(--on-surface)' }}>
            {SECTIONS.find(s => s.key === active)?.label}
          </h2>
          {content[active]}
        </div>
      </div>
    </div>
  );
}
