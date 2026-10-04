// Session helpers.
//
// Everything that needs to look inside the JWT goes through readClaims, so the
// token is base64url-decoded in exactly one place. The other tools each had their
// own copy of this logic, which is how the admin guard ended up disagreeing with
// the session check about whether a token was any good.

const SESSION_KEYS = ['token', 'role', 'username', 'customerId', 'customerName'];

/**
 * Drops every cached credential and tells the app the session changed.
 *
 * Removing the keys is not enough on its own: components that read the role on
 * render need to hear about it, so a window event is dispatched for them.
 */
export function clearSession() {
  for (const key of SESSION_KEYS) localStorage.removeItem(key);
  window.dispatchEvent(new Event('session-change'));
}

/**
 * Reads the claims out of a JWT payload.
 *
 * The payload is decoded, never trusted: the server is what validates the
 * signature. This is only used so the UI knows who it is talking to, and every
 * admin endpoint is authorised server-side regardless of what is returned here.
 *
 * Returns null for anything unreadable rather than throwing, because callers are
 * route guards and a malformed token should mean "signed out", not a crash.
 */
export function readClaims(token) {
  try {
    // base64url uses - and _ where base64 uses + and /, and drops the padding.
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);
    const bytes = atob(padded);
    // Decode UTF-8 by hand: atob hands back one byte per code unit, so any
    // character outside Latin-1 (an admin's name, a Hindi label) arrives as
    // mojibake unless the bytes are reassembled first.
    const json = decodeURIComponent(
      Array.from(bytes, (c) => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`).join('')
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

/**
 * True when the token is readable and has not expired.
 *
 * A token with no exp counts as not current: we cannot tell whether it is still
 * valid, and guessing wrong means rendering a page of failed requests.
 */
export function tokenIsCurrent(token) {
  const claims = readClaims(token);
  return Boolean(claims?.exp) && claims.exp * 1000 > Date.now();
}

/**
 * The role the server signed into the token, or null if there isn't one.
 *
 * Preferred over the copy cached in localStorage, which the browser can drop and
 * anyone can edit from devtools. Relying on that cache is what sent an admin to
 * the customer catalogue on a refresh.
 */
export function tokenRole(token) {
  return readClaims(token)?.role ?? null;
}