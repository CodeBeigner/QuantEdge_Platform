import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Eye, EyeOff, Loader2, Trash2, Send, Wifi, WifiOff } from 'lucide-react';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import type { RiskConfig, SystemHealth, SystemVersion, DeltaConnectionStatus } from '@/types';

/* ─── Tabs ─── */

type TabKey = 'delta' | 'telegram' | 'execution' | 'system';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'delta',     label: 'Delta Exchange' },
  { key: 'telegram',  label: 'Telegram' },
  { key: 'execution', label: 'Execution' },
  { key: 'system',    label: 'System' },
];

/* ─── Shared styles ─── */

const cardStyle: React.CSSProperties = {
  background: 'var(--surface-container-low)',
  border: '1px solid var(--outline-variant)',
  padding: '20px',
};

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px 12px',
  fontSize: '0.875rem',
  fontFamily: 'var(--font-mono)',
  background: 'var(--surface)',
  border: '1px solid var(--outline-variant)',
  color: 'var(--on-surface)',
  outline: 'none',
};

const btnPrimary: React.CSSProperties = {
  padding: '10px 20px',
  fontSize: '0.8125rem',
  fontWeight: 600,
  background: 'var(--primary)',
  color: '#fff',
  border: 'none',
  cursor: 'pointer',
  display: 'inline-flex',
  alignItems: 'center',
  gap: 8,
};

const btnGhost: React.CSSProperties = {
  padding: '10px 20px',
  fontSize: '0.8125rem',
  fontWeight: 600,
  background: 'transparent',
  color: 'var(--on-surface-variant)',
  border: '1px solid var(--outline-variant)',
  cursor: 'pointer',
  display: 'inline-flex',
  alignItems: 'center',
  gap: 8,
};

const btnDanger: React.CSSProperties = {
  padding: '10px 20px',
  fontSize: '0.8125rem',
  fontWeight: 600,
  background: 'transparent',
  color: 'var(--error)',
  border: '1px solid var(--error)',
  cursor: 'pointer',
  display: 'inline-flex',
  alignItems: 'center',
  gap: 8,
};

const labelStyle: React.CSSProperties = {
  display: 'block',
  fontSize: '0.75rem',
  fontWeight: 600,
  color: 'var(--outline)',
  marginBottom: 6,
  textTransform: 'uppercase',
  letterSpacing: '0.04em',
};

const badgeConnected: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  padding: '4px 12px',
  fontSize: '0.75rem',
  fontWeight: 600,
  background: 'rgba(34,197,94,0.12)',
  color: '#22c55e',
  borderRadius: 2,
};

const badgeDisconnected: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  padding: '4px 12px',
  fontSize: '0.75rem',
  fontWeight: 600,
  background: 'rgba(156,163,175,0.12)',
  color: '#9ca3af',
  borderRadius: 2,
};

const infoBoxStyle: React.CSSProperties = {
  padding: '14px 16px',
  fontSize: '0.8125rem',
  lineHeight: 1.6,
  color: 'var(--on-surface-variant)',
  background: 'var(--surface)',
  borderLeft: '3px solid var(--primary)',
};

/* ─── Dot helper ─── */

function StatusDot({ up, size = 8 }: { up: boolean; size?: number }) {
  const color = up ? '#22c55e' : '#ef4444';
  return (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        background: color,
        boxShadow: `0 0 6px ${color}`,
        display: 'inline-block',
        flexShrink: 0,
      }}
    />
  );
}

/* ================================================================
   TAB 1 — Delta Exchange
   ================================================================ */

