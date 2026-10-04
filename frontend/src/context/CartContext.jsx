import { createContext, useContext, useState, useEffect } from 'react';

const CartContext = createContext();

const STORAGE_KEY = 'shopnow_cart';

/**
 * Bundle tiers, mirroring OrderService.bundleDiscountPercent on the server.
 * This is a *preview* only - the server recomputes the discount at checkout and
 * its number is the one that gets charged, so the two must agree. Quantities
 * deliberately do not count: three of the same shirt is not a bundle.
 */
export function bundlePercentFor(distinctProducts) {
  if (distinctProducts >= 4) return 0.15;
  if (distinctProducts === 3) return 0.1;
  if (distinctProducts === 2) return 0.05;
  return 0;
}

/** Distinct products needed for the next tier up, or 0 once the top tier is reached. */
export function nextBundleTier(distinctProducts) {
  if (distinctProducts < 2) return 2;
  if (distinctProducts < 3) return 3;
  if (distinctProducts < 4) return 4;
  return 0;
}

function loadCart() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

export function CartProvider({ children }) {
  const [items, setItems] = useState(loadCart);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
    } catch {
      // storage unavailable (private mode) — ignore
    }
  }, [items]);

  const addToCart = (product, quantity) => {
    setItems((prev) => {
      const existing = prev.find((i) => i.product.id === product.id);
      if (existing) {
        return prev.map((i) =>
          i.product.id === product.id
            ? { ...i, quantity: i.quantity + quantity }
            : i
        );
      }
      return [...prev, { product, quantity }];
    });
  };

  const removeFromCart = (productId) => {
    setItems((prev) => prev.filter((i) => i.product.id !== productId));
  };

  const updateQuantity = (productId, quantity) => {
    setItems((prev) =>
      prev.map((i) => (i.product.id === productId ? { ...i, quantity } : i))
    );
  };

  const clearCart = () => setItems([]);

  const totalItems = items.reduce((sum, i) => sum + i.quantity, 0);
  const totalPrice = items.reduce((sum, i) => sum + i.product.price * i.quantity, 0);

  // Bundle progress. One line per product, so the line count is the distinct
  // product count; quantities are intentionally excluded from the tier.
  const distinctProductCount = items.length;
  const bundlePercent = bundlePercentFor(distinctProductCount);
  const bundleDiscount = Math.round(totalPrice * bundlePercent * 100) / 100;
  const nextBundleAt = nextBundleTier(distinctProductCount);

  // Delivery and tax, mirroring OrderService so the cart quotes the same figure
  // the server will charge. The server remains authoritative - these exist so a
  // customer is not surprised by a total that appears between checkout and the
  // confirmation.
  //
  // Shipping is measured against the pre-discount subtotal for the same reason it
  // is on the server: a coupon must not be able to tip a basket over the free
  // delivery threshold. Tax is charged on the discounted goods only, never on the
  // delivery charge.
  const FLAT_SHIPPING = 49;
  const FREE_SHIPPING_THRESHOLD = 999;
  const TAX_RATE = 0.18;

  const shipping = totalPrice <= 0 || totalPrice >= FREE_SHIPPING_THRESHOLD ? 0 : FLAT_SHIPPING;
  const goodsAfterDiscount = Math.max(totalPrice - bundleDiscount, 0);
  const taxAmount = Math.round(goodsAfterDiscount * TAX_RATE * 100) / 100;
  const grandTotal = Math.round((goodsAfterDiscount + shipping + taxAmount) * 100) / 100;
  const freeShippingRemaining = Math.round(Math.max(FREE_SHIPPING_THRESHOLD - totalPrice, 0) * 100) / 100;

  return (
    <CartContext.Provider
      value={{
        items,
        addToCart,
        removeFromCart,
        updateQuantity,
        clearCart,
        totalItems,
        totalPrice,
        distinctProductCount,
        bundlePercent,
        bundleDiscount,
        nextBundleAt,
        shipping,
        taxAmount,
        grandTotal,
        freeShippingRemaining,
      }}
    >
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  return useContext(CartContext);
}