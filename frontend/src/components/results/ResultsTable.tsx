import { useMemo, useState } from 'react';
import * as Dropdown from '@radix-ui/react-dropdown-menu';
import { ArrowDown, ArrowUp, ArrowUpDown, Columns3, LockKeyhole, Search } from 'lucide-react';
import type { Cell, QueryResult } from '@/lib/types';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Input, Select } from '@/components/ui/input';

type Sort = { index: number; dir: 'asc' | 'desc' } | null;

function compare(a: Cell, b: Cell): number {
  if (a === b) return 0;
  if (a === null) return 1;
  if (b === null) return -1;
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  return String(a).localeCompare(String(b), undefined, { numeric: true });
}

function renderCell(v: Cell) {
  if (v === null) return <span className="italic text-muted/70">null</span>;
  if (typeof v === 'boolean') return <span className="font-mono">{String(v)}</span>;
  if (typeof v === 'number') return <span className="tabular-nums">{Number.isInteger(v) ? v : v.toLocaleString(undefined, { maximumFractionDigits: 4 })}</span>;
  return v;
}

export function useTableState(result: QueryResult) {
  const [hidden, setHidden] = useState<Set<number>>(new Set());
  const [filter, setFilter] = useState('');
  const [sort, setSort] = useState<Sort>(null);
  const rows = useMemo(() => {
    const q = filter.trim().toLowerCase();
    let r = q ? result.rows.filter((row) => row.some((c) => c !== null && String(c).toLowerCase().includes(q))) : result.rows;
    if (sort) r = [...r].sort((a, b) => compare(a[sort.index], b[sort.index]) * (sort.dir === 'asc' ? 1 : -1));
    return r;
  }, [result.rows, filter, sort]);
  const visible = result.columns.map((c, i) => ({ ...c, index: i })).filter((c) => !hidden.has(c.index));
  return { hidden, setHidden, filter, setFilter, sort, setSort, rows, visible };
}

export function ResultsTable({ result, state }: { result: QueryResult; state: ReturnType<typeof useTableState> }) {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  const { rows, visible, sort, setSort, filter, setFilter, hidden, setHidden } = state;
  const pages = Math.max(1, Math.ceil(rows.length / pageSize));
  const current = Math.min(page, pages - 1);
  const slice = rows.slice(current * pageSize, current * pageSize + pageSize);

  const toggleSort = (index: number) =>
    setSort(sort?.index !== index ? { index, dir: 'asc' } : sort.dir === 'asc' ? { index, dir: 'desc' } : null);

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex flex-wrap items-center gap-2 border-b border-border px-3 py-2">
        <div className="relative min-w-40 flex-1 sm:max-w-64">
          <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted" />
          <Input value={filter} onChange={(e) => { setFilter(e.target.value); setPage(0); }} placeholder="Filter rows…" className="h-8 pl-8 text-xs" aria-label="Filter rows" />
        </div>
        <Dropdown.Root>
          <Dropdown.Trigger asChild>
            <Button variant="secondary" size="sm"><Columns3 /> Columns ({visible.length}/{result.columns.length})</Button>
          </Dropdown.Trigger>
          <Dropdown.Portal>
            <Dropdown.Content align="start" className="z-50 max-h-72 w-56 overflow-y-auto rounded-lg border border-border bg-surface p-1 shadow-xl">
              {result.columns.map((c, i) => (
                <Dropdown.CheckboxItem key={c.name + i} checked={!hidden.has(i)}
                  onSelect={(e) => e.preventDefault()}
                  onCheckedChange={(checked) => {
                    const next = new Set(hidden);
                    if (checked) next.delete(i); else if (visible.length > 1) next.add(i);
                    setHidden(next);
                  }}
                  className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-xs outline-none data-[highlighted]:bg-surface-2">
                  <span className={cn('size-3.5 rounded border border-border-strong', !hidden.has(i) && 'border-accent bg-accent')} />
                  <span className="truncate">{c.name}</span>
                </Dropdown.CheckboxItem>
              ))}
            </Dropdown.Content>
          </Dropdown.Portal>
        </Dropdown.Root>
        <span className="ml-auto text-xs text-muted">
          {rows.length.toLocaleString()} {filter ? `of ${result.rows.length.toLocaleString()} ` : ''}rows
        </span>
      </div>

      <div className="min-h-0 flex-1 overflow-auto">
        <table className="w-full border-collapse text-xs">
          <thead className="sticky top-0 z-10 bg-surface-2/95 backdrop-blur">
            <tr>
              <th className="w-10 border-b border-border px-2 py-2 text-right font-normal text-muted">#</th>
              {visible.map((c) => (
                <th key={c.index} className="border-b border-border px-3 py-2.5 text-left text-[11px] font-medium uppercase tracking-wider text-muted">
                  <button className="inline-flex items-center gap-1 hover:text-fg" onClick={() => toggleSort(c.index)}
                    aria-label={`Sort by ${c.name}`} title={`${c.sqlType}`}>
                    {result.maskedColumns?.includes(c.name) && <LockKeyhole className="size-3 text-accent" aria-label="masked personal data" />}
                    {c.name}
                    {sort?.index === c.index ? (sort.dir === 'asc' ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />)
                      : <ArrowUpDown className="size-3 opacity-30" />}
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {slice.map((row, r) => (
              <tr key={r} className="border-b border-border/50 transition-colors hover:bg-accent-soft/40 animate-fade" style={{ animationDelay: `${Math.min(r, 20) * 18}ms` }}>
                <td className="px-2 py-1.5 text-right font-mono text-[10px] text-muted">{current * pageSize + r + 1}</td>
                {visible.map((c) => (
                  <td key={c.index} className={cn('max-w-80 truncate px-3 py-2 text-fg', c.category === 'NUMBER' && 'text-right font-mono tabular-nums text-[11.5px]')}>
                    {renderCell(row[c.index])}
                  </td>
                ))}
              </tr>
            ))}
            {slice.length === 0 && (
              <tr><td colSpan={visible.length + 1} className="px-3 py-10 text-center text-muted">No rows{filter ? ' match the filter' : ''}.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between gap-2 border-t border-border px-3 py-2 text-xs text-muted">
        <label className="flex items-center gap-2">Rows per page
          <Select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0); }} className="h-7 w-20 text-xs">
            {[25, 50, 100, 250].map((n) => <option key={n} value={n}>{n}</option>)}
          </Select>
        </label>
        <div className="flex items-center gap-2">
          <Button size="sm" variant="secondary" disabled={current === 0} onClick={() => setPage(current - 1)}>Prev</Button>
          <span>{current + 1} / {pages}</span>
          <Button size="sm" variant="secondary" disabled={current >= pages - 1} onClick={() => setPage(current + 1)}>Next</Button>
        </div>
      </div>
    </div>
  );
}
