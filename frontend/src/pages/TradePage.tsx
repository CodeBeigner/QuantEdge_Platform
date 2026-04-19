import { useState, useRef, useEffect, useMemo, useCallback } from 'react';
import { toast } from 'sonner';
import {
  createChart,
  CandlestickSeries,
  HistogramSeries,
  ColorType,
  CrosshairMode,
} from 'lightweight-charts';
import type { IChartApi, CandlestickData, Time } from 'lightweight-charts';

/* ─── Constants ─── */
const SYMBOLS = ['BTCUSD', 'ETHUSD', 'SOLUSD', 'XRPUSD'] as const;
type Symbol = (typeof SYMBOLS)[number];

const TIMEFRAMES = ['15m', '1h', '4h'] as const;
type Timeframe = (typeof TIMEFRAMES)[number];

const SIDES = ['BUY', 'SELL'] as const;
type Side = (typeof SIDES)[number];

const ORDER_TYPES = ['LIMIT', 'MARKET'] as const;
type OrderType = (typeof ORDER_TYPES)[number];

const TABS = ['Open Positions', 'Open Orders', 'Trade History'] as const;
type Tab = (typeof TABS)[number];

/* ─── Sample Data Generators ─── */
function generateOHLCV(symbol: Symbol, tf: Timeframe, count = 100): CandlestickData<Time>[] {
  const basePrices: Record<Symbol, number> = { BTCUSD: 67000, ETHUSD: 3450, SOLUSD: 145, XRPUSD: 0.52 };
  const base = basePrices[symbol];
  const volatility = base * 0.008;
  const tfMinutes: Record<Timeframe, number> = { '15m': 15, '1h': 60, '4h': 240 };
  const interval = tfMinutes[tf] * 60;

  const now = Math.floor(Date.now() / 1000);
  const startTime = now - count * interval;
  const data: CandlestickData<Time>[] = [];
  let price = base * (1 + (Math.random() - 0.5) * 0.02);

  for (let i = 0; i < count; i++) {
    const time = (startTime + i * interval) as Time;
    const open = price;
    const change1 = (Math.random() - 0.48) * volatility;
    const change2 = (Math.random() - 0.48) * volatility;
    const high = Math.max(open, open + change1, open + change2) + Math.random() * volatility * 0.3;
    const low = Math.min(open, open + change1, open + change2) - Math.random() * volatility * 0.3;
    const close = open + (Math.random() - 0.48) * volatility;
    price = close;

    data.push({
      time,
      open: round(open, symbol),
      high: round(high, symbol),
      low: round(low, symbol),
      close: round(close, symbol),
    });
  }
  return data;
}

function round(n: number, symbol: Symbol): number {
  const decimals: Record<Symbol, number> = { BTCUSD: 1, ETHUSD: 2, SOLUSD: 2, XRPUSD: 4 };
  const d = decimals[symbol];
  return Math.round(n * 10 ** d) / 10 ** d;
}

interface OrderBookLevel {
  price: number;
  size: number;
  total: number;
}

function generateOrderBook(symbol: Symbol): { asks: OrderBookLevel[]; bids: OrderBookLevel[] } {
  const basePrices: Record<Symbol, number> = { BTCUSD: 67000, ETHUSD: 3450, SOLUSD: 145, XRPUSD: 0.52 };
  const base = basePrices[symbol];
  const tick = base * 0.0001;
  const decimals: Record<Symbol, number> = { BTCUSD: 1, ETHUSD: 2, SOLUSD: 2, XRPUSD: 4 };
  const d = decimals[symbol];

  const asks: OrderBookLevel[] = [];
  const bids: OrderBookLevel[] = [];
  let askTotal = 0;
  let bidTotal = 0;

  for (let i = 0; i < 10; i++) {
    const askSize = +(Math.random() * 2 + 0.1).toFixed(3);
    askTotal += askSize;
    asks.push({
      price: +(base + (10 - i) * tick * (3 + Math.random())).toFixed(d),
      size: askSize,
      total: +askTotal.toFixed(3),
    });

    const bidSize = +(Math.random() * 2 + 0.1).toFixed(3);
    bidTotal += bidSize;
    bids.push({
      price: +(base - (i + 1) * tick * (3 + Math.random())).toFixed(d),
      size: bidSize,
      total: +bidTotal.toFixed(3),
    });
  }

  // asks sorted descending by price (highest at top)
  asks.sort((a, b) => b.price - a.price);
  // bids sorted descending by price (highest at top)
  bids.sort((a, b) => b.price - a.price);

  return { asks, bids };
}

