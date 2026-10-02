import { useState, useEffect } from 'react';
import { Search, Ban, CheckCircle2, Trash2, History, X, Eye, Phone, MapPin } from 'lucide-react';
import axiosClient from '../../api/axiosClient';

function formatAddress(a) {
  const parts = [a.name, a.addressLine, a.city, a.state ? `${a.state} ${a.pincode || ''}`.trim() : a.pincode].filter(Boolean);
  return parts.join(', ') + (a.phone ? ` ${a.phone}` : '');
}

export default function AdminCustomers() {
  const [users, setUsers] = useState([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [history, setHistory] = useState(null); // { user, orders }
  const [detail, setDetail] = useState(null); // customer profile + addresses
  const [detailError, setDetailError] = useState('');

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await axiosClient.get('/admin/users');
      setUsers(res.data);
    } catch {
      setError('Failed to load customers. Please try again.');
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
      setDetailError(err.response?.data || 'Could not load customer details');
    }
  };

  const toggleEnabled = async (user) => {
    if (!window.confirm(`${user.enabled ? 'Block' : 'Activate'} "${user.username}"?`)) return;
    try {
      await axiosClient.put(`/admin/users/${user.id}/enabled`, { enabled: !user.enabled });
      load();
    } catch (err) {
      alert(err.response?.data || 'Action failed');
    }
  };

  const remove = async (user) => {
    if (!window.confirm(`Delete user "${user.username}"? This cannot be undone.`)) return;
    try {
      await axiosClient.delete(`/admin/users/${user.id}`);
      load();
    } catch (err) {
      alert(err.response?.data || 'Delete failed — user may be an admin or has orders.');
    }
  };

  const viewOrders = async (user) => {
    try {
      const res = await axiosClient.get(`/admin/customers/${user.customerId}/orders`);
      setHistory({ user, orders: res.data });
    } catch (err) {
      alert(err.response?.data || 'Could not load order history');
    }
  };

  const filtered = users.filter(
    (u) => !search || u.username.toLowerCase().includes(search.toLowerCase()) || (u.email || '').toLowerCase().includes(search.toLowerCase())
  );

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">Loading customers...</p>;

  if (error) return <p className="py-12 text-center text-[var(--color-error)]">{error}</p>;

  return (
    <div>
      <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold mb-6">Customers</h2>

      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] mb-6 p-4 flex items-center gap-2">
        <div className="flex items-center gap-2 px-3 h-10 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] w-full max-w-sm">
          <Search size={16} className="text-[var(--color-text-muted)]" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={`Search ${users.length} users...`}
            className="w-full bg-transparent outline-none text-sm"
          />
        </div>
      </div>

      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wide text-[var(--color-text-muted)] border-b border-[var(--color-border)]">
                <th className="py-3 px-4">User</th>
                <th className="py-3 px-4">Contact</th>
                <th className="py-3 px-4">Role</th>
                <th className="py-3 px-4 text-right">Orders</th>
                <th className="py-3 px-4 text-right">Total Spent</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4 text-right">Actions</th>
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
                      {u.addressCount || 0} address{(u.addressCount || 0) === 1 ? '' : 'es'}
                    </div>
                  </td>
                  <td className="py-3 px-4">
                    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${u.role === 'ADMIN' ? 'bg-[var(--color-error-bg)] text-[var(--color-error)]' : 'bg-[var(--color-info-bg)] text-[var(--color-info)]'}`}>
                      {u.role}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right">{u.orderCount}</td>
                  <td className="py-3 px-4 text-right">₹{Number(u.totalSpend || 0).toLocaleString('en-IN')}</td>
                  <td className="py-3 px-4">
                    <span className={`text-xs font-semibold ${u.enabled ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'}`}>
                      {u.enabled ? 'Active' : 'Blocked'}
                    </span>
                  </td>
                  <td className="py-3 px-4">
                    <div className="flex justify-end gap-1.5">
                      {u.role !== 'ADMIN' && u.customerId && (
                        <>
                          <button onClick={() => viewDetail(u)} aria-label="Customer details"
                            className="w-8 h-8 rounded-[var(--radius-md)] flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-border)] transition-colors">
                            <Eye size={15} />
                          </button>
                          <button onClick={() => viewOrders(u)} aria-label="Order history"
                            className="w-8 h-8 rounded-[var(--radius-md)] flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-border)] transition-colors">
                            <History size={15} />
                          </button>
                        </>
                      )}
                      {u.role !== 'ADMIN' && (
                        <button onClick={() => toggleEnabled(u)} aria-label="Block/activate"
                          className={`w-8 h-8 rounded-[var(--radius-md)] flex items-center justify-center hover:bg-[var(--color-border)] transition-colors ${u.enabled ? 'text-[var(--color-warning)]' : 'text-[var(--color-success)]'}`}>
                          {u.enabled ? <Ban size={15} /> : <CheckCircle2 size={15} />}
                        </button>
                      )}
                      {u.role !== 'ADMIN' && (
                        <button onClick={() => remove(u)} aria-label="Delete"
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
            <button onClick={() => setDetailError('')} className="mt-4 w-full py-2 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-sm font-semibold">Close</button>
          </div>
        </div>
      )}

      {detail && (
        <div className="fixed inset-0 z-[var(--z-modal)] bg-black/40 flex items-start justify-center p-4 overflow-y-auto" onClick={() => setDetail(null)}>
          <div className="w-full max-w-2xl rounded-[var(--radius-xl)] bg-[var(--color-surface)] shadow-[var(--shadow-lg)] mt-10" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-5 border-b border-[var(--color-border)]">
              <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold">Customer details — {detail.name}</h3>
              <button onClick={() => setDetail(null)} className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-[var(--color-card-bg-tint)]" aria-label="Close">
                <X size={18} />
              </button>
            </div>

            {detail.loading ? (
              <p className="p-5 text-sm text-[var(--color-text-muted)]">Loading details...</p>
            ) : (
              <div className="p-5 space-y-5">
                <div className="grid grid-cols-2 md:grid-cols-3 gap-4 text-sm">
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">Email</div>
                    <div className="font-medium break-all">{detail.email || '—'}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">Phone</div>
                    <div className="font-medium">{detail.phone || '—'}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">Tier</div>
                    <div className="font-medium">{detail.tier || '—'}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">Loyalty points</div>
                    <div className="font-medium">{detail.loyaltyPoints ?? 0}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">Orders</div>
                    <div className="font-medium">{detail.orderCount ?? 0}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">Total spent</div>
                    <div className="font-medium">₹{Number(detail.totalSpend || 0).toLocaleString('en-IN')}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">Referral code</div>
                    <div className="font-medium">{detail.referralCode || '—'}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">Referred by</div>
                    <div className="font-medium">{detail.referredBy || '—'}</div>
                  </div>
                </div>

                {detail.shippingAddress && (
                  <div className="text-sm">
                    <div className="text-xs text-[var(--color-text-muted)] mb-0.5">Default delivery address</div>
                    <div className="font-medium">{detail.shippingAddress}</div>
                  </div>
                )}

                <div>
                  <h4 className="text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)] mb-2">
                    Saved addresses ({detail.addresses?.length || 0})
                  </h4>
                  {detail.addresses?.length ? (
                    <ul className="text-sm divide-y divide-[var(--color-border)]">
                      {detail.addresses.map((a) => (
                        <li key={a.id} className="py-2.5 flex items-start gap-2">
                          <MapPin size={14} className="text-[var(--color-text-muted)] mt-0.5 shrink-0" />
                          <div>
                            <div className="font-semibold">
                              {a.label || 'Address'}
                              {a.isDefault && <span className="ml-2 text-[10px] font-bold uppercase tracking-wide text-[var(--color-primary)]">Default</span>}
                            </div>
                            <div className="text-[var(--color-text-secondary)]">{formatAddress(a)}</div>
                          </div>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-sm text-[var(--color-text-muted)]">No saved addresses.</p>
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
              <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold">Order history — {history.user.username}</h3>
              <button onClick={() => setHistory(null)} className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-[var(--color-card-bg-tint)]" aria-label="Close">
                <X size={18} />
              </button>
            </div>
            <div className="p-5">
              {history.orders.length === 0 ? (
                <p className="text-sm text-[var(--color-text-muted)]">No orders yet.</p>
              ) : (
                <ul className="text-sm divide-y divide-[var(--color-border)]">
                  {history.orders.map((o) => (
                    <li key={o.id} className="flex items-center justify-between py-2.5">
                      <div>
                        <div className="font-semibold">Order #{o.id}</div>
                        <div className="text-xs text-[var(--color-text-muted)]">{new Date(o.orderDate).toLocaleDateString('en-IN')} · {o.orderItems.length} item(s)</div>
                      </div>
                      <div className="text-right">
                        <div className="font-medium">₹{Number(o.totalAmount).toLocaleString('en-IN')}</div>
                        <div className="text-xs text-[var(--color-text-muted)]">{o.status}</div>
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