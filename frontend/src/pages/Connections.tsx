import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  AlertTriangle, CheckCircle2, CircleDashed, Database, Lock, Pencil, Plus, RefreshCw, Shield, Sparkles, Trash2, Users, XCircle, Zap,
} from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { useWorkspace } from '@/lib/workspace';
import type { ConnectionTestResult, DataSource, Grants, Member, SchemaView } from '@/lib/types';
import { formatDuration, timeAgo } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { useConfirm } from '@/components/ui/confirm';
import { Checkbox, Field, Input, Select } from '@/components/ui/input';
import { EmptyState, ErrorState, Notice, PageHeader, Skeleton } from '@/components/ui/feedback';
import { CsvUpload } from '@/components/upload/CsvUpload';

const IDENT = /^[A-Za-z0-9_$.-]{1,128}$/;
const schema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(120),
  dbType: z.enum(['POSTGRESQL', 'MYSQL']),
  host: z.string().trim().min(1, 'Host is required').regex(/^[A-Za-z0-9.\-:[\]]{1,255}$/, 'Invalid host'),
  port: z.coerce.number().int().min(1).max(65535),
  databaseName: z.string().trim().regex(IDENT, 'Letters, digits, _ $ . - only'),
  schemaName: z.string().trim().refine((v) => v === '' || IDENT.test(v), 'Letters, digits, _ $ . - only'),
  username: z.string().trim().regex(IDENT, 'Letters, digits, _ $ . - only'),
  password: z.string().max(512),
  sslMode: z.enum(['DISABLE', 'PREFER', 'REQUIRE']),
  maxRows: z.coerce.number().int().min(1).max(50000),
  queryTimeoutSec: z.coerce.number().int().min(1).max(600),
  restrictedAccess: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

export function Diagnostics({ result }: { result: ConnectionTestResult }) {
  return (
    <div className="space-y-2">
      <Notice tone={result.success ? 'success' : 'danger'} icon={result.success ? <CheckCircle2 /> : <XCircle />}>
        <p className="font-medium">{result.success ? `Connected in ${formatDuration(result.latencyMs)}` : 'Connection failed'}</p>
        {result.serverVersion && <p className="text-xs text-muted">{result.serverVersion} · {result.tableCount} tables visible</p>}
      </Notice>
      <ol className="space-y-1">
        {result.steps.map((s) => (
          <li key={s.name} className="flex items-start gap-2 text-xs">
            {s.status === 'OK' ? <CheckCircle2 className="mt-0.5 size-3.5 text-success" /> : s.status === 'WARNING' ? <AlertTriangle className="mt-0.5 size-3.5 text-warning" />
              : s.status === 'FAILED' ? <XCircle className="mt-0.5 size-3.5 text-danger" /> : <CircleDashed className="mt-0.5 size-3.5 text-muted" />}
            <span><b className="font-medium text-fg-2">{s.name}</b> <span className="text-muted">— {s.message} ({s.durationMs} ms)</span></span>
          </li>
        ))}
      </ol>
      {result.hint && <p className="rounded-md bg-surface-2 p-2 text-xs text-fg-2">💡 {result.hint}</p>}
    </div>
  );
}

