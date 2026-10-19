import { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';
import { APPROVAL_TYPES, labelKeyFor, STAFF_STATUSES } from '../../utils/staffLabels';

const inputClass =
  'w-full rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]';

export default function StaffApprovals() {
  const { t, formatDate } = useLanguage();
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState('');
  const [form, setForm] = useState({ type: 'PRICE_CHANGE', productId: '', requestedPrice: '', discountPercent: '', reason: '' });

  const load = async () => {
    const res = await axiosClient.get('/staff/approvals');
    setRequests(res.data);
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await axiosClient.post('/staff/approvals', {
        type: form.type,
        productId: Number(form.productId),
        requestedPrice: form.type === 'PRICE_CHANGE' ? Number(form.requestedPrice) : null,
        discountPercent: form.type === 'DISCOUNT' ? Number(form.discountPercent) : null,
        reason: form.reason,
      });
      setForm({ type: 'PRICE_CHANGE', productId: '', requestedPrice: '', discountPercent: '', reason: '' });
      setShowForm(false);
      load();
    } catch (err) {
      setError(err.response?.data || t('common.somethingWentWrong'));
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="font-[family-name:var(--font-heading)] text-2xl font-bold">{t('staff.approvals.title')}</h1>
        <button onClick={() => setShowForm((v) => !v)}
          className="rounded-[var(--radius-md)] bg-[var(--color-primary)] px-4 py-2 text-sm font-semibold text-white hover:bg-[var(--color-primary-hover)]">
          {t('staff.approvals.new')}
        </button>
      </div>

      {showForm && (
        <form onSubmit={submit} className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-4 shadow-[var(--shadow-sm)] grid sm:grid-cols-2 gap-3">
          <label className="text-sm text-[var(--color-text-secondary)] flex flex-col gap-1">
            {t('staff.approvals.type')}
            <select className={inputClass} value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
              {APPROVAL_TYPES.map((a) => <option key={a.value} value={a.value}>{t(a.labelKey)}</option>)}
            </select>
          </label>
          <input className={inputClass} type="number" placeholder={t('staff.approvals.productId')} value={form.productId}
            onChange={(e) => setForm({ ...form, productId: e.target.value })} required />
          {form.type === 'PRICE_CHANGE' ? (
            <input className={inputClass} type="number" step="0.01" placeholder={t('staff.approvals.newPrice')} value={form.requestedPrice} onChange={(e) => setForm({ ...form, requestedPrice: e.target.value })} required />
          ) : (
            <input className={inputClass} type="number" placeholder={t('staff.approvals.discount')} value={form.discountPercent} onChange={(e) => setForm({ ...form, discountPercent: e.target.value })} required />
          )}
          <input className={inputClass} placeholder={t('staff.approvals.reason')} value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} />
          {error && <p className="sm:col-span-2 text-sm text-[var(--color-error)]">{error}</p>}
          <button type="submit" className="sm:col-span-2 rounded-[var(--radius-md)] bg-[var(--color-primary)] px-4 py-2.5 text-sm font-semibold text-white hover:bg-[var(--color-primary-hover)]">
            {t('staff.approvals.submit')}
          </button>
        </form>
      )}

      {loading ? (
        <p className="text-sm text-[var(--color-text-muted)]">…</p>
      ) : requests.length === 0 ? (
        <p className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-6 text-sm text-[var(--color-text-muted)] shadow-[var(--shadow-sm)]">
          {t('staff.approvals.empty')}
        </p>
      ) : (
        <ul className="flex flex-col gap-3">
          {requests.map((r) => (
            <li key={r.id} className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-4 shadow-[var(--shadow-sm)]">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-semibold text-[var(--color-text)]">{t(labelKeyFor(APPROVAL_TYPES, r.type))}</span>
                <span className="text-xs px-2 py-0.5 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]">
                  {t(labelKeyFor(STAFF_STATUSES, r.status))}
                </span>
              </div>
              <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
                {r.productName || `#${r.productId}`}
                {r.requestedPrice != null ? ` · ${r.requestedPrice}` : ''}
                {r.discountPercent != null ? ` · ${r.discountPercent}%` : ''}
              </p>
              {r.reason && <p className="text-sm text-[var(--color-text-secondary)]">{r.reason}</p>}
              {r.decisionReason && <p className="text-sm text-[var(--color-text-muted)]">{r.decisionReason}</p>}
              {r.createdAt && <p className="text-xs text-[var(--color-text-muted)] mt-1">{formatDate(r.createdAt)}</p>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
