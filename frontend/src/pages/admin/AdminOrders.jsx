import { Fragment, useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';

const STATUS_COLORS = {
  PLACED: 'bg-[var(--color-info-bg)] text-[var(--color-info)]',
  SHIPPED: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  DELIVERED: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
  CANCELLED: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
};

const RETURN_COLORS = {
  REQUESTED: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  APPROVED: 'bg-[var(--color-info-bg)] text-[var(--color-info)]',
  REJECTED: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
  REFUNDED: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
};

function Badge({ status, map }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${map[status] || 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]'}`}>{status}</span>;
}

export default function AdminOrders() {
  const [tab, setTab] = useState('orders');
  const [orders, setOrders] = useState([]);
  const [returns, setReturns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(null);

  const loadOrders = async () => {
    const res = await axiosClient.get('/orders');
    setOrders(res.data || []);
  };

  const loadReturns = async () => {
    const res = await axiosClient.get('/returns');
    setReturns(res.data || []);
  };

  useEffect(() => {
    Promise.all([loadOrders(), loadReturns()])
      .catch((err) => setError(err.response?.data || 'Failed to load orders'))
      .finally(() => setLoading(false));
  }, []);

  const updateStatus = async (orderId, status) => {
    setBusy(`${orderId}:${status}`);
    setError('');
    setNotice('');
    try {
      await axiosClient.put(`/orders/${orderId}/status`, { status });
      setNotice(`Order #${orderId} set to ${status}.`);
      await loadOrders();
    } catch (err) {
      setError(err.response?.data || 'Failed to update status');
    } finally {
      setBusy(null);
    }
  };

  const processReturn = async (id, action) => {
    setBusy(`return:${id}`);
    setError('');
    try {
      await axiosClient.put(`/returns/${id}/${action}`);
      await loadReturns();
    } catch (err) {
      setError(err.response?.data || 'Action failed');
    } finally {
      setBusy(null);
    }
  };

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">Loading...</p>;

  return (
    <div>
      <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold mb-6">Orders</h2>

      {error && (
        <p className="mb-4 rounded-[var(--radius-md)] bg-[var(--color-error-bg)] text-[var(--color-error)] px-3 py-2 text-sm">
          {error}
        </p>
      )}
      {notice && (
        <p className="mb-4 rounded-[var(--radius-md)] bg-[var(--color-success-bg)] text-[var(--color-success)] px-3 py-2 text-sm">
          {notice}
        </p>
      )}

      <div className="flex gap-2 mb-6">
        {[['orders', `Orders (${orders.length})`], ['returns', `Returns (${returns.length})`]].map(([key, label]) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`rounded-full px-4 py-2 text-sm font-semibold transition-colors ${
              tab === key ? 'bg-[var(--color-primary)] text-white' : 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'orders' && (
        <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-[var(--color-text-muted)] border-b border-[var(--color-border)]">
                  <th className="py-3 px-4">#</th>
                  <th className="py-3 px-4">Customer</th>
                  <th className="py-3 px-4">Date</th>
                  <th className="py-3 px-4 text-right">Total</th>
                  <th className="py-3 px-4">Coupon</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {orders.map((o) => (
                  <Fragment key={o.id}>
                    <tr className="border-b border-[var(--color-border)] hover:bg-[var(--color-card-bg-tint)]">
                      <td className="py-3 px-4 font-semibold">{o.id}</td>
                      <td className="py-3 px-4">{o.customer?.name || '—'}</td>
                      <td className="py-3 px-4 text-[var(--color-text-secondary)]">{new Date(o.orderDate).toLocaleString('en-IN')}</td>
                      <td className="py-3 px-4 text-right font-medium">
                        ₹{Number(o.totalAmount).toLocaleString('en-IN')}
                        {o.discountAmount > 0 && (
                          <span className="block text-xs text-[var(--color-success)]">-₹{Number(o.discountAmount).toLocaleString('en-IN')}</span>
                        )}
                      </td>
                      <td className="py-3 px-4">{o.couponCode ? <Badge status={o.couponCode} map={{}} /> : '—'}</td>
                      <td className="py-3 px-4"><Badge status={o.status} map={STATUS_COLORS} /></td>
                      <td className="py-3 px-4">
                        <div className="flex justify-end items-center gap-2">
                          <button onClick={() => setExpanded((p) => ({ ...p, [o.id]: !p[o.id] }))}
                            className="text-xs font-semibold text-[var(--color-primary)] hover:underline">
                            {expanded[o.id] ? 'Hide items' : 'View items'}
                          </button>
                          {(o.allowedNextStatuses || []).length > 0 ? (
                            <select
                              value=""
                              disabled={busy === `${o.id}:${o.allowedNextStatuses[0]}`}
                              onChange={(e) => { if (e.target.value) updateStatus(o.id, e.target.value); }}
                              className="rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] px-2 py-1 text-xs bg-white disabled:opacity-50"
                            >
                              <option value="">Set status…</option>
                              {/* Only the moves the backend will actually accept. DELIVERED
                                  and CANCELLED orders arrive here with an empty list. */}
                              {o.allowedNextStatuses.map((s) => (
                                <option key={s} value={s}>{s}</option>
                              ))}
                            </select>
                          ) : (
                            <span className="text-xs text-[var(--color-text-muted)]">Final</span>
                          )}
                        </div>
                      </td>
                    </tr>
                    {expanded[o.id] && (
                      <tr className="border-b border-[var(--color-border)] bg-[var(--color-card-bg-tint)]">
                        <td colSpan={7} className="px-4 py-3">
                          <ul className="text-sm divide-y divide-[var(--color-border)]">
                            {o.orderItems.map((item) => (
                              <li key={item.id} className="flex items-center justify-between py-1.5 gap-3">
                                <span className="flex-1">{item.product.name} × {item.quantity}</span>
                                <span className="text-[var(--color-text-secondary)] tabular-nums">
                                  ₹{Number(item.unitPrice).toLocaleString('en-IN')}
                                </span>
                                <span className="font-semibold tabular-nums w-24 text-right">
                                  ₹{Number(item.lineTotal ?? Number(item.unitPrice) * item.quantity).toLocaleString('en-IN')}
                                </span>
                              </li>
                            ))}
                          </ul>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'returns' && (
        <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden">
          {returns.length === 0 ? (
            <p className="p-6 text-sm text-[var(--color-text-muted)]">No return requests.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs uppercase tracking-wide text-[var(--color-text-muted)] border-b border-[var(--color-border)]">
                    <th className="py-3 px-4">#</th>
                    <th className="py-3 px-4">Item</th>
                    <th className="py-3 px-4">Customer</th>
                    <th className="py-3 px-4">Reason</th>
                    <th className="py-3 px-4">Status</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {returns.map((r) => (
                    <tr key={r.id} className="border-b border-[var(--color-border)] last:border-0">
                      <td className="py-3 px-4 font-semibold">{r.id}</td>
                      <td className="py-3 px-4">{r.orderItem?.product?.name || '—'} × {r.orderItem?.quantity ?? '—'}</td>
                      <td className="py-3 px-4">{r.customer?.name || '—'}</td>
                      <td className="py-3 px-4 text-[var(--color-text-secondary)]">{r.reason || '—'}</td>
                      <td className="py-3 px-4"><Badge status={r.status} map={RETURN_COLORS} /></td>
                      <td className="py-3 px-4">
                        <div className="flex justify-end gap-1.5">
                          {r.status === 'REQUESTED' && (
                            <>
                              <button onClick={() => processReturn(r.id, 'approve')} disabled={busy === `return:${r.id}`}
                                className="rounded-[var(--radius-md)] bg-[var(--color-success)] text-white px-3 py-1.5 text-xs font-semibold disabled:opacity-50">Approve</button>
                              <button onClick={() => processReturn(r.id, 'reject')} disabled={busy === `return:${r.id}`}
                                className="rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-error)] text-[var(--color-error)] px-3 py-1.5 text-xs font-semibold disabled:opacity-50">Reject</button>
                            </>
                          )}
                          {r.status === 'APPROVED' && (
                            <button onClick={() => processReturn(r.id, 'refund')} disabled={busy === `return:${r.id}`}
                              className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-3 py-1.5 text-xs font-semibold disabled:opacity-50">Mark Refunded</button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}