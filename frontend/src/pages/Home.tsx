import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import {
  Activity, ArrowRight, Bookmark, CheckCircle2, Clock, Database, Gauge, LayoutDashboard, Network, Plus, Sparkles, ThumbsUp,
} from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useWorkspace } from '@/lib/workspace';
import type { Overview } from '@/lib/types';
import { formatDuration, formatPercent, timeAgo } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { EmptyState, ErrorState, PageHeader, Skeleton } from '@/components/ui/feedback';
import { ExecutionBadge } from '@/components/ui/status';

function Kpi({ icon: Icon, label, value, sub }: { icon: typeof Activity; label: string; value: string; sub?: string }) {
  return (
    <Card className="p-4">
      <div className="flex items-center gap-2 text-xs text-muted"><Icon className="size-3.5" />{label}</div>
      <p className="mt-2 text-2xl font-semibold tracking-tight">{value}</p>
      {sub && <p className="mt-0.5 text-xs text-muted">{sub}</p>}
    </Card>
  );
}

/** First-run guide: four steps, each ticked off from measured workspace activity; hidden once everything is done. */
function GettingStarted({ o, canAdmin }: { o: Overview; canAdmin: boolean }) {
  const steps = [
    { done: o.dataSourceCount > 0, title: 'Connect a database', text: 'PostgreSQL, MySQL or a CSV upload', to: canAdmin ? '/app/connections' : '/app/schema', icon: Database },
    { done: o.queriesInWindow > 0, title: 'Ask your first question', text: 'Plain English in, real rows out', to: '/app/studio', icon: Sparkles },
    { done: o.savedQueryCount > 0, title: 'Save a query', text: 'Keep answers you want again', to: '/app/studio', icon: Bookmark },
    { done: o.dashboardCount > 0, title: 'Build a dashboard', text: 'Pin live charts for your team', to: '/app/dashboards', icon: LayoutDashboard },
  ];
  const done = steps.filter((x) => x.done).length;
  if (done === steps.length) return null;
  return (
    <Card className="mb-4 overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-3 px-5 pt-4">
        <div>
          <p className="font-display text-xl text-fg">Get started</p>
          <p className="text-xs text-muted">{done} of {steps.length} done</p>
        </div>
        <div className="h-1.5 w-40 overflow-hidden rounded-full bg-surface-3">
          <div className="h-full rounded-full bg-linear-to-r from-accent-2 to-accent transition-all duration-700" style={{ width: `${(done / steps.length) * 100}%` }} />
        </div>
      </div>
      <div className="grid gap-2 p-4 sm:grid-cols-2 lg:grid-cols-4">
        {steps.map(({ done: ok, title, text, to, icon: Icon }) => (
          <Link key={title} to={to} className={`group flex items-start gap-3 rounded-2xl border p-3 transition-all duration-300 hover:-translate-y-0.5 ${ok ? 'border-success/30 bg-success-soft/40' : 'border-border hover:border-accent/50 hover:bg-surface-2/60'}`}>
            <span className={`mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full ${ok ? 'bg-success text-white' : 'bg-accent-soft text-accent'}`}>
              {ok ? <CheckCircle2 className="size-4" /> : <Icon className="size-4" />}
            </span>
            <span className="min-w-0">
              <span className={`block text-sm font-medium ${ok ? 'text-fg-2 line-through decoration-success/60' : 'text-fg'}`}>{title}</span>
              <span className="block text-xs text-muted">{text}</span>
            </span>
          </Link>
        ))}
      </div>
    </Card>
  );
}

