import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';

const STATUS_TABS = [
  { key: 'ALL', status: undefined },
  { key: 'OPEN', status: 'OPEN' },
  { key: 'LOCKED', status: 'LOCKED' },
  { key: 'CONFIRMED', status: 'CONFIRMED' },
  { key: 'SHIPPED', status: 'SHIPPED' },
  { key: 'DELIVERED', status: 'DELIVERED' },
  { key: 'CANCELLED', status: 'CANCELLED' },
];

const STATUS_KEYS = {
  OPEN: 'group.status.OPEN',
  LOCKED: 'group.status.LOCKED',
  AWAITING_PAYMENT: 'group.status.AWAITING_PAYMENT',
  CONFIRMED: 'group.status.CONFIRMED',
  PROCESSING: 'group.status.PROCESSING',
  SHIPPED: 'group.status.SHIPPED',
  DELIVERED: 'group.status.DELIVERED',
  CANCELLED: 'group.status.CANCELLED',
};

const STATUS_STYLES = {
  OPEN: 'bg-[var(--color-info)]/10 text-[var(--color-info)]',
  LOCKED: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  CONFIRMED: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
  PROCESSING: 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]',
  SHIPPED: 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]',
  DELIVERED: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
  CANCELLED: 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-muted)]',
};

/** Admin read-only view of every group order, with a pre-placement cancel. */
function AdminGroupOrders() {
  const { t, formatCurrency, formatNumber } = useLanguage();
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tab, setTab] = useState('ALL');

  const load = useCallback(async (status) => {
    setLoading(true);
    try {
      const res = await axiosClient.get('/admin/group-orders', {
        params: status ? { status } : {},
      });
      setGroups(res.data);
      setError('');
    } catch (err) {
      setError(err.response?.data?.error || t('admin.groups.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    load(STATUS_TABS.find((s) => s.key === tab)?.status);
  }, [tab, load]);

  const cancel = async (id) => {
    if (!window.confirm(t('admin.groups.cancelConfirm'))) return;
    try {
      await axiosClient.post(`/admin/group-orders/${id}/cancel`);
      load(STATUS_TABS.find((s) => s.key === tab)?.status);
    } catch (err) {
      setError(err.response?.data?.error || t('admin.groups.cancelFailed'));
    }
  };

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">{t('action.loading')}</p>;

  return (
    <div>
      <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold mb-6">{t('admin.nav.groupOrders')}</h2>

      <div className="flex flex-wrap gap-2 mb-6">
        {STATUS_TABS.map((s) => (
          <button
            key={s.key}
            onClick={() => setTab(s.key)}
            className={`rounded-[var(--radius-md)] px-3.5 py-1.5 text-sm font-semibold transition-colors ${
              tab === s.key
                ? 'bg-[var(--color-primary)] text-white'
                : 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)] hover:bg-[var(--color-border)]'
            }`}
          >
            {s.key === 'ALL' ? t('admin.groups.tabAll') : t(STATUS_KEYS[s.key] || s.key)}
          </button>
        ))}
      </div>

      {error && <p className="mb-4 rounded-[var(--radius-md)] bg-[var(--color-error-bg)] text-[var(--color-error)] px-4 py-2.5 text-sm">{error}</p>}

      <div className="rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden">
        {groups.length === 0 ? (
          <p className="p-6 text-sm text-[var(--color-text-muted)]">{t('admin.groups.empty')}</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-[var(--color-border)] bg-[var(--color-card-bg-tint)] text-left text-xs uppercase tracking-wide text-[var(--color-text-muted)]">
                <th className="py-3 px-4">{t('admin.groups.colName')}</th>
                <th className="py-3 px-4">{t('admin.payments.colCustomer')}</th>
                <th className="py-3 px-4 text-right">{t('admin.groups.colMembers')}</th>
                <th className="py-3 px-4">{t('admin.groups.colSplit')}</th>
                <th className="py-3 px-4 text-right">{t('admin.orders.colTotal')}</th>
                <th className="py-3 px-4">{t('admin.payments.colStatus')}</th>
                <th className="py-3 px-4">{t('admin.payments.colDate')}</th>
                <th className="py-3 px-4 text-right">{t('admin.categories.colActions')}</th>
              </tr>
            </thead>
            <tbody>
              {groups.map((g) => (
                <tr key={g.id} className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-card-bg-tint)]/50">
                  <td className="py-3 px-4 font-semibold">
                    <Link to={`/group-orders/${g.id}`} className="text-[var(--color-primary)] hover:underline">
                      {g.name}
                    </Link>
                    {g.inviteCode && <span className="block text-xs font-mono text-[var(--color-text-muted)]">{g.inviteCode}</span>}
                  </td>
                  <td className="py-3 px-4">{g.hostName}</td>
                  <td className="py-3 px-4 text-right">{formatNumber(g.memberCount)}</td>
                  <td className="py-3 px-4">{t(`group.split.${g.splitMethod}`)}</td>
                  <td className="py-3 px-4 text-right font-semibold">{g.totalAmount != null ? formatCurrency(g.totalAmount) : '—'}</td>
                  <td className="py-3 px-4">
                    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${STATUS_STYLES[g.status] || ''}`}>
                      {t(STATUS_KEYS[g.status] || g.status)}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-xs text-[var(--color-text-muted)]">
                    {g.createdAt ? new Date(g.createdAt).toLocaleDateString() : '—'}
                  </td>
                  <td className="py-3 px-4 text-right">
                    {['OPEN', 'LOCKED', 'AWAITING_PAYMENT'].includes(g.status) && (
                      <button
                        onClick={() => cancel(g.id)}
                        className="rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-error)] text-[var(--color-error)] px-3 py-1.5 text-xs font-semibold hover:bg-[var(--color-error-bg)]"
                      >
                        {t('admin.groups.cancel')}
                      </button>
                    )}
                    {g.placedOrderId != null && (
                      <Link to="/admin/orders" className="ml-2 text-xs font-semibold text-[var(--color-primary)] hover:underline">
                        #{g.placedOrderId}
                      </Link>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

export default AdminGroupOrders;
