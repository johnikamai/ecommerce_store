const fs = require('fs');

const seeder = fs.readFileSync('backend/src/main/java/com/ecommerce/ecommerce_system/config/ProductSeeder.java', 'utf8');

const baseRows = seeder.split('\n').filter(l => l.trim().startsWith('{"'));
const brandCounts = {};
baseRows.forEach(r => {
  const parts = r.split(',').map(s => s.trim().replace(/^"|"$/g, ''));
  const b = parts[parts.length - 1].replace(/\}.*$/, '').replace(/^"|"$/g, '');
  brandCounts[b] = (brandCounts[b] || 0) + 1;
});
console.log('Brands in CATALOG array:');
console.log(brandCounts);
