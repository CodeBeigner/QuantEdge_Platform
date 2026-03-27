import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  createChart,
  ColorType,
  CrosshairMode,
  CandlestickSeries,
  HistogramSeries as VolumeSeries,
} from 'lightweight-charts';
import type { IChartApi, ISeriesApi, Time, CandlestickData, HistogramData } from 'lightweight-charts';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';

const PERIODS = [
  { label: '30d', days: 30 },
  { label: '60d', days: 60 },
  { label: '120d', days: 120 },
  { label: '252d', days: 252 },
] as const;

const panelCard = 'qe-card';

function toChartTime(dateStr: string): Time {
  const d = dateStr.slice(0, 10);
  return d as Time;
}

function sma(values: number[], period: number): (number | null)[] {
  const out: (number | null)[] = [];
  for (let i = 0; i < values.length; i++) {
    if (i < period - 1) {
      out.push(null);
      continue;
    }
    let sum = 0;
    for (let j = 0; j < period; j++) sum += values[i - j];
    out.push(sum / period);
  }
  return out;
}

function StatRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-[#2a3650]/80 py-2.5 last:border-0">
      <span className="font-mono text-[10px] uppercase tracking-wider text-[#64748b]">
        {label}
      </span>
      <span className="tabular-nums text-sm font-medium text-[#f1f5f9]">{value}</span>
    </div>
  );
}

