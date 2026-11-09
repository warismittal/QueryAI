import { useEffect, useState, type ReactNode } from 'react';
import { BarChart3, Download, FileText, Info, Lightbulb, LockKeyhole, Table2 } from 'lucide-react';
import type { ChartRecommendation, ChartType, Interpretation, QueryResult } from '@/lib/types';
import { downloadCsv } from '@/lib/csv';
import { formatDuration } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Select } from '@/components/ui/input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ResultChart } from './ResultChart';
import { ResultsTable, useTableState } from './ResultsTable';

const CHART_TYPES: ChartType[] = ['BAR', 'LINE', 'PIE', 'SCATTER', 'METRIC'];

export function ResultsPanel({ result, chart, interpretation, queryRunId, filename = 'queryai-result', extraSummary }: {
  result: QueryResult;
  chart?: ChartRecommendation;
  interpretation?: Interpretation;
  queryRunId?: number;
  filename?: string;
  extraSummary?: ReactNode;
}) {
  const table = useTableState(result);
  const numeric = result.columns.filter((c) => c.category === 'NUMBER').map((c) => c.name);
  const [type, setType] = useState<ChartType>(chart && chart.type !== 'TABLE' ? chart.type : 'BAR');
  const [x, setX] = useState<string | undefined>(chart?.xColumn ?? result.columns[0]?.name);
  const [ys, setYs] = useState<string[]>(chart?.yColumns?.length ? chart.yColumns : numeric.slice(0, 1));
  const [tab, setTab] = useState<string>(chart && chart.type !== 'TABLE' ? 'chart' : 'table');

  useEffect(() => {
    setType(chart && chart.type !== 'TABLE' ? chart.type : 'BAR');
    setX(chart?.xColumn ?? result.columns[0]?.name);
    setYs(chart?.yColumns?.length ? chart.yColumns : result.columns.filter((c) => c.category === 'NUMBER').map((c) => c.name).slice(0, 1));
    setTab(chart && chart.type !== 'TABLE' ? 'chart' : 'table');
  }, [result, chart]);

  const exportCsv = () => {
    const cols = table.visible;
    downloadCsv(filename, cols.map((c) => c.name), table.rows.map((r) => cols.map((c) => r[c.index])));
  };

  return (
    <Tabs value={tab} onValueChange={setTab} className="flex h-full min-h-0 flex-col">
      <div className="flex flex-wrap items-center gap-2 border-b border-border px-3 py-2">
        <TabsList>
          <TabsTrigger value="table"><Table2 /> Table</TabsTrigger>
          <TabsTrigger value="chart"><BarChart3 /> Chart</TabsTrigger>
          <TabsTrigger value="summary"><Lightbulb /> Summary</TabsTrigger>
          <TabsTrigger value="meta"><Info /> Metadata</TabsTrigger>
        </TabsList>
        <div className="ml-auto flex items-center gap-2">
          <Badge>{result.rowCount.toLocaleString()} rows</Badge>
          {result.truncated && <Badge tone="warning">truncated at {result.maxRows.toLocaleString()}</Badge>}
          {!!result.maskedColumns?.length && (
            <Badge tone="accent" title={`Personal data masked: ${result.maskedColumns.join(', ')}`}>
              <LockKeyhole /> {result.maskedColumns.length} masked
            </Badge>
          )}
          <Badge>{formatDuration(result.durationMs)}</Badge>
          <Button size="sm" variant="secondary" onClick={exportCsv} disabled={!result.rows.length}><Download /> CSV</Button>
        </div>
      </div>

      <TabsContent value="table" className="flex min-h-0 flex-1 flex-col">
        <ResultsTable result={result} state={table} />
      </TabsContent>

      <TabsContent value="chart" className="min-h-0 flex-1 overflow-auto p-3">
        <div className="mb-3 flex flex-wrap items-end gap-2">
          <label className="text-xs text-muted">Type
            <Select value={type} onChange={(e) => setType(e.target.value as ChartType)} className="mt-1 h-8 w-32 text-xs">
              {CHART_TYPES.map((t) => <option key={t} value={t}>{t.toLowerCase()}</option>)}
            </Select>
          </label>
          {type !== 'METRIC' && (
            <label className="text-xs text-muted">X axis
              <Select value={x ?? ''} onChange={(e) => setX(e.target.value)} className="mt-1 h-8 w-40 text-xs">
                {result.columns.map((c) => <option key={c.name} value={c.name}>{c.name}</option>)}
              </Select>
            </label>
          )}
          <label className="text-xs text-muted">Y {type === 'PIE' || type === 'SCATTER' ? 'measure' : 'measures'}
            <Select value={ys[0] ?? ''} onChange={(e) => setYs([e.target.value, ...ys.slice(1).filter((y) => y !== e.target.value)])} className="mt-1 h-8 w-40 text-xs">
              {numeric.map((c) => <option key={c} value={c}>{c}</option>)}
            </Select>
          </label>
          {(type === 'BAR' || type === 'LINE') && numeric.length > 1 && (
            <div className="flex flex-wrap gap-1 pb-1">
              {numeric.filter((n) => n !== ys[0]).slice(0, 5).map((n) => (
                <button key={n} onClick={() => setYs(ys.includes(n) ? ys.filter((y) => y !== n) : [...ys, n].slice(0, 4))}
                  className={`rounded-md border px-2 py-1 text-[11px] ${ys.includes(n) ? 'border-accent bg-accent-soft text-accent' : 'border-border text-muted hover:text-fg'}`}>
                  + {n}
                </button>
              ))}
            </div>
          )}
        </div>
        {chart && (
          <p className="mb-2 text-xs text-muted"><Lightbulb className="mr-1 inline size-3 text-accent" />
            Recommended: <b className="font-medium text-fg-2">{chart.type.toLowerCase()}</b> — {chart.reason}</p>
        )}
        {numeric.length === 0 && type !== 'METRIC'
          ? <p className="p-8 text-center text-sm text-muted">This result has no numeric columns to chart.</p>
          : <ResultChart result={{ ...result, rows: table.rows }} type={type} xColumn={x} yColumns={ys} height={340} />}
      </TabsContent>

      <TabsContent value="summary" className="min-h-0 flex-1 overflow-auto p-4 text-sm">
        {interpretation ? (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <FileText className="size-4 text-accent" />
              <span className="font-medium">Result summary</span>
              <Badge tone={interpretation.generatedBy === 'ai' ? 'accent' : 'neutral'}>
                {interpretation.generatedBy === 'ai' ? 'AI explanation from the returned rows' : 'System summary'}
              </Badge>
            </div>
            <p className="leading-relaxed text-fg-2">{interpretation.summary}</p>
            {interpretation.insights.length > 0 && (
              <ul className="list-disc space-y-1 pl-5 text-fg-2">{interpretation.insights.map((i) => <li key={i}>{i}</li>)}</ul>
            )}
            {extraSummary}
          </div>
        ) : (
          <div className="space-y-2 text-muted">
            <p>{result.rowCount === 0 ? 'The query returned no rows.' : `The query returned ${result.rowCount.toLocaleString()} rows across ${result.columns.length} columns.`}</p>
            {extraSummary}
          </div>
        )}
      </TabsContent>

      <TabsContent value="meta" className="min-h-0 flex-1 overflow-auto p-4">
        <dl className="mb-4 grid grid-cols-2 gap-3 text-xs sm:grid-cols-4">
          <div><dt className="text-muted">Rows</dt><dd className="font-medium">{result.rowCount.toLocaleString()}</dd></div>
          <div><dt className="text-muted">Execution time</dt><dd className="font-medium">{formatDuration(result.durationMs)}</dd></div>
          <div><dt className="text-muted">Row cap</dt><dd className="font-medium">{result.maxRows.toLocaleString()}{result.truncated ? ' (reached)' : ''}</dd></div>
          {queryRunId && <div><dt className="text-muted">History ID</dt><dd className="font-mono">#{queryRunId}</dd></div>}
        </dl>
        <table className="w-full text-xs">
          <thead><tr className="text-left text-muted"><th className="py-1">Column</th><th>Database type</th><th>Category</th></tr></thead>
          <tbody>
            {result.columns.map((c) => (
              <tr key={c.name} className="border-t border-border"><td className="py-1.5 font-mono">{c.name}</td><td className="font-mono text-muted">{c.sqlType}</td><td>{c.category.toLowerCase()}</td></tr>
            ))}
          </tbody>
        </table>
      </TabsContent>
    </Tabs>
  );
}
