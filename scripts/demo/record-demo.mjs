#!/usr/bin/env node
/**
 * Records a ~90 second product walkthrough of a running QueryAI as a video (uses your installed Google Chrome).
 *
 *   cd scripts/demo && npm install && node record-demo.mjs --url http://localhost:13000
 *
 * Output: demo/queryai-demo.webm (and .mp4 when ffmpeg is installed). Needs the demo account (QUERYAI_DEMO_ENABLED=true)
 * and, for the AI part, a configured model. Every number on screen comes from the real running app.
 */
import { chromium } from 'playwright-core';
import { execFileSync } from 'node:child_process';
import { mkdirSync, renameSync, readdirSync, existsSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const args = Object.fromEntries(process.argv.slice(2).map((a, i, all) => (a.startsWith('--') ? [a.slice(2), all[i + 1]] : null)).filter(Boolean));
const BASE = (args.url ?? 'http://localhost:13000').replace(/\/$/, '');
const EMAIL = args.email ?? 'analyst@queryai.app';
const PASSWORD = args.password ?? 'QueryAI@2026';
const OUT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', 'demo');
const SIZE = { width: 1600, height: 900 };
const pause = (ms) => new Promise((r) => setTimeout(r, ms));

mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch({ channel: args.channel ?? 'chrome', headless: args.headless !== 'false' ? true : false });
const context = await browser.newContext({ viewport: SIZE, deviceScaleFactor: 1, recordVideo: { dir: OUT, size: SIZE } });
const page = await context.newPage();

async function smoothScroll(totalPx, ms) {
  const steps = Math.round(ms / 16);
  for (let i = 0; i < steps; i++) {
    await page.mouse.wheel(0, totalPx / steps);
    await pause(16);
  }
}

try {
  console.log('1/7 landing — the midnight library');
  await page.goto(BASE + '/', { waitUntil: 'networkidle' });
  await pause(3500);
  await smoothScroll(SIZE.height * 4.9, 24000);
  await pause(1500);

  console.log('2/7 sign in');
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
  await pause(1200);
  await page.fill('#email', EMAIL);
  await page.fill('#password', PASSWORD);
  await pause(600);
  await page.click('button[type=submit]');
  await page.waitForURL('**/app**', { timeout: 30000 });
  await pause(2500);

  console.log('3/7 ask a question');
  await page.goto(BASE + '/app/studio', { waitUntil: 'networkidle' });
  await pause(2500);
  const question = page.getByRole('button', { name: /loved the most/i }).first();
  if (await question.isVisible().catch(() => false)) {
    await question.hover();
    await pause(800);
    await question.click();
    await page.locator('table').first().waitFor({ timeout: 120000 }).catch(() => console.log('   (no AI answer — is a model configured?)'));
    await pause(3500);
    const chartTab = page.getByRole('tab', { name: /chart/i }).first();
    if (await chartTab.isVisible().catch(() => false)) { await chartTab.click(); await pause(3500); }
    const summaryTab = page.getByRole('tab', { name: /summary/i }).first();
    if (await summaryTab.isVisible().catch(() => false)) { await summaryTab.click(); await pause(3000); }
  }

  console.log('4/7 explore — semantic search');
  await page.goto(BASE + '/app/schema', { waitUntil: 'networkidle' });
  await pause(2000);
  await page.getByRole('tab', { name: /semantic/i }).click();
  await pause(800);
  await page.getByLabel('Search by meaning').type('customer happiness', { delay: 70 });
  await page.keyboard.press('Enter');
  await pause(3500);

  console.log('5/7 privacy');
  await page.getByRole('tab', { name: /privacy/i }).click();
  await pause(3500);
  await page.getByRole('tab', { name: /er diagram/i }).click();
  await pause(4000);

  console.log('6/7 visualizations');
  await page.goto(BASE + '/app/dashboards', { waitUntil: 'networkidle' });
  await pause(1500);
  const firstDashboard = page.locator('a[href^="/app/dashboards/"]').first();
  if (await firstDashboard.isVisible().catch(() => false)) { await firstDashboard.click(); await pause(5000); }

  console.log('7/7 atmospheres');
  for (const theme of ['Crimson', 'Graphite', 'Light', 'Midnight']) {
    await page.getByRole('button', { name: /change theme/i }).first().click();
    await pause(500);
    await page.getByRole('menuitem', { name: new RegExp(theme) }).click();
    await pause(1800);
  }
} finally {
  const video = page.video();
  await context.close();
  await browser.close();
  if (video) {
    const raw = await video.path();
    const target = join(OUT, 'queryai-demo.webm');
    renameSync(raw, target);
    console.log(`Saved ${target}`);
    try {
      execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', target, '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '20', join(OUT, 'queryai-demo.mp4')]);
      console.log(`Saved ${join(OUT, 'queryai-demo.mp4')}`);
    } catch {
      console.log('(install ffmpeg to also get an .mp4: brew install ffmpeg)');
    }
  }
  for (const f of existsSync(OUT) ? readdirSync(OUT) : []) if (/^[0-9a-f]{32}\.webm$/.test(f)) console.log(`leftover ${f}`);
}
