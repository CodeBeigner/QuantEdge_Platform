import { useEffect, useRef } from 'react';
import { MaterialIcon } from '@/components/ui/MaterialIcon';
import { useNotificationStore } from '@/stores/notificationStore';
import type { Notification } from '@/types';
import { formatDistanceToNow } from 'date-fns';

interface NotificationPanelProps {
  open: boolean;
  onClose: () => void;
}

const typeConfig: Record<
  Notification['type'],
  { icon: string; color: string; bg: string }
> = {
  info: { icon: 'info', color: 'var(--primary)', bg: 'rgba(173, 198, 255, 0.1)' },
  success: { icon: 'check_circle', color: 'var(--tertiary)', bg: 'rgba(0, 228, 121, 0.1)' },
  warning: { icon: 'warning', color: '#fbbf24', bg: 'rgba(251, 191, 36, 0.1)' },
  error: { icon: 'error', color: 'var(--error)', bg: 'rgba(255, 180, 171, 0.1)' },
};

export default function NotificationPanel({ open, onClose }: NotificationPanelProps) {
  const { notifications, markRead, markAllRead, clearAll } = useNotificationStore();
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) onClose();
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open, onClose]);

  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [open, onClose]);

  return (
    <>
      {open && <div style={{ position: 'fixed', inset: 0, zIndex: 40, background: 'rgba(14, 19, 32, 0.4)' }} onClick={onClose} />}

      <div
        ref={panelRef}
        style={{
          position: 'fixed', top: 0, right: 0, zIndex: 50,
          height: '100%', width: '100%', maxWidth: 400,
          background: 'var(--surface-container-lowest)',
          borderLeft: '1px solid var(--outline-variant)',
          boxShadow: 'var(--shadow-lg)',
          transition: 'transform 300ms ease-out',
          transform: open ? 'translateX(0)' : 'translateX(100%)',
        }}
      >
        {/* Header */}
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          height: 56, padding: '0 1rem',
          borderBottom: '1px solid rgba(66, 71, 84, 0.15)',
        }}>
          <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--on-surface)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
            Notifications
          </span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <button onClick={markAllRead} className="btn-icon" title="Mark all read">
              <MaterialIcon name="done_all" size={16} />
            </button>
            <button onClick={clearAll} className="btn-icon" title="Clear all" style={{ color: 'var(--error)' }}>
              <MaterialIcon name="delete_sweep" size={16} />
            </button>
            <button onClick={onClose} className="btn-icon">
              <MaterialIcon name="close" size={18} />
            </button>
          </div>
        </div>

        {/* List */}
        <div style={{ overflowY: 'auto', height: 'calc(100% - 56px)' }}>
          {notifications.length === 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: 256, color: 'var(--outline)' }}>
              <MaterialIcon name="notifications_none" size={32} />
              <p style={{ marginTop: '0.5rem', fontSize: '0.875rem' }}>No notifications</p>
            </div>
          ) : (
            notifications.map((n) => {
              const cfg = typeConfig[n.type];
              return (
                <div
                  key={n.id}
                  style={{
                    display: 'flex', gap: '0.75rem',
                    padding: '0.75rem 1rem',
                    borderBottom: '1px solid rgba(66, 71, 84, 0.1)',
                    opacity: n.read ? 0.5 : 1,
                    background: n.read ? 'transparent' : 'rgba(26, 31, 45, 0.4)',
                    transition: 'opacity 200ms',
                  }}
                >
                  <div style={{
                    width: 32, height: 32, flexShrink: 0,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    background: cfg.bg,
                  }}>
                    <MaterialIcon name={cfg.icon} size={16} style={{ color: cfg.color } as React.CSSProperties} />
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <p style={{ fontSize: '0.75rem', fontWeight: 500, color: 'var(--on-surface)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {n.title}
                    </p>
                    <p style={{ fontSize: '0.6875rem', color: 'var(--on-surface-variant)', marginTop: 2 }}>
                      {n.message}
                    </p>
                    <p style={{ fontSize: '0.625rem', color: 'var(--outline)', marginTop: 4, fontFamily: 'var(--font-mono)' }}>
                      {formatDistanceToNow(n.timestamp, { addSuffix: true })}
                    </p>
                  </div>
                  {!n.read && (
                    <button onClick={() => markRead(n.id)} className="btn-icon" title="Mark read" style={{ alignSelf: 'flex-start', flexShrink: 0 }}>
                      <MaterialIcon name="check" size={14} />
                    </button>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>
    </>
  );
}