function generateVolumeData(candles: CandlestickData<Time>[]) {
  return candles.map((c) => ({
    time: c.time,
    value: Math.round(Math.random() * 500 + 50),
    color: (c.close as number) >= (c.open as number)
      ? 'rgba(0, 228, 121, 0.3)'
      : 'rgba(255, 180, 171, 0.3)',
  }));
}

/* ─── Price Formatter ─── */
function fmtPrice(n: number, symbol: Symbol): string {
  const decimals: Record<Symbol, number> = { BTCUSD: 1, ETHUSD: 2, SOLUSD: 2, XRPUSD: 4 };
  return n.toFixed(decimals[symbol]);
}

/* ─── Shared Inline Styles ─── */
const cardBg = '#1a2235';
const cardBgHover = '#1e2740';
const monoFont = 'var(--font-mono)';
const bodyFont = 'var(--font-body)';
const textMuted = 'var(--outline)';
const textPrimary = 'var(--on-surface)';
const green = 'var(--tertiary)';
const red = 'var(--error)';
const borderColor = 'var(--outline-variant)';

/* ───────────────────────────────────────────────────────────────────────────────
   CHART COMPONENT
   ─────────────────────────────────────────────────────────────────────────────── */
function CandlestickChart({ symbol, timeframe }: { symbol: Symbol; timeframe: Timeframe }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candleSeriesRef = useRef<any>(null);
  const volumeSeriesRef = useRef<any>(null);

  // Create chart once on mount
  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: 'transparent' },
        textColor: '#8c909f',
        fontFamily: 'IBM Plex Mono, monospace',
        fontSize: 11,
      },
      grid: {
        vertLines: { color: 'rgba(66, 71, 84, 0.15)' },
        horzLines: { color: 'rgba(66, 71, 84, 0.15)' },
      },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: 'rgba(173, 198, 255, 0.3)', width: 1, labelBackgroundColor: '#303443' },
        horzLine: { color: 'rgba(173, 198, 255, 0.3)', width: 1, labelBackgroundColor: '#303443' },
      },
      rightPriceScale: {
        borderColor: 'rgba(66, 71, 84, 0.3)',
        scaleMargins: { top: 0.05, bottom: 0.2 },
      },
      timeScale: {
        borderColor: 'rgba(66, 71, 84, 0.3)',
        timeVisible: true,
        secondsVisible: false,
      },
      handleScroll: { vertTouchDrag: false },
    });

    const cSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#00e479',
      downColor: '#ff6b6b',
      borderUpColor: '#00e479',
      borderDownColor: '#ff6b6b',
      wickUpColor: '#00e479',
      wickDownColor: '#ff6b6b',
    });

    const vSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: 'volume' },
      priceScaleId: '',
    });
    vSeries.priceScale().applyOptions({
      scaleMargins: { top: 0.85, bottom: 0 },
    });

    chartRef.current = chart;
    candleSeriesRef.current = cSeries;
    volumeSeriesRef.current = vSeries;

    // Resize observer
    const ro = new ResizeObserver((entries) => {
      for (const entry of entries) {
        const { width, height } = entry.contentRect;
        chart.applyOptions({ width, height });
      }
    });
    ro.observe(containerRef.current);

    return () => {
      ro.disconnect();
      chart.remove();
      chartRef.current = null;
    };
  }, []);

  // Update data when symbol or timeframe changes
  useEffect(() => {
    if (!candleSeriesRef.current || !volumeSeriesRef.current) return;
    const candles = generateOHLCV(symbol, timeframe);
    const volumes = generateVolumeData(candles);
    candleSeriesRef.current.setData(candles);
    volumeSeriesRef.current.setData(volumes);
    chartRef.current?.timeScale().fitContent();
  }, [symbol, timeframe]);

  return (
    <div
      ref={containerRef}
      style={{ width: '100%', height: '100%', minHeight: 300 }}
    />
  );
}

