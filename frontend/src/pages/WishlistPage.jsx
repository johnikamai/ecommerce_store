import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, BellRing, ArrowDownRight, Check, AlertCircle, ShoppingCart, Trash2, Tag, ShieldCheck } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useCart } from '../context/CartContext';
import { useLanguage } from '../context/LanguageContext';
import { getCustomerId } from '../utils/customer';
import ProductImage from '../components/ProductImage';
import { getWishlistAlerts, saveWishlistAlert, removeWishlistAlert, evaluatePriceAlert } from '../utils/wishlistAlerts';

function WishlistPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [alerts, setAlerts] = useState({});
  const [editingAlertId, setEditingAlertId] = useState(null);
  const [targetInput, setTargetInput] = useState('');
  const { addToCart } = useCart();
  const { t, formatCurrency } = useLanguage();
  const navigate = useNavigate();

  const fetchWishlist = async () => {
    try {
      const res = await axiosClient.get(`/wishlist/customer/${getCustomerId()}`);
      setItems(res.data);
      const savedAlerts = getWishlistAlerts();
      
      // Auto-record initial price for items missing baseline alert tracking
      res.data.forEach((item) => {
        if (!savedAlerts[item.product.id]) {
          saveWishlistAlert(item.product.id, {
            initialPrice: Number(item.product.price),
            notifyOnDrop: true,
            notifyOnRestock: true,
          });
        }
      });
      setAlerts(getWishlistAlerts());
    } catch (err) {
      console.error('Failed to load wishlist');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchWishlist();
  }, []);

  const handleRemove = async (productId) => {
    try {
      await axiosClient.delete(`/wishlist/customer/${getCustomerId()}/product/${productId}`);
      removeWishlistAlert(productId);
      fetchWishlist();
    } catch (err) {
      console.error('Failed to remove');
    }
  };

  const handleMoveToCart = (product) => {
    addToCart(product, 1);
    handleRemove(product.id);
  };

  const handleSaveTargetAlert = (productId) => {
    const target = parseFloat(targetInput);
    if (!isNaN(target) && target > 0) {
      saveWishlistAlert(productId, { targetPrice: target, notifyOnDrop: true });
      setAlerts(getWishlistAlerts());
      setEditingAlertId(null);
      setTargetInput('');
    }
  };

  const toggleDropAlert = (productId, currentVal) => {
    saveWishlistAlert(productId, { notifyOnDrop: !currentVal });
    setAlerts(getWishlistAlerts());
  };

  const toggleRestockAlert = (productId, currentVal) => {
    saveWishlistAlert(productId, { notifyOnRestock: !currentVal });
    setAlerts(getWishlistAlerts());
  };

  // Count active price drop alerts that fired
  const dropAlertsCount = items.filter((item) => {
    const alertData = alerts[item.product.id];
    const evaluated = evaluatePriceAlert(item.product, alertData);
    return evaluated?.hasDropped || evaluated?.targetMet;
  }).length;

  if (loading) return <p className="max-w-[1320px] mx-auto px-6 py-12">{t('action.loading')}</p>;

  if (items.length === 0) {
    return (
      <div className="max-w-[1320px] mx-auto px-6 py-20 text-center">
        <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-[var(--color-card-bg-tint)] mb-4 text-2xl">
          ♡
        </div>
        <h4 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-1">{t('wishlist.empty.title')}</h4>
        <p className="text-[var(--color-text-muted)] mb-4">{t('wishlist.empty.hint')}</p>
        <button
          onClick={() => navigate('/products')}
          className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
        >
          {t('catalog.titleProducts')}
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-[1320px] mx-auto px-6 py-8">
      {/* Header & Smart Alert Banner */}
      <div className="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <h2 className="font-[family-name:var(--font-heading)] text-[32px] font-bold">
            {t('nav.wishlist')}
          </h2>
          <p className="text-xs text-[var(--color-text-muted)] mt-1">
            Track price changes, restocks, and custom price alerts
          </p>
        </div>

        {dropAlertsCount > 0 && (
          <div className="flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-[var(--color-success-bg)] text-[var(--color-success)] text-xs font-semibold animate-pulse">
            <BellRing size={14} />
            {dropAlertsCount} product{dropAlertsCount > 1 ? 's' : ''} have met your price alert!
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
        {items.map((item) => {
          const product = item.product;
          const alertData = alerts[product.id] || {};
          const evaluated = evaluatePriceAlert(product, alertData);
          const isOutOfStock = product.stockQuantity <= 0;

          return (
            <div
              key={item.id}
              className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] border border-[var(--color-border)] overflow-hidden flex flex-col hover:border-[var(--color-primary)] transition-all"
            >
              {/* Product Visual */}
              <div className="relative aspect-square bg-[var(--color-card-bg-tint)] flex items-center justify-center overflow-hidden">
                <ProductImage product={product} className="w-full h-full object-cover" />
                <button
                  onClick={() => handleRemove(product.id)}
                  className="absolute top-2.5 right-2.5 w-8 h-8 rounded-full bg-white/90 backdrop-blur-sm shadow-[var(--shadow-sm)] flex items-center justify-center text-[var(--color-secondary)] hover:bg-white hover:text-[var(--color-error)] transition-colors"
                  aria-label={t('product.removeFromWishlist')}
                >
                  <Trash2 size={15} />
                </button>

                {/* Status Badges */}
                <div className="absolute top-2.5 left-2.5 flex flex-col gap-1">
                  {evaluated?.hasDropped && (
                    <span className="px-2 py-0.5 rounded-full bg-[var(--color-success)] text-white text-[10px] font-bold flex items-center gap-0.5 shadow-sm">
                      <ArrowDownRight size={11} /> Price dropped {formatCurrency(evaluated.priceDropAmount)}
                    </span>
                  )}
                  {evaluated?.targetMet && (
                    <span className="px-2 py-0.5 rounded-full bg-purple-600 text-white text-[10px] font-bold shadow-sm">
                      Target Met (&le; {formatCurrency(evaluated.targetPrice)})
                    </span>
                  )}
                  {isOutOfStock ? (
                    <span className="px-2 py-0.5 rounded-full bg-[var(--color-error)] text-white text-[10px] font-semibold">
                      Out of stock
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 rounded-full bg-[var(--color-success-bg)] text-[var(--color-success)] text-[10px] font-semibold">
                      In Stock ({product.stockQuantity})
                    </span>
                  )}
                </div>
              </div>

              {/* Product Info & Price Alerts Config */}
              <div className="p-4 flex flex-col flex-1">
                <h3 className="font-[family-name:var(--font-heading)] font-semibold text-sm line-clamp-1 mb-1">
                  {product.name}
                </h3>
                <div className="flex items-baseline gap-2 mb-3">
                  <span className="font-[family-name:var(--font-heading)] text-lg font-bold text-[var(--color-primary)]">
                    {formatCurrency(product.price)}
                  </span>
                  {evaluated?.initialPrice > product.price && (
                    <span className="text-xs line-through text-[var(--color-text-muted)]">
                      {formatCurrency(evaluated.initialPrice)}
                    </span>
                  )}
                </div>

                {/* Smart Price Alert Panel */}
                <div className="rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] p-2.5 mb-4 text-xs space-y-2 border border-[var(--color-border)]">
                  <div className="flex items-center justify-between">
                    <span className="text-[var(--color-text-secondary)] font-medium flex items-center gap-1">
                      <Bell size={12} className="text-[var(--color-primary)]" /> Price Drop Alert
                    </span>
                    <button
                      type="button"
                      onClick={() => toggleDropAlert(product.id, alertData.notifyOnDrop)}
                      className={`text-[10px] font-bold px-2 py-0.5 rounded transition-colors ${
                        alertData.notifyOnDrop !== false
                          ? 'bg-[var(--color-primary)] text-white'
                          : 'bg-[var(--color-border)] text-[var(--color-text-muted)]'
                      }`}
                    >
                      {alertData.notifyOnDrop !== false ? 'Active' : 'Off'}
                    </button>
                  </div>

                  {/* Target Price Configuration */}
                  {editingAlertId === product.id ? (
                    <div className="flex items-center gap-1.5 pt-1">
                      <input
                        type="number"
                        placeholder="Target price (₹)"
                        value={targetInput}
                        onChange={(e) => setTargetInput(e.target.value)}
                        className="w-full bg-[var(--color-surface)] px-2 py-1 rounded border border-[var(--color-border)] text-xs outline-none"
                      />
                      <button
                        type="button"
                        onClick={() => handleSaveTargetAlert(product.id)}
                        className="px-2 py-1 rounded bg-[var(--color-primary)] text-white font-semibold text-[11px]"
                      >
                        Set
                      </button>
                      <button
                        type="button"
                        onClick={() => setEditingAlertId(null)}
                        className="px-1.5 py-1 text-[11px] text-[var(--color-text-muted)]"
                      >
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <div className="flex items-center justify-between text-[11px] text-[var(--color-text-muted)] pt-0.5">
                      <span>
                        Target: {alertData.targetPrice ? formatCurrency(alertData.targetPrice) : 'None set'}
                      </span>
                      <button
                        type="button"
                        onClick={() => {
                          setEditingAlertId(product.id);
                          setTargetInput(alertData.targetPrice ? String(alertData.targetPrice) : '');
                        }}
                        className="text-[var(--color-primary)] font-semibold hover:underline"
                      >
                        {alertData.targetPrice ? 'Change' : 'Set target'}
                      </button>
                    </div>
                  )}
                </div>

                <button
                  onClick={() => handleMoveToCart(product)}
                  disabled={isOutOfStock}
                  className="mt-auto rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white py-2 text-xs font-semibold hover:bg-[var(--color-primary-hover)] transition-colors flex items-center justify-center gap-1.5 disabled:opacity-50"
                >
                  <ShoppingCart size={14} />
                  {isOutOfStock ? t('compare.outOfStock') : t('wishlist.moveToCart')}
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default WishlistPage;
