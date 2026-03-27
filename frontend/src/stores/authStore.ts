import { create } from 'zustand';
import type { User } from '@/types';
import { api } from '@/services/api';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;

  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  checkAuth: () => void;
  clearError: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: api.getUser(),
  isAuthenticated: api.isLoggedIn(),
  isLoading: false,
  error: null,

  login: async (email, password) => {
    set({ isLoading: true, error: null });
    try {
      const user = await api.login(email, password);
      set({ user, isAuthenticated: true, isLoading: false });
    } catch (err) {
      set({ error: (err as Error).message, isLoading: false });
    }
  },

  register: async (name, email, password) => {
    set({ isLoading: true, error: null });
    try {
      const user = await api.register(name, email, password);
      set({ user, isAuthenticated: true, isLoading: false });
    } catch (err) {
      set({ error: (err as Error).message, isLoading: false });
    }
  },

  logout: () => {
    api.logout();
    set({ user: null, isAuthenticated: false });
  },

  checkAuth: () => {
    const user = api.getUser();
    set({ user, isAuthenticated: !!user });
  },

  clearError: () => set({ error: null }),
}));

window.addEventListener('auth:expired', () => {
  useAuthStore.getState().logout();
});
