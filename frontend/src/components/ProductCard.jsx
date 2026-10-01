import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Heart, ShoppingCart, Bell } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useCart } from '../context/CartContext';
import { getCustomerId } from '../utils/customer';

const SUSTAIN_COLORS = {
  'Eco-Friendly': 'text-[var(--color-success)]',
  'Moderate Impact': 'text-[var(--color-warning)]',
  'High Impact': 'text-[var(--color-error)]',
  'Not Rated': 'text-[var(--color-text-muted)]',
};

const SUSTAIN_BG = {
  'Eco-Friendly': 'bg-[var(--color-success-bg)]',
  'Moderate Impact': 'bg-[var(--color-warning-bg)]',
  'High Impact': 'bg-[var(--color-error-bg)]',
  'Not Rated': 'bg-[var(--color-card-bg-tint)]',
};

function Stars({ rating }) {
  return (
    <div className="flex items-center gap-0.5">
      {[1, 2, 3, 4, 5].map((star) => (
        <span
          key={star}
          className={star <= Math.round(rating || 0) ? 'text-[var(--color-accent)] text-sm' : 'text-[var(--color-border)] text-sm'}
        >
          ★
        </span>
      ))}
    </div>
  );
}

export default function ProductCard({ product, defaultRating, defaultReviewCount }) {
  const { addToCart } = useCart();
  const [quantity, setQuantity] = useState(1);
  const [justAdded, setJustAdded] = useState(false);
  const [isWishlisted, setIsWishlisted] = useState(false);
  const [notifyStatus, setNotifyStatus] = useState('idle');
  const [summary, setSummary] = useState({
    averageRating: defaultRating || 0,
    reviewCount: defaultReviewCount || 0,
  });
  const isOutOfStock = product.stockQuantity === 0;
  const isLowStock = !isOutOfStock && product.stockQuantity <= 5;
  const label = product.sustainabilityLabel || 'Not Rated';
  const score = product.sustainabilityScore;

  useEffect(() => {
    const customerId = getCustomerId();
    if (defaultRating == null && defaultReviewCount == null) {
      axiosClient.get(`/reviews/product/${product.id}/summary`)
        .then((res) => setSummary(res.data))
        .catch(() => {});
    }
    axiosClient.get(`/wishlist/customer/${customerId}`)
      .then((res) => setIsWishlisted(res.data.some((i) => i.product.id === product.id)))
      .catch(() => {});
  }, [product.id]);

  const toggleWishlist = async () => {
    const customerId = getCustomerId();
    try {
      if (isWishlisted) {
        await axiosClient.delete(`/wishlist/customer/${customerId}/product/${product.id}`);
        setIsWishlisted(false);
      } else {
        await axiosClient.post('/wishlist', { customerId: getCustomerId(), productId: product.id });
        setIsWishlisted(true);
      }
    } catch (err) {
      console.error('Wishlist toggle failed');
    }
  };

  const handleAddToCart = () => {
    addToCart(product, parseInt(quantity) || 1);
    setJustAdded(true);
    setTimeout(() => setJustAdded(false), 1200);
  };

  const handleNotify = async () => {
    try {
      await axiosClient.post('/restock-requests', { customer: { id: getCustomerId() }, product: { id: product.id } });
      setNotifyStatus('subscribed');
    } catch (err) {
      setNotifyStatus('duplicate');
    }
  };

  return (
    <div className="group relative flex flex-col bg-[var(--color-card-bg)] rounded-[var(--radius-lg)] shadow-[var(--shadow-sm)] hover:shadow-[var(--shadow-md)] hover:-translate-y-1 transition-all duration-[var(--transition-base)] overflow-hidden">
      {/* Image */}
      <Link to={`/product/${product.id}`} className="relative aspect-square bg-[var(--color-card-bg-tint)] overflow-hidden block">
        {product.imageUrl ? (
          <img
            src={product.imageUrl}
            alt={product.name}
            loading="lazy"
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-[var(--transition-base)]"
            onError={(e) => { e.currentTarget.style.display = 'none'; }}
          />
        ) : null}
        <div className={`${product.imageUrl ? 'hidden' : ''} absolute inset-0 flex items-center justify-center`}>
          <div className="w-16 h-16 rounded-[var(--radius-lg)] bg-gradient-hero flex items-center justify-center font-[family-name:var(--font-heading)] font-bold text-[var(--color-primary)] text-2xl group-hover:scale-105 transition-transform duration-[var(--transition-base)]">
            {product.name.charAt(0)}
          </div>
        </div>

        {isOutOfStock && (
          <span className="absolute top-2 left-2 rounded-full bg-[var(--color-text-muted)] text-white text-xs font-semibold px-2.5 py-1">
            OUT OF STOCK
          </span>
        )}
        {!isOutOfStock && isLowStock && (
          <span className="absolute top-2 left-2 rounded-full bg-[var(--color-warning)] text-white text-xs font-semibold px-2.5 py-1">
            LOW STOCK
          </span>
        )}
      </Link>

      {/* Wishlist */}
      <button
        onClick={toggleWishlist}
        aria-label={isWishlisted ? 'Remove from wishlist' : 'Add to wishlist'}
        className="absolute top-2 right-2 z-10 w-9 h-9 rounded-full bg-[var(--color-surface)] shadow-[var(--shadow-sm)] flex items-center justify-center transition-transform hover:scale-110"
      >
        <Heart size={18} className={isWishlisted ? 'fill-[var(--color-secondary)] text-[var(--color-secondary)]' : 'text-[var(--color-text-muted)]'} />
      </button>

      {/* Body */}
      <div className="p-[var(--space-4)] flex flex-col flex-1">
        <span className="text-[13px] font-medium uppercase tracking-[0.04em] text-[var(--color-text-muted)] mb-1">
          {product.category}
        </span>

        <Link to={`/product/${product.id}`} className="font-[family-name:var(--font-heading)] font-semibold text-[var(--color-text-primary)] text-[16px] leading-[1.3] mb-1 line-clamp-2 hover:text-[var(--color-primary)] transition-colors">
          {product.name}
        </Link>

        <div className="flex items-center gap-2 mb-2">
          <Stars rating={summary.averageRating} />
          <span className="text-xs text-[var(--color-text-muted)]">
            {summary.reviewCount > 0 ? `(${summary.reviewCount})` : '(no reviews)'}
          </span>
        </div>

        {/* Sustainability */}
        <div className="mb-3">
          <div className="flex items-center justify-between mb-1">
            <span className={`text-xs font-medium ${SUSTAIN_COLORS[label]}`}>{label}</span>
            {score != null && <span className="text-xs text-[var(--color-text-muted)]">{score}/100</span>}
          </div>
          {score != null && (
            <div className="h-1.5 rounded-full bg-[var(--color-card-bg-tint)] overflow-hidden">
              <div
                className={`h-full rounded-full ${SUSTAIN_BG[label]}`}
                style={{ width: `${Math.min(100, Math.max(0, score))}%` }}
              />
            </div>
          )}
        </div>

        {/* Price + CTA */}
        <div className="mt-auto">
          <p className="font-[family-name:var(--font-heading)] text-xl font-bold text-[var(--color-text-primary)] mb-3">
            ₹{product.price}
          </p>

          {isOutOfStock ? (
            <button
              onClick={handleNotify}
              disabled={notifyStatus !== 'idle'}
              className="w-full min-h-[44px] rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-primary)] text-[var(--color-primary)] py-2 px-4 text-sm font-semibold hover:bg-[var(--color-card-bg-tint)] transition-colors disabled:opacity-60"
            >
              <span className="inline-flex items-center gap-2">
                <Bell size={16} />
                {notifyStatus === 'subscribed' ? "We'll notify you" : notifyStatus === 'duplicate' ? 'Already subscribed' : 'Notify me when in stock'}
              </span>
            </button>
          ) : (
            <div className="flex items-center gap-2">
              <input
                type="number"
                min="1"
                max={product.stockQuantity}
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                className="w-14 px-2 py-2 text-sm rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)]"
              />
              <button
                onClick={handleAddToCart}
                className="flex-1 min-h-[44px] rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-primary)] text-[var(--color-primary)] py-2 px-4 text-sm font-semibold hover:bg-[var(--color-card-bg-tint)] transition-colors"
              >
                <span className="inline-flex items-center gap-2">
                  {justAdded ? '✓ Added' : (
                    <>
                      <ShoppingCart size={16} /> Add to Cart
                    </>
                  )}
                </span>
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}