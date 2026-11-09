import { useCallback, useSyncExternalStore } from 'react';

export type Theme = 'midnight' | 'crimson' | 'graphite' | 'marine' | 'azure' | 'light';

export const THEMES: { id: Theme; name: string; note: string; swatch: [string, string, string] }[] = [
  { id: 'midnight', name: 'Midnight', note: 'Deep black & charcoal', swatch: ['#050506', '#17161a', '#9a2a3c'] },
  { id: 'crimson', name: 'Crimson', note: 'Cinematic red light', swatch: ['#0a0506', '#3a0d16', '#d83b53'] },
  { id: 'graphite', name: 'Graphite', note: 'Quiet grayscale', swatch: ['#0c0c0d', '#2a2a2e', '#c9c9cf'] },
  { id: 'marine', name: 'Marine', note: 'Deep sea & bioluminescence', swatch: ['#02080c', '#0b3540', '#2ec4c0'] },
  { id: 'azure', name: 'Ombré Blue', note: 'Midnight navy to azure', swatch: ['#03050e', '#1b2a6b', '#5b8cff'] },
  { id: 'light', name: 'Light', note: 'Ivory & soft gray', swatch: ['#faf7f2', '#e9e3da', '#8c1c2c'] },
];

const KEY = 'queryai.theme';
const listeners = new Set<() => void>();

function normalize(value: string | null): Theme {
  if (value === 'dark') return 'midnight';
  return THEMES.some((t) => t.id === value) ? (value as Theme) : 'midnight';
}

function current(): Theme {
  if (typeof document === 'undefined') return 'midnight';
  return normalize(document.documentElement.dataset.theme ?? null);
}

function paint(theme: Theme) {
  const root = document.documentElement;
  root.dataset.theme = theme;
  root.classList.toggle('dark', theme !== 'light');
}

function apply(theme: Theme) {
  const run = () => {
    paint(theme);
    listeners.forEach((l) => l());
  };
  const reduced = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  // a soft cross-fade between themes where the View Transitions API exists
  const doc = document as Document & { startViewTransition?: (cb: () => void) => unknown };
  if (!reduced && typeof doc.startViewTransition === 'function') doc.startViewTransition(run);
  else run();
  try {
    localStorage.setItem(KEY, theme);
  } catch {
    /* storage unavailable: theme lasts for this page view */
  }
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** Shared theme state: every component (including the Monaco editor and the 3D scene) re-renders on change. */
export function useTheme() {
  const theme = useSyncExternalStore(subscribe, current, () => 'midnight' as Theme);
  const setTheme = useCallback((t: Theme) => apply(t), []);
  const toggle = useCallback(() => {
    const i = THEMES.findIndex((t) => t.id === current());
    apply(THEMES[(i + 1) % THEMES.length].id);
  }, []);
  return { theme, isDark: theme !== 'light', setTheme, toggle };
}
