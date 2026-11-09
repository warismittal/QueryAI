import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Bookmark, Copy, ExternalLink, FileCode2, Folder, GitCommitVertical, Globe, Lock, Pencil, Play, RotateCcw, Search, Trash2 } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { useWorkspace } from '@/lib/workspace';
import type { ExecuteResponse, SavedQuery, SavedQueryVersion, Visibility } from '@/lib/types';
import { formatDateTime, timeAgo } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { useConfirm } from '@/components/ui/confirm';
import { Checkbox, Field, Input, Select, Textarea } from '@/components/ui/input';
import { EmptyState, ErrorState, Notice, PageHeader, Skeleton } from '@/components/ui/feedback';
import { ExecutionBadge } from '@/components/ui/status';
import { SqlEditor } from '@/components/sql/SqlEditor';
import { ParameterInputs, toParams } from '@/components/sql/ValidationPanel';
import { ResultsPanel } from '@/components/results/ResultsPanel';

function EditDialog({ query, onClose }: { query: SavedQuery; onClose: () => void }) {
  const qc = useQueryClient();
  const [form, setForm] = useState({ name: query.name, description: query.description ?? '', folder: query.folder ?? '', sql: query.sql,
    visibility: query.visibility as Visibility, template: query.template, versionNote: '' });
  const save = useMutation({
    mutationFn: () => api.put<SavedQuery>(`/saved-queries/${query.id}`, { ...form, chartType: query.chartType }),
    onSuccess: (s) => { toast.success(s.currentVersion > query.currentVersion ? `Saved as version ${s.currentVersion}` : 'Saved'); qc.invalidateQueries({ queryKey: ['saved-queries'] }); onClose(); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={`Edit “${query.name}”`} className="max-w-3xl"
      description="Changing the SQL creates a new version; the previous one stays in the history."
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button onClick={() => save.mutate()} loading={save.isPending} disabled={!form.name.trim() || !form.sql.trim()}>Save</Button></>}>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Name" className="sm:col-span-2"><Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={160} /></Field>
        <Field label="Folder"><Input value={form.folder} onChange={(e) => setForm({ ...form, folder: e.target.value })} maxLength={120} /></Field>
        <Field label="Visibility">
          <Select value={form.visibility} onChange={(e) => setForm({ ...form, visibility: e.target.value as Visibility })}>
            <option value="PRIVATE">Private (only me)</option><option value="WORKSPACE">Shared with workspace</option>
          </Select>
        </Field>
        <Field label="Description" className="sm:col-span-2"><Textarea rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
        <div className="h-56 overflow-hidden rounded-lg border border-border sm:col-span-2">
          <SqlEditor value={form.sql} onChange={(sql) => setForm({ ...form, sql })} />
        </div>
        <Field label="Version note" hint="Describe what changed (only used if SQL changed)"><Input value={form.versionNote} onChange={(e) => setForm({ ...form, versionNote: e.target.value })} maxLength={500} /></Field>
        <div className="flex items-end pb-2"><Checkbox label="Reusable template" checked={form.template} onChange={(e) => setForm({ ...form, template: e.target.checked })} /></div>
      </div>
    </Dialog>
  );
}

function VersionsDialog({ query, onClose }: { query: SavedQuery; onClose: () => void }) {
  const qc = useQueryClient();
  const versions = useQuery({ queryKey: ['versions', query.id], queryFn: () => api.get<SavedQueryVersion[]>(`/saved-queries/${query.id}/versions`) });
  const restore = useMutation({
    mutationFn: (v: number) => api.post<SavedQuery>(`/saved-queries/${query.id}/versions/${v}/restore`),
    onSuccess: (s) => { toast.success(`Restored as version ${s.currentVersion}`); qc.invalidateQueries({ queryKey: ['saved-queries'] }); qc.invalidateQueries({ queryKey: ['versions', query.id] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={`Version history · ${query.name}`} className="max-w-3xl">
      {versions.isLoading ? <Skeleton className="h-40" /> : (
        <ol className="space-y-3">
          {versions.data?.map((v) => (
            <li key={v.version} className="rounded-lg border border-border p-3">
              <div className="mb-2 flex items-center gap-2 text-xs">
                <GitCommitVertical className="size-4 text-accent" /><b>v{v.version}</b>
                {v.version === query.currentVersion && <Badge tone="accent">current</Badge>}
                <span className="text-muted">{formatDateTime(v.createdAt)}{v.note ? ` · ${v.note}` : ''}</span>
                {query.canEdit && v.version !== query.currentVersion && (
                  <Button size="sm" variant="secondary" className="ml-auto" onClick={() => restore.mutate(v.version)} loading={restore.isPending && restore.variables === v.version}><RotateCcw /> Restore</Button>
                )}
              </div>
              <pre className="max-h-40 overflow-auto rounded bg-bg p-2 font-mono text-[11px] text-fg-2">{v.sql}</pre>
            </li>
          ))}
        </ol>
      )}
    </Dialog>
  );
}

function RunDialog({ query, onClose }: { query: SavedQuery; onClose: () => void }) {
  const [values, setValues] = useState<Record<string, string>>({});
  const run = useMutation({
    mutationFn: () => api.post<ExecuteResponse>(`/saved-queries/${query.id}/run`, { parameters: toParams(query.parameters, values) }),
    onError: (e) => toast.error(errorMessage(e)),
  });
  const { mutate } = run;
  useEffect(() => { if (query.parameters.length === 0) mutate(); }, [query, mutate]);
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={`Run · ${query.name}`} className="max-w-5xl"
      description={run.data ? <span className="flex items-center gap-2"><ExecutionBadge status={run.data.status} /> history #{run.data.queryRunId}</span> : 'Executed live against the database'}>
      {query.parameters.length > 0 && (
        <div className="mb-3 flex flex-wrap items-end gap-3">
          <ParameterInputs names={query.parameters} values={values} onChange={setValues} />
          <Button size="sm" onClick={() => run.mutate()} loading={run.isPending}><Play /> Run</Button>
        </div>
      )}
      {run.isPending && <Skeleton className="h-64" />}
      {run.data?.result && (
        <div className="flex h-[58vh] flex-col overflow-hidden rounded-lg border border-border">
          <ResultsPanel result={run.data.result} chart={run.data.chart} queryRunId={run.data.queryRunId} filename={query.name} />
        </div>
      )}
      {run.data && !run.data.result && <Notice tone="danger"><span className="font-mono text-xs">{run.data.error}</span></Notice>}
    </Dialog>
  );
}

export default function SavedQueries() {
  const { workspaceId, can } = useWorkspace();
  const qc = useQueryClient();
  const confirm = useConfirm();
  const [q, setQ] = useState('');
  const [folder, setFolder] = useState('');
  const [templates, setTemplates] = useState(false);
  const [editing, setEditing] = useState<SavedQuery | null>(null);
  const [versionsOf, setVersionsOf] = useState<SavedQuery | null>(null);
  const [running, setRunning] = useState<SavedQuery | null>(null);

  const list = useQuery({
    queryKey: ['saved-queries', workspaceId, q, folder, templates],
    queryFn: () => api.get<SavedQuery[]>('/saved-queries', { workspaceId: workspaceId!, q, folder, templates }),
    enabled: !!workspaceId,
  });
  const all = useQuery({ queryKey: ['saved-queries', workspaceId], queryFn: () => api.get<SavedQuery[]>('/saved-queries', { workspaceId: workspaceId! }), enabled: !!workspaceId });
  const folders = useMemo(() => [...new Set((all.data ?? []).map((s) => s.folder).filter(Boolean) as string[])].sort(), [all.data]);
  const grouped = useMemo(() => {
    const g = new Map<string, SavedQuery[]>();
    (list.data ?? []).forEach((s) => { const k = s.folder ?? 'Unfiled'; g.set(k, [...(g.get(k) ?? []), s]); });
    return [...g.entries()].sort(([a], [b]) => (a === 'Unfiled' ? 1 : b === 'Unfiled' ? -1 : a.localeCompare(b)));
  }, [list.data]);

  const duplicate = useMutation({
    mutationFn: (id: number) => api.post<SavedQuery>(`/saved-queries/${id}/duplicate`),
    onSuccess: (s) => { toast.success(`Created “${s.name}”`); qc.invalidateQueries({ queryKey: ['saved-queries'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/saved-queries/${id}`),
    onSuccess: () => { toast.success('Deleted'); qc.invalidateQueries({ queryKey: ['saved-queries'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <div>
      <PageHeader title="Saved queries" description="Reusable, versioned SQL. Shared queries are visible to every workspace member."
        actions={<Button asChild><Link to="/app/studio"><Bookmark /> Create in Studio</Link></Button>} />
      <Card className="mb-4 flex flex-wrap items-center gap-2 p-3">
        <div className="relative min-w-52 flex-1">
          <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted" />
          <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search by name or description" className="pl-8" aria-label="Search saved queries" />
        </div>
        <Select value={folder} onChange={(e) => setFolder(e.target.value)} className="w-44" aria-label="Folder">
          <option value="">All folders</option>
          {folders.map((f) => <option key={f} value={f}>{f}</option>)}
        </Select>
        <Checkbox label="Templates only" checked={templates} onChange={(e) => setTemplates(e.target.checked)} />
      </Card>

      {list.isError && <ErrorState message={errorMessage(list.error)} />}
      {list.isLoading ? <Skeleton className="h-72 rounded-xl" /> : grouped.length === 0 ? (
        <EmptyState icon={<Bookmark />} title="No saved queries" description="Save SQL from Query Studio or history to reuse it and build dashboards." />
      ) : grouped.map(([name, items]) => (
        <section key={name} className="mb-6">
          <h2 className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted"><Folder className="size-3.5" />{name}</h2>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {items.map((s) => (
              <Card key={s.id} className="flex flex-col p-4">
                <div className="flex items-start gap-2">
                  <FileCode2 className="mt-0.5 size-4 shrink-0 text-accent" />
                  <div className="min-w-0 flex-1">
                    <h3 className="truncate text-sm font-semibold">{s.name}</h3>
                    <p className="mt-0.5 line-clamp-2 text-xs text-muted">{s.description ?? s.question ?? 'No description'}</p>
                  </div>
                </div>
                <div className="mt-3 flex flex-wrap gap-1.5">
                  <Badge>{s.visibility === 'WORKSPACE' ? <><Globe />shared</> : <><Lock />private</>}</Badge>
                  {s.template && <Badge tone="accent">template</Badge>}
                  <Badge>v{s.currentVersion}</Badge>
                  {s.parameters.length > 0 && <Badge tone="warning">{s.parameters.map((p) => `:${p}`).join(' ')}</Badge>}
                </div>
                <pre className="mt-3 line-clamp-4 overflow-hidden rounded-md bg-bg p-2 font-mono text-[10.5px] text-fg-2">{s.sql}</pre>
                <p className="mt-2 text-[11px] text-muted">{s.dataSourceName} · {s.ownerName ?? 'unknown owner'} · updated {timeAgo(s.updatedAt)}</p>
                <div className="mt-3 flex flex-wrap gap-1.5 border-t border-border pt-3">
                  <Button size="sm" onClick={() => setRunning(s)}><Play /> Run</Button>
                  <Button asChild size="sm" variant="secondary"><Link to={`/app/studio?ds=${s.dataSourceId}&sql=${encodeURIComponent(s.sql)}`}><ExternalLink /> Studio</Link></Button>
                  <div className="ml-auto flex">
                    <Button size="icon-sm" variant="ghost" onClick={() => setVersionsOf(s)} aria-label="Version history"><GitCommitVertical /></Button>
                    {can('ANALYST') && <Button size="icon-sm" variant="ghost" onClick={() => duplicate.mutate(s.id)} aria-label="Duplicate"><Copy /></Button>}
                    {s.canEdit && <Button size="icon-sm" variant="ghost" onClick={() => setEditing(s)} aria-label="Edit or rename"><Pencil /></Button>}
                    {s.canEdit && <Button size="icon-sm" variant="ghost" aria-label="Delete" onClick={async () => {
                      if (await confirm({ title: `Delete “${s.name}”?`, description: 'Dashboard widgets using this query will be removed too.', confirmLabel: 'Delete', danger: true })) remove.mutate(s.id);
                    }}><Trash2 className="text-danger" /></Button>}
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </section>
      ))}
      {editing && <EditDialog query={editing} onClose={() => setEditing(null)} />}
      {versionsOf && <VersionsDialog query={versionsOf} onClose={() => setVersionsOf(null)} />}
      {running && <RunDialog query={running} onClose={() => setRunning(null)} />}
    </div>
  );
}
