import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Bell, MapPin, KeyRound, UserRound, Trash2, Plus, Check } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useLanguage } from '../context/LanguageContext';
import { getCustomerId } from '../utils/customer';

// The API sends a notification type enum; the label is looked up by key so the
// row reads in the active language.
const NOTIF_TYPE_KEYS = {
  ORDER: 'account.notif.order',
  PAYMENT: 'account.notif.payment',
  SHIPPING: 'account.notif.shipping',
  DELIVERY: 'account.notif.delivery',
  RESTOCK: 'account.notif.restock',
  LOW_STOCK: 'account.notif.lowStock',
  OFFER: 'account.notif.offer',
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
  const { t, formatDateTime } = useLanguage();
  const customerId = getCustomerId();
  const username = localStorage.getItem('username') || '';

  const [profile, setProfile] = useState(null);
  // Flash messages are { ok, value }. `value` is normally a translation key but
  // may be a server message, and t() returns an unknown key verbatim, so both
  // render correctly. Tracking `ok` separately keeps the styling independent of
  // whether the text happens to start with a tick.
  const [profileMsg, setProfileMsg] = useState(null);
  const [addresses, setAddresses] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [notifMsg, setNotifMsg] = useState(null);
  const [pwMsg, setPwMsg] = useState(null);

  // Profile form
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [emailCode, setEmailCode] = useState('');
  const [emailPassword, setEmailPassword] = useState('');
  const [emailSent, setEmailSent] = useState(false);
  const [emailBusy, setEmailBusy] = useState(false);
  const [emailMessage, setEmailMessage] = useState('');
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
    // The endpoint returns { notifications, unread }; fall back to a bare array
    // so an older cached backend response still renders.
    const payload = notifRes.data;
    setNotifications(Array.isArray(payload) ? payload : payload?.notifications || []);
  };

  useEffect(() => {
    loadAll().catch(() => {});
  }, [customerId]);

  const saveProfile = async (e) => {
    e.preventDefault();
    setSavingProfile(true);
    setProfileMsg(null);
    try {
      const res = await axiosClient.put(`/customers/${customerId}`, { name, phone, shippingAddress });
      setProfile(res.data);
      localStorage.setItem('customerName', res.data.name || '');
      setProfileMsg({ ok: true, value: 'account.profile.saved' });
      setTimeout(() => setProfileMsg(null), 2500);
    } catch (err) {
      setProfileMsg({
        ok: false,
        value: typeof err.response?.data === 'string' ? err.response.data : 'account.profile.saveFailed',
      });
    } finally {
      setSavingProfile(false);
    }
  };

  const changeEmail = async (confirm) => {
    setEmailBusy(true); setEmailMessage('');
    try {
      const response = await axiosClient.post(confirm ? '/auth/email/confirm' : '/auth/email/request',
        confirm ? { email, otp: emailCode } : { email, currentPassword: emailPassword });
      setEmailMessage(response.data.message);
      if (confirm) { setEmailSent(false); setEmailCode(''); setEmailPassword(''); await loadAll(); }
      else setEmailSent(true);
    } catch (err) { setEmailMessage(err.response?.data || t('fix.emailFailed')); }
    finally { setEmailBusy(false); }
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
      setAddrError(err.response?.data || 'account.address.saveFailed');
    }
  };

  const deleteAddress = async (id) => {
    if (!window.confirm(t('account.address.deleteConfirm'))) return;
    try {
      await axiosClient.delete(`/addresses/${id}`);
      loadAll().catch(() => {});
    } catch (err) {
      alert(err.response?.data || t('account.address.deleteFailed'));
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
    setPwMsg(null);
    if (newPassword !== confirmPassword) {
      setPwMsg({ ok: false, value: 'account.password.mismatch' });
      return;
    }
    setSavingPw(true);
    try {
      const res = await axiosClient.post('/auth/change-password', {
        username,
        currentPassword,
        newPassword,
      });
      setPwMsg({ ok: true, value: res.data.message || 'account.password.changed' });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err) {
      setPwMsg({ ok: false, value: err.response?.data || 'account.password.changeFailed' });
    } finally {
      setSavingPw(false);
    }
  };

  const markAllRead = async () => {
    try {
      await axiosClient.put(`/notifications/customer/${customerId}/read-all`);
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      setNotifMsg({ ok: true, value: 'account.markAllRead' });
      setTimeout(() => setNotifMsg(null), 1500);
    } catch (err) {
      // ignore
    }
  };

  if (!profile) {
    return <p className="max-w-[1320px] mx-auto px-6 py-12">{t('action.loading')}</p>;
  }

  return (
    <div className="max-w-[1100px] mx-auto px-6 py-8">
      <h2 className="font-[family-name:var(--font-heading)] text-[32px] font-bold mb-6">{t('nav.account')}</h2>

      <div className="space-y-6">
        {/* Profile */}
        <Section title={t('account.profile.title')} icon={UserRound}>
          <form onSubmit={saveProfile} className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-medium text-[var(--color-text-muted)] mb-1 block">{t('account.profile.fullName')}</label>
              <input value={name} onChange={(e) => setName(e.target.value)} className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
            </div>
            <div>
              <label className="text-xs font-medium text-[var(--color-text-muted)] mb-1 block">{t('auth.email')}</label>
              <input type="email" value={email} onChange={(e) => { setEmail(e.target.value); setEmailSent(false); }} className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
              <p className="text-xs mt-2">{t('fix.emailVerification')}</p>
              <input type="password" aria-label={t('fix.emailPassword')} placeholder={t('fix.emailPassword')} value={emailPassword} onChange={e => setEmailPassword(e.target.value)} className="border rounded p-2 mt-2 w-full" />
              <button type="button" disabled={emailBusy || email === profile?.email} onClick={() => changeEmail(false)} className="border rounded p-2 mt-2">{t('fix.sendEmailCode')}</button>
              {emailSent && <><input aria-label={t('fix.emailCode')} placeholder={t('fix.sixDigitCode')} value={emailCode} onChange={e => setEmailCode(e.target.value.replace(/\D/g, '').slice(0,6))} className="border rounded p-2 mt-2" /><button type="button" disabled={emailBusy || emailCode.length !== 6} onClick={() => changeEmail(true)} className="border rounded p-2">{t('fix.verifyEmail')}</button></>}
              {emailMessage && <p role="status" className="text-sm mt-2">{emailMessage}</p>}
            </div>
            <div>
              <label className="text-xs font-medium text-[var(--color-text-muted)] mb-1 block">{t('account.profile.phone')}</label>
              <input value={phone} onChange={(e) => setPhone(e.target.value)} className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
            </div>
            <div className="md:col-span-2">
              <label className="text-xs font-medium text-[var(--color-text-muted)] mb-1 block">{t('account.profile.defaultAddress')}</label>
              <input value={shippingAddress} onChange={(e) => setShippingAddress(e.target.value)} placeholder={t('account.profile.addressPlaceholder')} className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
            </div>
            <div className="md:col-span-2">
              <button type="submit" disabled={savingProfile} className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-50">
                {savingProfile ? t('account.profile.saving') : t('account.profile.save')}
              </button>
              {profileMsg && (
                <span className={`ml-3 text-sm ${profileMsg.ok ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'}`}>
                  {t(profileMsg.value)}
                </span>
              )}
            </div>
          </form>
        </Section>

        {/* Addresses */}
        <Section title={t('account.address.title')} icon={MapPin}>
          {addresses.length > 0 && (
            <ul className="space-y-3 mb-4">
              {addresses.map((a) => (
                <li key={a.id} className="flex items-start justify-between gap-4 p-3 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)]">
                  <div className="text-sm">
                    <p className="font-semibold text-[var(--color-text-primary)]">
                      {a.label || t('cart.addressFallback')}
                      {a.isDefault && <span className="ml-2 inline-flex items-center gap-1 text-xs text-[var(--color-success)]"><Check size={12} /> {t('cart.defaultBadge')}</span>}
                    </p>
                    <p className="text-[var(--color-text-secondary)]">{formatAddress(a)}</p>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <button onClick={() => startEditAddress(a)} className="text-xs font-semibold text-[var(--color-primary)] hover:underline">
                      {t('common.edit')}
                    </button>
                    <button onClick={() => deleteAddress(a.id)} className="text-[var(--color-error)] hover:opacity-70" aria-label={t('account.address.deleteAria')}>
                      <Trash2 size={16} />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}

          {showAddrForm ? (
            <form onSubmit={saveAddress} className="grid grid-cols-1 md:grid-cols-2 gap-3 p-4 rounded-[var(--radius-lg)] bg-[var(--color-card-bg-tint)]">
              <input value={addrForm.label} onChange={(e) => setAddrForm({ ...addrForm, label: e.target.value })} placeholder={t('account.address.labelPlaceholder')} className="px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
              <input value={addrForm.name} onChange={(e) => setAddrForm({ ...addrForm, name: e.target.value })} placeholder={t('account.address.receiverPlaceholder')} className="px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
              <input value={addrForm.addressLine} onChange={(e) => setAddrForm({ ...addrForm, addressLine: e.target.value })} placeholder={t('account.address.linePlaceholder')} required className="md:col-span-2 px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
              <input value={addrForm.city} onChange={(e) => setAddrForm({ ...addrForm, city: e.target.value })} placeholder={t('account.address.cityPlaceholder')} required className="px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
              <div className="flex gap-3">
                <input value={addrForm.state} onChange={(e) => setAddrForm({ ...addrForm, state: e.target.value })} placeholder={t('account.address.statePlaceholder')} className="flex-1 px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
                <input value={addrForm.pincode} onChange={(e) => setAddrForm({ ...addrForm, pincode: e.target.value })} placeholder={t('account.address.pincodePlaceholder')} required className="w-28 px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
              </div>
              <input value={addrForm.phone} onChange={(e) => setAddrForm({ ...addrForm, phone: e.target.value })} placeholder={t('account.profile.phone')} className="px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
              <label className="flex items-center gap-2 text-sm text-[var(--color-text-secondary)]">
                <input type="checkbox" checked={addrForm.isDefault} onChange={(e) => setAddrForm({ ...addrForm, isDefault: e.target.checked })} className="accent-[var(--color-primary)]" />
                {t('account.address.setDefault')}
              </label>
              {addrError && <p className="md:col-span-2 text-sm text-[var(--color-error)]">{t(addrError)}</p>}
              <div className="flex gap-2 md:col-span-2">
                <button type="submit" className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors">
                  {editingAddrId ? t('account.address.saveChanges') : t('account.address.add')}
                </button>
                <button type="button" onClick={() => { setShowAddrForm(false); resetAddrForm(); }} className="rounded-[var(--radius-md)] bg-white border-[1.5px] border-[var(--color-border)] text-[var(--color-text-secondary)] px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-card-bg)] transition-colors">
                  {t('common.cancel')}
                </button>
              </div>
            </form>
          ) : (
            <button onClick={() => setShowAddrForm(true)} className="inline-flex items-center gap-1.5 text-sm font-semibold text-[var(--color-primary)] hover:underline">
              <Plus size={16} /> {addresses.length === 0 ? t('account.address.addFirst') : t('account.address.add')}
            </button>
          )}

          <p className="text-xs text-[var(--color-text-muted)] mt-3">
            {t('account.address.checkoutHint')}
          </p>
        </Section>

        {/* Change password */}
        <Section title={t('account.password.title')} icon={KeyRound}>
          <form onSubmit={changePassword} className="max-w-sm space-y-3">
            <input type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} placeholder={t('account.password.currentPlaceholder')} required className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
            <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder={t('account.password.newPlaceholder')} required className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
            <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder={t('account.password.confirmPlaceholder')} required className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] outline-none focus:border-[var(--color-primary)] text-sm" />
            <div>
              <button type="submit" disabled={savingPw} className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-50">
                {savingPw ? t('account.password.updating') : t('account.password.title')}
              </button>
              {pwMsg && (
                <span className={`ml-3 text-sm ${pwMsg.ok ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'}`}>
                  {t(pwMsg.value)}
                </span>
              )}
            </div>
          </form>
        </Section>

        {/* Notifications */}
        <Section title={t('account.notifications')} icon={Bell}>
          {notifications.length > 0 && (
            <button onClick={markAllRead} className="mb-3 text-xs font-semibold text-[var(--color-primary)] hover:underline">
              {t('account.markAllRead')}
            </button>
          )}
          {notifMsg && <p className="text-xs text-[var(--color-success)] mb-2">{t(notifMsg.value)}</p>}
          {notifications.length === 0 ? (
            <p className="text-sm text-[var(--color-text-muted)]">
              {t('account.noNotifications')}
            </p>
          ) : (
            <ul className="divide-y divide-[var(--color-border)]">
              {notifications.map((n) => (
                <li key={n.id} className={`py-2.5 flex items-start gap-3 ${n.isRead ? 'opacity-60' : ''}`}>
                  <span className={`mt-1 w-2 h-2 rounded-full shrink-0 ${n.isRead ? 'bg-[var(--color-text-muted)]' : 'bg-[var(--color-primary)]'}`} />
                  <div className="flex-1">
                    <p className="text-sm text-[var(--color-text-secondary)]">{n.message}</p>
                    <p className="text-xs text-[var(--color-text-muted)] mt-0.5">
                      {t(NOTIF_TYPE_KEYS[n.type] || n.type)} · {formatDateTime(n.sentAt)}
                    </p>
                  </div>
                  {!n.isRead && (
                    <span className="text-[10px] font-bold uppercase tracking-wide text-[var(--color-primary)] shrink-0">{t('account.newBadge')}</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Section>

        <p className="text-sm text-[var(--color-text-muted)]">
          {t('account.ordersBefore')}{' '}
          <Link to="/orders" className="text-[var(--color-primary)] hover:underline">{t('account.ordersLink')}</Link>
        </p>
      </div>
    </div>
  );
}

export default Account;