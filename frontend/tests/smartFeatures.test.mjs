import test from 'node:test';
import assert from 'node:assert/strict';
import { pixelDescriptor, imageDistance } from '../src/utils/visualPixels.js';
import { generateComparisonVerdict } from '../src/utils/comparisonVerdict.js';

test('identical image pixels rank ahead of different colours without name or category input', () => {
  const red = pixelDescriptor(Uint8Array.from([255,0,0,255,255,0,0,255]));
  const blue = pixelDescriptor(Uint8Array.from([0,0,255,255,0,0,255,255]));
  assert.equal(imageDistance(red,red),0);
  assert(imageDistance(red,blue)>0.5);
});
test('transparency is composited consistently with the white canvas background', () => {
  const transparent = pixelDescriptor(Uint8Array.from([0,0,0,0]));
  const white = pixelDescriptor(Uint8Array.from([255,255,255,255]));
  assert.equal(imageDistance(transparent,white),0);
});
test('comparison uses actual recorded reviews rather than inventing an unrated score', () => {
  const result = generateComparisonVerdict([{id:1,name:'Budget',price:100},{id:2,name:'Rated',price:200,averageRating:4,reviewCount:2}]);
  assert.equal(result.bestPriceProduct.id,1);
  assert.equal(result.topPerf.id,2);
  assert.equal(result.bestValue.id,2);
});
test('comparison handles missing prices and ratings honestly', () => {
  const result=generateComparisonVerdict([{id:1,name:'A'},{id:2,name:'B'}]);
  assert.equal(result.bestPriceProduct,null);
  assert.equal(result.topPerf,null);
  assert.match(result.summary,/no recorded customer ratings/i);
});
