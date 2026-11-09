import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Fingerprint, LockKeyhole, Unlock } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import type { PiiOverview, PiiType } from '@/lib/types';
import { cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import { EmptyState, Notice, Skeleton } from '@/components/ui/feedback';

export const PII_TYPES: { id: PiiType; label: string }[] = [
  { id: 'EMAIL', label: 'Email address' }, { id: 'PHONE', label: 'Phone number' }, { id: 'PERSON_NAME', label: 'Person name' },
  { id: 'ADDRESS', label: 'Street address' }, { id: 'POSTAL_CODE', label: 'Postal code' }, { id: 'DATE_OF_BIRTH', label: 'Date of birth' },
  { id: 'NATIONAL_ID', label: 'National ID / tax number' }, { id: 'PAYMENT_CARD', label: 'Payment card' },
  { id: 'BANK_ACCOUNT', label: 'Bank account' }, { id: 'IP_ADDRESS', label: 'IP address' }, { id: 'CREDENTIAL', label: 'Credential / secret' },
  { id: 'OTHER', label: 'Other personal data' },
];

/** Personal-data columns of a data source, with admin controls for masking. */
export function PrivacyPanel({ dataSourceId, canManage }: { dataSourceId: number; canManage: boolean }) {
  const qc = useQueryClient();
  const overview = useQuery({ queryKey: ['pii', dataSourceId], queryFn: () => api.get<PiiOverview>(`/datasources/${dataSourceId}/pii`) });
  const onSaved = (o: PiiOverview) => {
    qc.setQueryData(['pii', dataSourceId], o);
    qc.invalidateQueries({ queryKey: ['schema', dataSourceId] });
    qc.invalidateQueries({ queryKey: ['datasources'] });
  };
  const settings = useMutation({
    mutationFn: (enabled: boolean) => api.put<PiiOverview>(`/datasources/${dataSourceId}/pii/settings`, { enabled }),
    onSuccess: (o) => { onSaved(o); toast.success(o.maskingEnabled ? 'PII masking on' : 'PII masking off'); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const column = useMutation({
    mutationFn: (body: { table: string; column: string; piiType: PiiType; masked: boolean }) => api.put<PiiOverview>(`/datasources/${dataSourceId}/pii/columns`, body),
    onSuccess: onSaved,
    onError: (e) => toast.error(errorMessage(e)),
  });

  if (overview.isLoading) return <Skeleton className="h-72" />;
  if (overview.isError) return <Notice tone="danger">{errorMessage(overview.error)}</Notice>;
  const o = overview.data!;

  return (
    <div className="grid gap-8 lg:grid-cols-[0.8fr_1.2fr]">
      <div>
        <p className="eyebrow">Privacy</p>
        <h2 className="mt-3 font-display text-4xl leading-tight text-fg">Personal data, <span className="italic text-accent">masked.</span></h2>
        <p className="mt-3 max-w-md text-sm text-muted">
          Columns are classified automatically from their names. Analysts and viewers receive masked values — including through
          aliases and expressions — while admins see raw data. Masking happens on the server, before results reach the screen,
          the AI explanation or a CSV export.
        </p>
        <div className="edge mt-6 flex items-center justify-between gap-4 rounded-2xl border border-border bg-surface/70 p-4">
          <div>
            <p className="text-sm text-fg">Masking for this connection</p>
            <p className="text-xs text-muted">{o.youSeeRawValues ? 'You see raw values (admin).' : 'You see masked values.'}</p>
          </div>
          <button role="switch" aria-checked={o.maskingEnabled} disabled={!canManage || settings.isPending}
            onClick={() => settings.mutate(!o.maskingEnabled)}
            className={cn('relative h-7 w-12 rounded-full border transition-colors duration-300 disabled:opacity-50',
              o.maskingEnabled ? 'border-accent/50 bg-accent' : 'border-border-strong bg-surface-3')}>
            <span className={cn('absolute top-0.5 size-5.5 rounded-full bg-white shadow transition-all duration-300', o.maskingEnabled ? 'left-[22px]' : 'left-0.5')} />
            <span className="sr-only">Toggle PII masking</span>
          </button>
        </div>
      </div>

      <div>
        {o.columns.length === 0 ? (
          <EmptyState icon={<Fingerprint />} title="No personal data detected" description="No column names look like emails, names, phones, IDs or similar." />
        ) : (
          <ul className="divide-y divide-border/60 border-y border-border/60">
            {o.columns.map((c) => (
              <li key={`${c.table}.${c.column}`} className="flex flex-wrap items-center gap-3 px-2 py-3">
                {c.masked ? <LockKeyhole className="size-4 text-accent" /> : <Unlock className="size-4 text-muted" />}
                <span className="min-w-40 flex-1 font-mono text-sm text-fg">{c.table}.<span className="text-accent">{c.column}</span></span>
                <Badge>{c.source}</Badge>
                {canManage ? (
                  <>
                    <select value={c.piiType} aria-label={`Type of ${c.column}`}
                      onChange={(e) => column.mutate({ table: c.table, column: c.column, piiType: e.target.value as PiiType, masked: c.masked })}
                      className="rounded-full border border-border bg-surface-2 px-3 py-1 text-xs text-fg-2">
                      {PII_TYPES.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
                    </select>
                    <button onClick={() => column.mutate({ table: c.table, column: c.column, piiType: c.piiType, masked: !c.masked })}
                      className={cn('rounded-full border px-3 py-1 text-xs transition-colors', c.masked ? 'border-accent/40 text-accent hover:bg-accent-soft' : 'border-border text-muted hover:text-fg')}>
                      {c.masked ? 'Masked' : 'Visible'}
                    </button>
                  </>
                ) : (
                  <span className="text-xs text-muted">{c.label}</span>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