function ConnectionDialog({ open, onOpenChange, existing }: { open: boolean; onOpenChange: (o: boolean) => void; existing?: DataSource }) {
  const { workspaceId } = useWorkspace();
  const qc = useQueryClient();
  const [test, setTest] = useState<ConnectionTestResult | null>(null);
  const form = useForm<FormValues>({ resolver: zodResolver(schema) });
  const dbType = form.watch('dbType');

  useEffect(() => {
    if (!open) return;
    setTest(null);
    form.reset(existing ? {
      name: existing.name, dbType: existing.dbType, host: existing.host, port: existing.port, databaseName: existing.databaseName,
      schemaName: existing.schemaName ?? '', username: existing.username, password: '', sslMode: existing.sslMode,
      maxRows: existing.maxRows, queryTimeoutSec: existing.queryTimeoutSec, restrictedAccess: existing.restrictedAccess,
    } : {
      name: '', dbType: 'POSTGRESQL', host: 'localhost', port: 5432, databaseName: '', schemaName: 'public', username: '', password: '',
      sslMode: 'DISABLE', maxRows: 5000, queryTimeoutSec: 30, restrictedAccess: false,
    });
  }, [open, existing, form]);

  const payload = (v: FormValues) => ({ ...v, workspaceId, schemaName: v.dbType === 'MYSQL' ? null : v.schemaName || null, password: v.password || undefined });

  const testMutation = useMutation({
    mutationFn: (v: FormValues) => api.post<ConnectionTestResult>('/datasources/test', { ...payload(v), dataSourceId: existing?.id }),
    onSuccess: setTest,
    onError: (e) => toast.error(errorMessage(e)),
  });
  const save = useMutation({
    mutationFn: (v: FormValues) => existing ? api.put<DataSource>(`/datasources/${existing.id}`, payload(v)) : api.post<DataSource>('/datasources', payload(v)),
    onSuccess: (ds) => {
      toast.success(existing ? 'Connection updated' : `Connection “${ds.name}” added`);
      qc.invalidateQueries({ queryKey: ['datasources'] });
      qc.invalidateQueries({ queryKey: ['schema', ds.id] });
      onOpenChange(false);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const submit = form.handleSubmit((v) => {
    if (!existing && !v.password) {
      form.setError('password', { message: 'Password is required for a new connection' });
      return;
    }
    save.mutate(v);
  });
  const e = form.formState.errors;

  return (
    <Dialog open={open} onOpenChange={onOpenChange} className="max-w-2xl"
      title={existing ? `Edit ${existing.name}` : 'Add database connection'}
      description="Credentials are encrypted with AES-256-GCM and never returned by the API. Use a read-only database user where possible."
      footer={<>
        <Button variant="secondary" onClick={form.handleSubmit((v) => testMutation.mutate(v))} loading={testMutation.isPending}><Zap /> Test connection</Button>
        <Button onClick={submit} loading={save.isPending}>{existing ? 'Save changes' : 'Add connection'}</Button>
      </>}>
      <form className="grid gap-3 sm:grid-cols-2" onSubmit={submit} noValidate>
        <Field label="Display name" error={e.name?.message} className="sm:col-span-2"><Input {...form.register('name')} placeholder="Production warehouse" /></Field>
        <Field label="Database type" error={e.dbType?.message}>
          <Select {...form.register('dbType', {
            onChange: (ev) => {
              const t = ev.target.value;
              form.setValue('port', t === 'MYSQL' ? 3306 : 5432);
              form.setValue('schemaName', t === 'MYSQL' ? '' : 'public');
            },
          })}>
            <option value="POSTGRESQL">PostgreSQL</option>
            <option value="MYSQL">MySQL</option>
          </Select>
        </Field>
        <Field label="SSL mode" error={e.sslMode?.message}>
          <Select {...form.register('sslMode')}><option value="DISABLE">Disable</option><option value="PREFER">Prefer</option><option value="REQUIRE">Require</option></Select>
        </Field>
        <Field label="Host" error={e.host?.message} hint="Inside Docker use the service name (e.g. postgres)"><Input {...form.register('host')} /></Field>
        <Field label="Port" error={e.port?.message}><Input type="number" {...form.register('port')} /></Field>
        <Field label="Database" error={e.databaseName?.message}><Input {...form.register('databaseName')} /></Field>
        {dbType !== 'MYSQL' && <Field label="Schema" error={e.schemaName?.message} hint="Defaults to public"><Input {...form.register('schemaName')} /></Field>}
        <Field label="Username" error={e.username?.message}><Input autoComplete="off" {...form.register('username')} /></Field>
        <Field label="Password" error={e.password?.message} hint={existing ? 'Leave blank to keep the stored password' : undefined}>
          <Input type="password" autoComplete="new-password" {...form.register('password')} placeholder={existing?.hasPassword ? '••••••••' : ''} />
        </Field>
        <Field label="Row limit per query" error={e.maxRows?.message}><Input type="number" {...form.register('maxRows')} /></Field>
        <Field label="Query timeout (seconds)" error={e.queryTimeoutSec?.message}><Input type="number" {...form.register('queryTimeoutSec')} /></Field>
        <div className="sm:col-span-2">
          <Checkbox label="Restricted: only admins and explicitly granted members can query this source" {...form.register('restrictedAccess')} />
        </div>
        {test && <div className="sm:col-span-2"><Diagnostics result={test} /></div>}
      </form>
    </Dialog>
  );
}

function GrantsDialog({ ds, onClose }: { ds: DataSource; onClose: () => void }) {
  const { workspaceId } = useWorkspace();
  const qc = useQueryClient();
  const grants = useQuery({ queryKey: ['grants', ds.id], queryFn: () => api.get<Grants>(`/datasources/${ds.id}/grants`) });
  const members = useQuery({ queryKey: ['members', workspaceId], queryFn: () => api.get<Member[]>(`/workspaces/${workspaceId}/members`) });
  const [restricted, setRestricted] = useState(ds.restrictedAccess);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  useEffect(() => {
    if (grants.data) {
      setRestricted(grants.data.restrictedAccess);
      setSelected(new Set(grants.data.users.map((u) => u.userId)));
    }
  }, [grants.data]);
  const save = useMutation({
    mutationFn: () => api.put<Grants>(`/datasources/${ds.id}/grants`, { restrictedAccess: restricted, userIds: [...selected] }),
    onSuccess: () => { toast.success('Permissions updated'); qc.invalidateQueries({ queryKey: ['datasources'] }); onClose(); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={`Permissions · ${ds.name}`}
      description="Workspace owners and admins can always query. Analysts and viewers need a grant when the source is restricted."
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button onClick={() => save.mutate()} loading={save.isPending}>Save</Button></>}>
      {grants.isLoading || members.isLoading ? <Skeleton className="h-32" /> : (
        <div className="space-y-3">
          <Checkbox label="Restrict access to granted members" checked={restricted} onChange={(e) => setRestricted(e.target.checked)} />
          <div className={restricted ? '' : 'pointer-events-none opacity-50'}>
            {(members.data ?? []).filter((m) => m.role === 'ANALYST' || m.role === 'VIEWER').map((m) => (
              <label key={m.userId} className="flex items-center gap-2 border-b border-border py-2 text-sm">
                <input type="checkbox" className="size-4 accent-[var(--accent)]" checked={selected.has(m.userId)}
                  onChange={(e) => { const n = new Set(selected); if (e.target.checked) n.add(m.userId); else n.delete(m.userId); setSelected(n); }} />
                <span>{m.fullName}</span><span className="text-xs text-muted">{m.email}</span><Badge className="ml-auto">{m.role.toLowerCase()}</Badge>
              </label>
            ))}
            {(members.data ?? []).filter((m) => m.role === 'ANALYST' || m.role === 'VIEWER').length === 0 && (
              <p className="text-xs text-muted">No analysts or viewers in this workspace yet.</p>
            )}
          </div>
        </div>
      )}
    </Dialog>
  );
}

export default function Connections() {
  const { dataSources, dataSourcesLoading, can, setDataSourceId } = useWorkspace();
  const qc = useQueryClient();
  const confirm = useConfirm();
  const [editing, setEditing] = useState<DataSource | undefined>();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [grantsFor, setGrantsFor] = useState<DataSource | null>(null);
  const [testResult, setTestResult] = useState<{ ds: DataSource; result: ConnectionTestResult } | null>(null);
  const isAdmin = can('ADMIN');

  const test = useMutation({
    mutationFn: (ds: DataSource) => api.post<ConnectionTestResult>(`/datasources/${ds.id}/test`).then((result) => ({ ds, result })),
    onSuccess: (r) => { setTestResult(r); qc.invalidateQueries({ queryKey: ['datasources'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const refresh = useMutation({
    mutationFn: (ds: DataSource) => api.post<SchemaView>(`/datasources/${ds.id}/schema/refresh`),
    onSuccess: (s, ds) => { toast.success(`Schema refreshed: ${s.tableCount} tables`); qc.setQueryData(['schema', ds.id], s); qc.invalidateQueries({ queryKey: ['datasources'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: (ds: DataSource) => api.delete(`/datasources/${ds.id}`),
    onSuccess: () => { toast.success('Connection removed'); qc.invalidateQueries({ queryKey: ['datasources'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <div>
      <PageHeader title="Connections" description="The databases this workspace can understand — PostgreSQL, MySQL, and the spreadsheets you upload."
        actions={isAdmin && <Button onClick={() => { setEditing(undefined); setDialogOpen(true); }}><Plus /> Add connection</Button>} />

      {!isAdmin && <Notice className="mb-4" icon={<Lock />}>Only workspace owners and admins can add or change connections.</Notice>}

      {dataSourcesLoading ? (
        <div className="grid gap-4 md:grid-cols-2">{[0, 1].map((i) => <Skeleton key={i} className="h-44 rounded-xl" />)}</div>
      ) : dataSources.length === 0 ? (
        <EmptyState icon={<Database />} title="No connections yet"
          description="Add a PostgreSQL or MySQL connection. We recommend a database user with read-only (SELECT) privileges."
          action={isAdmin && <Button onClick={() => { setEditing(undefined); setDialogOpen(true); }}><Plus /> Add connection</Button>} />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {dataSources.map((ds) => (
            <Card key={ds.id} className="flex flex-col p-6 transition-all duration-500 hover:border-border-strong hover:shadow-[0_30px_80px_-40px_rgb(var(--glow)/0.6)]">
              <div className="flex items-start gap-3">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-accent-soft text-accent"><Database className="size-5" /></div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="truncate font-semibold">{ds.name}</h3>
                    <Badge tone={ds.status === 'CONNECTED' ? 'success' : ds.status === 'FAILED' ? 'danger' : 'neutral'}>
                      {ds.status === 'CONNECTED' ? <CheckCircle2 /> : ds.status === 'FAILED' ? <XCircle /> : <CircleDashed />}{ds.status.toLowerCase()}
                    </Badge>
                    {ds.restrictedAccess && <Badge tone="warning"><Shield /> restricted</Badge>}
                  </div>
                  <p className="mt-1 truncate font-mono text-xs text-muted">{ds.dbType === 'POSTGRESQL' ? 'postgresql' : 'mysql'}://{ds.username}@{ds.host}:{ds.port}/{ds.databaseName}</p>
                  <p className="mt-0.5 text-xs text-muted">schema {ds.effectiveSchema} · {ds.maxRows.toLocaleString()} rows · {ds.queryTimeoutSec}s timeout · tested {timeAgo(ds.lastTestedAt)}</p>
                  {ds.serverVersion && <p className="mt-0.5 truncate text-xs text-muted">{ds.serverVersion}</p>}
                </div>
              </div>
              {ds.lastError && <Notice tone="danger" className="mt-3 text-xs" icon={<AlertTriangle />}>{ds.lastError}</Notice>}
              <div className="mt-4 flex flex-wrap gap-2 border-t border-border pt-4">
                <Button size="sm" variant="secondary" onClick={() => test.mutate(ds)} loading={test.isPending && test.variables?.id === ds.id}><Zap /> Test</Button>
                {ds.canQuery && <Button size="sm" variant="secondary" onClick={() => refresh.mutate(ds)} loading={refresh.isPending && refresh.variables?.id === ds.id}><RefreshCw /> Refresh schema</Button>}
                <Button asChild size="sm" variant="secondary"><Link to="/app/schema" onClick={() => setDataSourceId(ds.id)}>Schema</Link></Button>
                {ds.canQuery && <Button asChild size="sm" variant="secondary"><Link to="/app/studio" onClick={() => setDataSourceId(ds.id)}><Sparkles /> Query</Link></Button>}
                {ds.canManage && (
                  <div className="ml-auto flex gap-1">
                    <Button size="icon-sm" variant="ghost" onClick={() => setGrantsFor(ds)} aria-label="Permissions"><Users /></Button>
                    <Button size="icon-sm" variant="ghost" onClick={() => { setEditing(ds); setDialogOpen(true); }} aria-label="Edit"><Pencil /></Button>
                    <Button size="icon-sm" variant="ghost" aria-label="Delete" onClick={async () => {
                      if (await confirm({ title: `Remove ${ds.name}?`, description: 'Saved queries, dashboard widgets, conversations and glossary terms for this connection will be deleted. Query history is kept.', confirmLabel: 'Remove', danger: true })) remove.mutate(ds);
                    }}><Trash2 className="text-danger" /></Button>
                  </div>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      {test.isError && <ErrorState className="mt-4" message={errorMessage(test.error)} />}
      <CsvUpload />
      <ConnectionDialog open={dialogOpen} onOpenChange={setDialogOpen} existing={editing} />
      {grantsFor && <GrantsDialog ds={grantsFor} onClose={() => setGrantsFor(null)} />}
      {testResult && (
        <Dialog open onOpenChange={(o) => !o && setTestResult(null)} title={`Diagnostics · ${testResult.ds.name}`}
          footer={<Button onClick={() => setTestResult(null)}>Close</Button>}>
          <Diagnostics result={testResult.result} />
        </Dialog>
      )}
    </div>
  );
}
