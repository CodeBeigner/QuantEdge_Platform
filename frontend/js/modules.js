// ================================================================
// Recharts component aliases (from the global Recharts bundle)
// ================================================================
const {
    LineChart, Line, AreaChart, Area, BarChart, Bar,
    XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
    ReferenceLine, Legend
} = Recharts;

// Shared tooltip style
const tooltipStyle = {
    background: '#1a2234',
    border: '1px solid #2a3650',
    borderRadius: 8,
    fontFamily: 'IBM Plex Mono'
};

// ================================================================
// MODULE: MARKET MECHANICS
// ================================================================

const MarketMechanicsView = () => {
    const data = React.useMemo(() => generateMarketData(252), []);
    const [currentDay, setCurrentDay] = React.useState(200);
    const currentData = data[currentDay] || {};

    const identifyRegime = (vol) => {
        if (vol < 12) return { label: 'LOW VOL', color: 'var(--accent-green)' };
        if (vol < 20) return { label: 'NORMAL', color: 'var(--accent-blue)' };
        if (vol < 30) return { label: 'ELEVATED', color: 'var(--accent-yellow)' };
        return { label: 'CRISIS', color: 'var(--accent-red)' };
    };
    const regime = identifyRegime(currentData.volatility || 15);
    const recentData = data.slice(Math.max(0, currentDay - 60), currentDay + 1);

    return (
        React.createElement('div', { className: 'animate-in' },
            React.createElement('div', { className: 'metrics-row' },
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Price'),
                    React.createElement('div', { className: 'metric-value' }, '$' + currentData.price)
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Bid / Ask'),
                    React.createElement('div', { className: 'metric-value blue' }, currentData.bid + ' / ' + currentData.ask)
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Spread'),
                    React.createElement('div', { className: 'metric-value neutral' }, '$' + currentData.spread)
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Volume'),
                    React.createElement('div', { className: 'metric-value blue' }, (currentData.volume / 1e6).toFixed(1) + 'M')
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Regime'),
                    React.createElement('div', { className: 'metric-value', style: { color: regime.color } }, regime.label)
                )
            ),
            React.createElement('div', { className: 'insight-box' },
                React.createElement('p', null,
                    React.createElement('strong', null, 'Market Mechanics 101: '),
                    'Every trade has a buyer and a seller. The bid-ask spread is the market maker\'s compensation for providing liquidity. Tighter spreads = more liquid market. Watch how volatility regimes affect everything.'
                )
            ),
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, 'Price Action & Moving Averages'),
                React.createElement('div', { className: 'chart-container' },
                    React.createElement(ResponsiveContainer, { width: '100%', height: 320 },
                        React.createElement(LineChart, { data: recentData },
                            React.createElement(CartesianGrid, { strokeDasharray: '3 3', stroke: '#1e293b' }),
                            React.createElement(XAxis, { dataKey: 'day', tick: { fill: '#64748b', fontSize: 11 } }),
                            React.createElement(YAxis, { tick: { fill: '#64748b', fontSize: 11 }, domain: ['auto', 'auto'] }),
                            React.createElement(Tooltip, { contentStyle: tooltipStyle, labelStyle: { color: '#94a3b8' } }),
                            React.createElement(Line, { type: 'monotone', dataKey: 'price', stroke: '#00ff88', strokeWidth: 2, dot: false, name: 'Price' }),
                            React.createElement(Line, { type: 'monotone', dataKey: 'sma50', stroke: '#fbbf24', strokeWidth: 1.5, dot: false, name: 'SMA 50', strokeDasharray: '4 4' }),
                            React.createElement(Line, { type: 'monotone', dataKey: 'sma200', stroke: '#ef4444', strokeWidth: 1.5, dot: false, name: 'SMA 200', strokeDasharray: '8 4' }),
                            React.createElement(Legend, { wrapperStyle: { fontFamily: 'IBM Plex Mono', fontSize: 11 } })
                        )
                    )
                )
            ),
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, 'Volatility & Volume'),
                React.createElement('div', { className: 'chart-container' },
                    React.createElement(ResponsiveContainer, { width: '100%', height: 200 },
                        React.createElement(AreaChart, { data: recentData },
                            React.createElement(CartesianGrid, { strokeDasharray: '3 3', stroke: '#1e293b' }),
                            React.createElement(XAxis, { dataKey: 'day', tick: { fill: '#64748b', fontSize: 11 } }),
                            React.createElement(YAxis, { tick: { fill: '#64748b', fontSize: 11 } }),
                            React.createElement(Tooltip, { contentStyle: tooltipStyle }),
                            React.createElement(Area, { type: 'monotone', dataKey: 'volatility', stroke: '#3b82f6', fill: '#3b82f6', fillOpacity: 0.15, name: 'Volatility %' })
                        )
                    )
                )
            ),
            React.createElement('div', { className: 'slider-container' },
                React.createElement('span', { className: 'slider-label' }, 'Day ' + (currentDay + 1) + ' / ' + data.length),
                React.createElement('input', { type: 'range', min: 0, max: data.length - 1, value: currentDay, onChange: e => setCurrentDay(+e.target.value) })
            )
        )
    );
};

