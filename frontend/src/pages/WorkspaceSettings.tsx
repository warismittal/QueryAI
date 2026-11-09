import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Building2, CheckCircle2, Gauge, Plus, ScrollText, Shield, Trash2, UserPlus, Users, XCircle } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useWorkspace } from '@/lib/workspace';
import type { AuditEvent, Member, Page, Usage, Workspace, WorkspaceRole } from '@/lib/types';
import { formatDateTime } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { useConfirm } from '@/components/ui/confirm';
import { Field, Input, Select, Textarea } from '@/components/ui/input';
import { EmptyState, ErrorState, Notice, PageHeader, Pagination, Skeleton } from '@/components/ui/feedback';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

const ROLES: WorkspaceRole[] = ['OWNER', 'ADMIN', 'ANALYST', 'VIEWER'];
const ROLE_HELP: Record<WorkspaceRole, string> = {
  OWNER: 'Everything, including deleting the workspace',
  ADMIN: 'Manage members, connections, permissions; view audit logs',
  ANALYST: 'Ask questions, run SQL, save queries, build dashboards',
  VIEWER: 'View and run shared queries and dashboards',
};

export const AUDIT_ACTIONS = [
  'LOGIN', 'LOGIN_FAILED', 'USER_REGISTERED', 'WORKSPACE_CREATED', 'WORKSPACE_UPDATED', 'MEMBER_ADDED', 'MEMBER_ROLE_CHANGED', 'MEMBER_REMOVED',
  'DATASOURCE_CREATED', 'DATASOURCE_UPDATED', 'DATASOURCE_DELETED', 'DATASOURCE_TESTED', 'DATASOURCE_GRANTS_UPDATED', 'SCHEMA_REFRESHED',
  'GLOSSARY_UPDATED', 'ANNOTATION_UPDATED', 'SQL_GENERATED', 'QUERY_EXECUTED', 'QUERY_BLOCKED', 'QUERY_FAILED', 'SAVED_QUERY_CREATED',
  'SAVED_QUERY_UPDATED', 'SAVED_QUERY_DELETED', 'DASHBOARD_CREATED', 'DASHBOARD_UPDATED', 'DASHBOARD_DELETED', 'EVALUATION_RUN', 'ADMIN_USER_UPDATED',
];

