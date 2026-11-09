import { lazy, Suspense, useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { BookOpen, Check, Eye, Fingerprint, KeyRound, Link2, ListTree, LockKeyhole, Network, Pencil, Plus, RefreshCw, Search, Sparkles, Trash2, X } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { useWorkspace } from '@/lib/workspace';
import type { GlossaryTerm, QueryResult, SchemaView, TableView } from '@/lib/types';
import { cn, formatDateTime } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { useConfirm } from '@/components/ui/confirm';
import { Field, Input, Select, Textarea } from '@/components/ui/input';
import { EmptyState, ErrorState, PageHeader, Skeleton, Spinner } from '@/components/ui/feedback';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ResultsTable, useTableState } from '@/components/results/ResultsTable';
import { SemanticSearch } from '@/components/schema/SemanticSearch';
import { PrivacyPanel, PII_TYPES } from '@/components/schema/PrivacyPanel';

const ErDiagram = lazy(() => import('@/components/schema/ErDiagram').then((m) => ({ default: m.ErDiagram })));

function EditableDescription({ value, canEdit, onSave }: { value?: string; canEdit: boolean; onSave: (v: string) => Promise<unknown> }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(value ?? '');
  const [saving, setSaving] = useState(false);
  useEffect(() => setDraft(value ?? ''), [value]);
  if (!editing) {
    return (
      <span className="group inline-flex items-start gap-1">
        <span className={value ? 'text-fg-2' : 'italic text-muted/70'}>{value || 'No description'}</span>
        {canEdit && <button onClick={() => setEditing(true)} className="opacity-0 group-hover:opacity-100 focus:opacity-100" aria-label="Edit description"><Pencil className="size-3 text-muted" /></button>}
      </span>
    );
  }
  return (
    <span className="flex items-center gap-1">
      <Input value={draft} onChange={(e) => setDraft(e.target.value)} className="h-7 text-xs" autoFocus maxLength={1000} />
      <Button size="icon-sm" variant="ghost" loading={saving} aria-label="Save description" onClick={async () => {
        setSaving(true);
        try { await onSave(draft); setEditing(false); } finally { setSaving(false); }
      }}>{!saving && <Check />}</Button>
      <Button size="icon-sm" variant="ghost" onClick={() => { setDraft(value ?? ''); setEditing(false); }} aria-label="Cancel"><X /></Button>
    </span>
  );
}

function PreviewDialog({ dataSourceId, table, onClose }: { dataSourceId: number; table: string; onClose: () => void }) {
  const preview = useQuery({ queryKey: ['preview', dataSourceId, table], queryFn: () => api.get<QueryResult>(`/datasources/${dataSourceId}/tables/${encodeURIComponent(table)}/preview`) });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={`Sample rows · ${table}`} description="First 25 rows, read live from the database." className="max-w-5xl">
      {preview.isLoading ? <Skeleton className="h-64" /> : preview.isError ? <ErrorState message={errorMessage(preview.error)} /> : preview.data && (
        <div className="flex h-[60vh] flex-col overflow-hidden rounded-lg border border-border"><PreviewTable result={preview.data} /></div>
      )}
    </Dialog>
  );
}
function PreviewTable({ result }: { result: QueryResult }) {
  const state = useTableState(result);
  return <ResultsTable result={result} state={state} />;
}

