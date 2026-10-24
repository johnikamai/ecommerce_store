const fs = require('fs');

const seeder = fs.readFileSync('backend/src/main/java/com/ecommerce/ecommerce_system/config/ProductSeeder.java', 'utf8');

const imgMatches = seeder.match(/\/images\/[a-zA-Z0-9_\-\.]+/g) || [];
console.log('Matches with /images/:', imgMatches.length);
if (imgMatches.length > 0) {
  console.log('Sample /images/ in seeder:', imgMatches.slice(0, 10));
}

// Check where imageUrl is set in ProductSeeder
const imgLines = seeder.split('\n').filter(l => l.includes('setImageUrl'));
console.log('Lines setting imageUrl in ProductSeeder:', imgLines);
