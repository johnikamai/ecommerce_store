/**
 * Product image fallback utilities.
 *
 * Priority (handled in ProductImage.jsx):
 *   1. Curated photo from productPhotos.json
 *   2. Real photo URL stored on the product
 *   3. SVG tile - last resort, no network required (offline-friendly)
 *
 * The tile is deliberately designed to look like a branded placeholder rather
 * than a missing image: a category-coded gradient, a soft card, a large glyph
 * and the product name. A store with a mix of photos and these tiles reads as
 * intentional, whereas a blank/grey box reads as broken.
 */

/**
 * Category palette + glyph for the generated tile.
 *
 * Keys are matched loosely, because catalogue categories are longer than the
 * short keys here ("Home & Living", "Books & Stationery", "Groceries & Food",
 * "Toys & Kids"). `artFor` does a case-insensitive prefix/either-contains match
 * so every real category lands on a sensible palette.
 */
const CATEGORY_ART = {
  electronics: { from: '#7C6AE8', to: '#4B3AA8', glyph: '⚡', ink: 'rgba(255,255,255,0.16)' },
  fashion: { from: '#C98BB9', to: '#8E3F73', glyph: '👗', ink: 'rgba(255,255,255,0.16)' },
  home: { from: '#8FB3D9', to: '#4F6E8F', glyph: '🏠', ink: 'rgba(255,255,255,0.16)' },
  books: { from: '#D9B88F', to: '#9C7546', glyph: '📚', ink: 'rgba(255,255,255,0.18)' },
  stationery: { from: '#D9B88F', to: '#9C7546', glyph: '📚', ink: 'rgba(255,255,255,0.18)' },
  sports: { from: '#8FD9B3', to: '#3F8F68', glyph: '🏅', ink: 'rgba(255,255,255,0.16)' },
  toys: { from: '#D98F8F', to: '#9C4A4A', glyph: '🧸', ink: 'rgba(255,255,255,0.16)' },
  beauty: { from: '#F6A6C1', to: '#C25F82', glyph: '💄', ink: 'rgba(255,255,255,0.18)' },
  grocery: { from: '#8FD98F', to: '#3F8F3F', glyph: '🛒', ink: 'rgba(255,255,255,0.16)' },
  food: { from: '#8FD98F', to: '#3F8F3F', glyph: '🛒', ink: 'rgba(255,255,255,0.16)' },
  automotive: { from: '#9AA6B2', to: '#4C5966', glyph: '🚗', ink: 'rgba(255,255,255,0.16)' },
  pets: { from: '#E0B48F', to: '#A06A3C', glyph: '🐾', ink: 'rgba(255,255,255,0.18)' },
};
const DEFAULT_ART = { from: '#7C6AE8', to: '#4B3AA8', glyph: '🛍️', ink: 'rgba(255,255,255,0.16)' };

function artFor(cat) {
  const key = String(cat || '').trim().toLowerCase();
  if (!key) return DEFAULT_ART;
  if (CATEGORY_ART[key]) return CATEGORY_ART[key];
  for (const [name, art] of Object.entries(CATEGORY_ART)) {
    if (key.startsWith(name) || key.includes(name)) return art;
  }
  return DEFAULT_ART;
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

  const { from, to, glyph, ink } = artFor(product.category);
  const linesArr = wrap(product.name, 20, 3);
  const nameFontSize = linesArr.length > 2 ? 30 : 34;
  const blockHeight = linesArr.length * (nameFontSize + 12);
  const firstBaseline = 372 - blockHeight / 2 + nameFontSize;
  const uid = 't' + Math.abs(hash(key)).toString(36);

  const svg =
    "<svg xmlns='http://www.w3.org/2000/svg' width='600' height='600' viewBox='0 0 600 600'>" +
    "<defs>" +
    "<linearGradient id='" + uid + "g' x1='0' y1='0' x2='1' y2='1'>" +
    "<stop offset='0%' stop-color='" + from + "'/>" +
    "<stop offset='100%' stop-color='" + to + "'/>" +
    "</linearGradient>" +
    "<radialGradient id='" + uid + "r' cx='0.5' cy='0.34' r='0.75'>" +
    "<stop offset='0%' stop-color='rgba(255,255,255,0.28)'/>" +
    "<stop offset='60%' stop-color='rgba(255,255,255,0.06)'/>" +
    "<stop offset='100%' stop-color='rgba(0,0,0,0.14)'/>" +
    "</radialGradient>" +
    "</defs>" +
    "<rect width='600' height='600' fill='url(#" + uid + "g)'/>" +
    "<rect width='600' height='600' fill='url(#" + uid + "r)'/>" +
    // decorative oversized glyph, cropped, adds texture without noise
    "<text x='300' y='330' font-size='300' text-anchor='middle' fill='" + ink + "'>" +
    escapeXml(glyph) + "</text>" +
    // frosted card holding glyph + name
    "<rect x='96' y='150' width='408' height='300' rx='28' fill='rgba(255,255,255,0.14)' " +
    "stroke='rgba(255,255,255,0.35)' stroke-width='1.5'/>" +
    "<text x='300' y='252' font-size='96' text-anchor='middle' fill='white' opacity='0.95'>" +
    escapeXml(glyph) + "</text>" +
    "<text x='300' y='" + firstBaseline + "' font-size='" + nameFontSize +
    "' font-family='sans-serif' font-weight='700' fill='white' text-anchor='middle'>" +
    linesArr
      .map((l, i) => "<tspan x='300' dy='" + (i === 0 ? 0 : nameFontSize + 12) + "'>" + escapeXml(l) + "</tspan>")
      .join('') +
    "</text>" +
    // store wordmark so the tile looks owned, not blank
    "<text x='300' y='562' font-size='22' font-family='sans-serif' font-weight='600' " +
    "letter-spacing='4' text-anchor='middle' fill='rgba(255,255,255,0.7)'>SHOPEASE</text>" +
    "</svg>";

  const uri = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  cache.set(key, uri);
  return uri;
}

function hash(str) {
  let h = 0;
  for (let i = 0; i < str.length; i++) {
    h = (h << 5) - h + str.charCodeAt(i);
    h |= 0;
  }
  return h;
}

export default productTileDataUri;
