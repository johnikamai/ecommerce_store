import { useState, useEffect } from 'react';
import axiosClient from '../api/axiosClient';
import { getCustomerId } from '../utils/customer';
import ReceiptModal from '../components/ReceiptModal';

const STATUS_STEPS = ['PLACED', 'SHIPPED', 'DELIVERED'];

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
  if (status === 'CANCELLED') {
    return (
      <div className="flex items-center gap-2 text-[var(--color-error)] text-sm font-medium">
        <span className="w-3 h-3 rounded-full bg-[var(--color-error)]" />
        Cancelled
      </div>
    );
  }

  const currentIndex = STATUS_STEPS.indexOf(status);

  return (
    <div className="flex items-center">
      {STATUS_STEPS.map((step, i) => {
        const isDone = i <= currentIndex;
        const isLast = i === STATUS_STEPS.length - 1;
        return (
          <div key={step} className="flex items-center">
            <div className="flex flex-col items-center">
              <div
                className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold transition-colors ${
                  isDone
                    ? 'bg-[var(--color-success)] text-white'
                    : 'bg-[var(--color-border)] text-[var(--color-text-muted)]'
                }`}
              >
                {isDone ? '✓' : ''}
              </div>
              <span className={`text-xs mt-1 ${isDone ? 'text-[var(--color-text-primary)] font-medium' : 'text-[var(--color-text-muted)]'}`}>
                {step.charAt(0) + step.slice(1).toLowerCase()}
              </span>
            </div>
            {!isLast && (
              <div
                className={`w-12 h-0.5 mb-4 transition-colors ${
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
      alert('Failed to update status');
    }
  };

  const cancelMyOrder = async (orderId) => {
    if (!window.confirm(`Cancel order #${orderId}? Any reserved stock will be released back.`)) return;
    setCancelling((prev) => ({ ...prev, [orderId]: true }));
    try {
      await axiosClient.post(`/orders/${orderId}/cancel`);
      fetchOrders();
    } catch (err) {
      alert(err.response?.data || 'Failed to cancel order');
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
      setReturnForms((prev) => ({ ...prev, [itemId]: { ...prev[itemId], error: 'Please enter a reason' } }));
      return;
    }
    try {
      await axiosClient.post('/returns', { orderItemId: itemId, customerId, reason: form.reason });
      setReturnForms((prev) => ({ ...prev, [itemId]: { open: false, reason: '', error: '✓ Return requested' } }));
      fetchReturns();
    } catch (err) {
      setReturnForms((prev) => ({ ...prev, [itemId]: { ...prev[itemId], error: err.response?.data || 'Request failed' } }));
    }
  };

  const copyCode = async () => {
    if (!customer?.referralCode) return;
    try {
      await navigator.clipboard.writeText(customer.referralCode);
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
      setError('No payment record found for this order.');
    }
  };

  if (loading) return <p className="max-w-[1320px] mx-auto px-6 py-12">Loading orders...</p>;
  if (error) return <p className="max-w-[1320px] mx-auto px-6 py-12 text-[var(--color-error)]">{error}</p>;

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <div className="max-w-[1320px] mx-auto px-6 py-8">
      <h2 className="font-[family-name:var(--font-heading)] text-[32px] font-bold mb-6">
        {role === 'ADMIN' ? 'All Orders' : 'My Orders'}
      </h2>

      {/* Account: loyalty + referral + notifications */}
      {role !== 'ADMIN' && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
          <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
            <p className="text-xs text-[var(--color-text-muted)] mb-1">Loyalty Points</p>
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
              <p className="text-sm text-[var(--color-text-muted)]">Unavailable</p>
            )}
          </div>

          <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
            <p className="text-xs text-[var(--color-text-muted)] mb-1">Referral Program</p>
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
              Notifications
              {unreadCount > 0 && (
                <button onClick={markAllRead} className="text-[var(--color-primary)] font-semibold hover:underline">
                  Mark all read
                </button>
              )}
            </p>
            {notifications.length === 0 ? (
              <p className="text-sm text-[var(--color-text-muted)]">
                No notifications yet. Order updates, shipping alerts and offers land here.
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
                          {new Date(n.sentAt).toLocaleString()}
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
          <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-3">My Returns</h3>
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
          <p className="text-[var(--color-text-muted)]">No orders yet.</p>
        </div>
      )}

      <div className="space-y-4">
        {orders.map((order) => (
          <div key={order.id} className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-6">
            <div className="flex items-center justify-between mb-4 flex-wrap gap-4">
              <div>
                <p className="font-semibold text-[var(--color-text-primary)]">Order #{order.id}</p>
                <p className="text-sm text-[var(--color-text-muted)]">
                  {new Date(order.orderDate).toLocaleDateString()}
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
                <p className="font-[family-name:var(--font-heading)] text-xl font-bold">₹{order.totalAmount}</p>
                {order.discountAmount > 0 && (
                  <p className="text-xs text-[var(--color-success)]">Coupon saved ₹{order.discountAmount}</p>
                )}
                {order.couponCode && <p className="text-xs text-[var(--color-text-muted)]">Code: {order.couponCode}</p>}
              </div>
            </div>

            {order.shippingAddress && (
              <p className="text-xs text-[var(--color-text-muted)] mb-3">
                Delivering to: {order.shippingAddress}
              </p>
            )}

            <div className="mb-4">
              <OrderTimeline status={order.status} />
            </div>

            {/* Customer cancel: only while order is still PLACED */}
            {role !== 'ADMIN' && order.status === 'PLACED' && (
              <div className="flex gap-2 mb-4">
                <button
                  onClick={() => cancelMyOrder(order.id)}
                  disabled={cancelling[order.id]}
                  className="rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-error)] text-[var(--color-error)] px-4 py-2 text-xs font-semibold hover:bg-[var(--color-error-bg)] transition-colors disabled:opacity-50"
                >
                  {cancelling[order.id] ? 'Cancelling...' : 'Cancel Order'}
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
                View Receipt
              </button>
            </div>

            {role === 'ADMIN' && order.status !== 'CANCELLED' && order.status !== 'DELIVERED' && (
              <div className="flex gap-2 mb-4">
                {order.status === 'PLACED' && (
                  <button
                    onClick={() => updateStatus(order.id, 'SHIPPED')}
                    className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-4 py-2 text-xs font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
                  >
                    Mark as Shipped
                  </button>
                )}
                {order.status === 'SHIPPED' && (
                  <button
                    onClick={() => updateStatus(order.id, 'DELIVERED')}
                    className="rounded-[var(--radius-md)] bg-[var(--color-success)] text-white px-4 py-2 text-xs font-semibold hover:opacity-90 transition-opacity"
                  >
                    Mark as Delivered
                  </button>
                )}
                <button
                  onClick={() => updateStatus(order.id, 'CANCELLED')}
                  className="rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-error)] text-[var(--color-error)] px-4 py-2 text-xs font-semibold hover:bg-[var(--color-error-bg)] transition-colors"
                >
                  Cancel Order
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
                      ₹{Number(item.lineTotal ?? Number(item.unitPrice) * item.quantity).toLocaleString('en-IN')}
                    </span>
                  </span>
                  {order.status === 'DELIVERED' && role !== 'ADMIN' && (
                    <div className="flex items-center gap-2">
                      {returnForms[item.id]?.open ? (
                        formReturnInline(item, returnForms[item.id], setReturnForms, submitReturn)
                      ) : (
                        <button
                          onClick={() => toggleReturnForm(item.id)}
                          className="rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-error)] text-[var(--color-error)] px-3 py-1.5 text-xs font-semibold hover:bg-[var(--color-error-bg)] transition-colors"
                        >
                          Request Return
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

function formReturnInline(item, form, setReturnForms, submitReturn) {
  return (
    <div className="flex items-center gap-2">
      <input
        type="text"
        placeholder="Reason for return..."
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
        Submit
      </button>
      {form?.error && <span className="text-xs text-[var(--color-error)]">{form.error}</span>}
    </div>
  );
}

export default Orders;