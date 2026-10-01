import { useState, useEffect } from 'react';
import { Plus, Pencil, Trash2, X } from 'lucide-react';
import axiosClient from '../../api/axiosClient';

const emptyForm = {
  code: '', description: '', discountType: 'PERCENT', discountValue: '',
  minimumOrderAmount: '', expiryDate: '', maxUses: '',
};

export default function AdminCoupons() {
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
      setError(err.response?.data || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (coupon) => {
    try {
      await axiosClient.put(`/coupons/${coupon.id}/status`, { active: !coupon.active });
      load();
    } catch (err) {
      alert(err.response?.data || 'Action failed');
    }
  };

  const remove = async (coupon) => {
    if (!window.confirm(`Delete coupon "${coupon.code}"?`)) return;
    try {
      await axiosClient.delete(`/coupons/${coupon.id}`);
      load();
    } catch (err) {
      alert(err.response?.data || 'Delete failed');
    }
  };

  const inputClass = 'w-full px-3 py-2 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] outline-none text-sm';

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">Loading coupons...</p>;

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold">Coupons</h2>
        <button
          onClick={() => openModal(null)}
          className="inline-flex items-center gap-1.5 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-4 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
        >
          <Plus size={16} /> Add Coupon
        </button>
      </div>

      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden">
        {coupons.length === 0 ? (
          <p className="p-6 text-sm text-[var(--color-text-muted)]">No coupons yet. Create one to start offering discounts.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-[var(--color-text-muted)] border-b border-[var(--color-border)]">
                  <th className="py-3 px-4">Code</th>
                  <th className="py-3 px-4">Discount</th>
                  <th className="py-3 px-4 text-right">Min Order</th>
                  <th className="py-3 px-4">Expires</th>
                  <th className="py-3 px-4 text-right">Used</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Actions</th>
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
                      {c.discountType === 'PERCENT' ? `${c.discountValue}% off` : `₹${Number(c.discountValue).toLocaleString('en-IN')} off`}
                    </td>
                    <td className="py-3 px-4 text-right">{c.minimumOrderAmount ? `₹${Number(c.minimumOrderAmount).toLocaleString('en-IN')}` : '—'}</td>
                    <td className="py-3 px-4 text-[var(--color-text-secondary)]">{c.expiryDate ? new Date(c.expiryDate).toLocaleDateString('en-IN') : 'Never'}</td>
                    <td className="py-3 px-4 text-right">{c.timesUsed}{c.maxUses ? ` / ${c.maxUses}` : ''}</td>
                    <td className="py-3 px-4">
                      <button onClick={() => toggleActive(c)} className={`text-xs font-semibold ${c.active ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'}`}>
                        {c.active ? 'Active' : 'Disabled'}
                      </button>
                    </td>
                    <td className="py-3 px-4">
                      <div className="flex justify-end gap-1.5">
                        <button onClick={() => openModal(c)} aria-label="Edit"
                          className="w-8 h-8 rounded-[var(--radius-md)] flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-border)] transition-colors">
                          <Pencil size={15} />
                        </button>
                        <button onClick={() => remove(c)} aria-label="Delete"
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
                {modal.coupon ? `Edit coupon: ${modal.coupon.code}` : 'Add a new coupon'}
              </h3>
              <button onClick={() => { setModal(null); setForm(emptyForm); }} className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-[var(--color-card-bg-tint)]" aria-label="Close">
                <X size={18} />
              </button>
            </div>
            <form onSubmit={save} className="p-5 space-y-3">
              {!modal.coupon && (
                <input className={inputClass} placeholder="Coupon code * (e.g. WELCOME20)" value={form.code} disabled={!modal.coupon} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} required />
              )}
              <input className={inputClass} placeholder="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
              <div className="grid grid-cols-2 gap-3">
                <select className={inputClass} value={form.discountType} onChange={(e) => setForm({ ...form, discountType: e.target.value })} required>
                  <option value="PERCENT">Percent (%)</option>
                  <option value="FIXED">Fixed (₹)</option>
                </select>
                <input className={inputClass} placeholder="Discount value *" type="number" min="0" step="0.01" value={form.discountValue} onChange={(e) => setForm({ ...form, discountValue: e.target.value })} required />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <input className={inputClass} placeholder="Minimum order amount" type="number" min="0" step="0.01" value={form.minimumOrderAmount} onChange={(e) => setForm({ ...form, minimumOrderAmount: e.target.value })} />
                <input className={inputClass} placeholder="Max uses (blank = unlimited)" type="number" min="1" value={form.maxUses} onChange={(e) => setForm({ ...form, maxUses: e.target.value })} />
              </div>
              <input className={inputClass} placeholder="Expiry date (optional)" type="date" value={form.expiryDate} onChange={(e) => setForm({ ...form, expiryDate: e.target.value })} />
              {error && <p className="text-sm text-[var(--color-error)]">{error}</p>}
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={() => { setModal(null); setForm(emptyForm); }}
                  className="flex-1 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)] py-2.5 text-sm font-semibold">Cancel</button>
                <button type="submit" disabled={saving} className="flex-1 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white py-2.5 text-sm font-semibold disabled:opacity-50">
                  {saving ? 'Saving...' : modal.coupon ? 'Save Changes' : 'Add Coupon'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}