// ================================================================
// MODULE: MOMENTUM
// ================================================================

const MomentumModule = () => {
    const data = React.useMemo(() => generateMarketData(252), []);
    const [currentDay, setCurrentDay] = React.useState(220);
    const currentData = data[currentDay] || {};
    const signal = currentData.price > currentData.sma200 ? 'LONG' : 'SHORT';
    const recentData = data.slice(Math.max(0, currentDay - 80), currentDay + 1);

    return (
        React.createElement('div', { className: 'animate-in' },
            React.createElement('div', { className: 'metrics-row' },
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Price'),
                    React.createElement('div', { className: 'metric-value' }, '$' + currentData.price)
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'SMA 50'),
                    React.createElement('div', { className: 'metric-value neutral' }, '$' + currentData.sma50)
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'SMA 200'),
                    React.createElement('div', { className: 'metric-value blue' }, '$' + currentData.sma200)
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Signal'),
                    React.createElement('div', null,
                        React.createElement('span', { className: 'signal-badge ' + signal.toLowerCase() }, '⚡ ' + signal)
                    )
                )
            ),
            React.createElement('div', { className: 'insight-box' },
                React.createElement('p', null,
                    React.createElement('strong', null, 'Why momentum works: '),
                    'Markets trend because information takes time to fully price in. Institutional investors rebalance slowly. When the fast SMA (50) crosses above the slow SMA (200), it\'s a "golden cross" — historically bullish. The opposite is a "death cross".'
                )
            ),
            React.createElement('div', { className: 'formula' }, 'Signal = Price > SMA(200) ? LONG : SHORT'),
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, 'Momentum: Price vs Moving Averages'),
                React.createElement('div', { className: 'chart-container' },
                    React.createElement(ResponsiveContainer, { width: '100%', height: 350 },
                        React.createElement(LineChart, { data: recentData },
                            React.createElement(CartesianGrid, { strokeDasharray: '3 3', stroke: '#1e293b' }),
                            React.createElement(XAxis, { dataKey: 'day', tick: { fill: '#64748b', fontSize: 11 } }),
                            React.createElement(YAxis, { tick: { fill: '#64748b', fontSize: 11 }, domain: ['auto', 'auto'] }),
                            React.createElement(Tooltip, { contentStyle: tooltipStyle }),
                            React.createElement(Line, { type: 'monotone', dataKey: 'price', stroke: '#00ff88', strokeWidth: 2, dot: false, name: 'Price' }),
                            React.createElement(Line, { type: 'monotone', dataKey: 'sma50', stroke: '#fbbf24', strokeWidth: 1.5, dot: false, name: 'SMA 50', strokeDasharray: '4 4' }),
                            React.createElement(Line, { type: 'monotone', dataKey: 'sma200', stroke: '#ef4444', strokeWidth: 1.5, dot: false, name: 'SMA 200', strokeDasharray: '8 4' }),
                            React.createElement(Legend, { wrapperStyle: { fontFamily: 'IBM Plex Mono', fontSize: 11 } })
                        )
                    )
                )
            ),
            React.createElement('div', { className: 'slider-container' },
                React.createElement('span', { className: 'slider-label' }, 'Day ' + (currentDay + 1)),
                React.createElement('input', { type: 'range', min: 0, max: data.length - 1, value: currentDay, onChange: e => setCurrentDay(+e.target.value) })
            )
        )
    );
};

// ================================================================
// MODULE: VOLATILITY
// ================================================================

