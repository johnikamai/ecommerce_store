import photos from './productPhotos.json' with { type: 'json' };

// "Cotton T-Shirt Classic" and "Cotton T-Shirt" are the same product with a
// marketing suffix, so the base name is tried as well.
const SUFFIX = / (Classic|Lite|Pro|Ultra|Max)$/;

/**
 * The curated photo for a product, chosen by name.
 *
 * This exists because the database still holds a placeholder or a third-party
 * URL for a number of products, and the reviewed photo set lives in the
 * frontend. A name lookup fixes those rows without waiting for a backend
 * redeploy to rewrite them.
 *
 * Only paths in productPhotos.json are ever returned, and every one of those
 * points into the attributed set recorded in CREDITS.md. Returns null when there
 * is no curated photo, so callers can fall back rather than render a guess.
 */
export function productPhoto(product) {
  const name = product?.name?.trim() || '';
  if (!name) return null;
  return photos[name] || photos[name.replace(SUFFIX, '')] || null;
}