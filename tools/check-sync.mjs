// Checks whether the deployed site still matches this repository.
//
// Another tool has been given the Vercel URL and a copy of the project, and a
// deploy does not merge with git: whatever ran last is what is live. This script
// answers the only question that matters after someone else touches it - is what
// is deployed the code in this repo - and prints the action to take when it is
// not.
//
// Run it with no arguments for a verdict:
//
//   node tools/check-sync.mjs
//
// It is read-only apart from `git fetch`, so it is safe to run at any time.

import { execFileSync } from 'node:child_process';
import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = join(dirname(fileURLToPath(import.meta.url)), '..');
const SITE = process.env.SITE_URL || 'https://ecommerce-store-shopease.vercel.app';
const API = process.env.API_URL || 'https://ecommerce-backend-2gas.onrender.com/api';

// The last commit confirmed good in the browser and in production. Anything after
// it is a change to review rather than trust.
const BASELINE = 'good-2026-10-04';

const git = (...args) =>
  execFileSync('git', args, { cwd: REPO, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 }).trim();

const problems = [];
const notes = [];

const say = (s = '') => process.stdout.write(`${s}\n`);
const md5 = (buf) => createHash('md5').update(buf).digest('hex');

say('='.repeat(72));
say(`  deployed-vs-repo check   ${new Date().toISOString().slice(0, 19).replace('T', ' ')}`);
say('='.repeat(72));

/* ---------------------------------------------------------------- the repo */

say('\nREPOSITORY');
let head = '';
try {
  git('fetch', 'origin', '--quiet');
  head = git('rev-parse', '--short', 'HEAD');
  const origin = git('rev-parse', '--short', 'origin/main');
  say(`  HEAD          ${head}`);
  say(`  origin/main   ${origin}`);
  if (head !== origin) {
    problems.push(`Local and remote differ (HEAD ${head}, origin/main ${origin}).`);
    const ahead = git('log', '--oneline', 'origin/main..HEAD');
    if (ahead) say(`  unpushed:\n${ahead.split('\n').map((l) => `      ${l}`).join('\n')}`);
  }

  const dirty = git('status', '--porcelain');
  if (dirty) {
    problems.push('Uncommitted changes in the working tree.');
    say(`  uncommitted:\n${dirty.split('\n').map((l) => `      ${l}`).join('\n')}`);
  } else {
    say('  working tree clean');
  }

  // Anything committed since the baseline is a change made outside this check.
  let newer = '';
  try {
    newer = git('log', '--oneline', `${BASELINE}..HEAD`);
  } catch {
    notes.push(`Baseline tag ${BASELINE} not found, skipping change review.`);
  }
  if (newer) {
    const n = newer.split('\n').length;
    say(`\n  ${n} commit(s) after the ${BASELINE} baseline:`);
    say(newer.split('\n').map((l) => `      ${l}`).join('\n'));
    const files = git('diff', '--stat', `${BASELINE}..HEAD`);
    say(`\n  files they touch:\n${files.split('\n').map((l) => `      ${l}`).join('\n')}`);
    notes.push(`${n} commit(s) landed after the baseline - review them before deploying.`);
  } else {
    say(`\n  no commits after ${BASELINE}`);
  }
} catch (err) {
  problems.push(`Could not read git state: ${err.message}`);
}

/* -------------------------------------------------------------- the frontend */

say('\nFRONTEND (deployed)');
try {
  const html = await (await fetch(`${SITE}/admin/products`)).text();
  const asset = html.match(/\/assets\/index-[A-Za-z0-9_-]+\.js/)?.[0];
  if (!asset) throw new Error('no bundle referenced in index.html');

  const js = await (await fetch(`${SITE}${asset}`)).text();
  say(`  bundle        ${asset}`);
  say(`  size          ${Math.round(js.length / 1024)} KB`);

  // Presence checks rather than a hash: the minifier rewrites quotes and
  // whitespace, but it cannot drop a string literal or a call.
  const markers = [
    ['cart shipping + tax totals', 'cart.taxNote'],
    ['Hindi/Spanish i18n sweep', 'notStaff'],
    ['admin role from JWT', 'replace(/-/g'],
  ];
  for (const [label, needle] of markers) {
    const ok = js.includes(needle);
    say(`  ${ok ? 'present ' : 'MISSING '}  ${label}`);
    if (!ok) problems.push(`Deployed bundle is missing: ${label}.`);
  }

  // A redeploy from an older snapshot produces a different bundle; comparing the
  // hash with a local build catches that even when every marker survives.
  const distDir = join(REPO, 'frontend', 'dist', 'assets');
  if (existsSync(distDir)) {
    const local = readdirSync(distDir).find((f) => /^index-.*\.js$/.test(f));
    if (local) {
      const same = local === asset.replace('/assets/', '');
      say(`  local build   ${local}${same ? '  (identical to deployed)' : '  (differs - local dist may be stale)'}`);
    }
  } else {
    notes.push('No frontend/dist yet, so the bundle hash cannot be cross-checked.');
  }
} catch (err) {
  problems.push(`Could not read the deployed frontend: ${err.message}`);
}

