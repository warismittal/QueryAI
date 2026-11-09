import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ArrowDown, ArrowRight } from 'lucide-react';
import { useTheme } from '@/lib/theme';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import type { LibraryScene } from './libraryScene';
import { segment, useScrollProgress, window4 } from './useScrollProgress';

const SQL: [string, 'k' | 'i' | 'f' | 'n'][][] = [
  [['SELECT', 'k'], [' p.name,', 'i']],
  [['       ', 'i'], ['ROUND', 'f'], ['(', 'i'], ['AVG', 'f'], ['(r.rating), 2) ', 'i'], ['AS', 'k'], [' avg_rating,', 'i']],
  [['       ', 'i'], ['COUNT', 'f'], ['(*) ', 'i'], ['AS', 'k'], [' reviews', 'i']],
  [['FROM', 'k'], [' product_reviews r', 'i']],
  [['JOIN', 'k'], [' products p ', 'i'], ['ON', 'k'], [' p.id = r.product_id', 'i']],
  [['GROUP BY', 'k'], [' p.name', 'i']],
  [['HAVING', 'k'], [' ', 'i'], ['COUNT', 'f'], ['(*) >= ', 'i'], ['20', 'n']],
  [['ORDER BY', 'k'], [' avg_rating ', 'i'], ['DESC', 'k']],
  [['LIMIT', 'k'], [' ', 'i'], ['5', 'n'], [';', 'i']],
];
const SQL_LENGTH = SQL.reduce((n, l) => n + l.reduce((m, [t]) => m + t.length, 0) + 1, 0);
const BARS = [
  { label: 'Trail Runner X', value: 4.62 },
  { label: 'Oak Dining Chair', value: 4.48 },
  { label: 'Silent Orbit', value: 4.41 },
  { label: 'Chino Trousers', value: 4.3 },
  { label: 'Power Bank 20K', value: 4.21 },
];

function sqlColor(kind: 'k' | 'i' | 'f' | 'n') {
  return kind === 'k' ? 'text-accent' : kind === 'f' ? 'text-[#c7a26a]' : kind === 'n' ? 'text-[#8fb3d9]' : 'text-fg-2';
}

/** Typed SQL: reveals characters as the scene progresses, so scrolling literally writes the query. */
function TypedSql({ amount }: { amount: number }) {
  let budget = Math.floor(amount * SQL_LENGTH);
  let caretPlaced = amount >= 1;
  return (
    <pre className="font-mono text-[12.5px] leading-[1.75] sm:text-sm">
      {SQL.map((line, li) => {
        const parts: ReactNode[] = [];
        line.forEach(([text, kind], pi) => {
          const shown = text.slice(0, Math.max(0, budget));
          budget -= text.length;
          parts.push(<span key={pi} className={sqlColor(kind)}>{shown}</span>);
        });
        let caret = false;
        if (!caretPlaced && budget <= 0) {
          caret = true;
          caretPlaced = true;
        }
        budget -= 1; // newline
        return (
          <div key={li} className="flex min-h-[1.75em]">
            <span className="mr-4 w-4 select-none text-right text-muted/50">{li + 1}</span>
            <span className={cn(caret && 'caret')}>{parts}</span>
          </div>
        );
      })}
    </pre>
  );
}

function pickPalette(theme: string) {
  return (['crimson', 'graphite', 'marine', 'azure', 'light'] as const).find((t) => t === theme) ?? 'midnight';
}

