// ================================================================
// APP — Root Component with Auth, Navigation, Dashboard
// ================================================================

const { useState, useEffect, useCallback, useRef } = React;

// ─── WebSocket Hook ────────────────────────────────────────

const useWebSocket = () => {
    const [prices, setPrices] = useState({});
    const [connected, setConnected] = useState(false);
    const clientRef = useRef(null);

    useEffect(() => {
        try {
            const socket = new SockJS('http://localhost:8080/ws');
            const stompClient = typeof StompJs !== 'undefined' ? new StompJs.Client({
                webSocketFactory: () => socket,
                reconnectDelay: 5000,
                onConnect: () => {
                    setConnected(true);
                    stompClient.subscribe('/topic/prices', (msg) => {
                        const tick = JSON.parse(msg.body);
                        setPrices(prev => ({
                            ...prev,
                            [tick.symbol]: tick
                        }));
                    });
                },
                onDisconnect: () => setConnected(false),
                onStompError: () => setConnected(false),
            }) : null;

            if (stompClient) {
                stompClient.activate();
                clientRef.current = stompClient;
            }
        } catch (e) {
            console.warn('WebSocket not available:', e);
        }

        return () => {
            if (clientRef.current) {
                clientRef.current.deactivate();
            }
        };
    }, []);

    return { prices, connected };
};

// ─── Live Ticker Strip ──────────────────────────────────────

const LiveTickerStrip = ({ prices, connected }) => {
    const symbols = Object.keys(prices);
    if (symbols.length === 0) {
        return React.createElement('div', { className: 'ticker-strip' },
            React.createElement('div', { className: 'ticker-status' },
                React.createElement('span', {
                    className: 'ticker-dot ' + (connected ? 'connected' : '')
                }),
                connected ? 'LIVE' : 'CONNECTING...'
            )
        );
    }

    return React.createElement('div', { className: 'ticker-strip' },
        React.createElement('div', { className: 'ticker-status' },
            React.createElement('span', { className: 'ticker-dot connected' }),
            'LIVE'
        ),
        React.createElement('div', { className: 'ticker-items' },
            symbols.map(sym => {
                const t = prices[sym];
                const isUp = parseFloat(t.change) >= 0;
                return React.createElement('div', { key: sym, className: 'ticker-item' },
                    React.createElement('span', { className: 'ticker-symbol' }, sym),
                    React.createElement('span', { className: 'ticker-price' }, '$' + parseFloat(t.price).toFixed(2)),
                    React.createElement('span', {
                        className: 'ticker-change ' + (isUp ? 'up' : 'down')
                    }, (isUp ? '▲' : '▼') + ' ' + parseFloat(t.changePercent).toFixed(2) + '%')
                );
            })
        )
    );
};

const AuthScreen = ({ onLogin }) => {
    const [isLogin, setIsLogin] = useState(true);
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            if (isLogin) {
                await ApiClient.login(email, password);
            } else {
                await ApiClient.register(name, email, password);
            }
            onLogin(ApiClient.getUser());
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return React.createElement('div', { className: 'auth-backdrop' },
        React.createElement('div', { className: 'auth-container' },
            React.createElement('div', { className: 'auth-logo' },
                React.createElement('div', { className: 'logo pulse' }, 'Q'),
                React.createElement('div', { className: 'auth-brand' },
                    React.createElement('h1', null, 'QuantEdge'),
                    React.createElement('p', null, 'AI-Driven Trading Platform')
                )
            ),
            React.createElement('div', { className: 'auth-tabs' },
                React.createElement('button', {
                    className: 'auth-tab ' + (isLogin ? 'active' : ''),
                    onClick: () => { setIsLogin(true); setError(''); }
                }, 'Sign In'),
                React.createElement('button', {
                    className: 'auth-tab ' + (!isLogin ? 'active' : ''),
                    onClick: () => { setIsLogin(false); setError(''); }
                }, 'Register')
            ),
            React.createElement('form', { className: 'auth-form', onSubmit: handleSubmit },
                !isLogin && React.createElement('div', { className: 'form-group' },
                    React.createElement('label', null, 'Full Name'),
                    React.createElement('input', {
                        type: 'text', value: name, required: !isLogin,
                        placeholder: 'John Doe',
                        onChange: e => setName(e.target.value)
                    })
                ),
                React.createElement('div', { className: 'form-group' },
                    React.createElement('label', null, 'Email'),
                    React.createElement('input', {
                        type: 'email', value: email, required: true,
                        placeholder: 'trader@quantedge.com',
                        onChange: e => setEmail(e.target.value)
                    })
                ),
                React.createElement('div', { className: 'form-group' },
                    React.createElement('label', null, 'Password'),
                    React.createElement('input', {
                        type: 'password', value: password, required: true,
                        placeholder: '••••••••',
                        onChange: e => setPassword(e.target.value)
                    })
                ),
                error && React.createElement('div', { className: 'auth-error' }, '⚠ ' + error),
                React.createElement('button', {
                    type: 'submit', className: 'auth-submit', disabled: loading
                }, loading ? 'Processing...' : (isLogin ? 'Sign In' : 'Create Account'))
            )
        )
    );
};

// ─── Dashboard ───────────────────────────────────────────────

const DashboardView = ({ strategies, symbols }) => {
    const [health, setHealth] = useState(null);
    const [summaries, setSummaries] = useState({});
    const [agents, setAgents] = useState([]);
    const [recentSignals, setRecentSignals] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const load = async () => {
            setLoading(true);
            try {
                const [healthData, agentData] = await Promise.all([
                    ApiClient.getHealth(),
                    ApiClient.getAgents().catch(() => []),
                ]);
                setHealth(healthData);
                setAgents(agentData);

                // Fetch summaries for each symbol
                for (const sym of symbols) {
                    const s = await ApiClient.getSummary(sym).catch(() => ({}));
                    setSummaries(prev => ({ ...prev, [sym]: s }));
                }
            } catch (err) {
                console.error('Dashboard load error:', err);
            }
            setLoading(false);
        };
        load();
    }, [symbols]);

    const totalRecords = Object.values(summaries).reduce((a, s) => a + (s.totalRecords || 0), 0);
    const portfolioValue = strategies.reduce((a, s) => a + parseFloat(s.currentCash || 0), 0);
    const activeAgents = agents.filter(a => a.active || a.status === 'RUNNING').length;

    if (loading) {
        return React.createElement('div', { className: 'animate-in' },
            React.createElement('div', { className: 'dashboard-welcome' },
                React.createElement('h2', null, 'Platform Overview'),
                React.createElement('p', null, 'Loading dashboard data...')
            ),
            React.createElement('div', { className: 'metrics-row' },
                [1, 2, 3, 4, 5].map(i =>
                    React.createElement('div', { key: i, className: 'metric' },
                        React.createElement('div', { className: 'skeleton skeleton-text' }),
                        React.createElement('div', { className: 'skeleton skeleton-value' })
                    )
                )
            )
        );
    }

    return React.createElement('div', { className: 'animate-in' },
        React.createElement('div', { className: 'dashboard-welcome' },
            React.createElement('h2', null, 'Platform Overview'),
            React.createElement('p', null, 'Real-time status of your quantitative trading system')
        ),

        // Stats row — now includes portfolio value + active agents
        React.createElement('div', { className: 'metrics-row' },
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'System Status'),
                React.createElement('div', {
                    className: 'metric-value',
                    style: { color: health?.status === 'UP' ? 'var(--accent-green)' : 'var(--accent-red)' }
                }, health?.status || 'DOWN')
            ),
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'Portfolio Value'),
                React.createElement('div', { className: 'metric-value' }, '$' + portfolioValue.toLocaleString())
            ),
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'Symbols Tracked'),
                React.createElement('div', { className: 'metric-value blue' }, symbols.length)
            ),
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'Active Strategies'),
                React.createElement('div', { className: 'metric-value neutral' }, strategies.length)
            ),
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'Active Agents'),
                React.createElement('div', { className: 'metric-value purple' }, activeAgents + ' / ' + agents.length)
            ),
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'Data Points'),
                React.createElement('div', { className: 'metric-value' }, totalRecords.toLocaleString())
            )
        ),

        // Symbols
        React.createElement('div', { className: 'card' },
            React.createElement('div', { className: 'card-header' }, 'Market Data Coverage'),
            React.createElement('div', { className: 'data-grid' },
                symbols.map(sym =>
                    React.createElement('div', { key: sym, className: 'data-card' },
                        React.createElement('div', { className: 'data-card-symbol' }, sym),
                        React.createElement('div', { className: 'data-card-stat' },
                            (summaries[sym]?.totalRecords || '...') + ' records'
                        )
                    )
                )
            )
        ),

        // Strategies overview
        React.createElement('div', { className: 'card' },
            React.createElement('div', { className: 'card-header' }, 'Strategy Portfolio'),
            strategies.length === 0
                ? React.createElement('div', { className: 'empty-state' }, 'No strategies configured yet.')
                : React.createElement('div', { className: 'strategy-list' },
                    strategies.map(s =>
                        React.createElement('div', { key: s.id, className: 'strategy-card' },
                            React.createElement('div', { className: 'strategy-card-header' },
                                React.createElement('span', { className: 'strategy-name' }, s.name),
                                React.createElement('span', { className: 'strategy-badge ' + (s.modelType || '').toLowerCase() },
                                    s.modelType)
                            ),
                            React.createElement('div', { className: 'strategy-card-details' },
                                React.createElement('span', null, 'Symbol: ' + s.symbol),
                                React.createElement('span', null, 'Cash: $' + parseFloat(s.currentCash || 0).toLocaleString()),
                                React.createElement('span', null, 'Risk: $' + parseFloat(s.targetRisk || 0).toLocaleString())
                            )
                        )
                    )
                )
        ),

        // Recent Signals
        React.createElement('div', { className: 'card' },
            React.createElement('div', { className: 'card-header' }, '📡 Recent Signals'),
            recentSignals.length === 0
                ? React.createElement('div', { className: 'empty-state' },
                    'Execute strategies to see recent signals here.')
                : React.createElement('div', { className: 'signal-list' },
                    recentSignals.map((sig, i) =>
                        React.createElement('div', { key: i, className: 'signal-item' },
                            React.createElement('span', { className: 'signal-badge ' + (sig.signal || '').toLowerCase() }, sig.signal),
                            React.createElement('span', null, sig.strategy),
                            React.createElement('span', { className: 'ticker-change ' + (sig.confidence > 0.6 ? 'up' : '') },
                                (sig.confidence * 100).toFixed(0) + '% confidence')
                        )
                    )
                )
        )
    );
};

