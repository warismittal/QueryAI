import { useEffect, useMemo, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Responsive, WidthProvider, type Layout } from 'react-grid-layout';
import 'react-grid-layout/css/styles.css';
import { toast } from 'sonner';
import { ArrowLeft, Check, Globe, GripVertical, Lock, Pencil, Plus, RefreshCw, Settings2, Trash2, XCircle } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { useWorkspace } from '@/lib/workspace';
import type { ChartType, DashboardDetail, ExecuteResponse, SavedQuery, Widget } from '@/lib/types';
import { formatDuration, timeAgo } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Dialog } from '@/components/ui/dialog';
import { useConfirm } from '@/components/ui/confirm';
import { Field, Input, Select } from '@/components/ui/input';
import { EmptyState, ErrorState, Notice, Skeleton } from '@/components/ui/feedback';
import { ResultChart } from '@/components/results/ResultChart';
import { ResultsTable, useTableState } from '@/components/results/ResultsTable';
import { DashboardFormDialog } from './Dashboards';

const Grid = WidthProvider(Responsive);
const CHARTS: ChartType[] = ['BAR', 'LINE', 'PIE', 'SCATTER', 'METRIC', 'TABLE'];

function WidgetTable({ data }: { data: NonNullable<ExecuteResponse['result']> }) {
  const state = useTableState(data);
  return <ResultsTable result={data} state={state} />;
}

function WidgetBody({ dashboardId, widget, refreshKey }: { dashboardId: number; widget: Widget; refreshKey: number }) {
  const data = useQuery({
    queryKey: ['widget', dashboardId, widget.id, widget.savedQueryId, refreshKey],
    queryFn: () => api.post<ExecuteResponse>(`/dashboards/${dashboardId}/widgets/${widget.id}/data`),
    staleTime: 60_000,
  });
  if (data.isLoading) return <Skeleton className="m-3 h-[calc(100%-24px)]" />;
  if (data.isError) return <div className="p-3"><ErrorState message={errorMessage(data.error)} /></div>;
  const res = data.data!;
  if (!res.result) return <div className="p-3"><Notice tone="danger" icon={<XCircle />}><span className="text-xs">{res.error}</span></Notice></div>;
  const x = widget.xColumn ?? res.chart?.xColumn;
  const ys = widget.yColumns.length ? widget.yColumns : res.chart?.yColumns ?? [];
  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="min-h-0 flex-1 px-2 pb-1">
        {widget.chartType === 'TABLE'
          ? <div className="flex h-full flex-col overflow-hidden"><WidgetTable data={res.result} /></div>
          : <ResultChart result={res.result} type={widget.chartType} xColumn={x} yColumns={ys} height="100%" />}
      </div>
      <p className="px-3 pb-2 text-[10px] text-muted">{res.result.rowCount.toLocaleString()} rows · {formatDuration(res.result.durationMs)} · live</p>
    </div>
  );
}

