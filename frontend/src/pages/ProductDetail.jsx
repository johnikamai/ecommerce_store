import { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { ShoppingCart, Zap, Heart, Bell, ChevronRight } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useCart } from '../context/CartContext';
import { useLanguage } from '../context/LanguageContext';
import ProductCard from '../components/ProductCard';
import PersonalizedRecommendations from '../components/PersonalizedRecommendations';
import ProductImage from '../components/ProductImage';
import { getCustomerId } from '../utils/customer';

const AVAILABILITY = {
  in: { label: 'product.inStock', cls: 'bg-[var(--color-success-bg)] text-[var(--color-success)]' },
  low: { label: 'product.lowStock', cls: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]' },
  out: { label: 'product.outOfStock', cls: 'bg-[var(--color-error-bg)] text-[var(--color-error)]' },
};

// The API sends the sustainability enum in English; this maps it to both the
// pill colour and a translation key so the row reads correctly in any language.
const SUSTAIN_ROW = {
  'Eco-Friendly': { cls: 'text-[var(--color-success)]', key: 'sustain.ecoFriendly' },
  'Moderate Impact': { cls: 'text-[var(--color-warning)]', key: 'sustain.moderate' },
  'High Impact': { cls: 'text-[var(--color-error)]', key: 'sustain.high' },
  'Not Rated': { cls: 'text-[var(--color-text-muted)]', key: 'sustain.notRated' },
};
const SUSTAIN_DEFAULT = SUSTAIN_ROW['Not Rated'];

const BRANDS = ['Apex', 'Nova', 'Vertex'];

// Category is the API value so the lookup still matches; both halves of each
// pair are translation keys.
const EXTRA_SPECS = {
  'Electronics': [
    ['spec.power', 'spec.electronics.power'],
    ['spec.connectivity', 'spec.electronics.connectivity'],
    ['spec.inTheBox', 'spec.electronics.inTheBox'],
  ],
  'Fashion': [
    ['spec.material', 'spec.fashion.material'],
    ['spec.care', 'spec.fashion.care'],
    ['spec.fit', 'spec.fashion.fit'],
  ],
  'Beauty': [
    ['spec.suitableFor', 'spec.beauty.suitableFor'],
    ['spec.usage', 'spec.beauty.usage'],
    ['spec.shelfLife', 'spec.beauty.shelfLife'],
  ],
  'Home & Living': [
    ['spec.material', 'spec.home.material'],
    ['spec.care', 'spec.home.care'],
    ['spec.assembly', 'spec.home.assembly'],
  ],
  'Sports': [
    ['spec.material', 'spec.sports.material'],
    ['spec.care', 'spec.sports.care'],
    ['spec.usage', 'spec.sports.usage'],
  ],
  'Books & Stationery': [
    ['spec.format', 'spec.books.format'],
    ['spec.material', 'spec.books.material'],
    ['spec.inTheBox', 'spec.books.inTheBox'],
  ],
  'Toys & Kids': [
    ['spec.ageRange', 'spec.toys.ageRange'],
    ['spec.safety', 'spec.toys.safety'],
    ['spec.pieces', 'spec.toys.pieces'],
  ],
  'Groceries & Food': [
    ['spec.package', 'spec.groceries.package'],
    ['spec.storage', 'spec.groceries.storage'],
    ['spec.shelfLife', 'spec.groceries.shelfLife'],
  ],
  'Automotive': [
    ['spec.material', 'spec.automotive.material'],
    ['spec.compatibility', 'spec.automotive.compatibility'],
    ['spec.installation', 'spec.automotive.installation'],
  ],
  'Pets': [
    ['spec.material', 'spec.pets.material'],
    ['spec.care', 'spec.pets.care'],
    ['spec.size', 'spec.pets.size'],
  ],
};

const WARRANTY_POINTS = [
  'product.warranty.defects',
  'product.warranty.replacement',
  'product.warranty.support',
  'product.warranty.proof',
];

// Label key plus a value that is either a translation key or literal text
// (company name, emails), which is filled in after the brand is resolved.
const MANUFACTURER_INFO = [
  ['mfr.brand', ''],
  ['mfr.countryOfOrigin', 'mfr.countryOfOriginValue'],
  ['mfr.marketedBy', 'mfr.marketedByValue'],
  ['mfr.customerCare', 'mfr.customerCareValue'],
  ['mfr.grievanceOfficer', 'mfr.grievanceOfficerValue'],
];

