const fs = require('fs');

const seeder = fs.readFileSync('backend/src/main/java/com/ecommerce/ecommerce_system/config/ProductSeeder.java', 'utf8');

const lines = seeder.split('\n');
const brands = {};

for (const line of lines) {
  const trimmed = line.trim();
  if (trimmed.startsWith('{"')) {
    const parts = trimmed.split('", "');
    if (parts.length >= 7) {
      const brand = parts[6].replace(/".*$/, '').trim();
      brands[brand] = (brands[brand] || 0) + 1;
    }
  }
}

console.log('Brands found in ProductSeeder rows:');
for (const [b, c] of Object.entries(brands)) {
  console.log(`- ${b}: ${c}`);
}
