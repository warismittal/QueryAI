import { useEffect, useState, type ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { Menu, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Logo } from './Logo';
import { ThemeSelector } from './ThemeSelector';

export const PRIMARY_NAV = [
  { to: '/app', label: 'Home', end: true },
  { to: '/app/schema', label: 'Explore' },
  { to: '/app/studio', label: 'Ask AI' },
  { to: '/app/dashboards', label: 'Visualizations' },
  { to: '/app/saved', label: 'Saved Queries' },
  { to: '/app/history', label: 'History' },
  { to: '/app/workspace', label: 'Settings' },
];

/**
 * The floating navigation bar shared by the landing page and the workspace. On the landing page it starts as a
 * transparent hairline capsule and condenses into a defined dark bar once the visitor scrolls.
 */
export function FloatingNav({ variant, right, extra }: {
  variant: 'landing' | 'app';
  /** Right-hand controls (account menu, CTA). */
  right?: ReactNode;
  /** Optional extra menu entries shown in the mobile sheet. */
  extra?: ReactNode;
}) {
  const [scrolled, setScrolled] = useState(variant === 'app');
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (variant === 'app') return;
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [variant]);

  return (
    <header className={cn('pointer-events-none fixed inset-x-0 top-0 z-50 flex justify-center px-3 transition-all duration-500 sm:px-5',
      scrolled ? 'pt-3' : 'pt-5')}>
      <nav aria-label="Main"
        className={cn('pointer-events-auto flex w-full items-center gap-2 rounded-full border transition-all duration-500 ease-out',
          scrolled
            ? 'glass max-w-[1320px] border-border/80 py-1.5 pl-4 pr-1.5 shadow-[0_18px_50px_-24px_rgb(0_0_0/0.85)]'
            : 'glass-soft max-w-[1400px] py-2.5 pl-5 pr-2.5')}>
        <Link to={variant === 'landing' ? '/' : '/app'} className="mr-2 shrink-0" aria-label="QueryAI home"><Logo /></Link>

        <div className="hidden flex-1 items-center justify-center gap-0.5 lg:flex">
          {PRIMARY_NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end}
              className={({ isActive }) => cn(
                'group relative rounded-full px-3 py-1.5 text-[13px] tracking-[-0.005em] transition-colors duration-300',
                variant === 'app' && isActive ? 'text-fg' : 'text-fg-2/80 hover:text-fg',
              )}>
              {({ isActive }) => (
                <>
                  <span className={cn('absolute inset-0 rounded-full bg-surface-3/0 transition-colors duration-300 group-hover:bg-surface-3/60',
                    variant === 'app' && isActive && 'bg-surface-3/80')} />
                  <span className="relative">{item.label}</span>
                  <span className={cn('absolute -bottom-px left-1/2 h-px -translate-x-1/2 bg-linear-to-r from-transparent via-accent to-transparent transition-all duration-500',
                    variant === 'app' && isActive ? 'w-8 opacity-100' : 'w-0 opacity-0 group-hover:w-6 group-hover:opacity-80')} />
                </>
              )}
            </NavLink>
          ))}
        </div>

        <div className="ml-auto flex items-center gap-1.5 lg:ml-0">
          <ThemeSelector />
          {right}
          <button className="rounded-full p-2 text-fg-2 hover:bg-surface-2 lg:hidden" onClick={() => setOpen(true)} aria-label="Open menu">
            <Menu className="size-5" />
          </button>
        </div>
      </nav>

      {open && (
        <div className="pointer-events-auto fixed inset-0 z-[70] lg:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          <div className="absolute inset-0 bg-black/70 backdrop-blur-sm animate-fade" onClick={() => setOpen(false)} />
          <div className="glass absolute inset-x-3 top-3 rounded-3xl p-5 animate-fade-up">
            <div className="mb-4 flex items-center justify-between">
              <Logo />
              <button className="rounded-full p-2 text-fg-2 hover:bg-surface-2" onClick={() => setOpen(false)} aria-label="Close menu"><X className="size-5" /></button>
            </div>
            <div className="grid gap-1">
              {PRIMARY_NAV.map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end} onClick={() => setOpen(false)}
                  className={({ isActive }) => cn('rounded-2xl px-4 py-3 font-display text-2xl transition-colors',
                    isActive && variant === 'app' ? 'bg-surface-3/70 text-fg' : 'text-fg-2 hover:bg-surface-2')}>
                  {item.label}
                </NavLink>
              ))}
            </div>
            {extra && <div className="mt-4 border-t border-border pt-4" onClick={() => setOpen(false)}>{extra}</div>}
          </div>
        </div>
      )}
    </header>
  );
}
