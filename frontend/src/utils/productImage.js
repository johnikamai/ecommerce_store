/**
 * Product image fallback utilities.
 *
 * Priority (handled in ProductImage.jsx):
 *   1. Curated photo from productPhotos.json
 *   2. Real photo URL stored on the product
 *   3. SVG tile - last resort, no network required (offline-friendly)
 */

/** Brand-aligned gradient per category, with a matching glyph (SVG last-resort only). */
const CATEGORY_ART = {
  Electronics: { from: '#7C6AE8', to: '#5B4BC4', glyph: '⚡' },
  Fashion: { from: '#C98BB9', to: '#A85F92', glyph: '👗' },
  Home: { from: '#8FB3D9', to: '#6C8FAF', glyph: '🏠' },
  Books: { from: '#D9B88F', to: '#B08F6C', glyph: '📚' },
  Sports: { from: '#8FD9B3', to: '#6CB08F', glyph: '🏅' },
  Toys: { from: '#D98F8F', to: '#B06C6C', glyph: '🧸' },
  Beauty: { from: '#F6A6C1', to: '#D88FA8', glyph: '💄' },
  Grocery: { from: '#8FD98F', to: '#6CB06C', glyph: '🛒' },
};
const DEFAULT_ART = { from: '#7C6AE8', to: '#5B4BC4', glyph: '•' };

function artFor(cat) {
  return CATEGORY_ART[cat] || DEFAULT_ART;
}

function escapeXml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function wrap(text, perLine, maxLines) {
  const t = (text || 'Product').trim();
  if (!t) return ['Product'];
  if (t.length <= perLine) return [t];
  const words = t.split(/\s+/);
  const lines = [];
  let cur = '';
  for (let w of words) {
    const candidate = cur ? cur + ' ' + w : w;
    if (candidate.length <= perLine || !cur) {
      cur = candidate;
    } else {
      lines.push(cur);
      cur = w;
      if (lines.length >= maxLines - 1) break;
    }
  }
  if (lines.length < maxLines && cur) lines.push(cur);
  if (lines.length === 0) return [t.slice(0, perLine - 1) + '…'];
  if (lines.length >= maxLines) {
    const last = lines[maxLines - 1];
    lines[maxLines - 1] = last.length > perLine - 1 ? last.slice(0, perLine - 1) + '…' : last + '…';
  }
  return lines;
}

const cache = new Map();

export function productTileDataUri(product) {
  if (!product) return '';
  const key = (product.category || '') + '|' + (product.name || '');
  if (cache.has(key)) return cache.get(key);
  const { from, to, glyph } = artFor(product.category);
  const linesArr = wrap(product.name, 22, 3);
  const nameFontSize = linesArr.length > 2 ? 26 : 30;
  const blockHeight = linesArr.length * (nameFontSize + 8);
  const svg =
    "<svg xmlns='http://www.w3.org/2000/svg' width='600' height='600' viewBox='0 0 600 600'>" +
    "<defs>" +
    "<linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>" +
    "<stop offset='0%' stop-color='" + from + "'/>" +
    "<stop offset='100%' stop-color='" + to + "'/>" +
    "</linearGradient>" +
    "</defs>" +
    "<rect width='600' height='600' fill='url(#g)'/>" +
    "<text x='50' y='80' font-size='60' font-family='sans-serif' fill='white' opacity='0.6'>" + glyph + "</text>" +
    "<text x='50' y='" + (400 - blockHeight/2) + "' font-size='" + nameFontSize + "' font-family='sans-serif' font-weight='600' fill='white' opacity='0.95' text-anchor='start'>" +
    linesArr.map((l,i) => "<tspan x='50' dy='" + (i===0?0:nameFontSize+8) + "'>" + escapeXml(l) + "</tspan>").join('') +
    "</text>" +
    "</svg>";
  const uri = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  cache.set(key, uri);
  return uri;
}

export default productTileDataUri;