// ─── Market Data View ────────────────────────────────────────

const MarketDataView = ({ symbols }) => {
    const [symbol, setSymbol] = useState(symbols[0] || 'SPY');
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [days, setDays] = useState(120);

    const {
        LineChart, Line, AreaChart, Area, BarChart, Bar,
        XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
        ReferenceLine, Legend, ComposedChart
    } = Recharts;

    useEffect(() => {
        setLoading(true);
        ApiClient.getPrices(symbol, days)
            .then(prices => {
                const enriched = prices.map((p, i, arr) => {
                    const d = new Date(p.time);
                    const sma20 = i >= 19 ? arr.slice(i - 19, i + 1).reduce((a, x) => a + x.close, 0) / 20 : null;
                    const sma50 = i >= 49 ? arr.slice(i - 49, i + 1).reduce((a, x) => a + x.close, 0) / 50 : null;
                    return {
                        ...p,
                        day: (d.getMonth() + 1) + '/' + d.getDate(),
                        sma20: sma20 ? +sma20.toFixed(2) : null,
                        sma50: sma50 ? +sma50.toFixed(2) : null,
                        spread: +(p.high - p.low).toFixed(2),
                        change: i > 0 ? +((p.close - arr[i - 1].close) / arr[i - 1].close * 100).toFixed(2) : 0,
                    };
                });
                setData(enriched);
                setLoading(false);
            })
            .catch(() => setLoading(false));
    }, [symbol, days]);

    const tooltipStyle = { background: '#1a2234', border: '1px solid #2a3650', borderRadius: 8, fontFamily: 'IBM Plex Mono' };
    const latest = data[data.length - 1] || {};
    const prev = data[data.length - 2] || {};
    const priceChange = latest.close && prev.close ? ((latest.close - prev.close) / prev.close * 100).toFixed(2) : '0.00';

    if (loading) return React.createElement('div', { className: 'loading-state' }, 'Loading market data...');

    return React.createElement('div', { className: 'animate-in' },
        // Controls
        React.createElement('div', { className: 'controls-row' },
            React.createElement('div', { className: 'symbol-selector' },
                symbols.map(s =>
                    React.createElement('button', {
                        key: s,
                        className: 'symbol-btn ' + (symbol === s ? 'active' : ''),
                        onClick: () => setSymbol(s)
                    }, s)
                )
            ),
            React.createElement('div', { className: 'period-selector' },
                [30, 60, 120, 252].map(d =>
                    React.createElement('button', {
                        key: d,
                        className: 'period-btn ' + (days === d ? 'active' : ''),
                        onClick: () => setDays(d)
                    }, d + 'D')
                )
            )
        ),

        // Metrics
        React.createElement('div', { className: 'metrics-row' },
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, symbol + ' Price'),
                React.createElement('div', { className: 'metric-value' }, '$' + (latest.close || '—'))
            ),
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'Daily Change'),
                React.createElement('div', {
                    className: 'metric-value ' + (priceChange >= 0 ? '' : 'negative')
                }, (priceChange >= 0 ? '+' : '') + priceChange + '%')
            ),
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'SMA 20'),
                React.createElement('div', { className: 'metric-value neutral' }, '$' + (latest.sma20 || '—'))
            ),
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'SMA 50'),
                React.createElement('div', { className: 'metric-value blue' }, '$' + (latest.sma50 || '—'))
            ),
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'Signal'),
                React.createElement('div', null,
                    React.createElement('span', {
                        className: 'signal-badge ' + (latest.close > (latest.sma50 || 0) ? 'long' : 'short')
                    }, latest.close > (latest.sma50 || 0) ? '⚡ BULLISH' : '⚡ BEARISH')
                )
            )
        ),

        // Price chart
        React.createElement('div', { className: 'card' },
            React.createElement('div', { className: 'card-header' }, symbol + ' — Price & Moving Averages'),
            React.createElement('div', { className: 'chart-container' },
                React.createElement(ResponsiveContainer, { width: '100%', height: 380 },
                    React.createElement(LineChart, { data: data },
                        React.createElement(CartesianGrid, { strokeDasharray: '3 3', stroke: '#1e293b' }),
                        React.createElement(XAxis, { dataKey: 'day', tick: { fill: '#64748b', fontSize: 10 }, interval: Math.floor(data.length / 10) }),
                        React.createElement(YAxis, { tick: { fill: '#64748b', fontSize: 11 }, domain: ['auto', 'auto'] }),
                        React.createElement(Tooltip, { contentStyle: tooltipStyle }),
                        React.createElement(Line, { type: 'monotone', dataKey: 'close', stroke: '#00ff88', strokeWidth: 2, dot: false, name: 'Close' }),
                        React.createElement(Line, { type: 'monotone', dataKey: 'sma20', stroke: '#fbbf24', strokeWidth: 1.5, dot: false, name: 'SMA 20', strokeDasharray: '4 4' }),
                        React.createElement(Line, { type: 'monotone', dataKey: 'sma50', stroke: '#ef4444', strokeWidth: 1.5, dot: false, name: 'SMA 50', strokeDasharray: '8 4' }),
                        React.createElement(Legend, { wrapperStyle: { fontFamily: 'IBM Plex Mono', fontSize: 11 } })
                    )
                )
            )
        ),

        // Volume chart
        React.createElement('div', { className: 'card' },
            React.createElement('div', { className: 'card-header' }, 'Volume'),
            React.createElement('div', { className: 'chart-container' },
                React.createElement(ResponsiveContainer, { width: '100%', height: 180 },
                    React.createElement(BarChart, { data: data },
                        React.createElement(CartesianGrid, { strokeDasharray: '3 3', stroke: '#1e293b' }),
                        React.createElement(XAxis, { dataKey: 'day', tick: { fill: '#64748b', fontSize: 10 }, interval: Math.floor(data.length / 10) }),
                        React.createElement(YAxis, { tick: { fill: '#64748b', fontSize: 11 }, tickFormatter: v => (v / 1e6).toFixed(0) + 'M' }),
                        React.createElement(Tooltip, { contentStyle: tooltipStyle, formatter: v => (v / 1e6).toFixed(2) + 'M' }),
                        React.createElement(Bar, { dataKey: 'volume', fill: '#3b82f660', name: 'Volume' })
                    )
                )
            )
        )
    );
};

// ─── Strategies View ─────────────────────────────────────────