function WidgetDialog({ dashboard, widget, onClose }: { dashboard: DashboardDetail['dashboard']; widget?: Widget; onClose: () => void }) {
  const { workspaceId } = useWorkspace();
  const qc = useQueryClient();
  const queries = useQuery({ queryKey: ['saved-queries', workspaceId], queryFn: () => api.get<SavedQuery[]>('/saved-queries', { workspaceId: workspaceId! }) });
  const usable = (queries.data ?? []).filter((q) => dashboard.visibility === 'PRIVATE' || q.visibility === 'WORKSPACE');
  const [savedQueryId, setSavedQueryId] = useState<number | ''>(widget?.savedQueryId ?? '');
  const [title, setTitle] = useState(widget?.title ?? '');
  const [chartType, setChartType] = useState<ChartType>(widget?.chartType ?? 'BAR');
  const [xColumn, setX] = useState(widget?.xColumn ?? '');
  const [yColumns, setY] = useState(widget?.yColumns.join(', ') ?? '');
  useEffect(() => {
    if (!widget && savedQueryId) {
      const q = usable.find((u) => u.id === savedQueryId);
      if (q) {
        if (!title) setTitle(q.name);
        if (q.chartType && CHARTS.includes(q.chartType as ChartType)) setChartType(q.chartType as ChartType);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [savedQueryId]);
  const save = useMutation({
    mutationFn: () => {
      const body = { savedQueryId, title, chartType, xColumn: xColumn.trim() || null, yColumns: yColumns.split(',').map((s) => s.trim()).filter(Boolean),
        ...(widget ? { x: widget.x, y: widget.y, w: widget.w, h: widget.h } : { x: 0, w: 6, h: 4 }) };
      return widget ? api.put<DashboardDetail>(`/dashboards/${dashboard.id}/widgets/${widget.id}`, body)
        : api.post<DashboardDetail>(`/dashboards/${dashboard.id}/widgets`, body);
    },
    onSuccess: (d) => { qc.setQueryData(['dashboard', dashboard.id], d); toast.success(widget ? 'Widget updated' : 'Widget added'); onClose(); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={widget ? 'Edit widget' : 'Add widget'}
      description="Widgets run a saved query live. Leave the columns blank to use the recommended chart mapping."
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button onClick={() => save.mutate()} loading={save.isPending} disabled={!savedQueryId || !title.trim()}>Save</Button></>}>
      <div className="space-y-3">
        <Field label="Saved query" hint={dashboard.visibility === 'WORKSPACE' ? 'Only queries shared with the workspace can be used on a shared dashboard.' : undefined}>
          <Select value={savedQueryId} onChange={(e) => setSavedQueryId(e.target.value ? Number(e.target.value) : '')}>
            <option value="">Choose a saved query…</option>
            {usable.map((q) => <option key={q.id} value={q.id}>{q.folder ? `${q.folder} / ` : ''}{q.name}</option>)}
          </Select>
        </Field>
        {queries.data && usable.length === 0 && <Notice>No eligible saved queries yet. <Link className="text-accent underline" to="/app/studio">Create one in Query Studio</Link>.</Notice>}
        <Field label="Title"><Input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={160} /></Field>
        <Field label="Visualisation">
          <Select value={chartType} onChange={(e) => setChartType(e.target.value as ChartType)}>{CHARTS.map((c) => <option key={c} value={c}>{c.toLowerCase()}</option>)}</Select>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="X column (optional)"><Input value={xColumn} onChange={(e) => setX(e.target.value)} placeholder="e.g. month" /></Field>
          <Field label="Y columns (optional)"><Input value={yColumns} onChange={(e) => setY(e.target.value)} placeholder="e.g. revenue, orders" /></Field>
        </div>
      </div>
    </Dialog>
  );
}

export default function DashboardView() {
  const { id } = useParams();
  const dashboardId = Number(id);
  const [params] = useSearchParams();
  const qc = useQueryClient();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(params.get('edit') === '1');
  const [layout, setLayout] = useState<Layout[] | null>(null);
  const [dirty, setDirty] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [widgetDialog, setWidgetDialog] = useState<{ widget?: Widget } | null>(null);
  const [settings, setSettings] = useState(false);

  const detail = useQuery({ queryKey: ['dashboard', dashboardId], queryFn: () => api.get<DashboardDetail>(`/dashboards/${dashboardId}`), enabled: !Number.isNaN(dashboardId) });
  const d = detail.data;
  const baseLayout = useMemo<Layout[]>(() => (d?.widgets ?? []).map((w) => ({ i: String(w.id), x: w.x, y: w.y, w: w.w, h: w.h, minW: 2, minH: 2 })), [d]);
  useEffect(() => { setLayout(baseLayout); setDirty(false); }, [baseLayout]);

  const saveLayout = useMutation({
    mutationFn: () => api.put<DashboardDetail>(`/dashboards/${dashboardId}/layout`, {
      items: (layout ?? []).map((l) => ({ id: Number(l.i), x: l.x, y: l.y, w: l.w, h: l.h })),
    }),
    onSuccess: (res) => { qc.setQueryData(['dashboard', dashboardId], res); toast.success('Layout saved'); setDirty(false); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const removeWidget = useMutation({
    mutationFn: (wid: number) => api.delete<DashboardDetail>(`/dashboards/${dashboardId}/widgets/${wid}`),
    onSuccess: (res) => { qc.setQueryData<DashboardDetail>(['dashboard', dashboardId], res); },
    onError: (e) => toast.error(errorMessage(e)),
  });

  if (detail.isLoading) return <Skeleton className="h-96 rounded-xl" />;
  if (detail.isError || !d) return <ErrorState message={errorMessage(detail.error)} onRetry={() => detail.refetch()} />;
  const canEdit = d.dashboard.canEdit;

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-center gap-3">
        <Button asChild size="icon" variant="ghost" aria-label="Back to dashboards"><Link to="/app/dashboards"><ArrowLeft /></Link></Button>
        <div className="min-w-0 flex-1">
          <h1 className="flex items-center gap-2 truncate text-xl font-semibold">{d.dashboard.name}
            <Badge>{d.dashboard.visibility === 'WORKSPACE' ? <><Globe />shared</> : <><Lock />private</>}</Badge></h1>
          <p className="text-xs text-muted">{d.dashboard.description || 'Live data'} · by {d.dashboard.ownerName} · updated {timeAgo(d.dashboard.updatedAt)}</p>
        </div>
        <Button variant="secondary" onClick={() => setRefreshKey((k) => k + 1)}><RefreshCw /> Refresh</Button>
        {canEdit && !editing && <Button variant="secondary" onClick={() => setEditing(true)}><Pencil /> Edit layout</Button>}
        {canEdit && editing && (
          <>
            <Button variant="secondary" onClick={() => setWidgetDialog({})}><Plus /> Add widget</Button>
            <Button variant="secondary" onClick={() => setSettings(true)}><Settings2 /> Settings</Button>
            <Button onClick={() => { if (dirty) saveLayout.mutate(); setEditing(false); }} loading={saveLayout.isPending}><Check /> {dirty ? 'Save layout' : 'Done'}</Button>
          </>
        )}
      </div>

      {d.widgets.length === 0 ? (
        <EmptyState title="This dashboard is empty" description="Add widgets backed by saved queries."
          action={canEdit && <Button onClick={() => { setEditing(true); setWidgetDialog({}); }}><Plus /> Add widget</Button>} />
      ) : (
        <Grid className="-mx-2" layouts={{ lg: layout ?? baseLayout }} breakpoints={{ lg: 1024, md: 768, sm: 0 }} cols={{ lg: 12, md: 8, sm: 1 }}
          rowHeight={64} margin={[12, 12]} isDraggable={editing} isResizable={editing} draggableHandle=".widget-drag"
          onLayoutChange={(current, all) => { if (all.lg && editing) { setLayout(all.lg); setDirty(true); } else if (!editing && current.length && !layout) setLayout(current); }}>
          {d.widgets.map((w) => (
            <div key={String(w.id)} className="flex flex-col overflow-hidden rounded-xl border border-border bg-surface shadow-sm">
              <div className="flex items-center gap-2 border-b border-border px-3 py-2">
                {editing && <GripVertical className="widget-drag size-4 cursor-move text-muted" aria-label="Drag widget" />}
                <h3 className="truncate text-sm font-medium">{w.title}</h3>
                <span className="truncate text-[11px] text-muted">{w.savedQueryName}</span>
                {editing && (
                  <div className="ml-auto flex">
                    <Button size="icon-sm" variant="ghost" onClick={() => setWidgetDialog({ widget: w })} aria-label="Edit widget"><Pencil /></Button>
                    <Button size="icon-sm" variant="ghost" aria-label="Remove widget" onClick={async () => {
                      if (await confirm({ title: `Remove “${w.title}”?`, confirmLabel: 'Remove', danger: true })) removeWidget.mutate(w.id);
                    }}><Trash2 className="text-danger" /></Button>
                  </div>
                )}
              </div>
              <div className="min-h-0 flex-1"><WidgetBody dashboardId={dashboardId} widget={w} refreshKey={refreshKey} /></div>
            </div>
          ))}
        </Grid>
      )}
      {widgetDialog && <WidgetDialog dashboard={d.dashboard} widget={widgetDialog.widget} onClose={() => setWidgetDialog(null)} />}
      {settings && <DashboardFormDialog open onOpenChange={setSettings} existing={d.dashboard} />}
    </div>
  );
}
