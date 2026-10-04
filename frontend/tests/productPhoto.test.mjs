import test from 'node:test';
import assert from 'node:assert/strict';
import photos from '../src/utils/productPhotos.json' with { type: 'json' };
import { productPhoto } from '../src/utils/productPhoto.mjs';

test('resolves a curated photo by product name', () => {
  assert.equal(productPhoto({ name: 'Wireless Earbuds' }), photos['Wireless Earbuds']);
  assert.match(productPhoto({ name: 'Wireless Earbuds' }), /^\/products\/.+\.webp$/);
});

test('ignores a marketing suffix when looking up the name', () => {
  assert.equal(
    productPhoto({ name: 'Wireless Earbuds Pro' }),
    productPhoto({ name: 'Wireless Earbuds' })
  );
});

test('trims surrounding whitespace before matching', () => {
  assert.equal(productPhoto({ name: '  Wireless Earbuds  ' }), photos['Wireless Earbuds']);
});

test('returns null rather than guessing when there is no curated photo', () => {
  assert.equal(productPhoto({ name: 'Nonexistent Product' }), null);
  assert.equal(productPhoto({}), null);
  assert.equal(productPhoto({ name: '   ' }), null);
  assert.equal(productPhoto(null), null);
});

// The whole point of the corrected map: nothing may point back at the seeded
// /images/N.jpg files the curated set replaced, or at files with no CREDITS.md
// row, because either one puts an unreviewed photo back on the storefront.
test('no entry points at a legacy seed or an unattributed photo', () => {
  for (const [name, path] of Object.entries(photos)) {
    assert.ok(path.startsWith('/products/'), `${name} -> ${path} is outside the curated set`);
  }
});

test('the curated set has entries', () => {
  assert.ok(Object.keys(photos).length > 0);
});