import type { CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ArrowUpRight, FileUp, Fingerprint, Gauge, Layers, ShieldCheck, Sparkles } from 'lucide-react';
import { useAuth } from '@/lib/auth';
import { QUESTION_CATEGORIES } from '@/lib/questions';
import { Button } from '@/components/ui/button';
import { FloatingNav } from '@/components/layout/FloatingNav';
import { Logo } from '@/components/layout/Logo';
import { LibraryHero } from '@/components/landing/LibraryHero';
import { useReveal } from '@/components/landing/useScrollProgress';

const PIPELINE = ['Question', 'Intent', 'Semantic schema search', 'Business glossary', 'SQL', 'Validation & self-repair', 'Read-only run', 'Explanation'];

export default function Landing() {
  const { user } = useAuth();
  const rootRef = useReveal<HTMLDivElement>();
  const start = user ? '/app/studio' : '/register';

  return (
    <div ref={rootRef} className="relative min-h-screen overflow-x-clip">
      <FloatingNav
        variant="landing"
        right={user ? (
          <Button asChild size="sm"><Link to="/app">Open workspace <ArrowRight /></Link></Button>
        ) : (
          <>
            <Button asChild size="sm" variant="ghost" className="hidden sm:inline-flex"><Link to="/login">Sign in</Link></Button>
            <Button asChild size="sm"><Link to="/register">Get started</Link></Button>
          </>
        )}
      />

      <LibraryHero signedIn={!!user} />

      {/* ---------- Ask like you think ---------- */}
      <section className="relative mx-auto max-w-7xl px-5 pb-32 pt-24 sm:px-10" aria-labelledby="ask-title">
        <div className="grid gap-16 lg:grid-cols-[0.8fr_1.2fr]">
          <div className="lg:sticky lg:top-32 lg:self-start">
            <p className="eyebrow mb-5" data-reveal>Ask like you think</p>
            <h2 id="ask-title" className="font-display text-[clamp(2.6rem,5vw,4.6rem)] leading-[0.98] text-fg" data-reveal>
              Questions, <span className="italic text-accent">not</span> queries.
            </h2>
            <p className="mt-6 max-w-md text-[15px] leading-relaxed text-fg-2" data-reveal>
              QueryAI is built around the way people actually wonder about their business — the best, the lowest, what is
              changing and where to go next. Pick a thread and pull.
            </p>
            <Button asChild size="lg" className="mt-9" data-reveal><Link to={start}>Ask your own <ArrowRight /></Link></Button>
          </div>
          <div className="divide-y divide-border/70 border-y border-border/70">
            {QUESTION_CATEGORIES.map((cat, ci) => (
              <div key={cat.id} className="group grid gap-4 py-9 sm:grid-cols-[13rem_1fr]" data-reveal style={{ '--reveal-delay': `${ci * 60}ms` } as CSSProperties}>
                <div>
                  <p className="font-mono text-[11px] text-muted">0{ci + 1}</p>
                  <p className="mt-2 font-display text-[1.9rem] leading-none text-fg transition-colors duration-500 group-hover:text-accent">{cat.title}</p>
                  <p className="mt-2 text-xs text-muted">{cat.kicker}</p>
                </div>
                <ul className="space-y-1">
                  {cat.questions.map((q) => (
                    <li key={q}>
                      <Link to={user ? `/app/studio?q=${encodeURIComponent(q)}` : start}
                        className="group/q relative flex items-center justify-between gap-4 rounded-xl px-3 py-2 text-[15px] text-fg-2 transition-all duration-300 hover:bg-[linear-gradient(90deg,rgb(var(--glow)/0.12),transparent)] hover:pl-5 hover:text-fg">
                        <span>{q}</span>
                        <ArrowUpRight className="size-4 shrink-0 -translate-x-1 text-accent opacity-0 transition-all duration-300 group-hover/q:translate-x-0 group-hover/q:opacity-100" />
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ---------- the pipeline ---------- */}
      <section className="relative border-y border-border/60 bg-surface/40 py-20" aria-label="How an answer is made">
        <div className="mx-auto max-w-7xl px-5 sm:px-10">
          <p className="eyebrow mb-8" data-reveal>From question to answer — every step visible</p>
          <ol className="flex flex-wrap items-center gap-x-3 gap-y-4" data-reveal>
            {PIPELINE.map((step, i) => (
              <li key={step} className="flex items-center gap-3">
                <span className="rounded-full border border-border bg-surface-2/70 px-4 py-2 text-sm text-fg-2">{step}</span>
                {i < PIPELINE.length - 1 && <span className="h-px w-6 bg-linear-to-r from-border-strong to-accent/60" aria-hidden="true" />}
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* ---------- capabilities: asymmetric editorial grid ---------- */}
      <section className="mx-auto max-w-7xl px-5 py-32 sm:px-10" aria-labelledby="cap-title">
        <h2 id="cap-title" className="max-w-3xl font-display text-[clamp(2.4rem,4.6vw,4.2rem)] leading-[1] text-fg" data-reveal>
          Elegant on the surface. <span className="italic text-silver">Rigorous underneath.</span>
        </h2>
        <div className="mt-16 grid gap-5 lg:grid-cols-6">
          <article className="edge ombre-panel relative overflow-hidden rounded-3xl border border-border p-8 lg:col-span-4 lg:row-span-2" data-reveal>
            <ShieldCheck className="size-6 text-accent" />
            <h3 className="mt-6 font-display text-4xl text-fg">Validated before it runs.</h3>
            <p className="mt-4 max-w-lg text-sm leading-relaxed text-fg-2">
              Every statement is parsed and checked — single SELECT only, no system catalogs, only tables you are allowed to see —
              then dry-run with EXPLAIN for cost. It executes in a read-only transaction that is always rolled back, with a
              timeout and a row cap. If validation fails, the model repairs its own SQL once and tries again.
            </p>
            <div className="mt-10 grid max-w-xl grid-cols-3 gap-px overflow-hidden rounded-2xl border border-border bg-border text-center">
              {[['SELECT', 'only'], ['EXPLAIN', 'cost-checked'], ['ROLLBACK', 'always']].map(([a, b]) => (
                <div key={a} className="bg-surface/90 px-3 py-5">
                  <p className="font-mono text-sm text-fg">{a}</p>
                  <p className="mt-1 text-[11px] text-muted">{b}</p>
                </div>
              ))}
            </div>
            <div className="pointer-events-none absolute -right-24 -top-24 size-72 rounded-full bg-[radial-gradient(circle,rgb(var(--glow)/0.25),transparent_65%)]" />
          </article>
          <article className="edge rounded-3xl border border-border bg-surface/70 p-7 lg:col-span-2" data-reveal>
            <Layers className="size-5 text-accent" />
            <h3 className="mt-5 font-display text-2xl text-fg">Understands meaning.</h3>
            <p className="mt-3 text-sm leading-relaxed text-fg-2">
              Tables are embedded and searched with pgvector, so “loved” finds reviews and “running low” finds inventory —
              combined with keyword ranking and foreign-key paths.
            </p>
          </article>
          <article className="edge rounded-3xl border border-border bg-surface/70 p-7 lg:col-span-2" data-reveal>
            <Fingerprint className="size-5 text-accent" />
            <h3 className="mt-5 font-display text-2xl text-fg">Personal data, masked.</h3>
            <p className="mt-3 text-sm leading-relaxed text-fg-2">
              Emails, names, phones and IDs are detected automatically and masked for analysts — even through aliases — before
              results reach the screen, the AI or an export.
            </p>
          </article>
          <article className="edge rounded-3xl border border-border bg-surface/70 p-7 lg:col-span-3" data-reveal>
            <FileUp className="size-5 text-accent" />
            <h3 className="mt-5 font-display text-2xl text-fg">Bring a spreadsheet.</h3>
            <p className="mt-3 max-w-md text-sm leading-relaxed text-fg-2">
              Drop a CSV and it becomes a typed PostgreSQL table in seconds — dates, numbers and booleans inferred — ready to ask
              questions about.
            </p>
          </article>
          <article className="edge rounded-3xl border border-border bg-surface/70 p-7 lg:col-span-3" data-reveal>
            <Gauge className="size-5 text-accent" />
            <h3 className="mt-5 font-display text-2xl text-fg">Measured, not promised.</h3>
            <p className="mt-3 max-w-md text-sm leading-relaxed text-fg-2">
              A 40-question execution-accuracy benchmark runs against the real pipeline in CI, so every change to prompts, retrieval
              or models is scored on the same questions.
            </p>
          </article>
        </div>
      </section>

      {/* ---------- final call ---------- */}
      <section className="relative overflow-hidden px-5 pb-28 pt-10 text-center sm:px-10">
        <div className="pointer-events-none absolute left-1/2 top-1/2 h-[520px] w-[820px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[radial-gradient(closest-side,rgb(var(--glow)/0.3),transparent)]" />
        <div className="relative" data-reveal>
          <Sparkles className="mx-auto size-6 text-accent" />
          <h2 className="mx-auto mt-6 max-w-3xl font-display text-[clamp(2.6rem,6vw,5.2rem)] leading-[0.98] text-fg">
            A beautifully designed way to <span className="italic ombre-text">understand your data.</span>
          </h2>
          <div className="mt-10 flex flex-wrap justify-center gap-3">
            <Button asChild size="lg"><Link to={start}>{user ? 'Open the workspace' : 'Create a free account'} <ArrowRight /></Link></Button>
            {!user && <Button asChild size="lg" variant="outline"><Link to="/login">Sign in</Link></Button>}
          </div>
        </div>
      </section>

      <footer className="border-t border-border/60 px-5 py-10 sm:px-10">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 text-xs text-muted">
          <Logo />
          <p>Spring Boot · Spring AI · PostgreSQL + pgvector · React · three.js</p>
          <a href="https://github.com/warismittal" className="hover:text-fg" target="_blank" rel="noreferrer">Built by Waris</a>
        </div>
      </footer>
    </div>
  );
}
