import { useMemo } from 'react';
import { Background, Controls, Handle, MarkerType, Position, ReactFlow, type Edge, type Node, type NodeProps } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { KeyRound, Link2 } from 'lucide-react';
import type { SchemaView, TableView } from '@/lib/types';

type TableNodeData = { table: TableView; onSelect?: (name: string) => void };

function TableNode({ data }: NodeProps<Node<TableNodeData>>) {
  const t = data.table;
  return (
    <div className="w-56 overflow-hidden rounded-lg border border-border-strong bg-surface text-[11px] shadow-lg">
      <Handle type="target" position={Position.Left} className="size-2! border-0! bg-accent!" />
      <button className="flex w-full items-center justify-between bg-accent-soft px-2.5 py-1.5 text-left font-semibold text-fg" onClick={() => data.onSelect?.(t.name)}>
        <span className="truncate">{t.name}</span>
        {t.rowEstimate ? <span className="font-normal text-muted">{t.rowEstimate.toLocaleString()}</span> : null}
      </button>
      <ul>
        {t.columns.slice(0, 14).map((c) => (
          <li key={c.name} className="flex items-center gap-1.5 border-t border-border px-2.5 py-1">
            {c.primaryKey ? <KeyRound className="size-3 text-warning" /> : c.references ? <Link2 className="size-3 text-accent" /> : <span className="size-3" />}
            <span className="truncate text-fg-2">{c.name}</span>
            <span className="ml-auto font-mono text-[10px] text-muted">{c.dataType}</span>
          </li>
        ))}
        {t.columns.length > 14 && <li className="border-t border-border px-2.5 py-1 text-muted">+{t.columns.length - 14} more</li>}
      </ul>
      <Handle type="source" position={Position.Right} className="size-2! border-0! bg-accent!" />
    </div>
  );
}

const nodeTypes = { table: TableNode };

/** Layered layout: tables are placed in columns by their foreign-key depth (referenced tables to the left). */
function layout(schema: SchemaView): Record<string, { x: number; y: number }> {
  const depth: Record<string, number> = {};
  const refs: Record<string, string[]> = {};
  schema.relationships.forEach((r) => {
    if (r.fromTable !== r.toTable) (refs[r.fromTable] ??= []).push(r.toTable);
  });
  const visit = (t: string, seen: Set<string>): number => {
    if (depth[t] !== undefined) return depth[t];
    if (seen.has(t)) return 0;
    seen.add(t);
    const d = (refs[t] ?? []).reduce((m, p) => Math.max(m, visit(p, seen) + 1), 0);
    depth[t] = d;
    return d;
  };
  schema.tables.forEach((t) => visit(t.name, new Set()));
  const columns: Record<number, string[]> = {};
  schema.tables.forEach((t) => (columns[depth[t.name] ?? 0] ??= []).push(t.name));
  const pos: Record<string, { x: number; y: number }> = {};
  Object.entries(columns).forEach(([d, names]) => {
    let y = 0;
    names.forEach((n) => {
      const t = schema.tables.find((x) => x.name === n)!;
      pos[n] = { x: Number(d) * 300, y };
      y += 44 + Math.min(t.columns.length, 15) * 23 + 40;
    });
  });
  return pos;
}

export function ErDiagram({ schema, onSelect }: { schema: SchemaView; onSelect?: (table: string) => void }) {
  const { nodes, edges } = useMemo(() => {
    const pos = layout(schema);
    const nodes: Node<TableNodeData>[] = schema.tables.map((t) => ({
      id: t.name, type: 'table', position: pos[t.name] ?? { x: 0, y: 0 }, data: { table: t, onSelect },
    }));
    const edges: Edge[] = schema.relationships.map((r, i) => ({
      id: `${r.name}-${i}`,
      source: r.fromTable,
      target: r.toTable,
      label: `${r.fromColumns.join(',')} → ${r.toColumns.join(',')}`,
      labelStyle: { fontSize: 10, fill: 'var(--muted)' },
      labelBgStyle: { fill: 'var(--surface)' },
      style: { stroke: 'var(--accent)', strokeWidth: 1.5 },
      markerEnd: { type: MarkerType.ArrowClosed, color: 'var(--accent)' },
    }));
    return { nodes, edges };
  }, [schema, onSelect]);

  return (
    <div className="h-[640px] overflow-hidden rounded-xl border border-border bg-bg">
      <ReactFlow key={schema.refreshedAt} defaultNodes={nodes} defaultEdges={edges} nodeTypes={nodeTypes} fitView minZoom={0.2} proOptions={{ hideAttribution: true }}>
        <Background color="var(--border)" gap={20} />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  );
}
