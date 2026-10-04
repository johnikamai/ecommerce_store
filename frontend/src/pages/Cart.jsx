import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Banknote, CreditCard, Smartphone, MapPin, Plus, LocateFixed, Loader2 } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { useLanguage } from '../context/LanguageContext';
import axiosClient from '../api/axiosClient';
import { getCustomerId } from '../utils/customer';

// labelKey/descKey rather than literal copy so the same list renders in every
// locale. `key` is what handleCheckout actually sends as paymentMethod.
const PAYMENT_METHODS = [
  { key: 'CASH', labelKey: 'cart.payment.cash', descKey: 'cart.payment.cashDesc', icon: Banknote },
  { key: 'UPI', labelKey: 'cart.payment.upi', descKey: 'cart.payment.upiDesc', icon: Smartphone },
  { key: 'CARD', labelKey: 'cart.payment.card', descKey: 'cart.payment.cardDesc', icon: CreditCard },
];

const BUNDLE_TIERS = [
  { products: 2, off: '5%' },
  { products: 3, off: '10%' },
  { products: 4, off: '15%' },
];

function formatAddress(a) {
  const parts = [a.name, a.addressLine, a.city, a.state ? `${a.state} ${a.pincode || ''}`.trim() : a.pincode].filter(Boolean);
  return parts.join(', ') + (a.phone ? ` ${a.phone}` : '');
}

/**
 * Nudges the shopper toward the next bundle tier. Kept inline rather than in
 * components/ because it is only ever used here and reads off cart state.
 */
function BundleProgress() {
  const { distinctProductCount, bundlePercent, bundleDiscount, nextBundleAt } = useCart();
  const { t, formatCurrency } = useLanguage();

  if (distinctProductCount === 0) return null;

  // NB: the lookup parameter is named `tier`, not `t`, so it does not shadow the
  // translator returned by useLanguage above.
  const nextTierOff = BUNDLE_TIERS.find((tier) => tier.products === nextBundleAt)?.off;
  const needed = nextBundleAt - distinctProductCount;

  if (bundlePercent > 0) {
    return (
      <div className="p-4 rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] border-[1.5px] border-[var(--color-primary)] shadow-[var(--shadow-sm)]">
        <p className="text-sm font-semibold text-[var(--color-primary)]">
          {t('cart.bundle.unlocked', { percent: Math.round(bundlePercent * 100), amount: formatCurrency(currentQuote?.bundleDiscount ?? bundleDiscount) })}
        </p>
        <p className="text-xs text-[var(--color-text-muted)] mt-1">
          {nextBundleAt > 0
            ? t(needed > 1 ? 'cart.bundle.addMoreOther' : 'cart.bundle.addMoreOne', { count: needed, off: nextTierOff })
            : t('cart.bundle.bestTier')}
        </p>
      </div>
    );
  }

  return (
    <div className="p-4 rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)]">
      <p className="text-sm font-semibold">{t('cart.bundle.title')}</p>
      <p className="text-xs text-[var(--color-text-muted)] mt-1">
        {t(needed > 1 ? 'cart.bundle.moreOther' : 'cart.bundle.moreOne', { count: needed, off: nextTierOff })}{' '}
        {t('cart.bundle.stacks')}
      </p>
    </div>
  );
}

