import { useMemo } from 'react';
import {
  Bar, BarChart, CartesianGrid, Cell as PieCell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Scatter,
  ScatterChart, Tooltip, XAxis, YAxis,
} from 'recharts';
import type { ChartType, QueryResult } from '@/lib/types';
import { formatCompact, formatNumber, prefersReducedMotion, SERIES_VARS } from '@/lib/utils';

const MAX_CATEGORIES = 30;
const MAX_PIE_SLICES = 5;

const tooltipStyle = {
  background: 'color-mix(in oklab, var(--surface) 88%, transparent)',
  backdropFilter: 'blur(12px)',
  border: '1px solid var(--border-strong)',
  borderRadius: 12,
  fontSize: 12,
  color: 'var(--fg)',
  boxShadow: '0 18px 40px -12px rgb(0 0 0 / 0.55)',
  padding: '8px 12px',
};
const ANIM = { duration: 900, easing: 'ease-out' as const };
const axisTick = { fontSize: 11, fill: 'var(--muted)' };

const toNumber = (v: unknown): number | null => {
  if (typeof v === 'number') return v;
  if (typeof v === 'string' && v.trim() !== '' && !Number.isNaN(Number(v))) return Number(v);
  return null;
};

export function ResultChart({ result, type, xColumn, yColumns, height = 320 }: {
  result: QueryResult; type: ChartType; xColumn?: string; yColumns: string[]; height?: number | string;
}) {
  const xi = result.columns.findIndex((c) => c.name === xColumn);
  const ys = yColumns.map((y) => ({ name: y, index: result.columns.findIndex((c) => c.name === y) })).filter((y) => y.index >= 0).slice(0, 4);
  const animate = !prefersReducedMotion();

  const data = useMemo(() => result.rows.map((row) => {
    const o: Record<string, string | number | null> = { __x: xi >= 0 ? String(row[xi] ?? '∅') : '' };
    ys.forEach((y) => { o[y.name] = toNumber(row[y.index]); });
    return o;
  }), [result.rows, xi, ys]);

  if (type === 'METRIC') {
    const row = result.rows[0] ?? [];
    const metrics = (ys.length ? ys : result.columns.map((c, index) => ({ name: c.name, index }))).slice(0, 4);
    return (
      <div className="grid h-full grid-cols-1 content-center gap-3 sm:grid-cols-2" style={{ minHeight: typeof height === 'number' ? Math.min(height, 160) : undefined }}>
        {metrics.map((m) => {
          const n = toNumber(row[m.index]);
          return (
            <div key={m.name} className="edge ombre-panel rounded-2xl border border-border p-5 animate-fade-up">
              <p className="eyebrow truncate">{m.name.replace(/_/g, ' ')}</p>
              <p className="mt-2 font-display text-5xl leading-none tracking-tight text-fg tabular-nums">{n === null ? String(row[m.index] ?? '—') : formatNumber(n, 2)}</p>
            </div>
          );
        })}
      </div>
    );
  }

  if (!ys.length || (type !== 'SCATTER' && xi < 0)) {
    return <p className="p-6 text-center text-sm text-muted">Choose an X column and at least one numeric Y column to draw a chart.</p>;
  }

  const legend = ys.length > 1 ? <Legend wrapperStyle={{ fontSize: 12, color: 'var(--fg-2)' }} /> : null;
  const grid = <CartesianGrid vertical={false} stroke="var(--chart-grid)" />;
  const truncatedNote = (n: number, max: number) => n > max && (
    <p className="mt-1 text-center text-[11px] text-muted">Showing the first {max} of {n} rows in the chart; the table has all rows.</p>
  );

  if (type === 'PIE') {
    const y = ys[0];
    const sorted = [...data].filter((d) => (d[y.name] as number) > 0).sort((a, b) => (b[y.name] as number) - (a[y.name] as number));
    const slices = sorted.slice(0, MAX_PIE_SLICES).map((d) => ({ name: String(d.__x), value: d[y.name] as number }));
    const rest = sorted.slice(MAX_PIE_SLICES).reduce((s, d) => s + (d[y.name] as number), 0);
    if (rest > 0) slices.push({ name: 'Other', value: rest });
    return (
      <ResponsiveContainer width="100%" height={height}>
        <PieChart>
          <Tooltip contentStyle={tooltipStyle} formatter={(v) => formatNumber(Number(v), 2)} />
          <Legend wrapperStyle={{ fontSize: 12, color: 'var(--fg-2)' }} />
          <Pie data={slices} dataKey="value" nameKey="name" innerRadius="52%" outerRadius="80%" paddingAngle={1}
            stroke="var(--surface)" strokeWidth={2} isAnimationActive={animate} animationDuration={ANIM.duration} animationEasing={ANIM.easing}>
            {slices.map((s, i) => <PieCell key={s.name} fill={s.name === 'Other' ? 'var(--muted)' : SERIES_VARS[i % SERIES_VARS.length]} />)}
          </Pie>
        </PieChart>
      </ResponsiveContainer>
    );
  }

  if (type === 'SCATTER') {
    const xIndex = xi >= 0 ? xi : ys[0].index;
    const yCol = ys.find((y) => y.index !== xIndex) ?? ys[0];
    const points = result.rows.map((r) => ({ x: toNumber(r[xIndex]), y: toNumber(r[yCol.index]) })).filter((p) => p.x !== null && p.y !== null);
    return (
      <ResponsiveContainer width="100%" height={height}>
        <ScatterChart margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
          {grid}
          <XAxis type="number" dataKey="x" name={result.columns[xIndex].name} tick={axisTick} tickFormatter={formatCompact} axisLine={false} tickLine={false} />
          <YAxis type="number" dataKey="y" name={yCol.name} tick={axisTick} tickFormatter={formatCompact} axisLine={false} tickLine={false} />
          <Tooltip contentStyle={tooltipStyle} cursor={{ strokeDasharray: '3 3' }} />
          <Scatter data={points} fill="var(--series-1)" stroke="var(--surface)" strokeWidth={2} isAnimationActive={animate} />
        </ScatterChart>
      </ResponsiveContainer>
    );
  }

  if (type === 'LINE') {
    return (
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 0 }}>
          {grid}
          <XAxis dataKey="__x" tick={axisTick} axisLine={false} tickLine={false} minTickGap={24} />
          <YAxis tick={axisTick} tickFormatter={formatCompact} axisLine={false} tickLine={false} width={56} />
          <Tooltip contentStyle={tooltipStyle} formatter={(v) => formatNumber(Number(v), 2)} labelStyle={{ color: 'var(--muted)' }} />
          {legend}
          {ys.map((y, i) => (
            <Line key={y.name} type="monotone" dataKey={y.name} stroke={SERIES_VARS[i]} strokeWidth={2} dot={data.length <= 40 ? { r: 3, strokeWidth: 0, fill: SERIES_VARS[i] } : false}
              activeDot={{ r: 5, stroke: 'var(--surface)', strokeWidth: 2 }} isAnimationActive={animate} animationDuration={1400}
              animationEasing="ease-in-out" connectNulls />
          ))}
        </LineChart>
      </ResponsiveContainer>
    );
  }

  const barData = data.slice(0, MAX_CATEGORIES);
  return (
    <div style={{ height }}>
      <ResponsiveContainer width="100%" height={data.length > MAX_CATEGORIES ? '92%' : '100%'}>
        <BarChart data={barData} margin={{ top: 8, right: 16, bottom: 4, left: 0 }} barGap={2} barCategoryGap="22%">
          <defs>
            {ys.map((y, i) => (
              <linearGradient key={y.name} id={`qa-bar-${i}`} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={SERIES_VARS[i]} stopOpacity={1} />
                <stop offset="100%" stopColor={SERIES_VARS[i]} stopOpacity={0.55} />
              </linearGradient>
            ))}
          </defs>
          {grid}
          <XAxis dataKey="__x" tick={axisTick} axisLine={false} tickLine={false} interval="preserveStartEnd" minTickGap={8} />
          <YAxis tick={axisTick} tickFormatter={formatCompact} axisLine={false} tickLine={false} width={56} />
          <Tooltip contentStyle={tooltipStyle} cursor={{ fill: 'var(--surface-3)', opacity: 0.5 }} formatter={(v) => formatNumber(Number(v), 2)} />
          {legend}
          {ys.map((y, i) => (
            <Bar key={y.name} dataKey={y.name} fill={`url(#qa-bar-${i})`} radius={[4, 4, 0, 0]} maxBarSize={48}
              isAnimationActive={animate} animationDuration={ANIM.duration} animationBegin={i * 120} animationEasing={ANIM.easing} />
          ))}
        </BarChart>
      </ResponsiveContainer>
      {truncatedNote(data.length, MAX_CATEGORIES)}
    </div>
  );
}