const StrategiesView = ({ strategies, onRefresh }) => {
    const [executing, setExecuting] = useState(null);
    const [results, setResults] = useState({});
    const [creating, setCreating] = useState(false);
    const [newStrat, setNewStrat] = useState({
        name: '', symbol: 'SPY', modelType: 'MOMENTUM',
        currentCash: 100000, positionMultiplier: 1.0, targetRisk: 10000
    });

    const handleExecute = async (id) => {
        setExecuting(id);
        try {
            const result = await ApiClient.executeStrategy(id);
            setResults(prev => ({ ...prev, [id]: result }));
        } catch (err) {
            setResults(prev => ({ ...prev, [id]: { error: err.message } }));
        }
        setExecuting(null);
    };

    const handleCreate = async (e) => {
        e.preventDefault();
        try {
            await ApiClient.createStrategy(newStrat);
            setCreating(false);
            setNewStrat({ name: '', symbol: 'SPY', modelType: 'MOMENTUM', currentCash: 100000, positionMultiplier: 1.0, targetRisk: 10000 });
            onRefresh();
        } catch (err) {
            alert(err.message);
        }
    };

    const handleDelete = async (id) => {
        if (!confirm('Delete this strategy?')) return;
        try {
            await ApiClient.deleteStrategy(id);
            onRefresh();
        } catch (err) { alert(err.message); }
    };

    const { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } = Recharts;
    const tooltipStyle = { background: '#1a2234', border: '1px solid #2a3650', borderRadius: 8, fontFamily: 'IBM Plex Mono' };

    return React.createElement('div', { className: 'animate-in' },
        React.createElement('div', { className: 'section-header' },
            React.createElement('h2', null, 'Strategy Engine'),
            React.createElement('button', {
                className: 'btn-primary', onClick: () => setCreating(!creating)
            }, creating ? '✕ Cancel' : '+ New Strategy')
        ),

        // Create form
        creating && React.createElement('div', { className: 'card create-form' },
            React.createElement('form', { onSubmit: handleCreate },
                React.createElement('div', { className: 'form-row' },
                    React.createElement('div', { className: 'form-group' },
                        React.createElement('label', null, 'Name'),
                        React.createElement('input', {
                            value: newStrat.name, required: true,
                            onChange: e => setNewStrat({ ...newStrat, name: e.target.value })
                        })
                    ),
                    React.createElement('div', { className: 'form-group' },
                        React.createElement('label', null, 'Symbol'),
                        React.createElement('select', {
                            value: newStrat.symbol,
                            onChange: e => setNewStrat({ ...newStrat, symbol: e.target.value })
                        },
                            ['SPY', 'AAPL', 'QQQ'].map(s => React.createElement('option', { key: s, value: s }, s))
                        )
                    ),
                    React.createElement('div', { className: 'form-group' },
                        React.createElement('label', null, 'Model Type'),
                        React.createElement('select', {
                            value: newStrat.modelType,
                            onChange: e => setNewStrat({ ...newStrat, modelType: e.target.value })
                        },
                            ['MOMENTUM', 'VOLATILITY', 'MACRO', 'CORRELATION', 'REGIME'].map(t =>
                                React.createElement('option', { key: t, value: t }, t))
                        )
                    )
                ),
                React.createElement('div', { className: 'form-row' },
                    React.createElement('div', { className: 'form-group' },
                        React.createElement('label', null, 'Initial Cash ($)'),
                        React.createElement('input', {
                            type: 'number', value: newStrat.currentCash,
                            onChange: e => setNewStrat({ ...newStrat, currentCash: +e.target.value })
                        })
                    ),
                    React.createElement('div', { className: 'form-group' },
                        React.createElement('label', null, 'Target Risk ($)'),
                        React.createElement('input', {
                            type: 'number', value: newStrat.targetRisk,
                            onChange: e => setNewStrat({ ...newStrat, targetRisk: +e.target.value })
                        })
                    )
                ),
                React.createElement('button', { type: 'submit', className: 'btn-primary' }, 'Create Strategy')
            )
        ),

        // Strategy cards
        strategies.map(s => {
            const result = results[s.id];
            return React.createElement('div', { key: s.id, className: 'card strategy-exec-card' },
                React.createElement('div', { className: 'strategy-exec-header' },
                    React.createElement('div', null,
                        React.createElement('div', { className: 'strategy-name' }, s.name),
                        React.createElement('div', { className: 'strategy-meta' },
                            React.createElement('span', { className: 'strategy-badge ' + (s.modelType || '').toLowerCase() }, s.modelType),
                            React.createElement('span', null, s.symbol),
                            React.createElement('span', null, 'Cash: $' + (s.currentCash || 0).toLocaleString())
                        )
                    ),
                    React.createElement('div', { className: 'strategy-actions' },
                        React.createElement('button', {
                            className: 'btn-execute',
                            disabled: executing === s.id,
                            onClick: () => handleExecute(s.id)
                        }, executing === s.id ? '⏳ Running...' : '▶ Execute'),
                        React.createElement('button', {
                            className: 'btn-danger-sm',
                            onClick: () => handleDelete(s.id)
                        }, '✕')
                    )
                ),
                result && !result.error && React.createElement('div', { className: 'execution-result' },
                    React.createElement('div', { className: 'result-signal' },
                        React.createElement('span', {
                            className: 'signal-badge ' + (result.action || '').toLowerCase()
                        }, '⚡ ' + result.action),
                        result.confidence && React.createElement('span', { className: 'result-confidence' },
                            (result.confidence * 100).toFixed(0) + '% confidence')
                    ),
                    result.reasoning && React.createElement('div', { className: 'result-reasoning' }, result.reasoning),
                    result.metrics && React.createElement('div', { className: 'result-metrics' },
                        Object.entries(result.metrics).map(([k, v]) =>
                            React.createElement('div', { key: k, className: 'result-metric' },
                                React.createElement('span', { className: 'result-metric-label' }, k.replace(/([A-Z])/g, ' $1').trim()),
                                React.createElement('span', { className: 'result-metric-value' }, typeof v === 'number' ? v.toFixed(4) : String(v))
                            )
                        )
                    )
                ),
                result && result.error && React.createElement('div', { className: 'auth-error' }, result.error)
            );
        })
    );
};

// ─── Backtest View ───────────────────────────────────────────

const BacktestView = ({ strategies }) => {
    const [strategyId, setStrategyId] = useState(strategies[0]?.id || '');
    const [startDate, setStartDate] = useState('2024-03-01');
    const [endDate, setEndDate] = useState('2025-06-01');
    const [initialCapital, setInitialCapital] = useState(100000);
    const [result, setResult] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine } = Recharts;
    const tooltipStyle = { background: '#1a2234', border: '1px solid #2a3650', borderRadius: 8, fontFamily: 'IBM Plex Mono' };

    const handleRun = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError('');
        setResult(null);
        try {
            const res = await ApiClient.runBacktest(+strategyId, startDate, endDate, initialCapital);
            setResult(res);
        } catch (err) {
            setError(err.message);
        }
        setLoading(false);
    };

    const equityCurve = result?.equityCurve ? result.equityCurve.map((v, i) => ({
        day: i, equity: +v.toFixed(2)
    })) : [];

    return React.createElement('div', { className: 'animate-in' },
        React.createElement('h2', null, 'Backtesting Engine'),
        React.createElement('div', { className: 'card' },
            React.createElement('form', { className: 'backtest-form', onSubmit: handleRun },
                React.createElement('div', { className: 'form-row' },
                    React.createElement('div', { className: 'form-group' },
                        React.createElement('label', null, 'Strategy'),
                        React.createElement('select', {
                            value: strategyId, required: true,
                            onChange: e => setStrategyId(e.target.value)
                        },
                            strategies.map(s => React.createElement('option', { key: s.id, value: s.id },
                                s.name + ' (' + s.modelType + ')'))
                        )
                    ),
                    React.createElement('div', { className: 'form-group' },
                        React.createElement('label', null, 'Start Date'),
                        React.createElement('input', {
                            type: 'date', value: startDate, required: true,
                            onChange: e => setStartDate(e.target.value)
                        })
                    ),
                    React.createElement('div', { className: 'form-group' },
                        React.createElement('label', null, 'End Date'),
                        React.createElement('input', {
                            type: 'date', value: endDate, required: true,
                            onChange: e => setEndDate(e.target.value)
                        })
                    ),
                    React.createElement('div', { className: 'form-group' },
                        React.createElement('label', null, 'Initial Capital ($)'),
                        React.createElement('input', {
                            type: 'number', value: initialCapital, required: true,
                            onChange: e => setInitialCapital(+e.target.value)
                        })
                    )
                ),
                React.createElement('button', { type: 'submit', className: 'btn-primary', disabled: loading },
                    loading ? '⏳ Running Backtest...' : '▶ Run Backtest')
            )
        ),
        error && React.createElement('div', { className: 'auth-error', style: { marginTop: 16 } }, error),

        result && React.createElement('div', null,
            React.createElement('div', { className: 'metrics-row', style: { marginTop: 24 } },
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Total Return'),
                    React.createElement('div', {
                        className: 'metric-value ' + (result.totalReturn >= 0 ? '' : 'negative')
                    }, (result.totalReturn * 100).toFixed(2) + '%')
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Sharpe Ratio'),
                    React.createElement('div', { className: 'metric-value blue' },
                        (result.sharpeRatio || 0).toFixed(3))
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Max Drawdown'),
                    React.createElement('div', { className: 'metric-value negative' },
                        ((result.maxDrawdown || 0) * 100).toFixed(2) + '%')
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Win Rate'),
                    React.createElement('div', { className: 'metric-value neutral' },
                        ((result.winRate || 0) * 100).toFixed(1) + '%')
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Total Trades'),
                    React.createElement('div', { className: 'metric-value' }, result.totalTrades || 0)
                )
            ),
            equityCurve.length > 0 && React.createElement('div', { className: 'card', style: { marginTop: 16 } },
                React.createElement('div', { className: 'card-header' }, 'Equity Curve'),
                React.createElement('div', { className: 'chart-container' },
                    React.createElement(ResponsiveContainer, { width: '100%', height: 350 },
                        React.createElement(LineChart, { data: equityCurve },
                            React.createElement(CartesianGrid, { strokeDasharray: '3 3', stroke: '#1e293b' }),
                            React.createElement(XAxis, { dataKey: 'day', tick: { fill: '#64748b', fontSize: 11 } }),
                            React.createElement(YAxis, { tick: { fill: '#64748b', fontSize: 11 }, tickFormatter: v => '$' + (v / 1000).toFixed(0) + 'k' }),
                            React.createElement(Tooltip, { contentStyle: tooltipStyle, formatter: v => '$' + v.toLocaleString() }),
                            React.createElement(ReferenceLine, { y: initialCapital, stroke: '#fbbf24', strokeDasharray: '4 4' }),
                            React.createElement(Line, { type: 'monotone', dataKey: 'equity', stroke: '#00ff88', strokeWidth: 2, dot: false })
                        )
                    )
                )
            )
        )
    );
};

