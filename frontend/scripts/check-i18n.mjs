/**
 * Checks that every t('key') referenced in the source exists in each
 * dictionary, and that every dictionary key is actually used somewhere.
 *
 * Run with: node scripts/check-i18n.mjs
 *
 * A missing key renders as the raw key name at runtime, which is easy to miss
 * in a screenshot but obvious here, so this fails loudly instead.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DICTIONARIES, LANGUAGES } from '../src/i18n/dictionaries.js';

// fileURLToPath, not import.meta.url directly: the workspace path contains a
// space, which stays percent-encoded in a raw URL and breaks readdir.
const SRC = fileURLToPath(new URL('../src', import.meta.url));

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (['.js', '.jsx'].includes(extname(full))) out.push(full);
  }
  return out;
}

const files = walk(SRC);
const used = new Set();

// Prefixes that are referenced through a lookup map rather than a literal
// t('...') call, e.g. OrderTracking's HEADLINES map and ProductDetail's
// AVAILABILITY map both hold bare key strings resolved later with t(map[key]).
const INDIRECT_PREFIXES = ['tracking.', 'status.', 'recs.reason.', 'product.'];

for (const file of files) {
  const text = readFileSync(file, 'utf8');

  // Direct calls: t('key') / t("key"). Template literals such as
  // t(`status.${step}`) are resolved at runtime instead.
  for (const m of text.matchAll(/\bt\(\s*['"]([a-zA-Z][\w.]*)['"]/g)) {
    used.add(m[1]);
  }

  // Indirect keys held as plain string values in a map.
  for (const m of text.matchAll(/['"]([a-zA-Z][\w.]*)['"]/g)) {
    if (INDIRECT_PREFIXES.some((p) => m[1].startsWith(p))) used.add(m[1]);
  }
}

// Template-literal keys built from a status name: status.<ORDER_STATUS>.
const STATUSES = ['PLACED', 'PACKED', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED'];
for (const s of STATUSES) used.add(`status.${s}`);

// Recommendation reason codes come from the backend, not from a literal here.
for (const code of ['recs.reason.because', 'recs.reason.topRated', 'recs.reason.boughtTogether']) {
  used.add(code);
}

const problems = [];

for (const { code } of LANGUAGES) {
  const dict = DICTIONARIES[code];
  for (const key of used) {
    if (!(key in dict)) problems.push(`[${code}] missing: ${key}`);
  }
}

const enKeys = Object.keys(DICTIONARIES.en);
for (const key of enKeys) {
  if (!used.has(key)) problems.push(`[en] defined but never used: ${key}`);
}

console.log(`checked ${used.size} keys across ${LANGUAGES.length} locales in ${files.length} files`);
if (problems.length) {
  for (const p of problems) console.log(p);
  console.log(`\n${problems.length} problem(s)`);
  process.exit(1);
}
console.log('all keys resolve');
