import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Banknote, CreditCard, Smartphone, MapPin, Plus, LocateFixed, Loader2 } from 'lucide-react';
import { useCart } from '../context/CartContext';
import axiosClient from '../api/axiosClient';
import { getCustomerId } from '../utils/customer';

const PAYMENT_METHODS = [
  { key: 'CASH', label: 'Cash on Delivery', desc: 'Pay when your order arrives', icon: Banknote },
  { key: 'UPI', label: 'UPI', desc: 'Mock gateway — instant approval', icon: Smartphone },
  { key: 'CARD', label: 'Card', desc: 'Mock gateway — instant approval', icon: CreditCard },
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

  if (distinctProductCount === 0) return null;

  if (bundlePercent > 0) {
    return (
      <div className="p-4 rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] border-[1.5px] border-[var(--color-primary)] shadow-[var(--shadow-sm)]">
        <p className="text-sm font-semibold text-[var(--color-primary)]">
          Bundle discount unlocked — {Math.round(bundlePercent * 100)}% off, saving ₹{bundleDiscount}
        </p>
        <p className="text-xs text-[var(--color-text-muted)] mt-1">
          {nextBundleAt > 0
            ? `Add ${nextBundleAt - distinctProductCount} more product${nextBundleAt - distinctProductCount > 1 ? 's' : ''} to reach ${BUNDLE_TIERS.find((t) => t.products === nextBundleAt)?.off} off.`
            : 'You are on the best bundle tier available.'}
        </p>
      </div>
    );
  }

  return (
    <div className="p-4 rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)]">
      <p className="text-sm font-semibold">Buy 2+ different products and save automatically</p>
      <p className="text-xs text-[var(--color-text-muted)] mt-1">
        {nextBundleAt - distinctProductCount} more product
        {nextBundleAt - distinctProductCount > 1 ? 's' : ''} unlocks{' '}
        {BUNDLE_TIERS.find((t) => t.products === nextBundleAt)?.off} off — stacks with your coupon.
      </p>
    </div>
  );
}

function Cart() {
  const { items, removeFromCart, updateQuantity, clearCart, totalPrice, bundleDiscount } = useCart();
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
      setError('This browser cannot share a location. Please type your address.');
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
        setError(
          'Could not get your location. Check your browser permission, or type your address.'
        );
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 }
    );
  };

  const resolvedAddress = useManual || addresses.length === 0
    ? manualAddress
    : formatAddress(addresses.find((a) => a.id === selectedAddressId));

  const handleCheckout = async () => {
    const shippingAddress = resolvedAddress?.trim() || null;
    if (!shippingAddress && addresses.length === 0 && !manualAddress.trim()) {
      setError('Please enter a delivery address');
      return;
    }
    if (!shippingAddress && addresses.length > 0 && !useManual) {
      setError('Please choose a delivery address');
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
      });
      clearCart();
      navigate('/orders');
    } catch (err) {
      setError(err.response?.data || 'Checkout failed');
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
        <h4 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-1">Your cart is empty</h4>
        <p className="text-[var(--color-text-muted)] mb-4">Browse products and add something you like</p>
        <button
          onClick={() => navigate('/products')}
          className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
        >
          Start Shopping
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-[1320px] mx-auto px-6 py-8">
      <h2 className="font-[family-name:var(--font-heading)] text-[32px] font-bold mb-6">Your Cart</h2>

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
              <p className="text-sm text-[var(--color-text-muted)]">₹{item.product.price} each</p>
            </div>
            <input
              type="number"
              min="1"
              max={item.product.stockQuantity}
              value={item.quantity}
              onChange={(e) => updateQuantity(item.product.id, parseInt(e.target.value) || 1)}
              className="w-16 px-2 py-2 text-sm rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)]"
            />
            <p className="font-semibold w-24 text-right">₹{item.product.price * item.quantity}</p>
            <button
              onClick={() => removeFromCart(item.product.id)}
              className="text-[var(--color-error)] text-sm font-semibold hover:opacity-70"
            >
              Remove
            </button>
          </div>
        ))}
      </div>

      {/* Delivery address */}
      <div className="mb-4 p-6 rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)]">
        <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-3 flex items-center gap-2">
          <MapPin size={18} className="text-[var(--color-primary)]" /> Delivery Address
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
                    {addr.label || 'Address'}
                    {addr.isDefault && <span className="ml-2 text-xs text-[var(--color-primary)] font-medium">Default</span>}
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
                  <Loader2 size={15} className="animate-spin" /> Finding your location...
                </>
              ) : (
                <>
                  <LocateFixed size={15} /> Use my current location
                </>
              )}
            </button>
            <textarea
              value={manualAddress}
              onChange={(e) => setManualAddress(e.target.value)}
              placeholder="Flat / House no, Street, Area, City - State Pincode, Phone"
              rows={3}
              className="w-full p-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm"
            />
            <p className="mt-1.5 text-xs text-[var(--color-text-muted)]">
              Using your location fills in the area and pincode. Add your flat or
              house number so the courier can find you.
            </p>
          </>
        ) : null}

        <div className="flex items-center gap-3">
          <button
            onClick={() => setUseManual(!useManual)}
            className="inline-flex items-center gap-1.5 text-sm text-[var(--color-primary)] font-semibold hover:underline"
          >
            {useManual && addresses.length > 0 ? (
              'Pick a saved address'
            ) : (
              <>
                <Plus size={16} /> Use a different address
              </>
            )}
          </button>
          {addresses.length === 0 && (
            <span className="text-xs text-[var(--color-text-muted)]">
              or{' '}
              <Link to="/account" className="text-[var(--color-primary)] hover:underline">
                save addresses in My Account
              </Link>
            </span>
          )}
        </div>
      </div>

      {/* Payment method */}
      <div className="mb-4 p-6 rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)]">
        <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-3">Payment Method</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {PAYMENT_METHODS.map((m) => {
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
                    <Icon size={16} className="text-[var(--color-primary)]" /> {m.label}
                  </p>
                  <p className="text-[var(--color-text-muted)] text-xs">{m.desc}</p>
                </div>
              </label>
            );
          })}
        </div>
      </div>

      <BundleProgress />

      <div className="flex items-center justify-between p-6 rounded-[var(--radius-xl)] bg-[var(--color-card-bg-tint)]">
        <div>
          <p className="text-sm text-[var(--color-text-muted)]">
            Total
            {bundleDiscount > 0 && (
              <span className="ml-2 text-xs text-[var(--color-success)]">
                bundle −₹{bundleDiscount}
              </span>
            )}
          </p>
          <p className="font-[family-name:var(--font-heading)] text-2xl font-bold">
            ₹{totalPrice}
          </p>
        </div>
        <button
          onClick={handleCheckout}
          disabled={placing}
          className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-8 py-3 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-50"
        >
          {placing ? 'Placing order...' : `Place Order · ${PAYMENT_METHODS.find((m) => m.key === paymentMethod)?.label}`}
        </button>
      </div>

      <div className="flex items-center justify-between gap-4 mt-4 p-4 rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)]">
        <input
          value={couponCode}
          onChange={(e) => setCouponCode(e.target.value.toUpperCase())}
          placeholder="Have a coupon? Enter code (e.g. WELCOME10)"
          className="flex-1 max-w-sm px-3 py-2 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white outline-none focus:border-[var(--color-primary)] text-sm"
        />
        <p className="text-xs text-[var(--color-text-muted)]">Applied automatically at checkout</p>
      </div>

      {error && <p className="text-[var(--color-error)] text-sm mt-3">{error}</p>}
    </div>
  );
}

export default Cart;