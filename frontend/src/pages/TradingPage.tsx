import { useEffect, useMemo, useState, useCallback, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { PageHeader } from '@/components/ui/PageHeader'
import { MaterialIcon } from '@/components/ui/MaterialIcon'
import {
  deltaApi,
  createDeltaWebSocket,
  type DeltaProduct,
  type DeltaTicker,
  type DeltaOrderBook,
  type DeltaOrder,
  type DeltaPosition,
  type DeltaBalance,
  type DeltaTrade,
  type OrderBookLevel,
} from '@/services/deltaExchange'
import {
  createChart,
  ColorType,
  CrosshairMode,
  CandlestickSeries,
  HistogramSeries,
} from 'lightweight-charts'
import type { IChartApi, ISeriesApi, CandlestickData, HistogramData, Time } from 'lightweight-charts'

// ─── Helpers ───
function fmtNum(n: number | string, d = 2) {
  const v = typeof n === 'string' ? parseFloat(n) : n
  if (isNaN(v)) return '—'
  return v.toLocaleString(undefined, { minimumFractionDigits: d, maximumFractionDigits: d })
}
function fmtMoney(n: number | string) {
  const v = typeof n === 'string' ? parseFloat(n) : n
  if (isNaN(v)) return '$0.00'
  return v.toLocaleString(undefined, { style: 'currency', currency: 'USD', maximumFractionDigits: 2 })
}
function fmtCompact(n: number | string) {
  const v = typeof n === 'string' ? parseFloat(n) : n
  if (isNaN(v)) return '—'
  if (v >= 1e9) return `$${(v / 1e9).toFixed(2)}B`
  if (v >= 1e6) return `$${(v / 1e6).toFixed(2)}M`
  if (v >= 1e3) return `$${(v / 1e3).toFixed(1)}K`
  return `$${v.toFixed(2)}`
}
function pnlColor(n: number | string) {
  const v = typeof n === 'string' ? parseFloat(n) : n
  return v >= 0 ? 'var(--tertiary)' : 'var(--error)'
}
function changeColor(n: number | string) {
  const v = typeof n === 'string' ? parseFloat(n) : n
  return v >= 0 ? 'var(--tertiary)' : 'var(--error)'
}
// Testnet may not have last_price — fall back to close or mark_price
function tickerPrice(t: DeltaTicker | null | undefined): number {
  if (!t) return 0
  return parseFloat(t.last_price) || parseFloat(t.close) || parseFloat(t.mark_price) || 0
}
function tickerChange(t: DeltaTicker | null | undefined): number {
  if (!t) return 0
  const c = parseFloat(t.change_24h)
  if (!isNaN(c) && c !== 0) return c
  const price = tickerPrice(t)
  const open = parseFloat(t.open) || price
  return open > 0 ? ((price - open) / open) * 100 : 0
}

type ProductCategory = 'perpetual_futures' | 'call_options' | 'put_options' | 'spot' | 'move_options'
type BottomTab = 'positions' | 'open_orders' | 'stop_orders' | 'fills' | 'history'

const CATEGORIES: { key: ProductCategory | 'all'; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'perpetual_futures', label: 'Futures' },
  { key: 'call_options', label: 'Options' },
  { key: 'spot', label: 'Spot' },
]

const TIMEFRAMES = ['1m', '5m', '15m', '1h', '4h', '1d'] as const

