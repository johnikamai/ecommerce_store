import { useState, useEffect } from 'react';
import { Search, Ban, CheckCircle2, Trash2, History, X, Eye, Phone, MapPin } from 'lucide-react';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';

// Kept as literals so the i18n checker can see them.
const ROLE_KEYS = {
  ADMIN: 'admin.role.ADMIN',
  STAFF: 'admin.role.STAFF',
  CUSTOMER: 'admin.role.CUSTOMER',
};

function formatAddress(a) {
  const parts = [a.name, a.addressLine, a.city, a.state ? `${a.state} ${a.pincode || ''}`.trim() : a.pincode].filter(Boolean);
  return parts.join(', ') + (a.phone ? ` ${a.phone}` : '');
}

export default function AdminCustomers() {
  const { t, formatCurrency, formatNumber, formatDate } = useLanguage();
  const [users, setUsers] = useState([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [history, setHistory] = useState(null); // { user, orders }
  const [detail, setDetail] = useState(null); // customer profile + addresses
  const [detailError, setDetailError] = useState('');
  // Read from localStorage rather than trusting a prop: only an admin may promote
  // anyone, and staff must never see the control even if the API would allow it.
  const myRole = localStorage.getItem('role');
  const adminCount = users.filter((u) => u.role === 'ADMIN').length;

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await axiosClient.get('/admin/users');
      setUsers(res.data);
    } catch {
      setError(t('admin.customers.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const viewDetail = async (user) => {
    setDetail({ loading: true, name: user.customerName || user.username });
    setDetailError('');
    try {
      const res = await axiosClient.get(`/admin/customers/${user.customerId}`);
      setDetail(res.data);
    } catch (err) {
      setDetail(null);
      setDetailError(err.response?.data || t('admin.customers.detailLoadFailed'));
    }
  };

  const toggleEnabled = async (user) => {
    if (!window.confirm(t(user.enabled ? 'admin.customers.blockConfirm' : 'admin.customers.activateConfirm', { username: user.username }))) return;
    try {
      await axiosClient.put(`/admin/users/${user.id}/enabled`, { enabled: !user.enabled });
      load();
    } catch (err) {
      alert(err.response?.data || t('admin.coupons.actionFailed'));
    }
  };

  const remove = async (user) => {
    if (!window.confirm(t('admin.customers.deleteConfirm', { username: user.username }))) return;
    try {
      await axiosClient.delete(`/admin/users/${user.id}`);
      load();
    } catch (err) {
      alert(err.response?.data || t('admin.customers.deleteFailed'));
    }
  };

  /**
   * Promotes a customer to staff, or sends them back to being a customer.
   * Staff can work the catalog and read orders but cannot touch payments,
   * refunds or discounts, so this is deliberately a smaller step than admin.
   */
  const changeRole = async (user, role) => {
    if (!window.confirm(
      role === 'STAFF'
        ? t('admin.customers.makeStaffConfirm', { username: user.username })
        : t('admin.customers.unstaffConfirm', { username: user.username })
    )) return;
    try {
      await axiosClient.put(`/admin/users/${user.id}/role`, { role });
      load();
    } catch (err) {
      alert(err.response?.data || t('admin.customers.roleChangeFailed'));
    }
  };

  const viewOrders = async (user) => {
    try {
      const res = await axiosClient.get(`/admin/customers/${user.customerId}/orders`);
      setHistory({ user, orders: res.data });
    } catch (err) {
      alert(err.response?.data || t('admin.customers.historyLoadFailed'));
    }
  };

  const filtered = users.filter(
    (u) => !search || u.username.toLowerCase().includes(search.toLowerCase()) || (u.email || '').toLowerCase().includes(search.toLowerCase())
  );

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">{t('admin.customers.loading')}</p>;

  if (error) return <p className="py-12 text-center text-[var(--color-error)]">{error}</p>;

  return (
    <div>
      <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold mb-6">{t('admin.nav.customers')}</h2>

      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] mb-6 p-4 flex items-center gap-2">
        <div className="flex items-center gap-2 px-3 h-10 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] w-full max-w-sm">
          <Search size={16} className="text-[var(--color-text-muted)]" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={t('admin.customers.searchPlaceholder', { count: formatNumber(users.length) })}
            className="w-full bg-transparent outline-none text-sm"
          />
        </div>
      </div>

      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wide text-[var(--color-text-muted)] border-b border-[var(--color-border)]">
                <th className="py-3 px-4">{t('admin.customers.colUser')}</th>
                <th className="py-3 px-4">{t('admin.customers.colContact')}</th>
                <th className="py-3 px-4">{t('admin.customers.colRole')}</th>
                <th className="py-3 px-4 text-right">{t('admin.customers.colOrders')}</th>
                <th className="py-3 px-4 text-right">{t('admin.customers.colTotalSpent')}</th>
                <th className="py-3 px-4">{t('admin.payments.colStatus')}</th>
                <th className="py-3 px-4 text-right">{t('admin.categories.colActions')}</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((u) => (
                <tr key={u.id} className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-card-bg-tint)]">
                  <td className="py-3 px-4">
                    <div className="font-semibold">{u.username}</div>
                    <div className="text-xs text-[var(--color-text-muted)]">{u.email}</div>
                  </td>
                  <td className="py-3 px-4">
                    {u.phone ? (
                      <div className="flex items-center gap-1.5 text-xs">
                        <Phone size={12} className="text-[var(--color-text-muted)]" />
                        {u.phone}
                      </div>
                    ) : (
                      <span className="text-xs text-[var(--color-text-muted)]">—</span>
                    )}
                    <div className="flex items-center gap-1.5 text-xs text-[var(--color-text-muted)] mt-0.5">
                      <MapPin size={12} />
                      {t(
                        (u.addressCount || 0) === 1
                          ? 'admin.customers.addressCountOne'
                          : 'admin.customers.addressCountOther',
                        { count: formatNumber(u.addressCount || 0) },
                      )}
                    </div>
                  </td>
                  <td className="py-3 px-4">
                    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${u.role === 'ADMIN' ? 'bg-[var(--color-error-bg)] text-[var(--color-error)]' : u.role === 'STAFF' ? 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]' : 'bg-[var(--color-info-bg)] text-[var(--color-info)]'}`}>
                      {t(ROLE_KEYS[u.role] || ROLE_KEYS.CUSTOMER)}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right">{formatNumber(u.orderCount)}</td>
                  <td className="py-3 px-4 text-right">{formatCurrency(u.totalSpend || 0)}</td>
                  <td className="py-3 px-4">
                    <span className={`text-xs font-semibold ${u.enabled ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'}`}>
                      {u.enabled ? t('admin.coupons.active') : t('admin.customers.blocked')}
                    </span>
                  </td>
                  <td className="py-3 px-4">
                    <div className="flex justify-end gap-1.5">
                      {/* Promote/demote. Never offered on the row of the only
                          admin - the server refuses that too, but a button that
                          always fails is worse than no button. */}
                      {myRole === 'ADMIN' && !(u.role === 'ADMIN' && adminCount <= 1) && (
                        <button
                          onClick={() => changeRole(u, u.role === 'STAFF' ? 'CUSTOMER' : 'STAFF')}
                          aria-label={u.role === 'STAFF' ? t('admin.customers.unstaff') : t('admin.customers.makeStaff')}
                          title={u.role === 'STAFF'
                            ? t('admin.customers.removeStaffAria')
                            : t('admin.customers.makeStaffTitle')}
                          className={`px-2 h-8 rounded-[var(--radius-md)] text-[11px] font-semibold transition-colors ${u.role === 'STAFF'
                            ? 'bg-[var(--color-warning-bg)] text-[var(--color-warning)] hover:opacity-80'
                            : 'bg-[var(--color-info-bg)] text-[var(--color-info)] hover:opacity-80'}`}
                        >
                          {u.role === 'STAFF' ? t('admin.customers.unstaff') : t('admin.customers.makeStaff')}
                        </button>
                      )}
                      {u.role !== 'ADMIN' && u.customerId && (
                        <>
                          <button onClick={() => viewDetail(u)} aria-label={t('admin.customers.detailsAria')}
                            className="w-8 h-8 rounded-[var(--radius-md)] flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-border)] transition-colors">
                            <Eye size={15} />
                          </button>
                          <button onClick={() => viewOrders(u)} aria-label={t('admin.customers.historyAria')}
                            className="w-8 h-8 rounded-[var(--radius-md)] flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-border)] transition-colors">
                            <History size={15} />
                          </button>
                        </>
                      )}
                      {u.role !== 'ADMIN' && (
                        <button onClick={() => toggleEnabled(u)} aria-label={t('admin.customers.blockActivateAria')}
                          className={`w-8 h-8 rounded-[var(--radius-md)] flex items-center justify-center hover:bg-[var(--color-border)] transition-colors ${u.enabled ? 'text-[var(--color-warning)]' : 'text-[var(--color-success)]'}`}>
                          {u.enabled ? <Ban size={15} /> : <CheckCircle2 size={15} />}
                        </button>
                      )}
                      {u.role !== 'ADMIN' && (
                        <button onClick={() => remove(u)} aria-label={t('common.delete')}
                          className="w-8 h-8 rounded-[var(--radius-md)] flex items-center justify-center text-[var(--color-error)] hover:bg-[var(--color-error-bg)] transition-colors">
                          <Trash2 size={15} />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {detailError && (
        <div className="fixed inset-0 z-[var(--z-modal)] bg-black/40 flex items-start justify-center p-4" onClick={() => setDetailError('')}>
          <div className="w-full max-w-md rounded-[var(--radius-xl)] bg-[var(--color-surface)] shadow-[var(--shadow-lg)] mt-16 p-5" onClick={(e) => e.stopPropagation()}>
            <p className="text-sm text-[var(--color-error)]">{detailError}</p>
            <button onClick={() => setDetailError('')} className="mt-4 w-full py-2 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-sm font-semibold">{t('common.close')}</button>
          </div>
        </div>
      )}

      {detail && (
        <div className="fixed inset-0 z-[var(--z-modal)] bg-black/40 flex items-start justify-center p-4 overflow-y-auto" onClick={() => setDetail(null)}>
          <div className="w-full max-w-2xl rounded-[var(--radius-xl)] bg-[var(--color-surface)] shadow-[var(--shadow-lg)] mt-10" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-5 border-b border-[var(--color-border)]">
              <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold">{t('admin.customers.detailTitle', { name: detail.name })}</h3>
              <button onClick={() => setDetail(null)} className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-[var(--color-card-bg-tint)]" aria-label={t('common.close')}>
                <X size={18} />
              </button>
            </div>

            {detail.loading ? (
              <p className="p-5 text-sm text-[var(--color-text-muted)]">{t('admin.customers.detailLoading')}</p>
            ) : (
              <div className="p-5 space-y-5">
                <div className="grid grid-cols-2 md:grid-cols-3 gap-4 text-sm">
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">{t('auth.email')}</div>
                    <div className="font-medium break-all">{detail.email || '—'}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">{t('account.profile.phone')}</div>
                    <div className="font-medium">{detail.phone || '—'}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">{t('admin.customers.fieldTier')}</div>
                    <div className="font-medium">{detail.tier || '—'}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">{t('admin.customers.fieldLoyalty')}</div>
                    <div className="font-medium">{formatNumber(detail.loyaltyPoints ?? 0)}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">{t('admin.customers.colOrders')}</div>
                    <div className="font-medium">{formatNumber(detail.orderCount ?? 0)}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">{t('admin.customers.colTotalSpent')}</div>
                    <div className="font-medium">{formatCurrency(detail.totalSpend || 0)}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">{t('admin.customers.fieldReferralCode')}</div>
                    <div className="font-medium">{detail.referralCode || '—'}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">{t('admin.customers.fieldReferredBy')}</div>
                    <div className="font-medium">{detail.referredBy || '—'}</div>
                  </div>
                </div>

                {detail.shippingAddress && (
                  <div className="text-sm">
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">{t('admin.customers.defaultAddress')}</div>
                    <div className="font-medium">{detail.shippingAddress}</div>
                  </div>
                )}

                <div>
                  <h4 className="text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)] mb-2">
                    {t('admin.customers.savedAddresses', { count: formatNumber(detail.addresses?.length || 0) })}
                  </h4>
                  {detail.addresses?.length ? (
                    <ul className="text-sm divide-y divide-[var(--color-border)]">
                      {detail.addresses.map((a) => (
                        <li key={a.id} className="py-2.5 flex items-start gap-2">
                          <MapPin size={14} className="text-[var(--color-text-muted)] mt-0.5 shrink-0" />
                          <div>
                            <div className="font-semibold">
                              {a.label || t('admin.customers.addressFallback')}
                              {a.isDefault && <span className="ml-2 text-[10px] font-bold uppercase tracking-wide text-[var(--color-primary)]">{t('cart.defaultBadge')}</span>}
                            </div>
                            <div className="text-[var(--color-text-secondary)]">{formatAddress(a)}</div>
                          </div>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-sm text-[var(--color-text-muted)]">{t('admin.customers.noSavedAddresses')}</p>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {history && (
        <div className="fixed inset-0 z-[var(--z-modal)] bg-black/40 flex items-start justify-center p-4 overflow-y-auto" onClick={() => setHistory(null)}>
          <div className="w-full max-w-2xl rounded-[var(--radius-xl)] bg-[var(--color-surface)] shadow-[var(--shadow-lg)] mt-10" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-5 border-b border-[var(--color-border)]">
              <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold">{t('admin.customers.historyTitle', { username: history.user.username })}</h3>
              <button onClick={() => setHistory(null)} className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-[var(--color-card-bg-tint)]" aria-label={t('common.close')}>
                <X size={18} />
              </button>
            </div>
            <div className="p-5">
              {history.orders.length === 0 ? (
                <p className="text-sm text-[var(--color-text-muted)]">{t('admin.customers.noOrders')}</p>
              ) : (
                <ul className="text-sm divide-y divide-[var(--color-border)]">
                  {history.orders.map((o) => (
                    <li key={o.id} className="flex items-center justify-between py-2.5">
                      <div>
                        <div className="font-semibold">{t('admin.customers.orderNumber', { id: formatNumber(o.id) })}</div>
                        <div className="text-xs text-[var(--color-text-muted)]">
                          {formatDate(o.orderDate)} · {t('admin.customers.itemCount', { count: formatNumber(o.orderItems.length) })}
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="font-medium">{formatCurrency(o.totalAmount)}</div>
                        <div className="text-xs text-[var(--color-text-muted)]">{t(`status.${o.status}`)}</div>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}