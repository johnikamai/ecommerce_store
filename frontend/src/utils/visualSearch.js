import { productPhoto } from './productPhoto.mjs';
import { pixelDescriptor, imageDistance } from './visualPixels.js';
const descriptors = new Map();
export async function extractImageFeatures(image) {
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 16;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  context.fillStyle = '#fff';
  context.fillRect(0, 0, 16, 16);
  context.drawImage(image, 0, 0, 16, 16);
  return pixelDescriptor(context.getImageData(0, 0, 16, 16).data);
}
function descriptor(url) {
  if (!descriptors.has(url)) descriptors.set(url, new Promise((resolve, reject) => {
    const image = new Image();
    image.crossOrigin = 'anonymous';
    const timeout = setTimeout(() => reject(new Error('Image unavailable')), 8000);
    image.onload = async () => {
      clearTimeout(timeout);
      try { resolve(await extractImageFeatures(image)); } catch (error) { reject(error); }
    };
    image.onerror = () => { clearTimeout(timeout); reject(new Error('Image unavailable')); };
    image.src = url;
  }));
  return descriptors.get(url);
}
export async function rankProductsByVisualSimilarity(query, catalog) {
  const candidates = catalog.map(product => ({ product, curated: Boolean(productPhoto(product)), url: productPhoto(product) || product.imageUrl }))
    .filter(({ url, curated }) => url && (curated || (!url.includes('placehold.co') && !/^\/?images\/\d+\./.test(url))));
  const queue = [...new Set(candidates.map(c => c.url))];
  const features = new Map();
  await Promise.all(Array.from({ length: 8 }, async () => {
    while (queue.length) {
      const url = queue.shift();
      try { features.set(url, await descriptor(url)); } catch { /* Skip unreadable images; never invent descriptors. */ }
    }
  }));
  return candidates.filter(c => features.has(c.url))
    .map(({ product, url }) => ({ product, distance: imageDistance(query, features.get(url)) }))
    .sort((a, b) => a.distance - b.distance || a.product.id - b.product.id).slice(0, 8);
}
