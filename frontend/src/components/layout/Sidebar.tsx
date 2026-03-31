import { useLocation, useNavigate } from 'react-router-dom';
import { MaterialIcon } from '@/components/ui/MaterialIcon';
import { useAuthStore } from '@/stores/authStore';

const navItems = [
  { label: 'Trading Floor', icon: 'dashboard', path: '/dashboard' },
  { label: 'Market', icon: 'trending_up', path: '/market' },
  { label: 'Strategies', icon: 'psychology', path: '/strategies' },
  { label: 'Backtest', icon: 'science', path: '/backtest' },
  { label: 'Agents', icon: 'smart_toy', path: '/agents' },
  { label: 'AI Intel', icon: 'auto_awesome', path: '/ai-intel' },
  { label: 'Trading', icon: 'candlestick_chart', path: '/trading' },
  { label: 'Risk', icon: 'shield', path: '/risk' },
  { label: 'ML Models', icon: 'model_training', path: '/ml' },
  { label: 'Alerts', icon: 'notification_important', path: '/alerts' },
  { label: 'Settings', icon: 'settings', path: '/settings' },
];

interface SidebarProps {
  collapsed: boolean;
  onToggle: () => void;
}

export default function Sidebar({ collapsed, onToggle }: SidebarProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();

  const isActive = (path: string) =>
    location.pathname === path || location.pathname.startsWith(path + '/');

  return (
    <aside style={{ gridArea: 'sidebar', background: 'var(--surface-container-lowest)', display: 'flex', flexDirection: 'column', overflow: 'hidden', height: '100%' }}>
      {/* Brand */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: collapsed ? '0 0.5rem' : '0 1rem',
        height: 'calc(var(--ticker-height) + var(--topbar-height))',
        flexShrink: 0,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', overflow: 'hidden' }}>
          <div style={{
            width: 32, height: 32, flexShrink: 0,
            background: 'linear-gradient(135deg, var(--primary), var(--primary-container))',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: '1.25rem',
            color: 'var(--surface-container-lowest)',
          }}>
            Q
          </div>
          {!collapsed && (
            <span style={{
              fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: '1rem',
              color: 'var(--on-surface)', whiteSpace: 'nowrap',
            }}>
              QuantEdge
            </span>
          )}
        </div>
        {!collapsed && (
          <button onClick={onToggle} className="btn-icon" style={{ flexShrink: 0 }}>
            <MaterialIcon name="menu" size={20} />
          </button>
        )}
      </div>

      {/* Nav */}
      <nav style={{ flex: 1, overflowY: 'auto', padding: '0.5rem', display: 'flex', flexDirection: 'column', gap: 2 }}>
        {navItems.map(({ label, icon, path }) => {
          const active = isActive(path);
          return (
            <button
              key={path}
              onClick={() => navigate(path)}
              title={collapsed ? label : undefined}
              style={{
                display: 'flex', alignItems: 'center',
                gap: '0.75rem',
                padding: collapsed ? '0.75rem' : '0.75rem 0.75rem',
                justifyContent: collapsed ? 'center' : 'flex-start',
                color: active ? 'var(--primary)' : 'var(--on-surface-variant)',
                background: active ? 'rgba(173, 198, 255, 0.08)' : 'transparent',
                border: 'none', cursor: 'pointer', width: '100%',
                fontSize: '0.75rem', fontWeight: 500, fontFamily: 'var(--font-body)',
                transition: 'all 150ms ease-out', whiteSpace: 'nowrap', overflow: 'hidden',
                textDecoration: 'none', textAlign: 'left',
              }}
              onMouseEnter={e => {
                if (!active) {
                  (e.currentTarget as HTMLElement).style.color = 'var(--on-surface)';
                  (e.currentTarget as HTMLElement).style.background = 'var(--surface-container)';
                }
              }}
              onMouseLeave={e => {
                if (!active) {
                  (e.currentTarget as HTMLElement).style.color = 'var(--on-surface-variant)';
                  (e.currentTarget as HTMLElement).style.background = 'transparent';
                }
              }}
            >
              <MaterialIcon name={icon} size={20} />
              {!collapsed && <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{label}</span>}
            </button>
          );
        })}
      </nav>

      {/* Footer */}
      <div style={{ padding: collapsed ? '1rem 0.5rem' : '1rem', borderTop: '1px solid rgba(66, 71, 84, 0.15)', flexShrink: 0 }}>
        {/* User */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', overflow: 'hidden', justifyContent: collapsed ? 'center' : 'flex-start' }}>
          <div style={{
            width: 32, height: 32, flexShrink: 0,
            background: 'linear-gradient(135deg, var(--primary-container), var(--primary))',
            color: 'var(--on-primary-fixed)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontFamily: 'var(--font-mono)', fontSize: '0.6875rem', fontWeight: 600,
          }}>
            {user?.name?.slice(0, 2).toUpperCase() ?? 'U'}
          </div>
          {!collapsed && (
            <div style={{ overflow: 'hidden' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 500, color: 'var(--on-surface)', whiteSpace: 'nowrap' }}>
                {user?.name ?? 'User'}
              </div>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: '0.6875rem', color: 'var(--outline)', whiteSpace: 'nowrap' }}>
                {user?.email ?? ''}
              </div>
            </div>
          )}
        </div>

        {/* Logout */}
        <button
          onClick={logout}
          title="Logout"
          style={{
            display: 'flex', alignItems: 'center', gap: '0.75rem',
            width: '100%', padding: '0.5rem 0.75rem', marginTop: '0.5rem',
            background: 'transparent', border: 'none', cursor: 'pointer',
            color: 'var(--on-surface-variant)', fontSize: '0.75rem',
            fontFamily: 'var(--font-body)', transition: 'color 150ms ease-out',
            justifyContent: collapsed ? 'center' : 'flex-start',
          }}
          onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = 'var(--error)'; }}
          onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'var(--on-surface-variant)'; }}
        >
          <MaterialIcon name="logout" size={18} />
          {!collapsed && <span>Logout</span>}
        </button>

        {/* Expand toggle when collapsed */}
        {collapsed && (
          <button onClick={onToggle} className="btn-icon" style={{ width: '100%', marginTop: '0.5rem', justifyContent: 'center' }}>
            <MaterialIcon name="chevron_right" size={18} />
          </button>
        )}
      </div>
    </aside>
  );
}