// ─── Agents View ─────────────────────────────────────────────

const AgentsView = ({ strategies }) => {
    const [agents, setAgents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [creating, setCreating] = useState(false);
    const [newAgent, setNewAgent] = useState({ name: '', strategyId: '', cronExpression: '0 0 9 * * MON-FRI' });

    const loadAgents = useCallback(async () => {
        try {
            const data = await ApiClient.getAgents();
            setAgents(Array.isArray(data) ? data : []);
        } catch { setAgents([]); }
        setLoading(false);
    }, []);

    useEffect(() => { loadAgents(); }, [loadAgents]);

    const handleCreate = async (e) => {
        e.preventDefault();
        try {
            await ApiClient.createAgent({ ...newAgent, strategyId: +newAgent.strategyId });
            setCreating(false);
            loadAgents();
        } catch (err) { alert(err.message); }
    };

    const handleStart = async (id) => {
        await ApiClient.startAgent(id);
        loadAgents();
    };

    const handleStop = async (id) => {
        await ApiClient.stopAgent(id);
        loadAgents();
    };

    if (loading) return React.createElement('div', { className: 'loading-state' }, 'Loading agents...');

    return React.createElement('div', { className: 'animate-in' },
        React.createElement('div', { className: 'section-header' },
            React.createElement('h2', null, 'Trading Agents'),
            React.createElement('button', {
                className: 'btn-primary', onClick: () => setCreating(!creating)
            }, creating ? '✕ Cancel' : '+ New Agent')
        ),

        creating && React.createElement('div', { className: 'card create-form' },
            React.createElement('form', { onSubmit: handleCreate },
                React.createElement('div', { className: 'form-row' },
                    React.createElement('div', { className: 'form-group' },
                        React.createElement('label', null, 'Agent Name'),
                        React.createElement('input', {
                            value: newAgent.name, required: true,
                            onChange: e => setNewAgent({ ...newAgent, name: e.target.value })
                        })
                    ),
                    React.createElement('div', { className: 'form-group' },
                        React.createElement('label', null, 'Strategy'),
                        React.createElement('select', {
                            value: newAgent.strategyId, required: true,
                            onChange: e => setNewAgent({ ...newAgent, strategyId: e.target.value })
                        },
                            React.createElement('option', { value: '' }, 'Select...'),
                            strategies.map(s => React.createElement('option', { key: s.id, value: s.id }, s.name))
                        )
                    ),
                    React.createElement('div', { className: 'form-group' },
                        React.createElement('label', null, 'Cron Schedule'),
                        React.createElement('input', {
                            value: newAgent.cronExpression,
                            onChange: e => setNewAgent({ ...newAgent, cronExpression: e.target.value }),
                            placeholder: '0 0 9 * * MON-FRI'
                        })
                    )
                ),
                React.createElement('button', { type: 'submit', className: 'btn-primary' }, 'Create Agent')
            )
        ),

        agents.length === 0 && !creating
            ? React.createElement('div', { className: 'card empty-state' }, 'No trading agents configured. Create one to automate strategy execution.')
            : agents.map(a =>
                React.createElement('div', { key: a.id, className: 'card agent-card' },
                    React.createElement('div', { className: 'agent-header' },
                        React.createElement('div', null,
                            React.createElement('div', { className: 'strategy-name' }, a.name),
                            React.createElement('div', { className: 'strategy-meta' },
                                React.createElement('span', null, 'Cron: ' + (a.cronExpression || 'N/A')),
                                React.createElement('span', null, 'Last Run: ' + (a.lastExecution ? new Date(a.lastExecution).toLocaleString() : 'Never'))
                            )
                        ),
                        React.createElement('div', { className: 'agent-controls' },
                            React.createElement('span', {
                                className: 'agent-status ' + (a.active ? 'active' : 'inactive')
                            }, a.active ? '● ACTIVE' : '● STOPPED'),
                            a.active
                                ? React.createElement('button', { className: 'btn-danger-sm', onClick: () => handleStop(a.id) }, '■ Stop')
                                : React.createElement('button', { className: 'btn-execute', onClick: () => handleStart(a.id) }, '▶ Start')
                        )
                    )
                )
            )
    );
};

// ─── Orders View ─────────────────────────────────────────────

const OrdersView = ({ symbols, strategies }) => {
    const [orders, setOrders] = useState([]);
    const [positions, setPositions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [orderForm, setOrderForm] = useState({ symbol: symbols[0] || 'SPY', side: 'BUY', quantity: 100, orderType: 'MARKET' });
    const [msg, setMsg] = useState('');

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [o, p] = await Promise.all([ApiClient.getOrders(), ApiClient.getPositions()]);
            setOrders(o);
            setPositions(Array.isArray(p) ? p : []);
        } catch (e) { console.error(e); }
        setLoading(false);
    }, []);

    useEffect(() => { load(); }, [load]);

    const submitOrder = async () => {
        setMsg('Placing order...');
        try {
            const result = await ApiClient.placeOrder(orderForm);
            setMsg('Order ' + result.status + ' — ' + result.side + ' ' + result.quantity + ' ' + result.symbol + (result.filledPrice ? ' @ $' + result.filledPrice : ''));
            load();
        } catch (e) { setMsg('Error: ' + e.message); }
    };

    if (loading) return React.createElement('div', { className: 'loading-state' }, 'Loading orders...');

    return React.createElement('div', { className: 'animate-in' },
        React.createElement('h2', null, '📋 Order Management'),

        // Order form
        React.createElement('div', { className: 'card' },
            React.createElement('div', { className: 'card-header' }, 'Place Order'),
            React.createElement('div', { className: 'form-row' },
                React.createElement('select', {
                    value: orderForm.symbol,
                    onChange: e => setOrderForm({ ...orderForm, symbol: e.target.value }),
                }, symbols.map(s => React.createElement('option', { key: s, value: s }, s))),
                React.createElement('select', {
                    value: orderForm.side,
                    onChange: e => setOrderForm({ ...orderForm, side: e.target.value }),
                }, ['BUY', 'SELL'].map(s => React.createElement('option', { key: s, value: s }, s))),
                React.createElement('input', {
                    type: 'number', value: orderForm.quantity, min: 1,
                    onChange: e => setOrderForm({ ...orderForm, quantity: parseInt(e.target.value) || 1 }),
                }),
                React.createElement('select', {
                    value: orderForm.orderType,
                    onChange: e => setOrderForm({ ...orderForm, orderType: e.target.value }),
                }, ['MARKET', 'LIMIT'].map(t => React.createElement('option', { key: t, value: t }, t))),
                React.createElement('button', { className: 'btn-primary', onClick: submitOrder }, '🚀 Submit')
            ),
            msg && React.createElement('div', { className: 'form-msg', style: { marginTop: 12 } }, msg)
        ),

        // Positions
        React.createElement('div', { className: 'card' },
            React.createElement('div', { className: 'card-header' }, '💼 Portfolio Positions'),
            positions.length === 0
                ? React.createElement('div', { className: 'empty-state' }, 'No positions yet. Place an order to start.')
                : React.createElement('table', { className: 'data-table' },
                    React.createElement('thead', null,
                        React.createElement('tr', null,
                            ['Symbol', 'Qty', 'Avg Cost', 'Current', 'P&L'].map(h =>
                                React.createElement('th', { key: h }, h))
                        )
                    ),
                    React.createElement('tbody', null,
                        positions.map(p =>
                            React.createElement('tr', { key: p.id },
                                React.createElement('td', null, React.createElement('strong', null, p.symbol)),
                                React.createElement('td', null, p.quantity),
                                React.createElement('td', null, '$' + parseFloat(p.avgCost || 0).toFixed(2)),
                                React.createElement('td', null, '$' + parseFloat(p.currentPrice || 0).toFixed(2)),
                                React.createElement('td', {
                                    className: parseFloat(p.unrealizedPnl || 0) >= 0 ? 'positive' : 'negative'
                                }, '$' + parseFloat(p.unrealizedPnl || 0).toFixed(2))
                            )
                        )
                    )
                )
        ),

        // Orders
        React.createElement('div', { className: 'card' },
            React.createElement('div', { className: 'card-header' }, '📜 Order History'),
            React.createElement('table', { className: 'data-table' },
                React.createElement('thead', null,
                    React.createElement('tr', null,
                        ['ID', 'Symbol', 'Side', 'Qty', 'Status', 'Fill', 'Slippage'].map(h =>
                            React.createElement('th', { key: h }, h))
                    )
                ),
                React.createElement('tbody', null,
                    orders.slice(0, 20).map(o =>
                        React.createElement('tr', { key: o.id },
                            React.createElement('td', null, '#' + o.id),
                            React.createElement('td', null, o.symbol),
                            React.createElement('td', { className: 'signal-badge ' + (o.side || '').toLowerCase() }, o.side),
                            React.createElement('td', null, o.quantity),
                            React.createElement('td', null, React.createElement('span', {
                                className: 'strategy-badge ' + (o.status === 'FILLED' ? 'momentum' : 'volatility')
                            }, o.status)),
                            React.createElement('td', null, o.filledPrice ? '$' + parseFloat(o.filledPrice).toFixed(2) : '—'),
                            React.createElement('td', null, o.slippage ? parseFloat(o.slippage).toFixed(4) : '—')
                        )
                    )
                )
            )
        )
    );
};

