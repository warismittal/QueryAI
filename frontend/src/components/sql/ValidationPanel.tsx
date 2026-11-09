import { AlertTriangle, CheckCircle2, Info, XCircle } from 'lucide-react';
import type { SqlValidationResult } from '@/lib/types';
import { formatCompact } from '@/lib/utils';
import { ValidationBadge } from '@/components/ui/status';

export function ValidationPanel({ validation }: { validation: SqlValidationResult }) {
  const icon = { ERROR: <XCircle className="size-3.5 text-danger" />, WARNING: <AlertTriangle className="size-3.5 text-warning" />, INFO: <Info className="size-3.5 text-muted" /> };
  return (
    <div className="space-y-2 text-xs">
      <div className="flex flex-wrap items-center gap-2">
        <ValidationBadge status={validation.status} />
        {validation.databaseChecked && <span className="flex items-center gap-1 text-success"><CheckCircle2 className="size-3.5" /> Database dry run passed</span>}
        {validation.estimatedCost != null && <span className="text-muted">est. cost {formatCompact(validation.estimatedCost)}</span>}
        {validation.estimatedRows != null && <span className="text-muted">· est. rows {formatCompact(validation.estimatedRows)}</span>}
        {validation.referencedTables.length > 0 && <span className="text-muted">· tables: {validation.referencedTables.join(', ')}</span>}
      </div>
      {validation.issues.length > 0 && (
        <ul className="space-y-1">
          {validation.issues.map((i, idx) => (
            <li key={idx} className="flex items-start gap-2">
              {icon[i.severity]}
              <span className={i.severity === 'ERROR' ? 'text-danger' : 'text-fg-2'}>{i.message}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function ParameterInputs({ names, values, onChange }: {
  names: string[]; values: Record<string, string>; onChange: (v: Record<string, string>) => void;
}) {
  if (!names.length) return null;
  return (
    <div className="flex flex-wrap items-end gap-2">
      {names.map((n) => (
        <label key={n} className="text-[11px] text-muted">
          <span className="font-mono">:{n}</span>
          <input
            value={values[n] ?? ''}
            onChange={(e) => onChange({ ...values, [n]: e.target.value })}
            placeholder={/date|day|since|until|start|end/i.test(n) ? 'YYYY-MM-DD' : 'value'}
            className="mt-0.5 block h-7 w-36 rounded-md border border-border bg-surface px-2 text-xs text-fg focus:border-accent focus:outline-none"
          />
        </label>
      ))}
    </div>
  );
}

/** Converts string inputs into JSON values (numbers stay numbers; ISO dates are coerced server-side). */
export function toParams(names: string[], values: Record<string, string>): Record<string, string | number> | undefined {
  if (!names.length) return undefined;
  const out: Record<string, string | number> = {};
  names.forEach((n) => {
    const v = (values[n] ?? '').trim();
    out[n] = v !== '' && !Number.isNaN(Number(v)) && !/^\d{4}-/.test(v) ? Number(v) : v;
  });
  return out;
}
