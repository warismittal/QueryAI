import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Bookmark, ChevronDown, ChevronRight, ExternalLink, History as HistoryIcon, RotateCcw, Search, ThumbsDown, ThumbsUp } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { useWorkspace } from '@/lib/workspace';
import type { ExecuteResponse, ExecutionStatus, Page, QueryRun, QuerySource } from '@/lib/types';
import { formatDateTime, formatDuration, timeAgo } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { Checkbox, Input, Select } from '@/components/ui/input';
import { EmptyState, ErrorState, Notice, PageHeader, Pagination, Skeleton } from '@/components/ui/feedback';
import { ExecutionBadge, ValidationBadge } from '@/components/ui/status';
import { ResultsPanel } from '@/components/results/ResultsPanel';
import { SaveQueryDialog } from '@/components/sql/SaveQueryDialog';

function SqlBlock({ label, sql }: { label: string; sql?: string }) {
  if (!sql) return null;
  return (
    <div>
      <p className="mb-1 text-[11px] font-medium uppercase tracking-wide text-muted">{label}</p>
      <pre className="max-h-56 overflow-auto rounded-lg border border-border bg-bg p-3 font-mono text-[11px] leading-relaxed text-fg-2">{sql}</pre>
    </div>
  );
}

export default function History() {
  const { workspaceId, dataSources, can } = useWorkspace();
  const qc = useQueryClient();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<ExecutionStatus | ''>('');
  const [source, setSource] = useState<QuerySource | ''>('');
  const [ds, setDs] = useState<string>('');
  const [mine, setMine] = useState(false);
  const [page, setPage] = useState(0);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [rerun, setRerun] = useState<ExecuteResponse | null>(null);
  const [saving, setSaving] = useState<QueryRun | null>(null);

  const history = useQuery({
    queryKey: ['history', workspaceId, q, status, source, ds, mine, page],
    queryFn: () => api.get<Page<QueryRun>>('/history', {
      workspaceId: workspaceId!, q, status, source, dataSourceId: ds, mine, page, size: 20,
    }),
    enabled: !!workspaceId,
    placeholderData: keepPreviousData,
  });

  const feedback = useMutation({
    mutationFn: ({ id, rating }: { id: number; rating: 'UP' | 'DOWN' }) => api.post<QueryRun>(`/history/${id}/feedback`, { rating }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['history'] }),
    onError: (e) => toast.error(errorMessage(e)),
  });
  const rerunMutation = useMutation({
    mutationFn: (id: number) => api.post<ExecuteResponse>(`/history/${id}/rerun`),
    onSuccess: (r) => { setRerun(r); qc.invalidateQueries({ queryKey: ['history'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const resetPage = <T,>(setter: (v: T) => void) => (v: T) => { setter(v); setPage(0); };

  return (
    <div>
      <PageHeader title="Query history" description={can('ADMIN') ? 'Every question and SQL execution in this workspace.' : 'Your questions and SQL executions in this workspace.'} />

      <Card className="mb-4 flex flex-wrap items-center gap-2 p-3">
        <div className="relative min-w-52 flex-1">
          <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted" />
          <Input value={q} onChange={(e) => resetPage(setQ)(e.target.value)} placeholder="Search questions and SQL" className="pl-8" aria-label="Search history" />
        </div>
        <Select value={status} onChange={(e) => resetPage(setStatus)(e.target.value as ExecutionStatus | '')} className="w-40" aria-label="Status">
          <option value="">All outcomes</option>
          <option value="SUCCESS">Success</option><option value="FAILED">Failed</option><option value="BLOCKED">Blocked</option>
          <option value="CLARIFICATION_NEEDED">Clarification</option><option value="NOT_EXECUTED">Not executed</option>
        </Select>
        <Select value={source} onChange={(e) => resetPage(setSource)(e.target.value as QuerySource | '')} className="w-40" aria-label="Source">
          <option value="">All sources</option>
          <option value="AI">AI question</option><option value="MANUAL">Manual SQL</option><option value="SAVED_QUERY">Saved query</option>
          <option value="DASHBOARD">Dashboard</option><option value="RERUN">Re-run</option>
        </Select>
        <Select value={ds} onChange={(e) => resetPage(setDs)(e.target.value)} className="w-48" aria-label="Data source">
          <option value="">All data sources</option>
          {dataSources.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
        </Select>
        {can('ADMIN') && <Checkbox label="Only mine" checked={mine} onChange={(e) => resetPage(setMine)(e.target.checked)} />}
      </Card>

      {history.isError && <ErrorState message={errorMessage(history.error)} onRetry={() => history.refetch()} />}
      {history.isLoading ? <Skeleton className="h-96 rounded-xl" /> : history.data?.content.length === 0 ? (
        <EmptyState icon={<HistoryIcon />} title="No matching queries" description="Questions and SQL runs appear here automatically." />
      ) : (
        <Card className="divide-y divide-border">
          {history.data?.content.map((r) => {
            const open = expanded === r.id;
            const sql = r.finalSql ?? r.generatedSql;
            return (
              <div key={r.id}>
                <button className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-surface-2/50" onClick={() => setExpanded(open ? null : r.id)} aria-expanded={open}>
                  {open ? <ChevronDown className="size-4 shrink-0 text-muted" /> : <ChevronRight className="size-4 shrink-0 text-muted" />}
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm text-fg">{r.question ?? <span className="font-mono text-xs">{sql}</span>}</p>
                    <p className="mt-0.5 text-[11px] text-muted">
                      #{r.id} · {r.dataSourceName ?? 'deleted source'} · {r.source.toLowerCase().replace('_', ' ')} · {timeAgo(r.createdAt)}
                      {r.rowCount != null && ` · ${r.rowCount.toLocaleString()} rows`}{r.durationMs != null && ` · ${formatDuration(r.durationMs)}`}
                    </p>
                  </div>
                  <div className="hidden shrink-0 items-center gap-1.5 sm:flex">
                    {r.myFeedback === 'UP' && <ThumbsUp className="size-3.5 text-success" />}
                    {r.myFeedback === 'DOWN' && <ThumbsDown className="size-3.5 text-danger" />}
                    <ValidationBadge status={r.validationStatus} />
                    <ExecutionBadge status={r.executionStatus} />
                  </div>
                </button>
                {open && (
                  <div className="space-y-3 bg-surface-2/30 px-4 pb-4 pl-11">
                    <div className="flex flex-wrap gap-2 sm:hidden"><ValidationBadge status={r.validationStatus} /><ExecutionBadge status={r.executionStatus} /></div>
                    <dl className="grid grid-cols-2 gap-3 text-xs sm:grid-cols-4">
                      <div><dt className="text-muted">When</dt><dd>{formatDateTime(r.createdAt)}</dd></div>
                      <div><dt className="text-muted">Execution</dt><dd>{formatDuration(r.durationMs)}</dd></div>
                      <div><dt className="text-muted">Generation</dt><dd>{formatDuration(r.generationMs)}</dd></div>
                      <div><dt className="text-muted">Model</dt><dd className="truncate">{r.llmModel ?? '—'}</dd></div>
                    </dl>
                    {r.errorMessage && <Notice tone="danger"><span className="font-mono text-xs">{r.errorMessage}</span></Notice>}
                    {r.explanation && <p className="text-sm text-fg-2">{r.explanation}</p>}
                    {r.generatedSql && r.generatedSql !== r.finalSql && <SqlBlock label="Generated SQL" sql={r.generatedSql} />}
                    <SqlBlock label="Executed SQL" sql={r.finalSql} />
                    <div className="flex flex-wrap items-center gap-2">
                      {sql && r.dataSourceId && (
                        <>
                          <Button size="sm" onClick={() => rerunMutation.mutate(r.id)} loading={rerunMutation.isPending && rerunMutation.variables === r.id}><RotateCcw /> Re-run</Button>
                          <Button asChild size="sm" variant="secondary">
                            <Link to={`/app/studio?ds=${r.dataSourceId}&sql=${encodeURIComponent(sql)}`}><ExternalLink /> Open in Studio</Link>
                          </Button>
                          {can('ANALYST') && <Button size="sm" variant="secondary" onClick={() => setSaving(r)}><Bookmark /> Save</Button>}
                        </>
                      )}
                      <span className="ml-auto flex items-center gap-1 text-xs text-muted">Feedback
                        <Button size="icon-sm" variant={r.myFeedback === 'UP' ? 'primary' : 'ghost'} aria-label="Helpful" onClick={() => feedback.mutate({ id: r.id, rating: 'UP' })}><ThumbsUp /></Button>
                        <Button size="icon-sm" variant={r.myFeedback === 'DOWN' ? 'danger' : 'ghost'} aria-label="Not helpful" onClick={() => feedback.mutate({ id: r.id, rating: 'DOWN' })}><ThumbsDown /></Button>
                      </span>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </Card>
      )}
      <div className="mt-4 flex items-center justify-between">
        <span className="text-xs text-muted">{history.data ? `${history.data.totalElements.toLocaleString()} entries` : ''}</span>
        {history.data && <Pagination page={history.data.page} totalPages={history.data.totalPages} onPage={setPage} />}
      </div>

      {rerun && (
        <Dialog open onOpenChange={(o) => !o && setRerun(null)} title={`Re-run · #${rerun.queryRunId}`} className="max-w-5xl"
          description={<span className="flex items-center gap-2"><ExecutionBadge status={rerun.status} /><Badge>new history entry</Badge></span>}>
          {rerun.result ? (
            <div className="flex h-[60vh] flex-col overflow-hidden rounded-lg border border-border">
              <ResultsPanel result={rerun.result} chart={rerun.chart} queryRunId={rerun.queryRunId} />
            </div>
          ) : <Notice tone="danger"><span className="font-mono text-xs">{rerun.error}</span></Notice>}
        </Dialog>
      )}
      <SaveQueryDialog open={!!saving} onOpenChange={(o) => !o && setSaving(null)} sql={saving?.finalSql ?? saving?.generatedSql ?? ''}
        question={saving?.question} dataSourceId={saving?.dataSourceId} />
    </div>
  );
}
