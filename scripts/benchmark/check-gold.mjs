#!/usr/bin/env node
// CI guard: every gold query in benchmark/questions.json must run on the demo database and return rows.
// Usage: PGHOST=localhost PGUSER=postgres PGPASSWORD=postgres node scripts/benchmark/check-gold.mjs commerce
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
const db = process.argv[2] ?? 'commerce';
const { questions } = JSON.parse(readFileSync(new URL('../../benchmark/questions.json', import.meta.url), 'utf8'));
let failed = 0;
for (const q of questions) {
  try {
    const out = execFileSync('psql', ['-d', db, '-tA', '-v', 'ON_ERROR_STOP=1', '-c', q.sql], { encoding: 'utf8' }).trim();
    const rows = out ? out.split('\n').length : 0;
    if (!rows) { failed++; console.log(`✗ ${q.id} returned no rows`); } else console.log(`✓ ${q.id} ${rows} row(s)`);
  } catch (e) {
    failed++;
    console.log(`✗ ${q.id} ${String(e.stderr ?? e.message).trim()}`);
  }
}
console.log(`${questions.length - failed}/${questions.length} gold queries OK`);
process.exit(failed ? 1 : 0);
