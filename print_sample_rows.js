const fs = require('fs');

const seeder = fs.readFileSync('backend/src/main/java/com/ecommerce/ecommerce_system/config/ProductSeeder.java', 'utf8');

const baseRows = seeder.split('\n').filter(l => l.trim().startsWith('{"'));
console.log('Sample base rows:');
baseRows.slice(0, 15).forEach((r, idx) => console.log(idx + ': ' + r.trim()));
