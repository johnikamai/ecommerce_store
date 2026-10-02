import { useState } from 'react';

/**
 * Product image with a reliable fallback.
 *
 * A broken or missing URL falls back to the brand-tinted initial tile instead of
 * the browser's broken-image glyph. The failed state is tracked in React state
 * rather than by mutating the DOM, so the fallback actually re-renders (the
 * previous inline `style.display = 'none'` left the fallback permanently hidden
 * behind its own Tailwind `hidden` class).
 */
export default function ProductImage({ product, className = '', alt }) {
  const [failed, setFailed] = useState(false);
  const src = product?.imageUrl;
  const showImage = Boolean(src) && !failed;

  if (showImage) {
    return (
      <img
        src={src}
        alt={alt ?? product.name}
        loading="lazy"
        onError={() => setFailed(true)}
        className={className}
      />
    );
  }

  // Normal flow (not absolute) so this also fills the detail-page gallery,
  // whose container is not position:relative.
  return (
    <div className={`w-full h-full flex items-center justify-center ${className}`}>
      <div className="w-16 h-16 rounded-[var(--radius-lg)] bg-gradient-hero flex items-center justify-center font-[family-name:var(--font-heading)] font-bold text-[var(--color-primary)] text-2xl group-hover:scale-105 transition-transform duration-[var(--transition-base)]">
        {(product?.name || '?').charAt(0)}
      </div>
    </div>
  );
}