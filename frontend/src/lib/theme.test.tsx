import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { THEMES, useTheme } from './theme';

describe('useTheme', () => {
  it('offers six atmospheres including Marine and Ombré Blue', () => {
    expect(THEMES.map((t) => t.id)).toEqual(['midnight', 'crimson', 'graphite', 'marine', 'azure', 'light']);
    expect(THEMES.find((t) => t.id === 'azure')?.name).toBe('Ombré Blue');
    expect(THEMES.find((t) => t.id === 'marine')?.name).toBe('Marine');
  });

  it('paints the document, persists the choice and re-renders subscribers', () => {
    const { result } = renderHook(() => useTheme());
    expect(result.current.theme).toBe('midnight');

    act(() => result.current.setTheme('marine'));
    expect(result.current.theme).toBe('marine');
    expect(result.current.isDark).toBe(true);
    expect(document.documentElement.dataset.theme).toBe('marine');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem('queryai.theme')).toBe('marine');
  });

  it('drops the dark class only for the light theme', () => {
    const { result } = renderHook(() => useTheme());
    act(() => result.current.setTheme('light'));
    expect(result.current.isDark).toBe(false);
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    act(() => result.current.setTheme('azure'));
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('toggle cycles through the themes in order and wraps around', () => {
    const { result } = renderHook(() => useTheme());
    act(() => result.current.toggle());
    expect(result.current.theme).toBe('crimson');
    act(() => result.current.setTheme('light'));
    act(() => result.current.toggle());
    expect(result.current.theme).toBe('midnight');
  });

  it('treats unknown or legacy values as Midnight', () => {
    document.documentElement.dataset.theme = 'neon';
    const { result } = renderHook(() => useTheme());
    expect(result.current.theme).toBe('midnight');
  });
});
