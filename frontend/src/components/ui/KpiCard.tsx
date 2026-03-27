import { useEffect, useRef } from 'react';

interface KpiCardProps {
  label: string;
  value: string;
  change?: string;
  positive?: boolean;
  sparkData?: number[];
  delay?: number;
}

function drawSparkline(canvas: HTMLCanvasElement, data: number[], positive: boolean) {
  const ctx = canvas.getContext('2d');
  if (!ctx || data.length < 2) return;

  const dpr = window.devicePixelRatio || 1;
  const rect = canvas.getBoundingClientRect();
  canvas.width = rect.width * dpr;
  canvas.height = rect.height * dpr;
  ctx.scale(dpr, dpr);

  const w = rect.width;
  const h = rect.height;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const pad = 2;

  ctx.beginPath();
  ctx.strokeStyle = positive ? '#00e479' : '#ffb4ab';
  ctx.lineWidth = 1.5;
  ctx.lineJoin = 'round';

  for (let i = 0; i < data.length; i++) {
    const x = (i / (data.length - 1)) * w;
    const y = h - pad - ((data[i] - min) / range) * (h - pad * 2);
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  }
  ctx.stroke();
}

export function KpiCard({ label, value, change, positive = true, sparkData, delay = 0 }: KpiCardProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (canvasRef.current && sparkData && sparkData.length > 1) {
      drawSparkline(canvasRef.current, sparkData, positive);
    }
  }, [sparkData, positive]);

  return (
    <div
      className="kpi animate-in"
      style={{ animationDelay: `${delay}ms` }}
    >
      <div className="kpi-label">{label}</div>
      <div className="kpi-value">{value}</div>
      {change && (
        <div className={`kpi-change ${positive ? 'positive' : 'negative'}`}>
          <span className="material-icons-outlined" style={{ fontSize: 14 }}>
            {positive ? 'trending_up' : 'trending_down'}
          </span>
          {change}
        </div>
      )}
      {sparkData && sparkData.length > 1 && (
        <div className="kpi-sparkline">
          <canvas ref={canvasRef} style={{ width: '100%', height: '40px', display: 'block' }} />
        </div>
      )}
    </div>
  );
}