const VolatilityModule = () => {
    const data = React.useMemo(() => generateMarketData(252), []);
    const [currentDay, setCurrentDay] = React.useState(200);
    const [targetRisk, setTargetRisk] = React.useState(10000);
    const currentData = data[currentDay] || {};
    const currentVol = currentData.rollingVol || 15;
    const positionSize = Math.floor(targetRisk / (currentVol / 100));
    const recentData = data.slice(Math.max(0, currentDay - 80), currentDay + 1);

    return (
        React.createElement('div', { className: 'animate-in' },
            React.createElement('div', { className: 'metrics-row' },
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Current Volatility'),
                    React.createElement('div', { className: 'metric-value ' + (currentVol > 25 ? 'negative' : '') }, currentVol + '%')
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Position Size'),
                    React.createElement('div', { className: 'metric-value blue' }, positionSize.toLocaleString() + ' units')
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Target Risk'),
                    React.createElement('div', { className: 'metric-value neutral' }, '$' + targetRisk.toLocaleString())
                )
            ),
            React.createElement('div', { className: 'insight-box' },
                React.createElement('p', null,
                    React.createElement('strong', null, 'Key insight: '),
                    'Higher volatility → smaller position. This is how institutional traders maintain consistent risk. When vol spikes from 15% to 30%, your position halves. You\'re not predicting direction — you\'re managing uncertainty.'
                )
            ),
            React.createElement('div', { className: 'formula' }, 'Position Size = Target Risk $ ÷ (Volatility% / 100)'),
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, 'Rolling 20-Day Volatility'),
                React.createElement('div', { className: 'chart-container' },
                    React.createElement(ResponsiveContainer, { width: '100%', height: 300 },
                        React.createElement(AreaChart, { data: recentData },
                            React.createElement(CartesianGrid, { strokeDasharray: '3 3', stroke: '#1e293b' }),
                            React.createElement(XAxis, { dataKey: 'day', tick: { fill: '#64748b', fontSize: 11 } }),
                            React.createElement(YAxis, { tick: { fill: '#64748b', fontSize: 11 } }),
                            React.createElement(Tooltip, { contentStyle: tooltipStyle }),
                            React.createElement(ReferenceLine, { y: 20, stroke: '#fbbf24', strokeDasharray: '4 4', label: { value: 'Avg Vol', fill: '#fbbf24', fontSize: 10 } }),
                            React.createElement(Area, { type: 'monotone', dataKey: 'rollingVol', stroke: '#3b82f6', fill: '#3b82f6', fillOpacity: 0.15, name: 'Volatility %' })
                        )
                    )
                )
            ),
            React.createElement('div', { className: 'slider-container' },
                React.createElement('span', { className: 'slider-label' }, 'Day ' + (currentDay + 1)),
                React.createElement('input', { type: 'range', min: 0, max: data.length - 1, value: currentDay, onChange: e => setCurrentDay(+e.target.value) })
            ),
            React.createElement('div', { className: 'slider-container' },
                React.createElement('span', { className: 'slider-label' }, 'Risk $' + targetRisk.toLocaleString()),
                React.createElement('input', { type: 'range', min: 1000, max: 50000, step: 1000, value: targetRisk, onChange: e => setTargetRisk(+e.target.value) })
            )
        )
    );
};

// ================================================================
// MODULE: MACRO
// ================================================================

const MacroModule = () => {
    const [interestRate, setInterestRate] = React.useState(3.0);

    const calcPerf = (rate) => ({
        growth: +(20 - rate * 4).toFixed(1),
        value: +(5 + rate * 1.5).toFixed(1),
        bonds: +(8 - rate * 2).toFixed(1),
        commodities: +(3 + rate * 1.2).toFixed(1),
    });
    const perf = calcPerf(interestRate);
    const regime = interestRate > 4 ? 'TIGHTENING' : interestRate < 2 ? 'EASING' : 'NEUTRAL';
    const regimeColor = regime === 'TIGHTENING' ? 'var(--accent-red)' : regime === 'EASING' ? 'var(--accent-green)' : 'var(--accent-yellow)';

    const chartData = [
        { asset: 'Growth', value: perf.growth, fill: perf.growth >= 0 ? '#00ff88' : '#ef4444' },
        { asset: 'Value', value: perf.value, fill: perf.value >= 0 ? '#00ff88' : '#ef4444' },
        { asset: 'Bonds', value: perf.bonds, fill: perf.bonds >= 0 ? '#3b82f6' : '#ef4444' },
        { asset: 'Commodities', value: perf.commodities, fill: perf.commodities >= 0 ? '#fbbf24' : '#ef4444' },
    ];

    return (
        React.createElement('div', { className: 'animate-in' },
            React.createElement('div', { className: 'metrics-row' },
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Interest Rate'),
                    React.createElement('div', { className: 'metric-value neutral' }, interestRate.toFixed(1) + '%')
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Macro Regime'),
                    React.createElement('div', { className: 'metric-value', style: { color: regimeColor } }, regime)
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Best Asset'),
                    React.createElement('div', { className: 'metric-value' }, interestRate > 3 ? 'Value' : 'Growth')
                )
            ),
            React.createElement('div', { className: 'insight-box' },
                React.createElement('p', null,
                    React.createElement('strong', null, 'Macro strategy: '),
                    'When central banks raise rates, growth stocks suffer (future earnings are discounted more heavily), while value and commodities benefit. During easing, capital floods into growth. This is how macro hedge funds allocate.'
                )
            ),
            React.createElement('div', { className: 'formula' }, 'Growth Impact ≈ 20% − (Rate × 4)'),
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, 'Asset Performance at ' + interestRate.toFixed(1) + '% Rate'),
                React.createElement('div', { className: 'chart-container' },
                    React.createElement(ResponsiveContainer, { width: '100%', height: 280 },
                        React.createElement(BarChart, { data: chartData },
                            React.createElement(CartesianGrid, { strokeDasharray: '3 3', stroke: '#1e293b' }),
                            React.createElement(XAxis, { dataKey: 'asset', tick: { fill: '#94a3b8', fontSize: 12, fontFamily: 'IBM Plex Mono' } }),
                            React.createElement(YAxis, { tick: { fill: '#64748b', fontSize: 11 } }),
                            React.createElement(Tooltip, { contentStyle: tooltipStyle }),
                            React.createElement(ReferenceLine, { y: 0, stroke: '#64748b' }),
                            React.createElement(Bar, { dataKey: 'value', name: 'Return %' },
                                chartData.map((entry, i) =>
                                    React.createElement(Recharts.Cell, { key: i, fill: entry.fill })
                                )
                            )
                        )
                    )
                )
            ),
            React.createElement('div', { className: 'slider-container' },
                React.createElement('span', { className: 'slider-label' }, 'Rate ' + interestRate.toFixed(1) + '%'),
                React.createElement('input', { type: 'range', min: 0, max: 6, step: 0.25, value: interestRate, onChange: e => setInterestRate(+e.target.value) })
            )
        )
    );
};

