// Real gateway smoke tests. Never print tokens, passwords or OTP values.
// Creates one demo customer and a CASH order, then cancels it to restore stock.
const assert = require('node:assert/strict');
const base = process.env.API_URL || 'http://localhost:8080';
let token, customerId, orderId;
async function request(path, { method = 'GET', body, status = 200, backend, anonymous = false, headers = {} } = {}) {
  const response = await fetch(`${base}${path}`, {
    method, headers: { ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(!anonymous && token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(30000)
  });
  const raw = await response.text();
  assert(Array.isArray(status) ? status.includes(response.status) : response.status === status, `${method} ${path}: expected ${status}, got ${response.status}; ${raw.slice(0, 250)}`);
  if (backend) assert.equal(response.headers.get('x-shopease-backend'), backend, `${path} routing`);
  console.log(`PASS ${method} ${path} -> ${response.status}${backend ? ` [${backend}]` : ''}`);
  try { return JSON.parse(raw); } catch { return raw; }
}
async function main() {
  const registry = await fetch('http://localhost:8761/eureka/apps', { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(10000) });
  assert.equal(registry.status, 200);
  const applications = (await registry.json()).applications.application;
  for (const name of ['API-GATEWAY', 'PRODUCT-SERVICE']) {
    assert(applications.some(app => app.name === name && app.instance.some(instance => instance.status === 'UP')), `${name} must be registered UP`);
  }
  console.log('PASS Eureka HTTP 200; gateway and product-service registered UP');
  await request('/actuator/health');
  const products = await request('/api/products', { backend: 'product-service', anonymous: true });
  assert(Array.isArray(products) && products.length > 0, 'Existing products must be present');
  console.log(`PASS ${products.length} existing products loaded`);
  const product = products.find(p => p.stockQuantity > 2);
  assert(product, 'Need an in-stock product');
  const detail = await request(`/api/products/${product.id}`, { backend: 'product-service' });
  assert.equal(detail.name, product.name);
  const categories = await request('/api/categories', { backend: 'product-service' });
  assert(categories.length > 0 && categories.some(c => c.productCount > 0));
  await request('/api/products/9223372036854775807', { status: 404, backend: 'product-service' });
  const catalog = await request('/api/products/catalog', { backend: 'monolith-compatibility' });
  assert.equal(catalog.length, products.length);
  assert(catalog.every(p => 'averageRating' in p && 'unitsSold' in p));
  const search = await request('/api/products/search?q=wireless', { backend: 'monolith-compatibility' });
  assert(Array.isArray(search.results) && search.results.length > 0);
  const origin = 'http://localhost:5173';
  const cors = await fetch(`${base}/api/auth/login`, { method: 'OPTIONS', headers: { Origin: origin, 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'content-type,authorization' } });
  assert.equal(cors.status, 200);
  assert.equal(cors.headers.get('access-control-allow-origin'), origin);
  console.log('PASS CORS preflight HTTP 200; single allowed origin header');
  await request('/api/orders/customer/1', { status: 401, anonymous: true });
  const username = `review_${Date.now()}`;
  const password = `Review!${require('node:crypto').randomBytes(12).toString('hex')}9aA`;
  const registration = await request('/api/auth/register', { method: 'POST', body: { username, email: `${username}@example.com`, password }, backend: 'monolith-compatibility' });
  assert(registration.otpRequired);
  assert(registration.devOtp, 'Automated signup requires existing no-email demo mode; use real email manually if configured');
  const verified = await request('/api/auth/verify-otp', { method: 'POST', body: { username, otp: registration.devOtp } });
  assert(verified.token && verified.customerId);
  const login = await request('/api/auth/login', { method: 'POST', body: { username, password } });
  assert(login.token && login.customerId);
  token = login.token; customerId = login.customerId;
  await request('/api/orders', { status: 403 });
  await request('/api/coupons', { status: 403 });
  await request('/api/coupons/recommend?subtotal=1500', { status: [200, 204], anonymous: true });
  await request('/api/products', { method: 'POST', body: {}, status: 403, backend: 'monolith-compatibility' });
  await request('/api/wishlist', { method: 'POST', body: { customerId, productId: product.id }, status: 201 });
  const wishlist = await request(`/api/wishlist/customer/${customerId}`);
  assert(wishlist.some(item => item.product.id === product.id));
  await request(`/api/wishlist/customer/${customerId}/product/${product.id}`, { method: 'DELETE', status: 204 });
  await request(`/api/orders/customer/${customerId + 1}`, { status: 403 });
  const checkout = { customerId, shippingAddress: 'College review test, Bengaluru, Karnataka 560001', paymentMethod: 'CASH', items: [{ productId: product.id, quantity: 1 }] };
  const quote = await request('/api/orders/quote', { method: 'POST', body: checkout });
  assert(Number(quote.total) > 0);
  const order = await request('/api/orders', { method: 'POST', body: { ...checkout, expectedTotal: quote.total }, status: 201 });
  orderId = order.id;
  assert(orderId && order.customer.id === customerId);
  const after = await request(`/api/products/${product.id}`, { backend: 'product-service' });
  assert.equal(after.stockQuantity, product.stockQuantity - 1, 'Checkout stock change must be visible in product-service');
  const orders = await request(`/api/orders/customer/${customerId}`);
  assert(orders.some(o => o.id === orderId));
  await request(`/api/orders/${orderId}/tracking`);
  await request(`/api/payments/order/${orderId}`);
  await request(`/api/notifications/customer/${customerId}`);
  await request('/api/recommendations/personalized');
  await request(`/api/orders/${orderId}/cancel`, { method: 'POST' });
  orderId = null;
  const restored = await request(`/api/products/${product.id}`);
  assert.equal(restored.stockQuantity, product.stockQuantity, 'Cancellation restores stock');
  const frontend = await fetch('http://localhost:5173', { signal: AbortSignal.timeout(10000) });
  assert.equal(frontend.status, 200);
  assert((await frontend.text()).includes('root'));
  console.log('PASS Frontend HTTP 200');
  console.log(`SUCCESS: real customer ${customerId}; checkout and cancellation persisted; original stock restored.`);
}
main().catch(async error => {
  console.error(`FAIL ${error.message}`);
  if (orderId && token) {
    try { await request(`/api/orders/${orderId}/cancel`, { method: 'POST' }); console.log('Test order cancelled after failure.'); }
    catch { console.error('Test order remains; cancel it manually.'); }
  }
  process.exitCode = 1;
});