export function LibraryHero({ signedIn }: { signedIn: boolean }) {
  const sectionRef = useRef<HTMLElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const sceneRef = useRef<LibraryScene | null>(null);
  const [webgl, setWebgl] = useState<'loading' | 'ready' | 'unavailable'>('loading');
  const { theme } = useTheme();
  const progress = useScrollProgress(sectionRef);

  // create the 3D scene lazily (three.js is its own chunk)
  useEffect(() => {
    let disposed = false;
    let observer: IntersectionObserver | null = null;
    let onResize: (() => void) | null = null;
    let onVisibility: (() => void) | null = null;
    const canvas = canvasRef.current;
    if (!canvas) return;
    import('./libraryScene').then(({ createLibraryScene, PALETTES }) => {
      if (disposed) return;
      const nav = navigator as Navigator & { deviceMemory?: number };
      const low = (nav.hardwareConcurrency ?? 8) <= 4 || (nav.deviceMemory ?? 8) <= 4 || window.innerWidth < 760;
      let scene: LibraryScene;
      try {
        scene = createLibraryScene(canvas, PALETTES[pickPalette(document.documentElement.dataset.theme ?? 'midnight')], low ? 'low' : 'high');
      } catch {
        setWebgl('unavailable');
        return;
      }
      sceneRef.current = scene;
      const size = () => scene.resize(canvas.clientWidth, canvas.clientHeight);
      size();
      onResize = size;
      window.addEventListener('resize', size);
      let visible = true;
      observer = new IntersectionObserver(([entry]) => {
        visible = entry.isIntersecting;
        if (visible && !document.hidden) scene.start();
        else scene.stop();
      });
      observer.observe(canvas);
      onVisibility = () => (document.hidden || !visible ? scene.stop() : scene.start());
      document.addEventListener('visibilitychange', onVisibility);
      scene.start();
      setWebgl('ready');
    }).catch(() => setWebgl('unavailable'));
    return () => {
      disposed = true;
      observer?.disconnect();
      if (onResize) window.removeEventListener('resize', onResize);
      if (onVisibility) document.removeEventListener('visibilitychange', onVisibility);
      sceneRef.current?.dispose();
      sceneRef.current = null;
    };
  }, []);

  useEffect(() => {
    sceneRef.current?.setProgress(progress);
  }, [progress]);

  useEffect(() => {
    if (!sceneRef.current) return;
    import('./libraryScene').then(({ PALETTES }) => sceneRef.current?.setPalette(PALETTES[pickPalette(theme)]));
  }, [theme, webgl]);

  const s1 = window4(progress, -1, 0, 0.12, 0.2);
  const s2 = window4(progress, 0.17, 0.24, 0.38, 0.45);
  const s3 = window4(progress, 0.42, 0.49, 0.6, 0.67);
  const s4 = window4(progress, 0.64, 0.7, 0.8, 0.86);
  const s5 = window4(progress, 0.84, 0.92, 2, 3);
  const typed = segment(progress, 0.22, 0.4);
  const bars = segment(progress, 0.45, 0.58);
  const insight = segment(progress, 0.67, 0.76);

  const scene = (opacity: number, children: ReactNode, className?: string) => (
    <div className={cn('pointer-events-none absolute inset-0 flex px-5 sm:px-10 lg:px-16', className)}
      style={{ opacity, visibility: opacity > 0.01 ? 'visible' : 'hidden', transform: `translate3d(0, ${(1 - opacity) * 18}px, 0)` }}
      aria-hidden={opacity < 0.5}>
      {children}
    </div>
  );

  return (
    <section ref={sectionRef} className="relative h-[560vh]" aria-label="QueryAI story">
      <div className="sticky top-0 h-screen w-full overflow-hidden">
        {/* the library */}
        <canvas ref={canvasRef} className={cn('absolute inset-0 h-full w-full transition-opacity duration-1000', webgl === 'ready' ? 'opacity-100' : 'opacity-0')} />
        {webgl !== 'ready' && (
          <div className="absolute inset-0" style={{ background: 'radial-gradient(70% 60% at 50% 70%, rgb(var(--glow) / 0.28), transparent 70%), linear-gradient(180deg, var(--bg-2), var(--bg))' }} />
        )}
        {/* cinematic grading: vignette + left scrim for legible type + grain */}
        <div className="grain pointer-events-none absolute inset-0" />
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(120%_90%_at_50%_45%,transparent_55%,rgb(0_0_0/0.65))] dark:opacity-100 opacity-40" />
        <div className="pointer-events-none absolute inset-y-0 left-0 w-full bg-linear-to-r from-bg/85 via-bg/30 to-transparent sm:w-2/3" />
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-40 bg-linear-to-t from-bg to-transparent" />

        {/* Scene 1 — Curiosity */}
        {scene(s1, (
          <div className="pointer-events-auto mt-auto mb-[14vh] max-w-3xl">
            <p className="eyebrow mb-6 animate-fade-up">QueryAI · intelligent text-to-SQL</p>
            <h1 className="font-display text-[clamp(3rem,8.5vw,7.5rem)] leading-[0.92] text-fg animate-fade-up [animation-delay:120ms]">
              What is happening<br /><span className="italic ombre-text">inside my data?</span>
            </h1>
            <p className="mt-7 max-w-xl text-[15px] leading-relaxed text-fg-2 sm:text-base animate-fade-up [animation-delay:260ms]">
              Ask in plain language. QueryAI reads your schema, writes the SQL, proves it is safe, runs it read-only and tells you
              what the numbers mean.
            </p>
            <div className="mt-9 flex flex-wrap items-center gap-3 animate-fade-up [animation-delay:380ms]">
              <Button size="lg" asChild>
                <Link to={signedIn ? '/app/studio' : '/register'}>{signedIn ? 'Ask your data' : 'Begin exploring'} <ArrowRight /></Link>
              </Button>
              <Button size="lg" variant="ghost" onClick={() => window.scrollTo({ top: window.innerHeight * 1.1, behavior: 'smooth' })}>
                Walk the library <ArrowDown />
              </Button>
            </div>
          </div>
        ))}

        {/* Scene 2 — Intelligence: the question becomes SQL */}
        {scene(s2, (
          <div className="my-auto grid w-full max-w-6xl items-center gap-10 lg:grid-cols-[0.9fr_1.1fr]">
            <div>
              <p className="eyebrow mb-5">II · Intelligence</p>
              <p className="font-display text-[clamp(2.2rem,5vw,4.4rem)] italic leading-[1.02] text-fg">
                “Which product is <span className="text-accent">loved</span> the most?”
              </p>
              <p className="mt-6 max-w-md text-sm leading-relaxed text-fg-2">
                Old knowledge, new language. Your question is matched to the right tables by meaning — reviews, ratings,
                products — then rises off the page as SQL, checked against the database before anything runs.
              </p>
            </div>
            <div className="glass edge rounded-3xl p-6 shadow-[0_40px_120px_-40px_rgb(0_0_0/0.9)] sm:p-8">
              <div className="mb-4 flex items-center justify-between text-[11px] text-muted">
                <span className="eyebrow">Generated SQL</span>
                <span className={cn('rounded-full border px-2.5 py-0.5 transition-colors duration-500', typed >= 1 ? 'border-success/40 text-success' : 'border-border text-muted')}>
                  {typed >= 1 ? 'validated · read-only' : 'writing…'}
                </span>
              </div>
              <TypedSql amount={typed} />
            </div>
          </div>
        ))}

        {/* Scene 3 — Discovery: the chart emerges */}
        {scene(s3, (
          <div className="my-auto grid w-full max-w-6xl items-center gap-10 lg:grid-cols-[1.1fr_0.9fr]">
            <div className="glass edge order-2 rounded-3xl p-6 sm:p-8 lg:order-1">
              <div className="mb-6 flex items-baseline justify-between">
                <p className="eyebrow">Average rating · top products</p>
                <p className="font-mono text-[11px] text-muted">5 rows · 38 ms</p>
              </div>
              <div className="space-y-4">
                {BARS.map((b, i) => {
                  const grow = segment(bars, i * 0.12, 0.5 + i * 0.12);
                  return (
                    <div key={b.label} className="grid grid-cols-[8.5rem_1fr_3rem] items-center gap-3 text-sm">
                      <span className="truncate text-fg-2">{b.label}</span>
                      <div className="h-2.5 rounded-full bg-surface-3/70">
                        <div className="h-full rounded-full bg-linear-to-r from-accent-2 to-accent shadow-[0_0_18px_rgb(var(--glow)/0.55)]"
                          style={{ width: `${grow * ((b.value - 3.4) / 1.3) * 100}%` }} />
                      </div>
                      <span className="text-right font-mono text-xs text-fg tabular-nums">{(b.value * grow).toFixed(2)}</span>
                    </div>
                  );
                })}
              </div>
            </div>
            <div className="order-1 lg:order-2">
              <p className="eyebrow mb-5">III · Discovery</p>
              <p className="font-display text-[clamp(2.4rem,5.4vw,4.8rem)] leading-[1] text-fg">
                Numbers become <span className="italic text-accent">shape.</span>
              </p>
              <p className="mt-6 max-w-sm text-sm leading-relaxed text-fg-2">
                Every answer arrives with the chart it deserves — bars that rise, lines that draw themselves — straight from the
                rows the database returned.
              </p>
            </div>
          </div>
        ))}

        {/* Scene 4 — Understanding */}
        {scene(s4, (
          <div className="my-auto w-full max-w-5xl">
            <p className="eyebrow mb-6">IV · Understanding</p>
            <p className="font-display text-[clamp(5rem,16vw,13rem)] leading-[0.85] text-accent tabular-nums">
              {Math.round(32 * insight)}%
            </p>
            <p className="mt-6 max-w-2xl font-display text-[clamp(1.6rem,3.4vw,2.8rem)] leading-[1.1] text-fg">
              Your highest-performing product generated 32% of total revenue.
            </p>
            <p className="mt-5 text-xs text-muted">Example insight · QueryAI explains every result in plain language</p>
          </div>
        ))}

        {/* Scene 5 — the workspace */}
        {scene(s5, (
          <div className="pointer-events-auto my-auto flex w-full flex-col items-center text-center">
            <p className="eyebrow mb-5">V · Beyond the door</p>
            <p className="font-display text-[clamp(2.6rem,6vw,5.4rem)] leading-[0.98] text-fg">
              Step through. <span className="italic ombre-text">Ask anything.</span>
            </p>
            <p className="mt-5 max-w-lg text-sm text-fg-2">From centuries of shelves to a living city of data — one question away.</p>
            <div className="mt-9 flex flex-wrap justify-center gap-3">
              <Button size="lg" asChild><Link to={signedIn ? '/app/studio' : '/register'}>Open the workspace <ArrowRight /></Link></Button>
              {!signedIn && <Button size="lg" variant="outline" asChild><Link to="/login">Sign in</Link></Button>}
            </div>
          </div>
        ), 'justify-center')}

        {/* progress rail */}
        <div className="pointer-events-none absolute bottom-8 right-6 hidden flex-col items-end gap-2 sm:flex" aria-hidden="true">
          {['Curiosity', 'Intelligence', 'Discovery', 'Understanding', 'Workspace'].map((label, i) => {
            const active = [s1, s2, s3, s4, s5][i] > 0.5;
            return (
              <div key={label} className="flex items-center gap-3">
                <span className={cn('text-[10px] uppercase tracking-[0.2em] transition-all duration-500', active ? 'text-fg opacity-100' : 'text-muted opacity-0')}>{label}</span>
                <span className={cn('h-px transition-all duration-500', active ? 'w-10 bg-accent' : 'w-4 bg-border-strong')} />
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
