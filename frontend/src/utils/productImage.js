/**
 * Locally-generated product artwork.
 *
 * The catalog previously pointed every product at placehold.co. With 600+
 * products that meant 600+ third-party image requests on each catalog page load,
 * which is slow and gets rate-limited (tiles then render blank). Instead each
 * product gets a deterministic SVG tile built here: same product always yields
 * the same artwork, it costs zero network requests, and it cannot break.
 *
 * A real photo URL stored on the product still wins - see ProductImage.
 */

/** Brand-aligned gradient per category, with a matching glyph. */
const CATEGORY_ART = {
  Electronics: { from: '#7C6AE8', to: '#5B4BC4', glyph: '⚡' },
  Fashion: { from: '#C98BB9', to: '#A85F92', glyph: '👗' },
  Beauty: { from: '#E8A08B', to: '#D1785F', glyph: '💄' },
  'Home & Living': { from: '#8BB8C9', to: '#5F97AB', glyph: '🏠' },
  Sports: { from: '#8BC99B', to: '#5FA574', glyph: '🏆' },
  'Books & Stationery': { from: '#E8D48B', to: '#C9AF5A', glyph: '📚' },
  'Toys & Kids': { from: '#9B8BC9', to: '#7766A8', glyph: '🧸' },
  'Groceries & Food': { from: '#E0B27E', to: '#C08F52', glyph: '🛒' },
  Automotive: { from: '#8B9BC9', to: '#6274A8', glyph: '🚗' },
  Pets: { from: '#A9C98B', to: '#84A966', glyph: '🐾' },
};

const FALLBACK_ART = { from: '#9A93C9', to: '#7A72AC', glyph: '🛍️' };

function artFor(category) {
  return CATEGORY_ART[category] || FALLBACK_ART;
}

function escapeXml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/** Greedy wrap so long names stay inside the tile instead of overflowing. */
function wrap(text, perLine, maxLines) {
  const words = String(text ?? '').split(/\s+/).filter(Boolean);
  const lines = [];
  let current = '';
  for (const word of words) {
    const candidate = current ? `${current} ${word}` : word;
    if (candidate.length <= perLine) {
      current = candidate;
    } else {
      if (current) lines.push(current);
      current = word;
      if (lines.length === maxLines) break;
    }
  }
  if (current && lines.length < maxLines) lines.push(current);
  if (lines.length === maxLines && words.join(' ').length > lines.join(' ').length) {
    lines[maxLines - 1] = `${lines[maxLines - 1].slice(0, perLine - 1)}…`;
  }
  return lines;
}

/**
 * Builds an SVG data URI for a product. Cached by name so scrolling a category
 * of 60 tiles does not rebuild the same markup 60 times.
 */
const cache = new Map();

export function productTileDataUri(product) {
  if (!product) return '';

  const key = `${product.category || ''}|${product.name || ''}`;
  if (cache.has(key)) return cache.get(key);

  const { from, to, glyph } = artFor(product.category);
  const lines = wrap(product.name, 22, 3);
  const nameFontSize = lines.length > 2 ? 26 : 30;
  const blockHeight = lines.length * (nameFontSize + 8);

  const text = lines
    .map(
      (line, i) =>
        `<text x="50%" y="${560 - blockHeight + nameFontSize + i * (nameFontSize + 8)}" ` +
        `text-anchor="middle" font-family="Segoe UI, Arial, sans-serif" font-size="${nameFontSize}" ` +
        `font-weight="700" fill="#ffffff">${escapeXml(line)}</text>`
    )
    .join('');

  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 600" width="600" height="600">` +
    `<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">` +
    `<stop offset="0%" stop-color="${from}"/><stop offset="100%" stop-color="${to}"/>` +
    `</linearGradient></defs>` +
    `<rect width="600" height="600" fill="url(#g)"/>` +
    `<circle cx="300" cy="250" r="118" fill="#ffffff" opacity="0.16"/>` +
    `<text x="50%" y="250" text-anchor="middle" dominant-baseline="central" ` +
    `font-size="120">${glyph}</text>` +
    text +
    `</svg>`;

  const uri = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
  cache.set(key, uri);
  return uri;
}

export default productTileDataUri;