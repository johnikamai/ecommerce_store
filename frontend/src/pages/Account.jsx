import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Bell, MapPin, KeyRound, UserRound, Trash2, Plus, Check } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { getCustomerId } from '../utils/customer';

const NOTIF_TYPE_LABELS = {
  ORDER: 'Order',
  PAYMENT: 'Payment',
  SHIPPING: 'Shipping',
  DELIVERY: 'Delivery',
  RESTOCK: 'Restock',
  OFFER: 'Offer',
};

function formatAddress(a) {
  const parts = [a.name, a.addressLine, a.city, a.state ? `${a.state} ${a.pincode || ''}`.trim() : a.pincode].filter(Boolean);
  return parts.join(', ') + (a.phone ? ` ${a.phone}` : '');
}

function Section({ title, icon: Icon, children }) {
  return (
    <div className="rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-6">
      <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-4 flex items-center gap-2">
        <Icon size={18} className="text-[var(--color-primary)]" /> {title}
      </h3>
      {children}
    </div>
  );
}

function Account() {
  const customerId = getCustomerId();
  const username = localStorage.getItem('username') || '';

  const [profile, setProfile] = useState(null);
  const [profileMsg, setProfileMsg] = useState('');
  const [addresses, setAddresses] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [notifMsg, setNotifMsg] = useState('');
  const [pwMsg, setPwMsg] = useState('');

  // Profile form
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [shippingAddress, setShippingAddress] = useState('');
  const [savingProfile, setSavingProfile] = useState(false);

  // Address form
  const [showAddrForm, setShowAddrForm] = useState(false);
  const [editingAddrId, setEditingAddrId] = useState(null);
  const [addrForm, setAddrForm] = useState({ label: '', name: '', addressLine: '', city: '', state: '', pincode: '', phone: '', isDefault: false });
  const [addrError, setAddrError] = useState('');

  // Change password
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [savingPw, setSavingPw] = useState(false);

  const loadAll = async () => {
    const [profileRes, addrRes, notifRes] = await Promise.all([
      axiosClient.get(`/customers/${customerId}`),
      axiosClient.get(`/addresses/customer/${customerId}`),
      axiosClient.get(`/notifications/customer/${customerId}`),
    ]);
    setProfile(profileRes.data);
    setName(profileRes.data.name || '');
    setEmail(profileRes.data.email || '');
    setPhone(profileRes.data.phone || '');
    setShippingAddress(profileRes.data.shippingAddress || '');
    setAddresses(addrRes.data || []);
    setNotifications(notifRes.data || []);
  };

  useEffect(() => {
    loadAll().catch(() => {});
  }, [customerId]);

  const saveProfile = async (e) => {
    e.preventDefault();
    setSavingProfile(true);
    setProfileMsg('');
    try {
      const res = await axiosClient.put(`/customers/${customerId}`, { name, email, phone, shippingAddress });
      setProfile(res.data);
      localStorage.setItem('customerName', res.data.name || '');
      setProfileMsg('✓ Profile saved');
      setTimeout(() => setProfileMsg(''), 2500);
    } catch (err) {
      setProfileMsg(typeof err.response?.data === 'string' ? err.response.data : 'Failed to update profile');
    } finally {
      setSavingProfile(false);
    }
  };

  const resetAddrForm = () => {
    setAddrForm({ label: '', name: '', addressLine: '', city: '', state: '', pincode: '', phone: '', isDefault: false });
    setEditingAddrId(null);
    setAddrError('');
  };

  const saveAddress = async (e) => {
    e.preventDefault();
    setAddrError('');
    const payload = { ...addrForm, isDefault: Boolean(addrForm.isDefault) };
    try {
      if (editingAddrId) {
        await axiosClient.put(`/addresses/${editingAddrId}`, payload);
      } else {
        await axiosClient.post('/addresses', { ...payload, customerId });
      }
      resetAddrForm();
      setShowAddrForm(false);
      loadAll().catch(() => {});
    } catch (err) {
      setAddrError(err.response?.data || 'Failed to save address');
    }
  };

  const deleteAddress = async (id) => {
    if (!window.confirm('Delete this address?')) return;
    try {
      await axiosClient.delete(`/addresses/${id}`);
      loadAll().catch(() => {});
    } catch (err) {
      alert(err.response?.data || 'Failed to delete address');
    }
  };

  const startEditAddress = (a) => {
    setEditingAddrId(a.id);
    setAddrForm({
      label: a.label || '',
      name: a.name || '',
      addressLine: a.addressLine || '',
      city: a.city || '',
      state: a.state || '',
      pincode: a.pincode || '',
      phone: a.phone || '',
      isDefault: Boolean(a.isDefault),
    });
    setShowAddrForm(true);
  };

  const changePassword = async (e) => {
    e.preventDefault();
    setPwMsg('');
    if (newPassword !== confirmPassword) {
      setPwMsg("New passwords don't match");
      return;
    }
    setSavingPw(true);
    try {
      const res = await axiosClient.post('/auth/change-password', {
        username,
        currentPassword,
        newPassword,
      });
      setPwMsg(`✓ ${res.data.message || 'Password changed'}`);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err) {
      setPwMsg(err.response?.data || 'Failed to change password');
    } finally {
      setSavingPw(false);
    }
  };

  const markAllRead = async () => {
    try {
      await axiosClient.put(`/notifications/customer/${customerId}/read-all`);
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      setNotifMsg('✓ Marked as read');
      setTimeout(() => setNotifMsg(''), 1500);
    } catch (err) {
      // ignore
    }
  };

  if (!profile) {
    return <p className="max-w-[1320px] mx-auto px-6 py-12">Loading account...</p>;
  }

  return (
    <div className="max-w-[1100px] mx-auto px-6 py-8">
      <h2 className="font-[family-name:var(--font-heading)] text-[32px] font-bold mb-6">My Account</h2>

      <div className="space-y-6">
        {/* Profile */}
        <Section title="Profile" icon={UserRound}>
          <form onSubmit={saveProfile} className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-medium text-[var(--color-text-muted)] mb-1 block">Full name</label>
              <input value={name} onChange={(e) => setName(e.target.value)} className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
            </div>
            <div>
              <label className="text-xs font-medium text-[var(--color-text-muted)] mb-1 block">Email</label>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
            </div>
            <div>
              <label className="text-xs font-medium text-[var(--color-text-muted)] mb-1 block">Phone</label>
              <input value={phone} onChange={(e) => setPhone(e.target.value)} className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
            </div>
            <div className="md:col-span-2">
              <label className="text-xs font-medium text-[var(--color-text-muted)] mb-1 block">Default delivery address</label>
              <input value={shippingAddress} onChange={(e) => setShippingAddress(e.target.value)} placeholder="Flat / House no, Street, Area, City - State Pincode" className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
            </div>
            <div className="md:col-span-2">
              <button type="submit" disabled={savingProfile} className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-50">
                {savingProfile ? 'Saving...' : 'Save Profile'}
              </button>
              {profileMsg && (
                <span className={`ml-3 text-sm ${profileMsg.startsWith('✓') ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'}`}>
                  {profileMsg}
                </span>
              )}
            </div>
          </form>
        </Section>

        {/* Addresses */}
        <Section title="Addresses" icon={MapPin}>
          {addresses.length > 0 && (
            <ul className="space-y-3 mb-4">
              {addresses.map((a) => (
                <li key={a.id} className="flex items-start justify-between gap-4 p-3 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)]">
                  <div className="text-sm">
                    <p className="font-semibold text-[var(--color-text-primary)]">
                      {a.label || 'Address'}
                      {a.isDefault && <span className="ml-2 inline-flex items-center gap-1 text-xs text-[var(--color-success)]"><Check size={12} /> Default</span>}
                    </p>
                    <p className="text-[var(--color-text-secondary)]">{formatAddress(a)}</p>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <button onClick={() => startEditAddress(a)} className="text-xs font-semibold text-[var(--color-primary)] hover:underline">
                      Edit
                    </button>
                    <button onClick={() => deleteAddress(a.id)} className="text-[var(--color-error)] hover:opacity-70" aria-label="Delete address">
                      <Trash2 size={16} />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}

          {showAddrForm ? (
            <form onSubmit={saveAddress} className="grid grid-cols-1 md:grid-cols-2 gap-3 p-4 rounded-[var(--radius-lg)] bg-[var(--color-card-bg-tint)]">
              <input value={addrForm.label} onChange={(e) => setAddrForm({ ...addrForm, label: e.target.value })} placeholder="Label (Home / Work)" className="px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
              <input value={addrForm.name} onChange={(e) => setAddrForm({ ...addrForm, name: e.target.value })} placeholder="Receiver name" className="px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
              <input value={addrForm.addressLine} onChange={(e) => setAddrForm({ ...addrForm, addressLine: e.target.value })} placeholder="Address line *" required className="md:col-span-2 px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
              <input value={addrForm.city} onChange={(e) => setAddrForm({ ...addrForm, city: e.target.value })} placeholder="City *" required className="px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
              <div className="flex gap-3">
                <input value={addrForm.state} onChange={(e) => setAddrForm({ ...addrForm, state: e.target.value })} placeholder="State" className="flex-1 px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
                <input value={addrForm.pincode} onChange={(e) => setAddrForm({ ...addrForm, pincode: e.target.value })} placeholder="Pincode *" required className="w-28 px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
              </div>
              <input value={addrForm.phone} onChange={(e) => setAddrForm({ ...addrForm, phone: e.target.value })} placeholder="Phone" className="px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
              <label className="flex items-center gap-2 text-sm text-[var(--color-text-secondary)]">
                <input type="checkbox" checked={addrForm.isDefault} onChange={(e) => setAddrForm({ ...addrForm, isDefault: e.target.checked })} className="accent-[var(--color-primary)]" />
                Set as default
              </label>
              {addrError && <p className="md:col-span-2 text-sm text-[var(--color-error)]">{addrError}</p>}
              <div className="flex gap-2 md:col-span-2">
                <button type="submit" className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors">
                  {editingAddrId ? 'Save Changes' : 'Add Address'}
                </button>
                <button type="button" onClick={() => { setShowAddrForm(false); resetAddrForm(); }} className="rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-border)] text-[var(--color-text-secondary)] px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-card-bg)] transition-colors">
                  Cancel
                </button>
              </div>
            </form>
          ) : (
            <button onClick={() => setShowAddrForm(true)} className="inline-flex items-center gap-1.5 text-sm font-semibold text-[var(--color-primary)] hover:underline">
              <Plus size={16} /> {addresses.length === 0 ? 'Add your first address' : 'Add address'}
            </button>
          )}

          <p className="text-xs text-[var(--color-text-muted)] mt-3">
            Saved addresses appear in the delivery picker at checkout.
          </p>
        </Section>

        {/* Change password */}
        <Section title="Change Password" icon={KeyRound}>
          <form onSubmit={changePassword} className="max-w-sm space-y-3">
            <input type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} placeholder="Current password *" required className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
            <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="New password * (8+ chars, upper+lower+number+symbol)" required className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
            <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder="Confirm new password *" required className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
            <div>
              <button type="submit" disabled={savingPw} className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-50">
                {savingPw ? 'Updating...' : 'Change Password'}
              </button>
              {pwMsg && (
                <span className={`ml-3 text-sm ${pwMsg.startsWith('✓') ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'}`}>
                  {pwMsg}
                </span>
              )}
            </div>
          </form>
        </Section>

        {/* Notifications */}
        <Section title="Notifications" icon={Bell}>
          {notifications.length > 0 && (
            <button onClick={markAllRead} className="mb-3 text-xs font-semibold text-[var(--color-primary)] hover:underline">
              Mark all as read
            </button>
          )}
          {notifMsg && <p className="text-xs text-[var(--color-success)] mb-2">{notifMsg}</p>}
          {notifications.length === 0 ? (
            <p className="text-sm text-[var(--color-text-muted)]">
              No notifications yet. Order confirmations, shipping alerts, refunds and offers appear here.
            </p>
          ) : (
            <ul className="divide-y divide-[var(--color-border)]">
              {notifications.map((n) => (
                <li key={n.id} className={`py-2.5 flex items-start gap-3 ${n.isRead ? 'opacity-60' : ''}`}>
                  <span className={`mt-1 w-2 h-2 rounded-full shrink-0 ${n.isRead ? 'bg-[var(--color-text-muted)]' : 'bg-[var(--color-primary)]'}`} />
                  <div className="flex-1">
                    <p className="text-sm text-[var(--color-text-secondary)]">{n.message}</p>
                    <p className="text-xs text-[var(--color-text-muted)] mt-0.5">
                      {NOTIF_TYPE_LABELS[n.type] || n.type} · {new Date(n.sentAt).toLocaleString()}
                    </p>
                  </div>
                  {!n.isRead && (
                    <span className="text-[10px] font-bold uppercase tracking-wide text-[var(--color-primary)] shrink-0">New</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Section>

        <p className="text-sm text-[var(--color-text-muted)]">
          Looking for your orders? <Link to="/orders" className="text-[var(--color-primary)] hover:underline">View My Orders</Link>
        </p>
      </div>
    </div>
  );
}

export default Account;