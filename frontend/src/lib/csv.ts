import type { Cell } from './types';

/** RFC 4180 CSV with formula-injection protection for spreadsheet apps. */
export function toCsv(columns: string[], rows: Cell[][]): string {
  const escape = (value: Cell): string => {
    if (value === null || value === undefined) return '';
    let s = String(value);
    if (typeof value === 'string' && /^[=+\-@\t\r]/.test(s)) s = `'${s}`;
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  return [columns.map(escape).join(','), ...rows.map((r) => r.map(escape).join(','))].join('\r\n');
}

export function downloadCsv(filename: string, columns: string[], rows: Cell[][]) {
  const blob = new Blob(['﻿' + toCsv(columns, rows)], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename.endsWith('.csv') ? filename : `${filename}.csv`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