// ─── Risk View ───────────────────────────────────────────────

const RiskView = ({ symbols }) => {
    const [varData, setVarData] = useState({});
    const [portRisk, setPortRisk] = useState(null);
    const [posLimits, setPosLimits] = useState(null);
    const [loading, setLoading] = useState(true);
    const [selectedSym, setSelectedSym] = useState(symbols[0] || 'SPY');

    useEffect(() => {
        setLoading(true);
        Promise.all([
            ApiClient.getVaR(selectedSym).catch(() => ({})),
            ApiClient.getPortfolioRisk().catch(() => null),
            ApiClient.checkPositionLimits().catch(() => null),
        ]).then(([v, pr, pl]) => {
            setVarData(v);
            setPortRisk(pr);
            setPosLimits(pl);
            setLoading(false);
        });
    }, [selectedSym]);

    if (loading) return React.createElement('div', { className: 'loading-state' }, 'Computing risk metrics...');

    return React.createElement('div', { className: 'animate-in' },
        React.createElement('h2', null, '🛡️ Risk Dashboard'),

        React.createElement('div', { className: 'symbol-selector', style: { marginBottom: 24 } },
            symbols.map(s =>
                React.createElement('button', {
                    key: s,
                    className: 'symbol-btn ' + (selectedSym === s ? 'active' : ''),
                    onClick: () => setSelectedSym(s)
                }, s)
            )
        ),

        // VaR metrics
        React.createElement('div', { className: 'metrics-row' },
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'VaR (95%)'),
                React.createElement('div', { className: 'metric-value ' + (Math.abs(varData.var95 || 0) > 5 ? 'negative' : '') },
                    (varData.var95 || 0).toFixed(2) + '%')
            ),
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'CVaR (95%)'),
                React.createElement('div', { className: 'metric-value' }, (varData.cvar95 || 0).toFixed(2) + '%')
            ),
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'Max Drawdown'),
                React.createElement('div', { className: 'metric-value negative' }, (varData.maxDrawdown || 0).toFixed(2) + '%')
            ),
            React.createElement('div', { className: 'metric' },
                React.createElement('div', { className: 'metric-label' }, 'Breaches'),
                React.createElement('div', {
                    className: 'metric-value ' + (varData.breaches ? 'negative' : '')
                }, varData.breaches ? '⚠️ YES' : '✅ CLEAR')
            )
        ),

        // Portfolio risk
        portRisk && React.createElement('div', { className: 'card' },
            React.createElement('div', { className: 'card-header' }, '📊 Portfolio Risk Summary'),
            React.createElement('div', { className: 'metrics-row' },
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Total Value'),
                    React.createElement('div', { className: 'metric-value' }, '$' + parseFloat(portRisk.totalPortfolioValue || 0).toLocaleString())
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Unrealized P&L'),
                    React.createElement('div', { className: 'metric-value' }, '$' + parseFloat(portRisk.totalUnrealizedPnl || 0).toLocaleString())
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Max Position Limit'),
                    React.createElement('div', { className: 'metric-value' }, '$' + parseFloat(portRisk.maxPositionLimit || 0).toLocaleString())
                )
            )
        ),

        // Position limits
        posLimits && React.createElement('div', { className: 'card' },
            React.createElement('div', { className: 'card-header' },
                (posLimits.allClear ? '✅' : '⚠️') + ' Position Limits'
            ),
            React.createElement('div', { className: 'empty-state' },
                posLimits.allClear
                    ? 'All positions within limits (' + posLimits.totalPositions + ' total)'
                    : posLimits.breaches?.length + ' breach(es) detected'
            )
        )
    );
};

// ─── ML Signals View ─────────────────────────────────────────

const MLView = ({ symbols }) => {
    const [predictions, setPredictions] = useState({});
    const [mlHealth, setMlHealth] = useState(null);
    const [loading, setLoading] = useState(false);
    const [training, setTraining] = useState(null);
    const [optResult, setOptResult] = useState(null);

    useEffect(() => {
        ApiClient.mlHealth().then(setMlHealth).catch(() => setMlHealth({ status: 'DOWN' }));
    }, []);

    const predictSymbol = async (sym) => {
        setLoading(true);
        try {
            const res = await ApiClient.mlPredict(sym);
            setPredictions(p => ({ ...p, [sym]: res }));
        } catch (e) { setPredictions(p => ({ ...p, [sym]: { error: e.message } })); }
        setLoading(false);
    };

    const trainSymbol = async (sym) => {
        setTraining(sym);
        try {
            const res = await ApiClient.mlTrain(sym);
            setPredictions(p => ({ ...p, [sym + '_train']: res }));
        } catch (e) { console.error(e); }
        setTraining(null);
    };

    const runOptimizer = async () => {
        setLoading(true);
        try {
            const res = await ApiClient.mlOptimize(symbols);
            setOptResult(res);
        } catch (e) { setOptResult({ error: e.message }); }
        setLoading(false);
    };

    return React.createElement('div', { className: 'animate-in' },
        React.createElement('h2', null, '🧠 ML Intelligence'),

        // ML service status
        React.createElement('div', { className: 'card' },
            React.createElement('div', { className: 'card-header' }, 'ML Service Status'),
            React.createElement('div', { className: 'metric-value ' + (mlHealth?.status === 'UP' ? '' : 'negative') },
                mlHealth?.status === 'UP' ? '🟢 Connected (Python FastAPI)' : '🔴 Disconnected — start with: python ml-service/main.py'
            )
        ),

        // Per-symbol predictions
        React.createElement('div', { className: 'card' },
            React.createElement('div', { className: 'card-header' }, '📡 ML Signal Predictions'),
            React.createElement('div', { className: 'data-grid' },
                symbols.map(sym => {
                    const pred = predictions[sym];
                    return React.createElement('div', { key: sym, className: 'data-card' },
                        React.createElement('div', { className: 'data-card-symbol' }, sym),
                        pred && !pred.error
                            ? React.createElement('div', null,
                                React.createElement('div', {
                                    className: 'signal-badge ' + (pred.signal || '').toLowerCase(),
                                    style: { display: 'inline-block', marginBottom: 8 }
                                }, pred.signal),
                                React.createElement('div', { style: { fontSize: '0.85rem', color: 'var(--text-muted)' } },
                                    'Confidence: ' + ((pred.confidence || 0) * 100).toFixed(1) + '%'),
                                React.createElement('div', { style: { fontSize: '0.8rem', color: 'var(--text-muted)' } },
                                    'Accuracy: ' + ((pred.model_accuracy || 0) * 100).toFixed(1) + '%')
                            )
                            : pred?.error
                                ? React.createElement('div', { className: 'negative', style: { fontSize: '0.8rem' } }, 'Error: ' + pred.error)
                                : React.createElement('div', { style: { color: 'var(--text-muted)', fontSize: '0.85rem' } }, 'Click predict'),
                        React.createElement('div', { style: { display: 'flex', gap: 8, marginTop: 8 } },
                            React.createElement('button', {
                                className: 'btn-primary btn-small',
                                onClick: () => predictSymbol(sym),
                                disabled: loading
                            }, loading ? '...' : 'Predict'),
                            React.createElement('button', {
                                className: 'btn-secondary btn-small',
                                onClick: () => trainSymbol(sym),
                                disabled: training === sym
                            }, training === sym ? 'Training...' : 'Train')
                        )
                    );
                })
            )
        ),

        // Portfolio Optimizer
        React.createElement('div', { className: 'card' },
            React.createElement('div', { className: 'card-header' }, '📐 Portfolio Optimization (Markowitz)'),
            React.createElement('button', {
                className: 'btn-primary', onClick: runOptimizer, disabled: loading,
                style: { marginBottom: 16 }
            }, loading ? 'Optimizing...' : '🔄 Run Optimizer'),
            optResult && !optResult.error
                ? React.createElement('div', null,
                    React.createElement('div', { className: 'metrics-row' },
                        React.createElement('div', { className: 'metric' },
                            React.createElement('div', { className: 'metric-label' }, 'Expected Return'),
                            React.createElement('div', { className: 'metric-value' }, ((optResult.expected_return || 0) * 100).toFixed(2) + '%')
                        ),
                        React.createElement('div', { className: 'metric' },
                            React.createElement('div', { className: 'metric-label' }, 'Volatility'),
                            React.createElement('div', { className: 'metric-value' }, ((optResult.volatility || 0) * 100).toFixed(2) + '%')
                        ),
                        React.createElement('div', { className: 'metric' },
                            React.createElement('div', { className: 'metric-label' }, 'Sharpe Ratio'),
                            React.createElement('div', { className: 'metric-value' }, (optResult.sharpe_ratio || 0).toFixed(3))
                        )
                    ),
                    optResult.allocation && React.createElement('div', { className: 'strategy-list', style: { marginTop: 16 } },
                        Object.entries(optResult.allocation).map(([sym, w]) =>
                            React.createElement('div', { key: sym, className: 'strategy-card' },
                                React.createElement('strong', null, sym),
                                React.createElement('div', {
                                    className: 'progress-bar',
                                    style: { background: 'var(--border)', borderRadius: 4, height: 8, marginTop: 4 }
                                },
                                    React.createElement('div', {
                                        style: { width: (w * 100) + '%', background: 'var(--accent-blue)', height: '100%', borderRadius: 4 }
                                    })
                                ),
                                React.createElement('span', { style: { fontSize: '0.85rem', color: 'var(--text-muted)' } },
                                    (w * 100).toFixed(1) + '%')
                            )
                        )
                    )
                )
                : optResult?.error ? React.createElement('div', { className: 'negative' }, optResult.error) : null
        )
    );
};

