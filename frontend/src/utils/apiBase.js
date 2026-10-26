export function resolveApiBaseUrl(configured, production = false) {
  const value = configured?.trim();
  if (!value) {
    if (production) throw new Error('Set VITE_API_BASE_URL to the HTTPS API Gateway URL ending in /api.');
    return 'http://localhost:8080/api';
  }
  let url;
  try { url = new URL(value); } catch { throw new Error('VITE_API_BASE_URL must be an absolute Gateway URL.'); }
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new Error('VITE_API_BASE_URL must be an HTTP(S) Gateway URL without credentials, query or fragment.');
  }
  const loopback = url.hostname === 'localhost' || url.hostname.endsWith('.localhost') || /^(127\.|\[::1\]$|0\.0\.0\.0$)/.test(url.hostname);
  if (production && (url.protocol !== 'https:' || loopback)) {
    throw new Error('Production VITE_API_BASE_URL must use HTTPS and cannot point to loopback.');
  }
  if (!/\/api\/?$/.test(url.pathname)) throw new Error('VITE_API_BASE_URL must end in /api.');
  return value.replace(/\/$/, '');
}
