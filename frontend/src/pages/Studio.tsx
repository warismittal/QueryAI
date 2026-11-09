import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  AlertTriangle, BarChart3, Bookmark, Bot, Code2, CheckCircle2, ChevronDown, ChevronRight, CircleDashed, Copy, Database, HelpCircle,
  KeyRound, Link2, ListTree, Loader2, LockKeyhole, MessageSquarePlus, MessagesSquare, PanelLeft, Play, Send, ShieldCheck,
  Sparkles, ThumbsDown, ThumbsUp, User as UserIcon, Wand2, X, XCircle,
} from 'lucide-react';
import { api, errorMessage, piiPreview } from '@/lib/api';
import { useWorkspace } from '@/lib/workspace';
import type {
  AnalyzeResponse, ChartRecommendation, Conversation, ConversationMessage, ExecuteResponse, Interpretation, QueryResult,
  SavedQuery, SchemaView, SqlValidationResult, Stage, SystemInfo,
} from '@/lib/types';
import { cn, detectParameters, formatDuration } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Checkbox, Input, Select, Textarea } from '@/components/ui/input';
import { EmptyState, Notice, Skeleton } from '@/components/ui/feedback';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { SqlEditor } from '@/components/sql/SqlEditor';
import { ParameterInputs, toParams, ValidationPanel } from '@/components/sql/ValidationPanel';
import { ResultsPanel } from '@/components/results/ResultsPanel';
import { SaveQueryDialog } from '@/components/sql/SaveQueryDialog';
import { QueryHero } from '@/components/studio/QueryHero';

interface Turn {
  key: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  sql?: string;
  analysis?: AnalyzeResponse;
  queryRunId?: number;
  status?: string;
  pending?: boolean;
  error?: boolean;
}

interface ResultView {
  result: QueryResult;
  chart?: ChartRecommendation;
  interpretation?: Interpretation;
  queryRunId?: number;
  question?: string;
  sql: string;
}

function examplesFor(schema?: SchemaView): string[] {
  const names = new Set(schema?.tables.map((t) => t.name) ?? []);
  if (names.has('orders') && names.has('customers')) {
    return [
      'Show the top 10 customers by total spending this year, compare their spending with the previous year, calculate the percentage change, and identify customers whose spending declined by more than 15%.',
      'What is the monthly revenue trend over the last 12 months?',
      'Which product categories generate the most revenue?',
      'What is the average order value by sales channel?',
      'Which warehouses have the most products below their reorder level?',
    ];
  }
  const t = schema?.tables.slice(0, 3).map((x) => x.name) ?? [];
  return t.length ? [`How many rows are in ${t[0]}?`, ...t.slice(1).map((n) => `Show a breakdown of ${n} by its most important category`)] : [];
}