// ─── AI Intelligence View ────────────────────────────────────

const AIIntelligenceView = ({ strategies }) => {
    const [tab, setTab] = useState('roster');
    const [agents, setAgents] = useState([]);
    const [roles, setRoles] = useState([]);
    const [loading, setLoading] = useState(true);
    const [pipelineResult, setPipelineResult] = useState(null);
    const [attrResult, setAttrResult] = useState(null);
    const [icResults, setIcResults] = useState({});
    const [driftResults, setDriftResults] = useState({});
    const [runningPipeline, setRunningPipeline] = useState(null);
    const [systemAuditResult, setSystemAuditResult] = useState(null);
    const [execMonitorResult, setExecMonitorResult] = useState(null);
    const [runningAudit, setRunningAudit] = useState(false);
    const [runningMonitor, setRunningMonitor] = useState(false);

    useEffect(() => {
        Promise.all([
            ApiClient.getAgents().catch(() => []),
            ApiClient.getAgentRoles().catch(() => []),
        ]).then(([a, r]) => {
            setAgents(Array.isArray(a) ? a : []);
            setRoles(Array.isArray(r) ? r : []);
            setLoading(false);
        });
    }, []);

    const runPipeline = async (agentId) => {
        setRunningPipeline(agentId);
        try {
            const res = await ApiClient.runAgentPipeline(agentId);
            setPipelineResult(res);
        } catch (e) { setPipelineResult({ error: e.message }); }
        setRunningPipeline(null);
    };

    const runAttribution = async (agentId) => {
        try {
            const res = await ApiClient.runAttribution(agentId);
            setAttrResult(res);
        } catch (e) { setAttrResult({ error: e.message }); }
    };

    const fetchIC = async (strategyId) => {
        try {
            const [ic, drift] = await Promise.all([
                ApiClient.getSignalIC(strategyId, 20),
                ApiClient.getSignalDrift(strategyId),
            ]);
            setIcResults(p => ({ ...p, [strategyId]: ic }));
            setDriftResults(p => ({ ...p, [strategyId]: drift }));
        } catch (e) { console.error(e); }
    };

    const runSystemAudit = async () => {
        setRunningAudit(true);
        try {
            const res = await ApiClient.runSystemAudit();
            setSystemAuditResult(res);
        } catch (e) { setSystemAuditResult({ error: e.message }); }
        setRunningAudit(false);
    };

    const runExecMonitor = async () => {
        setRunningMonitor(true);
        try {
            const res = await ApiClient.runExecutionMonitor();
            setExecMonitorResult(res);
        } catch (e) { setExecMonitorResult({ error: e.message }); }
        setRunningMonitor(false);
    };

    const tabs = [
        { id: 'roster', label: '🤖 Agent Roster' },
        { id: 'pipeline', label: '🔬 Pipeline' },
        { id: 'ic', label: '📊 Signal IC' },
        { id: 'attribution', label: '📈 Attribution' },
        { id: 'sysaudit', label: '⚙️ System Audit' },
        { id: 'execmon', label: '🛡️ Exec Monitor' },
    ];

    if (loading) return React.createElement('div', { className: 'loading-state' }, 'Loading AI Intelligence...');

    return React.createElement('div', { className: 'animate-in' },
        React.createElement('h2', null, '🧬 AI Agent Intelligence'),

        // Tab selector
        React.createElement('div', { className: 'symbol-selector', style: { marginBottom: 24 } },
            tabs.map(t =>
                React.createElement('button', {
                    key: t.id,
                    className: 'symbol-btn ' + (tab === t.id ? 'active' : ''),
                    onClick: () => setTab(t.id)
                }, t.label)
            )
        ),

        // TAB: Roster
        tab === 'roster' && React.createElement('div', null,
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, 'Available AI Roles'),
                React.createElement('div', { className: 'data-grid' },
                    roles.map(r =>
                        React.createElement('div', { key: r.role, className: 'data-card' },
                            React.createElement('div', { className: 'data-card-symbol' }, r.role.replace(/_/g, ' ')),
                            React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-muted)' } },
                                r.promptPreview)
                        )
                    )
                )
            ),
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, 'Agent Roster (' + agents.length + ' agents)'),
                agents.length === 0
                    ? React.createElement('div', { className: 'empty-state' }, 'No agents configured. Create agents from the Agents page.')
                    : agents.map(a =>
                        React.createElement('div', { key: a.id, className: 'strategy-card', style: { marginBottom: 12 } },
                            React.createElement('div', { className: 'strategy-card-header' },
                                React.createElement('span', { className: 'strategy-name' }, a.name),
                                React.createElement('span', { className: 'strategy-badge' }, a.agentRole || 'N/A')
                            ),
                            React.createElement('div', { className: 'strategy-card-details' },
                                React.createElement('span', null, 'Executions: ' + (a.totalExecutions || 0)),
                                React.createElement('span', null, 'Success: ' + (a.successfulExecutions || 0)),
                                React.createElement('span', null, 'Confidence: ' + (a.lastConfidence ? (a.lastConfidence * 100).toFixed(0) + '%' : '—')),
                                React.createElement('span', {
                                    className: 'agent-status ' + (a.active ? 'active' : 'inactive')
                                }, a.active ? '● ACTIVE' : '● STOPPED')
                            ),
                            a.lastReasoning && React.createElement('div', {
                                style: { fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 8, fontStyle: 'italic' }
                            }, '"' + a.lastReasoning.substring(0, 200) + '..."')
                        )
                    )
            )
        ),

        // TAB: Pipeline
        tab === 'pipeline' && React.createElement('div', null,
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, '5-Stage Research Pipeline'),
                React.createElement('div', { className: 'insight-box' },
                    React.createElement('p', null,
                        React.createElement('strong', null, 'Pipeline Stages: '),
                        '1. Regime Analysis → 2. Quant Research → 3. Bias Audit → 4. Risk Analysis → 5. Psychology Check'
                    )
                ),
                React.createElement('div', { className: 'data-grid', style: { marginTop: 16 } },
                    agents.map(a =>
                        React.createElement('div', { key: a.id, className: 'data-card' },
                            React.createElement('div', { className: 'data-card-symbol' }, a.name),
                            React.createElement('button', {
                                className: 'btn-primary btn-small',
                                disabled: runningPipeline === a.id,
                                onClick: () => runPipeline(a.id),
                                style: { marginTop: 8 }
                            }, runningPipeline === a.id ? '⏳ Running...' : '▶ Run Pipeline')
                        )
                    )
                )
            ),
            pipelineResult && React.createElement('div', { className: 'card', style: { marginTop: 16 } },
                React.createElement('div', { className: 'card-header' }, 'Pipeline Result'),
                pipelineResult.error
                    ? React.createElement('div', { className: 'negative' }, pipelineResult.error)
                    : React.createElement('div', null,
                        React.createElement('div', { className: 'metrics-row' },
                            React.createElement('div', { className: 'metric' },
                                React.createElement('div', { className: 'metric-label' }, 'Status'),
                                React.createElement('div', {
                                    className: 'metric-value ' + (pipelineResult.pipeline_status === 'APPROVED' ? '' : 'negative')
                                }, pipelineResult.pipeline_status || '—')
                            ),
                            React.createElement('div', { className: 'metric' },
                                React.createElement('div', { className: 'metric-label' }, 'Final Decision'),
                                React.createElement('div', { className: 'metric-value blue' }, pipelineResult.final_decision || '—')
                            ),
                            React.createElement('div', { className: 'metric' },
                                React.createElement('div', { className: 'metric-label' }, 'Complete'),
                                React.createElement('div', { className: 'metric-value' },
                                    pipelineResult.pipeline_complete ? '✅ Yes' : '❌ No')
                            )
                        ),
                        React.createElement('pre', {
                            style: { background: '#0d1117', padding: 16, borderRadius: 8, fontSize: '0.8rem', overflow: 'auto', maxHeight: 300 }
                        }, JSON.stringify(pipelineResult, null, 2))
                    )
            )
        ),

        // TAB: Signal IC
        tab === 'ic' && React.createElement('div', null,
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, 'Signal Information Coefficient'),
                React.createElement('div', { className: 'insight-box' },
                    React.createElement('p', null,
                        React.createElement('strong', null, 'IC measures signal quality: '),
                        'Pearson correlation between predicted direction and actual returns. IC > 0.05 = strong, IC > 0.02 = moderate. Drift is detected when 20-day IC drops below 0.02.'
                    )
                ),
                React.createElement('div', { className: 'data-grid', style: { marginTop: 16 } },
                    strategies.map(s => {
                        const ic = icResults[s.id];
                        const drift = driftResults[s.id];
                        return React.createElement('div', { key: s.id, className: 'data-card' },
                            React.createElement('div', { className: 'data-card-symbol' }, s.name),
                            ic && React.createElement('div', null,
                                React.createElement('div', { className: 'metric-value ' + (Math.abs(ic.ic || 0) > 0.02 ? '' : 'negative') },
                                    'IC: ' + (ic.ic || 0).toFixed(4)),
                                React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-muted)' } },
                                    'Window: ' + (ic.window || 20) + 'd')
                            ),
                            drift && React.createElement('div', {
                                style: { marginTop: 4, fontSize: '0.8rem' }
                            },
                                React.createElement('span', {
                                    className: drift.is_drifting ? 'negative' : ''
                                }, drift.is_drifting ? '⚠ DRIFTING' : '✅ STABLE'),
                                drift.alert_triggered && React.createElement('span', {
                                    className: 'signal-badge sell', style: { marginLeft: 8 }
                                }, 'ALERT')
                            ),
                            React.createElement('button', {
                                className: 'btn-secondary btn-small',
                                onClick: () => fetchIC(s.id),
                                style: { marginTop: 8 }
                            }, 'Compute IC')
                        );
                    })
                )
            )
        ),

        // TAB: Attribution
        tab === 'attribution' && React.createElement('div', null,
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, 'P&L Attribution'),
                React.createElement('div', { className: 'data-grid' },
                    agents.map(a =>
                        React.createElement('div', { key: a.id, className: 'data-card' },
                            React.createElement('div', { className: 'data-card-symbol' }, a.name),
                            React.createElement('button', {
                                className: 'btn-secondary btn-small',
                                onClick: () => runAttribution(a.id),
                                style: { marginTop: 8 }
                            }, '📊 Run Attribution')
                        )
                    )
                )
            ),
            attrResult && React.createElement('div', { className: 'card', style: { marginTop: 16 } },
                React.createElement('div', { className: 'card-header' }, 'Attribution Result'),
                attrResult.error
                    ? React.createElement('div', { className: 'negative' }, attrResult.error)
                    : React.createElement('pre', {
                        style: { background: '#0d1117', padding: 16, borderRadius: 8, fontSize: '0.8rem', overflow: 'auto', maxHeight: 300 }
                    }, JSON.stringify(attrResult, null, 2))
            )
        ),

        // TAB: System Audit
        tab === 'sysaudit' && React.createElement('div', null,
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, '⚙️ HFT Systems Engineer Audit'),
                React.createElement('div', { className: 'insight-box' },
                    React.createElement('p', null,
                        React.createElement('strong', null, 'Thinks like a principal engineer at Citadel/Jump Trading. '),
                        'Analyzes architecture for latency bottlenecks, throughput limits, reliability gaps, and code quality. Provides prioritized optimizations with effort/impact/risk classification.'
                    )
                ),
                React.createElement('button', {
                    className: 'btn-primary',
                    onClick: runSystemAudit,
                    disabled: runningAudit,
                    style: { marginTop: 16 }
                }, runningAudit ? '⏳ Auditing system...' : '🔬 Run System Audit')
            ),
            systemAuditResult && React.createElement('div', { className: 'card', style: { marginTop: 16 } },
                React.createElement('div', { className: 'card-header' }, 'Audit Result'),
                systemAuditResult.error
                    ? React.createElement('div', { className: 'negative' }, systemAuditResult.error)
                    : React.createElement('div', null,
                        React.createElement('div', { className: 'metrics-row' },
                            React.createElement('div', { className: 'metric' },
                                React.createElement('div', { className: 'metric-label' }, 'Health Score'),
                                React.createElement('div', { className: 'metric-value ' + ((systemAuditResult.system_health_score || 0) >= 70 ? '' : 'negative') },
                                    (systemAuditResult.system_health_score || 0) + '/100')
                            ),
                            React.createElement('div', { className: 'metric' },
                                React.createElement('div', { className: 'metric-label' }, 'Assessment'),
                                React.createElement('div', {
                                    className: 'metric-value ' + (systemAuditResult.architecture_assessment === 'PRODUCTION_READY' ? '' : 'negative')
                                }, (systemAuditResult.architecture_assessment || '—').replace(/_/g, ' '))
                            ),
                            systemAuditResult.production_readiness && React.createElement('div', { className: 'metric' },
                                React.createElement('div', { className: 'metric-label' }, 'Prod Readiness'),
                                React.createElement('div', { className: 'metric-value' },
                                    (systemAuditResult.production_readiness.score || 0) + '/100')
                            )
                        ),
                        systemAuditResult.latency_analysis && React.createElement('div', { className: 'card', style: { marginTop: 12 } },
                            React.createElement('div', { className: 'card-header' }, '⏱ Latency Analysis'),
                            React.createElement('div', { className: 'metrics-row' },
                                React.createElement('div', { className: 'metric' },
                                    React.createElement('div', { className: 'metric-label' }, 'Est. Order Latency'),
                                    React.createElement('div', { className: 'metric-value' },
                                        (systemAuditResult.latency_analysis.estimated_order_latency_ms || 0) + 'ms')
                                ),
                                React.createElement('div', { className: 'metric' },
                                    React.createElement('div', { className: 'metric-label' }, 'Bottleneck'),
                                    React.createElement('div', { className: 'metric-value negative' },
                                        systemAuditResult.latency_analysis.bottleneck_component || '—')
                                ),
                                React.createElement('div', { className: 'metric' },
                                    React.createElement('div', { className: 'metric-label' }, 'Improvement'),
                                    React.createElement('div', { className: 'metric-value' },
                                        (systemAuditResult.latency_analysis.improvement_potential_pct || 0) + '%')
                                )
                            )
                        ),
                        React.createElement('pre', {
                            style: { background: '#0d1117', padding: 16, borderRadius: 8, fontSize: '0.8rem', overflow: 'auto', maxHeight: 400 }
                        }, JSON.stringify(systemAuditResult, null, 2))
                    )
            )
        ),

        // TAB: Execution Monitor
        tab === 'execmon' && React.createElement('div', null,
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, '🛡️ Execution Monitor'),
                React.createElement('div', { className: 'insight-box' },
                    React.createElement('p', null,
                        React.createElement('strong', null, 'Real-time surveillance of all trading algorithms. '),
                        'Detects anomalies (volume, price, frequency), monitors fill quality, tracks agent health, and enforces 5-level circuit breaker system (WARNING → THROTTLE → PAUSE → HALT → LIQUIDATE).'
                    )
                ),
                React.createElement('button', {
                    className: 'btn-primary',
                    onClick: runExecMonitor,
                    disabled: runningMonitor,
                    style: { marginTop: 16 }
                }, runningMonitor ? '⏳ Monitoring...' : '🔍 Run Execution Monitor')
            ),
            execMonitorResult && React.createElement('div', { className: 'card', style: { marginTop: 16 } },
                React.createElement('div', { className: 'card-header' }, 'Monitor Result'),
                execMonitorResult.error
                    ? React.createElement('div', { className: 'negative' }, execMonitorResult.error)
                    : React.createElement('div', null,
                        React.createElement('div', { className: 'metrics-row' },
                            React.createElement('div', { className: 'metric' },
                                React.createElement('div', { className: 'metric-label' }, 'Status'),
                                React.createElement('div', {
                                    className: 'metric-value ' + (execMonitorResult.monitoring_status === 'ALL_CLEAR' ? '' : 'negative')
                                }, execMonitorResult.monitoring_status || '—')
                            ),
                            React.createElement('div', { className: 'metric' },
                                React.createElement('div', { className: 'metric-label' }, 'Circuit Breaker'),
                                React.createElement('div', {
                                    className: 'metric-value ' + ((execMonitorResult.circuit_breaker_level || 0) >= 3 ? 'negative' : '')
                                }, 'Level ' + (execMonitorResult.circuit_breaker_level || 0))
                            ),
                            execMonitorResult.execution_quality && React.createElement('div', { className: 'metric' },
                                React.createElement('div', { className: 'metric-label' }, 'Avg Slippage'),
                                React.createElement('div', { className: 'metric-value' },
                                    (execMonitorResult.execution_quality.avg_slippage_bps || 0).toFixed(1) + ' bps')
                            ),
                            execMonitorResult.execution_quality && React.createElement('div', { className: 'metric' },
                                React.createElement('div', { className: 'metric-label' }, 'Fill Rate'),
                                React.createElement('div', { className: 'metric-value' },
                                    (execMonitorResult.execution_quality.fill_rate_pct || 0).toFixed(1) + '%')
                            )
                        ),
                        execMonitorResult.anomalies_detected && execMonitorResult.anomalies_detected.length > 0 &&
                            React.createElement('div', { className: 'card', style: { marginTop: 12 } },
                                React.createElement('div', { className: 'card-header' }, '⚠️ Anomalies (' + execMonitorResult.anomalies_detected.length + ')'),
                                React.createElement('div', { className: 'signal-list' },
                                    execMonitorResult.anomalies_detected.map((a, i) =>
                                        React.createElement('div', { key: i, className: 'signal-item' },
                                            React.createElement('span', {
                                                className: 'signal-badge ' + (a.severity === 'CRITICAL' ? 'sell' : 'hold')
                                            }, a.severity),
                                            React.createElement('span', { className: 'strategy-badge' }, a.type),
                                            React.createElement('span', null, a.description),
                                            React.createElement('span', {
                                                style: { color: 'var(--accent-blue)', fontSize: '0.8rem' }
                                            }, '→ ' + a.recommended_action)
                                        )
                                    )
                                )
                            ),
                        React.createElement('pre', {
                            style: { background: '#0d1117', padding: 16, borderRadius: 8, fontSize: '0.8rem', overflow: 'auto', maxHeight: 400 }
                        }, JSON.stringify(execMonitorResult, null, 2))
                    )
            )
        )
    );
};

