// Applies product-images.json to the running backend through the admin bulk
// endpoint, logging in first so the whole run is a single reproducible command.
//
//   ADMIN_EMAIL=... ADMIN_PASSWORD=... node apply-images.mjs
//   node apply-images.mjs --token <jwt>            (skip login)
//   node apply-images.mjs --verify                 (read-only, no writes)
//
// Credentials are read from the environment rather than argv so they do not end
// up in shell history, and never written to disk.

import { readFileSync } from 'node:fs';

const API =
  process.env.API_URL?.replace(/\/$/, '') ||
  'https://ecommerce-backend-2gas.onrender.com/api';

const MAP_FILE = new URL('./product-images.json', import.meta.url);

function arg(name) {
  const i = process.argv.indexOf(name);
  return i === -1 ? undefined : process.argv[i + 1];
}

const verifyOnly = process.argv.includes('--verify');

async function login() {
  const email = process.env.ADMIN_EMAIL;
  const password = process.env.ADMIN_PASSWORD;
  if (!email || !password) {
    throw new Error(
      'Set ADMIN_EMAIL and ADMIN_PASSWORD (the same values Render uses), or pass --token <jwt>.'
    );
  }
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: email, password }),
  });
  if (!res.ok) {
    throw new Error(`Login failed: ${res.status} ${(await res.text()).slice(0, 200)}`);
  }
  const { token } = await res.json();
  if (!token) throw new Error('Login succeeded but no token was returned.');
  return token;
}

async function currentImages(token) {
  const res = await fetch(`${API}/products`, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) throw new Error(`Could not read products: ${res.status}`);
  const products = await res.json();
  const withImage = products.filter((p) => p.imageUrl);
  return {
    total: products.length,
    withImage: withImage.length,
    unique: new Set(withImage.map((p) => p.imageUrl)).size,
    sample: withImage.slice(0, 2).map((p) => `${p.id} -> ${p.imageUrl}`),
  };
}

const images = JSON.parse(readFileSync(MAP_FILE, 'utf8'));
const entries = Object.entries(images).length;
console.log(`map file          : ${entries} products`);
console.log(`api               : ${API}`);

const token = arg('--token') || (await login());

if (verifyOnly) {
  const now = await currentImages(token);
  console.log(`products total    : ${now.total}`);
  console.log(`with imageUrl     : ${now.withImage}`);
  console.log(`unique imageUrls  : ${now.unique}`);
  if (now.sample.length) console.log(`sample            : ${now.sample.join(', ')}`);
  process.exit(0);
}

const res = await fetch(`${API}/admin/catalog/images`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
  body: JSON.stringify(images),
});
if (!res.ok) {
  throw new Error(`Bulk update failed: ${res.status} ${(await res.text()).slice(0, 300)}`);
}
console.log('apply result      :', await res.json());

const after = await currentImages(token);
console.log(`products total    : ${after.total}`);
console.log(`with imageUrl     : ${after.withImage}`);
console.log(`unique imageUrls  : ${after.unique}`);
if (after.withImage !== after.total || after.unique !== after.total) {
  console.log('\nNot every product has its own image yet.');
} else {
  console.log('\nEvery product has a distinct image.');
}
