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
  const usable = stored && !stored.startsWith(LEGACY_PLACEHOLDER) ? stored : null;
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