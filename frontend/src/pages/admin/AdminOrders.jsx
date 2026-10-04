import { Fragment, useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';

const STATUS_COLORS = {
  PLACED: 'bg-[var(--color-info-bg)] text-[var(--color-info)]',
  PACKED: 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]',
  SHIPPED: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  OUT_FOR_DELIVERY: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  DELIVERED: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
  CANCELLED: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
};

const RETURN_COLORS = {
  REQUESTED: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  APPROVED: 'bg-[var(--color-info-bg)] text-[var(--color-info)]',
  REJECTED: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
  REFUNDED: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
};

// Kept as literals so the i18n checker can see them; the checker cannot follow a
// template-built key.
const RETURN_STATUS_KEYS = {
  REQUESTED: 'admin.return.REQUESTED',
  APPROVED: 'admin.return.APPROVED',
  REJECTED: 'admin.return.REJECTED',
  REFUNDED: 'admin.return.REFUNDED',
};

// The API sends enums for both order status and return status; the badge colour
// comes from the map but the text is translated in the active language.
function Badge({ status, map, label }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${map[status] || 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]'}`}>{label ?? status}</span>;
}

export default function AdminOrders() {
  const { t, formatCurrency, formatNumber, formatDateTime } = useLanguage();
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
      .catch((err) => setError(err.response?.data || t('admin.orders.loadFailed')))
      .finally(() => setLoading(false));
  }, []);

  const updateStatus = async (orderId, status) => {
    setBusy(`${orderId}:${status}`);
    setError('');
    setNotice('');
    try {
      await axiosClient.put(`/orders/${orderId}/status`, { status });
      setNotice(t('admin.orders.statusUpdated', {
        id: formatNumber(orderId),
        status: t(`status.${status}`),
      }));
      await loadOrders();
    } catch (err) {
      setError(err.response?.data || t('admin.orders.statusUpdateFailed'));
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
      setError(err.response?.data || t('admin.coupons.actionFailed'));
    } finally {
      setBusy(null);
    }
  };

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">{t('admin.orders.loading')}</p>;

  return (
    <div>
      <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold mb-6">{t('admin.nav.orders')}</h2>

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
        {[
          ['orders', t('admin.orders.tabOrders', { count: formatNumber(orders.length) })],
          ['returns', t('admin.orders.tabReturns', { count: formatNumber(returns.length) })],
        ].map(([key, label]) => (
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
                  <th className="py-3 px-4">{t('admin.payments.colCustomer')}</th>
                  <th className="py-3 px-4">{t('admin.payments.colDate')}</th>
                  <th className="py-3 px-4 text-right">{t('admin.orders.colTotal')}</th>
                  <th className="py-3 px-4">{t('admin.orders.colCoupon')}</th>
                  <th className="py-3 px-4">{t('admin.payments.colStatus')}</th>
                  <th className="py-3 px-4 text-right">{t('admin.categories.colActions')}</th>
                </tr>
              </thead>
              <tbody>
                {orders.map((o) => (
                  <Fragment key={o.id}>
                    <tr className="border-b border-[var(--color-border)] hover:bg-[var(--color-card-bg-tint)]">
                      <td className="py-3 px-4 font-semibold">{o.id}</td>
                      <td className="py-3 px-4">{o.customer?.name || '—'}</td>
                      <td className="py-3 px-4 text-[var(--color-text-secondary)]">{formatDateTime(o.orderDate)}</td>
                      <td className="py-3 px-4 text-right font-medium">
                        {formatCurrency(o.totalAmount)}
                        {o.discountAmount > 0 && (
                          <span className="block text-xs text-[var(--color-success)]">-{formatCurrency(o.discountAmount)}</span>
                        )}
                      </td>
                      <td className="py-3 px-4">{o.couponCode ? <Badge status={o.couponCode} map={{}} /> : '—'}</td>
                      <td className="py-3 px-4"><Badge status={o.status} map={STATUS_COLORS} label={t(`status.${o.status}`)} /></td>
                      <td className="py-3 px-4">
                        <div className="flex justify-end items-center gap-2">
                          <button onClick={() => setExpanded((p) => ({ ...p, [o.id]: !p[o.id] }))}
                            className="text-xs font-semibold text-[var(--color-primary)] hover:underline">
                            {expanded[o.id] ? t('admin.orders.hideItems') : t('admin.orders.viewItems')}
                          </button>
                          {(o.allowedNextStatuses || []).length > 0 ? (
                            <select
                              value=""
                              disabled={busy === `${o.id}:${o.allowedNextStatuses[0]}`}
                              onChange={(e) => { if (e.target.value) updateStatus(o.id, e.target.value); }}
                              className="rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] px-2 py-1 text-xs bg-white disabled:opacity-50"
                            >
                              <option value="">{t('admin.orders.setStatus')}</option>
                              {/* Only the moves the backend will actually accept. DELIVERED
                                  and CANCELLED orders arrive here with an empty list. */}
                              {o.allowedNextStatuses.map((s) => (
                                <option key={s} value={s}>{t(`status.${s}`)}</option>
                              ))}
                            </select>
                          ) : (
                            <span className="text-xs text-[var(--color-text-muted)]">{t('admin.orders.final')}</span>
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
                                  {formatCurrency(item.unitPrice)}
                                </span>
                                <span className="font-semibold tabular-nums w-24 text-right">
                                  {formatCurrency(item.lineTotal ?? Number(item.unitPrice) * item.quantity)}
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
            <p className="p-6 text-sm text-[var(--color-text-muted)]">{t('admin.orders.noReturns')}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs uppercase tracking-wide text-[var(--color-text-muted)] border-b border-[var(--color-border)]">
                    <th className="py-3 px-4">#</th>
                    <th className="py-3 px-4">{t('admin.orders.colItem')}</th>
                    <th className="py-3 px-4">{t('admin.payments.colCustomer')}</th>
                    <th className="py-3 px-4">{t('admin.orders.colReason')}</th>
                    <th className="py-3 px-4">{t('admin.payments.colStatus')}</th>
                    <th className="py-3 px-4 text-right">{t('admin.categories.colActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {returns.map((r) => (
                    <tr key={r.id} className="border-b border-[var(--color-border)] last:border-0">
                      <td className="py-3 px-4 font-semibold">{r.id}</td>
                      <td className="py-3 px-4">{r.orderItem?.product?.name || '—'} × {r.orderItem?.quantity ?? '—'}</td>
                      <td className="py-3 px-4">{r.customer?.name || '—'}</td>
                      <td className="py-3 px-4 text-[var(--color-text-secondary)]">{r.reason || '—'}</td>
                      <td className="py-3 px-4"><Badge status={r.status} map={RETURN_COLORS} label={t(RETURN_STATUS_KEYS[r.status] || RETURN_STATUS_KEYS.REQUESTED)} /></td>
                      <td className="py-3 px-4">
                        <div className="flex justify-end gap-1.5">
                          {r.status === 'REQUESTED' && (
                            <>
                              <button onClick={() => processReturn(r.id, 'approve')} disabled={busy === `return:${r.id}`}
                                className="rounded-[var(--radius-md)] bg-[var(--color-success)] text-white px-3 py-1.5 text-xs font-semibold disabled:opacity-50">{t('admin.orders.approve')}</button>
                              <button onClick={() => processReturn(r.id, 'reject')} disabled={busy === `return:${r.id}`}
                                className="rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-error)] text-[var(--color-error)] px-3 py-1.5 text-xs font-semibold disabled:opacity-50">{t('admin.orders.reject')}</button>
                            </>
                          )}
                          {r.status === 'APPROVED' && (
                            <button onClick={() => processReturn(r.id, 'refund')} disabled={busy === `return:${r.id}`}
                              className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-3 py-1.5 text-xs font-semibold disabled:opacity-50">{t('admin.orders.markRefunded')}</button>
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