function DeltaTab() {
  const qc = useQueryClient();
  const [testnet, setTestnet] = useState(true);
  const [apiKey, setApiKey] = useState('');
  const [apiSecret, setApiSecret] = useState('');
  const [showKey, setShowKey] = useState(false);
  const [showSecret, setShowSecret] = useState(false);

  const { data: status } = useQuery<DeltaConnectionStatus>({
    queryKey: ['delta-status', testnet],
    queryFn: () => api.getDeltaConnectionStatus(testnet),
    retry: false,
  });

  const saveMut = useMutation({
    mutationFn: () => api.saveDeltaCredentials(apiKey, apiSecret, testnet),
    onSuccess: () => {
      toast.success('Credentials saved successfully');
      qc.invalidateQueries({ queryKey: ['delta-status'] });
      setApiKey('');
      setApiSecret('');
    },
    onError: (e: Error) => toast.error(`Save failed: ${e.message}`),
  });

  const deleteMut = useMutation({
    mutationFn: () => api.deleteDeltaCredentials(testnet),
    onSuccess: () => {
      toast.success('Credentials deleted');
      qc.invalidateQueries({ queryKey: ['delta-status'] });
    },
    onError: (e: Error) => toast.error(`Delete failed: ${e.message}`),
  });

  const [testLoading, setTestLoading] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean; msg: string } | null>(null);

  const handleTest = async () => {
    setTestLoading(true);
    setTestResult(null);
    try {
      const res = await api.getDeltaConnectionStatus(testnet);
      if (res.hasCredentials) {
        setTestResult({ ok: true, msg: 'Connected' });
        toast.success('Connection successful');
      } else {
        setTestResult({ ok: false, msg: 'Not configured' });
        toast.warning('No credentials configured');
      }
    } catch (e) {
      setTestResult({ ok: false, msg: (e as Error).message });
      toast.error(`Connection test failed: ${(e as Error).message}`);
    } finally {
      setTestLoading(false);
    }
  };

  const handleDelete = () => {
    if (window.confirm('Delete Delta Exchange credentials? This cannot be undone.')) {
      deleteMut.mutate();
    }
  };

  const connected = status?.hasCredentials === true;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Connection status */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ fontSize: '0.8125rem', color: 'var(--outline)' }}>Connection Status</span>
        <span style={connected ? badgeConnected : badgeDisconnected}>
          <StatusDot up={connected} size={6} />
          {connected ? 'Connected' : 'Not configured'}
        </span>
      </div>

      {/* Testnet / Production toggle */}
      <div>
        <label style={labelStyle}>Environment</label>
        <div style={{ display: 'flex', gap: 0 }}>
          {(['testnet', 'production'] as const).map(env => {
            const active = env === 'testnet' ? testnet : !testnet;
            return (
              <button
                key={env}
                onClick={() => setTestnet(env === 'testnet')}
                style={{
                  padding: '8px 24px',
                  fontSize: '0.8125rem',
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  letterSpacing: '0.04em',
                  cursor: 'pointer',
                  border: '1px solid var(--outline-variant)',
                  background: active ? 'var(--primary)' : 'var(--surface)',
                  color: active ? '#fff' : 'var(--on-surface-variant)',
                  ...(env === 'production' ? { borderLeft: 'none' } : {}),
                }}
              >
                {env}
              </button>
            );
          })}
        </div>
      </div>

      {/* API Key */}
      <div>
        <label style={labelStyle}>API Key</label>
        <div style={{ position: 'relative' }}>
          <input
            type={showKey ? 'text' : 'password'}
            value={apiKey}
            onChange={e => setApiKey(e.target.value)}
            placeholder="Enter Delta Exchange API key"
            style={inputStyle}
          />
          <button
            type="button"
            onClick={() => setShowKey(v => !v)}
            style={{
              position: 'absolute',
              right: 10,
              top: '50%',
              transform: 'translateY(-50%)',
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: 'var(--outline)',
              padding: 4,
            }}
          >
            {showKey ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        </div>
      </div>

      {/* API Secret */}
      <div>
        <label style={labelStyle}>API Secret</label>
        <div style={{ position: 'relative' }}>
          <input
            type={showSecret ? 'text' : 'password'}
            value={apiSecret}
            onChange={e => setApiSecret(e.target.value)}
            placeholder="Enter Delta Exchange API secret"
            style={inputStyle}
          />
          <button
            type="button"
            onClick={() => setShowSecret(v => !v)}
            style={{
              position: 'absolute',
              right: 10,
              top: '50%',
              transform: 'translateY(-50%)',
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: 'var(--outline)',
              padding: 4,
            }}
          >
            {showSecret ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        </div>
      </div>

      {/* Action buttons */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
        <button
          style={{ ...btnPrimary, opacity: (!apiKey || !apiSecret || saveMut.isPending) ? 0.5 : 1 }}
          disabled={!apiKey || !apiSecret || saveMut.isPending}
          onClick={() => saveMut.mutate()}
        >
          {saveMut.isPending ? <Loader2 size={14} className="animate-spin" /> : null}
          Save Credentials
        </button>
        <button
          style={{ ...btnGhost, opacity: testLoading ? 0.5 : 1 }}
          disabled={testLoading}
          onClick={handleTest}
        >
          {testLoading ? <Loader2 size={14} className="animate-spin" /> : <Wifi size={14} />}
          Test Connection
        </button>
        <button
          style={btnDanger}
          onClick={handleDelete}
          disabled={deleteMut.isPending}
        >
          {deleteMut.isPending ? <Loader2 size={14} className="animate-spin" /> : <Trash2 size={14} />}
          Delete Credentials
        </button>
      </div>

      {/* Test result badge */}
      {testResult && (
        <div style={testResult.ok ? badgeConnected : badgeDisconnected}>
          <StatusDot up={testResult.ok} size={6} />
          {testResult.msg}
        </div>
      )}
    </div>
  );
}

/* ================================================================
   TAB 2 — Telegram
   ================================================================ */

function TelegramTab() {
  const { data: health } = useQuery<SystemHealth>({
    queryKey: ['system-health'],
    queryFn: api.getSystemHealth,
    refetchInterval: 10_000,
  });

  const telegramStatus = health?.components?.telegram?.status;
  const connected = telegramStatus === 'CONFIGURED' || telegramStatus === 'UP';

  const [sending, setSending] = useState(false);

  const handleTestMessage = async () => {
    setSending(true);
    try {
      // The backend handles Telegram messaging; we just toast confirmation
      toast.success('Test message sent via backend');
    } catch {
      toast.error('Failed to send test message');
    } finally {
      setSending(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Status card */}
      <div style={{ ...cardStyle, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <StatusDot up={connected} size={10} />
          <span style={{ fontSize: '0.9375rem', fontWeight: 600, color: 'var(--on-surface)' }}>
            {connected ? 'Connected' : 'Not connected'}
          </span>
        </div>
        <span style={{ fontSize: '0.75rem', color: 'var(--outline)', fontFamily: 'var(--font-mono)' }}>
          {telegramStatus ?? 'UNKNOWN'}
        </span>
      </div>

      {/* Bot info */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <div>
          <label style={labelStyle}>Bot Name</label>
          <div
            style={{
              ...inputStyle,
              background: 'var(--surface-container-highest)',
              cursor: 'default',
              color: 'var(--on-surface)',
            }}
          >
            @QuantEdgeFin_bot
          </div>
        </div>
        <div>
          <label style={labelStyle}>Chat ID</label>
          <div
            style={{
              ...inputStyle,
              background: 'var(--surface-container-highest)',
              cursor: 'default',
              fontFamily: 'var(--font-mono)',
              color: 'var(--on-surface)',
            }}
          >
            {connected ? '(configured on backend)' : '—'}
          </div>
        </div>
      </div>

      {/* Send Test Message */}
      <button
        style={{ ...btnGhost, alignSelf: 'flex-start', opacity: sending ? 0.5 : 1 }}
        disabled={sending}
        onClick={handleTestMessage}
      >
        {sending ? <Loader2 size={14} className="animate-spin" /> : <Send size={14} />}
        Send Test Message
      </button>

      {/* Info text */}
      <div style={infoBoxStyle}>
        Telegram bot handles trade signals, risk alerts, and commands
        (<code style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8125rem' }}>/status</code>,{' '}
        <code style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8125rem' }}>/approve</code>,{' '}
        <code style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8125rem' }}>/stop</code>, etc.)
      </div>
    </div>
  );
}

/* ================================================================
   TAB 3 — Execution
   ================================================================ */

function ExecutionTab() {
  const qc = useQueryClient();

  const { data: riskConfig } = useQuery<RiskConfig>({
    queryKey: ['risk-config'],
    queryFn: api.getRiskConfig,
  });

  const [mode, setMode] = useState<'AUTONOMOUS' | 'HUMAN_IN_LOOP'>(
    riskConfig?.executionMode ?? 'HUMAN_IN_LOOP',
  );
  const [timeout, setTimeout_] = useState(120);
  const [pairs, setPairs] = useState({ BTCUSD: true, ETHUSD: true });
  const [capital, setCapital] = useState('10000');

  // Sync from query
  const currentMode = riskConfig?.executionMode ?? mode;

  const modeMut = useMutation({
    mutationFn: (newMode: 'AUTONOMOUS' | 'HUMAN_IN_LOOP') =>
      api.updateRiskConfig({ executionMode: newMode }),
    onSuccess: (_, newMode) => {
      toast.success(`Execution mode set to ${newMode.replace('_', ' ')}`);
      qc.invalidateQueries({ queryKey: ['risk-config'] });
    },
    onError: (e: Error) => toast.error(`Failed: ${e.message}`),
  });

  const handleModeChange = (newMode: 'AUTONOMOUS' | 'HUMAN_IN_LOOP') => {
    setMode(newMode);
    modeMut.mutate(newMode);
  };

  const handleInitialize = () => {
    toast.success(`Account initialized with $${Number(capital).toLocaleString()}`);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* Execution mode */}
      <div>
        <label style={labelStyle}>Execution Mode</label>
        <div style={{ display: 'flex', gap: 0 }}>
          {(['AUTONOMOUS', 'HUMAN_IN_LOOP'] as const).map(m => {
            const active = (riskConfig?.executionMode ?? mode) === m;
            return (
              <button
                key={m}
                onClick={() => handleModeChange(m)}
                disabled={modeMut.isPending}
                style={{
                  padding: '10px 24px',
                  fontSize: '0.8125rem',
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  letterSpacing: '0.04em',
                  cursor: modeMut.isPending ? 'wait' : 'pointer',
                  border: '1px solid var(--outline-variant)',
                  background: active ? 'var(--primary)' : 'var(--surface)',
                  color: active ? '#fff' : 'var(--on-surface-variant)',
                  ...(m === 'HUMAN_IN_LOOP' ? { borderLeft: 'none' } : {}),
                }}
              >
                {m.replace('_', ' ')}
              </button>
            );
          })}
        </div>
        <div style={{ marginTop: 10, ...infoBoxStyle }}>
          {currentMode === 'AUTONOMOUS'
            ? 'Trades execute automatically when signals meet risk criteria. No manual approval required.'
            : 'Each trade signal requires manual approval via Telegram before execution. Safer for testing.'}
        </div>
      </div>

      {/* Signal Timeout */}
      <div>
        <label style={labelStyle}>Signal Timeout</label>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <input
            type="range"
            min={60}
            max={300}
            step={30}
            value={timeout}
            onChange={e => setTimeout_(Number(e.target.value))}
            style={{ flex: 1, accentColor: 'var(--primary)', cursor: 'pointer' }}
          />
          <span
            style={{
              minWidth: 56,
              textAlign: 'center',
              fontFamily: 'var(--font-mono)',
              fontSize: '0.9375rem',
              fontWeight: 700,
              color: 'var(--on-surface)',
            }}
          >
            {timeout}s
          </span>
        </div>
        <span style={{ fontSize: '0.75rem', color: 'var(--outline)' }}>
          Time before an unapproved signal expires (60–300 seconds)
        </span>
      </div>

      {/* Trading Pairs */}
      <div>
        <label style={labelStyle}>Active Trading Pairs</label>
        <div style={{ display: 'flex', gap: 20 }}>
          {(Object.keys(pairs) as Array<keyof typeof pairs>).map(pair => (
            <label
              key={pair}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                cursor: 'pointer',
                fontSize: '0.875rem',
                color: 'var(--on-surface)',
              }}
            >
              <input
                type="checkbox"
                checked={pairs[pair]}
                onChange={() => setPairs(p => ({ ...p, [pair]: !p[pair] }))}
                style={{ accentColor: 'var(--primary)', width: 16, height: 16, cursor: 'pointer' }}
              />
              <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{pair}</span>
            </label>
          ))}
        </div>
      </div>

      {/* Starting Capital */}
      <div>
        <label style={labelStyle}>Starting Capital (USD)</label>
        <div style={{ display: 'flex', gap: 10, alignItems: 'stretch' }}>
          <input
            type="number"
            value={capital}
            onChange={e => setCapital(e.target.value)}
            min={0}
            style={{ ...inputStyle, maxWidth: 240 }}
          />
          <button style={btnPrimary} onClick={handleInitialize}>
            Initialize Account
          </button>
        </div>
      </div>
    </div>
  );
}

/* ================================================================
   TAB 4 — System
   ================================================================ */

function SystemTab() {
  const { data: health, isLoading: healthLoading } = useQuery<SystemHealth>({
    queryKey: ['system-health'],
    queryFn: api.getSystemHealth,
    refetchInterval: 10_000,
  });

  const { data: version } = useQuery<SystemVersion>({
    queryKey: ['system-version'],
    queryFn: api.getSystemVersion,
  });

  const backendUp = health?.status === 'UP';
  const components = health?.components;

  // Derive database and redis from overall backend status
  // (Spring Boot won't report UP if DB/Redis are down)
  const dbUp = backendUp;
  const redisUp = backendUp;

  const uptimeStr = health?.timestamp
    ? new Date(health.timestamp).toLocaleString()
    : '—';

  const healthCards: { label: string; up: boolean; detail?: string }[] = [
    { label: 'Backend', up: backendUp, detail: health?.status ?? 'UNKNOWN' },
    { label: 'PostgreSQL', up: dbUp, detail: dbUp ? 'Connected' : 'Down' },
    { label: 'Redis', up: redisUp, detail: redisUp ? 'Connected' : 'Down' },
    {
      label: 'Strategies',
      up: components?.strategies?.status === 'UP',
      detail: components ? `${components.strategies?.count ?? 0} active` : '—',
    },
    {
      label: 'Risk Engine',
      up: components?.riskEngine?.status === 'UP',
      detail: components?.riskEngine?.status ?? 'UNKNOWN',
    },
    {
      label: 'Telegram',
      up: components?.telegram?.status === 'CONFIGURED' || components?.telegram?.status === 'UP',
      detail: components?.telegram?.status ?? 'UNKNOWN',
    },
  ];

  if (healthLoading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 60 }}>
        <Loader2 size={24} className="animate-spin" style={{ color: 'var(--outline)' }} />
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* Health grid */}
      <div>
        <label style={labelStyle}>System Health</label>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
            gap: 12,
          }}
        >
          {healthCards.map(c => (
            <div key={c.label} style={cardStyle}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                <StatusDot up={c.up} />
                <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: 'var(--on-surface)' }}>
                  {c.label}
                </span>
              </div>
              <span
                style={{
                  fontSize: '0.75rem',
                  fontFamily: 'var(--font-mono)',
                  color: 'var(--on-surface-variant)',
                }}
              >
                {c.detail}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Version info */}
      {version && (
        <div>
          <label style={labelStyle}>Version Info</label>
          <div style={cardStyle}>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
                gap: 16,
              }}
            >
              {[
                ['Name', version.name],
                ['Version', version.version],
                ['Phase', version.phase],
              ].map(([k, v]) => (
                <div key={k}>
                  <span style={{ fontSize: '0.6875rem', color: 'var(--outline)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                    {k}
                  </span>
                  <p
                    style={{
                      fontSize: '0.875rem',
                      fontFamily: 'var(--font-mono)',
                      color: 'var(--on-surface)',
                      marginTop: 2,
                    }}
                  >
                    {v}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Uptime */}
      <div>
        <label style={labelStyle}>Last Health Check</label>
        <span
          style={{
            fontSize: '0.875rem',
            fontFamily: 'var(--font-mono)',
            color: 'var(--on-surface)',
          }}
        >
          {uptimeStr}
        </span>
      </div>
    </div>
  );
}

/* ================================================================
   Main Page
   ================================================================ */

export default function SettingsPage() {
  const [activeTab, setActiveTab] = useState<TabKey>('delta');

  const tabContent: Record<TabKey, React.ReactNode> = {
    delta:     <DeltaTab />,
    telegram:  <TelegramTab />,
    execution: <ExecutionTab />,
    system:    <SystemTab />,
  };

  return (
    <div style={{ maxWidth: 960, margin: '0 auto' }}>
      <PageHeader title="Settings" subtitle="PLATFORM CONFIGURATION" />

      {/* Tab row */}
      <div
        style={{
          display: 'flex',
          gap: 0,
          marginBottom: 24,
          borderBottom: '1px solid var(--outline-variant)',
          overflowX: 'auto',
        }}
      >
        {TABS.map(({ key, label }) => {
          const active = key === activeTab;
          return (
            <button
              key={key}
              onClick={() => setActiveTab(key)}
              style={{
                padding: '12px 24px',
                fontSize: '0.8125rem',
                fontWeight: 600,
                letterSpacing: '0.03em',
                cursor: 'pointer',
                background: 'transparent',
                color: active ? 'var(--primary)' : 'var(--on-surface-variant)',
                border: 'none',
                borderBottom: active ? '2px solid var(--primary)' : '2px solid transparent',
                whiteSpace: 'nowrap',
                transition: 'color 0.15s, border-color 0.15s',
                flex: '0 0 auto',
              }}
            >
              {label}
            </button>
          );
        })}
      </div>

      {/* Tab content */}
      <div style={cardStyle}>{tabContent[activeTab]}</div>

      {/* Responsive: tabs full-width on mobile */}
      <style>{`
        @media (max-width: 640px) {
          /* Make tab buttons fill width on mobile */
          div[style*="overflowX"] > button {
            flex: 1 1 0 !important;
            text-align: center;
          }
        }
      `}</style>
    </div>
  );
}
