import { useState, useEffect, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Users, Plus, LogIn, Copy, Check } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useLanguage } from '../context/LanguageContext';

const STATUS_STYLES = {
  OPEN: 'bg-[var(--color-info)]/10 text-[var(--color-info)]',
  LOCKED: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  AWAITING_PAYMENT: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  CONFIRMED: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
  PROCESSING: 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]',
  SHIPPED: 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]',
  DELIVERED: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
  CANCELLED: 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-muted)]',
};

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

const SPLIT_KEYS = {
  EQUAL: 'group.split.EQUAL',
  ITEM_BASED: 'group.split.ITEM_BASED',
  CUSTOM: 'group.split.CUSTOM',
};

/**
 * My Group Orders: list of groups you host or belong to, plus create/join.
 * This is the collaborative cart feature — distinct from the personal-cart
 * bill-split preview, which only divides one person's own basket.
 */
function GroupOrders() {
  const { t, formatCurrency } = useLanguage();
  const navigate = useNavigate();
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [showJoin, setShowJoin] = useState(() => new URLSearchParams(window.location.search).has('join'));
  const [joinCode, setJoinCode] = useState(() => new URLSearchParams(window.location.search).get('join') || '');
  const [copied, setCopied] = useState(null);
  const [form, setForm] = useState({ name: '', shippingAddress: '', deadline: '', splitMethod: 'EQUAL' });
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await axiosClient.get('/group-orders');
      setGroups(res.data);
      setError('');
    } catch (err) {
      setError(err.response?.data?.error || t('group.list.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { load(); }, [load]);

  const createGroup = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const res = await axiosClient.post('/group-orders', {
        name: form.name,
        shippingAddress: form.shippingAddress,
        deadline: form.deadline ? new Date(form.deadline).toISOString() : null,
        splitMethod: form.splitMethod,
        paymentMode: 'HOST_PAYS',
      });
      setShowCreate(false);
      setForm({ name: '', shippingAddress: '', deadline: '', splitMethod: 'EQUAL' });
      navigate(`/group-orders/${res.data.id}`);
    } catch (err) {
      setError(err.response?.data?.error || t('group.create.failed'));
    } finally {
      setBusy(false);
    }
  };

  const joinGroup = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const res = await axiosClient.post('/group-orders/join', { code: joinCode.trim().toUpperCase() });
      setShowJoin(false);
      setJoinCode('');
      navigate(`/group-orders/${res.data.id}`);
    } catch (err) {
      setError(err.response?.data?.error || t('group.join.failed'));
    } finally {
      setBusy(false);
    }
  };

  const copyCode = async (code) => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(code);
      setNotice(t('group.detail.codeCopied'));
      setTimeout(() => setCopied(null), 2000);
    } catch {
      setNotice(t('group.detail.codeCopyFailed'));
    }
  };

  const inputStyle = 'w-full rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-[var(--color-card-bg)] px-3.5 py-2.5 text-sm text-[var(--color-text-primary)] focus:outline-none focus:border-[var(--color-primary)] transition-colors';

  if (loading) return <p className="max-w-[1320px] mx-auto px-6 py-12">{t('action.loading')}</p>;

  return (
    <div className="max-w-[1320px] mx-auto px-6 py-10">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <h1 className="font-[family-name:var(--font-heading)] text-2xl font-bold flex items-center gap-2">
          <Users size={24} /> {t('group.title')}
        </h1>
        <div className="flex gap-2">
          <button
            onClick={() => { setShowJoin((v) => !v); setShowCreate(false); }}
            className="rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-primary)] text-[var(--color-primary)] px-4 py-2 text-sm font-semibold hover:bg-[var(--color-primary)]/5 transition-colors flex items-center gap-1.5"
          >
            <LogIn size={16} /> {t('group.action.join')}
          </button>
          <button
            onClick={() => { setShowCreate((v) => !v); setShowJoin(false); }}
            className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-4 py-2 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors flex items-center gap-1.5"
          >
            <Plus size={16} /> {t('group.action.create')}
          </button>
        </div>
      </div>

      {error && <p className="mb-4 rounded-[var(--radius-md)] bg-[var(--color-error-bg)] text-[var(--color-error)] px-4 py-2.5 text-sm">{error}</p>}
      {notice && <p className="mb-4 rounded-[var(--radius-md)] bg-[var(--color-success-bg)] text-[var(--color-success)] px-4 py-2.5 text-sm">{notice}</p>}

      {showJoin && (
        <form onSubmit={joinGroup} className="mb-6 p-5 rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)]">
          <label className="block text-sm font-semibold mb-1.5">{t('group.join.label')}</label>
          <div className="flex gap-2">
            <input
              className={inputStyle}
              value={joinCode}
              onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
              placeholder={t('group.join.placeholder')}
              maxLength={12}
              required
            />
            <button
              type="submit"
              disabled={busy || joinCode.trim().length < 4}
              className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2 text-sm font-semibold disabled:opacity-50 shrink-0"
            >
              {t('group.action.join')}
            </button>
          </div>
        </form>
      )}

      {showCreate && (
        <form onSubmit={createGroup} className="mb-6 p-5 rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] grid gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <label className="block text-sm font-semibold mb-1.5">{t('group.create.name')}</label>
            <input className={inputStyle} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={80} required placeholder={t('group.create.namePlaceholder')} />
          </div>
          <div className="sm:col-span-2">
            <label className="block text-sm font-semibold mb-1.5">{t('group.create.address')}</label>
            <textarea className={inputStyle} rows={2} value={form.shippingAddress} onChange={(e) => setForm({ ...form, shippingAddress: e.target.value })} maxLength={1000} required placeholder={t('group.create.addressPlaceholder')} />
          </div>
          <div>
            <label className="block text-sm font-semibold mb-1.5">{t('group.create.deadline')}</label>
            <input className={inputStyle} type="datetime-local" value={form.deadline} onChange={(e) => setForm({ ...form, deadline: e.target.value })} />
          </div>
          <div>
            <label className="block text-sm font-semibold mb-1.5">{t('group.create.splitMethod')}</label>
            <select className={inputStyle} value={form.splitMethod} onChange={(e) => setForm({ ...form, splitMethod: e.target.value })}>
              <option value="EQUAL">{t('group.split.EQUAL')}</option>
              <option value="ITEM_BASED">{t('group.split.ITEM_BASED')}</option>
              <option value="CUSTOM">{t('group.split.CUSTOM')}</option>
            </select>
          </div>
          <div className="sm:col-span-2 flex items-center gap-2 text-xs text-[var(--color-text-muted)]">
            <span className="inline-block w-2 h-2 rounded-full bg-[var(--color-success)]" />
            {t('group.create.hostPaysNote')}
          </div>
          <div className="sm:col-span-2">
            <button
              type="submit"
              disabled={busy}
              className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] disabled:opacity-50"
            >
              {busy ? t('action.loading') : t('group.action.create')}
            </button>
          </div>
        </form>
      )}

      {groups.length === 0 ? (
        <div className="text-center py-16">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-[var(--color-card-bg-tint)] mb-4 text-2xl">
            <Users size={28} className="text-[var(--color-text-muted)]" />
          </div>
          <h4 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-1">{t('group.empty.title')}</h4>
          <p className="text-[var(--color-text-muted)]">{t('group.empty.hint')}</p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {groups.map((g) => (
            <Link
              key={g.id}
              to={`/group-orders/${g.id}`}
              className="p-5 rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] hover:shadow-[var(--shadow-md)] transition-shadow block"
            >
              <div className="flex items-start justify-between gap-2 mb-2">
                <h3 className="font-[family-name:var(--font-heading)] font-semibold leading-snug">{g.name}</h3>
                <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold shrink-0 ${STATUS_STYLES[g.status] || ''}`}>
                  {t(STATUS_KEYS[g.status] || 'group.status.OPEN')}
                </span>
              </div>
              <p className="text-xs text-[var(--color-text-muted)] mb-3">
                {t('group.list.hostedBy', { name: g.hostName })}
                {' · '}
                {t('group.list.members', { count: g.memberCount })}
                {' · '}
                {t(SPLIT_KEYS[g.splitMethod] || 'group.split.EQUAL')}
              </p>
              <div className="flex items-center justify-between text-sm">
                {g.totalAmount != null && (
                  <span className="font-semibold">{formatCurrency(g.totalAmount)}</span>
                )}
                {g.myShare != null && (
                  <span className="text-xs text-[var(--color-text-secondary)]">
                    {t('group.list.yourShare')}: <b>{formatCurrency(g.myShare)}</b>
                  </span>
                )}
              </div>
              <button
                type="button"
                onClick={(e) => { e.preventDefault(); copyCode(g.inviteCode); }}
                className="mt-3 inline-flex items-center gap-1.5 text-xs font-mono text-[var(--color-text-muted)] hover:text-[var(--color-primary)] transition-colors"
                title={t('group.detail.copyCode')}
              >
                {copied === g.inviteCode ? <Check size={13} className="text-[var(--color-success)]" /> : <Copy size={13} />}
                {g.inviteCode}
              </button>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

export default GroupOrders;
