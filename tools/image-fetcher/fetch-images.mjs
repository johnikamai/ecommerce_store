// Downloads one real product photo per catalog entry from Openverse and writes
// a productId -> image URL map the backend can apply in a single bulk call.
//
// Why one photo per entry rather than one per base product: the seeder creates
// 101 base products, and each is expanded into 6 entries (the bare name plus
// Classic / Lite / Pro / Ultra / Max), giving 606. Requesting 6 candidates per
// query means the 6 variants of a base product get 6 *different* photos of the
// same real object, so no two products anywhere in the store share an image.
//
// Openverse is used instead of Pixabay because its API is built for
// programmatic access, whereas Pixabay's terms ask you not to send automated
// requests or mass-download. Anonymous access allows 200 requests/day, and this
// script needs 101 searches.
//
// The script is restartable: files that already exist are reused, so an
// interrupted run continues instead of starting over.

import { existsSync, mkdirSync, writeFileSync, readFileSync, readdirSync, renameSync, unlinkSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';
import { QUERIES, baseName } from './queries.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = join(HERE, '..', '..');
const OUT_DIR = join(REPO, 'frontend', 'public', 'products');
const API = process.env.API_BASE_URL || 'https://ecommerce-backend-2gas.onrender.com/api';

// Storefront thumbnails only ever render at a few hundred CSS pixels, so a
// 1024px source is more than enough. Everything is cropped square and
// re-encoded to keep the deploy small.
const SIZE = 600;
const QUALITY = 68;
const PER_BASE = 6;

// Only licences that permit commercial use AND allow derivative works. "by-nd"
// is excluded on purpose: cropping and resizing is a derivative, so an nd image
// would be unusable here even though it is commercially licensed.
const LICENSES = 'cc0,pdm,by,by-sa';

const REBUILD_CREDITS = process.argv.includes('--rebuild-credits');

// Re-download a family even though its files are already on disk. Used to redo a
// family whose photos were rejected without deleting anything first, so a failed
// fetch cannot leave a product with no image at all.
const FORCE = process.argv.includes('--force');

// Restrict the run to a single base family, e.g. --family=cotton-t-shirt. The
// remaining families are neither searched nor rewritten, which keeps a fix to one
// family from spending the whole 200/day budget or disturbing the rest.
const familyArg = process.argv.find((a) => a.startsWith('--family='));
const ONLY_FAMILY = familyArg ? familyArg.slice('--family='.length).trim().toLowerCase() : null;

// Persisted alongside credits so a rerun can tell which photos are already in
// use. Two problems this solves:
//   - a query returning fewer usable hits than a family has products used to
//     cycle the same hit, so six products shared one photo;
//   - different queries can surface the same photo, which put a water bottle on
//     the dumbbells and the yoga mat.
// Anything already claimed is skipped, and a family that runs out of unique
// candidates leaves the remainder unmapped so the storefront draws its generated
// SVG instead of showing a duplicate or the wrong object.
const STATE_FILE = join(HERE, 'state.json');

// Module scope, because search() filters candidates against it before main()
// has had a chance to declare anything local.
const usedUrls = new Set();


const slugify = (s) =>
  s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

/**
 * Reads attribution back out of CREDITS.md so the tracked file is the source of
 * truth for a rerun. The table row is:
 *
 *   | `file.webp` | Product | query | licence | Creator | [link](page) |
 */
function readCreditsFromMarkdown(file) {
  const out = new Map();
  if (!existsSync(file)) return out;
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    if (!line.startsWith('| `')) continue;
    const cells = line.split('|').slice(1, -1).map((c) => c.trim());
    if (cells.length < 6) continue;
    const name = cells[0].replace(/^`|`$/g, '');
    if (!name.endsWith('.webp')) continue;
    const link = /\[link\]\((.+)\)/.exec(cells[5]);
    out.set(name, {
      file: name,
      product: cells[1],
      query: cells[2],
      license: cells[3],
      creator: cells[4],
      page: link ? link[1] : cells[5],
    });
  }
  return out;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * Openverse allows a 20/minute burst on top of a 200/day budget, so searches
 * are paced against the live burst header rather than a fixed sleep.
 */
let burstLowAt = 0;
async function throttle(res) {
  const remaining = Number(res.headers.get('x-ratelimit-available-anon_burst'));
  if (Number.isFinite(remaining) && remaining < 4) {
    const waitMs = Number(res.headers.get('x-ratelimit-reset-anon_burst')) * 1000;
    const pause = Number.isFinite(waitMs) && waitMs > 0 ? waitMs + 500 : 4000;
    process.stdout.write(`\n  burst low, pausing ${Math.round(pause / 1000)}s   `);
    await sleep(pause);
  }
  const since = Date.now() - burstLowAt;
  if (since < 3200) await sleep(3200 - since);
  burstLowAt = Date.now();
}

async function search(query, page = 1, ignoreUsed = false) {
  const url =
    'https://api.openverse.org/v1/images/?' +
    `q=${encodeURIComponent(query)}&page_size=20&page=${page}` +
    `&license=${LICENSES}&category=photograph`;
  const res = await fetch(url);
  if (res.status === 429) throw new Error('rate limited (429)');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  await throttle(res);
  const body = await res.json();
  if (!Array.isArray(body.results)) return [];

  // Openverse indexes scans, diagrams and book pages alongside photographs, and
  // the search term often matches a caption rather than the subject. A minimum
  // size is a cheap proxy for "an actual photo of the object".
  const seen = new Set();
  return body.results
    .filter((r) => (r.width ?? 0) >= 480 && (r.height ?? 0) >= 420)
    .filter((r) => !seen.has(r.url) && seen.add(r.url))
    .filter((r) => ignoreUsed || !usedUrls.has(r.url));
}

/**
 * One page often yields fewer usable photos than a family has products. Extra
 * pages are pulled only when needed, since each one costs a request from the
 * 200/day anonymous budget.
 */
async function harvest(query, needed, ignoreUsed = false) {
  const hits = await search(query, 1, ignoreUsed);
  for (let page = 2; hits.length < needed && page <= 4; page++) {
    const more = await search(query, page, ignoreUsed);
    if (more.length === 0) break;
    hits.push(...more);
  }
  return hits;
}

async function download(hit, destFile, guard) {
  const existed = existsSync(destFile);
  if (existed && !FORCE) return false;
  const res = await fetch(hit.url);
  if (!res.ok) throw new Error(`image HTTP ${res.status}`);
  const buf = Buffer.from(await res.arrayBuffer());
  // Written to a sibling temp file and renamed into place, so an interrupted or
  // malformed conversion can never leave a half-written image where a product
  // expects a real photo. Renaming also means the previous photo stays in place
  // until its replacement is complete.
  const tmp = `${destFile}.tmp`;
  await sharp(buf)
    .resize(SIZE, SIZE, { fit: 'cover', position: 'centre' })
    .webp({ quality: QUALITY })
    .toFile(tmp);
  // The guard sees the finished file before it is published and returns false to
  // reject the candidate, which is how a duplicate is caught: see takenHashes.
  if (guard && !guard(tmp)) {
    unlinkSync(tmp);
    return null;
  }
  renameSync(tmp, destFile);
  return true;
}

const hashOf = (file) => createHash('md5').update(readFileSync(file)).digest('hex');

async function main() {
  mkdirSync(OUT_DIR, { recursive: true });

  console.log(`Fetching catalog from ${API}`);
  const products = await (await fetch(`${API}/products`)).json();
  console.log(`  ${products.length} products`);

  // Group the 606 entries into their 101 base families, preserving API order so
  // photo assignment stays stable between runs.
  const families = new Map();
  for (const p of products) {
    const base = baseName(p.name);
    if (!families.has(base)) families.set(base, []);
    families.get(base).push(p);
  }
  console.log(`  ${families.size} base families\n`);

  // Both of these are seeded from what is already committed, never from scratch.
  //
  // The map used to start empty, so any family whose search failed silently lost
  // its entries and its products fell back to a generated SVG. The credits used
  // to come only from state.json, which is gitignored, and were then written over
  // CREDITS.md wholesale - so a stale or partially restored state deleted
  // attribution rows for images that were still on disk and still in use.
  // CREDITS.md is the tracked record, so it is the base and state.json only fills
  // gaps; existing map entries are kept and overwritten only by a fresh fetch.
  const mapFile = join(HERE, 'product-images.json');
  const creditsFile = join(REPO, 'CREDITS.md');

  let mapping = {};
  if (existsSync(mapFile)) {
    try {
      mapping = JSON.parse(readFileSync(mapFile, 'utf8'));
    } catch {
      console.log('  existing map unreadable, starting from empty');
    }
  }

  const state = existsSync(STATE_FILE)
    ? JSON.parse(readFileSync(STATE_FILE, 'utf8'))
    : { credits: [], usedUrls: [] };
  const credits = readCreditsFromMarkdown(creditsFile);
  for (const c of state.credits || []) {
    if (!credits.has(c.file)) credits.set(c.file, c);
  }
  usedUrls.clear();
  (state.usedUrls || []).forEach((u) => usedUrls.add(u));

  // Reconcile state with what is actually on disk. audit.mjs --delete removes
  // unusable photos, and if their URLs stayed marked as claimed here they could
  // never be fetched again - leaving those products permanently unmapped.
  const onDisk = new Set(readdirSync(OUT_DIR).filter((f) => f.endsWith('.webp')));
  let pruned = 0;
  for (const [file, credit] of [...credits]) {
    if (!onDisk.has(file)) {
      // Unclaim by the image URL, which is what usedUrls actually stores. It used
      // to unclaim by the landing page, which never matched, so a deleted photo
      // stayed marked as used forever and its family could never be refetched.
      usedUrls.delete(credit.sourceUrl || credit.page);
      credits.delete(file);
      pruned++;
    }
  }
  const missing = [];
  let downloaded = 0;
  let replaced = 0;
  let reused = 0;
  let skipped = 0;
  let n = 0;

  for (const [base, items] of families) {
    n++;
    // --family= restricts the run; the other families keep their committed map
    // entries and credit rows untouched.
    if (ONLY_FAMILY && base.toLowerCase() !== ONLY_FAMILY && slugify(base) !== ONLY_FAMILY) {
      continue;
    }
    const query = QUERIES[base];

    // Skip the search entirely when this family's photos already landed. A
    // re-run after tweaking one query would otherwise re-search all 101
    // families and blow the 200/day anonymous budget on work already done.
    //
    // --rebuild-credits defeats the skip: attribution for by/by-sa images is a
    // licence requirement, so it can only be recovered by asking Openverse again.
    // --force defeats it too, since its whole purpose is to redo photos that are
    // already on disk. download() no-ops when the file exists, so this costs
    // requests, not bandwidth.
    const files = items.map((_, i) => `${slugify(base)}-${i + 1}.webp`);
    if (!REBUILD_CREDITS && !FORCE && files.every((f) => existsSync(join(OUT_DIR, f)))) {
      skipped++;
      items.forEach((p, i) => {
        mapping[p.id] = `/products/${files[i]}`;
      });
      process.stdout.write(`\r  ${n}/${families.size} families   `);
      continue;
    }

    if (!query) {
      missing.push(`${base} (no curated query)`);
      continue;
    }

    let hits = [];
    try {
      hits = await harvest(query, items.length, FORCE);
    } catch (err) {
      missing.push(`${base} (${err.message})`);
      continue;
    }

    if (hits.length === 0) {
      // Left unmapped on purpose: the storefront falls back to its own generated
      // SVG tile, which beats attaching a photo of the wrong object.
      missing.push(`${base} (no usable photo for "${query}")`);
      process.stdout.write(`\r  ${n}/${families.size} families   `);
      await sleep(3200);
      continue;
    }

    // --force replaces the photos a family already has, which means its own
    // current images are the candidates most likely to come back. usedUrls is
    // ignored for the search so a family can be redone, so the guard below is what
    // actually prevents a duplicate: every photo that will still be on disk after
    // this run is hashed up front, and a candidate whose converted bytes match one
    // of them is thrown away in favour of the next one.
    const replacing = new Set(files);
    const takenHashes = new Set();
    if (FORCE) {
      for (const f of readdirSync(OUT_DIR)) {
        if (!f.endsWith('.webp') || replacing.has(f)) continue;
        takenHashes.add(hashOf(join(OUT_DIR, f)));
      }
    }

    // Claim only unused candidates, in order, and stop when they run out rather
    // than wrapping around onto a photo this family (or another) already has.
    let cursor = 0;
    for (const product of items) {
      const file = `${slugify(base)}-${items.indexOf(product) + 1}.webp`;
      const dest = join(OUT_DIR, file);
      const existed = existsSync(dest);

      let wrote = null;
      let hit = null;
      let duplicate = false;
      // Under --force a candidate can be rejected for duplicating a photo that is
      // staying, so keep offering candidates until one is accepted.
      while (cursor < hits.length) {
        const candidate = hits[cursor++];
        try {
          const guard = FORCE ? (tmp) => !takenHashes.has(hashOf(tmp)) : undefined;
          const result = await download(candidate, dest, guard);
          if (result === null) {
            duplicate = true;
            continue;
          }
          wrote = result;
          hit = candidate;
          break;
        } catch (err) {
          missing.push(`${product.name} (image ${err.message})`);
          break;
        }
      }

      if (!hit) {
        if (duplicate) {
          missing.push(`${product.name} (every remaining candidate duplicated an existing photo)`);
        } else if (cursor >= hits.length) {
          missing.push(`${product.name} (no unique photo left for "${query}")`);
        }
        continue;
      }

      usedUrls.add(hit.url);
      if (wrote) {
        existed ? replaced++ : downloaded++;
        takenHashes.add(hashOf(dest));
      } else {
        reused++;
      }
      mapping[product.id] = `/products/${file}`;
      // Attribution is keyed to the bytes on disk. When the photo is replaced the
      // row has to be replaced too: keeping the old row would credit the new
      // creator's picture to the previous photographer, which is both wrong and a
      // licence breach for the by/by-sa images.
      if (wrote || !credits.has(file)) {
        credits.set(file, {
          file,
          product: product.name,
          query,
          creator: hit.creator || 'unknown',
          license: `${hit.license} ${hit.license_version || ''}`.trim(),
          provider: hit.provider || '',
          page: hit.foreign_landing_url || hit.url,
          // Kept so a later run can release this exact photo from usedUrls.
          sourceUrl: hit.url,
        });
      }
    }

    process.stdout.write(`\r  ${n}/${families.size} families   `);
    await sleep(3200);
  }

  process.stdout.write('\n\n');

  // Integrity gate. The map is what the storefront and the bulk apply call read, so
// it must not reference a photo that is not on disk. Anything left dangling by a
// failed download is dropped here and reported rather than being written out.
const dangling = [];
for (const [id, url] of Object.entries(mapping)) {
  if (!existsSync(join(OUT_DIR, url.replace(/^\/products\//, '')))) {
    delete mapping[id];
    dangling.push(`${id} -> ${url}`);
  }
}

writeFileSync(mapFile, JSON.stringify(mapping, null, 2));

  const creditLines = [
    '# Product photo credits',
    '',
    'Product photos come from [Openverse](https://openverse.org), which aggregates',
    'Flickr, Wikimedia Commons and other sources under Creative Commons licences.',
    '',
    '`cc0` and `pdm` need no attribution. `by` and `by-sa` do, and the images have',
    'been cropped and resized, which makes the crop a derivative - so the `by-sa`',
    'entries are share-alike. Review this list before using the storefront',
    'commercially.',
    '',
    '| File | Product | Search | Licence | Creator | Source |',
    '| --- | --- | --- | --- | --- | --- |',
    ...[...credits.values()].map(
      (c) =>
        `| \`${c.file}\` | ${c.product} | ${c.query} | ${c.license} | ${c.creator} | [link](${c.page}) |`
    ),
    '',
  ];
  writeFileSync(
    STATE_FILE,
    JSON.stringify({ credits: [...credits.values()], usedUrls: [...usedUrls] }, null, 2)
  );
  writeFileSync(join(REPO, 'CREDITS.md'), creditLines.join('\n'));

  console.log(`images downloaded  : ${downloaded}`);
  console.log(`images replaced    : ${replaced} (--force over existing)`);
  console.log(`images reused       : ${reused}`);
  console.log(`families skipped    : ${skipped} (already downloaded)`);
  console.log(`stale credits pruned: ${pruned}`);
  console.log(`map entries dropped : ${dangling.length} (target file missing)`);
  console.log(`products mapped     : ${Object.keys(mapping).length}/${products.length}`);
  console.log(`credits written     : ${credits.size} -> CREDITS.md`);
  console.log(`map written         : ${mapFile}`);

  if (dangling.length) {
    console.log(`\nmap entries dropped, ${dangling.length}:`);
    dangling.forEach((d) => console.log(`  - ${d}`));
  }

  if (missing.length) {
    console.log(`\nunmapped (keep generated SVG), ${missing.length}:`);
    missing.forEach((m) => console.log(`  - ${m}`));
  }
}

main().catch((err) => {
  console.error(`\nfailed: ${err.message}`);
  process.exit(1);
});
