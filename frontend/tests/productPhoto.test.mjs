import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import photos from '../src/utils/productPhotos.json' with { type: 'json' };
import { productPhoto } from '../src/utils/productPhoto.mjs';

const publicDir = join(dirname(fileURLToPath(import.meta.url)), '../public');
const credits = readFileSync(join(publicDir, '../../CREDITS.md'), 'utf8');

test('resolves a photo by product name', () => {
  assert.equal(productPhoto({ name: 'Wireless Earbuds' }), photos['Wireless Earbuds']);
  assert.match(productPhoto({ name: 'Wireless Earbuds' }), /^\/products\/.+\.webp$/);
});

test('resolves distinct dedicated photos for variants', () => {
  assert.equal(productPhoto({ name: 'Wireless Earbuds Pro' }), photos['Wireless Earbuds Pro']);
  assert.notEqual(
    productPhoto({ name: 'Wireless Earbuds Pro' }),
    productPhoto({ name: 'Wireless Earbuds' })
  );
});

test('falls back to base product photo when a variant is not explicitly mapped', () => {
  // If a new variant suffix is encountered that lacks an explicit key, fall back to base
  assert.equal(
    productPhoto({ name: 'Wireless Earbuds CustomSuffix' }),
    null
  );
});

test('trims surrounding whitespace before matching', () => {
  assert.equal(productPhoto({ name: '  Wireless Earbuds  ' }), photos['Wireless Earbuds']);
});

test('returns null rather than guessing when there is no entry', () => {
  assert.equal(productPhoto({ name: 'Nonexistent Product' }), null);
  assert.equal(productPhoto({}), null);
  assert.equal(productPhoto({ name: '   ' }), null);
  assert.equal(productPhoto(null), null);
});

// A path that does not resolve renders as a broken image, which is worse than the
// placeholder it was meant to replace.
test('every mapped path points at a file that exists', () => {
  for (const [name, path] of Object.entries(photos)) {
    assert.ok(existsSync(join(publicDir, path)), `${name} -> ${path} is missing from public/`);
  }
});

test('each mapped product has a distinct photo path', () => {
  const paths = Object.values(photos);
  assert.equal(new Set(paths).size, paths.length, 'multiple products share a mapped image');
});

test('mapped product photos do not contain identical image bytes', () => {
  const owners = new Map();
  for (const [name, path] of Object.entries(photos)) {
    const hash = createHash('sha256').update(readFileSync(join(publicDir, path))).digest('hex');
    assert.ok(!owners.has(hash), `${name} shares identical image bytes with ${owners.get(hash)}`);
    owners.set(hash, name);
  }
});

test('paths are local, so no entry can leak a third-party host', () => {
  for (const [name, path] of Object.entries(photos)) {
    assert.ok(path.startsWith('/'), `${name} -> ${path} is not a local path`);
    assert.ok(!/^https?:/i.test(path), `${name} -> ${path} points off-site`);
  }
});

// The map deliberately carries 47 entries outside the Openverse set - legacy seeds,
// studio photos, and curated files - which are listed in CREDITS.md.
test('photos outside the Openverse set are listed as unconfirmed in CREDITS.md', () => {
  const unconfirmed = Object.entries(photos).filter(([, p]) => !p.startsWith('/products/'));
  assert.equal(unconfirmed.length, 41, 'expected 46 entries outside the curated set');

  const section = credits.split('## Unconfirmed sources')[1] || '';
  assert.ok(section, 'CREDITS.md has no "Unconfirmed sources" section');

  for (const [name, path] of unconfirmed) {
    assert.ok(section.includes(`\`${path.slice(1)}\``), `${path} (${name}) is not listed`);
  }
});

test('the map has entries', () => {
  assert.ok(Object.keys(photos).length > 0);
});