// ─── Connect Hero ───
function ConnectDelta({ onConnected }: { onConnected: () => void }) {
  const [apiKey, setApiKey] = useState('')
  const [apiSecret, setApiSecret] = useState('')
  const [useTestnet, setUseTestnet] = useState(true)
  const [testing, setTesting] = useState(false)
  const [result, setResult] = useState<string | null>(null)

  const handleConnect = async () => {
    if (!apiKey || !apiSecret) return
    setTesting(true)
    setResult(null)
    try {
      deltaApi.saveConfig(apiKey, apiSecret, useTestnet)
      const balances = await deltaApi.getBalances()
      const usdt = balances.find(b => b.asset_symbol === 'USDT')
      setResult(`Connected! Balance: ${usdt ? parseFloat(usdt.available_balance).toFixed(2) : '0.00'} USDT`)
      toast.success('Delta Exchange connected')
      setTimeout(onConnected, 800)
    } catch (err) {
      setResult(`Failed: ${(err as Error).message}`)
      deltaApi.clearConfig()
    } finally {
      setTesting(false)
    }
  }

  return (
    <div className="connect-hero">
      <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
        <div style={{
          width: 64, height: 64, margin: '0 auto 1rem',
          background: 'linear-gradient(135deg, var(--primary), var(--secondary))',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: '1.5rem', fontWeight: 700, fontFamily: 'var(--font-display)',
          color: 'var(--surface)',
        }}>Δ</div>
        <h2 style={{ fontFamily: 'var(--font-display)', fontSize: '1.5rem', fontWeight: 700, color: 'var(--on-surface)', marginBottom: '0.5rem' }}>
          Connect Delta Exchange
        </h2>
        <p style={{ fontFamily: 'var(--font-body)', fontSize: '0.875rem', color: 'var(--on-surface-variant)', lineHeight: 1.6 }}>
          Link your Delta Exchange account to enable live trading across crypto futures, options, and spot markets.
        </p>
      </div>

      {/* Testnet toggle */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0.75rem 1rem', background: 'var(--surface-container-lowest)', marginBottom: '1.25rem' }}>
        <div>
          <div style={{ fontSize: '0.8125rem', fontWeight: 500, color: 'var(--on-surface)' }}>Testnet Mode</div>
          <div style={{ fontSize: '0.6875rem', color: 'var(--outline)', fontFamily: 'var(--font-mono)' }}>Paper trading — no real funds</div>
        </div>
        <button
          onClick={() => setUseTestnet(v => !v)}
          style={{
            width: 44, height: 24, borderRadius: 12, border: 'none', cursor: 'pointer',
            background: useTestnet ? 'var(--tertiary)' : 'var(--outline-variant)', position: 'relative', transition: 'background 150ms',
          }}
        >
          <span style={{
            position: 'absolute', top: 2, width: 20, height: 20, borderRadius: '50%',
            background: '#fff', transition: 'left 150ms',
            left: useTestnet ? 22 : 2,
          }} />
        </button>
      </div>

      <div style={{ marginBottom: '1rem' }}>
        <label className="input-label">API Key</label>
        <input type="text" value={apiKey} onChange={e => setApiKey(e.target.value)}
          placeholder="Enter API key" className="input-terminal" style={{ width: '100%' }} />
      </div>
      <div style={{ marginBottom: '1.5rem' }}>
        <label className="input-label">API Secret</label>
        <input type="password" value={apiSecret} onChange={e => setApiSecret(e.target.value)}
          placeholder="Enter API secret" className="input-terminal" style={{ width: '100%' }} />
      </div>

      <button onClick={handleConnect} disabled={!apiKey || !apiSecret || testing}
        className="btn-primary" style={{ width: '100%', justifyContent: 'center', padding: '0.75rem', fontSize: '0.8125rem' }}>
        <MaterialIcon name="link" size={16} />
        {testing ? 'Connecting...' : 'Connect Account'}
      </button>

      {result && (
        <div style={{
          marginTop: '1rem', padding: '0.75rem', fontSize: '0.75rem', fontFamily: 'var(--font-mono)',
          background: result.startsWith('Connected') ? 'rgba(0,228,121,0.08)' : 'rgba(255,180,171,0.08)',
          borderLeft: `3px solid ${result.startsWith('Connected') ? 'var(--tertiary)' : 'var(--error)'}`,
          color: result.startsWith('Connected') ? 'var(--tertiary)' : 'var(--error)',
        }}>
          {result}
        </div>
      )}

      <div style={{ marginTop: '1.5rem', padding: '1rem', background: 'var(--surface-container-lowest)', borderLeft: '2px solid var(--primary)' }}>
        <p style={{ fontSize: '0.6875rem', color: 'var(--outline)', fontFamily: 'var(--font-mono)', letterSpacing: '0.06em', textTransform: 'uppercase', marginBottom: '0.5rem', fontWeight: 600 }}>How to get keys</p>
        <ol style={{ fontSize: '0.75rem', color: 'var(--on-surface-variant)', lineHeight: 1.8, paddingLeft: '1rem' }}>
          <li>Go to Delta Exchange → Account → API Keys</li>
          <li>Create a key with trading permissions</li>
          <li>Copy key & secret here</li>
        </ol>
      </div>
    </div>
  )
}

// ─── Order Book ───
function OrderBook({ book, ticker }: { book: DeltaOrderBook | null; ticker: DeltaTicker | null }) {
  const asks = useMemo(() => (book?.sell ?? []).slice(0, 10).reverse(), [book])
  const bids = useMemo(() => (book?.buy ?? []).slice(0, 10), [book])
  const maxSize = useMemo(() => {
    const all = [...(book?.sell ?? []), ...(book?.buy ?? [])]
    return Math.max(...all.map(l => l.size), 1)
  }, [book])

  // Compute cumulative totals
  const asksWithTotal = useMemo(() => {
    const reversed = [...asks].reverse()
    let cum = 0
    const withTotals = reversed.map(l => { cum += l.size; return { ...l, total: cum } })
    return withTotals.reverse()
  }, [asks])

  const bidsWithTotal = useMemo(() => {
    let cum = 0
    return bids.map(l => { cum += l.size; return { ...l, total: cum } })
  }, [bids])

  return (
    <div style={{ background: 'var(--surface-container-lowest)', fontFamily: 'var(--font-mono)', fontSize: '0.6875rem', height: '100%' }}>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', padding: '0.5rem 0.75rem', borderBottom: '1px solid rgba(66,71,84,0.15)' }}>
        <span style={{ color: 'var(--outline)', fontSize: '0.5625rem', textTransform: 'uppercase', letterSpacing: '0.08em' }}>Price</span>
        <span style={{ color: 'var(--outline)', fontSize: '0.5625rem', textTransform: 'uppercase', letterSpacing: '0.08em', textAlign: 'right' }}>Size</span>
        <span style={{ color: 'var(--outline)', fontSize: '0.5625rem', textTransform: 'uppercase', letterSpacing: '0.08em', textAlign: 'right' }}>Total</span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {asksWithTotal.length === 0
          ? <div style={{ padding: '1rem', textAlign: 'center', color: 'var(--outline)' }}>No asks</div>
          : asksWithTotal.map((l, i) => <BookRow key={`a${i}`} level={l} maxSize={maxSize} side="sell" total={l.total} />)}
      </div>

      {/* Spread / Last price */}
      <div style={{
        padding: '0.5rem 0.75rem', textAlign: 'center',
        background: 'var(--surface-container)', borderTop: '1px solid rgba(66,71,84,0.15)', borderBottom: '1px solid rgba(66,71,84,0.15)',
      }}>
        <span style={{ fontSize: '1.125rem', fontWeight: 700, fontFamily: 'var(--font-display)', color: 'var(--on-surface)' }}>
          {ticker ? `$${fmtNum(tickerPrice(ticker))}` : '—'}
        </span>
        {ticker && (
          <span style={{ marginLeft: '0.5rem', fontSize: '0.6875rem', color: changeColor(tickerChange(ticker)) }}>
            {tickerChange(ticker) >= 0 ? '+' : ''}{fmtNum(tickerChange(ticker))}%
          </span>
        )}
      </div>

      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {bidsWithTotal.length === 0
          ? <div style={{ padding: '1rem', textAlign: 'center', color: 'var(--outline)' }}>No bids</div>
          : bidsWithTotal.map((l, i) => <BookRow key={`b${i}`} level={l} maxSize={maxSize} side="buy" total={l.total} />)}
      </div>
    </div>
  )
}

function BookRow({ level, maxSize, side, total }: { level: OrderBookLevel; maxSize: number; side: 'buy' | 'sell'; total: number }) {
  const pct = (level.size / maxSize) * 100
  const color = side === 'buy' ? 'rgba(0, 228, 121, 0.12)' : 'rgba(255, 180, 171, 0.12)'
  const textColor = side === 'buy' ? 'var(--tertiary)' : 'var(--error)'
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', padding: '1.5px 0.75rem', position: 'relative' }}>
      <div style={{ position: 'absolute', top: 0, bottom: 0, right: 0, width: `${pct}%`, background: color, transition: 'width 120ms ease-out' }} />
      <span style={{ color: textColor, position: 'relative', zIndex: 1 }}>{fmtNum(level.price)}</span>
      <span style={{ color: 'var(--on-surface)', position: 'relative', zIndex: 1, textAlign: 'right' }}>{level.size.toLocaleString()}</span>
      <span style={{ color: 'var(--outline)', position: 'relative', zIndex: 1, textAlign: 'right' }}>{total.toLocaleString()}</span>
    </div>
  )
}

