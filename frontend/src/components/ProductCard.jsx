import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Heart, ShoppingCart, Bell, Scale } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useCart } from '../context/CartContext';
import { useCompare } from '../context/CompareContext';
import { useLanguage } from '../context/LanguageContext';
import { getCustomerId } from '../utils/customer';
import ProductImage from './ProductImage';

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

// The badge arrives from the API as an English enum, so the colour classes are
// keyed by that value while the visible text is looked up by key. Mapping the
// text by value would bake English into the API contract.
const SUSTAIN_LABEL_KEYS = {
  'Eco-Friendly': 'sustain.ecoFriendly',
  'Moderate Impact': 'sustain.moderate',
  'High Impact': 'sustain.high',
  'Not Rated': 'sustain.notRated',
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
  const navigate = useNavigate();
  const { addToCart } = useCart();
  const { t, formatCurrency } = useLanguage();
  const { toggle: toggleCompare, isSelected: isComparing } = useCompare();
  const compareSelected = isComparing(product.id);
  const [compareFullMessage, setCompareFullMessage] = useState('');
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
  const labelText = t(SUSTAIN_LABEL_KEYS[label] || SUSTAIN_LABEL_KEYS['Not Rated']);
  const score = product.sustainabilityScore;

  useEffect(() => {
    const customerId = getCustomerId();
    if (defaultRating == null && defaultReviewCount == null) {
      axiosClient.get(`/reviews/product/${product.id}/summary`)
        .then((res) => setSummary(res.data))
        .catch(() => {});
    }
    if (getCustomerId()) axiosClient.get(`/wishlist/customer/${customerId}`)
      .then((res) => setIsWishlisted(res.data.some((i) => i.product.id === product.id)))
      .catch(() => {});
  }, [product.id]);

  const toggleWishlist = async () => {
    if (!getCustomerId()) { navigate('/login'); return; }
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
    if (!getCustomerId()) { navigate('/login'); return; }
    addToCart(product, parseInt(quantity) || 1);
    setJustAdded(true);
    setTimeout(() => setJustAdded(false), 1200);
  };

  const handleNotify = async () => {
    if (!getCustomerId()) { navigate('/login'); return; }
    try {
      await axiosClient.post('/restock-requests', { customer: { id: getCustomerId() }, product: { id: product.id } });
      setNotifyStatus('subscribed');
    } catch (err) {
      setNotifyStatus('duplicate');
    }
  };

  const handleCompare = () => {
    // The context refuses the fourth pick, so say so rather than letting the
    // button look broken when nothing happens.
    if (toggleCompare(product.id)) {
      setCompareFullMessage('');
      return;
    }
    setCompareFullMessage(t('product.compareLimit'));
    setTimeout(() => setCompareFullMessage(''), 2500);
  };

  return (
    <div className="group relative flex flex-col bg-[var(--color-card-bg)] rounded-[var(--radius-lg)] shadow-[var(--shadow-sm)] hover:shadow-[var(--shadow-md)] hover:-translate-y-1 transition-all duration-[var(--transition-base)] overflow-hidden">
      {/* Image */}
      <Link to={`/product/${product.id}`} className="relative aspect-square bg-[var(--color-card-bg-tint)] overflow-hidden block">
        <ProductImage
          product={product}
          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-[var(--transition-base)]"
        />

        {isOutOfStock && (
          <span className="absolute top-2 left-2 rounded-full bg-[var(--color-text-muted)] text-white text-xs font-semibold px-2.5 py-1 uppercase">
            {t('product.outOfStock')}
          </span>
        )}
        {!isOutOfStock && isLowStock && (
          <span className="absolute top-2 left-2 rounded-full bg-[var(--color-warning)] text-white text-xs font-semibold px-2.5 py-1 uppercase">
            {t('product.lowStock')}
          </span>
        )}
      </Link>

      {/* Wishlist */}
      <button
        onClick={toggleWishlist}
        aria-label={isWishlisted ? t('product.removeFromWishlist') : t('product.addToWishlist')}
        className="absolute top-2 right-2 z-10 w-9 h-9 rounded-full bg-[var(--color-surface)] shadow-[var(--shadow-sm)] flex items-center justify-center transition-transform hover:scale-110"
      >
        <Heart size={18} className={isWishlisted ? 'fill-[var(--color-secondary)] text-[var(--color-secondary)]' : 'text-[var(--color-text-muted)]'} />
      </button>

      {/* Compare tick. Sits under the wishlist heart so the two never overlap. */}
      <button
        onClick={handleCompare}
        aria-pressed={compareSelected}
        aria-label={compareSelected ? t('product.removeFromCompare', { name: product.name }) : t('product.compareNamed', { name: product.name })}
        title={compareSelected ? t('product.removeFromCompareShort') : t('product.addToCompare')}
        className={
          'absolute top-12 right-2 z-10 h-7 px-2 rounded-full bg-[var(--color-surface)] shadow-[var(--shadow-sm)] flex items-center gap-1 text-[11px] font-semibold transition-transform hover:scale-110 ' +
          (compareSelected
            ? 'text-[var(--color-primary)] ring-[1.5px] ring-[var(--color-primary)]'
            : 'text-[var(--color-text-muted)]')
        }
      >
        <Scale size={13} />
        {compareSelected ? t('product.addedShort') : t('product.compare')}
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
            {summary.reviewCount > 0 ? `(${summary.reviewCount})` : t('product.noReviews')}
          </span>
        </div>

        {/* Sustainability */}
        <div className="mb-3">
          <div className="flex items-center justify-between mb-1">
            <span className={`text-xs font-medium ${SUSTAIN_COLORS[label]}`}>{labelText}</span>
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
            {formatCurrency(product.price)}
          </p>

          {isOutOfStock ? (
            <button
              onClick={handleNotify}
              disabled={notifyStatus !== 'idle'}
              className="w-full min-h-[44px] rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-primary)] text-[var(--color-primary)] py-2 px-4 text-sm font-semibold hover:bg-[var(--color-card-bg-tint)] transition-colors disabled:opacity-60"
            >
              <span className="inline-flex items-center gap-2">
                <Bell size={16} />
                {notifyStatus === 'subscribed'
                  ? t('product.willNotify')
                  : notifyStatus === 'duplicate'
                    ? t('product.alreadySubscribed')
                    : t('product.notifyMe')}
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
                  {justAdded ? `\u2713 ${t('product.addedToCart')}` : (
                    <>
                      <ShoppingCart size={16} /> {t('product.addToCart')}
                    </>
                  )}
                </span>
              </button>
            </div>
          )}
        </div>

        {/* Shown when the shopper tries to tick a fourth product. */}
        {compareFullMessage && (
          <p className="absolute bottom-2 left-2 right-2 z-10 text-[11px] font-medium text-[var(--color-error)] bg-[var(--color-error-bg)] rounded-[var(--radius-sm)] px-2 py-1.5">
            {compareFullMessage}
          </p>
        )}
      </div>
    </div>
  );
}