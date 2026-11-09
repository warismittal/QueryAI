import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { CheckCircle2, FlaskConical, Play, Plus, Trash2, XCircle } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { useWorkspace } from '@/lib/workspace';
import type { EvaluationCase, EvaluationRun } from '@/lib/types';
import { formatDateTime, formatDuration, formatPercent } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { useConfirm } from '@/components/ui/confirm';
import { Field, Select, Textarea } from '@/components/ui/input';
import { EmptyState, ErrorState, Notice, PageHeader, Skeleton } from '@/components/ui/feedback';

export default function Evaluation() {
  const { dataSources, dataSourceId, setDataSourceId, can } = useWorkspace();
  const ds = dataSources.find((d) => d.id === dataSourceId);
  const qc = useQueryClient();
  const confirm = useConfirm();
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ question: '', expectedSql: '' });
  const [selectedRun, setSelectedRun] = useState<EvaluationRun | null>(null);
  const editable = can('ANALYST') && !!ds?.canQuery;

  const cases = useQuery({ queryKey: ['eval-cases', dataSourceId], queryFn: () => api.get<EvaluationCase[]>('/evaluation/cases', { dataSourceId: dataSourceId! }), enabled: !!dataSourceId });
  const runs = useQuery({ queryKey: ['eval-runs', dataSourceId], queryFn: () => api.get<EvaluationRun[]>('/evaluation/runs', { dataSourceId: dataSourceId! }), enabled: !!dataSourceId });
  const create = useMutation({
    mutationFn: () => api.post<EvaluationCase>('/evaluation/cases', form, { dataSourceId: dataSourceId! }),
    onSuccess: () => { toast.success('Case added'); setAdding(false); setForm({ question: '', expectedSql: '' }); qc.invalidateQueries({ queryKey: ['eval-cases', dataSourceId] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/evaluation/cases/${id}`, { dataSourceId: dataSourceId! }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['eval-cases', dataSourceId] }),
    onError: (e) => toast.error(errorMessage(e)),
  });
  const run = useMutation({
    mutationFn: () => api.post<EvaluationRun>('/evaluation/run', {}, { dataSourceId: dataSourceId! }),
    onSuccess: (r) => { setSelectedRun(r); qc.invalidateQueries({ queryKey: ['eval-runs', dataSourceId] }); toast.success(`Accuracy ${formatPercent(r.accuracy)}`); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const latest = selectedRun ?? runs.data?.[0] ?? null;

  if (!dataSources.length) return <EmptyState icon={<FlaskConical />} title="No data sources" description="Add a connection to benchmark text-to-SQL accuracy." />;

  return (
    <div>
      <PageHeader title="Text-to-SQL evaluation"
        description="Benchmark the AI against reference queries. A case passes when the generated SQL returns the same results (execution accuracy)."
        actions={<>
          <Select value={dataSourceId ?? ''} onChange={(e) => { setDataSourceId(Number(e.target.value)); setSelectedRun(null); }} className="w-56" aria-label="Data source">
            {dataSources.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </Select>
          {editable && <Button variant="secondary" onClick={() => setAdding(true)}><Plus /> Add case</Button>}
          {editable && <Button onClick={() => run.mutate()} loading={run.isPending} disabled={!cases.data?.length}>{!run.isPending && <Play />} Run benchmark</Button>}
        </>} />
      {run.isPending && <Notice className="mb-4">Generating and executing SQL for each case against the live database… this can take a minute.</Notice>}
      <div className="grid gap-4 lg:grid-cols-[1fr_1.2fr]">
        <Card>
          <CardHeader title={`Cases (${cases.data?.length ?? 0})`} description="Question + hand-written reference SQL" />
          {cases.isLoading ? <div className="p-5"><Skeleton className="h-40" /></div> : cases.isError ? <div className="p-5"><ErrorState message={errorMessage(cases.error)} /></div>
            : cases.data?.length === 0 ? <div className="p-5"><EmptyState icon={<FlaskConical />} title="No benchmark cases" description="Add questions with reference SQL to measure accuracy over time." /></div> : (
              <div className="divide-y divide-border">
                {cases.data?.map((c) => {
                  const res = latest?.results.find((r) => r.caseId === c.id);
                  return (
                    <div key={c.id} className="flex gap-3 px-5 py-3">
                      {res ? (res.passed ? <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-success" /> : <XCircle className="mt-0.5 size-4 shrink-0 text-danger" />) : <span className="size-4 shrink-0" />}
                      <div className="min-w-0 flex-1">
                        <p className="text-sm">{c.question}</p>
                        <pre className="mt-1 max-h-24 overflow-auto font-mono text-[10.5px] text-muted">{c.expectedSql}</pre>
                      </div>
                      {editable && <Button size="icon-sm" variant="ghost" aria-label="Delete case" onClick={async () => {
                        if (await confirm({ title: 'Delete this case?', confirmLabel: 'Delete', danger: true })) remove.mutate(c.id);
                      }}><Trash2 className="text-danger" /></Button>}
                    </div>
                  );
                })}
              </div>
            )}
        </Card>
        <div className="space-y-4">
          <Card>
            <CardHeader title="Latest result" description={latest ? `${formatDateTime(latest.createdAt)} · ${latest.llmModel ?? ''}` : 'Run the benchmark to see results'} />
            <CardBody>
              {!latest ? <p className="text-sm text-muted">No runs yet.</p> : (
                <div className="space-y-4">
                  <div className="flex items-end gap-6">
                    <div><p className="text-xs text-muted">Execution accuracy</p><p className="text-3xl font-semibold">{formatPercent(latest.accuracy)}</p></div>
                    <div><p className="text-xs text-muted">Passed</p><p className="text-xl font-semibold">{latest.passedCases}/{latest.totalCases}</p></div>
                  </div>
                  <ul className="space-y-2">
                    {latest.results.map((r) => (
                      <li key={r.caseId} className="rounded-lg border border-border p-3 text-xs">
                        <div className="flex items-center gap-2">
                          {r.passed ? <Badge tone="success">pass</Badge> : <Badge tone="danger">fail</Badge>}
                          <span className="truncate font-medium text-fg">{r.question}</span>
                          <span className="ml-auto shrink-0 text-muted">{formatDuration(r.generationMs)}</span>
                        </div>
                        <p className="mt-1 text-muted">{r.reason}{r.expectedRows != null && ` · expected ${r.expectedRows} rows, got ${r.actualRows}`}</p>
                        {r.generatedSql && <pre className="mt-1 max-h-28 overflow-auto rounded bg-bg p-2 font-mono text-[10.5px] text-fg-2">{r.generatedSql}</pre>}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </CardBody>
          </Card>
          {runs.data && runs.data.length > 1 && (
            <Card>
              <CardHeader title="Run history" />
              <CardBody className="space-y-1.5">
                {runs.data.map((r) => (
                  <button key={r.id} onClick={() => setSelectedRun(r)} className="flex w-full items-center gap-3 rounded-md px-2 py-1.5 text-left text-xs hover:bg-surface-2">
                    <span className="w-14 font-semibold">{formatPercent(r.accuracy)}</span>
                    <span className="text-muted">{r.passedCases}/{r.totalCases}</span>
                    <span className="truncate text-muted">{r.llmModel}</span>
                    <span className="ml-auto text-muted">{formatDateTime(r.createdAt)}</span>
                  </button>
                ))}
              </CardBody>
            </Card>
          )}
        </div>
      </div>
      <Dialog open={adding} onOpenChange={setAdding} title="Add evaluation case" description="The reference SQL must pass QueryAI's safety validation."
        footer={<><Button variant="secondary" onClick={() => setAdding(false)}>Cancel</Button>
          <Button onClick={() => create.mutate()} loading={create.isPending} disabled={!form.question.trim() || !form.expectedSql.trim()}>Add</Button></>}>
        <div className="space-y-3">
          <Field label="Question"><Textarea rows={2} value={form.question} onChange={(e) => setForm({ ...form, question: e.target.value })} /></Field>
          <Field label="Reference SQL"><Textarea rows={6} className="font-mono text-xs" value={form.expectedSql} onChange={(e) => setForm({ ...form, expectedSql: e.target.value })} /></Field>
        </div>
      </Dialog>
    </div>
  );
}
