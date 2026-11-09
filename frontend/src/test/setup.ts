import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// ---- jsdom gaps used by Radix, the theme system and the landing page ----
if (!window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (query: string) => ({
      matches: false, media: query, onchange: null,
      addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {}, dispatchEvent: () => false,
    }),
  });
}

class NoopObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords() { return []; }
}
if (!('ResizeObserver' in globalThis)) Object.defineProperty(globalThis, 'ResizeObserver', { configurable: true, value: NoopObserver });
if (!('IntersectionObserver' in globalThis)) Object.defineProperty(globalThis, 'IntersectionObserver', { configurable: true, value: NoopObserver });

const proto = Element.prototype as unknown as Record<string, unknown>;
proto.scrollIntoView ??= function scrollIntoView() {};
proto.hasPointerCapture ??= () => false;
proto.setPointerCapture ??= () => {};
proto.releasePointerCapture ??= () => {};
window.scrollTo = (() => {}) as typeof window.scrollTo;

Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: vi.fn(async () => {}) } });

afterEach(() => {
  cleanup();
  try {
    localStorage.clear();
    sessionStorage.clear();
  } catch {
    /* ignore */
  }
  document.documentElement.dataset.theme = 'midnight';
  document.documentElement.classList.add('dark');
});