// ─── Alerts View ─────────────────────────────────────────────

const AlertsView = () => {
    const [alerts, setAlerts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [filter, setFilter] = useState('all');

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const data = filter === 'unack'
                ? await ApiClient.getUnacknowledgedAlerts()
                : await ApiClient.getAlerts();
            setAlerts(data);
        } catch (e) { console.error(e); }
        setLoading(false);
    }, [filter]);

    useEffect(() => { load(); }, [load]);

    const ack = async (id) => {
        await ApiClient.acknowledgeAlert(id);
        load();
    };

    if (loading) return React.createElement('div', { className: 'loading-state' }, 'Loading alerts...');

    return React.createElement('div', { className: 'animate-in' },
        React.createElement('h2', null, '🔔 Risk Alerts'),

        React.createElement('div', { className: 'symbol-selector', style: { marginBottom: 24 } },
            [{ id: 'all', label: 'All' }, { id: 'unack', label: 'Unacknowledged' }].map(f =>
                React.createElement('button', {
                    key: f.id,
                    className: 'symbol-btn ' + (filter === f.id ? 'active' : ''),
                    onClick: () => setFilter(f.id)
                }, f.label)
            )
        ),

        alerts.length === 0
            ? React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'empty-state' }, '🎉 No alerts. Run risk checks from the Risk Dashboard to generate alerts.')
            )
            : React.createElement('div', { className: 'signal-list' },
                alerts.map(a =>
                    React.createElement('div', { key: a.id, className: 'signal-item' },
                        React.createElement('span', { className: 'signal-badge ' + (a.severity === 'CRITICAL' ? 'sell' : 'hold') }, a.severity),
                        React.createElement('span', { className: 'strategy-badge' }, a.alertType),
                        React.createElement('span', null, a.message),
                        a.symbol && React.createElement('span', { style: { color: 'var(--accent-blue)' } }, a.symbol),
                        !a.acknowledged && React.createElement('button', {
                            className: 'btn-small btn-secondary', onClick: () => ack(a.id)
                        }, '✓ Ack')
                    )
                )
            )
    );
};

