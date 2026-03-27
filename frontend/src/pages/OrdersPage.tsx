import { useEffect, useMemo, useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
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
  type OrderBookLevel,
} from '@/services/deltaExchange'

function fmtNum(n: number | string, decimals = 2) {
  const v = typeof n === 'string' ? parseFloat(n) : n
  if (isNaN(v)) return '—'
  return v.toLocaleString(undefined, { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
}

function fmtMoney(n: number | string) {
  const v = typeof n === 'string' ? parseFloat(n) : n
  if (isNaN(v)) return '$0.00'
  return v.toLocaleString(undefined, { style: 'currency', currency: 'USD', maximumFractionDigits: 2 })
}

function pnlColor(n: number | string) {
  const v = typeof n === 'string' ? parseFloat(n) : n
  return v >= 0 ? 'var(--tertiary)' : 'var(--error)'
}

/* ─── Order Book Component ─── */
function OrderBook({ book, ticker }: { book: DeltaOrderBook | null; ticker: DeltaTicker | null }) {
  const asks = useMemo(() => (book?.sell ?? []).slice(0, 12).reverse(), [book])
  const bids = useMemo(() => (book?.buy ?? []).slice(0, 12), [book])
  const maxSize = useMemo(() => {
    const all = [...(book?.sell ?? []), ...(book?.buy ?? [])]
    return Math.max(...all.map(l => l.size), 1)
  }, [book])

  return (
    <div style={{ background: 'var(--surface-container-lowest)', fontFamily: 'var(--font-mono)', fontSize: '0.6875rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0.75rem', borderBottom: '1px solid rgba(66,71,84,0.15)' }}>
        <span style={{ color: 'var(--outline)', textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 500 }}>Price</span>
        <span style={{ color: 'var(--outline)', textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 500 }}>Size</span>
      </div>

      {/* Asks (sells) - red */}
      <div style={{ maxHeight: 200, overflowY: 'auto', display: 'flex', flexDirection: 'column' }}>
        {asks.length === 0 ? (
          <div style={{ padding: '1rem', textAlign: 'center', color: 'var(--outline)' }}>No asks</div>
        ) : asks.map((level, i) => (
          <BookRow key={`ask-${i}`} level={level} maxSize={maxSize} side="sell" />
        ))}
      </div>

      {/* Spread / Last price */}
      <div style={{
        padding: '0.5rem 0.75rem', textAlign: 'center',
        background: 'var(--surface-container)',
        borderTop: '1px solid rgba(66,71,84,0.15)',
        borderBottom: '1px solid rgba(66,71,84,0.15)',
      }}>
        <span style={{ fontSize: '1rem', fontWeight: 700, fontFamily: 'var(--font-display)', color: 'var(--on-surface)' }}>
          {ticker ? fmtNum(ticker.last_price) : '—'}
        </span>
        {ticker && (
          <span style={{
            marginLeft: '0.5rem', fontSize: '0.6875rem',
            color: parseFloat(ticker.change_24h || '0') >= 0 ? 'var(--tertiary)' : 'var(--error)',
          }}>
            {parseFloat(ticker.change_24h || '0') >= 0 ? '+' : ''}{fmtNum(ticker.change_24h || '0')}%
          </span>
        )}
      </div>

      {/* Bids (buys) - green */}
      <div style={{ maxHeight: 200, overflowY: 'auto' }}>
        {bids.length === 0 ? (
          <div style={{ padding: '1rem', textAlign: 'center', color: 'var(--outline)' }}>No bids</div>
        ) : bids.map((level, i) => (
          <BookRow key={`bid-${i}`} level={level} maxSize={maxSize} side="buy" />
        ))}
      </div>
    </div>
  )
}

function BookRow({ level, maxSize, side }: { level: OrderBookLevel; maxSize: number; side: 'buy' | 'sell' }) {
  const pct = (level.size / maxSize) * 100
  const color = side === 'buy' ? 'rgba(0, 228, 121, 0.12)' : 'rgba(255, 180, 171, 0.12)'
  const textColor = side === 'buy' ? 'var(--tertiary)' : 'var(--error)'

  return (
    <div style={{
      display: 'flex', justifyContent: 'space-between', padding: '2px 0.75rem',
      position: 'relative',
    }}>
      <div style={{
        position: 'absolute', top: 0, bottom: 0,
        right: 0, width: `${pct}%`,
        background: color,
        transition: 'width 150ms ease-out',
      }} />
      <span style={{ color: textColor, position: 'relative', zIndex: 1 }}>{fmtNum(level.price)}</span>
      <span style={{ color: 'var(--on-surface)', position: 'relative', zIndex: 1 }}>{level.size.toLocaleString()}</span>
    </div>
  )
}

/* ─── Not Configured State ─── */
function NotConfigured() {
  return (
    <div style={{ textAlign: 'center', padding: '4rem 2rem' }}>
      <MaterialIcon name="link_off" size={48} style={{ color: 'var(--outline)', marginBottom: '1rem' } as React.CSSProperties} />
      <h2 style={{ fontFamily: 'var(--font-display)', fontSize: '1.25rem', fontWeight: 600, color: 'var(--on-surface)', marginBottom: '0.5rem' }}>
        Delta Exchange Not Connected
      </h2>
      <p style={{ color: 'var(--on-surface-variant)', fontSize: '0.875rem', maxWidth: 400, margin: '0 auto 1.5rem' }}>
        Configure your Delta Exchange API keys in Settings to enable live trading, order book, and position management.
      </p>
      <a
        href="#/settings"
        onClick={(e) => { e.preventDefault(); window.location.hash = ''; window.location.pathname = '/settings'; }}
        className="btn-primary"
        style={{ textDecoration: 'none' }}
      >
        <MaterialIcon name="settings" size={16} /> Go to Settings
      </a>
    </div>
  )
}

/* ─── Main Orders Page ─── */
export function OrdersPage() {
  const qc = useQueryClient()
  const configured = deltaApi.isConfigured()

  // Selected product
  const [selectedSymbol, setSelectedSymbol] = useState('BTCUSD')
  const [orderBook, setOrderBook] = useState<DeltaOrderBook | null>(null)

  // Order form
  const [side, setSide] = useState<'buy' | 'sell'>('buy')
  const [orderType, setOrderType] = useState<'market_order' | 'limit_order'>('limit_order')
  const [size, setSize] = useState('1')
  const [limitPrice, setLimitPrice] = useState('')
  const [stopLoss, setStopLoss] = useState('')
  const [takeProfit, setTakeProfit] = useState('')

  // Products
  const { data: products = [] } = useQuery<DeltaProduct[]>({
    queryKey: ['delta-products'],
    queryFn: () => deltaApi.getProducts({ contract_types: 'perpetual_futures', states: 'live' }),
    staleTime: 60_000,
  })

  const selectedProduct = useMemo(
    () => products.find(p => p.symbol === selectedSymbol),
    [products, selectedSymbol],
  )

  // Ticker
  const { data: ticker } = useQuery<DeltaTicker>({
    queryKey: ['delta-ticker', selectedSymbol],
    queryFn: () => deltaApi.getTicker(selectedSymbol),
    refetchInterval: 3000,
    enabled: !!selectedSymbol,
  })

  // Balances
  const { data: balances = [] } = useQuery<DeltaBalance[]>({
    queryKey: ['delta-balances'],
    queryFn: () => deltaApi.getBalances(),
    enabled: configured,
    refetchInterval: 10_000,
  })

  // Positions
  const { data: positions = [] } = useQuery<DeltaPosition[]>({
    queryKey: ['delta-positions'],
    queryFn: () => deltaApi.getPositions(),
    enabled: configured,
    refetchInterval: 5_000,
  })

  // Open orders
  const { data: openOrders = [] } = useQuery<DeltaOrder[]>({
    queryKey: ['delta-orders'],
    queryFn: () => deltaApi.getOpenOrders(),
    enabled: configured,
    refetchInterval: 5_000,
  })

  // Order history
  const { data: orderHistory = [] } = useQuery<DeltaOrder[]>({
    queryKey: ['delta-order-history'],
    queryFn: () => deltaApi.getOrderHistory({ page_size: '20' }),
    enabled: configured,
    refetchInterval: 10_000,
  })

  // WebSocket for order book
  useEffect(() => {
    if (!selectedProduct) return
    const ws = createDeltaWebSocket(
      [`l2_orderbook.${selectedSymbol}`],
      (_channel, data) => {
        setOrderBook(data as DeltaOrderBook)
      },
    )
    return () => ws.close()
  }, [selectedSymbol, selectedProduct])

  // Fetch initial order book via REST
  useEffect(() => {
    if (!selectedProduct) return
    deltaApi.getOrderBook(selectedProduct.id, 20).then(setOrderBook).catch(() => {})
  }, [selectedProduct])

  // Place order mutation
  const placeOrderMut = useMutation({
    mutationFn: async () => {
      const order: Parameters<typeof deltaApi.placeOrder>[0] = {
        product_symbol: selectedSymbol,
        side,
        size: parseInt(size) || 1,
        order_type: orderType,
      }
      if (orderType === 'limit_order' && limitPrice) {
        order.limit_price = limitPrice
      }
      if (stopLoss) {
        order.bracket_stop_loss_price = stopLoss
      }
      if (takeProfit) {
        order.bracket_take_profit_price = takeProfit
      }
      return deltaApi.placeOrder(order)
    },
    onSuccess: (result) => {
      toast.success(`Order placed: ${result.side.toUpperCase()} ${result.size} ${result.product_symbol}`)
      qc.invalidateQueries({ queryKey: ['delta-orders'] })
      qc.invalidateQueries({ queryKey: ['delta-balances'] })
      setSize('1')
      setLimitPrice('')
      setStopLoss('')
      setTakeProfit('')
    },
    onError: (err) => {
      toast.error('Order failed', { description: (err as Error).message })
    },
  })

  // Cancel order mutation
  const cancelMut = useMutation({
    mutationFn: ({ orderId, productId }: { orderId: number; productId: number }) =>
      deltaApi.cancelOrder(orderId, productId),
    onSuccess: () => {
      toast.success('Order cancelled')
      qc.invalidateQueries({ queryKey: ['delta-orders'] })
    },
    onError: (err) => {
      toast.error('Cancel failed', { description: (err as Error).message })
    },
  })

  // Auto-fill limit price from ticker
  const fillPrice = useCallback(() => {
    if (ticker) setLimitPrice(ticker.last_price)
  }, [ticker])

  const usdtBalance = balances.find(b => b.asset_symbol === 'USDT')

  if (!configured) {
    return (
      <div style={{ maxWidth: 1400, margin: '0 auto' }}>
        <PageHeader title="Orders & Positions" subtitle="DELTA EXCHANGE // LIVE EXECUTION" />
        <NotConfigured />
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 1400, margin: '0 auto' }}>
      <PageHeader title="Orders & Positions" subtitle="DELTA EXCHANGE // LIVE EXECUTION">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          {usdtBalance && (
            <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', color: 'var(--on-surface-variant)' }}>
              Balance: <span style={{ color: 'var(--tertiary)', fontWeight: 600 }}>{fmtMoney(usdtBalance.available_balance)}</span>
            </span>
          )}
          <span className="status-dot live" />
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.6875rem', color: 'var(--tertiary)', letterSpacing: '0.08em' }}>
            DELTA_LINKED
          </span>
        </div>
      </PageHeader>

      {/* Symbol selector */}
      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '1.5rem' }}>
        {['BTCUSD', 'ETHUSD', 'SOLUSD', 'XRPUSD', 'AVAXUSD'].map(sym => (
          <button
            key={sym}
            onClick={() => setSelectedSymbol(sym)}
            style={{
              padding: '0.375rem 0.75rem',
              fontFamily: 'var(--font-mono)', fontSize: '0.75rem', fontWeight: 500,
              border: `1px solid ${selectedSymbol === sym ? 'var(--primary)' : 'var(--outline-variant)'}`,
              background: selectedSymbol === sym ? 'rgba(173, 198, 255, 0.1)' : 'var(--surface-container-low)',
              color: selectedSymbol === sym ? 'var(--primary)' : 'var(--on-surface-variant)',
              cursor: 'pointer', transition: 'all 150ms ease-out',
            }}
          >
            {sym}
          </button>
        ))}
        <select
          value={selectedSymbol}
          onChange={e => setSelectedSymbol(e.target.value)}
          style={{
            padding: '0.375rem 0.75rem', fontFamily: 'var(--font-mono)', fontSize: '0.75rem',
            background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)',
            color: 'var(--on-surface)', cursor: 'pointer', outline: 'none',
          }}
        >
          {products.slice(0, 50).map(p => (
            <option key={p.symbol} value={p.symbol}>{p.symbol}</option>
          ))}
        </select>
      </div>

      {/* Main 3-column layout: Order Book | Order Form | Positions */}
      <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr 1fr', gap: '1rem', marginBottom: '1.5rem' }}>

        {/* Order Book */}
        <div>
          <div className="qe-card-header" style={{ marginBottom: '0.5rem' }}>
            <div className="qe-card-title">Order Book</div>
            <div className="qe-card-meta">{selectedSymbol}</div>
          </div>
          <OrderBook book={orderBook} ticker={ticker ?? null} />
        </div>

        {/* Order Entry */}
        <div style={{ background: 'var(--surface-container-low)', padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
            <span className="qe-card-title">Place Order</span>
            {ticker && (
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.6875rem', color: 'var(--outline)' }}>
                Mark: {fmtNum(ticker.mark_price)}
              </span>
            )}
          </div>

          {/* Side toggle */}
          <div style={{ display: 'flex', gap: 0, marginBottom: '1rem', border: '1px solid var(--outline-variant)' }}>
            <button
              onClick={() => setSide('buy')}
              style={{
                flex: 1, padding: '0.625rem', fontWeight: 600, fontSize: '0.75rem',
                fontFamily: 'var(--font-mono)', border: 'none', cursor: 'pointer',
                background: side === 'buy' ? 'rgba(0, 228, 121, 0.15)' : 'transparent',
                color: side === 'buy' ? 'var(--tertiary)' : 'var(--outline)',
                transition: 'all 150ms',
              }}
            >
              LONG / BUY
            </button>
            <button
              onClick={() => setSide('sell')}
              style={{
                flex: 1, padding: '0.625rem', fontWeight: 600, fontSize: '0.75rem',
                fontFamily: 'var(--font-mono)', border: 'none', cursor: 'pointer',
                background: side === 'sell' ? 'rgba(255, 180, 171, 0.15)' : 'transparent',
                color: side === 'sell' ? 'var(--error)' : 'var(--outline)',
                transition: 'all 150ms',
              }}
            >
              SHORT / SELL
            </button>
          </div>

          {/* Order type */}
          <div style={{ marginBottom: '1rem' }}>
            <label className="input-label">Order Type</label>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              {(['limit_order', 'market_order'] as const).map(t => (
                <button
                  key={t}
                  onClick={() => setOrderType(t)}
                  style={{
                    flex: 1, padding: '0.5rem', fontSize: '0.6875rem',
                    fontFamily: 'var(--font-mono)', border: 'none', cursor: 'pointer',
                    background: orderType === t ? 'rgba(173, 198, 255, 0.1)' : 'var(--surface)',
                    color: orderType === t ? 'var(--primary)' : 'var(--outline)',
                    transition: 'all 150ms',
                  }}
                >
                  {t === 'limit_order' ? 'LIMIT' : 'MARKET'}
                </button>
              ))}
            </div>
          </div>

          {/* Size */}
          <div style={{ marginBottom: '1rem' }}>
            <label className="input-label">Size (Contracts)</label>
            <input
              type="number" min="1" value={size}
              onChange={e => setSize(e.target.value)}
              className="input-terminal"
              style={{ width: '100%' }}
            />
          </div>

          {/* Limit Price */}
          {orderType === 'limit_order' && (
            <div style={{ marginBottom: '1rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <label className="input-label" style={{ marginBottom: 0 }}>Limit Price</label>
                <button onClick={fillPrice} style={{
                  background: 'none', border: 'none', cursor: 'pointer',
                  fontFamily: 'var(--font-mono)', fontSize: '0.6875rem', color: 'var(--primary)',
                }}>
                  Use Last
                </button>
              </div>
              <input
                type="text" value={limitPrice}
                onChange={e => setLimitPrice(e.target.value)}
                placeholder="0.00"
                className="input-terminal"
                style={{ width: '100%', marginTop: '0.5rem' }}
              />
            </div>
          )}

          {/* Bracket: Stop Loss */}
          <div style={{ marginBottom: '1rem' }}>
            <label className="input-label">Stop Loss (Optional)</label>
            <input
              type="text" value={stopLoss}
              onChange={e => setStopLoss(e.target.value)}
              placeholder="Trigger price"
              className="input-terminal"
              style={{ width: '100%' }}
            />
          </div>

          {/* Bracket: Take Profit */}
          <div style={{ marginBottom: '1.25rem' }}>
            <label className="input-label">Take Profit (Optional)</label>
            <input
              type="text" value={takeProfit}
              onChange={e => setTakeProfit(e.target.value)}
              placeholder="Trigger price"
              className="input-terminal"
              style={{ width: '100%' }}
            />
          </div>

          {/* Submit */}
          <button
            onClick={() => placeOrderMut.mutate()}
            disabled={placeOrderMut.isPending || !size}
            style={{
              width: '100%', padding: '0.75rem',
              background: side === 'buy'
                ? 'linear-gradient(135deg, var(--tertiary), var(--tertiary-container))'
                : 'linear-gradient(135deg, var(--error), var(--error-container))',
              color: side === 'buy' ? 'var(--on-tertiary)' : 'var(--on-error)',
              fontFamily: 'var(--font-mono)', fontSize: '0.75rem', fontWeight: 700,
              letterSpacing: '0.06em', textTransform: 'uppercase',
              border: 'none', cursor: 'pointer',
              opacity: placeOrderMut.isPending ? 0.5 : 1,
              transition: 'all 150ms',
            }}
          >
            {placeOrderMut.isPending ? 'PLACING...' : `${side.toUpperCase()} ${selectedSymbol}`}
          </button>

          {placeOrderMut.isError && (
            <p style={{ color: 'var(--error)', fontSize: '0.75rem', marginTop: '0.5rem' }}>
              {(placeOrderMut.error as Error).message}
            </p>
          )}
        </div>

        {/* Positions */}
        <div>
          <div className="qe-card-header" style={{ marginBottom: '0.5rem' }}>
            <div className="qe-card-title">Open Positions</div>
            <div className="qe-card-meta">{positions.length} ACTIVE</div>
          </div>
          {positions.length === 0 ? (
            <div style={{
              background: 'var(--surface-container-low)', padding: '2rem',
              textAlign: 'center', color: 'var(--outline)', fontSize: '0.875rem',
            }}>
              No open positions
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              {positions.map(pos => (
                <div key={pos.product_id} style={{ background: 'var(--surface-container-low)', padding: '1rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', fontWeight: 600, color: 'var(--primary)' }}>
                      {pos.product_symbol}
                    </span>
                    <span style={{
                      fontFamily: 'var(--font-mono)', fontSize: '0.6875rem', fontWeight: 500,
                      color: pos.size > 0 ? 'var(--tertiary)' : 'var(--error)',
                    }}>
                      {pos.size > 0 ? 'LONG' : 'SHORT'} {Math.abs(pos.size)}
                    </span>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem', fontFamily: 'var(--font-mono)', fontSize: '0.6875rem' }}>
                    <div><span style={{ color: 'var(--outline)' }}>Entry</span> <span style={{ color: 'var(--on-surface)' }}>{fmtNum(pos.entry_price)}</span></div>
                    <div><span style={{ color: 'var(--outline)' }}>Liq</span> <span style={{ color: 'var(--error)' }}>{fmtNum(pos.liquidation_price)}</span></div>
                    <div><span style={{ color: 'var(--outline)' }}>Margin</span> <span style={{ color: 'var(--on-surface)' }}>{fmtNum(pos.margin)}</span></div>
                    <div><span style={{ color: 'var(--outline)' }}>PnL</span> <span style={{ color: pnlColor(pos.unrealized_pnl) }}>{fmtMoney(pos.unrealized_pnl)}</span></div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Open Orders Table */}
      <div style={{ marginBottom: '1.5rem' }}>
        <div className="qe-card-header">
          <div className="qe-card-title">Open Orders</div>
          <div className="qe-card-meta">{openOrders.length} PENDING</div>
        </div>
        <div style={{ background: 'var(--surface-container-low)', overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Symbol</th>
                  <th>Side</th>
                  <th>Type</th>
                  <th>Size</th>
                  <th>Price</th>
                  <th>Unfilled</th>
                  <th>SL</th>
                  <th>TP</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {openOrders.length === 0 ? (
                  <tr><td colSpan={10} style={{ textAlign: 'center', padding: '2rem', color: 'var(--outline)' }}>No open orders</td></tr>
                ) : openOrders.map(o => (
                  <tr key={o.id}>
                    <td style={{ fontFamily: 'var(--font-mono)', fontWeight: 500 }}>{o.product_symbol}</td>
                    <td style={{ color: o.side === 'buy' ? 'var(--tertiary)' : 'var(--error)', fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{o.side.toUpperCase()}</td>
                    <td style={{ fontFamily: 'var(--font-mono)' }}>{o.order_type.replace('_', ' ')}</td>
                    <td style={{ fontFamily: 'var(--font-mono)' }}>{o.size}</td>
                    <td style={{ fontFamily: 'var(--font-mono)' }}>{o.limit_price || '—'}</td>
                    <td style={{ fontFamily: 'var(--font-mono)' }}>{o.unfilled_size}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', color: o.bracket_stop_loss_price ? 'var(--error)' : 'var(--outline)' }}>
                      {o.bracket_stop_loss_price || '—'}
                    </td>
                    <td style={{ fontFamily: 'var(--font-mono)', color: o.bracket_take_profit_price ? 'var(--tertiary)' : 'var(--outline)' }}>
                      {o.bracket_take_profit_price || '—'}
                    </td>
                    <td><span className="badge badge-info">{o.state}</span></td>
                    <td>
                      <button
                        onClick={() => cancelMut.mutate({ orderId: o.id, productId: o.product_id })}
                        disabled={cancelMut.isPending}
                        className="btn-icon"
                        title="Cancel order"
                        style={{ color: 'var(--error)' }}
                      >
                        <MaterialIcon name="close" size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Order History */}
      <div>
        <div className="qe-card-header">
          <div className="qe-card-title">Recent Orders</div>
          <div className="qe-card-meta">HISTORY</div>
        </div>
        <div style={{ background: 'var(--surface-container-low)', overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Symbol</th>
                  <th>Side</th>
                  <th>Type</th>
                  <th>Size</th>
                  <th>Price</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {orderHistory.length === 0 ? (
                  <tr><td colSpan={7} style={{ textAlign: 'center', padding: '2rem', color: 'var(--outline)' }}>No order history</td></tr>
                ) : orderHistory.map(o => (
                  <tr key={o.id}>
                    <td style={{ fontFamily: 'var(--font-mono)', color: 'var(--on-surface-variant)' }}>
                      {o.created_at ? new Date(parseInt(o.created_at) / 1000).toLocaleString() : '—'}
                    </td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontWeight: 500 }}>{o.product_symbol}</td>
                    <td style={{ color: o.side === 'buy' ? 'var(--tertiary)' : 'var(--error)', fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{o.side.toUpperCase()}</td>
                    <td style={{ fontFamily: 'var(--font-mono)' }}>{o.order_type.replace('_', ' ')}</td>
                    <td style={{ fontFamily: 'var(--font-mono)' }}>{o.size}</td>
                    <td style={{ fontFamily: 'var(--font-mono)' }}>{o.limit_price || '—'}</td>
                    <td>
                      <span className={`badge ${o.state === 'filled' ? 'badge-active' : o.state === 'cancelled' ? 'badge-neutral' : 'badge-info'}`}>
                        {o.state}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  )
}

export default OrdersPage
