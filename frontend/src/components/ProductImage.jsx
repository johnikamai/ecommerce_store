import { useState } from 'react';
import { productTileDataUri } from '../utils/productImage';
import { productPhoto } from '../utils/productPhoto.mjs';

/**
 * placehold.co URLs were assigned to the whole catalog by an earlier seeder.
 * They are text placeholders, not product photos, so we ignore them and draw
 * our own tile. This keeps the upgrade independent of a backend deploy: the
 * rows still carry those URLs until the seeder cleans them, but the storefront
 * already looks right.
 */
const LEGACY_PLACEHOLDER = 'https://placehold.co/';

/**
 * The local /images/<n>.jpg files are the same kind of artifact. Only the first
 * ~106 were real photos (and those are already covered by the curated map);
 * everything the seeder wrote after that is a flat, near-blank tile, which is
 * why the catalog looked like it ran out of images partway through. Treat them
 * as unusable so the product falls through to the curated photo or, failing
 * that, the generated SVG tile - never a blank box.
 */
const LEGACY_SEED_IMAGE = /^\/?images\/\d+\.(jpe?g|png|webp)$/i;

/** Whether a URL stored on the product is a legacy seeder artifact, not a real photo. */
export function isPlaceholderImage(url) {
  return !!url && (url.startsWith(LEGACY_PLACEHOLDER) || LEGACY_SEED_IMAGE.test(url));
}

/**
 * Product image with a guaranteed visual.
 *
 * Order of preference:
 *   1. the curated photo for this product name (reviewed, attributed)
 *   2. a real photo URL stored on the product
 *   3. a locally-generated SVG tile (no network request, cannot break)
 *
 * The curated set wins over the stored URL because some rows still point at a
 * placeholder or an outside thumbnail even though a reviewed photo exists.
 *
 * Failure is tracked in React state rather than by mutating the DOM, so the
 * fallback actually re-renders. The previous version set style.display='none'
 * on the <img> while the fallback div kept its own Tailwind 'hidden' class,
 * which revealed an empty box instead of anything.
 */
export default function ProductImage({ product, className = '', alt }) {
  const [failedSource, setFailedSource] = useState(null);

  const stored = product?.imageUrl;
  const usable = stored && !isPlaceholderImage(stored) ? stored : null;
  const photo = productPhoto(product) || usable;

  // A source that has already failed must not be retried on the next render,
  // otherwise the fallback never gets a turn.
  const shown = photo && photo !== failedSource ? photo : null;
  const tile = productTileDataUri(product);

  return (
    <img
      src={shown || tile}
      alt={alt ?? product?.name ?? 'Product'}
      loading="lazy"
      onError={() => {
        // Only reachable for a real photo; the data URI cannot fail, but this
        // guards against a browser that blocks data URIs entirely.
        if (shown) setFailedSource(shown);
      }}
      className={className}
    />
  );
}