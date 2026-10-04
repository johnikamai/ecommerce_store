// Curated Pixabay search terms, one per base catalog product.
//
// These are deliberately hand-written rather than derived from product names.
// Several names do not match how a stock library tags photos: "Multigrain
// Atta 5kg" is whole-wheat flour, "Hooked Taking Notes" is a doodle/sketch
// notebook, and "USB-C Charger 65W" needs the cable noun to surface charger
// photos. Sending the raw product name to the API returns food shots for the
// first and empty results for the second.
//
// Keep keys exactly equal to the base product names in ProductSeeder.CATALOG.

export const QUERIES = {
  // ---------- Electronics ----------
  'Wireless Earbuds': 'wireless earbuds',
  'Bluetooth Speaker': 'bluetooth speaker',
  Smartwatch: 'smartwatch',
  '4K Action Camera': 'action camera',
  'USB-C Charger 65W': 'power charger',
  'Mechanical Keyboard': 'mechanical keyboard',
  'Wireless Mouse': 'computer mouse',
  'LED Desk Lamp': 'desk lamp',
  'Power Bank 20000mAh': 'power bank',
  'Smartphone Stand': 'phone holder',
  'Action Cam Mount Kit': 'camera accessory mount',
  'HDMI Cable 2m': 'hdmi cable',

  // ---------- Fashion ----------
  'Cotton T-Shirt': 'plain t-shirt',
  'Slim Fit Jeans': 'jeans denim',
  'Running Sneakers': 'running sneakers',
  'Denim Jacket': 'denim jacket',
  'A-Line Dress': 'summer dress',
  'Oversized Hoodie': 'hoodie sweatshirt',
  'Leather Wallet': 'leather wallet',
  'Polarized Sunglasses': 'sunglasses',
  'Silk Scarf': 'silk scarf',
  'Canvas Tote Bag': 'tote bag',
  'Formal Shirt': 'dress shirt',
  'Classic Ballerina Flats': 'ballet shoes',

  // ---------- Beauty ----------
  'Vitamin C Serum': 'skincare serum',
  'Sunscreen SPF 50': 'sunscreen',
  'Matte Lipstick': 'lipstick',
  'Hair Repair Oil': 'hair oil',
  'Aloe Face Wash': 'face wash',
  'Skincare Gift Kit': 'cosmetics set',
  'Roll-On Perfume': 'perfume bottle',
  'Clay Face Mask': 'facial skincare',
  'Lip Balm Trio': 'lip balm',
  'Anti-Dandruff Shampoo': 'shampoo',
  'Hydrating Night Cream': 'cosmetic cream',
  'Rose Water Toner': 'cosmetic bottle',

  // ---------- Home & Living ----------
  'Himalayan Salt Lamp': 'salt lamp',
  'Velvet Cushion Cover': 'cushion pillow',
  'Ceramic Coffee Set': 'ceramic mug coffee',
  'Faux Fiddle-Leaf Plant': 'fiddle leaf fig plant',
  'Cotton Area Rug': 'area rug',
  'Sheer Curtains Pair': 'curtains window',
  'Framed Wall Art': 'framed wall art',
  'Cotton Bedsheet Set': 'bed sheets bedroom',
  'Aroma Diffuser': 'humidifier',
  'Ceramic Vase': 'ceramic vase',
  'Scented Candle Set': 'scented candle',
  'Storage Basket Trio': 'wicker basket',

  // ---------- Sports ----------
  'Adjustable Dumbbells': 'dumbbell',
  'Non-Slip Yoga Mat': 'yoga mat',
  'Insulated Water Bottle': 'water bottle',
  'Cricket Bat': 'cricket bat',
  'Resistance Bands Set': 'resistance band',
  'Speed Jump Rope': 'jump rope',
  'Foldable Squat Rack': 'barbell gym',
  'Cycling Helmet': 'bicycle helmet',
  'Gym Floor Mats': 'gym mat',
  'Adjustable Ankle Weights': 'dumbbell',
  'Tennis Racket': 'tennis racket',
  'Swim Goggles': 'swim goggles',

  // ---------- Books & Stationery ----------
  'Bullet Journal': 'journal',
  'Gel Pen Set': 'gel pens',
  'Best-Selling Novel': 'paperback novel book',
  'Weekly Planner': 'planner notebook',
  'Drawing Pencil Set': 'pencils',
  'Laptop Backpack': 'backpack',
  'Pastel Marker Set': 'markers pens',
  'Linen Hardcover Journal': 'notebook',
  'Zip Pencil Case': 'pencil case',
  'Desk Pen Mug': 'pencil holder',
  'Hooked Taking Notes': 'notebook',
  'English Grammar Book': 'english language book',

  // ---------- Toys & Kids ----------
  'Building Blocks 500': 'building blocks',
  'Plush Teddy Bear': 'teddy bear',
  '3D Wooden Puzzle': 'wooden puzzle',
  'Off-Road RC Car': 'remote control car',
  'Family Board Game': 'board game',
  'Stacking Rings Set': 'stacking toy',
  'Watercolor Paint Set': 'watercolour paints',
  'Kids Science Kit': 'children science experiment',

  // ---------- Groceries & Food ----------
  'Arabica Coffee Beans': 'coffee beans',
  'Green Tea Bags': 'green tea',
  'Dark Chocolate Box': 'chocolate box',
  'Cold-Pressed Olive Oil': 'olive oil bottle',
  'Organic Honey Jar': 'honey jar',
  'Breakfast Granola': 'granola cereal',
  'Whole-Wheat Pasta': 'pasta',
  'Buttery Croissants': 'croissants',
  'Multigrain Atta 5kg': 'wheat flour',

  // ---------- Automotive ----------
  'Car Cleaning Kit': 'car cleaning',
  'Tyre Inflator': 'tire inflator',
  'Phone Mount Holder': 'car mount',
  'Steering Wheel Cover': 'steering wheel cover',
  'LED Headlight Bulbs': 'car headlight',
  'Car Air Freshener': 'car interior',

  // ---------- Pets ----------
  'Dog Food Premium': 'dog food',
  'Cat Tower Scratcher': 'cat furniture',
  'Pet Grooming Brush': 'dog brush',
  'Aquarium Starter Kit': 'aquarium fish tank',
  'Pet Travel Carrier': 'dog carrier',
  'Cat Litter Scoop Box': 'cat litter',
};

// Product names ending in one of these are the generated variants of a base
// entry (ProductSeeder.seedExpandedCatalog) and must reuse the base query.
export const VARIANT_SUFFIXES = ['Classic', 'Lite', 'Pro', 'Ultra', 'Max'];

/** Strips a generated variant suffix, e.g. "Bullet Journal Ultra" -> "Bullet Journal". */
export function baseName(productName) {
  for (const suffix of VARIANT_SUFFIXES) {
    const tail = ` ${suffix}`;
    if (productName.endsWith(tail)) {
      return productName.slice(0, -tail.length);
    }
  }
  return productName;
}