export default function Home() {
  const { user } = useAuth();
  const { workspaceId, workspace, can } = useWorkspace();
  const overview = useQuery({
    queryKey: ['overview', workspaceId],
    queryFn: () => api.get<Overview>('/analytics/overview', { workspaceId: workspaceId! }),
    enabled: !!workspaceId,
  });
  const o = overview.data;

  return (
    <div>
      <PageHeader
        title={`Welcome back, ${user?.fullName.split(' ')[0] ?? ''}`}
        description={<>{workspace?.name} · <span className="inline-flex items-center gap-1"><Gauge className="size-3.5" /> Live figures from this workspace, last 30 days.</span></>}
        actions={<>
          <Button asChild variant="secondary"><Link to="/app/schema"><Network /> Explore schema</Link></Button>
          <Button asChild><Link to="/app/studio"><Sparkles /> Ask a question</Link></Button>
        </>}
      />

      {overview.isError && <ErrorState message={errorMessage(overview.error)} onRetry={() => overview.refetch()} />}
      {o && <GettingStarted o={o} canAdmin={can('ADMIN')} />}

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {!o ? Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-[104px] rounded-xl" />) : (
          <>
            <Kpi icon={Database} label="Data sources" value={`${o.connectedDataSources}/${o.dataSourceCount}`} sub="connected / total" />
            <Kpi icon={Activity} label="Queries (30 days)" value={o.queriesInWindow.toLocaleString()} sub={`${o.executedInWindow.toLocaleString()} executed`} />
            <Kpi icon={CheckCircle2} label="Execution success rate" value={formatPercent(o.successRate)} sub={o.executedInWindow ? 'successful / executed' : 'no executions yet'} />
            <Kpi icon={Clock} label="Avg. execution time" value={formatDuration(o.avgExecutionMs)} sub="database time per query" />
          </>
        )}
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Query activity" description="Daily queries by outcome (measured)" />
          <CardBody>
            {!o ? <Skeleton className="h-56" /> : o.activity.length === 0 ? (
              <EmptyState className="h-56 py-6" icon={<Activity />} title="No queries yet" description="Activity appears here once you run your first query." />
            ) : (
              <div className="h-56">
                <ResponsiveContainer>
                  <BarChart data={o.activity.map((a) => ({ ...a, other: Math.max(0, a.total - a.succeeded - a.failed) }))} margin={{ left: -20, right: 4, top: 4 }}>
                    <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
                    <XAxis dataKey="day" tick={{ fontSize: 11, fill: 'var(--muted)' }} axisLine={false} tickLine={false}
                      tickFormatter={(d: string) => new Date(d).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} minTickGap={16} />
                    <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: 'var(--muted)' }} axisLine={false} tickLine={false} />
                    <Tooltip contentStyle={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 8, fontSize: 12 }}
                      cursor={{ fill: 'var(--surface-3)', opacity: 0.5 }} labelFormatter={(d) => new Date(String(d)).toLocaleDateString()} />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                    <Bar dataKey="succeeded" name="Succeeded" stackId="a" fill="var(--success)" stroke="var(--surface)" strokeWidth={1} />
                    <Bar dataKey="failed" name="Failed" stackId="a" fill="var(--danger)" stroke="var(--surface)" strokeWidth={1} />
                    <Bar dataKey="other" name="Blocked / not run" stackId="a" fill="var(--muted)" stroke="var(--surface)" strokeWidth={1} radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Usage this month" description="AI questions count toward the workspace quota" />
          <CardBody className="space-y-4">
            {!o ? <Skeleton className="h-32" /> : (
              <>
                <div>
                  <div className="flex items-baseline justify-between">
                    <span className="text-2xl font-semibold">{o.usage.aiQueriesUsed.toLocaleString()}</span>
                    <span className="text-xs text-muted">of {o.usage.quota.toLocaleString()} AI queries</span>
                  </div>
                  <div className="mt-2 h-2 overflow-hidden rounded-full bg-surface-3" role="progressbar"
                    aria-valuenow={o.usage.aiQueriesUsed} aria-valuemax={o.usage.quota} aria-label="AI quota used">
                    <div className="h-full rounded-full bg-accent" style={{ width: `${Math.min(100, (o.usage.aiQueriesUsed / Math.max(1, o.usage.quota)) * 100)}%` }} />
                  </div>
                </div>
                <dl className="grid grid-cols-2 gap-3 text-xs">
                  <div><dt className="text-muted">All queries</dt><dd className="text-base font-semibold">{o.usage.totalQueries.toLocaleString()}</dd></div>
                  <div><dt className="text-muted">Feedback</dt><dd className="flex items-center gap-1 text-base font-semibold"><ThumbsUp className="size-3.5 text-success" />{o.positiveFeedback} / {o.positiveFeedback + o.negativeFeedback}</dd></div>
                  <div><dt className="text-muted">Saved queries</dt><dd className="text-base font-semibold">{o.savedQueryCount}</dd></div>
                  <div><dt className="text-muted">Dashboards</dt><dd className="text-base font-semibold">{o.dashboardCount}</dd></div>
                </dl>
                <p className="text-[11px] text-muted">AI model: {o.llmConfigured ? o.llmDescription : <Badge tone="warning">not configured</Badge>}</p>
              </>
            )}
          </CardBody>
        </Card>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Your recent queries" actions={<Button asChild size="sm" variant="ghost"><Link to="/app/history">All history <ArrowRight /></Link></Button>} />
          <div className="divide-y divide-border">
            {!o ? <div className="p-5"><Skeleton className="h-40" /></div> : o.recentQueries.length === 0 ? (
              <div className="p-5"><EmptyState icon={<Sparkles />} title="Ask your first question" action={<Button asChild size="sm"><Link to="/app/studio">Open Query Studio</Link></Button>} /></div>
            ) : o.recentQueries.map((q) => (
              <div key={q.id} className="flex items-center gap-3 px-5 py-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm text-fg">{q.question ?? q.finalSql ?? '(no SQL)'}</p>
                  <p className="text-[11px] text-muted">{q.dataSourceName} · {q.source.toLowerCase().replace('_', ' ')} · {timeAgo(q.createdAt)}{q.durationMs != null ? ` · ${formatDuration(q.durationMs)}` : ''}</p>
                </div>
                <ExecutionBadge status={q.executionStatus} />
              </div>
            ))}
          </div>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader title="Quick actions" />
            <CardBody className="grid grid-cols-2 gap-2">
              <Button asChild variant="secondary" className="h-auto flex-col gap-1.5 py-3"><Link to="/app/studio"><Sparkles />Ask</Link></Button>
              <Button asChild variant="secondary" className="h-auto flex-col gap-1.5 py-3"><Link to="/app/connections"><Plus />Connect DB</Link></Button>
              <Button asChild variant="secondary" className="h-auto flex-col gap-1.5 py-3"><Link to="/app/dashboards"><LayoutDashboard />Dashboards</Link></Button>
              <Button asChild variant="secondary" className="h-auto flex-col gap-1.5 py-3"><Link to="/app/saved"><Bookmark />Saved</Link></Button>
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Data sources" actions={<Button asChild size="sm" variant="ghost"><Link to="/app/connections">Manage</Link></Button>} />
            <CardBody className="space-y-2">
              {o?.dataSources.length === 0 && <p className="text-xs text-muted">No connections yet.</p>}
              {o?.dataSources.map((d) => (
                <div key={d.id} className="flex items-center gap-2 text-sm">
                  <span className={`size-2 rounded-full ${d.status === 'CONNECTED' ? 'bg-success' : d.status === 'FAILED' ? 'bg-danger' : 'bg-muted'}`} />
                  <span className="truncate">{d.name}</span>
                  <span className="ml-auto text-[11px] text-muted">{d.dbType === 'POSTGRESQL' ? 'PostgreSQL' : 'MySQL'}</span>
                </div>
              ))}
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Recent workspace activity" />
            <CardBody className="space-y-2.5">
              {o?.recentActivity.length === 0 && <p className="text-xs text-muted">Nothing yet.</p>}
              {o?.recentActivity.slice(0, 6).map((e) => (
                <div key={e.id} className="text-xs">
                  <span className="font-medium text-fg-2">{e.action.toLowerCase().replace(/_/g, ' ')}</span>
                  <span className="text-muted"> · {e.userEmail ?? 'system'} · {timeAgo(e.createdAt)}</span>
                </div>
              ))}
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  );
}
