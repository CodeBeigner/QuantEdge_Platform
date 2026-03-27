import { BrowserRouter, Routes, Route, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useState, useEffect, useCallback } from 'react';
import { useKeyboard } from '@/hooks/useKeyboard';
import { Toaster, toast } from 'sonner';

import { useAuthStore } from '@/stores/authStore';
import { useNotificationStore } from '@/stores/notificationStore';
import { useWebSocket } from '@/hooks/useWebSocket';
import { api } from '@/services/api';
import type { FirmProfile } from '@/types';

import Sidebar from '@/components/layout/Sidebar';
import TopBar from '@/components/layout/TopBar';
import LiveTicker from '@/components/layout/LiveTicker';
import CommandPalette from '@/components/layout/CommandPalette';
import NotificationPanel from '@/components/layout/NotificationPanel';

import { AuthPage } from '@/pages/AuthPage';
import { FirmSetupPage } from '@/pages/FirmSetupPage';
import DashboardPage from '@/pages/DashboardPage';
import MarketPage from '@/pages/MarketPage';
import { StrategiesPage } from '@/pages/StrategiesPage';
import { BacktestPage } from '@/pages/BacktestPage';
import AgentsPage from '@/pages/AgentsPage';
import AIIntelPage from '@/pages/AIIntelPage';
import { OrdersPage } from '@/pages/OrdersPage';
import RiskPage from '@/pages/RiskPage';
import MLPage from '@/pages/MLPage';
import AlertsPage from '@/pages/AlertsPage';
import SettingsPage from '@/pages/SettingsPage';

function AppLayout() {
  const { prices, connected } = useWebSocket();
  const [firm, setFirm] = useState<FirmProfile | null>(null);
  const [firmLoading, setFirmLoading] = useState(true);
  const [notifOpen, setNotifOpen] = useState(false);
  const [cmdPaletteOpen, setCmdPaletteOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  useKeyboard('mod+k', (e) => {
    e.preventDefault();
    setCmdPaletteOpen((p) => !p);
  }, []);
  const addNotification = useNotificationStore((s) => s.addNotification);
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    api.getFirm().then((f) => {
      setFirm(f);
      setFirmLoading(false);
      if (!f && location.pathname !== '/firm-setup') {
        navigate('/firm-setup', { replace: true });
      }
    }).catch(() => setFirmLoading(false));
  }, []);

  const handleCeoCommand = useCallback(async (message: string) => {
    try {
      const result = await api.ceoBroadcast(message);
      toast.success(`${result.agentName || 'Agent'} responded`, {
        description: typeof result.response === 'string'
          ? result.response.slice(0, 200)
          : 'Response received',
        duration: 8000,
      });
      addNotification({
        type: 'info',
        title: `CEO Command: ${message.slice(0, 50)}`,
        message: typeof result.response === 'string' ? result.response.slice(0, 150) : 'Response received',
      });
    } catch (err) {
      toast.error('Command failed', { description: (err as Error).message });
    }
  }, [addNotification]);

  if (firmLoading) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--surface)' }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{
            width: 56, height: 56, margin: '0 auto 1rem',
            background: 'linear-gradient(135deg, var(--primary), var(--primary-container))',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: '1.5rem',
            color: 'var(--surface-container-lowest)',
          }}>
            Q
          </div>
          <p style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', color: 'var(--outline)' }}>
            Loading QuantEdge...
          </p>
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: sidebarCollapsed ? 'var(--sidebar-collapsed) 1fr' : 'var(--sidebar-width) 1fr',
        gridTemplateRows: 'var(--ticker-height) var(--topbar-height) 1fr',
        gridTemplateAreas: '"sidebar ticker" "sidebar topbar" "sidebar main"',
        height: '100vh',
        width: '100vw',
        overflow: 'hidden',
        transition: 'grid-template-columns 200ms ease-out',
      }}
    >
      <Sidebar collapsed={sidebarCollapsed} onToggle={() => setSidebarCollapsed(c => !c)} />
      <LiveTicker prices={prices} connected={connected} />
      <TopBar
        onOpenCommandPalette={() => setCmdPaletteOpen(true)}
        onToggleNotifications={() => setNotifOpen(true)}
        connected={connected}
      />
      <main style={{
        gridArea: 'main',
        overflowY: 'auto',
        overflowX: 'hidden',
        padding: '1.5rem',
      }}>
        <div className="animate-in" key={location.pathname}>
          <Outlet />
        </div>
      </main>
      <CommandPalette open={cmdPaletteOpen} onClose={() => setCmdPaletteOpen(false)} />
      <NotificationPanel open={notifOpen} onClose={() => setNotifOpen(false)} />
    </div>
  );
}

function ProtectedRoute() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  if (!isAuthenticated) return <Navigate to="/auth" replace />;
  return <AppLayout />;
}

export default function App() {
  return (
    <BrowserRouter>
      <Toaster
        theme="dark"
        position="top-right"
        toastOptions={{
          style: {
            background: 'var(--surface-container-highest)',
            border: 'none',
            borderLeft: '3px solid var(--primary)',
            color: 'var(--on-surface)',
            fontFamily: 'var(--font-body)',
          },
        }}
      />
      <Routes>
        <Route path="/auth" element={<AuthPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/firm-setup" element={<FirmSetupPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/market" element={<MarketPage />} />
          <Route path="/strategies" element={<StrategiesPage />} />
          <Route path="/backtest" element={<BacktestPage />} />
          <Route path="/agents" element={<AgentsPage />} />
          <Route path="/ai-intel" element={<AIIntelPage />} />
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/risk" element={<RiskPage />} />
          <Route path="/ml" element={<MLPage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
