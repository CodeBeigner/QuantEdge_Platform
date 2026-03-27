import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { Loader2, Mail, Lock, User } from 'lucide-react';
import { useAuthStore } from '@/stores/authStore';

type Tab = 'login' | 'register';

export function AuthPage() {
  const { login, register, error, isLoading, isAuthenticated, clearError } = useAuthStore();
  const [tab, setTab] = useState<Tab>('login');

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const switchTab = (next: Tab) => {
    clearError();
    setTab(next);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (tab === 'login') {
      void login(email.trim(), password);
    } else {
      void register(name.trim(), email.trim(), password);
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[#0a0f1c] px-4 py-12">
      <div
        className="pointer-events-none absolute inset-0"
        aria-hidden
      >
        <div className="absolute left-[10%] top-[15%] h-[min(520px,50vw)] w-[min(520px,50vw)] rounded-full bg-[#3b82f6]/10 blur-[100px]" />
        <div className="absolute bottom-[10%] right-[12%] h-[min(480px,45vw)] w-[min(480px,45vw)] rounded-full bg-[#00ff88]/8 blur-[90px]" />
        <div className="absolute left-1/2 top-1/2 h-[min(600px,60vw)] w-[min(600px,60vw)] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#1a2234]/40 blur-[120px]" />
      </div>

      <div className="relative z-10 w-full max-w-md">
        <div className="mb-8 flex flex-col items-center text-center">
          <div className="mb-4 flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-[#00ff88] to-[#3b82f6] text-lg font-bold text-[#0a0f1c] shadow-lg shadow-[#00ff88]/20">
              Q
            </div>
            <div className="text-left">
              <h1 className="text-2xl font-semibold tracking-tight text-[#f1f5f9]">
                QuantEdge
              </h1>
              <p className="text-sm text-[#64748b]">Firm OS</p>
            </div>
          </div>
        </div>

        <div className="bg-[#1a2234]/90 backdrop-blur-xl border border-[#2a3650] rounded-2xl p-8 shadow-2xl shadow-black/40">
          <div className="mb-6 flex rounded-xl bg-[#111827] p-1">
            <button
              type="button"
              onClick={() => switchTab('login')}
              className={`flex-1 rounded-lg py-2.5 text-sm font-medium transition-colors ${
                tab === 'login'
                  ? 'bg-[#1a2234] text-[#f1f5f9] shadow-sm'
                  : 'text-[#94a3b8] hover:text-[#f1f5f9]'
              }`}
            >
              Login
            </button>
            <button
              type="button"
              onClick={() => switchTab('register')}
              className={`flex-1 rounded-lg py-2.5 text-sm font-medium transition-colors ${
                tab === 'register'
                  ? 'bg-[#1a2234] text-[#f1f5f9] shadow-sm'
                  : 'text-[#94a3b8] hover:text-[#f1f5f9]'
              }`}
            >
              Register
            </button>
          </div>

          {error ? (
            <div
              role="alert"
              className="mb-6 rounded-xl border border-red-500/40 bg-red-950/60 px-4 py-3 text-sm text-red-200"
            >
              {error}
            </div>
          ) : null}

          <form onSubmit={handleSubmit} className="space-y-4">
            {tab === 'register' ? (
              <label className="block">
                <span className="mb-1.5 block text-xs font-medium uppercase tracking-wide text-[#64748b]">
                  Name
                </span>
                <div className="relative">
                  <User
                    className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#64748b]"
                    aria-hidden
                  />
                  <input
                    type="text"
                    autoComplete="name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    className="w-full rounded-xl border border-[#2a3650] bg-[#111827]/80 py-3 pl-10 pr-3 text-[#f1f5f9] placeholder:text-[#64748b] outline-none transition focus:border-[#3b82f6] focus:ring-1 focus:ring-[#3b82f6]"
                    placeholder="Your name"
                    required={tab === 'register'}
                  />
                </div>
              </label>
            ) : null}

            <label className="block">
              <span className="mb-1.5 block text-xs font-medium uppercase tracking-wide text-[#64748b]">
                Email
              </span>
              <div className="relative">
                <Mail
                  className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#64748b]"
                  aria-hidden
                />
                <input
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full rounded-xl border border-[#2a3650] bg-[#111827]/80 py-3 pl-10 pr-3 text-[#f1f5f9] placeholder:text-[#64748b] outline-none transition focus:border-[#3b82f6] focus:ring-1 focus:ring-[#3b82f6]"
                  placeholder="you@firm.com"
                  required
                />
              </div>
            </label>

            <label className="block">
              <span className="mb-1.5 block text-xs font-medium uppercase tracking-wide text-[#64748b]">
                Password
              </span>
              <div className="relative">
                <Lock
                  className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#64748b]"
                  aria-hidden
                />
                <input
                  type="password"
                  autoComplete={tab === 'login' ? 'current-password' : 'new-password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full rounded-xl border border-[#2a3650] bg-[#111827]/80 py-3 pl-10 pr-3 text-[#f1f5f9] placeholder:text-[#64748b] outline-none transition focus:border-[#3b82f6] focus:ring-1 focus:ring-[#3b82f6]"
                  placeholder="••••••••"
                  required
                  minLength={6}
                />
              </div>
            </label>

            <button
              type="submit"
              disabled={isLoading}
              className="mt-2 flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-[#00ff88] to-[#3b82f6] py-3.5 text-sm font-semibold text-[#0a0f1c] shadow-lg shadow-[#00ff88]/15 transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isLoading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                  Please wait
                </>
              ) : tab === 'login' ? (
                'Sign in'
              ) : (
                'Create account'
              )}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
