import { describe, expect, it } from 'vitest';
import { detectParameters, formatDuration, formatPercent } from './utils';

describe('detectParameters', () => {
  it('finds named parameters but ignores casts, strings and comments', () => {
    const sql = "SELECT :a, x::date, ':notparam' FROM t -- :nope\nWHERE d >= :start AND d < :end AND :a IS NOT NULL /* :hidden */";
    expect(detectParameters(sql)).toEqual(['a', 'start', 'end']);
  });
});

describe('formatters', () => {
  it('formats durations and percentages', () => {
    expect(formatDuration(42)).toBe('42 ms');
    expect(formatDuration(1500)).toBe('1.50 s');
    expect(formatDuration(undefined)).toBe('—');
    expect(formatPercent(0.9567)).toBe('95.7%');
  });
});
