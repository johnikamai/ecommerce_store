import { readFileSync } from 'node:fs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { tokenIsCurrent, tokenRole, clearSession, adminGuard } from '../src/utils/session.js';

const token = (claims) =>
  `header.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.signature`;
const live = (extra) => token({ exp: Date.now() / 1000 + 60, ...extra });

test('expired and malformed tokens do not grant client access', () => {
  assert.equal(tokenIsCurrent(token({ exp: Date.now() / 1000 - 10 })), false);
  assert.equal(tokenIsCurrent(token({ exp: Date.now() / 1000 + 60 })), true);
  for (const t of [null, '', 'broken', 'a.e30.b']) assert.equal(tokenIsCurrent(t), false);
});

test('a token with no expiry counts as not current', () => {
  // Nothing to compare against, so access cannot be justified.
  assert.equal(tokenIsCurrent(token({ role: 'ADMIN' })), false);
});

test('the role is read from the signed token', () => {
  assert.equal(tokenRole(live({ role: 'ADMIN' })), 'ADMIN');
  assert.equal(tokenRole(live({ role: 'STAFF' })), 'STAFF');
  assert.equal(tokenRole(live({})), null);
});

test('an admin keeps the console across a refresh with no cached role', () => {
  // The reported bug: localStorage.role was gone, so the guard read it as a
  // customer and redirected to the catalogue. The signed claim has to carry it.
  assert.equal(adminGuard(live({ role: 'ADMIN' }), null), null);
  assert.equal(adminGuard(live({ role: 'STAFF' }), null), null);
});

test('a customer is sent to the catalogue, not the console', () => {
  assert.equal(adminGuard(live({ role: 'CUSTOMER' }), 'ADMIN'), '/products');
  assert.equal(adminGuard(live({ role: 'CUSTOMER' }), null), '/products');
});

test('a token predating the role claim still falls back to the cached copy', () => {
  assert.equal(adminGuard(live({}), 'ADMIN'), null);
});

test('signed-out and unusable tokens go back to sign-in', () => {
  for (const t of [null, '', 'broken']) assert.equal(adminGuard(t, 'ADMIN'), '/');
  assert.equal(adminGuard(token({ exp: Date.now() / 1000 - 10, role: 'ADMIN' }), 'ADMIN'), '/');
});

test('logout clears identity and notifies account-scoped state', () => {
  const removed = [];
  let changed = false;
  globalThis.localStorage = { removeItem: (key) => removed.push(key) };
  globalThis.window = { dispatchEvent: (event) => { changed = event.type === 'session-change'; } };
  clearSession();
  assert.deepEqual(removed, ['token', 'role', 'username', 'customerId', 'customerName']);
  assert.equal(changed, true);
});

// Structural guard rather than a behavioural one: rendering the router needs a
// DOM, but the requirement that guests can browse the storefront is worth pinning
// down, because wrapping these routes in ProtectedRoute is a one-line change that
// locks every visitor out and shows up only in the browser.
test('the storefront stays reachable without signing in', () => {
  const app = readFileSync(new URL('../src/App.jsx', import.meta.url), 'utf8');
  for (const [path, element] of [
    ['/products', 'Products'],
    ['/product/:id', 'ProductDetail'],
    ['/compare', 'Compare'],
  ]) {
    const line = app.split('\n').find((l) => l.includes(`path="${path}"`));
    assert.ok(line, `route ${path} is missing`);
    assert.ok(
      !line.includes('ProtectedRoute'),
      `${path} is behind ProtectedRoute, which would lock guests out: ${line.trim()}`
    );
    assert.ok(line.includes(`<${element} />`), `${path} should render ${element}: ${line.trim()}`);
  }
});

test('the admin console stays behind the guard', () => {
  const app = readFileSync(new URL('../src/App.jsx', import.meta.url), 'utf8');
  const line = app.split('\n').find((l) => l.includes('path="/admin"'));
  assert.ok(line.includes('<AdminRoute>'), `admin route is unguarded: ${line.trim()}`);
});