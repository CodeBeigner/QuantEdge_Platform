import { useEffect } from 'react';

type KeyHandler = (e: KeyboardEvent) => void;

const handlers = new Map<string, Set<KeyHandler>>();

export function useKeyboard(key: string, handler: KeyHandler, deps: unknown[] = []) {
  useEffect(() => {
    const normalizedKey = key.toLowerCase();
    if (!handlers.has(normalizedKey)) {
      handlers.set(normalizedKey, new Set());
    }
    handlers.get(normalizedKey)!.add(handler);

    return () => {
      handlers.get(normalizedKey)?.delete(handler);
    };
  }, deps);
}

if (typeof window !== 'undefined') {
  window.addEventListener('keydown', (e) => {
    const parts: string[] = [];
    if (e.metaKey || e.ctrlKey) parts.push('mod');
    if (e.shiftKey) parts.push('shift');
    if (e.altKey) parts.push('alt');
    parts.push(e.key.toLowerCase());
    const combo = parts.join('+');

    const comboHandlers = handlers.get(combo);
    if (comboHandlers) {
      comboHandlers.forEach((h) => h(e));
    }
  });
}