/* ───────────────────────────────────────────────────────────────────────────────
   ORDER BOOK COMPONENT
   ─────────────────────────────────────────────────────────────────────────────── */
function OrderBook({ symbol }: { symbol: Symbol }) {
  const book = useMemo(() => generateOrderBook(symbol), [symbol]);
  const maxTotal = Math.max(
    book.asks[book.asks.length - 1]?.total ?? 1,
    book.bids[book.bids.length - 1]?.total ?? 1,
  );
  const spread = book.asks.length && book.bids.length
    ? +(book.asks[book.asks.length - 1].price - book.bids[0].price).toFixed(4)
    : 0;
  const spreadPct = book.bids[0]?.price
    ? ((spread / book.bids[0].price) * 100).toFixed(3)
    : '0';

  const rowStyle: React.CSSProperties = {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    height: 18,
    alignItems: 'center',
    fontFamily: monoFont,
    fontSize: '0.6875rem',
    position: 'relative',
    padding: '0 8px',
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Header */}
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        padding: '8px 10px', borderBottom: `1px solid rgba(66, 71, 84, 0.2)`,
      }}>
        <span style={{ fontFamily: bodyFont, fontSize: '0.6875rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.06em', color: textPrimary }}>
          Order Book
        </span>
        <span style={{ fontFamily: monoFont, fontSize: '0.625rem', color: textMuted }}>
          Spread: {spread} ({spreadPct}%)
        </span>
      </div>

      {/* Asks */}
      <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column', justifyContent: 'flex-end' }}>
        {book.asks.map((level, i) => {
          const depthPct = (level.total / maxTotal) * 100;
          return (
            <div key={`ask-${i}`} style={rowStyle}>
              <div style={{
                position: 'absolute', right: 0, top: 0, bottom: 0,
                width: `${depthPct}%`, background: 'rgba(255, 107, 107, 0.08)',
              }} />
              <span style={{ color: '#ff6b6b', position: 'relative', zIndex: 1 }}>{fmtPrice(level.price, symbol)}</span>
              <span style={{ textAlign: 'right', color: textPrimary, position: 'relative', zIndex: 1 }}>{level.size.toFixed(3)}</span>
            </div>
          );
        })}
      </div>

      {/* Spread row */}
      <div style={{
        display: 'flex', justifyContent: 'center', alignItems: 'center',
        padding: '4px 10px', background: 'var(--surface-container-highest)',
        fontFamily: monoFont, fontSize: '0.75rem', fontWeight: 600, color: textPrimary,
        gap: 8,
      }}>
        <span>{fmtPrice(book.asks[book.asks.length - 1]?.price ?? 0, symbol)}</span>
        <span style={{ fontSize: '0.5625rem', color: textMuted }}>SPREAD {spread}</span>
      </div>

      {/* Bids */}
      <div style={{ flex: 1, overflow: 'hidden' }}>
        {book.bids.map((level, i) => {
          const depthPct = (level.total / maxTotal) * 100;
          return (
            <div key={`bid-${i}`} style={rowStyle}>
              <div style={{
                position: 'absolute', right: 0, top: 0, bottom: 0,
                width: `${depthPct}%`, background: 'rgba(0, 228, 121, 0.08)',
              }} />
              <span style={{ color: '#00e479', position: 'relative', zIndex: 1 }}>{fmtPrice(level.price, symbol)}</span>
              <span style={{ textAlign: 'right', color: textPrimary, position: 'relative', zIndex: 1 }}>{level.size.toFixed(3)}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ───────────────────────────────────────────────────────────────────────────────
   ORDER ENTRY COMPONENT
   ─────────────────────────────────────────────────────────────────────────────── */
function OrderEntry({ symbol }: { symbol: Symbol }) {
  const [side, setSide] = useState<Side>('BUY');
  const [orderType, setOrderType] = useState<OrderType>('LIMIT');
  const [size, setSize] = useState('');
  const [price, setPrice] = useState('');
  const [stopLoss, setStopLoss] = useState('');
  const [takeProfit, setTakeProfit] = useState('');

  const handleSubmit = useCallback(() => {
    toast.info('Order placement will be available when connected to Delta Exchange', {
      description: `${side} ${orderType} ${symbol} — Size: ${size || '0'}`,
      duration: 4000,
    });
  }, [side, orderType, symbol, size]);

  const inputStyle: React.CSSProperties = {
    width: '100%',
    background: 'var(--surface-container-lowest)',
    border: '1px solid rgba(66, 71, 84, 0.3)',
    color: textPrimary,
    fontFamily: monoFont,
    fontSize: '0.8125rem',
    padding: '6px 10px',
    outline: 'none',
    boxSizing: 'border-box',
  };

  const labelStyle: React.CSSProperties = {
    fontFamily: monoFont,
    fontSize: '0.625rem',
    color: textMuted,
    textTransform: 'uppercase',
    letterSpacing: '0.08em',
    marginBottom: 3,
  };

  const isBuy = side === 'BUY';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '10px' }}>
      {/* Side Toggle */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 4 }}>
        {SIDES.map((s) => (
          <button
            key={s}
            onClick={() => setSide(s)}
            style={{
              padding: '8px 0',
              border: 'none',
              cursor: 'pointer',
              fontFamily: bodyFont,
              fontSize: '0.75rem',
              fontWeight: 600,
              letterSpacing: '0.04em',
              color: side === s ? '#fff' : textMuted,
              background: side === s
                ? s === 'BUY' ? 'rgba(0, 228, 121, 0.85)' : 'rgba(255, 107, 107, 0.85)'
                : 'var(--surface-container-lowest)',
              transition: 'all 150ms ease-out',
            }}
          >
            {s}
          </button>
        ))}
      </div>

      {/* Type Toggle */}
      <div style={{ display: 'flex', gap: 4, background: 'var(--surface-container-lowest)', padding: 3 }}>
        {ORDER_TYPES.map((t) => (
          <button
            key={t}
            onClick={() => setOrderType(t)}
            style={{
              flex: 1,
              padding: '5px 0',
              border: 'none',
              cursor: 'pointer',
              fontFamily: monoFont,
              fontSize: '0.625rem',
              fontWeight: 500,
              letterSpacing: '0.04em',
              color: orderType === t ? textPrimary : textMuted,
              background: orderType === t ? 'var(--surface-container-high)' : 'transparent',
              borderRadius: 2,
              transition: 'all 100ms ease-out',
            }}
          >
            {t}
          </button>
        ))}
      </div>

      {/* Size */}
      <div>
        <div style={labelStyle}>Size</div>
        <input
          type="number"
          value={size}
          onChange={(e) => setSize(e.target.value)}
          placeholder="0.00"
          style={inputStyle}
          step="0.001"
          min="0"
        />
      </div>

      {/* Price (disabled for MARKET) */}
      <div>
        <div style={labelStyle}>Price</div>
        <input
          type="number"
          value={price}
          onChange={(e) => setPrice(e.target.value)}
          placeholder={orderType === 'MARKET' ? 'Market' : '0.00'}
          style={{
            ...inputStyle,
            opacity: orderType === 'MARKET' ? 0.4 : 1,
            cursor: orderType === 'MARKET' ? 'not-allowed' : 'text',
          }}
          disabled={orderType === 'MARKET'}
          step="0.01"
          min="0"
        />
      </div>

      {/* Stop Loss */}
      <div>
        <div style={labelStyle}>Stop Loss</div>
        <input
          type="number"
          value={stopLoss}
          onChange={(e) => setStopLoss(e.target.value)}
          placeholder="Optional"
          style={inputStyle}
          step="0.01"
          min="0"
        />
      </div>

      {/* Take Profit */}
      <div>
        <div style={labelStyle}>Take Profit</div>
        <input
          type="number"
          value={takeProfit}
          onChange={(e) => setTakeProfit(e.target.value)}
          placeholder="Optional"
          style={inputStyle}
          step="0.01"
          min="0"
        />
      </div>

      {/* Submit */}
      <button
        onClick={handleSubmit}
        style={{
          width: '100%',
          padding: '10px 0',
          border: 'none',
          cursor: 'pointer',
          fontFamily: bodyFont,
          fontSize: '0.8125rem',
          fontWeight: 600,
          letterSpacing: '0.02em',
          color: '#fff',
          background: isBuy
            ? 'linear-gradient(135deg, #00e479, #00a657)'
            : 'linear-gradient(135deg, #ff6b6b, #cc3333)',
          boxShadow: isBuy
            ? '0 0 20px rgba(0, 228, 121, 0.2)'
            : '0 0 20px rgba(255, 107, 107, 0.2)',
          transition: 'all 150ms ease-out',
          marginTop: 4,
        }}
      >
        Place {side} Order
      </button>
    </div>
  );
}