// ================================================================
// MODULE: CORRELATION
// ================================================================

const CorrelationModule = () => {
    const data = React.useMemo(() => generateMarketData(252), []);
    const [currentDay, setCurrentDay] = React.useState(200);
    const currentCorr = data[currentDay]?.correlation || 0;
    const diversification = Math.abs(currentCorr) < 0.3 ? 'STRONG' : Math.abs(currentCorr) < 0.6 ? 'MODERATE' : 'WEAK';
    const divColor = diversification === 'STRONG' ? 'var(--accent-green)' : diversification === 'MODERATE' ? 'var(--accent-yellow)' : 'var(--accent-red)';
    const recentData = data.slice(Math.max(0, currentDay - 80), currentDay + 1);

    return (
        React.createElement('div', { className: 'animate-in' },
            React.createElement('div', { className: 'metrics-row' },
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Correlation'),
                    React.createElement('div', { className: 'metric-value ' + (currentCorr > 0.5 ? 'negative' : currentCorr < -0.2 ? '' : 'neutral') }, currentCorr.toFixed(3))
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Diversification'),
                    React.createElement('div', { className: 'metric-value', style: { color: divColor } }, diversification)
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Risk Status'),
                    React.createElement('div', { className: 'metric-value', style: { color: Math.abs(currentCorr) > 0.7 ? 'var(--accent-red)' : 'var(--accent-green)' } },
                        Math.abs(currentCorr) > 0.7 ? '⚠ HIGH' : '✓ OK'
                    )
                )
            ),
            React.createElement('div', { className: 'insight-box' },
                React.createElement('p', null,
                    React.createElement('strong', null, 'Correlation matters: '),
                    'When assets correlate highly (|ρ| > 0.7), your portfolio has hidden concentration risk — two "different" assets move together. Low correlation (|ρ| < 0.3) means genuine diversification. Correlations shift in crises — often jumping toward 1.0 exactly when you need diversification most.'
                )
            ),
            React.createElement('div', { className: 'formula' }, 'ρ(A,B) = Cov(A,B) / (σ_A × σ_B)'),
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, '60-Day Rolling Correlation'),
                React.createElement('div', { className: 'chart-container' },
                    React.createElement(ResponsiveContainer, { width: '100%', height: 300 },
                        React.createElement(LineChart, { data: recentData },
                            React.createElement(CartesianGrid, { strokeDasharray: '3 3', stroke: '#1e293b' }),
                            React.createElement(XAxis, { dataKey: 'day', tick: { fill: '#64748b', fontSize: 11 } }),
                            React.createElement(YAxis, { domain: [-1, 1], tick: { fill: '#64748b', fontSize: 11 } }),
                            React.createElement(Tooltip, { contentStyle: tooltipStyle }),
                            React.createElement(ReferenceLine, { y: 0, stroke: '#64748b', strokeDasharray: '4 4' }),
                            React.createElement(ReferenceLine, { y: 0.7, stroke: '#ef4444', strokeDasharray: '4 4', label: { value: 'Danger', fill: '#ef4444', fontSize: 10 } }),
                            React.createElement(ReferenceLine, { y: -0.7, stroke: '#ef4444', strokeDasharray: '4 4' }),
                            React.createElement(Line, { type: 'monotone', dataKey: 'correlation', stroke: '#3b82f6', strokeWidth: 2.5, dot: false, name: 'Correlation' })
                        )
                    )
                )
            ),
            React.createElement('div', { className: 'slider-container' },
                React.createElement('span', { className: 'slider-label' }, 'Day ' + (currentDay + 1)),
                React.createElement('input', { type: 'range', min: 0, max: data.length - 1, value: currentDay, onChange: e => setCurrentDay(+e.target.value) })
            )
        )
    );
};

