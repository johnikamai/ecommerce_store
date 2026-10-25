import { bundlePercentFor } from './cartCalculations.js';
/**
 * Engine to dynamically generate complementary shopping bundles with discount savings.
 * Example: Laptop + Mouse + Bag, or Mobile + Earbuds + Charger.
 */

const BUNDLE_MAP = {
  Laptops: [
    { searchWords: ['mouse', 'wireless mouse'], category: 'Electronics', fallbackName: 'Wireless Mouse' },
    { searchWords: ['backpack', 'laptop backpack', 'bag'], category: 'Books & Stationery', fallbackName: 'Laptop Backpack' },
  ],
  Mobiles: [
    { searchWords: ['earbuds', 'headphone', 'pods'], category: 'Headphones', fallbackName: 'Wireless Earbuds' },
    { searchWords: ['charger', 'fast charger', 'adapter'], category: 'Electronics', fallbackName: 'Fast Charger' },
  ],
  Headphones: [
    { searchWords: ['stand', 'holder', 'mount'], category: 'Automotive', fallbackName: 'Headphone Stand' },
    { searchWords: ['speaker', 'soundbar'], category: 'Electronics', fallbackName: 'Bluetooth Speaker' },
  ],
  Shoes: [
    { searchWords: ['bottle', 'water bottle'], category: 'Sports', fallbackName: 'Insulated Water Bottle' },
    { searchWords: ['socks', 'beanie', 'hoodie'], category: 'Fashion', fallbackName: 'Knit Beanie' },
  ],
  Watches: [
    { searchWords: ['earbuds', 'airbuds'], category: 'Headphones', fallbackName: 'Wireless Earbuds' },
    { searchWords: ['bottle', 'flask'], category: 'Sports', fallbackName: 'Water Bottle' },
  ],
  Sports: [
    { searchWords: ['bottle', 'water bottle'], category: 'Sports', fallbackName: 'Water Bottle' },
    { searchWords: ['bands', 'resistance'], category: 'Sports', fallbackName: 'Resistance Bands' },
  ],
  Fashion: [
    { searchWords: ['sneakers', 'shoes'], category: 'Shoes', fallbackName: 'Lifestyle Sneakers' },
    { searchWords: ['backpack', 'bag'], category: 'Books & Stationery', fallbackName: 'Classic Bag' },
  ],
  'Home & Living': [
    { searchWords: ['lamp', 'light'], category: 'Home & Living', fallbackName: 'Desk Lamp' },
    { searchWords: ['cushion', 'pillow'], category: 'Home & Living', fallbackName: 'Cushion Cover' },
  ]
};



export function buildProductBundle(mainProduct, catalog = []) {
  if (!mainProduct || !catalog || catalog.length === 0) return null;

  const category = mainProduct.category || '';
  const rules = BUNDLE_MAP[category] || [
    { searchWords: ['bottle', 'charger', 'bag'], category: null },
    { searchWords: ['mouse', 'lamp', 'snack'], category: null }
  ];

  const matchedItems = [];
  const usedIds = new Set([mainProduct.id]);

  for (const rule of rules) {
    let candidate = catalog.find((p) => {
      if (usedIds.has(p.id) || p.stockQuantity <= 0) return false;
      if (rule.category && p.category !== rule.category) return false;
      const lowerName = (p.name || '').toLowerCase();
      return rule.searchWords.some((w) => lowerName.includes(w));
    });

    // Fallback: match by rule category alone if specific keyword not matched
    if (!candidate && rule.category) {
      candidate = catalog.find((p) => !usedIds.has(p.id) && p.category === rule.category && p.stockQuantity > 0);
    }

    if (candidate) {
      usedIds.add(candidate.id);
      matchedItems.push(candidate);
    }
  }

  if (matchedItems.length === 0) return null;

  const allItems = [mainProduct, ...matchedItems];
  const regularTotal = allItems.reduce((acc, p) => acc + Number(p.price || 0), 0);
  const discountPercent = bundlePercentFor(allItems.length) * 100;
  const bundleDiscount = Math.round(regularTotal * discountPercent) / 100;
  const bundlePrice = regularTotal - bundleDiscount;

  return {
    mainProduct,
    accessories: matchedItems,
    allItems,
    regularTotal,
    bundleDiscount,
    bundlePrice,
    discountPercent,
  };
}
