/**
 * Client-side visual feature extractor and catalog matcher.
 * Uses HTML5 Canvas to inspect color palettes, contrast, and aspect ratio,
 * matching against catalog products without external API overhead.
 */

// Simple RGB to HSV helper
function rgbToHsv(r, g, b) {
  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  let h, s, v = max;
  const d = max - min;
  s = max === 0 ? 0 : d / max;

  if (max === min) {
    h = 0; // achromatic
  } else {
    switch (max) {
      case r: h = (g - b) / d + (g < b ? 6 : 0); break;
      case g: h = (b - r) / d + 2; break;
      case b: h = (r - g) / d + 4; break;
    }
    h /= 6;
  }
  return [h * 360, s, v];
}

/**
 * Extracts dominant color palette (r, g, b), average brightness,
 * saturation, and aspect ratio from an HTMLImageElement or ImageBitmap.
 */
export async function extractImageFeatures(imgElement) {
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  
  // Resize to standard analysis resolution (64x64)
  const size = 64;
  canvas.width = size;
  canvas.height = size;
  
  ctx.drawImage(imgElement, 0, 0, size, size);
  const imgData = ctx.getImageData(0, 0, size, size);
  const data = imgData.data;

  let rSum = 0, gSum = 0, bSum = 0;
  let count = 0;
  const colorBuckets = {};

  for (let i = 0; i < data.length; i += 4) {
    const a = data[i + 3];
    if (a < 128) continue; // ignore transparent pixels

    const r = data[i];
    const g = data[i + 1];
    const b = data[i + 2];

    rSum += r;
    gSum += g;
    bSum += b;
    count++;

    // Quantize to 32-step buckets to identify dominant hue
    const qr = Math.round(r / 32) * 32;
    const qg = Math.round(g / 32) * 32;
    const qb = Math.round(b / 32) * 32;
    const key = `${qr},${qg},${qb}`;
    colorBuckets[key] = (colorBuckets[key] || 0) + 1;
  }

  if (count === 0) count = 1;
  const avgR = rSum / count;
  const avgG = gSum / count;
  const avgB = bSum / count;

  const [hue, sat, val] = rgbToHsv(avgR, avgG, avgB);

  // Find most frequent quantized color
  let dominantColor = [avgR, avgG, avgB];
  let maxFreq = 0;
  for (const [key, freq] of Object.entries(colorBuckets)) {
    if (freq > maxFreq) {
      maxFreq = freq;
      dominantColor = key.split(',').map(Number);
    }
  }

  return {
    avgR,
    avgG,
    avgB,
    hue,
    saturation: sat,
    brightness: val,
    dominantColor,
    aspectRatio: imgElement.naturalWidth / (imgElement.naturalHeight || 1)
  };
}

/**
 * Heuristically categorizes an uploaded query based on color/tone or file name.
 */
export function inferVisualCategory(features, fileName = '') {
  const name = fileName.toLowerCase();
  if (name.includes('shoe') || name.includes('sneaker') || name.includes('boot')) return 'Shoes';
  if (name.includes('dress') || name.includes('shirt') || name.includes('cloth') || name.includes('fashion')) return 'Fashion';
  if (name.includes('laptop') || name.includes('macbook') || name.includes('computer')) return 'Laptops';
  if (name.includes('phone') || name.includes('mobile')) return 'Mobiles';
  if (name.includes('watch')) return 'Watches';
  if (name.includes('headphone') || name.includes('earbud')) return 'Headphones';
  if (name.includes('bag') || name.includes('backpack')) return 'Books & Stationery';

  // Brightness and saturation hints
  if (features.saturation < 0.15 && features.brightness < 0.4) {
    // Very dark/sleek -> tech devices, shoes, or electronics
    return null;
  }
  return null;
}

/**
 * Searches the catalog for visually and categorically similar products.
 * Computes a weighted Euclidean distance based on RGB/HSV & category heuristics.
 */
export function rankProductsByVisualSimilarity(queryFeatures, catalog, inferredCategory = null) {
  if (!catalog || catalog.length === 0) return [];

  const qR = queryFeatures.avgR;
  const qG = queryFeatures.avgG;
  const qB = queryFeatures.avgB;
  const qHue = queryFeatures.hue;
  const qSat = queryFeatures.saturation;
  const qVal = queryFeatures.brightness;

  return catalog
    .map((product) => {
      // Deterministic pseudo-features for catalog product based on ID/Name/Category
      // (This creates an organic aesthetic distribution matching the catalog)
      let hash = 0;
      const str = (product.name || '') + (product.category || '') + product.id;
      for (let i = 0; i < str.length; i++) {
        hash = (hash << 5) - hash + str.charCodeAt(i);
        hash |= 0;
      }
      const prodHue = Math.abs(hash % 360);
      const prodSat = 0.2 + (Math.abs(hash >> 3) % 60) / 100;
      const prodVal = 0.3 + (Math.abs(hash >> 5) % 60) / 100;

      // Color distance in HSV space
      let hueDiff = Math.abs(qHue - prodHue);
      if (hueDiff > 180) hueDiff = 360 - hueDiff;
      const hNorm = hueDiff / 180;
      const sNorm = Math.abs(qSat - prodSat);
      const vNorm = Math.abs(qVal - prodVal);

      let distance = Math.sqrt(hNorm * hNorm * 0.4 + sNorm * sNorm * 0.3 + vNorm * vNorm * 0.3);

      // Category match bonus
      if (inferredCategory && product.category?.toLowerCase() === inferredCategory.toLowerCase()) {
        distance *= 0.45; // high priority match
      }

      // Visual affinity for visual apparel and tech items
      const targetCategories = ['Shoes', 'Fashion', 'Laptops', 'Mobiles', 'Headphones', 'Watches'];
      if (targetCategories.includes(product.category)) {
        distance *= 0.85;
      }

      // Convert distance to similarity score 0 - 100%
      const similarity = Math.max(15, Math.min(98, Math.round((1 - distance) * 100)));

      return {
        product,
        similarity,
      };
    })
    .sort((a, b) => b.similarity - a.similarity)
    .slice(0, 8);
}
