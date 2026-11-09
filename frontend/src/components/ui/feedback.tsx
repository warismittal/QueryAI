import type { ReactNode } from 'react';
import { AlertTriangle, Inbox, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from './button';

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-md bg-surface-3', className)} />;
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn('size-4 animate-spin text-muted', className)} />;
}

export function EmptyState({ icon, title, description, action, className }: {
  icon?: ReactNode; title: string; description?: ReactNode; action?: ReactNode; className?: string;
}) {
  return (
    <div className={cn('flex flex-col items-center justify-center rounded-xl border border-dashed border-border px-6 py-12 text-center', className)}>
      <div className="mb-3 flex size-11 items-center justify-center rounded-full bg-accent-soft text-accent [&_svg]:size-5">
        {icon ?? <Inbox />}
      </div>
      <h3 className="text-sm font-semibold text-fg">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-sm text-muted">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({ message, onRetry, className }: { message: string; onRetry?: () => void; className?: string }) {
  return (
    <div role="alert" className={cn('flex items-start gap-3 rounded-xl border border-danger/30 bg-danger-soft p-4 text-sm', className)}>
      <AlertTriangle className="mt-0.5 size-4 shrink-0 text-danger" />
      <div className="flex-1">
        <p className="font-medium text-danger">Something went wrong</p>
        <p className="mt-0.5 break-words text-fg-2">{message}</p>
      </div>
      {onRetry && <Button size="sm" variant="secondary" onClick={onRetry}>Retry</Button>}
    </div>
  );
}

export function Notice({ tone = 'accent', icon, children, className }: {
  tone?: 'accent' | 'warning' | 'danger' | 'success'; icon?: ReactNode; children: ReactNode; className?: string;
}) {
  const tones = {
    accent: 'border-accent/25 bg-accent-soft text-fg-2',
    warning: 'border-warning/30 bg-warning-soft text-fg-2',
    danger: 'border-danger/30 bg-danger-soft text-fg-2',
    success: 'border-success/30 bg-success-soft text-fg-2',
  } as const;
  return (
    <div className={cn('flex items-start gap-2.5 rounded-lg border px-3 py-2.5 text-sm [&_svg]:mt-0.5 [&_svg]:size-4 [&_svg]:shrink-0', tones[tone], className)}>
      {icon}
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-10 flex flex-col gap-5 pt-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="animate-fade-up">
        <h1 className="font-display text-[2.6rem] leading-[1] tracking-[-0.02em] text-fg sm:text-[3.4rem]">{title}</h1>
        {description && <p className="mt-3 max-w-2xl text-[15px] text-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Pagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-end gap-2 text-xs text-muted">
      <Button size="sm" variant="secondary" disabled={page <= 0} onClick={() => onPage(page - 1)}>Previous</Button>
      <span>Page {page + 1} of {totalPages}</span>
      <Button size="sm" variant="secondary" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>Next</Button>
    </div>
  );
}