// ─── Recent Trades ───
function RecentTrades({ trades }: { trades: DeltaTrade[] }) {
  return (
    <div style={{ fontFamily: 'var(--font-mono)', fontSize: '0.6875rem' }}>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', padding: '0.5rem 0.75rem', borderBottom: '1px solid rgba(66,71,84,0.15)' }}>
        <span style={{ color: 'var(--outline)', fontSize: '0.5625rem', textTransform: 'uppercase' }}>Price</span>
        <span style={{ color: 'var(--outline)', fontSize: '0.5625rem', textTransform: 'uppercase', textAlign: 'right' }}>Size</span>
        <span style={{ color: 'var(--outline)', fontSize: '0.5625rem', textTransform: 'uppercase', textAlign: 'right' }}>Time</span>
      </div>
      {trades.slice(0, 15).map((t, i) => (
        <div key={i} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', padding: '1.5px 0.75rem' }}>
          <span style={{ color: t.side === 'buy' ? 'var(--tertiary)' : 'var(--error)' }}>{fmtNum(t.price)}</span>
          <span style={{ color: 'var(--on-surface)', textAlign: 'right' }}>{t.size}</span>
          <span style={{ color: 'var(--outline)', textAlign: 'right' }}>{new Date(t.timestamp / 1000).toLocaleTimeString()}</span>
        </div>
      ))}
    </div>
  )
}

