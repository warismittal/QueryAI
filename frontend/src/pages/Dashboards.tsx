import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Globe, LayoutDashboard, Lock, Plus, Trash2 } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { useWorkspace } from '@/lib/workspace';
import type { DashboardSummary, Visibility } from '@/lib/types';
import { timeAgo } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { useConfirm } from '@/components/ui/confirm';
import { Field, Input, Select, Textarea } from '@/components/ui/input';
import { EmptyState, ErrorState, PageHeader, Skeleton } from '@/components/ui/feedback';

export function DashboardFormDialog({ open, onOpenChange, existing }: { open: boolean; onOpenChange: (o: boolean) => void; existing?: DashboardSummary }) {
  const { workspaceId } = useWorkspace();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [name, setName] = useState(existing?.name ?? '');
  const [description, setDescription] = useState(existing?.description ?? '');
  const [visibility, setVisibility] = useState<Visibility>(existing?.visibility ?? 'PRIVATE');
  const save = useMutation({
    mutationFn: () => {
      const body = { workspaceId, name, description, visibility };
      return existing ? api.put<DashboardSummary>(`/dashboards/${existing.id}`, body) : api.post<DashboardSummary>('/dashboards', body);
    },
    onSuccess: (d) => {
      qc.invalidateQueries({ queryKey: ['dashboards'] });
      qc.invalidateQueries({ queryKey: ['dashboard', d.id] });
      onOpenChange(false);
      if (!existing) navigate(`/app/dashboards/${d.id}?edit=1`);
      else toast.success('Dashboard updated');
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={existing ? 'Dashboard settings' : 'New dashboard'}
      footer={<><Button variant="secondary" onClick={() => onOpenChange(false)}>Cancel</Button>
        <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!name.trim()}>{existing ? 'Save' : 'Create'}</Button></>}>
      <div className="space-y-3">
        <Field label="Name"><Input value={name} onChange={(e) => setName(e.target.value)} maxLength={160} autoFocus /></Field>
        <Field label="Description"><Textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={2000} /></Field>
        <Field label="Sharing" hint="Shared dashboards can only use queries that are shared with the workspace.">
          <Select value={visibility} onChange={(e) => setVisibility(e.target.value as Visibility)}>
            <option value="PRIVATE">Private (only me)</option>
            <option value="WORKSPACE">Shared with all workspace members</option>
          </Select>
        </Field>
      </div>
    </Dialog>
  );
}

export default function Dashboards() {
  const { workspaceId, can } = useWorkspace();
  const qc = useQueryClient();
  const confirm = useConfirm();
  const [creating, setCreating] = useState(false);
  const list = useQuery({ queryKey: ['dashboards', workspaceId], queryFn: () => api.get<DashboardSummary[]>('/dashboards', { workspaceId: workspaceId! }), enabled: !!workspaceId });
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/dashboards/${id}`),
    onSuccess: () => { toast.success('Dashboard deleted'); qc.invalidateQueries({ queryKey: ['dashboards'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <div>
      <PageHeader title="Dashboards" description="Live dashboards built from saved queries. Every widget runs against the database when opened."
        actions={can('ANALYST') && <Button onClick={() => setCreating(true)}><Plus /> New dashboard</Button>} />
      {list.isError && <ErrorState message={errorMessage(list.error)} />}
      {list.isLoading ? <div className="grid gap-4 md:grid-cols-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-36 rounded-xl" />)}</div>
        : list.data?.length === 0 ? (
          <EmptyState icon={<LayoutDashboard />} title="No dashboards yet" description="Create a dashboard and add widgets from your saved queries."
            action={can('ANALYST') && <Button onClick={() => setCreating(true)}><Plus /> New dashboard</Button>} />
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {list.data?.map((d) => (
              <Card key={d.id} className="group relative p-5 transition-colors hover:border-accent/40">
                <Link to={`/app/dashboards/${d.id}`} className="absolute inset-0" aria-label={`Open ${d.name}`} />
                <div className="flex items-start gap-3">
                  <div className="flex size-10 items-center justify-center rounded-lg bg-accent-soft text-accent"><LayoutDashboard className="size-5" /></div>
                  <div className="min-w-0 flex-1">
                    <h3 className="truncate font-semibold">{d.name}</h3>
                    <p className="line-clamp-2 text-xs text-muted">{d.description || 'No description'}</p>
                  </div>
                </div>
                <div className="mt-4 flex items-center gap-2 text-xs text-muted">
                  <Badge>{d.visibility === 'WORKSPACE' ? <><Globe />shared</> : <><Lock />private</>}</Badge>
                  <span>{d.widgetCount} widgets · {d.ownerName} · {timeAgo(d.updatedAt)}</span>
                  {d.canEdit && (
                    <Button size="icon-sm" variant="ghost" className="relative z-10 ml-auto" aria-label="Delete dashboard" onClick={async () => {
                      if (await confirm({ title: `Delete “${d.name}”?`, confirmLabel: 'Delete', danger: true })) remove.mutate(d.id);
                    }}><Trash2 className="text-danger" /></Button>
                  )}
                </div>
              </Card>
            ))}
          </div>
        )}
      {creating && <DashboardFormDialog open onOpenChange={setCreating} />}
    </div>
  );
}
