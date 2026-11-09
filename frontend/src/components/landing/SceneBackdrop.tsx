import { useEffect, useRef, useState } from 'react';
import { useTheme } from '@/lib/theme';
import { cn } from '@/lib/utils';
import type { LibraryScene } from './libraryScene';

function pick(theme: string) {
  return (['crimson', 'graphite', 'marine', 'azure', 'light'] as const).find((t) => t === theme) ?? 'midnight';
}

/** The library scene as a still, living backdrop (e.g. behind the sign-in card), framed at a fixed moment. */
export function SceneBackdrop({ progress = 0.68, className }: { progress?: number; className?: string }) {
  const ref = useRef<HTMLCanvasElement | null>(null);
  const sceneRef = useRef<LibraryScene | null>(null);
  const [ready, setReady] = useState(false);
  const { theme } = useTheme();

  useEffect(() => {
    let disposed = false;
    let onResize: (() => void) | null = null;
    const canvas = ref.current;
    if (!canvas) return;
    import('./libraryScene').then(({ createLibraryScene, PALETTES }) => {
      if (disposed) return;
      try {
        const nav = navigator as Navigator & { deviceMemory?: number };
        const low = (nav.deviceMemory ?? 8) <= 4 || window.innerWidth < 760;
        const scene = createLibraryScene(canvas, PALETTES[pick(document.documentElement.dataset.theme ?? 'midnight')], low ? 'low' : 'high');
        sceneRef.current = scene;
        onResize = () => scene.resize(canvas.clientWidth, canvas.clientHeight);
        onResize();
        window.addEventListener('resize', onResize);
        scene.setProgress(progress);
        scene.start();
        setReady(true);
      } catch {
        /* no WebGL: the CSS gradient below stays */
      }
    }).catch(() => undefined);
    return () => {
      disposed = true;
      if (onResize) window.removeEventListener('resize', onResize);
      sceneRef.current?.dispose();
      sceneRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    sceneRef.current?.setProgress(progress);
  }, [progress]);

  useEffect(() => {
    if (!sceneRef.current) return;
    import('./libraryScene').then(({ PALETTES }) => sceneRef.current?.setPalette(PALETTES[pick(theme)]));
  }, [theme, ready]);

  return (
    <div className={cn('pointer-events-none fixed inset-0 -z-10', className)} aria-hidden="true">
      <div className="absolute inset-0" style={{ background: 'radial-gradient(70% 60% at 60% 60%, rgb(var(--glow) / 0.3), transparent 70%), linear-gradient(180deg, var(--bg-2), var(--bg))' }} />
      <canvas ref={ref} className={cn('absolute inset-0 h-full w-full transition-opacity duration-[1500ms]', ready ? 'opacity-100' : 'opacity-0')} />
      <div className="grain absolute inset-0" />
      <div className="absolute inset-0 bg-[radial-gradient(120%_90%_at_50%_45%,transparent_50%,rgb(0_0_0/0.6))]" />
    </div>
  );
}