function Stars({ rating, size = 'text-lg' }) {
  return (
    <div className="flex items-center gap-0.5">
      {[1, 2, 3, 4, 5].map((star) => (
        <span key={star} className={`${size} ${star <= Math.round(rating || 0) ? 'text-[var(--color-accent)]' : 'text-[var(--color-border)]'}`}>
          ★
        </span>
      ))}
    </div>
  );
}

export default function ProductDetail() {
  const { id } = useParams();
  const { addToCart } = useCart();
  const { t, formatCurrency } = useLanguage();
  const navigate = useNavigate();
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [justAdded, setJustAdded] = useState(false);
  const [isWishlisted, setIsWishlisted] = useState(false);
  const [notifyStatus, setNotifyStatus] = useState('idle');
  const [summary, setSummary] = useState({ averageRating: 0, reviewCount: 0 });
  const [reviews, setReviews] = useState([]);
  const [related, setRelated] = useState([]);
  const [tab, setTab] = useState('description');
  const [showReviewForm, setShowReviewForm] = useState(false);
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const [reviewMsg, setReviewMsg] = useState('');
  const [editingReviewId, setEditingReviewId] = useState(null);
  const [editRating, setEditRating] = useState(5);
  const [editComment, setEditComment] = useState('');

  useEffect(() => {
    let active = true;
    axiosClient.get(`/products/${id}`)
      .then((res) => { if (active) setProduct(res.data); })
      .catch(() => { if (active) setError(t('product.loadFailed')); })
      .finally(() => { if (active) setLoading(false); });
    axiosClient.get(`/products/${id}/recommendations?limit=4`)
      .then((res) => { if (active) setRelated(res.data); })
      .catch(() => {});
    axiosClient.get(`/reviews/product/${id}/summary`)
      .then((res) => { if (active) setSummary(res.data); })
      .catch(() => {});
    axiosClient.get(`/reviews/product/${id}`)
      .then((res) => { if (active) setReviews(res.data); })
      .catch(() => {});
    axiosClient.get(`/wishlist/customer/${getCustomerId()}`)
      .then((res) => { if (active) setIsWishlisted(res.data.some((i) => i.product.id === Number(id))); })
      .catch(() => {});
    return () => { active = false; };
  }, [id, t]);

  if (loading) {
    return (
      <div className="container-x py-[var(--space-8)]">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 animate-pulse">
          <div className="aspect-square rounded-[var(--radius-xl)] bg-[var(--color-card-bg-tint)]" />
          <div className="space-y-4">
            <div className="h-4 bg-[var(--color-card-bg-tint)] rounded w-1/3" />
            <div className="h-8 bg-[var(--color-card-bg-tint)] rounded w-2/3" />
            <div className="h-6 bg-[var(--color-card-bg-tint)] rounded w-1/2" />
            <div className="h-24 bg-[var(--color-card-bg-tint)] rounded w-full" />
          </div>
        </div>
      </div>
    );
  }

  if (error || !product) {
    return (
      <div className="container-x py-20 text-center">
        <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-[var(--color-error-bg)] mb-4 text-2xl">!</div>
        <h4 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-1">{t('common.somethingWentWrong')}</h4>
          <p className="text-[var(--color-text-muted)] mb-4">{error || t('product.notFound')}</p>
          <Link to="/products" className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors">
            {t('product.backToProducts')}
          </Link>
      </div>
    );
  }

  const isOutOfStock = product.stockQuantity === 0;
  const isLowStock = !isOutOfStock && product.stockQuantity <= 5;
  const avail = isOutOfStock ? AVAILABILITY.out : isLowStock ? AVAILABILITY.low : AVAILABILITY.in;
  const availLabel = t(avail.label);
  const label = product.sustainabilityLabel || 'Not Rated';
  const sustain = SUSTAIN_ROW[label] || SUSTAIN_DEFAULT;
  const sustainText = t(sustain.key);
  const score = product.sustainabilityScore;

  const brand = BRANDS.find((b) => product.name.startsWith(b)) || 'ShopEase';
  // Every cell is [labelKey, displayText] with the value already resolved, so
  // the table renders identically in every language.
  const specs = [
    ['spec.brand', brand],
    ['spec.category', product.category],
    ['spec.itemCode', `SHOP-${product.id}`],
    ['spec.availability', availLabel],
    ['spec.stock', t('spec.units', { count: product.stockQuantity })],
    ['spec.price', formatCurrency(product.price)],
    ['spec.sustainability', `${sustainText}${score != null ? ` (${score}/100)` : ''}`],
    ...(EXTRA_SPECS[product.category] || []).map(([labelKey, valueKey]) => [labelKey, t(valueKey)]),
  ];
  const warranty = WARRANTY_POINTS.map((key) => t(key));
  const manufacturer = MANUFACTURER_INFO.map(([labelKey, value]) => [
    labelKey,
    labelKey === 'mfr.brand' ? brand : t(value),
  ]);

  const toggleWishlist = async () => {
    try {
      if (isWishlisted) {
        await axiosClient.delete(`/wishlist/customer/${getCustomerId()}/product/${product.id}`);
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

  const handleBuyNow = () => {
    addToCart(product, parseInt(quantity) || 1);
    navigate('/cart');
  };

  const handleNotify = async () => {
    try {
      await axiosClient.post('/restock-requests', { customer: { id: getCustomerId() }, product: { id: product.id } });
      setNotifyStatus('subscribed');
    } catch (err) {
      setNotifyStatus('duplicate');
    }
  };

  const refreshReviews = async () => {
    const [s, r] = await Promise.all([
      axiosClient.get(`/reviews/product/${product.id}/summary`),
      axiosClient.get(`/reviews/product/${product.id}`),
    ]);
    setSummary(s.data);
    setReviews(r.data);
  };

  const handleSubmitReview = async (e) => {
    e.preventDefault();
    try {
      await axiosClient.post('/reviews', { product: { id: product.id }, customer: { id: getCustomerId() }, rating, comment });
      setComment('');
      setShowReviewForm(false);
      setReviewMsg({ ok: true, value: 'product.review.submitted' });
      setTimeout(() => setReviewMsg(null), 2500);
      await refreshReviews();
    } catch (err) {
      const msg = err.response?.data || 'product.review.submitFailed';
      setReviewMsg({ ok: false, value: msg });
      setShowReviewForm(false);
    }
  };

  const startEdit = (r) => {
    setEditingReviewId(r.id);
    setEditRating(r.rating || 5);
    setEditComment(r.comment || '');
    setReviewMsg(null);
  };

  const handleEditReview = async (e) => {
    e.preventDefault();
    try {
      await axiosClient.put(`/reviews/${editingReviewId}`, { customerId: getCustomerId(), rating: editRating, comment: editComment });
      setEditingReviewId(null);
      setReviewMsg({ ok: true, value: 'product.review.updated' });
      setTimeout(() => setReviewMsg(null), 2500);
      await refreshReviews();
    } catch (err) {
      setReviewMsg({ ok: false, value: err.response?.data || 'product.review.updateFailed' });
    }
  };

  const handleDeleteReview = async (r) => {
    if (!window.confirm(t('product.review.deleteConfirm'))) return;
    try {
      await axiosClient.delete(`/reviews/${r.id}`, { data: { customerId: getCustomerId() } });
      setReviewMsg({ ok: true, value: 'product.review.deleted' });
      setTimeout(() => setReviewMsg(null), 2500);
      await refreshReviews();
    } catch (err) {
      setReviewMsg({ ok: false, value: err.response?.data || 'product.review.deleteFailed' });
    }
  };

  return (
    <div className="container-x py-[var(--space-8)]">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-1.5 text-sm text-[var(--color-text-muted)] mb-6">
        <Link to="/products" className="hover:text-[var(--color-primary)]">{t('nav.shop')}</Link>
        <ChevronRight size={14} />
        <span className="text-[var(--color-text-secondary)]">{product.category}</span>
        <ChevronRight size={14} />
        <span className="text-[var(--color-text-secondary)]">{product.name}</span>
      </nav>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-[var(--space-9)]">
        {/* Gallery */}
        <div className="rounded-[var(--radius-xl)] bg-gradient-hero aspect-square flex items-center justify-center overflow-hidden">
          <ProductImage product={product} className="w-full h-full object-cover" />
        </div>

        {/* Info */}
        <div>
          <span className="text-[13px] font-medium uppercase tracking-[0.04em] text-[var(--color-text-muted)]">
            {product.category}
          </span>
          <h2 className="font-[family-name:var(--font-heading)] text-[32px] font-bold mt-1">{product.name}</h2>

          <div className="flex items-center gap-2 mt-2 mb-4">
            <Stars rating={summary.averageRating} size="text-lg" />
            <span className="text-sm text-[var(--color-text-muted)]">
              {summary.reviewCount > 0
                ? t('product.reviewsCount', { count: summary.reviewCount, rating: summary.averageRating })
                : t('product.noReviewsYet')}
            </span>
          </div>

          <p className="font-[family-name:var(--font-heading)] text-[28px] font-bold mb-1">{formatCurrency(product.price)}</p>
          <p className="text-xs text-[var(--color-text-muted)] mb-4">{t('product.inclusiveOfTaxes')}</p>

          <p className="text-[var(--color-text-secondary)] text-[17px] leading-[1.5] mb-5">
            {product.description}
          </p>

          <div className="flex flex-wrap gap-2 mb-4">
                <span className={`rounded-full px-3 py-1 text-xs font-semibold ${avail.cls}`}>{availLabel}</span>
            <span className={`rounded-full px-3 py-1 text-xs font-semibold ${sustain.cls} bg-[var(--color-card-bg-tint)]`}>
              {t('product.sustainability', { label: sustainText })}{score != null ? ` (${score}/100)` : ''}
            </span>
            {isWishlisted && <span className="rounded-full px-3 py-1 text-xs font-semibold bg-[var(--color-secondary)] text-white">{t('product.inWishlist')}</span>}
          </div>

          {score != null && (
            <div className="h-2 rounded-full bg-[var(--color-card-bg-tint)] overflow-hidden mb-5">
              <div
                className="h-full rounded-full"
                style={{ width: `${Math.min(100, Math.max(0, score))}%`, backgroundColor: score >= 80 ? 'var(--color-success)' : score >= 50 ? 'var(--color-warning)' : 'var(--color-error)' }}
              />
            </div>
          )}

          {/* Qty + actions */}
          <div className="flex items-center gap-3 mb-4">
            <div className="flex items-center border-[1.5px] border-[var(--color-border)] rounded-[var(--radius-md)] overflow-hidden">
              <button onClick={() => setQuantity(Math.max(1, quantity - 1))} className="w-10 h-11 text-lg text-[var(--color-text-secondary)] hover:bg-[var(--color-card-bg-tint)]">−</button>
              <input
                type="number"
                min="1"
                max={product.stockQuantity || 1}
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                className="w-12 h-11 text-center outline-none border-x-[1.5px] border-[var(--color-border)] text-sm"
              />
              <button onClick={() => setQuantity(Math.min(product.stockQuantity || 1, quantity + 1))} className="w-10 h-11 text-lg text-[var(--color-text-secondary)] hover:bg-[var(--color-card-bg-tint)]">+</button>
            </div>
          </div>

          <div className="flex flex-col sm:flex-row gap-3 mb-3">
            {isOutOfStock ? (
              <button
                onClick={handleNotify}
                disabled={notifyStatus !== 'idle'}
                className="flex-1 min-h-[52px] rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-6 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-60"
              >
                <span className="inline-flex items-center justify-center gap-2">
                  <Bell size={18} />
                    {notifyStatus === 'subscribed'
                      ? t('product.willNotify')
                      : notifyStatus === 'duplicate'
                        ? t('product.alreadySubscribed')
                        : t('product.notifyMe')}
                </span>
              </button>
            ) : (
              <>
                <button onClick={handleAddToCart} className="flex-1 min-h-[52px] rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-6 text-sm font-semibold hover:bg-[var(--color-primary-hover)] hover:shadow-[var(--shadow-md)] hover:-translate-y-0.5 transition-all">
                  {justAdded ? t('product.addedToCart') : (
                    <span className="inline-flex items-center justify-center gap-2"><ShoppingCart size={18} /> {t('product.addToCart')}</span>
                  )}
                </button>
                <button onClick={handleBuyNow} className="flex-1 min-h-[52px] rounded-[var(--radius-md)] bg-gradient-premium text-white px-6 text-sm font-semibold hover:opacity-90 hover:shadow-[var(--shadow-md)] hover:-translate-y-0.5 transition-all">
                  <span className="inline-flex items-center justify-center gap-2"><Zap size={18} /> {t('product.buyNow')}</span>
                </button>
              </>
            )}
          </div>

          <button onClick={toggleWishlist} className="inline-flex items-center gap-2 text-sm text-[var(--color-text-secondary)] hover:text-[var(--color-secondary)] transition-colors">
            <Heart size={18} className={isWishlisted ? 'fill-[var(--color-secondary)] text-[var(--color-secondary)]' : ''} />
            {isWishlisted ? t('product.removeFromWishlist') : t('product.addToWishlist')}
          </button>
        </div>
      </div>

      {/* Below fold: tabs */}
      <div className="rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-6 mb-[var(--space-9)]">
        <div className="flex gap-1 sm:gap-6 border-b border-[var(--color-border)] mb-5 overflow-x-auto">
          {[
            ['showcase', 'product.tab.showcase'],
            ['description', 'product.tab.description'],
            ['specifications', 'product.tab.specifications'],
            ['warranty', 'product.tab.warranty'],
            ['manufacturer', 'product.tab.manufacturer'],
            ['reviews', 'product.tab.reviews'],
          ].map(([tabId, labelKey]) => (
            <button
              key={tabId}
              onClick={() => setTab(tabId)}
              className={`whitespace-nowrap pb-3 text-sm font-semibold border-b-2 -mb-px transition-colors ${
                tab === tabId ? 'border-[var(--color-primary)] text-[var(--color-primary)]' : 'border-transparent text-[var(--color-text-muted)] hover:text-[var(--color-text-secondary)]'
              }`}
            >
              {tabId === 'reviews'
                ? t('product.tab.reviews', { count: summary.reviewCount })
                : t(labelKey)}
            </button>
          ))}
        </div>

        {tab === 'showcase' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="rounded-[var(--radius-xl)] bg-gradient-hero aspect-square flex items-center justify-center overflow-hidden">
              <ProductImage product={product} className="w-full h-full object-cover" />
            </div>
            <div className="flex flex-col justify-center">
              <h4 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-2">{t('product.showcase')}</h4>
              <p className="text-[var(--color-text-secondary)] text-[15px] leading-[1.6] mb-4">
                {product.description}
              </p>
              <div className="flex flex-wrap gap-2">
            <span className={`rounded-full px-3 py-1 text-xs font-semibold ${avail.cls}`}>{availLabel}</span>
                <span className={`rounded-full px-3 py-1 text-xs font-semibold ${sustain.cls} bg-[var(--color-card-bg-tint)]`}>
                  {t('product.sustainability', { label: sustainText })}{score != null ? ` (${score}/100)` : ''}
                </span>
              </div>
            </div>
          </div>
        )}

        {tab === 'description' && (
          <div>
            <p className="text-[var(--color-text-secondary)] mb-2">{product.description}</p>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4">
              {[
                ['spec.category', product.category],
                ['spec.stock', String(product.stockQuantity ?? 0)],
                ['spec.sustainability', `${sustainText}${score != null ? ` ${score}/100` : ''}`],
                ['spec.price', formatCurrency(product.price)],
              ].map(([labelKey, v]) => (
                <div key={labelKey} className="rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] p-3">
                  <p className="text-xs text-[var(--color-text-muted)]">{t(labelKey)}</p>
                  <p className="text-sm font-semibold mt-0.5">{v}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {tab === 'specifications' && (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {specs.map(([labelKey, v]) => (
              <div key={labelKey} className="flex items-center justify-between gap-4 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] p-4">
                <span className="text-sm text-[var(--color-text-muted)]">{t(labelKey)}</span>
                <span className="text-sm font-semibold text-right">{v}</span>
              </div>
            ))}
          </div>
        )}

        {tab === 'warranty' && (
          <div>
            <h4 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-2">{t('product.warrantyTitle')}</h4>
            <ul className="space-y-2.5">
              {warranty.map((w) => (
                <li key={w} className="flex items-start gap-2.5 text-[15px] text-[var(--color-text-secondary)] leading-[1.5]">
                  <span className="mt-1.5 w-1.5 h-1.5 rounded-full bg-[var(--color-primary)] shrink-0" />
                  {w}
                </li>
              ))}
            </ul>
            <p className="text-sm text-[var(--color-text-muted)] mt-4">
              {t('product.warrantyNote')}
            </p>
          </div>
        )}

        {tab === 'manufacturer' && (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {manufacturer.map(([labelKey, v]) => (
              <div key={labelKey} className="rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] p-4">
                <p className="text-xs text-[var(--color-text-muted)]">{t(labelKey)}</p>
                <p className="text-sm font-semibold mt-0.5">{v}</p>
              </div>
            ))}
          </div>
        )}

        {tab === 'reviews' && (
          <div>
            <button
              onClick={() => setShowReviewForm(!showReviewForm)}
              className="mb-4 inline-flex items-center min-h-[44px] rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
            >
              {showReviewForm ? t('common.cancel') : t('product.writeReview')}
            </button>
            {reviewMsg && (
              <p className={`text-sm mb-3 ${reviewMsg.ok ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'}`}>
                {t(reviewMsg.value)}
              </p>
            )}

            {showReviewForm && (
              <form onSubmit={handleSubmitReview} className="mb-5 p-4 rounded-[var(--radius-lg)] bg-[var(--color-card-bg-tint)] max-w-md">
                <div className="flex gap-1 mb-2">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <button key={star} type="button" onClick={() => setRating(star)} className={star <= rating ? 'text-[var(--color-accent)] text-2xl' : 'text-[var(--color-border)] text-2xl'}>
                      ★
                    </button>
                  ))}
                </div>
                <textarea
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  placeholder={t('product.reviewPlaceholder')}
                  rows={3}
                  className="w-full p-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm"
                />
                <button type="submit" className="mt-2 w-full rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors">
                  {t('product.submitReview')}
                </button>
              </form>
            )}

            {reviews.length === 0 ? (
              <p className="text-sm text-[var(--color-text-muted)]">{t('product.noReviewsFirst')}</p>
            ) : (
              <ul className="space-y-3">
                {reviews.map((r) => {
                  const isMine = r.customer && r.customer.id === getCustomerId();
                  return (
                    <li key={r.id} className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg-tint)] p-4">
                      {editingReviewId === r.id ? (
                        <form onSubmit={handleEditReview} className="max-w-md">
                          <div className="flex gap-1 mb-2">
                            {[1, 2, 3, 4, 5].map((star) => (
                              <button
                                key={star}
                                type="button"
                                onClick={() => setEditRating(star)}
                                className={star <= editRating ? 'text-[var(--color-accent)] text-2xl' : 'text-[var(--color-border)] text-2xl'}
                              >
                                ★
                              </button>
                            ))}
                          </div>
                          <textarea
                            value={editComment}
                            onChange={(e) => setEditComment(e.target.value)}
                            placeholder={t('product.editReviewPlaceholder')}
                            rows={2}
                            className="w-full p-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm"
                          />
                          <div className="flex gap-2 mt-2">
                            <button type="submit" className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-4 py-2 text-xs font-semibold hover:bg-[var(--color-primary-hover)] transition-colors">
                              {t('product.saveReview')}
                            </button>
                            <button
                              type="button"
                              onClick={() => setEditingReviewId(null)}
                              className="rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-border)] text-[var(--color-text-secondary)] px-4 py-2 text-xs font-semibold hover:bg-[var(--color-card-bg)] transition-colors"
                            >
                              {t('common.cancel')}
                            </button>
                          </div>
                        </form>
                      ) : (
                        <>
                          <div className="flex items-center gap-2 mb-1">
                            <Stars rating={r.rating} size="text-sm" />
                            <span className="text-sm font-medium">{r.customer.name}</span>
                            {isMine && (
                              <span className="text-[11px] text-[var(--color-text-muted)]">{t('product.you')}</span>
                            )}
                          </div>
                          {r.comment && <p className="text-sm text-[var(--color-text-secondary)]">{r.comment}</p>}
                          {isMine && (
                            <div className="flex gap-2 mt-2">
                              <button
                                onClick={() => startEdit(r)}
                                className="text-xs font-semibold text-[var(--color-primary)] hover:underline"
                              >
                                {t('common.edit')}
                              </button>
                              <button
                                onClick={() => handleDeleteReview(r)}
                                className="text-xs font-semibold text-[var(--color-error)] hover:underline"
                              >
                                {t('common.delete')}
                              </button>
                            </div>
                          )}
                        </>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        )}
      </div>

      {/* Co-occurrence picks for this specific product */}
      {related.length > 0 && (
        <div className="mb-[var(--space-9)]">
          <h3 className="font-[family-name:var(--font-heading)] text-[24px] font-semibold mb-5">{t('recs.related')}</h3>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-5">
            {related.map((rec) => (
              <div key={rec.id} className="flex flex-col">
                <ProductCard product={rec} />
                {rec.reasonCode && (
                  <p className="text-[11px] text-[var(--color-text-muted)] mt-1.5 px-1 leading-snug">
                    {t(rec.reasonCode, rec.reasonParams)}
                  </p>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Shopper-level picks, ranked from their own order history */}
      <PersonalizedRecommendations limit={4} />
    </div>
  );
}