// ─── TradingView Chart ───
function TradingChart({ symbol, productId }: { symbol: string; productId: number }) {
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const candleRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
  const volRef = useRef<ISeriesApi<'Histogram'> | null>(null)
  const [tf, setTf] = useState<string>('1h')

  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    
    const chart = createChart(el, {
      autoSize: true,
      layout: { background: { type: ColorType.Solid, color: '#090e1b' }, textColor: '#8c909f', attributionLogo: false },
      grid: { vertLines: { color: '#161b29' }, horzLines: { color: '#161b29' } },
      crosshair: { mode: CrosshairMode.Normal },
      rightPriceScale: { borderColor: '#424754' },
      timeScale: { borderColor: '#424754', timeVisible: true, secondsVisible: false },
    })
    const candle = chart.addSeries(CandlestickSeries, {
      upColor: '#00e479', downColor: '#ffb4ab', borderVisible: false,
      wickUpColor: '#00e479', wickDownColor: '#ffb4ab',
    })
    const vol = chart.addSeries(HistogramSeries, { color: '#4d8eff', priceFormat: { type: 'volume' }, priceScaleId: 'vol' })
    chart.priceScale('vol').applyOptions({ scaleMargins: { top: 0.85, bottom: 0 } })
    
    chartRef.current = chart
    candleRef.current = candle
    volRef.current = vol
    
    return () => { 
      chart.remove()
      chartRef.current = null
      candleRef.current = null
      volRef.current = null
    }
  }, [])

  useEffect(() => {
    if (!symbol) return
    const end = Math.floor(Date.now() / 1000)
    const start = end - (tf === '1m' ? 3600 * 6 : tf === '5m' ? 3600 * 24 : tf === '15m' ? 86400 * 3 : tf === '1h' ? 86400 * 14 : tf === '4h' ? 86400 * 30 : 86400 * 180)
    deltaApi.getOHLCCandles(symbol, tf, start, end)
      .then(candles => {
        if (!candleRef.current || !chartRef.current) return
        const safeCandles = Array.isArray(candles) ? candles : []
        const cd: CandlestickData[] = safeCandles.map(c => ({ time: c.time as Time, open: c.open, high: c.high, low: c.low, close: c.close }))
        const vd: HistogramData[] = safeCandles.map(c => ({ time: c.time as Time, value: c.volume, color: c.close >= c.open ? 'rgba(0,228,121,0.4)' : 'rgba(255,180,171,0.4)' }))
        candleRef.current.setData(cd)
        volRef.current?.setData(vd)
        chartRef.current.timeScale().fitContent()
      })
      .catch(() => {})
  }, [symbol, tf])

  return (
    <div style={{ background: 'var(--surface-container-lowest)', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', gap: '2px', padding: '0.375rem 0.5rem', borderBottom: '1px solid rgba(66,71,84,0.15)' }}>
        {TIMEFRAMES.map(t => (
          <button key={t} onClick={() => setTf(t)}
            style={{
              padding: '0.25rem 0.5rem', fontSize: '0.625rem', fontFamily: 'var(--font-mono)', fontWeight: 500,
              border: 'none', cursor: 'pointer',
              background: tf === t ? 'rgba(173,198,255,0.12)' : 'transparent',
              color: tf === t ? 'var(--primary)' : 'var(--outline)',
              transition: 'all 100ms',
            }}>
            {t.toUpperCase()}
          </button>
        ))}
      </div>
      <div ref={containerRef} style={{ flex: 1, minHeight: 300 }} />
    </div>
  )
}

// ─── Markets Table ───
function MarketsTable({
  products, tickers, onSelect, searchQuery, category,
}: {
  products: DeltaProduct[]; tickers: DeltaTicker[]; onSelect: (p: DeltaProduct) => void;
  searchQuery: string; category: ProductCategory | 'all';
}) {
  const tickerMap = useMemo(() => {
    const m: Record<string, DeltaTicker> = {}
    tickers.forEach(t => { m[t.symbol] = t })
    return m
  }, [tickers])

  const filtered = useMemo(() => {
    let list = products.filter(p => p.state === 'live')
    if (category !== 'all') list = list.filter(p => p.contract_type === category)
    if (searchQuery) {
      const q = searchQuery.toLowerCase()
      list = list.filter(p => p.symbol.toLowerCase().includes(q) || p.description.toLowerCase().includes(q))
    }
    return list.slice(0, 80)
  }, [products, category, searchQuery])

  return (
    <div style={{ background: 'var(--surface-container-low)', overflow: 'hidden' }}>
      {/* Header */}
      <div className="product-row" style={{
        gridTemplateColumns: '2fr 2fr 1.2fr 1fr 1.2fr 1.2fr 1.2fr 1fr',
        cursor: 'default', borderBottom: '1px solid rgba(66,71,84,0.2)',
      }}>
        {['Contract', 'Description', 'Last Price', '24h Change', '24h Volume', 'Open Interest', '24h Prices', 'Funding'].map(h => (
          <span key={h} style={{ color: 'var(--outline)', fontSize: '0.5625rem', textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 500 }}>{h}</span>
        ))}
      </div>
      {/* Rows */}
      <div style={{ maxHeight: 500, overflowY: 'auto' }}>
        {filtered.length === 0 ? (
          <div style={{ padding: '3rem', textAlign: 'center', color: 'var(--outline)' }}>No products found</div>
        ) : filtered.map(p => {
          const t = tickerMap[p.symbol]
          const price = tickerPrice(t)
          const change = tickerChange(t)
          return (
            <div key={p.id} className="product-row" onClick={() => onSelect(p)}
              style={{ gridTemplateColumns: '2fr 2fr 1.2fr 1fr 1.2fr 1.2fr 1.2fr 1fr' }}>
              <span style={{ fontWeight: 600, color: 'var(--on-surface)' }}>
                <MaterialIcon name={change >= 0 ? 'arrow_drop_up' : 'arrow_drop_down'} size={14} style={{ color: changeColor(change), verticalAlign: 'middle' } as React.CSSProperties} />
                {p.symbol}
              </span>
              <span style={{ color: 'var(--on-surface-variant)', fontSize: '0.6875rem' }}>
                {p.description}
                {p.contract_type === 'perpetual_futures' && (
                  <span style={{ marginLeft: 6, padding: '1px 4px', fontSize: '0.5625rem', background: 'rgba(173,198,255,0.1)', color: 'var(--primary)' }}>
                    {p.max_leverage_notional ? `${Math.floor(1 / parseFloat(p.initial_margin || '0.01'))}x` : '—'}
                  </span>
                )}
              </span>
              <span style={{ fontWeight: 500 }}>{price > 0 ? `$${fmtNum(price)}` : '—'}</span>
              <span style={{ color: changeColor(change), fontWeight: 500 }}>{change >= 0 ? '+' : ''}{fmtNum(change)}%</span>
              <span>{t ? fmtCompact(t.turnover_usd || t.turnover || '0') : '—'}</span>
              <span>{t?.oi_value_usd ? fmtCompact(t.oi_value_usd) : '—'}</span>
              <span style={{ fontSize: '0.625rem' }}>
                {t ? (
                  <>
                    <span style={{ color: 'var(--tertiary)' }}>H: {fmtNum(t.high)}</span>{' '}
                    <span style={{ color: 'var(--error)' }}>L: {fmtNum(t.low)}</span>
                  </>
                ) : '—'}
              </span>
              <span style={{ color: 'var(--secondary)', fontSize: '0.625rem' }}>{t?.funding_rate ? `${fmtNum(parseFloat(t.funding_rate) * 100, 4)}%` : '—'}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

// ─── Order Form ───
function OrderForm({ symbol, ticker, product }: { symbol: string; ticker: DeltaTicker | null; product: DeltaProduct | null }) {
  const qc = useQueryClient()
  const [side, setSide] = useState<'buy' | 'sell'>('buy')
  const [orderType, setOrderType] = useState<'limit_order' | 'market_order'>('limit_order')
  const [size, setSize] = useState('1')
  const [limitPrice, setLimitPrice] = useState('')
  const [leverage, setLeverage] = useState(10)
  const [stopLoss, setStopLoss] = useState('')
  const [takeProfit, setTakeProfit] = useState('')
  const [reduceOnly, setReduceOnly] = useState(false)
  const [showBracket, setShowBracket] = useState(false)

  const { data: balances = [] } = useQuery<DeltaBalance[]>({
    queryKey: ['delta-balances'],
    queryFn: () => deltaApi.getBalances(),
    refetchInterval: 10_000,
  })
  const usdtBalance = balances.find(b => b.asset_symbol === 'USDT')

  const maxLeverage = product?.initial_margin ? Math.floor(1 / parseFloat(product.initial_margin)) : 100

  const placeOrderMut = useMutation({
    mutationFn: async () => {
      const order: Parameters<typeof deltaApi.placeOrder>[0] = {
        product_symbol: symbol, side, size: parseInt(size) || 1, order_type: orderType,
      }
      if (orderType === 'limit_order' && limitPrice) order.limit_price = limitPrice
      if (stopLoss) order.bracket_stop_loss_price = stopLoss
      if (takeProfit) order.bracket_take_profit_price = takeProfit
      if (reduceOnly) order.reduce_only = true
      return deltaApi.placeOrder(order)
    },
    onSuccess: (res) => {
      toast.success(`Order placed: ${res.side.toUpperCase()} ${res.size} ${res.product_symbol}`)
      qc.invalidateQueries({ queryKey: ['delta-orders'] })
      qc.invalidateQueries({ queryKey: ['delta-balances'] })
      setSize('1'); setLimitPrice(''); setStopLoss(''); setTakeProfit('')
    },
    onError: (err) => toast.error('Order failed', { description: (err as Error).message }),
  })

  const fillPrice = useCallback(() => { if (ticker) setLimitPrice(String(tickerPrice(ticker))) }, [ticker])

  return (
    <div style={{ background: 'var(--surface-container-low)', padding: '1rem', display: 'flex', flexDirection: 'column', gap: '0.75rem', height: '100%', overflowY: 'auto' }}>
      {/* Side toggle */}
      <div style={{ display: 'flex', gap: 0, border: '1px solid var(--outline-variant)' }}>
        <button onClick={() => setSide('buy')} style={{
          flex: 1, padding: '0.625rem', fontWeight: 600, fontSize: '0.75rem', fontFamily: 'var(--font-mono)',
          border: 'none', cursor: 'pointer',
          background: side === 'buy' ? 'rgba(0,228,121,0.15)' : 'transparent',
          color: side === 'buy' ? 'var(--tertiary)' : 'var(--outline)', transition: 'all 150ms',
        }}>BUY / LONG</button>
        <button onClick={() => setSide('sell')} style={{
          flex: 1, padding: '0.625rem', fontWeight: 600, fontSize: '0.75rem', fontFamily: 'var(--font-mono)',
          border: 'none', cursor: 'pointer',
          background: side === 'sell' ? 'rgba(255,180,171,0.15)' : 'transparent',
          color: side === 'sell' ? 'var(--error)' : 'var(--outline)', transition: 'all 150ms',
        }}>SELL / SHORT</button>
      </div>

      {/* Leverage */}
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.375rem' }}>
          <label className="input-label" style={{ marginBottom: 0 }}>Leverage</label>
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', fontWeight: 600, color: 'var(--primary)' }}>{leverage}x</span>
        </div>
        <input type="range" min={1} max={maxLeverage} value={leverage} onChange={e => setLeverage(Number(e.target.value))} className="leverage-slider" />
      </div>

      {/* Order type */}
      <div style={{ display: 'flex', gap: '0.375rem' }}>
        {(['limit_order', 'market_order'] as const).map(t => (
          <button key={t} onClick={() => setOrderType(t)} style={{
            flex: 1, padding: '0.4rem', fontSize: '0.625rem', fontFamily: 'var(--font-mono)', fontWeight: 500,
            border: 'none', cursor: 'pointer',
            background: orderType === t ? 'rgba(173,198,255,0.12)' : 'var(--surface)',
            color: orderType === t ? 'var(--primary)' : 'var(--outline)', transition: 'all 150ms',
          }}>{t === 'limit_order' ? 'LIMIT' : 'MARKET'}</button>
        ))}
      </div>

      {/* Quantity */}
      <div>
        <label className="input-label">Quantity</label>
        <input type="number" min="1" value={size} onChange={e => setSize(e.target.value)} className="input-terminal" style={{ width: '100%' }} />
        <div style={{ display: 'flex', gap: '0.25rem', marginTop: '0.375rem' }}>
          {['10%', '25%', '50%', '75%', '100%'].map(p => (
            <button key={p} className="size-btn" style={{ flex: 1 }}>{p}</button>
          ))}
        </div>
      </div>

      {/* Limit Price */}
      {orderType === 'limit_order' && (
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <label className="input-label" style={{ marginBottom: 0 }}>Limit Price</label>
            <button onClick={fillPrice} style={{ background: 'none', border: 'none', cursor: 'pointer', fontFamily: 'var(--font-mono)', fontSize: '0.625rem', color: 'var(--primary)' }}>LAST</button>
          </div>
          <input type="text" value={limitPrice} onChange={e => setLimitPrice(e.target.value)} placeholder="0.00" className="input-terminal" style={{ width: '100%', marginTop: '0.25rem' }} />
        </div>
      )}

      {/* Bracket / TP/SL */}
      <button onClick={() => setShowBracket(v => !v)} style={{
        background: 'none', border: 'none', cursor: 'pointer', fontFamily: 'var(--font-mono)',
        fontSize: '0.6875rem', color: 'var(--secondary)', display: 'flex', alignItems: 'center', gap: '0.25rem',
      }}>
        <MaterialIcon name={showBracket ? 'remove' : 'add'} size={14} /> {showBracket ? 'Hide' : 'Add'} TP/SL
      </button>

      {showBracket && (
        <>
          <div>
            <label className="input-label">Stop Loss</label>
            <input type="text" value={stopLoss} onChange={e => setStopLoss(e.target.value)} placeholder="Trigger price" className="input-terminal" style={{ width: '100%' }} />
          </div>
          <div>
            <label className="input-label">Take Profit</label>
            <input type="text" value={takeProfit} onChange={e => setTakeProfit(e.target.value)} placeholder="Trigger price" className="input-terminal" style={{ width: '100%' }} />
          </div>
        </>
      )}

      {/* Reduce Only */}
      <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.6875rem', color: 'var(--on-surface-variant)', cursor: 'pointer' }}>
        <input type="checkbox" checked={reduceOnly} onChange={e => setReduceOnly(e.target.checked)} style={{ accentColor: 'var(--primary)' }} />
        Reduce Only
      </label>

      {/* Funds info */}
      <div style={{ borderTop: '1px solid rgba(66,71,84,0.15)', paddingTop: '0.5rem', display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.6875rem' }}>
          <span style={{ color: 'var(--outline)' }}>Available Margin</span>
          <span style={{ color: 'var(--on-surface)', fontFamily: 'var(--font-mono)' }}>{usdtBalance ? fmtMoney(usdtBalance.available_balance) : '$0.00'}</span>
        </div>
      </div>

      {/* Submit */}
      <button onClick={() => placeOrderMut.mutate()} disabled={placeOrderMut.isPending || !size}
        style={{
          width: '100%', padding: '0.75rem',
          background: side === 'buy'
            ? 'linear-gradient(135deg, var(--tertiary), var(--tertiary-container))'
            : 'linear-gradient(135deg, var(--error), var(--error-container))',
          color: side === 'buy' ? 'var(--on-tertiary)' : 'var(--on-error)',
          fontFamily: 'var(--font-mono)', fontSize: '0.8125rem', fontWeight: 700,
          letterSpacing: '0.04em', textTransform: 'uppercase', border: 'none', cursor: 'pointer',
          opacity: placeOrderMut.isPending ? 0.5 : 1, transition: 'all 150ms',
        }}>
        {placeOrderMut.isPending ? 'Placing...' : side === 'buy' ? 'Buy' : 'Sell'}
      </button>
    </div>
  )
}

// ─── Bottom Panel ───
function BottomPanel() {
  const [tab, setTab] = useState<BottomTab>('positions')
  const configured = deltaApi.isConfigured()

  const { data: positions = [] } = useQuery<DeltaPosition[]>({ queryKey: ['delta-positions'], queryFn: () => deltaApi.getPositions(), enabled: configured, refetchInterval: 5000 })
  const { data: openOrders = [] } = useQuery<DeltaOrder[]>({ queryKey: ['delta-orders'], queryFn: () => deltaApi.getOpenOrders(), enabled: configured, refetchInterval: 5000 })
  const { data: orderHistory = [] } = useQuery<DeltaOrder[]>({ queryKey: ['delta-order-history'], queryFn: () => deltaApi.getOrderHistory({ page_size: '30' }), enabled: configured, refetchInterval: 10000 })
  const qc = useQueryClient()
  const cancelMut = useMutation({
    mutationFn: ({ orderId, productId }: { orderId: number; productId: number }) => deltaApi.cancelOrder(orderId, productId),
    onSuccess: () => { toast.success('Order cancelled'); qc.invalidateQueries({ queryKey: ['delta-orders'] }) },
    onError: (err) => toast.error('Cancel failed', { description: (err as Error).message }),
  })

  const stopOrders = openOrders.filter(o => o.stop_price && parseFloat(o.stop_price) > 0)
  const limitOrders = openOrders.filter(o => !o.stop_price || parseFloat(o.stop_price) === 0)
  const filledOrders = orderHistory.filter(o => o.state === 'filled')

  const tabs: { key: BottomTab; label: string; count: number }[] = [
    { key: 'positions', label: 'Positions', count: positions.filter(p => p.size !== 0).length },
    { key: 'open_orders', label: 'Open Orders', count: limitOrders.length },
    { key: 'stop_orders', label: 'Stop Orders', count: stopOrders.length },
    { key: 'fills', label: 'Fills', count: filledOrders.length },
    { key: 'history', label: 'Order History', count: orderHistory.length },
  ]

  return (
    <div style={{ background: 'var(--surface-container-low)' }}>
      <div style={{ display: 'flex', borderBottom: '1px solid rgba(66,71,84,0.2)', overflowX: 'auto' }}>
        {tabs.map(t => (
          <button key={t.key} className={`trading-tab ${tab === t.key ? 'active' : ''}`} onClick={() => setTab(t.key)}>
            {t.label} {t.count > 0 && <span style={{ marginLeft: 4, padding: '1px 5px', fontSize: '0.5625rem', background: tab === t.key ? 'rgba(173,198,255,0.15)' : 'rgba(140,144,159,0.1)', borderRadius: 2 }}>{t.count}</span>}
          </button>
        ))}
      </div>
      <div style={{ overflowX: 'auto', maxHeight: 240 }}>
        {tab === 'positions' && (
          <table className="data-table">
            <thead><tr><th>Symbol</th><th>Side</th><th>Size</th><th>Entry Price</th><th>Liq Price</th><th>Margin</th><th>Unrealized PnL</th></tr></thead>
            <tbody>
              {positions.filter(p => p.size !== 0).length === 0 ? (
                <tr><td colSpan={7} style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--outline)' }}>No open positions</td></tr>
              ) : positions.filter(p => p.size !== 0).map(p => (
                <tr key={p.product_id}>
                  <td style={{ fontWeight: 600 }}>{p.product_symbol}</td>
                  <td style={{ color: p.size > 0 ? 'var(--tertiary)' : 'var(--error)', fontWeight: 600 }}>{p.size > 0 ? 'LONG' : 'SHORT'}</td>
                  <td>{Math.abs(p.size)}</td>
                  <td>{fmtNum(p.entry_price)}</td>
                  <td style={{ color: 'var(--error)' }}>{fmtNum(p.liquidation_price)}</td>
                  <td>{fmtNum(p.margin)}</td>
                  <td style={{ color: pnlColor(p.unrealized_pnl), fontWeight: 600 }}>{fmtMoney(p.unrealized_pnl)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {tab === 'open_orders' && (
          <table className="data-table">
            <thead><tr><th>Symbol</th><th>Side</th><th>Type</th><th>Size</th><th>Price</th><th>Unfilled</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {limitOrders.length === 0 ? (
                <tr><td colSpan={8} style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--outline)' }}>No open orders</td></tr>
              ) : limitOrders.map(o => (
                <tr key={o.id}>
                  <td style={{ fontWeight: 500 }}>{o.product_symbol}</td>
                  <td style={{ color: o.side === 'buy' ? 'var(--tertiary)' : 'var(--error)', fontWeight: 600 }}>{o.side.toUpperCase()}</td>
                  <td>{o.order_type.replace('_', ' ')}</td>
                  <td>{o.size}</td>
                  <td>{o.limit_price || '—'}</td>
                  <td>{o.unfilled_size}</td>
                  <td><span className="badge badge-info">{o.state}</span></td>
                  <td><button onClick={() => cancelMut.mutate({ orderId: o.id, productId: o.product_id })} className="btn-icon" style={{ color: 'var(--error)' }}><MaterialIcon name="close" size={14} /></button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {tab === 'stop_orders' && (
          <table className="data-table">
            <thead><tr><th>Symbol</th><th>Side</th><th>Size</th><th>Stop Price</th><th>Limit Price</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {stopOrders.length === 0 ? (
                <tr><td colSpan={7} style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--outline)' }}>No stop orders</td></tr>
              ) : stopOrders.map(o => (
                <tr key={o.id}>
                  <td style={{ fontWeight: 500 }}>{o.product_symbol}</td>
                  <td style={{ color: o.side === 'buy' ? 'var(--tertiary)' : 'var(--error)', fontWeight: 600 }}>{o.side.toUpperCase()}</td>
                  <td>{o.size}</td>
                  <td>{o.stop_price}</td>
                  <td>{o.limit_price || 'Market'}</td>
                  <td><span className="badge badge-warning">{o.state}</span></td>
                  <td><button onClick={() => cancelMut.mutate({ orderId: o.id, productId: o.product_id })} className="btn-icon" style={{ color: 'var(--error)' }}><MaterialIcon name="close" size={14} /></button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {tab === 'fills' && (
          <table className="data-table">
            <thead><tr><th>Time</th><th>Symbol</th><th>Side</th><th>Size</th><th>Price</th><th>Status</th></tr></thead>
            <tbody>
              {filledOrders.length === 0 ? (
                <tr><td colSpan={6} style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--outline)' }}>No fills</td></tr>
              ) : filledOrders.slice(0, 30).map(o => (
                <tr key={o.id}>
                  <td style={{ color: 'var(--on-surface-variant)' }}>{o.created_at ? new Date(parseInt(o.created_at) / 1000).toLocaleString() : '—'}</td>
                  <td style={{ fontWeight: 500 }}>{o.product_symbol}</td>
                  <td style={{ color: o.side === 'buy' ? 'var(--tertiary)' : 'var(--error)', fontWeight: 600 }}>{o.side.toUpperCase()}</td>
                  <td>{o.size}</td>
                  <td>{o.average_fill_price || o.limit_price || '—'}</td>
                  <td><span className="badge badge-active">filled</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {tab === 'history' && (
          <table className="data-table">
            <thead><tr><th>Time</th><th>Symbol</th><th>Side</th><th>Type</th><th>Size</th><th>Price</th><th>Status</th></tr></thead>
            <tbody>
              {orderHistory.length === 0 ? (
                <tr><td colSpan={7} style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--outline)' }}>No order history</td></tr>
              ) : orderHistory.map(o => (
                <tr key={o.id}>
                  <td style={{ color: 'var(--on-surface-variant)' }}>{o.created_at ? new Date(parseInt(o.created_at) / 1000).toLocaleString() : '—'}</td>
                  <td style={{ fontWeight: 500 }}>{o.product_symbol}</td>
                  <td style={{ color: o.side === 'buy' ? 'var(--tertiary)' : 'var(--error)', fontWeight: 600 }}>{o.side.toUpperCase()}</td>
                  <td>{o.order_type.replace('_', ' ')}</td>
                  <td>{o.size}</td>
                  <td>{o.limit_price || '—'}</td>
                  <td><span className={`badge ${o.state === 'filled' ? 'badge-active' : o.state === 'cancelled' ? 'badge-neutral' : 'badge-info'}`}>{o.state}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

// ═══════════════════════════════════════════
// ─── MAIN PAGE ───
// ═══════════════════════════════════════════

export function TradingPage() {
  const [configured, setConfigured] = useState(deltaApi.isConfigured())
  const [selectedProduct, setSelectedProduct] = useState<DeltaProduct | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [category, setCategory] = useState<ProductCategory | 'all'>('perpetual_futures')
  const [orderBook, setOrderBook] = useState<DeltaOrderBook | null>(null)
  const [recentTrades, setRecentTrades] = useState<DeltaTrade[]>([])

  // Fetch products (public endpoint, no auth needed)
  const { data: products = [] } = useQuery<DeltaProduct[]>({
    queryKey: ['delta-products-all'],
    queryFn: () => deltaApi.getProducts(),
    staleTime: 60_000,
  })

  // Fetch tickers (public endpoint, no auth needed)
  const { data: tickers = [] } = useQuery<DeltaTicker[]>({
    queryKey: ['delta-tickers'],
    queryFn: () => deltaApi.getTickers(),
    refetchInterval: 5000,
  })

  // Selected ticker
  const selectedTicker = useMemo(() => {
    if (!selectedProduct) return null
    return tickers.find(t => t.symbol === selectedProduct.symbol) ?? null
  }, [selectedProduct, tickers])

  // Order book WS
  useEffect(() => {
    if (!selectedProduct) return
    const ws = createDeltaWebSocket(
      [`l2_orderbook.${selectedProduct.symbol}`],
      (_ch, data) => setOrderBook(data as DeltaOrderBook),
    )
    return () => ws.close()
  }, [selectedProduct])

  // Order book REST fallback
  useEffect(() => {
    if (!selectedProduct) return
    deltaApi.getOrderBook(selectedProduct.symbol, 20).then(setOrderBook).catch(() => {})
  }, [selectedProduct])

  // Recent trades
  useEffect(() => {
    if (!selectedProduct) return
    deltaApi.getRecentTrades(selectedProduct.symbol).then(setRecentTrades).catch(() => {})
    const iv = setInterval(() => {
      deltaApi.getRecentTrades(selectedProduct.symbol).then(setRecentTrades).catch(() => {})
    }, 5000)
    return () => clearInterval(iv)
  }, [selectedProduct])

  // Order form wrapper (prompts to connect if not configured)
  const OrderFormWrapper = () => {
    if (!configured) {
      return (
        <div style={{ background: 'var(--surface-container-low)', padding: '1rem', display: 'flex', flexDirection: 'column', gap: '1rem', height: '100%', justifyContent: 'center', alignItems: 'center', textAlign: 'center' }}>
          <MaterialIcon name="lock_outline" size={32} style={{ color: 'var(--outline)' } as any} />
          <div style={{ fontSize: '0.875rem', color: 'var(--on-surface-variant)' }}>Connect Delta Exchange account to place orders.</div>
          <button onClick={() => setSelectedProduct(null)} className="btn-primary" style={{ padding: '0.5rem 1rem', fontSize: '0.8125rem' }}>Go Connect</button>
        </div>
      )
    }
    return <OrderForm symbol={selectedProduct!.symbol} ticker={selectedTicker} product={selectedProduct} />
  }

  // Bottom panel wrapper
  const BottomPanelWrapper = () => {
    if (!configured) return <div style={{ background: 'var(--surface-container-low)', padding: '2rem', textAlign: 'center', color: 'var(--outline)' }}>Connect account to view portfolio.</div>
    return <BottomPanel />
  }

  // Trading view (product selected)
  if (selectedProduct) {
    return (
      <div style={{ margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '0' }}>
        {/* Product info bar */}
        <div style={{
          display: 'flex', alignItems: 'center', gap: '0.5rem',
          padding: '0.5rem 0.75rem', background: 'var(--surface-container-lowest)',
          borderBottom: '1px solid rgba(66,71,84,0.15)', flexWrap: 'wrap',
        }}>
          <button onClick={() => { setSelectedProduct(null); setOrderBook(null) }}
            className="btn-icon" style={{ marginRight: '0.25rem' }}>
            <MaterialIcon name="arrow_back" size={18} />
          </button>
          <span style={{ fontFamily: 'var(--font-display)', fontSize: '1rem', fontWeight: 700, color: 'var(--primary)' }}>
            {selectedProduct.symbol}
          </span>
          {selectedTicker && (
            <>
              <span style={{ fontFamily: 'var(--font-display)', fontSize: '1rem', fontWeight: 700, color: 'var(--on-surface)' }}>
                ${fmtNum(tickerPrice(selectedTicker))}
              </span>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', fontWeight: 500, color: changeColor(tickerChange(selectedTicker)) }}>
                {tickerChange(selectedTicker) >= 0 ? '+' : ''}{fmtNum(tickerChange(selectedTicker))}%
              </span>
              <div className="trading-stat"><span className="trading-stat-label">Mark</span><span className="trading-stat-value">{fmtNum(selectedTicker.mark_price)}</span></div>
              <div className="trading-stat"><span className="trading-stat-label">24h High</span><span className="trading-stat-value" style={{ color: 'var(--tertiary)' }}>{fmtNum(selectedTicker.high)}</span></div>
              <div className="trading-stat"><span className="trading-stat-label">24h Low</span><span className="trading-stat-value" style={{ color: 'var(--error)' }}>{fmtNum(selectedTicker.low)}</span></div>
              <div className="trading-stat"><span className="trading-stat-label">24h Vol</span><span className="trading-stat-value">{fmtCompact(selectedTicker.turnover_usd || selectedTicker.turnover)}</span></div>
              {selectedTicker.funding_rate && (
                <div className="trading-stat"><span className="trading-stat-label">Funding</span><span className="trading-stat-value" style={{ color: 'var(--secondary)' }}>{fmtNum(parseFloat(selectedTicker.funding_rate) * 100, 4)}%</span></div>
              )}
            </>
          )}
          <div style={{ marginLeft: 'auto' }}>
            <span className="status-dot live" /> <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.625rem', color: 'var(--tertiary)', letterSpacing: '0.08em' }}>LIVE</span>
          </div>
        </div>

        {/* Main trading grid */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 260px 280px', gap: 0, height: 'calc(100vh - 320px)', minHeight: 400 }}>
          {/* Chart */}
          <TradingChart symbol={selectedProduct.symbol} productId={selectedProduct.id} />

          {/* Order Book + Recent Trades */}
          <div style={{ display: 'flex', flexDirection: 'column', borderLeft: '1px solid rgba(66,71,84,0.15)', borderRight: '1px solid rgba(66,71,84,0.15)' }}>
            <div style={{ flex: 1, overflowY: 'auto' }}>
              <div style={{ padding: '0.375rem 0.75rem', borderBottom: '1px solid rgba(66,71,84,0.15)', display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.625rem', color: 'var(--on-surface)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em' }}>Order Book</span>
              </div>
              <OrderBook book={orderBook} ticker={selectedTicker} />
            </div>
            <div style={{ borderTop: '1px solid rgba(66,71,84,0.15)' }}>
              <div style={{ padding: '0.375rem 0.75rem', borderBottom: '1px solid rgba(66,71,84,0.15)' }}>
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.625rem', color: 'var(--on-surface)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em' }}>Recent Trades</span>
              </div>
              <RecentTrades trades={recentTrades} />
            </div>
          </div>

          {/* Order Form */}
          <OrderFormWrapper />
        </div>

        {/* Bottom panel */}
        <BottomPanelWrapper />
      </div>
    )
  }

  // Markets overview
  return (
    <div style={{ maxWidth: 1400, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      <PageHeader title="Trading" subtitle="DELTA EXCHANGE // LIVE MARKETS">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <span className={`status-dot ${configured ? 'live' : ''}`} style={{ background: configured ? undefined : 'var(--outline)' }} />
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.6875rem', color: configured ? 'var(--tertiary)' : 'var(--outline)', letterSpacing: '0.08em' }}>
            {configured ? 'CONNECTED' : 'DISCONNECTED'}
          </span>
          {configured && (
            <button onClick={() => { deltaApi.clearConfig(); setConfigured(false) }} className="btn-ghost" style={{ fontSize: '0.6875rem', padding: '0.25rem 0.5rem', color: 'var(--error)' }}>
              Disconnect
            </button>
          )}
        </div>
      </PageHeader>

      {!configured && (
        <div style={{ marginBottom: '0.5rem' }}>
          <ConnectDelta onConnected={() => setConfigured(true)} />
        </div>
      )}

      {/* Category tabs + Search */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.75rem' }}>
        <div style={{ display: 'flex', gap: '0.375rem', flexWrap: 'wrap' }}>
          {CATEGORIES.map(c => (
            <button key={c.key} className={`category-pill ${category === c.key ? 'active' : ''}`}
              onClick={() => setCategory(c.key)}>
              {c.label}
            </button>
          ))}
        </div>
        <div style={{ position: 'relative' }}>
          <MaterialIcon name="search" size={14} style={{ position: 'absolute', left: 8, top: '50%', transform: 'translateY(-50%)', color: 'var(--outline)' } as React.CSSProperties} />
          <input type="text" value={searchQuery} onChange={e => setSearchQuery(e.target.value)} placeholder="Search markets..." className="search-input" />
        </div>
      </div>

      {/* Products table */}
      <MarketsTable products={products} tickers={tickers} onSelect={setSelectedProduct} searchQuery={searchQuery} category={category} />

      {/* Bottom panel */}
      <div style={{ marginTop: '1.5rem' }}>
        <BottomPanel />
      </div>
    </div>
  )
}

export default TradingPage
