import { useEffect, useRef, useState, type RefObject } from 'react';

/** Progress (0..1) of the page scrolling through a tall element; 0 when its top reaches the viewport top. */
export function useScrollProgress(ref: RefObject<HTMLElement | null>): number {
  const [progress, setProgress] = useState(0);
  useEffect(() => {
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    let frame = 0;
    let target = 0;
    let current = 0;
    let last = 0;
    const measure = () => {
      const el = ref.current;
      if (!el) return target;
      const rect = el.getBoundingClientRect();
      const total = rect.height - window.innerHeight;
      return total > 0 ? Math.min(1, Math.max(0, -rect.top / total)) : 0;
    };
    // eases the displayed progress towards the scroll position with a time-based critically damped step,
    // so wheel ticks and trackpad flicks become one continuous glide at any frame rate
    const tick = (now: number) => {
      const dt = last ? Math.min(0.05, (now - last) / 1000) : 1 / 60;
      last = now;
      const k = reduced ? 1 : 1 - Math.exp(-dt * 9);
      current += (target - current) * k;
      if (Math.abs(target - current) < 0.0004) current = target;
      setProgress(current);
      frame = current === target ? 0 : requestAnimationFrame(tick);
      if (!frame) last = 0;
    };
    const onScroll = () => {
      target = measure();
      if (!frame) frame = requestAnimationFrame(tick);
    };
    target = current = measure();
    setProgress(current);
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    return () => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
      if (frame) cancelAnimationFrame(frame);
    };
  }, [ref]);
  return progress;
}

/** Adds .is-visible to every [data-reveal] element inside the container once it scrolls into view. */
export function useReveal<T extends HTMLElement>() {
  const ref = useRef<T | null>(null);
  useEffect(() => {
    const root = ref.current;
    if (!root) return;
    const items = Array.from(root.querySelectorAll<HTMLElement>('[data-reveal]'));
    if (!('IntersectionObserver' in window)) {
      items.forEach((i) => i.classList.add('is-visible'));
      return;
    }
    const io = new IntersectionObserver((entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) {
          e.target.classList.add('is-visible');
          io.unobserve(e.target);
        }
      });
    }, { rootMargin: '0px 0px -10% 0px', threshold: 0.12 });
    items.forEach((i) => io.observe(i));
    return () => io.disconnect();
  }, []);
  return ref;
}

/** 0..1 position of `p` inside [from, to], clamped. */
export const segment = (p: number, from: number, to: number) => Math.min(1, Math.max(0, (p - from) / (to - from)));

/** Opacity for a story scene that fades in over [a,b] and out over [c,d]. */
export const window4 = (p: number, a: number, b: number, c: number, d: number) =>
  p < a || p > d ? 0 : p < b ? (p - a) / (b - a) : p <= c ? 1 : 1 - (p - c) / (d - c);
