const fs = require('fs');

const seeder = fs.readFileSync('backend/src/main/java/com/ecommerce/ecommerce_system/config/ProductSeeder.java', 'utf8');

const baseRows = seeder.split('\n').filter(l => l.trim().startsWith('{"')).slice(0, 97);
const brandCounts = {};
baseRows.forEach(r => {
  const m = r.match(/"([^"]+)"\s*\}\s*,?$/);
  if (m) {
    brandCounts[m[1]] = (brandCounts[m[1]] || 0) + 1;
  }
});
console.log('Found brands in CATALOG:');
console.log(brandCounts);