/* ───────────────────────────────────────────────────────────────────────────────
   BOTTOM TABLES
   ─────────────────────────────────────────────────────────────────────────────── */
function EmptyState({ message }: { message: string }) {
  return (
    <div style={{
      padding: '2rem 1rem', textAlign: 'center',
      fontFamily: monoFont, fontSize: '0.75rem', color: textMuted,
    }}>
      {message}
    </div>
  );
}

function PositionsTable() {
  return <EmptyState message="No open positions" />;
}

function OrdersTable() {
  return <EmptyState message="No open orders" />;
}

function HistoryTable() {
  return <EmptyState message="No trade history" />;
}

function BottomTabs() {
  const [tab, setTab] = useState<Tab>('Open Positions');

  const columns: Record<Tab, string[]> = {
    'Open Positions': ['Pair', 'Side', 'Size', 'Entry Price', 'Current', 'P&L', 'Leverage', 'Action'],
    'Open Orders': ['Pair', 'Side', 'Type', 'Size', 'Price', 'SL', 'TP', 'Status', 'Action'],
    'Trade History': ['Time', 'Pair', 'Side', 'Type', 'Size', 'Price', 'Status'],
  };

  const tables: Record<Tab, React.ReactNode> = {
    'Open Positions': <PositionsTable />,
    'Open Orders': <OrdersTable />,
    'Trade History': <HistoryTable />,
  };

  return (
    <div style={{ background: cardBg, overflow: 'hidden' }}>
      {/* Tab headers */}
      <div style={{
        display: 'flex', borderBottom: '1px solid rgba(66, 71, 84, 0.2)',
        overflow: 'auto',
      }}>
        {TABS.map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            style={{
              padding: '10px 16px',
              fontFamily: monoFont,
              fontSize: '0.6875rem',
              fontWeight: 500,
              color: tab === t ? 'var(--primary)' : textMuted,
              background: 'none',
              border: 'none',
              borderBottom: tab === t ? '2px solid var(--primary)' : '2px solid transparent',
              cursor: 'pointer',
              textTransform: 'uppercase',
              letterSpacing: '0.06em',
              whiteSpace: 'nowrap',
              transition: 'all 150ms ease-out',
            }}
          >
            {t}
          </button>
        ))}
      </div>

      {/* Column headers */}
      <div style={{ overflowX: 'auto' }}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: columns[tab].map(() => '1fr').join(' '),
          padding: '8px 12px',
          borderBottom: '1px solid rgba(66, 71, 84, 0.1)',
          minWidth: columns[tab].length * 100,
        }}>
          {columns[tab].map((col) => (
            <span key={col} style={{
              fontFamily: monoFont,
              fontSize: '0.625rem',
              color: textMuted,
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
              fontWeight: 500,
            }}>
              {col}
            </span>
          ))}
        </div>

        {/* Table body */}
        {tables[tab]}
      </div>
    </div>
  );
}

