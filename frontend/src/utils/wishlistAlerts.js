/**
 * Utility to manage smart wishlist preferences:
 * - Target price alerts (e.g. alert me if price drops below X)
 * - Price drop tracking (initial saved price vs current catalog price)
 * - Restock alert subscriptions
 */

const STORAGE_KEY = 'shopease_wishlist_smart_alerts';

export function getWishlistAlerts() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

export function saveWishlistAlert(productId, alertData) {
  try {
    const current = getWishlistAlerts();
    current[productId] = {
      ...current[productId],
      ...alertData,
      updatedAt: new Date().toISOString(),
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(current));
    return current[productId];
  } catch {
    return null;
  }
}

export function removeWishlistAlert(productId) {
  try {
    const current = getWishlistAlerts();
    delete current[productId];
    localStorage.setItem(STORAGE_KEY, JSON.stringify(current));
  } catch {}
}

/**
 * Checks if a product has dropped in price compared to initial recorded price
 * or if it has met the customer's custom target alert price.
 */
export function evaluatePriceAlert(product, alertData) {
  if (!product || !alertData) return null;
  const currentPrice = Number(product.price || 0);
  const initialPrice = Number(alertData.initialPrice || currentPrice);
  const targetPrice = Number(alertData.targetPrice || 0);

  const priceDropAmount = initialPrice > currentPrice ? initialPrice - currentPrice : 0;
  const targetMet = targetPrice > 0 && currentPrice <= targetPrice;

  return {
    hasDropped: priceDropAmount > 0,
    priceDropAmount,
    initialPrice,
    currentPrice,
    targetPrice,
    targetMet,
    notifyOnDrop: !!alertData.notifyOnDrop,
    notifyOnRestock: !!alertData.notifyOnRestock,
  };
}
