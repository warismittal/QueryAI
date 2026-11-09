import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ArrowRight, FileSpreadsheet, Loader2, Trash2, UploadCloud } from 'lucide-react';
import { api, ApiError, errorMessage } from '@/lib/api';
import { useWorkspace } from '@/lib/workspace';
import type { SystemInfo, UploadedTable, UploadResult } from '@/lib/types';
import { cn, timeAgo } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Checkbox, Input } from '@/components/ui/input';
import { useConfirm } from '@/components/ui/confirm';

const TYPE_LABEL: Record<string, string> = { BIGINT: 'integer', NUMERIC: 'decimal', BOOLEAN: 'yes / no', DATE: 'date', TIMESTAMP: 'date-time', TEXT: 'text' };

function formatBytes(n: number) {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

/** Drag-and-drop CSV upload: the file becomes a typed table in the workspace's "Uploaded files" data source. */
export function CsvUpload() {
  const { workspaceId, can, setDataSourceId } = useWorkspace();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const confirm = useConfirm();
  const input = useRef<HTMLInputElement>(null);
  const section = useRef<HTMLElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [tableName, setTableName] = useState('');
  const [replace, setReplace] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [last, setLast] = useState<UploadResult | null>(null);

  const info = useQuery({ queryKey: ['system-info'], queryFn: () => api.get<SystemInfo>('/system/info'), staleTime: 300_000 });
  const uploads = useQuery({
    queryKey: ['uploads', workspaceId],
    queryFn: () => api.get<UploadedTable[]>('/uploads', { workspaceId: workspaceId! }),
    enabled: !!workspaceId && info.data?.uploadsEnabled === true,
  });

  useEffect(() => {
    if (window.location.hash === '#upload') section.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, []);

  const upload = useMutation({
    mutationFn: () => {
      const form = new FormData();
      form.append('file', file!);
      form.append('workspaceId', String(workspaceId));
      if (tableName.trim()) form.append('tableName', tableName.trim());
      form.append('replace', String(replace));
      return api.upload<UploadResult>('/uploads', form);
    },
    onSuccess: (res) => {
      setLast(res);
      setFile(null);
      setTableName('');
      toast.success(`${res.table}: ${res.rowCount.toLocaleString()} rows loaded`);
      qc.invalidateQueries({ queryKey: ['uploads'] });
      qc.invalidateQueries({ queryKey: ['datasources'] });
      qc.invalidateQueries({ queryKey: ['schema', res.dataSourceId] });
    },
    onError: (e) => toast.error(e instanceof ApiError && e.status === 409 ? `${e.message}` : errorMessage(e)),
  });

  const remove = useMutation({
    mutationFn: (t: UploadedTable) => api.delete(`/uploads/${t.id}`),
    onSuccess: () => {
      toast.success('Table deleted');
      qc.invalidateQueries({ queryKey: ['uploads'] });
      qc.invalidateQueries({ queryKey: ['schema'] });
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const pick = (f: File | undefined | null) => {
    if (!f) return;
    if (!/\.(csv|tsv|txt)$/i.test(f.name)) {
      toast.error('Choose a .csv, .tsv or .txt file');
      return;
    }
    setFile(f);
    setLast(null);
    setTableName(f.name.replace(/\.(csv|tsv|txt)$/i, '').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, ''));
  };

  if (info.data && !info.data.uploadsEnabled) {
    return (
      <section ref={section} id="upload" className="mt-14 rounded-3xl border border-dashed border-border p-8 text-sm text-muted">
        <p className="font-display text-2xl text-fg">Bring your own data</p>
        <p className="mt-2">CSV uploads are switched off on this server (QUERYAI_WAREHOUSE_ENABLED=false).</p>
      </section>
    );
  }

  return (
    <section ref={section} id="upload" className="mt-14 scroll-mt-28" aria-labelledby="upload-title">
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <p className="eyebrow">Bring your own data</p>
          <h2 id="upload-title" className="mt-2 font-display text-4xl text-fg">Upload a spreadsheet</h2>
          <p className="mt-2 max-w-xl text-sm text-muted">
            A CSV becomes a typed table you can explore, chart and ask questions about. Column types are inferred from every row;
            data is stored in this workspace's own schema and queried read-only.
          </p>
        </div>
      </div>

      <div className="grid gap-5 lg:grid-cols-[1.2fr_0.8fr]">
        <div
          onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={(e) => { e.preventDefault(); setDragging(false); pick(e.dataTransfer.files?.[0]); }}
          className={cn('edge relative flex min-h-64 flex-col justify-center overflow-hidden rounded-3xl border p-8 transition-all duration-500',
            dragging ? 'border-accent/70 bg-accent-soft/50 shadow-[0_0_0_6px_rgb(var(--glow)/0.1)]' : 'border-border bg-surface/70')}>
          <div className="pointer-events-none absolute -right-20 -top-20 size-64 rounded-full bg-[radial-gradient(circle,rgb(var(--glow)/0.18),transparent_65%)]" />
          <input ref={input} type="file" accept=".csv,.tsv,.txt,text/csv" className="hidden" onChange={(e) => pick(e.target.files?.[0])} />
          {!file ? (
            <button type="button" onClick={() => input.current?.click()} disabled={!can('ANALYST')}
              className="group flex flex-col items-center gap-4 text-center disabled:opacity-50">
              <span className="flex size-16 items-center justify-center rounded-full border border-border-strong bg-surface-2 text-accent transition-transform duration-500 group-hover:-translate-y-1">
                <UploadCloud className="size-7" />
              </span>
              <span className="font-display text-2xl text-fg">Drop a CSV here, or choose a file</span>
              <span className="text-xs text-muted">Up to 25 MB · header row required · comma, semicolon, tab or pipe separated</span>
            </button>
          ) : (
            <form className="space-y-5" onSubmit={(e) => { e.preventDefault(); upload.mutate(); }}>
              <div className="flex items-center gap-3">
                <FileSpreadsheet className="size-8 text-accent" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-fg">{file.name}</p>
                  <p className="text-xs text-muted">{formatBytes(file.size)}</p>
                </div>
                <Button type="button" variant="ghost" size="sm" className="ml-auto" onClick={() => setFile(null)}>Change</Button>
              </div>
              <div>
                <label className="mb-1.5 block text-xs text-fg-2" htmlFor="table-name">Table name</label>
                <Input id="table-name" value={tableName} onChange={(e) => setTableName(e.target.value)} placeholder="sales_2025" />
              </div>
              <Checkbox label="Replace the table if it already exists" checked={replace} onChange={(e) => setReplace(e.target.checked)} />
              <Button type="submit" size="lg" disabled={upload.isPending || !tableName.trim()}>
                {upload.isPending ? <><Loader2 className="animate-spin" /> Loading rows…</> : <>Create table <ArrowRight /></>}
              </Button>
            </form>
          )}
        </div>

        <div className="edge rounded-3xl border border-border bg-surface/60 p-6">
          {last ? (
            <div className="animate-fade-up">
              <Badge tone="success">{last.replaced ? 'Replaced' : 'Created'}</Badge>
              <p className="mt-3 font-display text-3xl text-fg">{last.table}</p>
              <p className="text-xs text-muted">{last.rowCount.toLocaleString()} rows · {last.columns.length} columns · {last.dataSourceName}</p>
              <ul className="mt-4 max-h-52 space-y-1 overflow-y-auto pr-1 text-xs">
                {last.columns.map((c) => (
                  <li key={c.name} className="flex items-center justify-between gap-3 rounded-lg px-2 py-1 hover:bg-surface-2">
                    <span className="truncate font-mono text-fg-2">{c.name}</span>
                    <span className="shrink-0 text-muted">{TYPE_LABEL[c.type] ?? c.type}</span>
                  </li>
                ))}
              </ul>
              <Button className="mt-5" onClick={() => { setDataSourceId(last.dataSourceId); navigate(`/app/studio?q=${encodeURIComponent(`Give me an overview of ${last.table}`)}`); }}>
                Ask about it <ArrowRight />
              </Button>
            </div>
          ) : (
            <>
              <p className="eyebrow mb-3">Your uploads</p>
              {uploads.data && uploads.data.length > 0 ? (
                <ul className="space-y-1">
                  {uploads.data.map((t) => (
                    <li key={t.id} className="group flex items-center gap-3 rounded-xl px-2 py-2 hover:bg-surface-2/70">
                      <FileSpreadsheet className="size-4 shrink-0 text-accent" />
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-mono text-xs text-fg">{t.table}</p>
                        <p className="truncate text-[11px] text-muted">{t.rowCount.toLocaleString()} rows · {t.originalFile} · {timeAgo(t.createdAt)}</p>
                      </div>
                      <button className="rounded-lg p-1.5 text-muted opacity-0 transition-opacity hover:text-danger group-hover:opacity-100"
                        aria-label={`Delete ${t.table}`}
                        onClick={async () => { if (await confirm({ title: `Delete ${t.table}?`, description: 'The table and its data are removed from the warehouse.', confirmLabel: 'Delete', danger: true })) remove.mutate(t); }}>
                        <Trash2 className="size-3.5" />
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-muted">Nothing uploaded yet. Tables you create appear here and in the “Uploaded files” connection.</p>
              )}
            </>
          )}
        </div>
      </div>
    </section>
  );
}
