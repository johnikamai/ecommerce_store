import { useState, useEffect } from 'react';
import axiosClient from '../api/axiosClient';
import { getCustomerId } from '../utils/customer';
import ReceiptModal from '../components/ReceiptModal';
import OrderTracking from '../components/OrderTracking';
import { useLanguage } from '../context/LanguageContext';

// PACKED and OUT_FOR_DELIVERY are real backend states, so the rail has to
// include them or an order sitting in one of them renders no progress at all.
const STATUS_STEPS = ['PLACED', 'PACKED', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED'];

const TIER_STYLES = {
  GOLD: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  SILVER: 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]',
  BRONZE: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
};

const RETURN_STATUS_STYLES = {
  REQUESTED: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  APPROVED: 'bg-[var(--color-info)] text-white',
  REJECTED: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
  REFUNDED: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
};

const PAYMENT_STATUS_STYLES = {
  PAID: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
  PENDING: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  FAILED: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
  REFUNDED: 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-muted)]',
};

const PAYMENT_METHOD_LABELS = {
  CASH: 'Cash on Delivery',
  UPI: 'UPI',
  CARD: 'Card',
};

function StatusChip({ value, styles, fallback }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${styles[value] || fallback || ''}`}>{value}</span>;
}

function OrderTimeline({ status }) {
  const { t } = useLanguage();

  if (status === 'CANCELLED') {
    return (
      <div className="flex items-center gap-2 text-[var(--color-error)] text-sm font-medium">
        <span className="w-3 h-3 rounded-full bg-[var(--color-error)]" />
        {t('status.CANCELLED')}
      </div>
    );
  }

  const currentIndex = STATUS_STEPS.indexOf(status);

  // Show the milestones actually reached, plus DELIVERED if it is still ahead.
  // PACKED / OUT_FOR_DELIVERY are optional waypoints, so an order that skipped
  // them must not display a pending step for a stage it never went through.
  const steps = currentIndex < 0
    ? STATUS_STEPS
    : [
        ...STATUS_STEPS.slice(0, currentIndex + 1),
        ...(currentIndex < STATUS_STEPS.length - 1 ? ['DELIVERED'] : []),
      ];

  return (
    <div className="flex items-center overflow-x-auto pb-1">
      {steps.map((step, i) => {
        const isDone = i <= currentIndex;
        const isLast = i === steps.length - 1;
        return (
          <div key={step} className="flex items-center">
            <div className="flex flex-col items-center">
              <div
                className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold transition-colors shrink-0 ${
                  isDone
                    ? 'bg-[var(--color-success)] text-white'
                    : 'bg-[var(--color-border)] text-[var(--color-text-muted)]'
                }`}
              >
                {isDone ? '✓' : ''}
              </div>
              <span className={`text-xs mt-1 whitespace-nowrap ${isDone ? 'text-[var(--color-text-primary)] font-medium' : 'text-[var(--color-text-muted)]'}`}>
                {t(`status.${step}`)}
              </span>
            </div>
            {!isLast && (
              <div
                className={`w-8 h-0.5 mb-4 transition-colors shrink-0 ${
                  i < currentIndex ? 'bg-[var(--color-success)]' : 'bg-[var(--color-border)]'
                }`}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

function Orders() {
  const { t, formatDate, formatDateTime, formatCurrency, formatNumber } = useLanguage();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [customer, setCustomer] = useState(null);
  const [notifications, setNotifications] = useState([]);
  const [returns, setReturns] = useState([]);
  const [returnForms, setReturnForms] = useState({});
  const [copied, setCopied] = useState(false);
  const [cancelling, setCancelling] = useState({});
  const [receiptFor, setReceiptFor] = useState(null);

  const role = localStorage.getItem('role');
  const customerId = getCustomerId();

  const fetchOrders = async () => {
    try {
      const url = role === 'ADMIN' ? '/orders' : `/orders/customer/${customerId}`;
      const response = await axiosClient.get(url);
      setOrders(response.data);
    } catch (err) {
      setError('Failed to load orders');
    } finally {
      setLoading(false);
    }
  };

  const fetchReturns = async () => {
    try {
      const res = await axiosClient.get(`/returns/customer/${customerId}`);
      setReturns(res.data);
    } catch (err) {
      setReturns([]);
    }
  };

  useEffect(() => {
    const init = async () => {
      try {
        const [customerRes, notifRes] = await Promise.all([
          axiosClient.get(`/customers/${customerId}`),
          axiosClient.get(`/notifications/customer/${customerId}`),
        ]);
        setCustomer(customerRes.data);
        // The endpoint returns { notifications, unread }; fall back to a bare
        // array so an older cached backend response still renders.
        const payload = notifRes.data;
        setNotifications(Array.isArray(payload) ? payload : payload?.notifications || []);
      } catch (err) {
        setCustomer(null);
        setNotifications([]);
      }
    };
    fetchOrders();
    fetchReturns();
    init();
  }, [customerId]);

  const updateStatus = async (orderId, newStatus) => {
    try {
      await axiosClient.put(`/orders/${orderId}/status`, { status: newStatus });
      fetchOrders();
    } catch (err) {
      alert(t('orders.statusUpdateFailed'));
    }
  };

  const cancelMyOrder = async (orderId) => {
    if (!window.confirm(t('orders.cancelConfirm', { id: orderId }))) return;
    setCancelling((prev) => ({ ...prev, [orderId]: true }));
    try {
      await axiosClient.post(`/orders/${orderId}/cancel`);
      fetchOrders();
    } catch (err) {
      alert(err.response?.data || t('orders.cancelFailed'));
    } finally {
      setCancelling((prev) => ({ ...prev, [orderId]: false }));
    }
  };

  const toggleReturnForm = (itemId) => {
    setReturnForms((prev) => ({
      ...prev,
      [itemId]: prev[itemId]?.open ? { open: false, reason: '', error: '' } : { open: true, reason: '', error: '' },
    }));
  };

  const submitReturn = async (itemId) => {
    const form = returnForms[itemId] || {};
    if (!form.reason || !form.reason.trim()) {
      setReturnForms((prev) => ({ ...prev, [itemId]: { ...prev[itemId], error: t('orders.returnReasonRequired') } }));
      return;
    }
    try {
      await axiosClient.post('/returns', { orderItemId: itemId, customerId, reason: form.reason });
      setReturnForms((prev) => ({ ...prev, [itemId]: { open: false, reason: '', error: t('orders.returnRequested') } }));
      fetchReturns();
    } catch (err) {
      setReturnForms((prev) => ({ ...prev, [itemId]: { ...prev[itemId], error: err.response?.data || t('action.retry') } }));
    }
  };

  const copyCode = async () => {
    if (!customer?.referralCode) return;
    try {
      // Share a link, not a bare code. The signup form reads ?ref= and pre-fills
      // the field, so a friend who taps the link does not have to type anything.
      const link = `${window.location.origin}/login?mode=register&ref=${encodeURIComponent(customer.referralCode)}`;
      await navigator.clipboard.writeText(link);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch (err) {
      // clipboard not available; ignore
    }
  };

  const markAllRead = async () => {
    try {
      await axiosClient.put(`/notifications/customer/${customerId}/read-all`);
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
    } catch (err) {
      // ignore
    }
  };

  const markRead = async (n) => {
    if (n.isRead) return;
    try {
      await axiosClient.put(`/notifications/${n.id}/read`);
      setNotifications((prev) => prev.map((x) => (x.id === n.id ? { ...x, isRead: true } : x)));
    } catch (err) {
      // ignore
    }
  };

  // The order carries no payment id, so resolve it before opening the receipt.
  const openReceipt = async (orderId) => {
    try {
      const res = await axiosClient.get(`/payments/order/${orderId}`);
      setReceiptFor(res.data.id);
    } catch {
      setError(t('orders.noReceipt'));
    }
  };

  if (loading) return <p className="max-w-[1320px] mx-auto px-6 py-12">{t('orders.loading')}</p>;
  if (error) return <p className="max-w-[1320px] mx-auto px-6 py-12 text-[var(--color-error)]">{t('orders.error')}</p>;

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <div className="max-w-[1320px] mx-auto px-6 py-8">
      <h2 className="font-[family-name:var(--font-heading)] text-[32px] font-bold mb-6">
        {role === 'ADMIN' ? t('orders.title.all') : t('orders.title.mine')}
      </h2>

      {/* Account: loyalty + referral + notifications */}
      {role !== 'ADMIN' && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
          <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
            <p className="text-xs text-[var(--color-text-muted)] mb-1">{t('account.loyaltyPoints')}</p>
            {customer ? (
              <>
                <div className="flex items-center gap-2 mb-1">
                  <StatusChip value={customer.tier || 'BRONZE'} styles={TIER_STYLES} />
                </div>
                <p className="font-[family-name:var(--font-heading)] text-2xl font-bold">
                  {customer.loyaltyPoints ?? 0} pts
                </p>
                <p className="text-xs text-[var(--color-text-muted)] mt-1">
                  Earn 1 pt per ₹100. Higher tier = bigger discount at checkout.
                </p>
              </>
            ) : (
              <p className="text-sm text-[var(--color-text-muted)]">{t('account.unavailable')}</p>
            )}
          </div>

          <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
            <p className="text-xs text-[var(--color-text-muted)] mb-1">{t('account.referralProgram')}</p>
            {customer?.referralCode ? (
              <>
                <button
                  onClick={copyCode}
                  title="Copy invite code"
                  className="font-[family-name:var(--font-heading)] text-xl font-bold text-[var(--color-primary)] hover:underline"
                >
                  {customer.referralCode} {copied ? '✓' : '⧉'}
                </button>
                <p className="text-xs text-[var(--color-text-muted)] mt-1">
                  When a friend signs up with your code, you get +500 pts on their first order.
                </p>
              </>
            ) : (
              <p className="text-sm text-[var(--color-text-muted)]">
                No code yet. New customers get one automatically.
              </p>
            )}
          </div>

          <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
            <p className="text-xs text-[var(--color-text-muted)] mb-2 flex items-center justify-between">
              {t('account.notifications')}
              {unreadCount > 0 && (
                <button onClick={markAllRead} className="text-[var(--color-primary)] font-semibold hover:underline">
                  {t('account.markAllRead')}
                </button>
              )}
            </p>
            {notifications.length === 0 ? (
              <p className="text-sm text-[var(--color-text-muted)]">
                {t('account.noNotifications')}
              </p>
            ) : (
              <ul className="space-y-1.5 max-h-36 overflow-y-auto">
                {notifications.slice(0, 10).map((n) => (
                  <li key={n.id}>
                    <button
                      onClick={() => markRead(n)}
                      className={`w-full text-left border-l-2 pl-2 transition-colors ${n.isRead ? 'opacity-60 border-[var(--color-border)]' : 'border-[var(--color-primary)]'}`}
                    >
                      <span className="block text-xs text-[var(--color-text-secondary)]">{n.message}</span>
                      {n.sentAt && (
                        <span className="block text-[var(--color-text-muted)] text-[11px] mt-0.5">
                          {formatDateTime(n.sentAt)}
                        </span>
                      )}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}

      {/* My Returns */}
      {role !== 'ADMIN' && returns.length > 0 && (
        <div className="mb-8">
          <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-3">{t('orders.myReturns')}</h3>
          <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
            <ul className="space-y-2 text-sm">
              {returns.map((r) => (
                <li key={r.id} className="flex items-center justify-between gap-4 flex-wrap">
                  <div>
                    <span className="font-medium text-[var(--color-text-primary)]">
                      {r.orderItem.product.name}
                    </span>
                    <span className="text-[var(--color-text-muted)]"> × {r.orderItem.quantity}</span>
                    {r.reason && <span className="text-[var(--color-text-muted)]"> — {r.reason}</span>}
                  </div>
                  <StatusChip value={r.status} styles={RETURN_STATUS_STYLES} />
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {orders.length === 0 && (
        <div className="text-center py-20">
          <p className="text-[var(--color-text-muted)]">{t('orders.empty')}</p>
        </div>
      )}

      <div className="space-y-4">
        {orders.map((order) => (
          <div key={order.id} className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-6">
            <div className="flex items-center justify-between mb-4 flex-wrap gap-4">
              <div>
                <p className="font-semibold text-[var(--color-text-primary)]">{t('orders.number', { id: order.id })}</p>
                <p className="text-sm text-[var(--color-text-muted)]">
                  {formatDate(order.orderDate)}
                  {order.paymentMethod && (
                    <span className="ml-2 inline-block rounded-full px-2 py-0.5 text-xs bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]">
                      {PAYMENT_METHOD_LABELS[order.paymentMethod] || order.paymentMethod}
                      {order.paymentStatus && (
                        <>
                          {' · '}
                          <span className={PAYMENT_STATUS_STYLES[order.paymentStatus] || 'text-[var(--color-text-secondary)]'}>
                            {order.paymentStatus}
                          </span>
                        </>
                      )}
                    </span>
                  )}
                </p>
              </div>
              <div className="text-right">
                <p className="font-[family-name:var(--font-heading)] text-xl font-bold">{formatCurrency(order.totalAmount)}</p>
                {/* Itemise the charges that make up the grand total. Without these
                    lines the total simply reads higher than the basket the customer
                    agreed to, with nothing on screen to account for the gap. */}
                {(Number(order.shippingAmount) > 0 || Number(order.taxAmount) > 0) && (
                  <div className="mt-1 space-y-0.5 text-xs text-[var(--color-text-muted)]">
                    {Number(order.shippingAmount) > 0 && (
                      <p>{t('orders.shipping', { amount: formatNumber(order.shippingAmount) })}</p>
                    )}
                    {Number(order.taxAmount) > 0 && (
                      <p>{t('orders.tax', { amount: formatNumber(order.taxAmount) })}</p>
                    )}
                  </div>
                )}
                {order.discountAmount > 0 && (
                  <p className="text-xs text-[var(--color-success)]">{t('orders.couponSaved', { amount: formatNumber(order.discountAmount) })}</p>
                )}
                {order.couponCode && <p className="text-xs text-[var(--color-text-muted)]">{t('orders.couponCode', { code: order.couponCode })}</p>}
              </div>
            </div>

            {order.shippingAddress && (
              <p className="text-xs text-[var(--color-text-muted)] mb-3">
                {t('orders.deliveringTo', { address: order.shippingAddress })}
              </p>
            )}

            <div className="mb-4">
              <OrderTimeline status={order.status} />
            </div>

            {/* Live courier tracking. Polls itself only while the parcel moves. */}
            {order.status !== 'CANCELLED' && (
              <OrderTracking orderId={order.id} status={order.status} />
            )}

            {/* Customer cancel: only while order is still PLACED */}
            {role !== 'ADMIN' && order.status === 'PLACED' && (
              <div className="flex gap-2 mb-4">
                <button
                  onClick={() => cancelMyOrder(order.id)}
                  disabled={cancelling[order.id]}
                  className="rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-error)] text-[var(--color-error)] px-4 py-2 text-xs font-semibold hover:bg-[var(--color-error-bg)] transition-colors disabled:opacity-50"
                >
                  {cancelling[order.id] ? t('orders.cancelling') : t('action.cancelOrder')}
                </button>
              </div>
            )}

            {/* Digital receipt for the order's payment */}
            <div className="flex gap-2 mb-4">
              <button
                type="button"
                onClick={() => openReceipt(order.id)}
                className="rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)] px-4 py-2 text-xs font-semibold hover:bg-[var(--color-border)] transition-colors"
              >
                {t('orders.viewReceipt')}
              </button>
            </div>

            {/* Admin fulfilment controls. The options mirror the backend's
                allowed transitions, so the console only offers moves the API
                will actually accept. */}
            {role === 'ADMIN' && order.status !== 'CANCELLED' && order.status !== 'DELIVERED' && (
              <div className="flex flex-wrap gap-2 mb-4">
                {order.status === 'PLACED' && (
                  <button
                    onClick={() => updateStatus(order.id, 'PACKED')}
                    className="rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-[var(--color-text-primary)] px-4 py-2 text-xs font-semibold hover:bg-[var(--color-border)] transition-colors"
                  >
                    {t('admin.markPacked')}
                  </button>
                )}
                {(order.status === 'PLACED' || order.status === 'PACKED') && (
                  <button
                    onClick={() => updateStatus(order.id, 'SHIPPED')}
                    className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-4 py-2 text-xs font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
                  >
                    {t('admin.markShipped')}
                  </button>
                )}
                {order.status === 'SHIPPED' && (
                  <button
                    onClick={() => updateStatus(order.id, 'OUT_FOR_DELIVERY')}
                    className="rounded-[var(--radius-md)] bg-[var(--color-info)] text-white px-4 py-2 text-xs font-semibold hover:opacity-90 transition-opacity"
                  >
                    {t('admin.markOutForDelivery')}
                  </button>
                )}
                {/* Every non-terminal state can jump straight to delivered; the
                    guard above already excludes CANCELLED and DELIVERED. */}
                <button
                  onClick={() => updateStatus(order.id, 'DELIVERED')}
                  className="rounded-[var(--radius-md)] bg-[var(--color-success)] text-white px-4 py-2 text-xs font-semibold hover:opacity-90 transition-opacity"
                >
                  {t('admin.markDelivered')}
                </button>
                <button
                  onClick={() => updateStatus(order.id, 'CANCELLED')}
                  className="rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-error)] text-[var(--color-error)] px-4 py-2 text-xs font-semibold hover:bg-[var(--color-error-bg)] transition-colors"
                >
                  {t('action.cancelOrder')}
                </button>
              </div>
            )}

            <ul className="text-sm text-[var(--color-text-secondary)] space-y-2 border-t border-[var(--color-border)] pt-3">
              {order.orderItems.map((item) => (
                <li key={item.id} className="flex items-center justify-between gap-4 flex-wrap">
                  <span className="flex-1 min-w-[220px] flex items-baseline justify-between gap-3">
                    <span>
                      {item.product.name} × {item.quantity} — ₹{item.unitPrice} each
                    </span>
                    <span className="font-semibold text-[var(--color-text-primary)] tabular-nums">
                      {formatCurrency(item.lineTotal ?? Number(item.unitPrice) * item.quantity)}
                    </span>
                  </span>
                  {order.status === 'DELIVERED' && role !== 'ADMIN' && (
                    <div className="flex items-center gap-2">
                      {returnForms[item.id]?.open ? (
                        formReturnInline(item, returnForms[item.id], setReturnForms, submitReturn, t)
                      ) : (
                        <button
                          onClick={() => toggleReturnForm(item.id)}
                          className="rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-error)] text-[var(--color-error)] px-3 py-1.5 text-xs font-semibold hover:bg-[var(--color-error-bg)] transition-colors"
                        >
                          {t('orders.requestReturn')}
                        </button>
                      )}
                    </div>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>

      {receiptFor != null && <ReceiptModal paymentId={receiptFor} onClose={() => setReceiptFor(null)} />}
    </div>
  );
}

function formReturnInline(item, form, setReturnForms, submitReturn, t) {
  return (
    <div className="flex items-center gap-2">
      <input
        type="text"
        placeholder={t('orders.returnReason')}
        value={form?.reason || ''}
        onChange={(e) =>
          setReturnForms((prev) => ({ ...prev, [item.id]: { ...prev[item.id], reason: e.target.value } }))
        }
        className="w-48 px-3 py-1.5 text-xs rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)]"
      />
      <button
        onClick={() => submitReturn(item.id)}
        className="rounded-[var(--radius-md)] bg-[var(--color-error)] text-white px-3 py-1.5 text-xs font-semibold hover:opacity-90 transition-opacity"
      >
        {t('action.submit')}
      </button>
      {form?.error && <span className="text-xs text-[var(--color-error)]">{form.error}</span>}
    </div>
  );
}

export default Orders;