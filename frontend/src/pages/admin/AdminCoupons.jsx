import { useState, useEffect } from 'react';
import { Plus, Pencil, Trash2, X } from 'lucide-react';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';

const emptyForm = {
  code: '', description: '', discountType: 'PERCENT', discountValue: '',
  minimumOrderAmount: '', expiryDate: '', maxUses: '',
};

export default function AdminCoupons() {
  const { t, formatCurrency, formatDate } = useLanguage();
  const [coupons, setCoupons] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const load = async () => {
    const res = await axiosClient.get('/coupons');
    setCoupons(res.data);
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const openModal = (coupon) => {
    setForm(coupon ? {
      code: coupon.code,
      description: coupon.description || '',
      discountType: coupon.discountType,
      discountValue: coupon.discountValue,
      minimumOrderAmount: coupon.minimumOrderAmount || '',
      expiryDate: coupon.expiryDate ? coupon.expiryDate.slice(0, 10) : '',
      maxUses: coupon.maxUses ?? '',
    } : { ...emptyForm });
    setError('');
    setModal(coupon ? { coupon } : {});
  };

  const save = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      const body = {
        description: form.description,
        discountType: form.discountType,
        discountValue: parseFloat(form.discountValue),
        minimumOrderAmount: form.minimumOrderAmount === '' ? null : parseFloat(form.minimumOrderAmount),
        expiryDate: form.expiryDate === '' ? null : form.expiryDate,
        maxUses: form.maxUses === '' ? null : parseInt(form.maxUses),
      };
      if (modal.coupon) {
        await axiosClient.put(`/coupons/${modal.coupon.id}`, body);
      } else {
        await axiosClient.post('/coupons', { ...body, code: form.code });
      }
      setModal(null);
      setForm(emptyForm);
      load();
    } catch (err) {
      setError(err.response?.data || t('admin.coupons.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (coupon) => {
    try {
      await axiosClient.put(`/coupons/${coupon.id}/status`, { active: !coupon.active });
      load();
    } catch (err) {
      alert(err.response?.data || t('admin.coupons.actionFailed'));
    }
  };

  const remove = async (coupon) => {
    if (!window.confirm(t('admin.coupons.deleteConfirm', { code: coupon.code }))) return;
    try {
      await axiosClient.delete(`/coupons/${coupon.id}`);
      load();
    } catch (err) {
      alert(err.response?.data || t('admin.coupons.deleteFailed'));
    }
  };

  const inputClass = 'w-full px-3 py-2 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] outline-none text-sm';

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">{t('admin.coupons.loading')}</p>;

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold">{t('admin.nav.coupons')}</h2>
        <button
          onClick={() => openModal(null)}
          className="inline-flex items-center gap-1.5 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-4 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
        >
          <Plus size={16} /> {t('admin.coupons.add')}
        </button>
      </div>

      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden">
        {coupons.length === 0 ? (
          <p className="p-6 text-sm text-[var(--color-text-muted)]">{t('admin.coupons.empty')}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-[var(--color-text-muted)] border-b border-[var(--color-border)]">
                  <th className="py-3 px-4">{t('admin.coupons.colCode')}</th>
                  <th className="py-3 px-4">{t('admin.coupons.colDiscount')}</th>
                  <th className="py-3 px-4 text-right">{t('admin.coupons.colMinOrder')}</th>
                  <th className="py-3 px-4">{t('admin.coupons.colExpires')}</th>
                  <th className="py-3 px-4 text-right">{t('admin.coupons.colUsed')}</th>
                  <th className="py-3 px-4">{t('admin.coupons.colStatus')}</th>
                  <th className="py-3 px-4 text-right">{t('admin.coupons.colActions')}</th>
                </tr>
              </thead>
              <tbody>
                {coupons.map((c) => (
                  <tr key={c.id} className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-card-bg-tint)]">
                    <td className="py-3 px-4">
                      <span className="font-semibold text-[var(--color-primary)]">{c.code}</span>
                      <div className="text-xs text-[var(--color-text-muted)]">{c.description}</div>
                    </td>
                    <td className="py-3 px-4">
                      {c.discountType === 'PERCENT'
                        ? t('admin.coupons.percentOff', { value: c.discountValue })
                        : t('admin.coupons.fixedOff', { amount: formatCurrency(c.discountValue) })}
                    </td>
                    <td className="py-3 px-4 text-right">{c.minimumOrderAmount ? formatCurrency(c.minimumOrderAmount) : '—'}</td>
                    <td className="py-3 px-4 text-[var(--color-text-secondary)]">{c.expiryDate ? formatDate(c.expiryDate) : t('admin.coupons.never')}</td>
                    <td className="py-3 px-4 text-right">{c.timesUsed}{c.maxUses ? ` / ${c.maxUses}` : ''}</td>
                    <td className="py-3 px-4">
                      <button onClick={() => toggleActive(c)} className={`text-xs font-semibold ${c.active ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'}`}>
                        {c.active ? t('admin.coupons.active') : t('admin.coupons.disabled')}
                      </button>
                    </td>
                    <td className="py-3 px-4">
                      <div className="flex justify-end gap-1.5">
                        <button onClick={() => openModal(c)} aria-label={t('common.edit')}
                          className="w-8 h-8 rounded-[var(--radius-md)] flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-border)] transition-colors">
                          <Pencil size={15} />
                        </button>
                        <button onClick={() => remove(c)} aria-label={t('common.delete')}
                          className="w-8 h-8 rounded-[var(--radius-md)] flex items-center justify-center text-[var(--color-error)] hover:bg-[var(--color-error-bg)] transition-colors">
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {modal && (
        <div className="fixed inset-0 z-[var(--z-modal)] bg-black/40 flex items-start justify-center p-4 overflow-y-auto" onClick={() => { setModal(null); setForm(emptyForm); }}>
          <div className="w-full max-w-lg rounded-[var(--radius-xl)] bg-[var(--color-surface)] shadow-[var(--shadow-lg)] mt-10" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-5 border-b border-[var(--color-border)]">
              <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold">
                {modal.coupon
                  ? t('admin.coupons.modalEdit', { code: modal.coupon.code })
                  : t('admin.coupons.modalAdd')}
              </h3>
              <button onClick={() => { setModal(null); setForm(emptyForm); }} className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-[var(--color-card-bg-tint)]" aria-label={t('common.close')}>
                <X size={18} />
              </button>
            </div>
            <form onSubmit={save} className="p-5 space-y-3">
              {!modal.coupon && (
<input className={inputClass} placeholder={t('admin.coupons.codePlaceholder')} value={form.code} disabled={!modal.coupon} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} required />
              )}
              <input className={inputClass} placeholder={t('common.description')} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
              <div className="grid grid-cols-2 gap-3">
                <select className={inputClass} value={form.discountType} onChange={(e) => setForm({ ...form, discountType: e.target.value })} required>
                  <option value="PERCENT">{t('admin.coupons.typePercent')}</option>
                  <option value="FIXED">{t('admin.coupons.typeFixed')}</option>
                </select>
                <input className={inputClass} placeholder={t('admin.coupons.valuePlaceholder')} type="number" min="0" step="0.01" value={form.discountValue} onChange={(e) => setForm({ ...form, discountValue: e.target.value })} required />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <input className={inputClass} placeholder={t('admin.coupons.minOrderPlaceholder')} type="number" min="0" step="0.01" value={form.minimumOrderAmount} onChange={(e) => setForm({ ...form, minimumOrderAmount: e.target.value })} />
                <input className={inputClass} placeholder={t('admin.coupons.maxUsesPlaceholder')} type="number" min="1" value={form.maxUses} onChange={(e) => setForm({ ...form, maxUses: e.target.value })} />
              </div>
              <input className={inputClass} placeholder={t('admin.coupons.expiryPlaceholder')} type="date" value={form.expiryDate} onChange={(e) => setForm({ ...form, expiryDate: e.target.value })} />
              {error && <p className="text-sm text-[var(--color-error)]">{error}</p>}
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={() => { setModal(null); setForm(emptyForm); }}
                  className="flex-1 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)] py-2.5 text-sm font-semibold">{t('common.cancel')}</button>
                <button type="submit" disabled={saving} className="flex-1 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white py-2.5 text-sm font-semibold disabled:opacity-50">
                  {saving ? t('admin.coupons.saving') : modal.coupon ? t('admin.coupons.saveChanges') : t('admin.coupons.add')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}