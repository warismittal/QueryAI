import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Activity, Building2, Cpu, Database, Search, Users } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import type { Page, PlatformStats, User, Workspace } from '@/lib/types';
import { formatDateTime, timeAgo } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardHeader } from '@/components/ui/card';
import { useConfirm } from '@/components/ui/confirm';
import { Input } from '@/components/ui/input';
import { ErrorState, PageHeader, Pagination, Skeleton } from '@/components/ui/feedback';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { AuditTable } from './WorkspaceSettings';

function Stat({ icon: Icon, label, value }: { icon: typeof Users; label: string; value: string | number }) {
  return (
    <Card className="p-4">
      <p className="flex items-center gap-1.5 text-xs text-muted"><Icon className="size-3.5" />{label}</p>
      <p className="mt-1 text-2xl font-semibold">{typeof value === 'number' ? value.toLocaleString() : value}</p>
    </Card>
  );
}

function QuotaEditor({ ws }: { ws: Workspace }) {
  const qc = useQueryClient();
  const [value, setValue] = useState(String(ws.monthlyQueryQuota));
  const save = useMutation({
    mutationFn: () => api.put<Workspace>(`/admin/workspaces/${ws.id}/quota`, { monthlyQueryQuota: Number(value) }),
    onSuccess: () => { toast.success(`Quota for ${ws.name} updated`); qc.invalidateQueries({ queryKey: ['admin-workspaces'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const valid = /^\d+$/.test(value) && Number(value) <= 1_000_000;
  return (
    <div className="flex items-center gap-2">
      <Input value={value} onChange={(e) => setValue(e.target.value)} className="h-8 w-28 text-xs" aria-label={`Monthly AI quota for ${ws.name}`} inputMode="numeric" />
      <Button size="sm" variant="secondary" disabled={!valid || Number(value) === ws.monthlyQueryQuota} loading={save.isPending} onClick={() => save.mutate()}>Set</Button>
    </div>
  );
}

export default function Admin() {
  const { user: me } = useAuth();
  const qc = useQueryClient();
  const confirm = useConfirm();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [wsPage, setWsPage] = useState(0);
  const stats = useQuery({ queryKey: ['admin-stats'], queryFn: () => api.get<PlatformStats>('/admin/stats') });
  const users = useQuery({ queryKey: ['admin-users', q, page], queryFn: () => api.get<Page<User>>('/admin/users', { q, page, size: 20 }), placeholderData: keepPreviousData });
  const workspaces = useQuery({ queryKey: ['admin-workspaces', wsPage], queryFn: () => api.get<Page<Workspace>>('/admin/workspaces', { page: wsPage, size: 20 }), placeholderData: keepPreviousData });
  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: { systemRole?: string; enabled?: boolean } }) => api.put<User>(`/admin/users/${id}`, body),
    onSuccess: () => { toast.success('User updated'); qc.invalidateQueries({ queryKey: ['admin-users'] }); qc.invalidateQueries({ queryKey: ['admin-stats'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <div>
      <PageHeader title="Administration" description="Platform-wide users, workspaces, quotas and audit trail. Restricted to platform administrators." />
      {stats.isError && <ErrorState message={errorMessage(stats.error)} />}
      <div className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-5">
        {!stats.data ? Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-20 rounded-xl" />) : (
          <>
            <Stat icon={Users} label="Users (enabled)" value={`${stats.data.enabledUsers}/${stats.data.users}`} />
            <Stat icon={Building2} label="Workspaces" value={stats.data.workspaces} />
            <Stat icon={Database} label="Data sources" value={stats.data.dataSources} />
            <Stat icon={Activity} label="Queries 24h / 30d" value={`${stats.data.queriesLast24h} / ${stats.data.queriesLast30d}`} />
            <Stat icon={Cpu} label="AI model" value={stats.data.llmConfigured ? 'configured' : 'not configured'} />
          </>
        )}
      </div>
      <Tabs defaultValue="users">
        <TabsList className="mb-4">
          <TabsTrigger value="users">Users</TabsTrigger>
          <TabsTrigger value="workspaces">Workspaces & quotas</TabsTrigger>
          <TabsTrigger value="audit">Platform audit log</TabsTrigger>
        </TabsList>
        <TabsContent value="users">
          <Card>
            <CardHeader title="Users" actions={
              <div className="relative"><Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted" />
                <Input value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} placeholder="Search name or email" className="h-8 w-60 pl-8 text-xs" aria-label="Search users" /></div>} />
            {users.isLoading ? <div className="p-5"><Skeleton className="h-48" /></div> : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead><tr className="border-b border-border text-left text-xs text-muted"><th className="px-5 py-2">User</th><th>Role</th><th>Status</th><th>Last login</th><th>Joined</th><th className="pr-5 text-right">Actions</th></tr></thead>
                  <tbody>
                    {users.data?.content.map((u) => (
                      <tr key={u.id} className="border-b border-border/60">
                        <td className="px-5 py-2.5"><p>{u.fullName}</p><p className="text-xs text-muted">{u.email}</p></td>
                        <td><Badge tone={u.systemRole === 'ADMIN' ? 'accent' : 'neutral'}>{u.systemRole.toLowerCase()}</Badge></td>
                        <td><Badge tone={u.enabled ? 'success' : 'danger'}>{u.enabled ? 'active' : 'disabled'}</Badge></td>
                        <td className="text-xs text-muted">{timeAgo(u.lastLoginAt)}</td>
                        <td className="text-xs text-muted">{formatDateTime(u.createdAt)}</td>
                        <td className="pr-5 text-right">
                          {u.id === me?.id ? <span className="text-xs text-muted">you</span> : (
                            <div className="flex justify-end gap-1.5">
                              <Button size="sm" variant="secondary" onClick={() => update.mutate({ id: u.id, body: { systemRole: u.systemRole === 'ADMIN' ? 'USER' : 'ADMIN' } })}>
                                {u.systemRole === 'ADMIN' ? 'Revoke admin' : 'Make admin'}
                              </Button>
                              <Button size="sm" variant={u.enabled ? 'outline' : 'secondary'} onClick={async () => {
                                if (!u.enabled || await confirm({ title: `Disable ${u.fullName}?`, description: 'They will be signed out immediately and cannot log in.', confirmLabel: 'Disable', danger: true })) {
                                  update.mutate({ id: u.id, body: { enabled: !u.enabled } });
                                }
                              }}>{u.enabled ? 'Disable' : 'Enable'}</Button>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {users.data && <div className="p-3"><Pagination page={users.data.page} totalPages={users.data.totalPages} onPage={setPage} /></div>}
              </div>
            )}
          </Card>
        </TabsContent>
        <TabsContent value="workspaces">
          <Card>
            <CardHeader title="Workspaces" description="Monthly AI question quota per workspace." />
            {workspaces.isLoading ? <div className="p-5"><Skeleton className="h-48" /></div> : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead><tr className="border-b border-border text-left text-xs text-muted"><th className="px-5 py-2">Workspace</th><th>Members</th><th>Created</th><th className="pr-5">Monthly AI quota</th></tr></thead>
                  <tbody>
                    {workspaces.data?.content.map((w) => (
                      <tr key={w.id} className="border-b border-border/60">
                        <td className="px-5 py-2.5"><p>{w.name}</p><p className="text-xs text-muted">{w.description}</p></td>
                        <td>{w.memberCount}</td>
                        <td className="text-xs text-muted">{formatDateTime(w.createdAt)}</td>
                        <td className="pr-5"><QuotaEditor ws={w} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {workspaces.data && <div className="p-3"><Pagination page={workspaces.data.page} totalPages={workspaces.data.totalPages} onPage={setWsPage} /></div>}
              </div>
            )}
          </Card>
        </TabsContent>
        <TabsContent value="audit"><AuditTable /></TabsContent>
      </Tabs>
    </div>
  );
}