// ─── Root App ────────────────────────────────────────────────

const App = () => {
    const [user, setUser] = useState(ApiClient.getUser());
    const [page, setPage] = useState('dashboard');
    const [strategies, setStrategies] = useState([]);
    const [symbols, setSymbols] = useState([]);
    const [loading, setLoading] = useState(true);
    const { prices: livePrices, connected: wsConnected } = useWebSocket();

    // Listen for auth expiration
    useEffect(() => {
        const handler = () => setUser(null);
        window.addEventListener('auth:expired', handler);
        return () => window.removeEventListener('auth:expired', handler);
    }, []);

    // Load data on login
    const loadData = useCallback(async () => {
        if (!user) return;
        setLoading(true);
        try {
            const [strats, syms] = await Promise.all([
                ApiClient.getStrategies(),
                ApiClient.getSymbols(),
            ]);
            setStrategies(strats);
            setSymbols(syms.length > 0 ? syms : ['SPY', 'AAPL', 'QQQ']);
        } catch (err) {
            console.error('Load error:', err);
        }
        setLoading(false);
    }, [user]);

    useEffect(() => { loadData(); }, [loadData]);

    const handleLogout = () => {
        ApiClient.logout();
        setUser(null);
    };

    // Not logged in → show auth
    if (!user) return React.createElement(AuthScreen, { onLogin: setUser });

    // Loading
    if (loading) return React.createElement('div', { className: 'loading-fullscreen' },
        React.createElement('div', { className: 'logo pulse' }, 'Q'),
        React.createElement('div', { style: { marginTop: 16, color: 'var(--text-muted)' } }, 'Loading QuantEdge...')
    );

    // Nav items
    const navItems = [
        { id: 'dashboard', label: 'Dashboard', icon: '◉' },
        { id: 'market', label: 'Market', icon: '📈' },
        { id: 'strategies', label: 'Strategies', icon: '⚡' },
        { id: 'backtest', label: 'Backtest', icon: '🔬' },
        { id: 'agents', label: 'Agents', icon: '🤖' },
        { id: 'ai', label: 'AI Intel', icon: '🧬' },
        { id: 'orders', label: 'Orders', icon: '📋' },
        { id: 'risk', label: 'Risk', icon: '🛡️' },
        { id: 'ml', label: 'ML', icon: '🧠' },
        { id: 'alerts', label: 'Alerts', icon: '🔔' },
    ];

    // Page content
    let content;
    switch (page) {
        case 'market':
            content = React.createElement(MarketDataView, { symbols });
            break;
        case 'strategies':
            content = React.createElement(StrategiesView, { strategies, onRefresh: loadData });
            break;
        case 'backtest':
            content = React.createElement(BacktestView, { strategies });
            break;
        case 'agents':
            content = React.createElement(AgentsView, { strategies });
            break;
        case 'ai':
            content = React.createElement(AIIntelligenceView, { strategies });
            break;
        case 'orders':
            content = React.createElement(OrdersView, { symbols, strategies });
            break;
        case 'risk':
            content = React.createElement(RiskView, { symbols });
            break;
        case 'ml':
            content = React.createElement(MLView, { symbols });
            break;
        case 'alerts':
            content = React.createElement(AlertsView);
            break;
        default:
            content = React.createElement(DashboardView, { strategies, symbols });
    }

    return React.createElement('div', { className: 'app-layout' },
        // Header
        React.createElement('header', { className: 'header' },
            React.createElement('div', { className: 'header-brand' },
                React.createElement('div', { className: 'logo pulse' }, 'Q'),
                React.createElement('div', null,
                    React.createElement('div', { className: 'header-title' }, 'QuantEdge'),
                    React.createElement('div', { className: 'header-subtitle' }, 'AI-Driven Trading Platform')
                )
            ),
            React.createElement('div', { className: 'header-user' },
                React.createElement('span', { className: 'user-name' }, user.name || user.email),
                React.createElement('button', { className: 'btn-logout', onClick: handleLogout }, 'Sign Out')
            )
        ),

        // Live Ticker
        React.createElement(LiveTickerStrip, { prices: livePrices, connected: wsConnected }),

        // Navigation
        React.createElement('nav', { className: 'main-nav' },
            navItems.map(n =>
                React.createElement('button', {
                    key: n.id,
                    className: 'nav-item ' + (page === n.id ? 'active' : ''),
                    onClick: () => setPage(n.id)
                }, React.createElement('span', { className: 'nav-icon' }, n.icon), ' ', n.label)
            )
        ),

        // Content
        React.createElement('main', { className: 'main-content' }, content)
    );
};

// ================================================================
// MOUNT
// ================================================================

ReactDOM.render(React.createElement(App), document.getElementById('root'));
