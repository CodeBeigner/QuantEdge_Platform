import { useEffect, useState, useMemo } from 'react';
import type { WebSocketPrice } from '@/types';
import { deltaApi, type DeltaTicker } from '@/services/deltaExchange';

interface LiveTickerProps {
  prices: Record<string, WebSocketPrice>;
  connected: boolean;
}

// World market clocks
const MARKETS = [
  { label: 'NYC', tz: 'America/New_York', flag: '🇺🇸', openH: 9, closeH: 16 },
  { label: 'LDN', tz: 'Europe/London', flag: '🇬🇧', openH: 8, closeH: 16 },
  { label: 'TYO', tz: 'Asia/Tokyo', flag: '🇯🇵', openH: 9, closeH: 15 },
  { label: 'MUM', tz: 'Asia/Kolkata', flag: '🇮🇳', openH: 9, closeH: 15 },
  { label: 'SYD', tz: 'Australia/Sydney', flag: '🇦🇺', openH: 10, closeH: 16 },
] as const;

function getMarketTime(tz: string) {
  return new Date().toLocaleTimeString('en-US', {
    timeZone: tz, hour: '2-digit', minute: '2-digit', hour12: false,
  });
}

function isMarketOpen(tz: string, openH: number, closeH: number) {
  const now = new Date();
  const hStr = now.toLocaleString('en-US', { timeZone: tz, hour: 'numeric', hour12: false });
  const dayStr = now.toLocaleString('en-US', { timeZone: tz, weekday: 'short' });
  const h = parseInt(hStr);
  const isWeekend = dayStr === 'Sat' || dayStr === 'Sun';
  return !isWeekend && h >= openH && h < closeH;
}

// Top crypto symbols to show
const CRYPTO_SYMBOLS = ['BTCUSD', 'ETHUSD', 'SOLUSD', 'XRPUSD', 'AVAXUSD'];

export default function LiveTicker({ prices, connected }: LiveTickerProps) {
  const [now, setNow] = useState(new Date());
  const [deltaTickers, setDeltaTickers] = useState<DeltaTicker[]>([]);

  // Update clock every second
  useEffect(() => {
    const iv = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(iv);
  }, []);

  // Fetch Delta Exchange tickers for crypto prices
  useEffect(() => {
    const fetchTickers = () => {
      deltaApi.getTickers().then(t => setDeltaTickers(t)).catch(() => {});
    };
    fetchTickers();
    const iv = setInterval(fetchTickers, 5000);
    return () => clearInterval(iv);
  }, []);

  // Map tickers for display — testnet may not have last_price, use close/mark_price fallback
  const cryptoItems = useMemo(() => {
    return CRYPTO_SYMBOLS.map(sym => {
      const t = deltaTickers.find(d => d.symbol === sym);
      if (!t) return null;
      const price = parseFloat(t.last_price) || parseFloat(t.close) || parseFloat(t.mark_price) || 0;
      if (price === 0) return null;
      const open = parseFloat(t.open) || price;
      const change = parseFloat(t.change_24h) || (open > 0 ? ((price - open) / open) * 100 : 0);
      return { symbol: sym, price, change };
    }).filter(Boolean) as { symbol: string; price: number; change: number }[];
  }, [deltaTickers]);

  // Merge backend WS prices + Delta crypto prices
  const wsItems = Object.values(prices);
  const allTickerItems = [...cryptoItems, ...wsItems.map(p => ({
    symbol: p.symbol,
    price: typeof p.price === 'number' ? p.price : parseFloat(String(p.price)),
    change: typeof p.changePercent === 'number' ? p.changePercent : 0,
  }))];

  // Deduplicate
  const seen = new Set<string>();
  const uniqueItems = allTickerItems.filter(item => {
    if (seen.has(item.symbol)) return false;
    seen.add(item.symbol);
    return true;
  });

  const scrollItems = uniqueItems.length > 0 ? [...uniqueItems, ...uniqueItems] : [];

  return (
    <div style={{
      gridArea: 'ticker',
      height: 'var(--ticker-height)',
      background: 'var(--surface-container-highest)',
      overflow: 'hidden',
      display: 'flex',
      alignItems: 'center',
    }}>
      {/* World Market Clocks */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: '0.75rem',
        padding: '0 0.75rem', flexShrink: 0,
        borderRight: '1px solid rgba(66,71,84,0.3)',
      }}>
        {MARKETS.map(m => {
          const open = isMarketOpen(m.tz, m.openH, m.closeH);
          return (
            <span key={m.label} style={{
              display: 'inline-flex', alignItems: 'center', gap: '3px',
              fontFamily: 'var(--font-mono)', fontSize: '0.5625rem',
              color: open ? 'var(--tertiary)' : 'var(--outline)',
              whiteSpace: 'nowrap',
            }}>
              <span style={{ fontSize: '0.625rem' }}>{m.flag}</span>
              <span style={{ fontWeight: 600 }}>{m.label}</span>
              <span>{getMarketTime(m.tz)}</span>
              <span style={{
                width: 4, height: 4, borderRadius: '50%', flexShrink: 0,
                background: open ? 'var(--tertiary)' : 'var(--outline)',
              }} />
            </span>
          );
        })}
      </div>

      {/* Scrolling ticker tape */}
      <div style={{ flex: 1, overflow: 'hidden' }}>
        {scrollItems.length > 0 ? (
          <div style={{
            display: 'flex', gap: '1.5rem',
            animation: 'tickerScroll 60s linear infinite',
            whiteSpace: 'nowrap',
          }}>
            {scrollItems.map((item, i) => {
              const isUp = item.change >= 0;
              return (
                <span key={`${item.symbol}-${i}`} style={{
                  display: 'inline-flex', alignItems: 'center', gap: '0.375rem',
                  fontFamily: 'var(--font-mono)', fontSize: '0.6875rem',
                  padding: '0 0.25rem',
                }}>
                  <span style={{ color: 'var(--on-surface-variant)', fontWeight: 500 }}>{item.symbol}</span>
                  <span style={{ color: 'var(--on-surface)', fontWeight: 600 }}>
                    ${item.price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </span>
                  <span style={{ color: isUp ? 'var(--tertiary)' : 'var(--error)', fontWeight: 500 }}>
                    {isUp ? '▲' : '▼'}{Math.abs(item.change).toFixed(2)}%
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
            {connected ? 'Loading markets...' : 'Connecting...'}
          </span>
        )}
      </div>
    </div>
  );
}