function TableDetail({ table, dataSourceId, canEdit, canQuery, onSaved }: {
  table: TableView; dataSourceId: number; canEdit: boolean; canQuery: boolean; onSaved: (s: SchemaView) => void;
}) {
  const [preview, setPreview] = useState(false);
  const annotate = (column: string, description: string) =>
    api.put<SchemaView>(`/datasources/${dataSourceId}/annotations`, { tableName: table.name, columnName: column, description })
      .then((s) => { onSaved(s); toast.success('Description saved'); })
      .catch((e) => toast.error(errorMessage(e)));
  return (
    <Card>
      <CardHeader title={<span className="flex items-center gap-2"><ListTree className="size-4 text-accent" />{table.name} <Badge>{table.type.toLowerCase()}</Badge></span>}
        description={table.rowEstimate != null ? `~${table.rowEstimate.toLocaleString()} rows (estimate)` : undefined}
        actions={canQuery && <Button size="sm" variant="secondary" onClick={() => setPreview(true)}><Eye /> Preview rows</Button>} />
      <CardBody className="space-y-5">
        <div className="text-sm"><EditableDescription value={table.description} canEdit={canEdit} onSave={(v) => annotate('', v)} /></div>
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead><tr className="border-b border-border text-left text-muted"><th className="py-2 pr-3">Column</th><th className="pr-3">Type</th><th className="pr-3">Null</th><th className="pr-3">Keys</th><th>Description</th></tr></thead>
            <tbody>
              {table.columns.map((c) => (
                <tr key={c.name} className="border-b border-border/60 align-top">
                  <td className="py-2 pr-3 font-mono text-fg">{c.name}</td>
                  <td className="pr-3 font-mono text-muted">{c.dataType}{c.defaultValue ? <span className="block text-[10px]">default {c.defaultValue}</span> : null}</td>
                  <td className="pr-3 text-muted">{c.nullable ? 'yes' : 'no'}</td>
                  <td className="pr-3">
                    {c.primaryKey && <Badge tone="warning"><KeyRound />PK</Badge>}
                    {c.references && <Badge tone="accent" className="ml-1"><Link2 />{c.references}</Badge>}
                    {c.pii && (
                      <Badge tone={c.masked ? 'danger' : 'neutral'} className="ml-1" title={c.masked ? 'Masked for analysts and viewers' : 'Personal data, not masked'}>
                        <LockKeyhole />{PII_TYPES.find((p) => p.id === c.pii)?.label ?? c.pii}
                      </Badge>
                    )}
                  </td>
                  <td className="py-2"><EditableDescription value={c.description} canEdit={canEdit} onSave={(v) => annotate(c.name, v)} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Indexes</h4>
            {table.indexes.length === 0 ? <p className="text-xs text-muted">None</p> : table.indexes.map((i) => (
              <p key={i.name} className="text-xs"><span className="font-mono">{i.name}</span> <span className="text-muted">({i.columns.join(', ')})</span> {i.unique && <Badge>unique</Badge>}</p>
            ))}
          </div>
          <div>
            <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Foreign keys</h4>
            {table.foreignKeys.length === 0 ? <p className="text-xs text-muted">None</p> : table.foreignKeys.map((f) => (
              <p key={f.name} className="text-xs"><span className="font-mono">{f.columns.join(', ')}</span> → <span className="font-mono">{f.referencedTable}({f.referencedColumns.join(', ')})</span></p>
            ))}
          </div>
        </div>
      </CardBody>
      {preview && <PreviewDialog dataSourceId={dataSourceId} table={table.name} onClose={() => setPreview(false)} />}
    </Card>
  );
}

function Glossary({ dataSourceId, canEdit }: { dataSourceId: number; canEdit: boolean }) {
  const qc = useQueryClient();
  const confirm = useConfirm();
  const terms = useQuery({ queryKey: ['glossary', dataSourceId], queryFn: () => api.get<GlossaryTerm[]>(`/datasources/${dataSourceId}/glossary`) });
  const [editing, setEditing] = useState<GlossaryTerm | 'new' | null>(null);
  const [form, setForm] = useState({ term: '', definition: '', sqlHint: '' });
  useEffect(() => {
    if (editing === 'new') setForm({ term: '', definition: '', sqlHint: '' });
    else if (editing) setForm({ term: editing.term, definition: editing.definition, sqlHint: editing.sqlHint ?? '' });
  }, [editing]);
  const save = useMutation({
    mutationFn: () => editing === 'new' || !editing
      ? api.post(`/datasources/${dataSourceId}/glossary`, form)
      : api.put(`/datasources/${dataSourceId}/glossary/${editing.id}`, form),
    onSuccess: () => { toast.success('Glossary updated'); qc.invalidateQueries({ queryKey: ['glossary', dataSourceId] }); setEditing(null); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/datasources/${dataSourceId}/glossary/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['glossary', dataSourceId] }),
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Card>
      <CardHeader title="Business glossary" description="Definitions the AI must follow, e.g. what counts as “revenue” or an “active customer”."
        actions={canEdit && <Button size="sm" onClick={() => setEditing('new')}><Plus /> Add term</Button>} />
      <div className="divide-y divide-border">
        {terms.isLoading && <div className="p-5"><Skeleton className="h-24" /></div>}
        {terms.data?.length === 0 && <div className="p-5"><EmptyState icon={<BookOpen />} title="No glossary terms" description="Add definitions so generated SQL matches your business language." /></div>}
        {terms.data?.map((t) => (
          <div key={t.id} className="flex gap-4 px-5 py-3">
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium">{t.term}</p>
              <p className="text-sm text-fg-2">{t.definition}</p>
              {t.sqlHint && <p className="mt-1 font-mono text-[11px] text-muted">{t.sqlHint}</p>}
            </div>
            {canEdit && (
              <div className="flex gap-1">
                <Button size="icon-sm" variant="ghost" onClick={() => setEditing(t)} aria-label="Edit term"><Pencil /></Button>
                <Button size="icon-sm" variant="ghost" aria-label="Delete term" onClick={async () => {
                  if (await confirm({ title: `Delete “${t.term}”?`, confirmLabel: 'Delete', danger: true })) remove.mutate(t.id);
                }}><Trash2 className="text-danger" /></Button>
              </div>
            )}
          </div>
        ))}
      </div>
      <Dialog open={!!editing} onOpenChange={(o) => !o && setEditing(null)} title={editing === 'new' ? 'Add glossary term' : 'Edit glossary term'}
        footer={<><Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
          <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!form.term.trim() || !form.definition.trim()}>Save</Button></>}>
        <div className="space-y-3">
          <Field label="Term"><Input value={form.term} onChange={(e) => setForm({ ...form, term: e.target.value })} maxLength={120} /></Field>
          <Field label="Definition"><Textarea value={form.definition} onChange={(e) => setForm({ ...form, definition: e.target.value })} maxLength={2000} /></Field>
          <Field label="SQL hint (optional)" hint="e.g. SUM(o.total_amount) WHERE o.status <> 'cancelled'">
            <Textarea className="font-mono text-xs" value={form.sqlHint} onChange={(e) => setForm({ ...form, sqlHint: e.target.value })} maxLength={2000} />
          </Field>
        </div>
      </Dialog>
    </Card>
  );
}

export default function SchemaExplorer() {
  const { dataSources, dataSourceId, setDataSourceId, can, dataSourcesLoading, workspaceId, loading: workspacesLoading } = useWorkspace();
  const ds = dataSources.find((d) => d.id === dataSourceId);
  const qc = useQueryClient();
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const [tab, setTab] = useState('tables');
  const schema = useQuery({
    queryKey: ['schema', dataSourceId],
    queryFn: () => api.get<SchemaView>(`/datasources/${dataSourceId}/schema`),
    enabled: !!dataSourceId,
  });
  const refresh = useMutation({
    mutationFn: () => api.post<SchemaView>(`/datasources/${dataSourceId}/schema/refresh`),
    onSuccess: (s) => { qc.setQueryData(['schema', dataSourceId], s); toast.success(`Refreshed ${s.tableCount} tables`); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const tables = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (schema.data?.tables ?? []).filter((t) => !q || t.name.toLowerCase().includes(q)
      || t.columns.some((c) => c.name.toLowerCase().includes(q) || c.description?.toLowerCase().includes(q))
      || t.description?.toLowerCase().includes(q));
  }, [schema.data, search]);
  const current = schema.data?.tables.find((t) => t.name === selected) ?? tables[0];
  const canEdit = can('ANALYST') && !!ds?.canQuery;

  if (!workspacesLoading && workspaceId !== null && !dataSourcesLoading && dataSources.length === 0) {
    return <EmptyState icon={<Network />} title="No data sources" description="Add a connection to explore its schema." />;
  }

  return (
    <div>
      <PageHeader title="Explore" description={schema.data ? `${schema.data.tableCount} tables · ${schema.data.relationships.length} relationships · refreshed ${formatDateTime(schema.data.refreshedAt)}` : 'Tables, keys, relationships and business definitions'}
        actions={<>
          <Select value={dataSourceId ?? ''} onChange={(e) => { setDataSourceId(Number(e.target.value)); setSelected(null); }} className="w-56" aria-label="Data source">
            {dataSources.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </Select>
          {ds?.canQuery && <Button variant="secondary" onClick={() => refresh.mutate()} loading={refresh.isPending}>{!refresh.isPending && <RefreshCw />} Refresh</Button>}
        </>} />

      {schema.isError && <ErrorState message={errorMessage(schema.error)} onRetry={() => schema.refetch()} />}

      <Tabs value={tab} onValueChange={setTab}>
        <TabsList className="mb-8 flex-wrap">
          <TabsTrigger value="tables"><ListTree /> Tables</TabsTrigger>
          <TabsTrigger value="er"><Network /> ER diagram</TabsTrigger>
          <TabsTrigger value="semantic"><Sparkles /> Semantic search</TabsTrigger>
          <TabsTrigger value="privacy"><Fingerprint /> Privacy</TabsTrigger>
          <TabsTrigger value="glossary"><BookOpen /> Glossary</TabsTrigger>
        </TabsList>
        <TabsContent value="tables">
          {schema.isLoading ? <Skeleton className="h-96" /> : schema.data && (
            <div className="grid gap-4 lg:grid-cols-[260px_1fr]">
              <Card className="h-fit">
                <div className="relative border-b border-border p-2">
                  <Search className="absolute left-4 top-1/2 size-3.5 -translate-y-1/2 text-muted" />
                  <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search tables, columns, descriptions" className="h-8 pl-8 text-xs" aria-label="Search schema" />
                </div>
                <ul className="max-h-[60vh] overflow-y-auto p-1">
                  {tables.map((t) => (
                    <li key={t.name}>
                      <button onClick={() => setSelected(t.name)}
                        className={cn('flex w-full items-center justify-between rounded-md px-2.5 py-1.5 text-left text-sm hover:bg-surface-2', current?.name === t.name && 'bg-accent-soft text-accent')}>
                        <span className="truncate">{t.name}</span><span className="text-[11px] text-muted">{t.columns.length} cols</span>
                      </button>
                    </li>
                  ))}
                  {tables.length === 0 && <li className="p-3 text-xs text-muted">No matches.</li>}
                </ul>
              </Card>
              {current ? <TableDetail table={current} dataSourceId={dataSourceId!} canEdit={canEdit} canQuery={!!ds?.canQuery}
                onSaved={(s) => { qc.setQueryData<SchemaView>(['schema', dataSourceId], s); }} /> : <EmptyState title="No tables visible" />}
            </div>
          )}
        </TabsContent>
        <TabsContent value="er">
          {schema.data ? (
            <Suspense fallback={<div className="flex h-96 items-center justify-center"><Spinner /></div>}>
              <ErDiagram schema={schema.data} onSelect={(t) => { setSelected(t); setTab('tables'); }} />
            </Suspense>
          ) : <Skeleton className="h-96" />}
          <p className="mt-2 text-xs text-muted">Drag tables to rearrange · scroll to zoom · click a table header for details.</p>
        </TabsContent>
        <TabsContent value="semantic">
          {dataSourceId && <SemanticSearch dataSourceId={dataSourceId} onSelect={(t) => { setSelected(t); setTab('tables'); }} />}
        </TabsContent>
        <TabsContent value="privacy">{dataSourceId && <PrivacyPanel dataSourceId={dataSourceId} canManage={!!ds?.canManage} />}</TabsContent>
        <TabsContent value="glossary">{dataSourceId && <Glossary dataSourceId={dataSourceId} canEdit={canEdit} />}</TabsContent>
      </Tabs>
    </div>
  );
}