function Cart() {
  const {
    items,
    removeFromCart,
    updateQuantity,
    clearCart,
    totalPrice,
    bundleDiscount,
    shipping,
    taxAmount,
    grandTotal,
  } = useCart();
  const { t, formatCurrency } = useLanguage();
  const [error, setError] = useState('');
  const [placing, setPlacing] = useState(false);
  const [couponCode, setCouponCode] = useState('');
  const customerId = getCustomerId();
  const navigate = useNavigate();

  // Checkout state
  const [addresses, setAddresses] = useState([]);
  const [selectedAddressId, setSelectedAddressId] = useState(null);
  const [useManual, setUseManual] = useState(false);
  const [manualAddress, setManualAddress] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [locating, setLocating] = useState(false);

  useEffect(() => {
    axiosClient.get(`/addresses/customer/${customerId}`)
      .then((res) => {
        setAddresses(res.data);
        if (res.data.length > 0) {
          const def = res.data.find((a) => a.isDefault) || res.data[0];
          setSelectedAddressId(def.id);
        } else {
          setUseManual(true);
        }
      })
      .catch(() => setUseManual(true));
  }, [customerId]);

  /**
   * Fills the delivery address from the browser's GPS.
   *
   * The coordinates alone are useless to a courier, so they are reversed into a
   * readable address. BigDataCloud is used because it needs no API key and
   * accepts browser calls; if it is unreachable the coordinates are still filled
   * in rather than failing outright, since a rough area beats nothing.
   */
  const detectLocation = () => {
    if (!navigator.geolocation) {
      setError('cart.geo.unsupported');
      return;
    }
    setLocating(true);
    setError('');

    navigator.geolocation.getCurrentPosition(
      async ({ coords }) => {
        const { latitude, longitude } = coords;
        let place = `${latitude.toFixed(5)}, ${longitude.toFixed(5)}`;
        try {
          const res = await fetch(
            `https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${latitude}&longitude=${longitude}&localityLanguage=en`
          );
          if (res.ok) {
            const d = await res.json();
            // The API spells it "postcode" and omits it for some coordinates, so
            // both spellings are checked before the pincode is dropped.
            const pin = d.postcode || d.postalCode || '';
            const area = [d.city || d.locality, d.principalSubdivision, pin]
              .filter(Boolean)
              .join(', ');
            // Only replace the field if the lookup gave us something readable.
            if (area) place = area;
          }
        } catch {
          // Keep the coordinates; the customer can edit from here.
        }
        setManualAddress(place);
        setLocating(false);
      },
      () => {
        setLocating(false);
        setError('cart.geo.failed');
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 }
    );
  };

  const resolvedAddress = useManual || addresses.length === 0
    ? manualAddress
    : formatAddress(addresses.find((a) => a.id === selectedAddressId));

  const [quote, setQuote] = useState(null);
  const [quoteError, setQuoteError] = useState('');
  const [quoteRevision, setQuoteRevision] = useState(0);
  const quoteInput = JSON.stringify({ customerId,
    items: items.map(i => ({ productId: i.product.id, quantity: i.quantity })),
    couponCode: couponCode.trim().toUpperCase() || null,
    shippingAddress: resolvedAddress?.trim() || null, paymentMethod });
  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      const input = JSON.parse(quoteInput);
      if (!input.items.length || !input.shippingAddress) return;
      setQuoteError('');
      try {
        const response = await axiosClient.post('/orders/quote', JSON.parse(quoteInput), { signal: controller.signal });
        if (!controller.signal.aborted) setQuote({ input: quoteInput, ...response.data });
      } catch (err) {
        if (!controller.signal.aborted) setQuoteError(err.response?.data || t('fix.quoteFailed'));
      }
    }, 250);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [quoteInput, quoteRevision, t]);
  const currentQuote = quote?.input === quoteInput && !quoteError ? quote : null;

  const handleCheckout = async () => {
    if (!currentQuote || placing) return;
    const shippingAddress = resolvedAddress?.trim() || null;
    if (!shippingAddress && addresses.length === 0 && !manualAddress.trim()) {
      setError('cart.addressRequired');
      return;
    }
    if (!shippingAddress && addresses.length > 0 && !useManual) {
      setError('cart.addressChoose');
      return;
    }
    setError('');
    setPlacing(true);
    try {
      await axiosClient.post('/orders', {
        customerId,
        items: items.map((i) => ({ productId: i.product.id, quantity: i.quantity })),
        couponCode: couponCode.trim().toUpperCase() || null,
        shippingAddress,
        paymentMethod,
        expectedTotal: currentQuote.total,
      });
      clearCart();
      navigate('/orders');
    } catch (err) {
      setQuote(null); setQuoteRevision(v => v + 1);
      setError(err.response?.data || 'cart.checkoutFailed');
    } finally {
      setPlacing(false);
    }
  };

  if (items.length === 0) {
    return (
      <div className="max-w-[1320px] mx-auto px-6 py-20 text-center">
        <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-[var(--color-card-bg-tint)] mb-4 text-2xl">
          🛒
        </div>
        <h4 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-1">{t('cart.empty.title')}</h4>
        <p className="text-[var(--color-text-muted)] mb-4">{t('cart.empty.hint')}</p>
        <button
          onClick={() => navigate('/products')}
          className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
        >
          {t('catalog.hero.cta')}
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-[1320px] mx-auto px-6 py-8">
      <h2 className="font-[family-name:var(--font-heading)] text-[32px] font-bold mb-6">{t('cart.title')}</h2>

      <div className="space-y-4 mb-6">
        {items.map((item) => (
          <div
            key={item.product.id}
            className="flex items-center gap-4 p-4 rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)]"
          >
            <div className="w-16 h-16 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] flex items-center justify-center font-[family-name:var(--font-heading)] font-bold text-[var(--color-primary)] opacity-40 text-xl">
              {item.product.name.charAt(0)}
            </div>
            <div className="flex-1">
              <h3 className="font-semibold text-[var(--color-text-primary)]">{item.product.name}</h3>
              <p className="text-sm text-[var(--color-text-muted)]">{t('cart.each', { price: formatCurrency(item.product.price) })}</p>
            </div>
            <input
              type="number"
              min="1"
              max={item.product.stockQuantity}
              value={item.quantity}
              onChange={(e) => updateQuantity(item.product.id, parseInt(e.target.value) || 1)}
              className="w-16 px-2 py-2 text-sm rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)]"
            />
            <p className="font-semibold w-24 text-right">{formatCurrency(item.product.price * item.quantity)}</p>
            <button
              onClick={() => removeFromCart(item.product.id)}
              className="text-[var(--color-error)] text-sm font-semibold hover:opacity-70"
            >
              {t('common.remove')}
            </button>
          </div>
        ))}
      </div>

      {/* Delivery address */}
      <div className="mb-4 p-6 rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)]">
        <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-3 flex items-center gap-2">
          <MapPin size={18} className="text-[var(--color-primary)]" /> {t('cart.deliveryAddress')}
        </h3>

        {addresses.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-3">
            {addresses.map((addr) => (
              <label
                key={addr.id}
                className={`flex items-start gap-3 p-3 rounded-[var(--radius-md)] border-[1.5px] transition-colors cursor-pointer ${
                  selectedAddressId === addr.id && !useManual
                    ? 'border-[var(--color-primary)] bg-[var(--color-card-bg-tint)]'
                    : 'border-[var(--color-border)] hover:border-[var(--color-primary)]'
                }`}
              >
                <input
                  type="radio"
                  name="address"
                  checked={selectedAddressId === addr.id && !useManual}
                  onChange={() => { setSelectedAddressId(addr.id); setUseManual(false); }}
                  className="mt-1 accent-[var(--color-primary)]"
                />
                <div className="text-sm">
                  <p className="font-semibold text-[var(--color-text-primary)]">
                    {addr.label || t('cart.addressFallback')}
                    {addr.isDefault && <span className="ml-2 text-xs text-[var(--color-primary)] font-medium">{t('cart.defaultBadge')}</span>}
                  </p>
                  <p className="text-[var(--color-text-secondary)]">{formatAddress(addr)}</p>
                </div>
              </label>
            ))}
          </div>
        )}

        {useManual ? (
          <>
            <button
              type="button"
              onClick={detectLocation}
              disabled={locating}
              className="mb-2 inline-flex items-center gap-1.5 px-3 py-2 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] text-sm font-semibold text-[var(--color-primary)] hover:bg-[var(--color-card-bg-tint)] transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {locating ? (
                <>
                  <Loader2 size={15} className="animate-spin" /> {t('cart.locating')}
                </>
              ) : (
                <>
                  <LocateFixed size={15} /> {t('cart.useLocation')}
                </>
              )}
            </button>
            <textarea
              value={manualAddress}
              onChange={(e) => setManualAddress(e.target.value)}
              placeholder={t('cart.addressPlaceholder')}
              rows={3}
              className="w-full p-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm"
            />
            <p className="mt-1.5 text-xs text-[var(--color-text-muted)]">
              {t('cart.addressHint')}
            </p>
          </>
        ) : null}

        <div className="flex items-center gap-3">
          <button
            onClick={() => setUseManual(!useManual)}
            className="inline-flex items-center gap-1.5 text-sm text-[var(--color-primary)] font-semibold hover:underline"
          >
            {useManual && addresses.length > 0 ? (
              t('cart.pickSaved')
            ) : (
              <>
                <Plus size={16} /> {t('cart.useDifferent')}
              </>
            )}
          </button>
          {addresses.length === 0 && (
            <span className="text-xs text-[var(--color-text-muted)]">
              {t('cart.or')}{' '}
              <Link to="/account" className="text-[var(--color-primary)] hover:underline">
                {t('cart.saveInAccount')}
              </Link>
            </span>
          )}
        </div>
      </div>

      {/* Payment method */}
      <div className="mb-4 p-6 rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)]">
        <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-3">{t('cart.paymentMethod')}</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {PAYMENT_METHODS.filter(m => m.key === 'CASH' || quote?.demoPaymentsEnabled).map((m) => {
            const Icon = m.icon;
            return (
              <label
                key={m.key}
                className={`flex items-start gap-3 p-3 rounded-[var(--radius-md)] border-[1.5px] transition-colors cursor-pointer ${
                  paymentMethod === m.key
                    ? 'border-[var(--color-primary)] bg-[var(--color-card-bg-tint)]'
                    : 'border-[var(--color-border)] hover:border-[var(--color-primary)]'
                }`}
              >
                <input
                  type="radio"
                  name="payment"
                  checked={paymentMethod === m.key}
                  onChange={() => setPaymentMethod(m.key)}
                  className="mt-1 accent-[var(--color-primary)]"
                />
                <div className="text-sm">
                  <p className="font-semibold text-[var(--color-text-primary)] flex items-center gap-1.5">
                    <Icon size={16} className="text-[var(--color-primary)]" /> {t(m.labelKey)}
                  </p>
                  <p className="text-[var(--color-text-muted)] text-xs">{t(m.descKey)}</p>
                </div>
              </label>
            );
          })}
        </div>
      </div>

      {quote?.demoPaymentsEnabled && <p className="text-sm mb-4">{t('fix.demoPayments')}</p>}
      {!quote?.demoPaymentsEnabled && <p className="text-sm mb-4">{t('fix.cashOnly')}</p>}
      <BundleProgress />

      <div className="flex items-center justify-between gap-6 flex-wrap p-6 rounded-[var(--radius-xl)] bg-[var(--color-card-bg-tint)]">
        <div className="space-y-1.5">
          <div className="flex items-center justify-between gap-8 text-sm text-[var(--color-text-muted)]">
            <span>{t('cart.subtotal')}</span>
            <span>{formatCurrency(currentQuote?.subtotal ?? totalPrice)}</span>
          </div>
          {(currentQuote?.bundleDiscount ?? bundleDiscount) > 0 && (
            <div className="flex items-center justify-between gap-8 text-sm text-[var(--color-success)]">
              <span>{t('cart.discount')}</span>
              <span>{t('cart.bundleDiscount', { amount: formatCurrency(currentQuote?.bundleDiscount ?? bundleDiscount) })}</span>
            </div>
          )}
          <div className="flex items-center justify-between gap-8 text-sm text-[var(--color-text-muted)]">
            <span>{t('cart.shipping')}</span>
            <span>{(currentQuote?.shipping ?? shipping) > 0 ? formatCurrency(currentQuote?.shipping ?? shipping) : t('cart.freeShipping')}</span>
          </div>
          <div className="flex items-center justify-between gap-8 text-sm text-[var(--color-text-muted)]">
            <span>{t('cart.tax')}</span>
            <span>{formatCurrency(currentQuote?.tax ?? taxAmount)}</span>
          </div>
          {currentQuote?.couponDiscount > 0 && <p className="text-sm">{t('fix.couponDiscount')}: −{formatCurrency(currentQuote.couponDiscount)}</p>}
          {currentQuote?.tierDiscount > 0 && <p className="text-sm">{t('fix.loyaltyDiscount')}: −{formatCurrency(currentQuote.tierDiscount)}</p>}
          <div className="flex items-center justify-between gap-8 pt-2 mt-1 border-t border-[var(--color-border)]">
            <p className="text-sm text-[var(--color-text-muted)]">{t('cart.total')}</p>
            <p className="font-[family-name:var(--font-heading)] text-2xl font-bold">
              {formatCurrency(currentQuote?.total ?? grandTotal)}
            </p>
          </div>
          <p className="text-xs text-[var(--color-text-muted)] max-w-xs">{t('cart.taxNote')}</p>
        </div>
        <button
          onClick={handleCheckout}
          disabled={placing || !currentQuote}
          className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-8 py-3 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-50"
        >
          {placing
            ? t('cart.placing')
            : t('cart.placeOrder', {
                method: t(PAYMENT_METHODS.find((m) => m.key === paymentMethod)?.labelKey),
              })}
        </button>
      </div>

      <div className="flex items-center justify-between gap-4 mt-4 p-4 rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)]">
        <input
          value={couponCode}
          onChange={(e) => setCouponCode(e.target.value.toUpperCase())}
          placeholder={t('cart.couponPlaceholder')}
          className="flex-1 max-w-sm px-3 py-2 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white outline-none focus:border-[var(--color-primary)] text-sm"
        />
        <p className="text-xs text-[var(--color-text-muted)]">{t('cart.couponNote')}</p>
      </div>

      {!currentQuote && !quoteError && <p className="text-sm mt-3">{t('fix.addressQuote')}</p>}
      {quoteError && <p className="text-[var(--color-error)] text-sm mt-3">{quoteError}</p>}
      {error && <p className="text-[var(--color-error)] text-sm mt-3">{t(error)}</p>}
    </div>
  );
}

export default Cart;