#!/usr/bin/env node
/**
 * QueryAI execution-accuracy benchmark.
 *
 * For every question in benchmark/questions.json it asks the running QueryAI server to generate SQL
 * (POST /api/v1/analytics/generate — the full pipeline: intent, semantic schema retrieval, glossary, generation,
 * validation with self-repair), executes the generated SQL and the gold SQL through QueryAI's read-only executor,
 * and compares the two result sets. A question counts as correct when both return the same number of rows and every
 * gold column appears (as a multiset of values, numbers rounded to 2 decimals) among the generated columns — the same
 * rule as the in-app evaluation harness (backend ResultComparator).
 *
 * Usage:
 *   node scripts/benchmark/run-benchmark.mjs --url http://localhost:13000 \
 *        [--email analyst@queryai.app --password QueryAI@2026] [--source "E-commerce (PostgreSQL)"] \
 *        [--ids e01,m02] [--limit 10] [--out benchmark/results] [--min-accuracy 0.6]
 *
 * Needs Node 18+ (global fetch). No dependencies.
 */
import { readFileSync, writeFileSync, mkdirSync, appendFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, all) => {
  if (a.startsWith('--')) acc.push([a.slice(2), all[i + 1] && !all[i + 1].startsWith('--') ? all[i + 1] : 'true']);
  return acc;
}, []));

