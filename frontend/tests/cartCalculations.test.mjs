import test from 'node:test';
import assert from 'node:assert/strict';
import { bundlePercentFor, nextBundleTier } from '../src/utils/cartCalculations.js';
import { buildProductBundle } from '../src/utils/bundleEngine.js';

test('bundlePercentFor calculates tiered discounts accurately based on distinct item count', () => {
  assert.equal(bundlePercentFor(0), 0, '0 products should have 0% discount');
  assert.equal(bundlePercentFor(1), 0, '1 product should have 0% discount');
  assert.equal(bundlePercentFor(2), 0.05, '2 distinct products should unlock 5% discount');
  assert.equal(bundlePercentFor(3), 0.10, '3 distinct products should unlock 10% discount');
  assert.equal(bundlePercentFor(4), 0.15, '4 distinct products should unlock 15% discount');
  assert.equal(bundlePercentFor(8), 0.15, '5+ distinct products should cap at 15% discount');
});

test('nextBundleTier indicates correct threshold for tier upgrade', () => {
  assert.equal(nextBundleTier(0), 2, 'With 0 products, target is 2');
  assert.equal(nextBundleTier(1), 2, 'With 1 product, target is 2');
  assert.equal(nextBundleTier(2), 3, 'With 2 products, target is 3');
  assert.equal(nextBundleTier(3), 4, 'With 3 products, target is 4');
  assert.equal(nextBundleTier(4), 0, 'With 4 products, top tier is reached (0)');
  assert.equal(nextBundleTier(6), 0, 'With 6 products, top tier is reached (0)');
});

test('cart financial calculation logic: shipping thresholds and tax computations', () => {
  const FLAT_SHIPPING = 49;
  const FREE_SHIPPING_THRESHOLD = 999;
  const TAX_RATE = 0.18;

  // Scenario 1: Subtotal under threshold (e.g. ₹500 with 1 item)
  const total1 = 500;
  const discount1 = total1 * bundlePercentFor(1); // 0
  const shipping1 = total1 <= 0 || total1 >= FREE_SHIPPING_THRESHOLD ? 0 : FLAT_SHIPPING;
  const goods1 = total1 - discount1;
  const tax1 = Math.round(goods1 * TAX_RATE * 100) / 100;
  const grand1 = Math.round((goods1 + shipping1 + tax1) * 100) / 100;

  assert.equal(shipping1, 49);
  assert.equal(tax1, 90);
  assert.equal(grand1, 639);

  // Scenario 2: Subtotal eligible for free shipping (e.g. ₹1200 with 3 items = 10% bundle off)
  const total2 = 1200;
  const discount2 = Math.round(total2 * bundlePercentFor(3) * 100) / 100; // 120
  const shipping2 = total2 <= 0 || total2 >= FREE_SHIPPING_THRESHOLD ? 0 : FLAT_SHIPPING;
  const goods2 = total2 - discount2; // 1080
  const tax2 = Math.round(goods2 * TAX_RATE * 100) / 100; // 194.4
  const grand2 = Math.round((goods2 + shipping2 + tax2) * 100) / 100; // 1274.4

  assert.equal(discount2, 120);
  assert.equal(shipping2, 0);
  assert.equal(tax2, 194.4);
  assert.equal(grand2, 1274.4);
});

test('buildProductBundle pairs complementary accessories with 12% savings', () => {
  const mainLaptop = { id: 101, name: 'MacBook Air M2', category: 'Laptops', price: 99900, stockQuantity: 5 };
  const catalog = [
    { id: 101, name: 'MacBook Air M2', category: 'Laptops', price: 99900, stockQuantity: 5 },
    { id: 202, name: 'Logitech Wireless Mouse', category: 'Electronics', price: 1500, stockQuantity: 10 },
    { id: 303, name: 'Urban Laptop Backpack', category: 'Books & Stationery', price: 2500, stockQuantity: 8 },
    { id: 404, name: 'Casual T-Shirt', category: 'Fashion', price: 799, stockQuantity: 20 },
  ];

  const bundle = buildProductBundle(mainLaptop, catalog);
  assert.ok(bundle, 'Bundle should be successfully built for Laptop');
  assert.equal(bundle.mainProduct.id, 101);
  assert.equal(bundle.accessories.length, 2);
  assert.equal(bundle.allItems.length, 3);
  assert.equal(bundle.regularTotal, 103900); // 99900 + 1500 + 2500
  assert.equal(bundle.discountPercent, 12);
  assert.equal(bundle.bundleDiscount, Math.round((103900 * 12) / 100));
  assert.equal(bundle.bundlePrice, bundle.regularTotal - bundle.bundleDiscount);
});
