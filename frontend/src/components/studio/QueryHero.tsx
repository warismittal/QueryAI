import { useEffect, useRef, useState } from 'react';
import { ArrowUpRight, CornerDownLeft, Loader2, PenLine } from 'lucide-react';
import { QUESTION_CATEGORIES } from '@/lib/questions';
import { cn } from '@/lib/utils';

/**
 * The first thing people see in Ask AI: one beautiful question field and editorial question threads.
 * Feels like asking, not like writing database commands.
 */
export function QueryHero({ sourceName, value, onChange, onSubmit, onPick, onManual, disabledReason, pending }: {
  sourceName?: string;
  value: string;
  onChange: (v: string) => void;
  onSubmit: () => void;
  onPick: (q: string) => void;
  onManual: () => void;
  disabledReason?: string;
  pending: boolean;
}) {
  const [category, setCategory] = useState(QUESTION_CATEGORIES[0].id);
  const ref = useRef<HTMLTextAreaElement>(null);
  const active = QUESTION_CATEGORIES.find((c) => c.id === category) ?? QUESTION_CATEGORIES[0];
  const disabled = !!disabledReason;

  useEffect(() => {
    if (!disabled) ref.current?.focus();
  }, [disabled]);

  return (
    <div className="relative mx-auto flex w-full max-w-5xl flex-col px-5 pb-16 pt-[6vh] sm:px-8">
      <div className="pointer-events-none absolute left-1/2 top-0 -z-10 h-[420px] w-[900px] -translate-x-1/2 rounded-full bg-[radial-gradient(closest-side,rgb(var(--glow)/0.16),transparent)]" />
      <p className="eyebrow animate-fade-up">Ask AI{sourceName ? ` · ${sourceName}` : ''}</p>
      <h1 className="mt-4 font-display text-[clamp(2.4rem,5.4vw,4.6rem)] leading-[0.98] text-fg animate-fade-up [animation-delay:80ms]">
        What would you like <span className="italic ombre-text">to understand?</span>
      </h1>

      <form className="group relative mt-10 animate-fade-up [animation-delay:160ms]" onSubmit={(e) => { e.preventDefault(); onSubmit(); }}>
        <div className={cn('edge rounded-[28px] border bg-surface/80 p-2 pl-6 backdrop-blur-xl transition-all duration-500',
          disabled ? 'border-border opacity-70' : 'border-border-strong/80 focus-within:border-accent/60 focus-within:shadow-[0_0_0_6px_rgb(var(--glow)/0.08),0_30px_80px_-30px_rgb(var(--glow)/0.55)]')}>
          <div className="flex items-end gap-3">
            <textarea ref={ref} value={value} onChange={(e) => onChange(e.target.value)} rows={2} maxLength={4000}
              disabled={disabled || pending}
              placeholder={disabled ? disabledReason : 'Ask your data something interesting…'}
              onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); onSubmit(); } }}
              aria-label="Your question"
              className="min-h-16 flex-1 resize-none bg-transparent py-4 font-display text-2xl leading-snug text-fg placeholder:text-muted/60 focus:outline-none sm:text-[1.7rem]" />
            <button type="submit" disabled={disabled || pending || !value.trim()} aria-label="Ask"
              className="mb-1 mr-1 flex size-12 shrink-0 items-center justify-center rounded-full bg-linear-to-b from-accent to-accent-2 text-accent-fg shadow-[0_12px_30px_-10px_rgb(var(--glow)/0.9)] transition-all duration-300 hover:scale-105 disabled:scale-100 disabled:opacity-40">
              {pending ? <Loader2 className="size-5 animate-spin" /> : <CornerDownLeft className="size-5" />}
            </button>
          </div>
        </div>
        <div className="mt-3 flex flex-wrap items-center justify-between gap-2 px-2 text-[11px] text-muted">
          <span>Enter to ask · Shift + Enter for a new line · answers run read-only against your database</span>
          <button type="button" onClick={onManual} className="inline-flex items-center gap-1.5 hover:text-fg"><PenLine className="size-3.5" /> Write SQL yourself</button>
        </div>
      </form>

      {/* editorial question threads */}
      <div className="mt-14 grid gap-10 md:grid-cols-[14rem_1fr] animate-fade-up [animation-delay:240ms]">
        <nav aria-label="Question themes" className="flex gap-1 overflow-x-auto md:flex-col md:overflow-visible">
          {QUESTION_CATEGORIES.map((c, i) => (
            <button key={c.id} onClick={() => setCategory(c.id)}
              className={cn('group/c relative shrink-0 rounded-2xl px-4 py-3 text-left transition-all duration-300',
                c.id === category ? 'bg-surface-2/80' : 'hover:bg-surface-2/40')}>
              <span className={cn('absolute left-0 top-1/2 h-6 w-px -translate-y-1/2 bg-accent transition-opacity duration-300', c.id === category ? 'opacity-100' : 'opacity-0')} />
              <span className="block font-mono text-[10px] text-muted">0{i + 1}</span>
              <span className={cn('block whitespace-nowrap font-display text-xl transition-colors', c.id === category ? 'text-fg' : 'text-fg-2 group-hover/c:text-fg')}>{c.title}</span>
            </button>
          ))}
        </nav>
        <div key={active.id} className="animate-fade">
          <p className="eyebrow mb-3">{active.kicker}</p>
          <ul className="divide-y divide-border/60 border-y border-border/60">
            {active.questions.map((q, i) => (
              <li key={q} className="animate-fade-up" style={{ animationDelay: `${i * 60}ms` }}>
                <button onClick={() => onPick(q)} disabled={disabled || pending}
                  className="group/q flex w-full items-center justify-between gap-6 px-2 py-5 text-left transition-all duration-500 hover:bg-[linear-gradient(90deg,rgb(var(--glow)/0.1),transparent_70%)] hover:pl-5 disabled:opacity-50">
                  <span className="font-display text-[1.45rem] leading-snug text-fg-2 transition-colors duration-300 group-hover/q:text-fg sm:text-[1.7rem]">{q}</span>
                  <ArrowUpRight className="size-5 shrink-0 text-accent opacity-0 transition-all duration-300 group-hover/q:translate-x-0.5 group-hover/q:opacity-100" />
                </button>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
