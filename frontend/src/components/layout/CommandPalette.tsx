import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { MaterialIcon } from '@/components/ui/MaterialIcon';

interface Action {
  id: string;
  label: string;
  section: string;
  icon: string;
  shortcut?: string;
  onSelect: () => void;
}

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
}

export default function CommandPalette({ open, onClose }: CommandPaletteProps) {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  const nav = (path: string) => () => { navigate(path); onClose(); };

  const actions: Action[] = [
    { id: 'dashboard', label: 'Go to Trading Floor', section: 'Navigate', icon: 'dashboard', shortcut: 'G D', onSelect: nav('/dashboard') },
    { id: 'market', label: 'Go to Market', section: 'Navigate', icon: 'trending_up', shortcut: 'G M', onSelect: nav('/market') },
    { id: 'strategies', label: 'Go to Strategies', section: 'Navigate', icon: 'psychology', shortcut: 'G S', onSelect: nav('/strategies') },
    { id: 'backtest', label: 'Go to Backtest', section: 'Navigate', icon: 'science', onSelect: nav('/backtest') },
    { id: 'agents', label: 'Go to Agents', section: 'Navigate', icon: 'smart_toy', shortcut: 'G A', onSelect: nav('/agents') },
    { id: 'ai-intel', label: 'Go to AI Intel', section: 'Navigate', icon: 'auto_awesome', onSelect: nav('/ai-intel') },
    { id: 'orders', label: 'Go to Orders', section: 'Navigate', icon: 'receipt_long', shortcut: 'G O', onSelect: nav('/orders') },
    { id: 'risk', label: 'Go to Risk', section: 'Navigate', icon: 'shield', shortcut: 'G R', onSelect: nav('/risk') },
    { id: 'ml', label: 'Go to ML Models', section: 'Navigate', icon: 'model_training', onSelect: nav('/ml') },
    { id: 'alerts', label: 'Go to Alerts', section: 'Navigate', icon: 'notification_important', onSelect: nav('/alerts') },
    { id: 'settings', label: 'Go to Settings', section: 'Navigate', icon: 'settings', onSelect: nav('/settings') },
  ];

  const filtered = query.trim()
    ? actions.filter((a) =>
        a.label.toLowerCase().includes(query.toLowerCase()) ||
        a.section.toLowerCase().includes(query.toLowerCase())
      )
    : actions;

  const grouped = filtered.reduce<Record<string, Action[]>>((acc, a) => {
    (acc[a.section] ??= []).push(a);
    return acc;
  }, {});

  useEffect(() => {
    if (open) {
      setQuery('');
      setSelectedIndex(0);
      requestAnimationFrame(() => inputRef.current?.focus());
    }
  }, [open]);

  useEffect(() => { setSelectedIndex(0); }, [query]);

  useEffect(() => {
    const el = listRef.current?.querySelector('[data-selected="true"]');
    el?.scrollIntoView({ block: 'nearest' });
  }, [selectedIndex]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault();
          setSelectedIndex((i) => Math.min(i + 1, filtered.length - 1));
          break;
        case 'ArrowUp':
          e.preventDefault();
          setSelectedIndex((i) => Math.max(i - 1, 0));
          break;
        case 'Enter':
          e.preventDefault();
          filtered[selectedIndex]?.onSelect();
          break;
        case 'Escape':
          e.preventDefault();
          onClose();
          break;
      }
    },
    [filtered, selectedIndex, onClose]
  );

  if (!open) return null;

  let flatIndex = -1;

  return (
    <div className="overlay-glass" onClick={onClose}>
      <div
        className="overlay-glass-content"
        style={{ width: 560, maxWidth: '90vw' }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Search */}
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Search commands, pages..."
          style={{
            width: '100%', background: 'transparent', border: 'none',
            borderBottom: '1px solid rgba(66, 71, 84, 0.15)',
            padding: '1rem 1.25rem',
            fontFamily: 'var(--font-body)', fontSize: '1rem',
            color: 'var(--on-surface)', outline: 'none',
          }}
        />

        {/* Results */}
        <div ref={listRef} style={{ maxHeight: 320, overflowY: 'auto', padding: '0.5rem' }}>
          {filtered.length === 0 ? (
            <p style={{ textAlign: 'center', padding: '2rem', color: 'var(--outline)', fontSize: '0.875rem' }}>
              No results found
            </p>
          ) : (
            Object.entries(grouped).map(([section, items]) => (
              <div key={section}>
                <p style={{
                  padding: '0.75rem 1rem 0.25rem',
                  fontFamily: 'var(--font-mono)', fontSize: '0.6875rem',
                  fontWeight: 600, color: 'var(--outline)',
                  textTransform: 'uppercase', letterSpacing: '0.08em',
                }}>
                  {section}
                </p>
                {items.map((action) => {
                  flatIndex++;
                  const idx = flatIndex;
                  const selected = idx === selectedIndex;
                  return (
                    <button
                      key={action.id}
                      data-selected={selected}
                      onClick={action.onSelect}
                      onMouseEnter={() => setSelectedIndex(idx)}
                      style={{
                        display: 'flex', alignItems: 'center', gap: '0.75rem',
                        width: '100%', padding: '0.75rem 1rem',
                        background: selected ? 'var(--surface-container-high)' : 'transparent',
                        border: 'none', cursor: 'pointer',
                        fontSize: '0.875rem', color: 'var(--on-surface)',
                        fontFamily: 'var(--font-body)',
                        transition: 'background 100ms ease-out',
                      }}
                    >
                      <MaterialIcon name={action.icon} size={18} style={{ color: 'var(--outline)' } as React.CSSProperties} />
                      <span>{action.label}</span>
                      {action.shortcut && (
                        <span style={{
                          marginLeft: 'auto',
                          fontFamily: 'var(--font-mono)', fontSize: '0.6875rem',
                          color: 'var(--outline)',
                          background: 'var(--surface-container-highest)',
                          padding: '2px 6px',
                        }}>
                          {action.shortcut}
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div style={{
          display: 'flex', alignItems: 'center', gap: '1rem',
          padding: '0 1rem', height: 36,
          borderTop: '1px solid rgba(66, 71, 84, 0.15)',
          fontFamily: 'var(--font-mono)', fontSize: '0.6875rem', color: 'var(--outline)',
        }}>
          <span>↑↓ navigate</span>
          <span>↵ select</span>
          <span>esc close</span>
        </div>
      </div>
    </div>
  );
}
