import photos from './productPhotos.json' with { type: 'json' };

// "Cotton T-Shirt Classic" and "Cotton T-Shirt" are the same product with a
// marketing suffix, so the base name is tried as well.
const SUFFIX = / (Classic|Lite|Pro|Ultra|Max)$/;

/**
 * The photo to show for a product, chosen by name.
 *
 * This exists because the database still holds a placeholder or a third-party
 * URL for a number of products, and the reviewed photo set lives in the
 * frontend. A name lookup fixes those rows without waiting for a backend
 * redeploy to rewrite them.
 *
 * Only paths in productPhotos.json are ever returned. Most resolve into the
 * Openverse set credited at the top of CREDITS.md; the remaining 46 point at
 * legacy seed files and files added alongside the map, and are listed in the
 * "Unconfirmed sources" table at the bottom of that file until their licence is
 * known. Returns null when there is no entry, so callers fall back rather than
 * render a guess.
 */
export function productPhoto(product) {
  const name = product?.name?.trim() || '';
  if (!name) return null;
  return photos[name] || photos[name.replace(SUFFIX, '')] || null;
}