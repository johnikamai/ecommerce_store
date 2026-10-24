const fs = require('fs');

const seeder = fs.readFileSync('backend/src/main/java/com/ecommerce/ecommerce_system/config/ProductSeeder.java', 'utf8');

function countRows(block) {
  return block.split('\n').filter(l => l.trim().startsWith('{"')).length;
}

const catBlock = seeder.substring(seeder.indexOf('CATALOG = {'), seeder.indexOf('};', seeder.indexOf('CATALOG = {')));
const extraBlock = seeder.substring(seeder.indexOf('EXTRA_PRODUCTS = {'), seeder.indexOf('};', seeder.indexOf('EXTRA_PRODUCTS = {')));
const showBlock = seeder.substring(seeder.indexOf('SHOWCASE_PRODUCTS = {'), seeder.indexOf('};', seeder.indexOf('SHOWCASE_PRODUCTS = {')));

console.log('CATALOG base items:', countRows(catBlock));
console.log('EXTRA_PRODUCTS items:', countRows(extraBlock));
console.log('SHOWCASE_PRODUCTS items:', countRows(showBlock));

const photos = JSON.parse(fs.readFileSync('frontend/src/utils/productPhotos.json', 'utf8'));
console.log('Total mapped in productPhotos.json:', Object.keys(photos).length);
