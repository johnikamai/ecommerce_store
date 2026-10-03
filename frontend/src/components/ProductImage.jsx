import { useState } from 'react';
import { productTileDataUri } from '../utils/productImage';

/**
 * Product image with a guaranteed visual.
 *
 * Order of preference:
 *   1. a real photo URL stored on the product
 *   2. a locally-generated SVG tile (no network request, cannot break)
 *
 * Failure is tracked in React state rather than by mutating the DOM, so the
 * fallback actually re-renders. The previous version set style.display='none'
 * on the <img> while the fallback div kept its own Tailwind 'hidden' class,
 * which revealed an empty box instead of anything.
 */
export default function ProductImage({ product, className = '', alt }) {
  const [failed, setFailed] = useState(false);
  const photo = product?.imageUrl;
  const tile = productTileDataUri(product);

  const src = photo && !failed ? photo : tile;

  return (
    <img
      src={src}
      alt={alt ?? product?.name ?? 'Product'}
      loading="lazy"
      onError={() => {
        // Only meaningful for a real photo; the data URI cannot fail, but this
        // guards against a browser that blocks data URIs entirely.
        if (!failed) setFailed(true);
      }}
      className={className}
    />
  );
}