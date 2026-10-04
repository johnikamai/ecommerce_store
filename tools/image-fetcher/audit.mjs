// Sanity-checks the downloaded product photos without needing to look at them.
//
// The first version of this audit flagged 180/606, but almost all of those were
// false alarms: it treated "few distinct colours" as suspicious, and a product
// shot on a plain white background legitimately has few colours while still
// being a perfectly good photo. That heuristic is gone.
//
// What actually goes wrong, and what is checked here:
//
//   dark     mean luminance near zero. Openverse surfaces underexposed uploads
//            and dark line art, which render as black squares in the storefront.
//   flat     low channel standard deviation, i.e. almost no tonal variation.
//   contrast very low entropy - a near-solid fill.
//   duplicate identical bytes. When a query returns fewer usable photos than a
//            family has products, the fetch loop cycles the same hit, so several
//            products end up sharing one image.
//   tiny     file size too small to be a 600x600 photograph.

import { readdir, stat, readFile, rm } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const HERE = dirname(fileURLToPath(import.meta.url));
const DIR = join(HERE, '..', '..', 'frontend', 'public', 'products');

const LIMIT_MEAN = 32; // below this the image is effectively black
const LIMIT_STDEV = 20; // almost no tonal variation
const LIMIT_ENTROPY = 2.8; // near-solid fill
const LIMIT_BYTES = 4000; // cannot be a real 600x600 photo

const files = (await readdir(DIR)).filter((f) => f.endsWith('.webp')).sort();
console.log(`auditing ${files.length} images\n`);

const results = [];
const byHash = new Map();

for (const file of files) {
  const path = join(DIR, file);
  const { size } = await stat(path);
  const { channels, entropy, isOpaque } = await sharp(path).stats();

  const stdev = channels.reduce((a, c) => a + c.stdev, 0) / channels.length;
  const mean = channels.reduce((a, c) => a + c.mean, 0) / channels.length;

  const hash = createHash('md5')
    .update(await readFile(path))
    .digest('hex');

  const reasons = [];
  if (mean < LIMIT_MEAN) reasons.push(`dark mean=${mean.toFixed(0)}`);
  if (stdev < LIMIT_STDEV) reasons.push(`flat stdev=${stdev.toFixed(1)}`);
  if (entropy < LIMIT_ENTROPY) reasons.push(`flat entropy=${entropy.toFixed(2)}`);
  if (size < LIMIT_BYTES) reasons.push(`tiny ${(size / 1024).toFixed(1)}KB`);
  if (!isOpaque) reasons.push('transparent');

  if (!byHash.has(hash)) byHash.set(hash, []);
  byHash.get(hash).push(file);

  results.push({ file, reasons, mean, stdev, entropy, size, hash });
}

const problems = results.filter((r) => r.reasons.length);

// Byte-identical images are reported per family rather than per file.
const dupes = [...byHash.entries()].filter(([, v]) => v.length > 1);
const dupeFiles = new Set(dupes.flatMap(([, v]) => v.slice(1)));

console.log(`statistically suspicious : ${problems.length}`);
for (const p of problems) {
  console.log(
    `  ${p.file.padEnd(36)} ${p.reasons.join(', ')}  [mean=${p.mean.toFixed(0)} stdev=${p.stdev.toFixed(1)} ent=${p.entropy.toFixed(2)}]`
  );
}

console.log(`\nduplicate groups         : ${dupes.length} (${dupeFiles.size} extra files)`);
for (const [hash, group] of dupes) {
  console.log(`  ${hash.slice(0, 8)}  ${group.join(', ')}`);
}

console.log(
  `\nclean                    : ${results.length - problems.length - dupeFiles.size}/${results.length}`
);

// --delete removes the unusable files so a rerun refetches just those products.
// Duplicates keep their first occurrence, which preserves one valid photo while
// forcing the rest of the family to look for a different candidate.
const DELETE = process.argv.includes('--delete');
if (DELETE) {
  const bad = new Set([
    ...problems.map((p) => p.file),
    ...dupeFiles,
  ]);
  for (const file of bad) {
    await rm(join(DIR, file));
  }
  console.log(`\ndeleted ${bad.size} files - rerun fetch-images.mjs to replace them`);
}