/* ------------------------------------------------------------------ the photos */

say('\nPRODUCT PHOTOS (deployed vs repo)');
try {
  // A sample across the batches that were refreshed, plus one deliberately
  // untouched file. If a zip from before the refreshes was deployed, these fail.
  const sample = [
    'matte-lipstick-1.webp',
    'cricket-bat-1.webp',
    'watercolor-paint-set-2.webp',
    'denim-jacket-4.webp',
    'cotton-t-shirt-2.webp',
    'wireless-earbuds-1.webp',
  ];
  let bad = 0;
  for (const f of sample) {
    const localPath = join(REPO, 'frontend', 'public', 'products', f);
    if (!existsSync(localPath)) continue;
    const live = Buffer.from(await (await fetch(`${SITE}/products/${f}`)).arrayBuffer());
    const same = md5(live) === md5(readFileSync(localPath));
    if (!same) bad++;
    say(`  ${same ? 'match   ' : 'DIFFERS '}  ${f}`);
  }
  if (bad) problems.push(`${bad} sampled photo(s) differ from the repo - an older copy was deployed.`);
} catch (err) {
  problems.push(`Could not compare photos: ${err.message}`);
}

/* ----------------------------------------------------------------- the backend */

say('\nBACKEND (deployed)');
try {
  const res = await fetch(`${API}/products/search?q=shoes`);
  const body = await res.json();
  const corrected = body.correctedQuery ?? '(none)';
  const count = Array.isArray(body.results) ? body.results.length : 0;
  const ok = corrected.toLowerCase() === 'shoes';
  say(`  search "shoes" -> corrected "${corrected}", ${count} results  ${ok ? 'OK' : 'STALE'}`);
  if (!ok) {
    problems.push(
      'The API still returns the old typo correction. Render needs a manual deploy - a Vercel deploy does not publish backend changes.'
    );
  }
} catch (err) {
  problems.push(`Could not reach the API: ${err.message} (Render free tier may be asleep)`);
}

/* --------------------------------------------------------------------- verdict */

say(`\n${'='.repeat(72)}`);
if (problems.length === 0) {
  say('  VERDICT: deployed site matches this repository. Nothing to do.');
} else {
  say(`  VERDICT: ${problems.length} problem(s)\n`);
  problems.forEach((p, i) => say(`   ${i + 1}. ${p}`));
  say('\n  WHAT TO DO');
  say('   - If the deployed site is missing fixes this repo already has, the');
  say('     simplest fix is to redeploy the current main rather than debate it:');
  say('       git fetch origin && git checkout main && git pull');
  say('       (Vercel redeploys automatically; Render needs Manual Deploy.)');
  say('   - If the other tool pushed commits you did not expect, do not deploy');
  say('     them blind. Read the diff first:');
  say('       git log --oneline good-2026-10-04..HEAD');
  say('       git diff good-2026-10-04..HEAD');
  say('   - To put the last known-good build back, deploy this tag:');
  say(`       ${BASELINE}  (${git('log', '-1', '--format=%h %s', BASELINE)})`);
  const revCount = Number(git('rev-list', '--count', `${BASELINE}..HEAD`)) || 0;
  if (revCount > 0) {
    say('   - To undo those commits locally before anything is deployed');
    say('     (revert, not reset, so nothing is lost):');
    say(`       git revert ${BASELINE}..HEAD`);
  }
}
say(`${'='.repeat(72)}\n`);

if (notes.length) notes.forEach((n) => say(`  note: ${n}`));
process.exit(problems.length === 0 ? 0 : 1);