export default function MarketPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const chartApiRef = useRef<IChartApi | null>(null);
  const candleRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const volumeRef = useRef<ISeriesApi<'Histogram'> | null>(null);

  const { data: symbols = [] } = useQuery({
    queryKey: ['symbols'],
    queryFn: () => api.getSymbols(),
  });

  const urlSymbol = searchParams.get('symbol')?.trim() || '';
  const [days, setDays] = useState<number>(252);

  const effectiveSymbol = urlSymbol || symbols[0] || 'SPY';

  const { data: prices = [], isFetching } = useQuery({
    queryKey: ['prices', effectiveSymbol, days],
    queryFn: () => api.getPrices(effectiveSymbol, days),
    enabled: Boolean(effectiveSymbol),
  });

  const candleData: CandlestickData[] = useMemo(
    () =>
      prices.map((p) => ({
        time: toChartTime(p.date),
        open: p.open,
        high: p.high,
        low: p.low,
        close: p.close,
      })),
    [prices],
  );

  const volumeHistogram: HistogramData[] = useMemo(
    () =>
      prices.map((p) => ({
        time: toChartTime(p.date),
        value: p.volume,
        color: p.close >= p.open ? 'rgba(0, 228, 121, 0.45)' : 'rgba(255, 180, 171, 0.45)',
      })),
    [prices],
  );

  const volumeChartData = useMemo(
    () =>
      prices.map((p) => ({
        date: p.date.slice(0, 10),
        volume: p.volume,
        fill: p.close >= p.open ? '#00e479' : '#ffb4ab',
      })),
    [prices],
  );

  const summary = useMemo(() => {
    if (!prices.length) return null;
    const last = prices[prices.length - 1];
    const closes = prices.map((p) => p.close);
    const s20 = sma(closes, 20);
    const s50 = sma(closes, 50);
    const sma20 = s20[s20.length - 1];
    const sma50 = s50[s50.length - 1];
    return {
      open: last.open,
      high: last.high,
      low: last.low,
      close: last.close,
      volume: last.volume,
      sma20,
      sma50,
    };
  }, [prices]);

  const initChart = useCallback(() => {
    const el = chartContainerRef.current;
    if (!el) return;

    chartApiRef.current?.remove();
    chartApiRef.current = null;
    candleRef.current = null;
    volumeRef.current = null;

    const chart = createChart(el, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: '#0e1320' },
        textColor: '#c2c6d6',
        attributionLogo: false,
      },
      grid: {
        vertLines: { color: '#1a1f2d' },
        horzLines: { color: '#1a1f2d' },
      },
      crosshair: {
        mode: CrosshairMode.Normal,
      },
      rightPriceScale: {
        borderColor: '#424754',
      },
      timeScale: {
        borderColor: '#424754',
        timeVisible: true,
        secondsVisible: false,
      },
    });

    const candle = chart.addSeries(CandlestickSeries, {
      upColor: '#00e479',
      downColor: '#ffb4ab',
      borderVisible: false,
      wickUpColor: '#00e479',
      wickDownColor: '#ffb4ab',
    });

    chart.addPane();
    const vol = chart.addSeries(
      VolumeSeries,
      {
        color: '#4d8eff',
        priceFormat: { type: 'volume' },
      },
      1,
    );
    vol.priceScale().applyOptions({
      scaleMargins: { top: 0.85, bottom: 0 },
    });

    chartApiRef.current = chart;
    candleRef.current = candle;
    volumeRef.current = vol;
  }, []);

  useEffect(() => {
    initChart();
    return () => {
      chartApiRef.current?.remove();
      chartApiRef.current = null;
      candleRef.current = null;
      volumeRef.current = null;
    };
  }, [initChart]);

  useEffect(() => {
    if (!candleRef.current || !chartApiRef.current) return;
    candleRef.current.setData(candleData);
    volumeRef.current?.setData(volumeHistogram);
    chartApiRef.current.timeScale().fitContent();
  }, [candleData, volumeHistogram]);

  const selectSymbol = (s: string) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('symbol', s);
      return next;
    });
  };

  const fmt = (n: number, opts?: Intl.NumberFormatOptions) =>
    new Intl.NumberFormat('en-US', { maximumFractionDigits: 2, ...opts }).format(n);

  return (
    <div className="text-[#f1f5f9]">
      <div className="mx-auto max-w-[1600px]">
        <PageHeader title="Market Data" subtitle="OHLCV // SYNCED VOLUME PANE // SUMMARY STATISTICS" />

        <div className="mb-4 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0 flex-1">
            <p className="mb-2 font-mono text-[10px] uppercase tracking-wider text-[#64748b]">
              Symbol
            </p>
            <div className="flex flex-wrap gap-2">
              {(symbols.length ? symbols : [effectiveSymbol]).map((s) => (
                <button
                  key={s}
                  type="button"
                  onClick={() => selectSymbol(s)}
                  className={`rounded-lg border px-3 py-1.5 text-sm font-medium transition ${
                    s === effectiveSymbol
                      ? 'border-[#3b82f6] bg-[#3b82f6]/15 text-[#f1f5f9]'
                      : 'border-[#2a3650] bg-[#1a2234] text-[#94a3b8] hover:border-[#3b82f6]'
                  }`}
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
          <div>
            <p className="mb-2 font-mono text-[10px] uppercase tracking-wider text-[#64748b]">
              Period
            </p>
            <div className="flex flex-wrap gap-2">
              {PERIODS.map((p) => (
                <button
                  key={p.label}
                  type="button"
                  onClick={() => setDays(p.days)}
                  className={`rounded-lg border px-3 py-1.5 text-sm font-medium transition ${
                    days === p.days
                      ? 'border-[#3b82f6] bg-[#3b82f6]/15 text-[#f1f5f9]'
                      : 'border-[#2a3650] bg-[#1a2234] text-[#94a3b8] hover:border-[#3b82f6]'
                  }`}
                >
                  {p.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="flex flex-col gap-6 xl:flex-row">
          <div className="min-w-0 flex-1 space-y-4">
            <div className={`${panelCard} relative overflow-hidden p-0`}>
              {isFetching ? (
                <div className="absolute right-3 top-3 z-10 rounded-md bg-[#1a2234]/90 px-2 py-1 text-xs text-[#94a3b8]">
                  Loading…
                </div>
              ) : null}
              <div ref={chartContainerRef} className="h-[420px] w-full" />
            </div>

            <div className={panelCard}>
              <h2 className="gradient-text mb-3 text-sm font-semibold">Volume</h2>
              <div className="h-[180px] w-full min-h-[160px]">
                {volumeChartData.length > 0 ? (
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={volumeChartData} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#1a2234" vertical={false} />
                      <XAxis
                        dataKey="date"
                        tick={{ fill: '#64748b', fontSize: 10 }}
                        axisLine={{ stroke: '#2a3650' }}
                        tickLine={false}
                        interval="preserveStartEnd"
                      />
                      <YAxis
                        tick={{ fill: '#64748b', fontSize: 10 }}
                        axisLine={false}
                        tickLine={false}
                        width={56}
                        tickFormatter={(v) =>
                          v >= 1e6 ? `${(v / 1e6).toFixed(1)}M` : v >= 1e3 ? `${(v / 1e3).toFixed(0)}K` : `${v}`
                        }
                      />
                      <Tooltip
                        contentStyle={{
                          background: '#1a2234',
                          border: '1px solid #2a3650',
                          borderRadius: 8,
                          color: '#f1f5f9',
                        }}
                        labelStyle={{ color: '#94a3b8' }}
                        formatter={(value) => [
                          fmt(Number(value ?? 0), { maximumFractionDigits: 0 }),
                          'Volume',
                        ]}
                      />
                      <Bar dataKey="volume" radius={[2, 2, 0, 0]} fill="#3b82f6">
                        {volumeChartData.map((entry, index) => (
                          <Cell key={`cell-${entry.date}-${index}`} fill={entry.fill} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                ) : (
                  <p className="py-8 text-center text-sm text-[#64748b]">No volume data</p>
                )}
              </div>
            </div>
          </div>

          <aside className="w-full shrink-0 xl:w-80">
            <div className={panelCard}>
              <h2 className="gradient-text mb-1 text-sm font-semibold">Market summary</h2>
              <p className="mb-3 font-mono text-[10px] uppercase tracking-wider text-[#64748b]">
                {effectiveSymbol} · last bar
              </p>
              {!summary ? (
                <p className="text-sm text-[#64748b]">No data for this range.</p>
              ) : (
                <div>
                  <StatRow label="Open" value={fmt(summary.open)} />
                  <StatRow label="High" value={fmt(summary.high)} />
                  <StatRow label="Low" value={fmt(summary.low)} />
                  <StatRow label="Close" value={fmt(summary.close)} />
                  <StatRow
                    label="Volume"
                    value={fmt(summary.volume, { maximumFractionDigits: 0 })}
                  />
                  <StatRow
                    label="SMA 20"
                    value={summary.sma20 != null ? fmt(summary.sma20) : '—'}
                  />
                  <StatRow
                    label="SMA 50"
                    value={summary.sma50 != null ? fmt(summary.sma50) : '—'}
                  />
                </div>
              )}
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}