export function AuditTable({ workspaceId }: { workspaceId?: number }) {
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  const audit = useQuery({
    queryKey: ['audit', workspaceId ?? 'all', action, page],
    queryFn: () => api.get<Page<AuditEvent>>('/audit', { workspaceId, action, page, size: 25 }),
    placeholderData: keepPreviousData,
  });
  return (
    <Card>
      <CardHeader title="Audit log" description="Security-relevant events, newest first."
        actions={<Select value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }} className="h-8 w-56 text-xs" aria-label="Filter by action">
          <option value="">All actions</option>{AUDIT_ACTIONS.map((a) => <option key={a} value={a}>{a.toLowerCase().replace(/_/g, ' ')}</option>)}
        </Select>} />
      {audit.isError ? <div className="p-5"><ErrorState message={errorMessage(audit.error)} /></div> : audit.isLoading ? <div className="p-5"><Skeleton className="h-60" /></div> : (
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead><tr className="border-b border-border text-left text-muted"><th className="px-5 py-2">Time</th><th>Action</th><th>User</th><th>Resource</th><th>Details</th><th className="pr-5">IP</th></tr></thead>
            <tbody>
              {audit.data?.content.map((e) => (
                <tr key={e.id} className="border-b border-border/60 align-top">
                  <td className="whitespace-nowrap px-5 py-2 text-muted">{formatDateTime(e.createdAt)}</td>
                  <td className="py-2 pr-3"><span className="flex items-center gap-1">{e.success ? <CheckCircle2 className="size-3 text-success" /> : <XCircle className="size-3 text-danger" />}{e.action.toLowerCase().replace(/_/g, ' ')}</span></td>
                  <td className="py-2 pr-3">{e.userEmail ?? '—'}</td>
                  <td className="py-2 pr-3 text-muted">{e.resourceType ? `${e.resourceType.toLowerCase()} #${e.resourceId}` : '—'}</td>
                  <td className="max-w-80 truncate py-2 pr-3 font-mono text-[10px] text-muted" title={e.details ? JSON.stringify(e.details) : ''}>{e.details ? JSON.stringify(e.details) : ''}</td>
                  <td className="py-2 pr-5 font-mono text-[10px] text-muted">{e.ipAddress}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {audit.data?.content.length === 0 && <p className="p-5 text-center text-sm text-muted">No events.</p>}
          {audit.data && <div className="p-3"><Pagination page={audit.data.page} totalPages={audit.data.totalPages} onPage={setPage} /></div>}
        </div>
      )}
    </Card>
  );
}

function CreateWorkspaceDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient();
  const { setWorkspaceId } = useWorkspace();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const create = useMutation({
    mutationFn: () => api.post<Workspace>('/workspaces', { name, description }),
    onSuccess: async (w) => { await qc.invalidateQueries({ queryKey: ['workspaces'] }); setWorkspaceId(w.id); toast.success(`Workspace “${w.name}” created`); onOpenChange(false); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title="Create workspace" description="Workspaces isolate connections, queries, dashboards and members."
      footer={<><Button variant="secondary" onClick={() => onOpenChange(false)}>Cancel</Button><Button onClick={() => create.mutate()} loading={create.isPending} disabled={!name.trim()}>Create</Button></>}>
      <div className="space-y-3">
        <Field label="Name"><Input value={name} onChange={(e) => setName(e.target.value)} maxLength={120} autoFocus /></Field>
        <Field label="Description"><Textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} /></Field>
      </div>
    </Dialog>
  );
}

export default function WorkspaceSettings() {
  const { user } = useAuth();
  const { workspace, workspaceId, can, workspaces, setWorkspaceId } = useWorkspace();
  const qc = useQueryClient();
  const confirm = useConfirm();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [creating, setCreating] = useState(params.get('new') === '1');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [invite, setInvite] = useState({ email: '', role: 'ANALYST' as WorkspaceRole });
  useEffect(() => { setName(workspace?.name ?? ''); setDescription(workspace?.description ?? ''); }, [workspace]);
  useEffect(() => { if (!creating && params.get('new')) setParams({}, { replace: true }); }, [creating, params, setParams]);

  const members = useQuery({ queryKey: ['members', workspaceId], queryFn: () => api.get<Member[]>(`/workspaces/${workspaceId}/members`), enabled: !!workspaceId });
  const usage = useQuery({ queryKey: ['usage', workspaceId], queryFn: () => api.get<Usage>(`/workspaces/${workspaceId}/usage`), enabled: !!workspaceId });

  const update = useMutation({
    mutationFn: () => api.put<Workspace>(`/workspaces/${workspaceId}`, { name, description }),
    onSuccess: () => { toast.success('Workspace updated'); qc.invalidateQueries({ queryKey: ['workspaces'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: () => api.delete(`/workspaces/${workspaceId}`),
    onSuccess: async () => {
      toast.success('Workspace deleted');
      const next = workspaces.find((w) => w.id !== workspaceId);
      if (next) setWorkspaceId(next.id);
      await qc.invalidateQueries({ queryKey: ['workspaces'] });
      navigate('/app');
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const add = useMutation({
    mutationFn: () => api.post<Member>(`/workspaces/${workspaceId}/members`, invite),
    onSuccess: (m) => { toast.success(`${m.fullName} added as ${m.role.toLowerCase()}`); setInvite({ email: '', role: 'ANALYST' }); qc.invalidateQueries({ queryKey: ['members', workspaceId] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const changeRole = useMutation({
    mutationFn: ({ userId, role }: { userId: number; role: WorkspaceRole }) => api.put<Member>(`/workspaces/${workspaceId}/members/${userId}`, { role }),
    onSuccess: () => { toast.success('Role updated'); qc.invalidateQueries({ queryKey: ['members', workspaceId] }); qc.invalidateQueries({ queryKey: ['workspaces'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const removeMember = useMutation({
    mutationFn: (userId: number) => api.delete(`/workspaces/${workspaceId}/members/${userId}`),
    onSuccess: (_, userId) => {
      qc.invalidateQueries({ queryKey: ['members', workspaceId] });
      if (userId === user?.id) { qc.invalidateQueries({ queryKey: ['workspaces'] }); navigate('/app'); }
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const isAdmin = can('ADMIN');
  return (
    <div>
      <PageHeader title="Workspace" description={workspace ? `${workspace.name} · your role: ${workspace.myRole?.toLowerCase()}` : undefined}
        actions={<Button variant="secondary" onClick={() => setCreating(true)}><Plus /> New workspace</Button>} />
      <Tabs defaultValue="members">
        <TabsList className="mb-4">
          <TabsTrigger value="members"><Users /> Members</TabsTrigger>
          <TabsTrigger value="general"><Building2 /> General</TabsTrigger>
          <TabsTrigger value="usage"><Gauge /> Usage & quotas</TabsTrigger>
          {isAdmin && <TabsTrigger value="audit"><ScrollText /> Audit log</TabsTrigger>}
        </TabsList>

        <TabsContent value="members" className="space-y-4">
          {isAdmin && (
            <Card>
              <CardHeader title="Add member" description="The person must already have a QueryAI account." />
              <CardBody>
                <form className="flex flex-wrap items-end gap-3" onSubmit={(e) => { e.preventDefault(); add.mutate(); }}>
                  <Field label="Email" className="min-w-60 flex-1"><Input type="email" value={invite.email} onChange={(e) => setInvite({ ...invite, email: e.target.value })} placeholder="teammate@company.com" /></Field>
                  <Field label="Role" className="w-44">
                    <Select value={invite.role} onChange={(e) => setInvite({ ...invite, role: e.target.value as WorkspaceRole })}>
                      {ROLES.filter((r) => r !== 'OWNER' || can('OWNER')).map((r) => <option key={r} value={r}>{r.toLowerCase()}</option>)}
                    </Select>
                  </Field>
                  <Button type="submit" loading={add.isPending} disabled={!/.+@.+\..+/.test(invite.email)}><UserPlus /> Add</Button>
                </form>
                <p className="mt-2 text-xs text-muted">{ROLE_HELP[invite.role]}</p>
              </CardBody>
            </Card>
          )}
          <Card>
            <CardHeader title={`Members (${members.data?.length ?? '…'})`} description={<>Per-connection access is managed on the <Link className="text-accent hover:underline" to="/app/connections">Connections</Link> page (<Shield className="inline size-3" /> permissions).</>} />
            {members.isLoading ? <div className="p-5"><Skeleton className="h-32" /></div> : members.isError ? <div className="p-5"><ErrorState message={errorMessage(members.error)} /></div> : (
              <div className="divide-y divide-border">
                {members.data?.map((m) => (
                  <div key={m.userId} className="flex flex-wrap items-center gap-3 px-5 py-3">
                    <div className="flex size-8 items-center justify-center rounded-full bg-surface-3 text-xs font-semibold">{m.fullName.split(' ').map((p) => p[0]).slice(0, 2).join('')}</div>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm">{m.fullName} {m.userId === user?.id && <Badge>you</Badge>}</p>
                      <p className="text-xs text-muted">{m.email} · joined {formatDateTime(m.joinedAt)}</p>
                    </div>
                    {isAdmin && m.userId !== user?.id ? (
                      <Select value={m.role} className="h-8 w-32 text-xs" aria-label={`Role of ${m.fullName}`}
                        onChange={(e) => changeRole.mutate({ userId: m.userId, role: e.target.value as WorkspaceRole })}>
                        {ROLES.filter((r) => can('OWNER') || (r !== 'OWNER' && m.role !== 'OWNER') || r === m.role).map((r) => <option key={r} value={r}>{r.toLowerCase()}</option>)}
                      </Select>
                    ) : <Badge tone="accent">{m.role.toLowerCase()}</Badge>}
                    {(isAdmin || m.userId === user?.id) && (
                      <Button size="icon-sm" variant="ghost" aria-label={m.userId === user?.id ? 'Leave workspace' : `Remove ${m.fullName}`} onClick={async () => {
                        const self = m.userId === user?.id;
                        if (await confirm({ title: self ? 'Leave this workspace?' : `Remove ${m.fullName}?`, description: self ? 'You will lose access to its connections, queries and dashboards.' : undefined, confirmLabel: self ? 'Leave' : 'Remove', danger: true })) removeMember.mutate(m.userId);
                      }}><Trash2 className="text-danger" /></Button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </Card>
        </TabsContent>

        <TabsContent value="general">
          <Card>
            <CardHeader title="Workspace details" />
            <CardBody className="max-w-xl space-y-3">
              <Field label="Name"><Input value={name} onChange={(e) => setName(e.target.value)} disabled={!isAdmin} maxLength={120} /></Field>
              <Field label="Description"><Textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} disabled={!isAdmin} maxLength={500} /></Field>
              {isAdmin && <Button onClick={() => update.mutate()} loading={update.isPending} disabled={!name.trim()}>Save changes</Button>}
            </CardBody>
          </Card>
          {can('OWNER') && (
            <Card className="mt-4 border-danger/30">
              <CardHeader title="Danger zone" description="Deleting a workspace permanently removes its connections, history, saved queries and dashboards." />
              <CardBody>
                <Button variant="danger" loading={remove.isPending} onClick={async () => {
                  if (await confirm({ title: `Delete “${workspace?.name}”?`, description: 'This cannot be undone.', confirmLabel: 'Delete workspace', danger: true })) remove.mutate();
                }}><Trash2 /> Delete workspace</Button>
              </CardBody>
            </Card>
          )}
        </TabsContent>

        <TabsContent value="usage">
          <Card>
            <CardHeader title="Monthly usage" description="AI questions consume the workspace quota; SQL runs are unlimited. Platform administrators set quotas." />
            <CardBody>
              {usage.isLoading ? <Skeleton className="h-24" /> : usage.data && (
                <div className="grid gap-4 sm:grid-cols-4">
                  {[['AI questions', usage.data.aiQueriesUsed], ['Monthly quota', usage.data.quota], ['Remaining', usage.data.remaining], ['All queries', usage.data.totalQueries]].map(([k, v]) => (
                    <div key={k as string} className="rounded-lg border border-border p-4"><p className="text-xs text-muted">{k}</p><p className="mt-1 text-2xl font-semibold">{(v as number).toLocaleString()}</p></div>
                  ))}
                  <p className="text-xs text-muted sm:col-span-4">Period started {formatDateTime(usage.data.periodStart)} (UTC month).</p>
                  {usage.data.remaining === 0 && <Notice tone="warning" className="sm:col-span-4">The AI quota is exhausted for this month. SQL editing and execution still work.</Notice>}
                </div>
              )}
            </CardBody>
          </Card>
        </TabsContent>

        {isAdmin && <TabsContent value="audit">{workspaceId ? <AuditTable workspaceId={workspaceId} /> : <EmptyState title="No workspace" />}</TabsContent>}
      </Tabs>
      <CreateWorkspaceDialog open={creating} onOpenChange={setCreating} />
    </div>
  );
}
