const fs = require('fs');

const seeder = fs.readFileSync('backend/src/main/java/com/ecommerce/ecommerce_system/config/ProductSeeder.java', 'utf8');

const baseRows = seeder.split('\n').filter(l => l.trim().startsWith('{"')).map(l => l.trim());
console.log('Total base rows in file:', baseRows.length);
let withBrand = 0;
const brandsFound = new Set();
baseRows.forEach(r => {
  const parts = r.split('", "');
  if (parts.length >= 8) {
    const b = parts[parts.length - 1].replace(/["\}],?$/, '').trim();
    if (b && b !== 'null') {
      withBrand++;
      brandsFound.add(b);
    }
  }
});
console.log('Rows containing brand names:', withBrand);
console.log('Brands found:', [...brandsFound]);
