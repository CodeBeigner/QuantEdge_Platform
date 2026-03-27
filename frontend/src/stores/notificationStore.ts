import { create } from 'zustand';
import type { Notification } from '@/types';

interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  addNotification: (n: Omit<Notification, 'id' | 'timestamp' | 'read'>) => void;
  markRead: (id: string) => void;
  markAllRead: () => void;
  removeNotification: (id: string) => void;
  clearAll: () => void;
}

let idCounter = 0;

export const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  unreadCount: 0,

  addNotification: (n) => {
    const notification: Notification = {
      ...n,
      id: `notif-${++idCounter}`,
      timestamp: new Date(),
      read: false,
    };
    set((state) => ({
      notifications: [notification, ...state.notifications].slice(0, 100),
      unreadCount: state.unreadCount + 1,
    }));
  },

  markRead: (id) => {
    set((state) => ({
      notifications: state.notifications.map((n) =>
        n.id === id ? { ...n, read: true } : n
      ),
      unreadCount: Math.max(0, state.unreadCount - (
        state.notifications.find((n) => n.id === id && !n.read) ? 1 : 0
      )),
    }));
  },

  markAllRead: () => {
    set((state) => ({
      notifications: state.notifications.map((n) => ({ ...n, read: true })),
      unreadCount: 0,
    }));
  },

  removeNotification: (id) => {
    const n = get().notifications.find((n) => n.id === id);
    set((state) => ({
      notifications: state.notifications.filter((n) => n.id !== id),
      unreadCount: n && !n.read ? state.unreadCount - 1 : state.unreadCount,
    }));
  },

  clearAll: () => set({ notifications: [], unreadCount: 0 }),
}));
