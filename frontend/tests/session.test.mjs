import test from 'node:test';
import assert from 'node:assert/strict';
import { tokenIsCurrent, clearSession } from '../src/utils/session.js';
const token = exp => `header.${Buffer.from(JSON.stringify({ exp })).toString('base64url')}.signature`;
test('expired and malformed tokens do not grant client access', () => {
  assert.equal(tokenIsCurrent(token(Date.now()/1000 - 10)), false);
  assert.equal(tokenIsCurrent(token(Date.now()/1000 + 60)), true);
  for (const t of [null, '', 'broken', 'a.e30.b']) assert.equal(tokenIsCurrent(t), false);
});
test('logout clears identity and notifies account-scoped state', () => {
  const removed = []; let changed = false;
  globalThis.localStorage = { removeItem: key => removed.push(key) };
  globalThis.window = { dispatchEvent: event => { changed = event.type === 'session-change'; } };
  clearSession();
  assert.deepEqual(removed, ['token', 'role', 'username', 'customerId', 'customerName']);
  assert.equal(changed, true);
});
