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
import { join, extname, sep } from 'node:path';
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

// The dictionary file itself must not be scanned for usage. It contains every
// key as a quoted literal, so counting it would mark all of them as used and
// silently disable the "defined but never used" check below. Compared on a
// normalised path so a case-insensitive filesystem cannot hide the match.
const normalizePath = (file) => file.split(sep).join('/').toLowerCase();
const DICTIONARY_FILE_SUFFIX = '/i18n/dictionaries.js';

const files = walk(SRC);
const sourceFiles = files.filter((f) => !normalizePath(f).endsWith(DICTIONARY_FILE_SUFFIX));

// Keys that are not written as a literal t('...') call fall into two buckets:
//   1. Lookup maps resolved later, e.g. OrderTracking's HEADLINES map,
//      ProductDetail's AVAILABILITY map, ProductCard's SUSTAIN_LABEL_KEYS map
//      and the catalogue's SORT_OPTIONS labelKey list.
//   2. Keys held in state and translated at render (t(error)), so the text
//      follows a language switch instead of freezing at request time.
// Both still appear in the source as plain quoted strings, so rather than
// maintaining a list of namespaces here we intersect every dotted string
// literal with the English dictionary. A namespace that is added later needs
// no change to this file, and a literal can only count as "used" if the key
// genuinely exists.
const enKeys = Object.keys(DICTIONARIES.en);

const used = new Set();

for (const file of sourceFiles) {
  const text = readFileSync(file, 'utf8');

  // Every quoted dotted string, e.g. 'status.SHIPPED' or 'cart.checkoutFailed'.
  // Counted as used only when the key genuinely exists in English, so a new
  // namespace needs no change to this file and unrelated dotted strings (API
  // paths and the like) are ignored.
  for (const m of text.matchAll(/['"]([a-zA-Z][\w]*(?:\.[\w]+)+)['"]/g)) {
    if (enKeys.includes(m[1])) used.add(m[1]);
  }

  // Direct calls: t('key') / t("key"). Added unconditionally so a typo is still
  // reported below as missing. Template literals such as t(`status.${step}`)
  // are resolved at runtime instead and are seeded after the loop.
  for (const m of text.matchAll(/\bt\(\s*['"]([a-zA-Z][\w.]*)['"]/g)) {
    used.add(m[1]);
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

for (const key of enKeys) {
  if (!used.has(key)) problems.push(`[en] defined but never used: ${key}`);
}

console.log(`checked ${used.size} keys across ${LANGUAGES.length} locales in ${sourceFiles.length} files`);
if (problems.length) {
  for (const p of problems) console.log(p);
  console.log(`\n${problems.length} problem(s)`);
  process.exit(1);
}
console.log('all keys resolve');
