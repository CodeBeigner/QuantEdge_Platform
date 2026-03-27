import type { WebSocketPrice } from '@/types';

interface LiveTickerProps {
  prices: Record<string, WebSocketPrice>;
  connected: boolean;
}

export default function LiveTicker({ prices, connected }: LiveTickerProps) {
  const items = Object.values(prices);
  const allItems = items.length > 0 ? [...items, ...items] : [];

  return (
    <div style={{
      gridArea: 'ticker',
      height: 'var(--ticker-height)',
      background: 'var(--surface-container-highest)',
      overflow: 'hidden',
      display: 'flex',
      alignItems: 'center',
    }}>
      {allItems.length > 0 ? (
        <div style={{
          display: 'flex',
          gap: '1.75rem',
          animation: 'tickerScroll 60s linear infinite',
          whiteSpace: 'nowrap',
        }}>
          {allItems.map((p, i) => {
            const changeNum = typeof p.changePercent === 'number' ? p.changePercent : 0;
            const isUp = changeNum >= 0;
            return (
              <span key={`${p.symbol}-${i}`} style={{
                display: 'inline-flex', alignItems: 'center', gap: '0.5rem',
                fontFamily: 'var(--font-mono)', fontSize: '0.6875rem',
                padding: '0 0.5rem',
              }}>
                <span style={{ color: 'var(--on-surface-variant)', fontWeight: 500 }}>{p.symbol}</span>
                <span style={{ color: 'var(--on-surface)', fontWeight: 600 }}>
                  {typeof p.price === 'number' ? p.price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : p.price}
                </span>
                <span style={{ color: isUp ? 'var(--tertiary)' : 'var(--error)', fontWeight: 500 }}>
                  {isUp ? '+' : ''}{changeNum.toFixed(2)}%
                </span>
              </span>
            );
          })}
        </div>
      ) : (
        <span style={{
          fontFamily: 'var(--font-mono)', fontSize: '0.6875rem',
          color: 'var(--outline)', padding: '0 1rem',
        }}>
          {connected ? 'Awaiting feed...' : 'Connecting...'}
        </span>
      )}
    </div>
  );
}