/* ───────────────────────────────────────────────────────────────────────────────
   MAIN TRADE PAGE
   ─────────────────────────────────────────────────────────────────────────────── */
export default function TradePage() {
  const [symbol, setSymbol] = useState<Symbol>('BTCUSD');
  const [timeframe, setTimeframe] = useState<Timeframe>('1h');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, height: 'calc(100vh - 130px)' }}>
      {/* ── TOP: L-Shape Grid ── */}
      <div
        className="trade-grid"
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 320px',
          gridTemplateRows: '1fr',
          gap: 8,
          flex: 1,
          minHeight: 0,
        }}
      >
        {/* ─ LEFT: Chart Panel ─ */}
        <div style={{
          background: cardBg,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          minHeight: 0,
        }}>
          {/* Symbol selector row */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: 4,
            padding: '8px 10px', borderBottom: '1px solid rgba(66, 71, 84, 0.2)',
            flexWrap: 'wrap',
          }}>
            {SYMBOLS.map((s) => (
              <button
                key={s}
                onClick={() => setSymbol(s)}
                style={{
                  padding: '4px 10px',
                  fontFamily: monoFont,
                  fontSize: '0.6875rem',
                  fontWeight: symbol === s ? 600 : 400,
                  color: symbol === s ? textPrimary : textMuted,
                  background: symbol === s ? 'var(--surface-container-highest)' : 'transparent',
                  border: symbol === s ? '1px solid rgba(66, 71, 84, 0.4)' : '1px solid transparent',
                  cursor: 'pointer',
                  transition: 'all 100ms ease-out',
                  letterSpacing: '0.02em',
                }}
              >
                {s}
              </button>
            ))}
            <span style={{ fontFamily: monoFont, fontSize: '0.625rem', color: textMuted, marginLeft: 4 }}>+4 more</span>
          </div>

          {/* Timeframe row */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: 4,
            padding: '6px 10px', borderBottom: '1px solid rgba(66, 71, 84, 0.15)',
          }}>
            {TIMEFRAMES.map((tf) => (
              <button
                key={tf}
                onClick={() => setTimeframe(tf)}
                style={{
                  padding: '3px 12px',
                  fontFamily: monoFont,
                  fontSize: '0.625rem',
                  fontWeight: 500,
                  color: timeframe === tf ? '#fff' : textMuted,
                  background: timeframe === tf
                    ? 'linear-gradient(135deg, var(--primary), var(--primary-container))'
                    : 'transparent',
                  border: 'none',
                  borderRadius: 10,
                  cursor: 'pointer',
                  transition: 'all 100ms ease-out',
                  letterSpacing: '0.04em',
                }}
              >
                {tf}
              </button>
            ))}
            <span style={{
              marginLeft: 'auto',
              fontFamily: monoFont,
              fontSize: '0.6875rem',
              fontWeight: 600,
              color: textPrimary,
            }}>
              {symbol}
            </span>
          </div>

          {/* Chart area */}
          <div style={{ flex: 1, minHeight: 0, position: 'relative' }}>
            <CandlestickChart symbol={symbol} timeframe={timeframe} />
          </div>
        </div>

        {/* ─ RIGHT: Order Book + Order Entry ─ */}
        <div style={{
          display: 'grid',
          gridTemplateRows: '1fr auto',
          gap: 8,
          minHeight: 0,
          overflow: 'hidden',
        }}>
          {/* Order Book */}
          <div style={{
            background: cardBg,
            overflow: 'hidden',
            minHeight: 0,
          }}>
            <OrderBook symbol={symbol} />
          </div>

          {/* Order Entry */}
          <div style={{
            background: cardBg,
            overflow: 'auto',
          }}>
            <div style={{
              padding: '8px 10px 4px',
              borderBottom: '1px solid rgba(66, 71, 84, 0.2)',
            }}>
              <span style={{ fontFamily: bodyFont, fontSize: '0.6875rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.06em', color: textPrimary }}>
                Order Entry
              </span>
            </div>
            <OrderEntry symbol={symbol} />
          </div>
        </div>
      </div>

      {/* ── BOTTOM: Tabbed Table ── */}
      <BottomTabs />

      {/* ── Responsive Media Query Style ── */}
      <style>{`
        .trade-grid {
          grid-template-columns: 1fr 320px;
        }
        @media (max-width: 768px) {
          .trade-grid {
            grid-template-columns: 1fr !important;
            grid-template-rows: 400px auto auto !important;
          }
        }
      `}</style>
    </div>
  );
}
