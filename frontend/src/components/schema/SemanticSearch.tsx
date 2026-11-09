import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { ArrowUpRight, Loader2, Sparkles } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import type { SchemaSearchResponse } from '@/lib/types';
import { Badge } from '@/components/ui/badge';
import { Notice } from '@/components/ui/feedback';

const EXAMPLES = ['customer happiness', 'running low on stock', 'money we refunded', 'where orders ship from', 'how people pay'];

/** Search tables by meaning: embeddings ranked by cosine similarity (pgvector when the database has it). */
export function SemanticSearch({ dataSourceId, onSelect }: { dataSourceId: number; onSelect: (table: string) => void }) {
  const [q, setQ] = useState('');
  const search = useMutation({
    mutationFn: (query: string) => api.get<SchemaSearchResponse>(`/datasources/${dataSourceId}/schema/search`, { q: query }),
  });
  const run = (query: string) => {
    setQ(query);
    if (query.trim()) search.mutate(query.trim());
  };
  const best = search.data?.matches[0]?.score ?? 1;

  return (
    <div className="grid gap-8 lg:grid-cols-[0.9fr_1.1fr]">
      <div>
        <p className="eyebrow">Semantic schema search</p>
        <h2 className="mt-3 font-display text-4xl leading-tight text-fg">Find tables by <span className="italic text-accent">meaning.</span></h2>
        <p className="mt-3 max-w-md text-sm text-muted">
          Every table is described and embedded as a vector. Your words are embedded the same way and matched by cosine
          similarity — the same retrieval the AI uses before it writes SQL.
        </p>
        <form className="mt-6" onSubmit={(e) => { e.preventDefault(); run(q); }}>
          <div className="edge flex items-center gap-2 rounded-full border border-border-strong/70 bg-surface/80 py-1.5 pl-5 pr-1.5 focus-within:border-accent/60">
            <Sparkles className="size-4 shrink-0 text-accent" />
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="e.g. customer happiness" aria-label="Search by meaning"
              className="min-w-0 flex-1 bg-transparent py-2 text-[15px] text-fg placeholder:text-muted/60 focus:outline-none" />
            <button type="submit" disabled={!q.trim() || search.isPending}
              className="rounded-full bg-linear-to-b from-accent to-accent-2 px-4 py-2 text-sm text-accent-fg disabled:opacity-40">
              {search.isPending ? <Loader2 className="size-4 animate-spin" /> : 'Search'}
            </button>
          </div>
        </form>
        <div className="mt-4 flex flex-wrap gap-2">
          {EXAMPLES.map((e) => (
            <button key={e} onClick={() => run(e)} className="rounded-full border border-border px-3 py-1 text-xs text-fg-2 transition-colors hover:border-accent/50 hover:text-fg">{e}</button>
          ))}
        </div>
      </div>

      <div className="min-h-64">
        {search.isError && <Notice tone="danger">{errorMessage(search.error)}</Notice>}
        {search.data && (
          <div className="animate-fade">
            <div className="mb-3 flex items-center gap-2 text-xs text-muted">
              <Badge tone={search.data.engine === 'pgvector' ? 'accent' : 'neutral'}>
                {search.data.engine === 'pgvector' ? 'pgvector · cosine distance' : search.data.engine === 'in-app' ? 'in-app cosine similarity' : 'semantic search off'}
              </Badge>
              {search.data.model && <span className="font-mono">{search.data.model}</span>}
            </div>
            <ul className="divide-y divide-border/60 border-y border-border/60">
              {search.data.matches.map((m, i) => (
                <li key={m.table} className="animate-fade-up" style={{ animationDelay: `${i * 50}ms` }}>
                  <button onClick={() => onSelect(m.table)} className="group grid w-full grid-cols-[1fr_7rem] items-center gap-4 px-2 py-3.5 text-left hover:bg-surface-2/50">
                    <span className="min-w-0">
                      <span className="flex items-center gap-2 font-mono text-sm text-fg">{m.table}
                        <ArrowUpRight className="size-3.5 text-accent opacity-0 transition-opacity group-hover:opacity-100" /></span>
                      {m.description && <span className="mt-0.5 block truncate text-xs text-muted">{m.description}</span>}
                    </span>
                    <span className="flex items-center gap-2">
                      <span className="h-1.5 flex-1 rounded-full bg-surface-3">
                        <span className="block h-full rounded-full bg-linear-to-r from-accent-2 to-accent transition-all duration-700"
                          style={{ width: `${Math.max(4, (Math.max(0, m.score) / Math.max(0.0001, best)) * 100)}%` }} />
                      </span>
                      <span className="w-10 text-right font-mono text-[11px] tabular-nums text-muted">{m.score.toFixed(2)}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
        {!search.data && !search.isError && (
          <div className="flex h-full min-h-64 items-center justify-center rounded-3xl border border-dashed border-border text-sm text-muted">
            Results ranked by similarity appear here.
          </div>
        )}
      </div>
    </div>
  );
}
