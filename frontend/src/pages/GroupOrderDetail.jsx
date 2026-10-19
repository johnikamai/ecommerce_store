import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  Users, Copy, Check, Lock, Unlock, ShoppingCart, Trash2, Plus, Minus,
  CheckCircle2, CreditCard, Banknote, AlertTriangle, ArrowLeft, Clock,
} from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useLanguage } from '../context/LanguageContext';
import ProductImage from '../components/ProductImage';

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

const SHARE_STATUS_KEYS = {
  PENDING: 'group.share.PENDING',
  PAYMENT_INITIATED: 'group.share.PAYMENT_INITIATED',
  PAID: 'group.share.PAID',
  FAILED: 'group.share.FAILED',
  REFUNDED: 'group.share.REFUNDED',
  OWED: 'group.share.OWED',
  REPORTED_PAID: 'group.share.REPORTED_PAID',
  CONFIRMED_RECEIVED: 'group.share.CONFIRMED_RECEIVED',
};

const SHARE_STATUS_STYLES = {
  PENDING: 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-muted)]',
  PAYMENT_INITIATED: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  PAID: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
  FAILED: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
  REFUNDED: 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-muted)]',
  OWED: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  REPORTED_PAID: 'bg-[var(--color-info)]/10 text-[var(--color-info)]',
  CONFIRMED_RECEIVED: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
};

const PAYMENT_METHODS = [
  { key: 'CASH', labelKey: 'cart.payment.cash', descKey: 'cart.payment.cashDesc' },
  { key: 'UPI', labelKey: 'cart.payment.upi', descKey: 'cart.payment.upiDesc' },
  { key: 'CARD', labelKey: 'cart.payment.card', descKey: 'cart.payment.cardDesc' },
];

/** Poll interval for shared-cart updates; keeps everyone's screen in sync. */
const POLL_MS = 10000;

/**
 * One group order: shared cart (everyone adds), members & live shares,
 * host controls (split method, freeze, checkout), and offline settlement
 * (friends report paying the host; the host confirms receipt).
 *
 * The page polls the server so another member's add shows up without a refresh.
 */
function GroupOrderDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { t, formatCurrency } = useLanguage();
  const [group, setGroup] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const [customShares, setCustomShares] = useState({});
  const [payMethod, setPayMethod] = useState('CASH');
  const [showCheckout, setShowCheckout] = useState(false);
  const pollRef = useRef(null);

  const customerId = Number(localStorage.getItem('customerId'));

  const load = useCallback(async (silent = false) => {
    try {
      const res = await axiosClient.get(`/group-orders/${id}`);
      setGroup(res.data);
      if (!silent) setError('');
    } catch (err) {
      if (!silent) setError(err.response?.data?.error || t('group.detail.loadFailed'));
    } finally {
      if (!silent) setLoading(false);
    }
  }, [id, t]);

  useEffect(() => {
    load();
    pollRef.current = setInterval(() => load(true), POLL_MS);
    return () => clearInterval(pollRef.current);
  }, [load]);

  // Keep the custom-split editor primed from the current shares.
  useEffect(() => {
    if (group?.splitMethod === 'CUSTOM' && group.members) {
      const next = {};
      for (const m of group.members) {
        next[m.customerId] = m.shareAmount ?? '';
      }
      setCustomShares((prev) => (Object.keys(prev).length ? prev : next));
    }
  }, [group?.splitMethod, group?.members]);

  const act = async (fn, successMsg) => {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await fn();
      await load(true);
      if (successMsg) setNotice(successMsg);
    } catch (err) {
      setError(err.response?.data?.error || t('group.error.generic'));
    } finally {
      setBusy(false);
    }
  };

  const copyCode = async () => {
    try {
      await navigator.clipboard.writeText(group.inviteCode);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setError(t('group.detail.codeCopyFailed'));
    }
  };

  if (loading) return <p className="max-w-[1320px] mx-auto px-6 py-12">{t('action.loading')}</p>;
  if (!group) {
    return (
      <div className="max-w-[1320px] mx-auto px-6 py-16 text-center">
        <p className="text-[var(--color-text-muted)] mb-4">{error || t('group.detail.loadFailed')}</p>
        <button onClick={() => navigate('/group-orders')} className="text-[var(--color-primary)] font-semibold">{t('group.backToList')}</button>
      </div>
    );
  }

  const isHost = group.isHost;
  const editable = group.status === 'OPEN';
  const placed = group.placedOrderId != null;
  const myMember = group.members.find((m) => m.customerId === customerId);
  const myPayment = group.payments.find((p) => p.customerId === customerId);

  const setSplit = (method) => act(() => axiosClient.post(`/group-orders/${id}/split`, { method }));
  const saveCustomSplit = () => {
    const amounts = {};
    for (const [cid, val] of Object.entries(customShares)) {
      amounts[cid] = Number(val);
    }
    return act(() => axiosClient.post(`/group-orders/${id}/split`, { method: 'CUSTOM', customAmounts: amounts }), t('group.detail.splitSaved'));
  };
  const confirmShare = () => act(() => axiosClient.post(`/group-orders/${id}/confirm`), t('group.detail.shareConfirmed'));
  const lock = () => act(() => axiosClient.post(`/group-orders/${id}/lock`), t('group.detail.cartLocked'));
  const unlock = () => act(() => axiosClient.post(`/group-orders/${id}/unlock`));
  const checkout = () => act(async () => {
    await axiosClient.post(`/group-orders/${id}/checkout`, { paymentMethod: payMethod });
    setShowCheckout(false);
  }, t('group.detail.orderPlaced'));
  const cancelGroup = () => {
    if (!window.confirm(t('group.detail.cancelConfirm'))) return;
    act(() => axiosClient.post(`/group-orders/${id}/cancel`), t('group.detail.cancelled'));
  };
  const removeItem = (itemId) => act(() => axiosClient.delete(`/group-orders/${id}/items/${itemId}`));
  const removeMember = (customerId2) => {
    if (!window.confirm(t('group.detail.removeMemberConfirm'))) return;
    act(() => axiosClient.delete(`/group-orders/${id}/members/${customerId2}`));
  };
  const reportPaid = () => {
    const ref = window.prompt(t('group.detail.reportPaidPrompt')) || '';
    act(() => axiosClient.post(`/group-orders/${id}/payment/report`, { reference: ref }), t('group.detail.reportedPaid'));
  };
  const confirmReceipt = (memberId) => act(() => axiosClient.post(`/group-orders/${id}/payment/${memberId}/confirm`), t('group.detail.receiptConfirmed'));

  const inputStyle = 'w-full rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-[var(--color-card-bg)] px-3.5 py-2.5 text-sm text-[var(--color-text-primary)] focus:outline-none focus:border-[var(--color-primary)] transition-colors';

  return (
    <div className="max-w-[1320px] mx-auto px-6 py-10">
      <button onClick={() => navigate('/group-orders')} className="inline-flex items-center gap-1.5 text-sm text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] mb-4 transition-colors">
        <ArrowLeft size={15} /> {t('group.backToList')}
      </button>

      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-3 mb-6">
        <div>
          <h1 className="font-[family-name:var(--font-heading)] text-2xl font-bold flex items-center gap-2">
            <Users size={24} /> {group.name}
          </h1>
          <p className="text-sm text-[var(--color-text-muted)] mt-1">
            {t('group.detail.hostedBy', { name: group.members.find((m) => m.role === 'HOST')?.name || '' })}
            {' · '}
            {t(SPLIT_KEYS[group.splitMethod] || 'group.split.EQUAL')}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className={`inline-block rounded-full px-3 py-1 text-sm font-semibold ${STATUS_STYLES[group.status] || ''}`}>
            {t(STATUS_KEYS[group.status] || 'group.status.OPEN')}
          </span>
          {placed && (
            <Link to={`/orders`} className="text-sm font-semibold text-[var(--color-primary)] hover:underline">
              {t('group.detail.viewOrder')} #{group.placedOrderId}
            </Link>
          )}
        </div>
      </div>

      {error && <p className="mb-4 rounded-[var(--radius-md)] bg-[var(--color-error-bg)] text-[var(--color-error)] px-4 py-2.5 text-sm">{error}</p>}
      {notice && <p className="mb-4 rounded-[var(--radius-md)] bg-[var(--color-success-bg)] text-[var(--color-success)] px-4 py-2.5 text-sm">{notice}</p>}

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Left: cart + members */}
        <div className="lg:col-span-2 space-y-6">
          {/* Invite bar */}
          {editable && (
            <div className="p-4 rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] flex flex-wrap items-center gap-3">
              <span className="text-sm font-semibold">{t('group.detail.inviteFriends')}</span>
              <button
                onClick={copyCode}
                className="inline-flex items-center gap-1.5 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] px-3 py-1.5 font-mono text-sm hover:border-[var(--color-primary)] transition-colors"
              >
                {copied ? <Check size={14} className="text-[var(--color-success)]" /> : <Copy size={14} />}
                {group.inviteCode}
              </button>
              <span className="text-xs text-[var(--color-text-muted)]">{t('group.detail.codeHint')}</span>
            </div>
          )}

          {/* Cart */}
          <section className="rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-[family-name:var(--font-heading)] text-lg font-semibold flex items-center gap-2">
                <ShoppingCart size={18} /> {t('group.detail.cart')}
              </h2>
              {group.status === 'LOCKED' && (
                <span className="inline-flex items-center gap-1 text-xs font-semibold text-[var(--color-warning)]">
                  <Lock size={13} /> {t('group.detail.frozen')}
                </span>
              )}
            </div>

            {group.items.length === 0 ? (
              <p className="text-sm text-[var(--color-text-muted)]">{t('group.detail.cartEmpty')}</p>
            ) : (
              <ul className="divide-y divide-[var(--color-border)]">
                {group.items.map((item) => (
                  <li key={item.id} className="py-3 flex items-center gap-3">
                    <div className="w-12 h-12 rounded-[var(--radius-md)] overflow-hidden bg-[var(--color-card-bg-tint)] shrink-0">
                      <ProductImage product={{ imageUrl: item.imageUrl, name: item.productName }} className="w-full h-full object-cover" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold truncate">{item.productName}</p>
                      <p className="text-xs text-[var(--color-text-muted)]">
                        {t('group.detail.addedBy', { name: item.addedByName })} · {formatCurrency(item.unitPrice)}
                      </p>
                    </div>
                    <span className="text-sm font-semibold shrink-0">×{item.quantity}</span>
                    <span className="text-sm font-semibold w-24 text-right shrink-0">{formatCurrency(item.lineTotal)}</span>
                    {editable && (item.addedById === customerId || isHost) && (
                      <button onClick={() => removeItem(item.id)} aria-label={t('group.detail.removeItem')} className="text-[var(--color-text-muted)] hover:text-[var(--color-error)] transition-colors shrink-0">
                        <Trash2 size={16} />
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}

            <div className="border-t border-[var(--color-border)] mt-4 pt-3 space-y-1 text-sm">
              {group.split && (
                <>
                  <div className="flex justify-between text-[var(--color-text-secondary)]"><span>{t('cart.subtotal')}</span><span>{formatCurrency(group.split.merchandiseTotal)}</span></div>
                  {Number(group.split.discountTotal) > 0 && (
                    <div className="flex justify-between text-[var(--color-success)]"><span>{t('cart.discount')}</span><span>−{formatCurrency(group.split.discountTotal)}</span></div>
                  )}
                  <div className="flex justify-between text-[var(--color-text-secondary)]"><span>{t('cart.shipping')}</span><span>{Number(group.split.shipping) === 0 ? t('cart.freeShipping') : formatCurrency(group.split.shipping)}</span></div>
                  <div className="flex justify-between text-[var(--color-text-secondary)]"><span>{t('cart.tax')}</span><span>{formatCurrency(group.split.tax)}</span></div>
                  <div className="flex justify-between font-bold text-base pt-1"><span>{t('cart.total')}</span><span>{formatCurrency(group.split.totalAmount)}</span></div>
                </>
              )}
            </div>

            {editable && (
              <Link
                to="/products"
                className="mt-4 inline-flex items-center gap-1.5 text-sm font-semibold text-[var(--color-primary)] hover:underline"
              >
                <Plus size={15} /> {t('group.detail.browseToAdd')}
              </Link>
            )}
          </section>

          {/* Split */}
          <section className="rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
            <h2 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-1">{t('group.detail.billSplit')}</h2>
            {group.split?.breakdown && (
              <p className="text-xs text-[var(--color-text-muted)] mb-4">{group.split.breakdown}</p>
            )}

            {isHost && !placed && group.status !== 'CANCELLED' && (
              <div className="flex flex-wrap gap-2 mb-4">
                {['EQUAL', 'ITEM_BASED', 'CUSTOM'].map((method) => (
                  <button
                    key={method}
                    onClick={() => setSplit(method)}
                    disabled={busy || group.splitMethod === method}
                    className={`rounded-[var(--radius-md)] px-3.5 py-1.5 text-sm font-semibold border-[1.5px] transition-colors ${
                      group.splitMethod === method
                        ? 'bg-[var(--color-primary)] text-white border-[var(--color-primary)]'
                        : 'border-[var(--color-border)] text-[var(--color-text-secondary)] hover:border-[var(--color-primary)]'
                    } disabled:opacity-60`}
                  >
                    {t(SPLIT_KEYS[method])}
                  </button>
                ))}
              </div>
            )}

            {group.splitMethod === 'CUSTOM' && isHost && !placed && (
              <div className="mb-4 p-4 rounded-[var(--radius-lg)] bg-[var(--color-card-bg-tint)] space-y-3">
                {group.members.map((m) => (
                  <div key={m.id} className="flex items-center gap-3">
                    <span className="text-sm flex-1">{m.name}</span>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      className={`${inputStyle} w-32`}
                      value={customShares[m.customerId] ?? ''}
                      onChange={(e) => setCustomShares({ ...customShares, [m.customerId]: e.target.value })}
                    />
                  </div>
                ))}
                <button
                  onClick={saveCustomSplit}
                  disabled={busy}
                  className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-4 py-2 text-sm font-semibold disabled:opacity-50"
                >
                  {t('group.detail.saveSplit')}
                </button>
              </div>
            )}

            <ul className="divide-y divide-[var(--color-border)]">
              {group.members.map((m) => (
                <li key={m.id} className="py-3 flex flex-wrap items-center gap-2">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold">
                      {m.name}
                      {m.role === 'HOST' && <span className="ml-1.5 text-xs font-normal text-[var(--color-text-muted)]">({t('group.detail.host')})</span>}
                    </p>
                    <p className="text-xs text-[var(--color-text-muted)]">{t('group.detail.itemsCount', { count: m.itemCount })}</p>
                  </div>
                  <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${SHARE_STATUS_STYLES[m.shareStatus] || ''}`}>
                    {t(SHARE_STATUS_KEYS[m.shareStatus] || 'group.share.PENDING')}
                  </span>
                  <span className="text-sm font-bold w-24 text-right">{m.shareAmount != null ? formatCurrency(m.shareAmount) : '—'}</span>
                  {m.shareConfirmed && <CheckCircle2 size={16} className="text-[var(--color-success)]" title={t('group.detail.confirmed')} />}
                  {!m.shareConfirmed && !placed && group.split != null && (
                    <span className="text-xs text-[var(--color-warning)] inline-flex items-center gap-1"><Clock size={12} />{t('group.detail.needsConfirm')}</span>
                  )}
                  {isHost && !placed && m.role !== 'HOST' && (
                    <button onClick={() => removeMember(m.customerId)} className="text-[var(--color-text-muted)] hover:text-[var(--color-error)] transition-colors" aria-label={t('group.detail.removeMember')}>
                      <Trash2 size={14} />
                    </button>
                  )}
                </li>
              ))}
            </ul>

            {myMember && !placed && group.status !== 'CANCELLED' && !myMember.shareConfirmed && group.split != null && (
              <button
                onClick={confirmShare}
                disabled={busy}
                className="mt-4 w-full rounded-[var(--radius-md)] bg-[var(--color-success)] text-white px-4 py-2.5 text-sm font-semibold hover:opacity-90 disabled:opacity-50 inline-flex items-center justify-center gap-1.5"
              >
                <CheckCircle2 size={16} /> {t('group.detail.confirmMyShare')} ({myMember.shareAmount != null ? formatCurrency(myMember.shareAmount) : ''})
              </button>
            )}
          </section>

          {/* Settlement (placed only) */}
          {placed && group.payments.length > 0 && (
            <section className="rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
              <h2 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-4">{t('group.detail.settlement')}</h2>
              {group.remainingToCollect != null && Number(group.remainingToCollect) > 0 && (
                <p className="mb-3 text-sm font-semibold text-[var(--color-warning)]">
                  {t('group.detail.remaining', { amount: formatCurrency(group.remainingToCollect) })}
                </p>
              )}
              <ul className="divide-y divide-[var(--color-border)]">
                {group.payments.map((p) => (
                  <li key={p.id} className="py-3 flex flex-wrap items-center gap-2">
                    <span className="text-sm font-semibold flex-1">{p.memberName}</span>
                    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${SHARE_STATUS_STYLES[p.status] || ''}`}>
                      {t(SHARE_STATUS_KEYS[p.status] || 'group.share.PENDING')}
                    </span>
                    <span className="text-sm font-bold w-24 text-right">{formatCurrency(p.amount)}</span>
                    {isHost && p.status === 'REPORTED_PAID' && (
                      <button
                        onClick={() => confirmReceipt(p.memberId)}
                        disabled={busy}
                        className="rounded-[var(--radius-md)] bg-[var(--color-success)] text-white px-3 py-1 text-xs font-semibold disabled:opacity-50"
                      >
                        {t('group.detail.confirmReceipt')}
                      </button>
                    )}
                  </li>
                ))}
              </ul>
              {myPayment?.status === 'OWED' && (
                <button
                  onClick={reportPaid}
                  disabled={busy}
                  className="mt-4 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-primary)] text-[var(--color-primary)] px-4 py-2 text-sm font-semibold hover:bg-[var(--color-primary)]/5 disabled:opacity-50"
                >
                  {t('group.detail.iPaidHost')}
                </button>
              )}
              {!isHost && myPayment?.status === 'PAID' && (
                <p className="mt-3 text-sm text-[var(--color-success)] font-semibold">{t('group.detail.alreadyPaid')}</p>
              )}
            </section>
          )}
        </div>

        {/* Right rail: controls + activity */}
        <div className="space-y-6">
          {/* Host controls */}
          {!placed && group.status !== 'CANCELLED' && isHost && (
            <section className="rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5 space-y-3">
              <h3 className="font-[family-name:var(--font-heading)] font-semibold">{t('group.detail.hostControls')}</h3>
              {group.status === 'OPEN' && (
                <button
                  onClick={lock}
                  disabled={busy || group.items.length === 0 || !group.allConfirmed}
                  className="w-full rounded-[var(--radius-md)] bg-[var(--color-warning)] text-white px-4 py-2.5 text-sm font-semibold hover:opacity-90 disabled:opacity-50 inline-flex items-center justify-center gap-1.5"
                  title={!group.allConfirmed ? t('group.detail.allMustConfirm') : ''}
                >
                  <Lock size={16} /> {t('group.detail.freezeCart')}
                </button>
              )}
              {group.status === 'LOCKED' && (
                <>
                  <button
                    onClick={() => setShowCheckout((v) => !v)}
                    disabled={busy || !group.allConfirmed}
                    className="w-full rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-4 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] disabled:opacity-50 inline-flex items-center justify-center gap-1.5"
                    title={!group.allConfirmed ? t('group.detail.allMustConfirm') : ''}
                  >
                    <CreditCard size={16} /> {t('group.detail.checkout')}
                  </button>
                  <button onClick={unlock} disabled={busy} className="w-full rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] px-4 py-2 text-sm font-semibold text-[var(--color-text-secondary)] hover:border-[var(--color-primary)] disabled:opacity-50 inline-flex items-center justify-center gap-1.5">
                    <Unlock size={15} /> {t('group.detail.reopenCart')}
                  </button>
                </>
              )}
              <button onClick={cancelGroup} disabled={busy} className="w-full rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-error)] text-[var(--color-error)] px-4 py-2 text-sm font-semibold hover:bg-[var(--color-error-bg)] disabled:opacity-50">
                {t('group.detail.cancelGroup')}
              </button>
              {!group.allConfirmed && group.status === 'OPEN' && (
                <p className="text-xs text-[var(--color-text-muted)] flex items-center gap-1"><AlertTriangle size={13} className="text-[var(--color-warning)]" />{t('group.detail.allMustConfirm')}</p>
              )}
            </section>
          )}

          {/* Checkout panel */}
          {showCheckout && group.status === 'LOCKED' && (
            <section className="rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5 space-y-3">
              <h3 className="font-[family-name:var(--font-heading)] font-semibold">{t('group.detail.paymentTitle')}</h3>
              <p className="text-xs text-[var(--color-text-muted)]">{t('group.detail.hostPaysNote')}</p>
              {PAYMENT_METHODS.filter(m => m.key === 'CASH' || group.demoPaymentsEnabled).map((m) => (
                <label key={m.key} className={`flex items-start gap-2.5 p-3 rounded-[var(--radius-lg)] border-[1.5px] cursor-pointer transition-colors ${payMethod === m.key ? 'border-[var(--color-primary)] bg-[var(--color-primary)]/5' : 'border-[var(--color-border)]'}`}>
                  <input type="radio" name="payMethod" value={m.key} checked={payMethod === m.key} onChange={(e) => setPayMethod(e.target.value)} className="mt-0.5" />
                  <span>
                    <span className="block text-sm font-semibold">{t(m.labelKey)}</span>
                    <span className="block text-xs text-[var(--color-text-muted)]">{t(m.descKey)}</span>
                  </span>
                </label>
              ))}
              <button
                onClick={checkout}
                disabled={busy}
                className="w-full rounded-[var(--radius-md)] bg-[var(--color-success)] text-white px-4 py-2.5 text-sm font-semibold hover:opacity-90 disabled:opacity-50 inline-flex items-center justify-center gap-1.5"
              >
                <Banknote size={16} /> {t('group.detail.placeOrder')} ({group.split ? formatCurrency(group.split.totalAmount) : ''})
              </button>
            </section>
          )}

          {/* Address */}
          <section className="rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
            <h3 className="font-[family-name:var(--font-heading)] font-semibold mb-2">{t('group.detail.deliveryTo')}</h3>
            <p className="text-sm text-[var(--color-text-secondary)] whitespace-pre-line">{group.shippingAddress}</p>
            {group.deadline && (
              <p className="text-xs text-[var(--color-text-muted)] mt-2 flex items-center gap-1">
                <Clock size={12} /> {t('group.detail.joinedBy', { when: new Date(group.deadline).toLocaleString() })}
              </p>
            )}
          </section>

          {/* Activity */}
          <section className="rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
            <h3 className="font-[family-name:var(--font-heading)] font-semibold mb-3">{t('group.detail.activity')}</h3>
            <ul className="space-y-2.5">
              {group.activities.map((a) => (
                <li key={a.id} className="text-sm">
                  <span className="text-[var(--color-text-secondary)]">{a.message}</span>
                  <span className="block text-xs text-[var(--color-text-muted)]">{new Date(a.createdAt).toLocaleTimeString()}</span>
                </li>
              ))}
            </ul>
          </section>
        </div>
      </div>
    </div>
  );
}

export default GroupOrderDetail;
