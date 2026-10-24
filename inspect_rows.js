const fs = require('fs');

const seeder = fs.readFileSync('backend/src/main/java/com/ecommerce/ecommerce_system/config/ProductSeeder.java', 'utf8');

const sampleRows = seeder.split('\n').filter(l => l.trim().startsWith('{"')).slice(0, 5);
sampleRows.forEach(r => console.log(r.trim()));