// ================================================================
// MODULE: REGIME DETECTION
// ================================================================

const RegimeModule = () => {
    const [regime, setRegime] = React.useState('risk-on');

    const allocations = {
        'risk-on': { equity: 70, bonds: 20, commodities: 5, cash: 5 },
        'risk-off': { equity: 30, bonds: 50, commodities: 5, cash: 15 },
        'inflationary': { equity: 40, bonds: 10, commodities: 40, cash: 10 },
        'recessionary': { equity: 25, bonds: 60, commodities: 5, cash: 10 },
    };
    const alloc = allocations[regime];

    const colors = { equity: '#00ff88', bonds: '#3b82f6', commodities: '#fbbf24', cash: '#94a3b8' };
    const chartData = Object.entries(alloc).map(([k, v]) => ({
        name: k.charAt(0).toUpperCase() + k.slice(1),
        value: v,
        fill: colors[k]
    }));

    const regimeIndicators = {
        'risk-on': { vix: '< 20', curve: 'Positive', signal: 'BUY equities' },
        'risk-off': { vix: '> 25', curve: 'Flat', signal: 'SELL equities' },
        'inflationary': { vix: 'Any', curve: 'Steepening', signal: 'BUY commodities' },
        'recessionary': { vix: '> 30', curve: 'Inverted', signal: 'DEFENSIVE' },
    };
    const ind = regimeIndicators[regime];

    return (
        React.createElement('div', { className: 'animate-in' },
            React.createElement('div', { className: 'metrics-row' },
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'VIX Level'),
                    React.createElement('div', { className: 'metric-value neutral' }, ind.vix)
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Yield Curve'),
                    React.createElement('div', { className: 'metric-value blue' }, ind.curve)
                ),
                React.createElement('div', { className: 'metric' },
                    React.createElement('div', { className: 'metric-label' }, 'Action'),
                    React.createElement('div', { className: 'metric-value' }, ind.signal)
                )
            ),
            React.createElement('div', { className: 'insight-box' },
                React.createElement('p', null,
                    React.createElement('strong', null, 'Regime detection: '),
                    'Markets cycle through distinct states. Institutional allocators use VIX, yield curves, and inflation to classify regimes, then adjust allocations mechanically. This removes emotion from the process.'
                )
            ),
            React.createElement('div', { className: 'regime-pills' },
                Object.keys(allocations).map(r =>
                    React.createElement('button', {
                        key: r,
                        className: 'regime-pill ' + (regime === r ? 'active' : ''),
                        onClick: () => setRegime(r)
                    }, r.replace('-', ' ').toUpperCase())
                )
            ),
            React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, 'Allocation: ' + regime.replace('-', ' ').toUpperCase() + ' Regime'),
                React.createElement('div', { className: 'chart-container' },
                    React.createElement(ResponsiveContainer, { width: '100%', height: 280 },
                        React.createElement(BarChart, { data: chartData },
                            React.createElement(CartesianGrid, { strokeDasharray: '3 3', stroke: '#1e293b' }),
                            React.createElement(XAxis, { dataKey: 'name', tick: { fill: '#94a3b8', fontSize: 12, fontFamily: 'IBM Plex Mono' } }),
                            React.createElement(YAxis, { domain: [0, 80], tick: { fill: '#64748b', fontSize: 11 }, tickFormatter: v => v + '%' }),
                            React.createElement(Tooltip, { contentStyle: tooltipStyle, formatter: v => v + '%' }),
                            React.createElement(Bar, { dataKey: 'value', name: 'Allocation %' },
                                chartData.map((entry, i) =>
                                    React.createElement(Recharts.Cell, { key: i, fill: entry.fill })
                                )
                            )
                        )
                    )
                )
            )
        )
    );
};
