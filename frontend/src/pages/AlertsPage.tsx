import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Bell, Check, CheckCheck, Loader2 } from 'lucide-react';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import type { Alert } from '@/types';

const SEVERITY_STYLES: Record<string, React.CSSProperties> = {
  LOW:      { background: 'rgba(59,130,246,0.15)', color: 'var(--primary)', borderColor: 'rgba(59,130,246,0.3)' },
  MEDIUM:   { background: 'rgba(251,191,36,0.15)', color: '#fbbf24', borderColor: 'rgba(251,191,36,0.3)' },
  HIGH:     { background: 'rgba(249,115,22,0.15)', color: '#f97316', borderColor: 'rgba(249,115,22,0.3)' },
  CRITICAL: { background: 'rgba(239,68,68,0.15)', color: 'var(--error)', borderColor: 'rgba(239,68,68,0.3)', boxShadow: '0 0 12px rgba(239,68,68,0.2)' },
};

export default function AlertsPage() {
  const qc = useQueryClient();
  const [tab, setTab] = useState<'all' | 'unacked'>('all');

  const { data: allAlerts = [], isLoading: loadingAll } = useQuery<Alert[]>({
    queryKey: ['alerts'],
    queryFn: api.getAlerts,
    refetchInterval: 10_000,
  });

  const { data: unackedAlerts = [], isLoading: loadingUnacked } = useQuery<Alert[]>({
    queryKey: ['alerts-unacked'],
    queryFn: api.getUnacknowledgedAlerts,
    refetchInterval: 10_000,
  });

  const ackMut = useMutation({
    mutationFn: (id: number) => api.acknowledgeAlert(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['alerts'] });
      qc.invalidateQueries({ queryKey: ['alerts-unacked'] });
    },
  });

  const bulkAckMut = useMutation({
    mutationFn: async () => {
      const ids = unackedAlerts.map(a => a.id);
      await Promise.all(ids.map(id => api.acknowledgeAlert(id)));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['alerts'] });
      qc.invalidateQueries({ queryKey: ['alerts-unacked'] });
    },
  });

  const alerts = tab === 'all' ? allAlerts : unackedAlerts;
  const isLoading = tab === 'all' ? loadingAll : loadingUnacked;

  return (
    <div className="space-y-6 max-w-[1000px] mx-auto">
      <PageHeader title="Alerts" subtitle="RISK BREACH // SYSTEM NOTIFICATIONS">
        <div className="flex items-center gap-3">
          {unackedAlerts.length > 0 && (
            <span className="text-xs px-2 py-0.5 rounded-full font-medium" style={{ background: 'rgba(239,68,68,0.15)', color: 'var(--error)' }}>
              {unackedAlerts.length} unread
            </span>
          )}
          {tab === 'unacked' && unackedAlerts.length > 0 && (
            <button
              onClick={() => bulkAckMut.mutate()}
              disabled={bulkAckMut.isPending}
              className="flex items-center gap-2 px-4 py-2 text-sm font-medium transition-colors disabled:opacity-40 cursor-pointer"
              style={{ background: 'rgba(0,255,136,0.15)', color: 'var(--tertiary)' }}
            >
              {bulkAckMut.isPending ? <Loader2 size={14} className="animate-spin" /> : <CheckCheck size={16} />}
              Acknowledge All
            </button>
          )}
        </div>
      </PageHeader>

      {/* Tabs */}
      <div className="flex gap-1 p-1 w-fit" style={{ background: 'var(--surface)' }}>
        {(['all', 'unacked'] as const).map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className="qe-tab px-4 py-2 text-sm font-medium transition-colors cursor-pointer"
            style={tab === t
              ? { background: 'var(--surface-container-low)', color: 'var(--on-surface)' }
              : { color: 'var(--outline)' }
            }
          >
            {t === 'all' ? 'All' : 'Unacknowledged'}
          </button>
        ))}
      </div>

      {/* Alert list */}
      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => <div key={i} className="h-20 skeleton" />)}
        </div>
      ) : alerts.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20" style={{ color: 'var(--outline)' }}>
          <Bell size={48} className="mb-3 opacity-40" />
          <p className="text-lg">{tab === 'all' ? 'No alerts' : 'All caught up!'}</p>
          <p className="text-sm mt-1">
            {tab === 'all' ? 'Alerts will appear here when triggered' : 'No unacknowledged alerts'}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {alerts.map(alert => (
            <div
              key={alert.id}
              className="p-4 flex items-start gap-4 transition-colors"
              style={{
                background: 'var(--surface-container-low)',
                border: '1px solid var(--outline-variant)',
                opacity: alert.acknowledged ? 0.6 : 1,
              }}
            >
              {/* Severity badge */}
              <span
                className="text-[10px] font-bold uppercase px-2.5 py-1 rounded-full shrink-0 mt-0.5"
                style={{ ...SEVERITY_STYLES[alert.severity], borderWidth: '1px', borderStyle: 'solid' }}
              >
                {alert.severity}
              </span>

              {/* Content */}
              <div className="flex-1 min-w-0">
                <p className="text-sm leading-relaxed" style={{ color: 'var(--on-surface)' }}>{alert.message}</p>
                <div className="flex items-center gap-3 mt-1.5">
                  <span className="text-xs" style={{ color: 'var(--outline)' }}>{new Date(alert.createdAt).toLocaleString()}</span>
                  {alert.type && <span className="text-xs px-2 py-0.5" style={{ color: 'var(--outline)', background: 'var(--surface)' }}>{alert.type}</span>}
                </div>
              </div>

              {/* Acknowledge */}
              {!alert.acknowledged && (
                <button
                  onClick={() => ackMut.mutate(alert.id)}
                  disabled={ackMut.isPending}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium transition-colors shrink-0 cursor-pointer"
                  style={{ background: 'rgba(0,255,136,0.15)', color: 'var(--tertiary)' }}
                >
                  <Check size={14} /> Ack
                </button>
              )}
              {alert.acknowledged && (
                <span className="text-xs flex items-center gap-1 shrink-0" style={{ color: 'var(--outline)' }}>
                  <Check size={14} /> Done
                </span>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