function StageList({ stages }: { stages: Stage[] }) {
  const [open, setOpen] = useState(false);
  const total = stages.reduce((s, x) => s + x.durationMs, 0);
  return (
    <div className="mt-2">
      <button onClick={() => setOpen(!open)} className="flex items-center gap-1 text-[11px] text-muted hover:text-fg">
        {open ? <ChevronDown className="size-3" /> : <ChevronRight className="size-3" />} Pipeline · {stages.length} steps · {formatDuration(total)}
      </button>
      {open && (
        <ol className="mt-1.5 space-y-1 border-l border-border pl-3">
          {stages.map((s, i) => (
            <li key={i} className="text-[11px]">
              <span className="flex items-center gap-1.5">
                {s.status === 'OK' ? <CheckCircle2 className="size-3 text-success" /> : s.status === 'FAILED' ? <XCircle className="size-3 text-danger" /> : <CircleDashed className="size-3 text-muted" />}
                <span className="font-medium text-fg-2">{s.name}</span>
                <span className="text-muted">{formatDuration(s.durationMs)}</span>
              </span>
              {s.detail && <span className="block pl-4.5 text-muted">{s.detail}</span>}
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

function AssistantBubble({ turn, onUseSql, onFollowUp }: { turn: Turn; onUseSql: (sql: string) => void; onFollowUp: (q: string) => void }) {
  const a = turn.analysis;
  return (
    <div className="flex gap-2.5">
      <div className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full border border-accent/30 bg-accent-soft text-accent"><Bot className="size-4" /></div>
      <div className={cn('min-w-0 flex-1 rounded-2xl rounded-tl-md border px-4 py-3 text-sm animate-fade-up',
        turn.error ? 'border-danger/30 bg-danger-soft' : 'border-border bg-surface-2/70 backdrop-blur')}>
        {turn.pending ? (
          <div className="flex items-center gap-2 text-muted"><Loader2 className="size-4 animate-spin" /> Understanding the question, reading the schema and writing SQL…</div>
        ) : (
          <>
            {a?.status === 'CLARIFICATION_NEEDED' && <Badge tone="warning" className="mb-1.5"><HelpCircle /> Needs clarification</Badge>}
            {a?.status === 'BLOCKED' && <Badge tone="danger" className="mb-1.5"><ShieldCheck /> Blocked by validation</Badge>}
            <p className="whitespace-pre-wrap leading-relaxed text-fg">{turn.content}</p>
            {a?.sqlExplanation && a.status !== 'CLARIFICATION_NEEDED' && a.sqlExplanation !== turn.content && (
              <p className="mt-2 text-xs text-muted"><b className="font-medium text-fg-2">How:</b> {a.sqlExplanation}</p>
            )}
            {a?.assumptions && a.assumptions.length > 0 && (
              <ul className="mt-1.5 list-disc pl-4 text-xs text-muted">{a.assumptions.map((x) => <li key={x}>{x}</li>)}</ul>
            )}
            <div className="mt-2 flex flex-wrap items-center gap-2">
              {a?.result && <Badge tone="success">{a.result.rowCount} rows · {formatDuration(a.result.durationMs)}</Badge>}
              {a?.confidence != null && <Badge>confidence {(a.confidence * 100).toFixed(0)}%</Badge>}
              {a?.repairAttempts ? <Badge tone="accent"><Wand2 /> self-repaired</Badge> : null}
              {turn.sql && <Button size="sm" variant="ghost" className="h-6 px-2 text-[11px]" onClick={() => onUseSql(turn.sql!)}>Open SQL in editor</Button>}
            </div>
            {a?.stages && a.stages.length > 0 && <StageList stages={a.stages} />}
            {a?.interpretation?.followUps && a.interpretation.followUps.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-1.5">
                {a.interpretation.followUps.map((f) => (
                  <button key={f} onClick={() => onFollowUp(f)} className="rounded-full border border-border px-2.5 py-1 text-[11px] text-fg-2 hover:border-accent hover:text-accent">{f}</button>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function SchemaTree({ schema, onInsert }: { schema?: SchemaView; onInsert: (text: string) => void }) {
  const [open, setOpen] = useState<Set<string>>(new Set());
  const [q, setQ] = useState('');
  if (!schema) return <div className="space-y-2 p-3">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-6" />)}</div>;
  const filtered = schema.tables.filter((t) => !q || t.name.includes(q.toLowerCase()) || t.columns.some((c) => c.name.includes(q.toLowerCase())));
  return (
    <div className="p-2">
      <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search tables & columns" className="mb-2 h-8 text-xs" aria-label="Search schema" />
      <ul className="space-y-0.5">
        {filtered.map((t) => {
          const isOpen = open.has(t.name) || (!!q && t.columns.some((c) => c.name.includes(q.toLowerCase())));
          return (
            <li key={t.name}>
              <div className="group flex items-center gap-1 rounded-md px-1.5 py-1 hover:bg-surface-2">
                <button className="flex flex-1 items-center gap-1.5 text-left text-xs" aria-expanded={isOpen}
                  onClick={() => { const n = new Set(open); if (n.has(t.name)) n.delete(t.name); else n.add(t.name); setOpen(n); }}>
                  {isOpen ? <ChevronDown className="size-3 text-muted" /> : <ChevronRight className="size-3 text-muted" />}
                  <ListTree className="size-3.5 text-accent" />
                  <span className="truncate font-medium text-fg">{t.name}</span>
                  {t.rowEstimate ? <span className="ml-auto text-[10px] text-muted">{t.rowEstimate.toLocaleString()}</span> : null}
                </button>
                <button onClick={() => onInsert(t.name)} className="hidden rounded px-1 text-[10px] text-muted hover:text-accent group-hover:block" aria-label={`Insert ${t.name}`}>insert</button>
              </div>
              {isOpen && (
                <ul className="mb-1 ml-5 border-l border-border pl-2">
                  {t.columns.map((c) => (
                    <li key={c.name}>
                      <button onClick={() => onInsert(c.name)} title={c.description ?? c.dataType}
                        className="flex w-full items-center gap-1.5 rounded px-1 py-0.5 text-left text-[11px] hover:bg-surface-2">
                        {c.primaryKey ? <KeyRound className="size-3 text-warning" /> : c.references ? <Link2 className="size-3 text-accent" /> : <span className="size-3" />}
                        <span className="truncate text-fg-2">{c.name}</span>
                        <span className="ml-auto shrink-0 font-mono text-[10px] text-muted">{c.dataType}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

export default function Studio() {
  const { workspaceId, dataSources, dataSourceId, setDataSourceId, dataSourcesLoading, can, loading: workspacesLoading } = useWorkspace();
  const qc = useQueryClient();
  const [params] = useSearchParams();
  const ds = dataSources.find((d) => d.id === dataSourceId);
  const [leftOpen, setLeftOpen] = useState(false);
  const [question, setQuestion] = useState('');
  const [autoRun, setAutoRun] = useState(true);
  const [turns, setTurns] = useState<Turn[]>([]);
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [sql, setSql] = useState('');
  const [paramValues, setParamValues] = useState<Record<string, string>>({});
  const [validation, setValidation] = useState<SqlValidationResult | null>(null);
  const [view, setView] = useState<ResultView | null>(null);
  const [execError, setExecError] = useState<{ message: string; status: string } | null>(null);
  const [saveOpen, setSaveOpen] = useState(false);
  const [feedback, setFeedback] = useState<Record<number, 'UP' | 'DOWN'>>({});
  const [manual, setManual] = useState(false);
  const [pane, setPane] = useState<'answer' | 'sql'>('answer');
  const masked = useSyncExternalStore(piiPreview.subscribe, piiPreview.get, () => false);
  const autoAsked = useRef(false);
  const chatEnd = useRef<HTMLDivElement>(null);
  const lastQuestion = [...turns].reverse().find((t) => t.role === 'USER')?.content;
  const paramNames = useMemo(() => detectParameters(sql), [sql]);

  const info = useQuery({ queryKey: ['system-info'], queryFn: () => api.get<SystemInfo>('/system/info'), staleTime: 300_000 });
  const schema = useQuery({
    queryKey: ['schema', dataSourceId],
    queryFn: () => api.get<SchemaView>(`/datasources/${dataSourceId}/schema`),
    enabled: !!dataSourceId,
    staleTime: 300_000,
  });
  const saved = useQuery({
    queryKey: ['saved-queries', workspaceId],
    queryFn: () => api.get<SavedQuery[]>('/saved-queries', { workspaceId: workspaceId! }),
    enabled: !!workspaceId,
  });
  const conversations = useQuery({
    queryKey: ['conversations', workspaceId, dataSourceId],
    queryFn: () => api.get<Conversation[]>('/conversations', { workspaceId: workspaceId!, dataSourceId: dataSourceId! }),
    enabled: !!workspaceId && !!dataSourceId,
  });

  const completions = useMemo(() => ({
    tables: schema.data?.tables.map((t) => t.name) ?? [],
    columns: schema.data?.tables.flatMap((t) => t.columns.map((c) => c.name)) ?? [],
  }), [schema.data]);

  useEffect(() => { chatEnd.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }); }, [turns]);

  // deep links: ?sql=…  (from history / saved queries)
  useEffect(() => {
    const preset = params.get('sql');
    if (preset) setSql(preset);
    const presetDs = params.get('ds');
    if (presetDs) setDataSourceId(Number(presetDs));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const newChat = () => {
    setManual(false);
    setTurns([]);
    setConversationId(null);
    setView(null);
    setValidation(null);
    setExecError(null);
  };

  const loadConversation = async (id: number) => {
    try {
      const detail = await api.get<{ conversation: Conversation; messages: ConversationMessage[] }>(`/conversations/${id}`);
      setConversationId(id);
      setTurns(detail.messages.map((m) => ({
        key: `m${m.id}`, role: m.role, content: m.content, sql: m.sql, queryRunId: m.queryRunId,
        status: (m.metadata?.status as string | undefined),
      })));
      const lastSql = [...detail.messages].reverse().find((m) => m.sql)?.sql;
      if (lastSql) setSql(lastSql);
      setView(null);
      setLeftOpen(false);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  const analyze = useMutation({
    mutationFn: (q: string) => api.post<AnalyzeResponse>('/analytics/analyze', {
      dataSourceId, question: q, conversationId, execute: autoRun,
    }),
    onMutate: (q) => {
      setPane('answer');
      setTurns((t) => [...t, { key: `u${Date.now()}`, role: 'USER', content: q }, { key: 'pending', role: 'ASSISTANT', content: '', pending: true }]);
      setQuestion('');
    },
    onSuccess: (res) => {
      setConversationId(res.conversationId);
      const content = res.status === 'CLARIFICATION_NEEDED' ? (res.clarificationQuestion ?? 'Could you clarify your question?')
        : res.status === 'FAILED' ? `I couldn't complete this request: ${res.error}`
          : res.status === 'BLOCKED' ? `The generated SQL did not pass validation and was not executed: ${res.error ?? ''}`
            : res.interpretation?.summary ?? res.sqlExplanation ?? 'SQL generated. Review it in the editor and run it when ready.';
      setTurns((t) => [...t.filter((x) => !x.pending), {
        key: `a${res.queryRunId}`, role: 'ASSISTANT', content, sql: res.sql, analysis: res, queryRunId: res.queryRunId,
        status: res.status, error: res.status === 'FAILED' || res.status === 'BLOCKED',
      }]);
      if (res.sql) setSql(res.sql);
      setValidation(res.validation ?? null);
      setExecError(res.status === 'FAILED' || res.status === 'BLOCKED' ? { message: res.error ?? 'Failed', status: res.status } : null);
      if (res.result) {
        setView({ result: res.result, chart: res.chart, interpretation: res.interpretation, queryRunId: res.queryRunId, question: res.question, sql: res.sql ?? '' });
        setPane('answer');
      } else if (res.sql && res.status !== 'FAILED' && res.status !== 'BLOCKED') {
        setPane('sql');
      }
      qc.invalidateQueries({ queryKey: ['conversations'] });
      qc.invalidateQueries({ queryKey: ['overview'] });
    },
    onError: (e) => {
      setTurns((t) => [...t.filter((x) => !x.pending), { key: `e${Date.now()}`, role: 'ASSISTANT', content: errorMessage(e), error: true }]);
    },
  });

  const validate = useMutation({
    mutationFn: () => api.post<SqlValidationResult>('/sql/validate', { dataSourceId, sql, parameters: toParams(paramNames, paramValues) }),
    onSuccess: (v) => {
      setValidation(v);
      if (v.status === 'INVALID') toast.error('Validation failed');
      else toast.success(v.status === 'WARNING' ? 'Valid with warnings' : 'SQL is valid');
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const execute = useMutation({
    mutationFn: () => api.post<ExecuteResponse>('/sql/execute', {
      dataSourceId, sql, parameters: toParams(paramNames, paramValues), question: lastQuestion, conversationId,
    }),
    onSuccess: (res) => {
      setValidation(res.validation);
      if (res.result) {
        setExecError(null);
        setPane('answer');
        setView({ result: res.result, chart: res.chart, queryRunId: res.queryRunId, question: lastQuestion, sql });
      } else {
        setExecError({ message: res.error ?? 'Execution failed', status: res.status });
      }
      qc.invalidateQueries({ queryKey: ['overview'] });
    },
    onError: (e) => setExecError({ message: errorMessage(e), status: 'FAILED' }),
  });

  const explain = useMutation({
    mutationFn: (v: ResultView) => api.post<{ interpretation: Interpretation; chart: ChartRecommendation }>('/analytics/interpret', {
      question: v.question, sql: v.sql, result: { ...v.result, rows: v.result.rows.slice(0, 200) },
    }),
    onSuccess: (res) => setView((v) => (v ? { ...v, interpretation: res.interpretation } : v)),
    onError: (e) => toast.error(errorMessage(e)),
  });

  const sendFeedback = useMutation({
    mutationFn: ({ id, rating }: { id: number; rating: 'UP' | 'DOWN' }) => api.post(`/history/${id}/feedback`, { rating }),
    onSuccess: (_, v) => { setFeedback((f) => ({ ...f, [v.id]: v.rating })); toast.success('Thanks for the feedback'); },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const ask = (q: string) => {
    if (!q.trim() || !dataSourceId || analyze.isPending) return;
    analyze.mutate(q.trim());
  };

  // deep link from the landing page: /app/studio?q=… asks once the data source and AI are ready
  useEffect(() => {
    const q = params.get('q');
    if (!q || autoAsked.current || !dataSourceId || !info.data) return;
    autoAsked.current = true;
    if (info.data.llmConfigured && ds?.canQuery) ask(q);
    else setQuestion(q);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dataSourceId, info.data, ds?.canQuery]);

  // only once we know the workspace (and its sources) for sure — avoids flashing "connect a database" while loading
  if (!workspacesLoading && workspaceId !== null && !dataSourcesLoading && dataSources.length === 0) {
    return (
      <div className="p-8">
        <EmptyState icon={<Database />} title="Connect a database to start asking questions"
          description="Query Studio needs a PostgreSQL or MySQL connection. Credentials are encrypted and queries run read-only."
          action={can('ADMIN') ? <Button asChild><Link to="/app/connections">Add a connection</Link></Button>
            : <p className="text-xs text-muted">Ask a workspace admin to add a connection.</p>} />
      </div>
    );
  }

  const canQuery = !!ds?.canQuery;
  const aiReady = info.data?.llmConfigured !== false;
  const showHero = turns.length === 0 && !view && !manual && !sql && !analyze.isPending;
  const heroBlocked = !canQuery ? 'You do not have query access to this data source'
    : !aiReady ? 'Connect an AI model to ask in plain English — or write SQL yourself' : undefined;

  const leftPanel = (
    <div className="flex h-full flex-col">
      <div className="space-y-2 border-b border-border p-3">
        <label className="text-[11px] font-medium uppercase tracking-wide text-muted" htmlFor="ds-select">Data source</label>
        <Select id="ds-select" value={dataSourceId ?? ''} onChange={(e) => { setDataSourceId(Number(e.target.value)); newChat(); }} className="h-8 text-xs">
          {dataSources.map((d) => <option key={d.id} value={d.id}>{d.name} · {d.dbType === 'POSTGRESQL' ? 'PostgreSQL' : 'MySQL'}</option>)}
        </Select>
        {ds && (
          <p className="flex items-center gap-1.5 text-[11px] text-muted">
            <span className={cn('size-1.5 rounded-full', ds.status === 'CONNECTED' ? 'bg-success' : ds.status === 'FAILED' ? 'bg-danger' : 'bg-muted')} />
            {ds.status.toLowerCase()} · {ds.effectiveSchema}{!ds.canQuery && ' · no query access'}
          </p>
        )}
        {ds?.canManage && ds.piiMaskingEnabled !== false && (
          <label className="flex cursor-pointer items-center justify-between gap-2 rounded-xl border border-border bg-surface-2/50 px-2.5 py-2 text-[11px] text-fg-2"
            title="Admins see raw values. Turn this on to see results exactly as analysts do, with personal data masked.">
            <span className="flex items-center gap-1.5"><LockKeyhole className="size-3.5 text-accent" /> Preview as analyst</span>
            <input type="checkbox" className="size-3.5 accent-[var(--accent)]" checked={masked}
              onChange={(e) => { piiPreview.set(e.target.checked); setView(null); toast.info(e.target.checked ? 'Personal data will be masked in new results' : 'Showing raw values (admin)'); }} />
          </label>
        )}
      </div>
      <Tabs defaultValue="schema" className="flex min-h-0 flex-1 flex-col">
        <TabsList className="m-2 grid grid-cols-3">
          <TabsTrigger value="schema"><ListTree /> Schema</TabsTrigger>
          <TabsTrigger value="saved"><Bookmark /> Saved</TabsTrigger>
          <TabsTrigger value="chats"><MessagesSquare /> Chats</TabsTrigger>
        </TabsList>
        <TabsContent value="schema" className="min-h-0 flex-1 overflow-y-auto">
          {schema.isError ? <p className="p-3 text-xs text-danger">{errorMessage(schema.error)}</p>
            : <SchemaTree schema={schema.data} onInsert={(t) => setSql((s) => (s ? `${s}${/\s$/.test(s) ? '' : ' '}${t}` : t))} />}
        </TabsContent>
        <TabsContent value="saved" className="min-h-0 flex-1 overflow-y-auto p-2">
          {(saved.data ?? []).filter((s) => s.dataSourceId === dataSourceId).map((s) => (
            <button key={s.id} onClick={() => { setSql(s.sql); setLeftOpen(false); toast.info(`Loaded “${s.name}”`); }}
              className="mb-1 w-full rounded-md px-2 py-1.5 text-left hover:bg-surface-2">
              <p className="truncate text-xs font-medium text-fg">{s.name}</p>
              <p className="truncate text-[11px] text-muted">{s.folder ?? 'No folder'} · v{s.currentVersion}{s.template ? ' · template' : ''}</p>
            </button>
          ))}
          {saved.data && saved.data.filter((s) => s.dataSourceId === dataSourceId).length === 0 && <p className="p-2 text-xs text-muted">No saved queries for this source yet.</p>}
        </TabsContent>
        <TabsContent value="chats" className="min-h-0 flex-1 overflow-y-auto p-2">
          <Button size="sm" variant="secondary" className="mb-2 w-full" onClick={newChat}><MessageSquarePlus /> New conversation</Button>
          {(conversations.data ?? []).map((c) => (
            <button key={c.id} onClick={() => loadConversation(c.id)}
              className={cn('mb-1 w-full rounded-md px-2 py-1.5 text-left hover:bg-surface-2', c.id === conversationId && 'bg-accent-soft')}>
              <p className="truncate text-xs font-medium text-fg">{c.title}</p>
              <p className="text-[11px] text-muted">{c.messageCount} messages</p>
            </button>
          ))}
        </TabsContent>
      </Tabs>
    </div>
  );

  const resultsBody = execute.isPending || (analyze.isPending && autoRun) ? (
    <div className="space-y-3 p-6">
      <p className="eyebrow breathe">{analyze.isPending ? 'Reading the schema and writing SQL…' : 'Running the query…'}</p>
      {Array.from({ length: 7 }).map((_, i) => <Skeleton key={i} className="h-7 rounded-lg" />)}
    </div>
  ) : execError && !view ? (
    <div className="p-6">
      <Notice tone="danger" icon={<XCircle />}>
        <p className="font-medium">{execError.status === 'BLOCKED' ? 'Query blocked by validation' : 'Query failed'}</p>
        <p className="mt-1 break-words font-mono text-xs">{execError.message}</p>
      </Notice>
      <Button size="sm" variant="secondary" className="mt-4" onClick={() => setPane('sql')}><Code2 /> Review the SQL</Button>
    </div>
  ) : view ? (
    <div className="flex min-h-0 flex-1 flex-col">
      {execError && (
        <div className="border-b border-border p-3"><Notice tone="danger" icon={<XCircle />}>Latest run {execError.status.toLowerCase()}: {execError.message}. Showing the previous result.</Notice></div>
      )}
      <ResultsPanel result={view.result} chart={view.chart} interpretation={view.interpretation} queryRunId={view.queryRunId}
        filename={`queryai-${view.queryRunId ?? 'result'}`}
        extraSummary={
          <div className="flex flex-wrap items-center gap-2 pt-2">
            {!view.interpretation && <Button size="sm" variant="secondary" onClick={() => explain.mutate(view)} loading={explain.isPending}><Sparkles /> Explain this result</Button>}
            {view.queryRunId && (
              <>
                <span className="text-xs text-muted">Was this answer correct?</span>
                <Button size="icon-sm" variant={feedback[view.queryRunId] === 'UP' ? 'primary' : 'secondary'} aria-label="Helpful"
                  onClick={() => sendFeedback.mutate({ id: view.queryRunId!, rating: 'UP' })}><ThumbsUp /></Button>
                <Button size="icon-sm" variant={feedback[view.queryRunId] === 'DOWN' ? 'danger' : 'secondary'} aria-label="Not helpful"
                  onClick={() => sendFeedback.mutate({ id: view.queryRunId!, rating: 'DOWN' })}><ThumbsDown /></Button>
              </>
            )}
          </div>
        } />
    </div>
  ) : (
    <div className="flex flex-1 items-center justify-center p-8">
      <EmptyState className="w-full max-w-md" icon={<Play />} title="Your answer will appear here"
        description="Ask a question on the left or write SQL and press Run. Every result comes straight from the connected database." />
    </div>
  );

  const sqlBody = (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="min-h-[320px] flex-1 overflow-hidden rounded-2xl border border-border lg:min-h-0">
        <SqlEditor value={sql} onChange={(v) => { setSql(v); setValidation(null); }} onRun={() => sql && canQuery && execute.mutate()} completions={completions} />
      </div>
      {(paramNames.length > 0 || validation) && (
        <div className="mt-3 max-h-48 space-y-2 overflow-y-auto rounded-2xl border border-border bg-surface/60 p-3">
          <ParameterInputs names={paramNames} values={paramValues} onChange={setParamValues} />
          {validation && <ValidationPanel validation={validation} />}
        </div>
      )}
      <p className="mt-2 text-[11px] text-muted">Ctrl/⌘ + Enter runs · read-only transaction{ds ? ` · ${ds.queryTimeoutSec}s timeout · ${ds.maxRows.toLocaleString()} row cap` : ''}</p>
    </div>
  );

  return (
    <div className="flex min-h-[calc(100vh-76px)] lg:h-full lg:min-h-0">
      <aside className="hidden w-72 shrink-0 border-r border-border/70 bg-surface/50 backdrop-blur-xl lg:block">{leftPanel}</aside>
      {leftOpen && (
        <div className="fixed inset-0 z-[60] lg:hidden">
          <div className="absolute inset-0 bg-black/60" onClick={() => setLeftOpen(false)} />
          <aside className="glass absolute inset-y-0 left-0 w-80 max-w-[90vw]">
            <button className="absolute right-2 top-2 z-10 rounded p-1 text-muted" onClick={() => setLeftOpen(false)} aria-label="Close panel"><X className="size-4" /></button>
            {leftPanel}
          </aside>
        </div>
      )}

      {showHero ? (
        <div className="min-w-0 flex-1 overflow-y-auto">
          <div className="flex items-center gap-2 px-4 pt-3 lg:hidden">
            <Button size="icon-sm" variant="ghost" onClick={() => setLeftOpen(true)} aria-label="Open schema panel"><PanelLeft /></Button>
          </div>
          <QueryHero sourceName={ds?.name} value={question} onChange={setQuestion} onSubmit={() => ask(question)}
            onPick={(q) => ask(q)} onManual={() => { setManual(true); setPane('sql'); }} disabledReason={heroBlocked} pending={analyze.isPending} />
        </div>
      ) : (
        <div className="grid min-w-0 flex-1 grid-cols-1 gap-4 p-3 sm:p-4 lg:min-h-0 xl:grid-cols-[minmax(340px,400px)_1fr]">
          {/* ---- conversation: the thread and the composer, nothing else ---- */}
          <section className="edge flex min-h-[460px] min-w-0 flex-col overflow-hidden rounded-3xl border border-border bg-surface/60 backdrop-blur-xl xl:min-h-0" aria-label="Conversation">
            <div className="flex items-center gap-2 border-b border-border/70 px-4 py-3">
              <Button size="icon-sm" variant="ghost" className="lg:hidden" onClick={() => setLeftOpen(true)} aria-label="Open schema panel"><PanelLeft /></Button>
              <Sparkles className="size-4 text-accent" />
              <h2 className="font-display text-xl">Conversation</h2>
              {conversationId && <Badge className="ml-1">follow-ups use context</Badge>}
              <Button size="sm" variant="ghost" className="ml-auto" onClick={newChat}><MessageSquarePlus /> New</Button>
            </div>
            <div className="min-h-0 max-h-[55vh] flex-1 space-y-4 overflow-y-auto p-4 xl:max-h-none">
              {!aiReady && (
                <Notice tone="warning" icon={<AlertTriangle />}>
                  No AI model is connected, so questions can't be answered. You can still write and run SQL.
                </Notice>
              )}
              {turns.length === 0 && (
                <div>
                  <p className="mb-2 text-xs text-muted">Ask anything about <b className="text-fg-2">{ds?.name}</b>, for example:</p>
                  <div className="space-y-1.5">
                    {examplesFor(schema.data).slice(1).map((e) => (
                      <button key={e} onClick={() => ask(e)} disabled={!canQuery || !aiReady}
                        className="block w-full rounded-xl border border-border bg-surface-2/60 px-3 py-2 text-left text-xs text-fg-2 transition-colors hover:border-accent/50 hover:text-fg disabled:opacity-50">
                        {e}
                      </button>
                    ))}
                  </div>
                </div>
              )}
              {turns.map((t) => t.role === 'USER' ? (
                <div key={t.key} className="flex justify-end gap-2.5">
                  <div className="max-w-[88%] rounded-2xl rounded-tr-md bg-linear-to-br from-accent to-accent-2 px-4 py-2.5 font-display text-[17px] leading-snug text-accent-fg shadow-[0_10px_30px_-14px_rgb(var(--glow)/0.9)] animate-fade-up">{t.content}</div>
                  <div className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-surface-3"><UserIcon className="size-3.5 text-fg-2" /></div>
                </div>
              ) : (
                <AssistantBubble key={t.key} turn={t} onUseSql={(q) => { setSql(q); setPane('sql'); }} onFollowUp={ask} />
              ))}
              <div ref={chatEnd} />
            </div>
            <form className="border-t border-border/70 p-3" onSubmit={(e) => { e.preventDefault(); ask(question); }}>
              <div className="relative">
                <Textarea value={question} onChange={(e) => setQuestion(e.target.value)} rows={2} maxLength={4000}
                  placeholder={canQuery ? (conversationId ? 'Ask a follow-up… e.g. “only for the web channel”' : 'Ask your data something interesting…') : 'You do not have query access to this data source'}
                  disabled={!canQuery || !aiReady}
                  onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); ask(question); } }}
                  className="resize-none rounded-2xl pr-12" aria-label="Question" />
                <Button type="submit" size="icon-sm" className="absolute bottom-2.5 right-2.5" disabled={!question.trim() || !canQuery || !aiReady} loading={analyze.isPending} aria-label="Send question">
                  {!analyze.isPending && <Send />}
                </Button>
              </div>
              <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-[11px] text-muted">
                <Checkbox label="Run automatically after validation" checked={autoRun} onChange={(e) => setAutoRun(e.target.checked)} className="text-[11px]" />
                <span>Enter to send · Shift+Enter for a new line</span>
              </div>
            </form>
          </section>

          {/* ---- the answer: results and SQL live in their own space ---- */}
          <section className="edge flex min-h-[560px] min-w-0 flex-col overflow-hidden rounded-3xl border border-border bg-surface/70 backdrop-blur-xl xl:min-h-0" aria-label="Answer">
            <div className="flex flex-wrap items-center gap-3 border-b border-border/70 px-4 py-3">
              <div className="inline-flex rounded-full border border-border bg-surface-2/70 p-1" role="tablist" aria-label="Answer view">
                {(['answer', 'sql'] as const).map((p) => (
                  <button key={p} role="tab" aria-selected={pane === p} onClick={() => setPane(p)}
                    className={cn('inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-xs font-medium transition-all duration-300',
                      pane === p ? 'bg-linear-to-b from-accent to-accent-2 text-accent-fg shadow-[0_6px_20px_-8px_rgb(var(--glow)/0.9)]' : 'text-muted hover:text-fg')}>
                    {p === 'answer' ? <><BarChart3 className="size-3.5" /> Answer</> : <><Code2 className="size-3.5" /> SQL</>}
                  </button>
                ))}
              </div>
              <p className="hidden min-w-0 flex-1 truncate font-display text-lg text-fg-2 md:block" title={view?.question ?? lastQuestion}>
                {view?.question ?? lastQuestion ?? 'Write SQL or ask a question'}
              </p>
              <div className="ml-auto flex items-center gap-1.5">
                <Button size="sm" variant="ghost" onClick={() => { void navigator.clipboard.writeText(sql); toast.success('SQL copied'); }} disabled={!sql}><Copy /> Copy</Button>
                <Button size="sm" variant="ghost" onClick={() => setSaveOpen(true)} disabled={!sql || !can('ANALYST')}><Bookmark /> Save</Button>
                <Button size="sm" variant="secondary" onClick={() => validate.mutate()} loading={validate.isPending} disabled={!sql || !canQuery}><ShieldCheck /> Validate</Button>
                <Button size="sm" onClick={() => execute.mutate()} loading={execute.isPending} disabled={!sql || !canQuery}>{!execute.isPending && <Play />} Run</Button>
              </div>
            </div>
            <div className={cn('flex min-h-0 flex-1 flex-col', pane === 'sql' && 'p-4')}>
              {pane === 'answer' ? resultsBody : sqlBody}
            </div>
          </section>
        </div>
      )}
      <SaveQueryDialog open={saveOpen} onOpenChange={setSaveOpen} sql={sql} question={lastQuestion} chartType={view?.chart?.type} dataSourceId={dataSourceId} />
    </div>
  );
}