const BASE = (args.url ?? process.env.QUERYAI_URL ?? 'http://localhost:13000').replace(/\/$/, '');
const EMAIL = args.email ?? process.env.QUERYAI_EMAIL ?? 'analyst@queryai.app';
const PASSWORD = args.password ?? process.env.QUERYAI_PASSWORD ?? 'QueryAI@2026';
const suite = JSON.parse(readFileSync(join(root, 'benchmark', 'questions.json'), 'utf8'));
const SOURCE = args.source ?? suite.dataSource;
const OUT = resolve(root, args.out ?? 'benchmark/results');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let token = null;
async function api(method, path, body) {
  for (let attempt = 0; attempt < 6; attempt++) {
    const res = await fetch(`${BASE}/api/v1${path}`, {
      method,
      headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (res.status === 429) {
      const wait = Number(res.headers.get('Retry-After') ?? 15);
      process.stdout.write(`  (rate limited, waiting ${wait}s)\n`);
      await sleep(wait * 1000);
      continue;
    }
    const text = await res.text();
    let json;
    try { json = text ? JSON.parse(text) : null; } catch { json = { message: text }; }
    if (!res.ok) {
      const err = new Error(`${res.status} ${json?.message ?? text}`);
      err.status = res.status;
      throw err;
    }
    return json;
  }
  throw new Error('Gave up after repeated rate limiting');
}

// ---- result comparison (mirrors backend ResultComparator) ----
function normalize(v) {
  if (v === null || v === undefined) return '∅';
  const s = String(v).trim();
  if (s !== '' && !Number.isNaN(Number(s)) && /^[-+]?(\d+\.?\d*|\.\d+)(e[-+]?\d+)?$/i.test(s)) {
    const n = Math.round(Number(s) * 100) / 100;
    return String(Object.is(n, -0) ? 0 : n);
  }
  // timestamps at midnight compare equal to dates: 2025-07-01T00:00 == 2025-07-01
  const ts = s.match(/^(\d{4}-\d{2}-\d{2})[T ]00:00(:00(\.0+)?)?$/);
  if (ts) return ts[1];
  return s.toLowerCase();
}
function multiset(rows, col) {
  const m = new Map();
  for (const r of rows) { const k = normalize(r[col]); m.set(k, (m.get(k) ?? 0) + 1); }
  return m;
}
const sameMultiset = (a, b) => a.size === b.size && [...a].every(([k, v]) => b.get(k) === v);
export function compare(expected, actual) {
  if (expected.length !== actual.length) return { match: false, reason: `row count ${actual.length}, expected ${expected.length}` };
  if (expected.length === 0) return { match: true, reason: 'both empty' };
  const actualCols = actual[0].length;
  const pool = Array.from({ length: actualCols }, (_, c) => multiset(actual, c));
  const used = new Array(actualCols).fill(false);
  for (let c = 0; c < expected[0].length; c++) {
    const exp = multiset(expected, c);
    const hit = pool.findIndex((p, i) => !used[i] && sameMultiset(p, exp));
    if (hit < 0) return { match: false, reason: `expected column #${c + 1} not found in generated result` };
    used[hit] = true;
  }
  return { match: true, reason: `all ${expected[0].length} columns matched` };
}

async function main() {
  console.log(`QueryAI benchmark → ${BASE}`);
  const login = await api('POST', '/auth/login', { email: EMAIL, password: PASSWORD });
  token = login.token;
  const info = await api('GET', '/system/info');
  if (!info.llmConfigured) throw new Error('The server has no AI model configured (see README → AI setup).');
  const workspaces = await api('GET', '/workspaces');
  let ds = null;
  for (const w of workspaces) {
    const list = await api('GET', `/datasources?workspaceId=${w.id}`);
    ds = list.find((d) => d.name === SOURCE) ?? ds;
    if (ds) break;
  }
  if (!ds) throw new Error(`Data source "${SOURCE}" not found for ${EMAIL}`);

  let questions = suite.questions;
  if (args.ids) { const ids = new Set(args.ids.split(',')); questions = questions.filter((q) => ids.has(q.id)); }
  if (args.limit) questions = questions.slice(0, Number(args.limit));
  console.log(`Model: ${info.llmProvider} · retrieval: ${info.semanticSearch ? `${info.embeddingModel}${info.pgvector ? ' + pgvector' : ''}` : 'lexical'}`);
  console.log(`Data source: ${ds.name} · ${questions.length} questions\n`);

  const results = [];
  for (const q of questions) {
    const started = Date.now();
    const r = { id: q.id, difficulty: q.difficulty, question: q.question, correct: false, executed: false };
    try {
      const gold = await api('POST', '/sql/execute', { dataSourceId: ds.id, sql: q.sql, maxRows: 5000 });
      if (!gold.result) throw new Error(`gold SQL failed: ${gold.error}`);
      let gen;
      for (let attempt = 0; ; attempt++) {
        gen = await api('POST', '/analytics/generate', { dataSourceId: ds.id, question: q.question });
        const transient = gen.status === 'FAILED' && /429|rate.?limit|timeout|temporar/i.test(gen.error ?? '');
        if (!transient || attempt >= 3) break;
        process.stdout.write('  (provider busy, retrying in 20s)\n');
        await sleep(20_000);
      }
      r.generationMs = Date.now() - started;
      r.status = gen.status;
      r.sql = gen.sql;
      r.repairs = gen.repairAttempts ?? 0;
      r.model = gen.model;
      r.retrieval = gen.stages?.find((s) => s.name === 'Schema retrieval')?.detail;
      if (gen.sql && gen.status !== 'BLOCKED' && gen.status !== 'FAILED' && gen.status !== 'CLARIFICATION_NEEDED') {
        const pred = await api('POST', '/sql/execute', { dataSourceId: ds.id, sql: gen.sql, maxRows: 5000 });
        if (pred.result) {
          r.executed = true;
          const cmp = compare(gold.result.rows, pred.result.rows);
          r.correct = cmp.match;
          r.reason = cmp.reason;
        } else {
          r.reason = `generated SQL failed: ${pred.error}`;
        }
      } else {
        r.reason = gen.status === 'CLARIFICATION_NEEDED' ? `asked for clarification: ${gen.clarificationQuestion ?? ''}` : (gen.error ?? gen.status);
      }
    } catch (e) {
      r.reason = `error: ${e.message}`;
    }
    r.totalMs = Date.now() - started;
    results.push(r);
    console.log(`${r.correct ? '✓' : '✗'} ${q.id.padEnd(4)} ${(r.totalMs / 1000).toFixed(1).padStart(5)}s  ${q.question.slice(0, 70)}${r.correct ? '' : `\n         ↳ ${r.reason}`}`);
  }

  const pct = (n, d) => (d ? (100 * n) / d : 0);
  const by = (k) => results.filter((r) => r.difficulty === k);
  const summary = {
    suite: suite.name,
    date: new Date().toISOString(),
    server: BASE,
    model: info.llmProvider,
    retrieval: info.semanticSearch ? `${info.embeddingModel}${info.pgvector ? ' + pgvector' : ''}` : 'lexical',
    questions: results.length,
    executionAccuracy: pct(results.filter((r) => r.correct).length, results.length),
    validSqlRate: pct(results.filter((r) => r.executed).length, results.length),
    selfRepaired: results.filter((r) => r.repairs > 0).length,
    medianGenerationMs: (() => { const t = results.map((r) => r.generationMs ?? r.totalMs).sort((a, b) => a - b); return t[Math.floor(t.length / 2)] ?? 0; })(),
    byDifficulty: Object.fromEntries(['easy', 'medium', 'hard'].map((k) => [k, { questions: by(k).length, accuracy: pct(by(k).filter((r) => r.correct).length, by(k).length) }])),
  };

  const md = [
    `### QueryAI benchmark — ${summary.executionAccuracy.toFixed(1)}% execution accuracy`,
    '',
    `Model \`${summary.model}\` · retrieval \`${summary.retrieval}\` · ${summary.questions} questions · ${summary.date.slice(0, 10)}`,
    '',
    '| Metric | Value |',
    '|---|---|',
    `| Execution accuracy | **${summary.executionAccuracy.toFixed(1)}%** |`,
    ...['easy', 'medium', 'hard'].filter((k) => summary.byDifficulty[k].questions).map((k) => `| — ${k} (${summary.byDifficulty[k].questions}) | ${summary.byDifficulty[k].accuracy.toFixed(1)}% |`),
    `| Generated SQL that executed | ${summary.validSqlRate.toFixed(1)}% |`,
    `| Answers fixed by self-repair | ${summary.selfRepaired} |`,
    `| Median generation time | ${(summary.medianGenerationMs / 1000).toFixed(1)} s |`,
    '',
    '<details><summary>Per-question results</summary>',
    '',
    '| ID | Result | Question | Note |',
    '|---|---|---|---|',
    ...results.map((r) => `| ${r.id} | ${r.correct ? '✅' : '❌'} | ${r.question.replace(/\|/g, '\\|')} | ${r.correct ? '' : String(r.reason ?? '').replace(/\|/g, '\\|').slice(0, 120)} |`),
    '',
    '</details>',
  ].join('\n');

  mkdirSync(OUT, { recursive: true });
  writeFileSync(join(OUT, 'latest.json'), JSON.stringify({ summary, results }, null, 2));
  writeFileSync(join(OUT, 'latest.md'), md + '\n');
  const color = summary.executionAccuracy >= 80 ? 'brightgreen' : summary.executionAccuracy >= 60 ? 'yellow' : 'orange';
  writeFileSync(join(OUT, 'badge.json'), JSON.stringify({ schemaVersion: 1, label: 'text-to-SQL accuracy', message: `${summary.executionAccuracy.toFixed(1)}%`, color }));
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, md + '\n');

  console.log(`\nExecution accuracy: ${summary.executionAccuracy.toFixed(1)}% (${results.filter((r) => r.correct).length}/${results.length})`);
  console.log(`Results written to ${OUT}`);
  if (args['min-accuracy'] && summary.executionAccuracy / 100 < Number(args['min-accuracy'])) {
    console.error(`Accuracy below the required ${(Number(args['min-accuracy']) * 100).toFixed(0)}%`);
    process.exit(1);
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch((e) => { console.error(e.message); process.exit(1); });
}
