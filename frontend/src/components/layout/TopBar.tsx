import { useState, useEffect } from 'react';
import { MaterialIcon } from '@/components/ui/MaterialIcon';
import { useNotificationStore } from '@/stores/notificationStore';

interface TopBarProps {
  onOpenCommandPalette: () => void;
  onToggleNotifications: () => void;
  connected?: boolean;
}

export default function TopBar({ onOpenCommandPalette, onToggleNotifications, connected = false }: TopBarProps) {
  const [time, setTime] = useState('');
  const unreadCount = useNotificationStore((s) => s.unreadCount);

  useEffect(() => {
    const update = () => setTime(new Date().toLocaleTimeString('en-US', { hour12: false }));
    update();
    const id = setInterval(update, 1000);
    return () => clearInterval(id);
  }, []);

  return (
    <div style={{
      gridArea: 'topbar',
      height: 'var(--topbar-height)',
      background: 'var(--surface-container-low)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '0 1.25rem',
    }}>
      {/* Left: Status */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
        <span className={`status-dot ${connected ? 'live' : 'idle'}`} />
        <span style={{
          fontFamily: 'var(--font-mono)',
          fontSize: '0.6875rem',
          color: connected ? 'var(--tertiary)' : 'var(--outline)',
          letterSpacing: '0.08em',
        }}>
          {connected ? 'NEURAL_LINK_ACTIVE' : 'CONNECTING'}
        </span>
      </div>

      {/* Right: Actions */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        <button
          onClick={onOpenCommandPalette}
          className="btn-icon"
          title="Command Palette (Ctrl+K)"
        >
          <MaterialIcon name="search" size={20} />
        </button>

        <button
          onClick={onToggleNotifications}
          className="btn-icon"
          title="Notifications"
          style={{ position: 'relative' }}
        >
          <MaterialIcon name="notifications_none" size={20} />
          {unreadCount > 0 && (
            <span style={{
              position: 'absolute', top: 2, right: 2,
              width: 8, height: 8, borderRadius: '50%',
              background: 'var(--error)',
            }} />
          )}
        </button>

        <span style={{
          fontFamily: 'var(--font-mono)',
          fontSize: '0.6875rem',
          color: 'var(--outline)',
          letterSpacing: '0.06em',
        }}>
          {time}
        </span>
      </div>
    </div>
  );
}
