const fs = require('fs');

const seeder = fs.readFileSync('backend/src/main/java/com/ecommerce/ecommerce_system/config/ProductSeeder.java', 'utf8');

const line = seeder.split('\n').find(l => l.includes('Wireless Earbuds'));
const parts = line.split(',').map(s => s.trim());
parts.forEach((p, i) => console.log(i + ' = ' + p));
