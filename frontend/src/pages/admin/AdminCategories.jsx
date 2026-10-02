import { useState, useEffect } from 'react';
import { Plus, Pencil, Trash2, X } from 'lucide-react';
import axiosClient from '../../api/axiosClient';

const emptyForm = { name: '', parentId: '' };

export default function AdminCategories() {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await axiosClient.get('/categories');
      setCategories(res.data);
    } catch {
      setError('Failed to load categories. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const openModal = (category) => {
    setForm(category ? { name: category.name, parentId: category.parentId ?? '' } : { ...emptyForm });
    setError('');
    setModal(category ? { category } : {});
  };

  const save = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      const body = { name: form.name, parentId: form.parentId === '' ? null : parseInt(form.parentId) };
      if (modal.category) {
        await axiosClient.put(`/categories/${modal.category.id}`, body);
      } else {
        await axiosClient.post('/categories', body);
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

  const remove = async (category) => {
    if (!window.confirm(`Delete category "${category.name}"? You cannot delete a category that still has products.`)) return;
    try {
      await axiosClient.delete(`/categories/${category.id}`);
      load();
    } catch (err) {
      alert(err.response?.data || 'Delete failed — category still has products.');
    }
  };

  const inputClass = 'w-full px-3 py-2 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] outline-none text-sm';

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">Loading categories...</p>;

  if (error) return <p className="py-12 text-center text-[var(--color-error)]">{error}</p>;

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold">Categories</h2>
        <button
          onClick={() => openModal(null)}
          className="inline-flex items-center gap-1.5 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-4 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
        >
          <Plus size={16} /> Add Category
        </button>
      </div>

      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wide text-[var(--color-text-muted)] border-b border-[var(--color-border)]">
                <th className="py-3 px-4">Category</th>
                <th className="py-3 px-4">Parent</th>
                <th className="py-3 px-4 text-right">Products</th>
                <th className="py-3 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {categories.map((c) => (
                <tr key={c.id} className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-card-bg-tint)]">
                  <td className="py-3 px-4 font-semibold">{c.name}</td>
                  <td className="py-3 px-4 text-[var(--color-text-secondary)]">{c.parentId ? categories.find((p) => p.id === c.parentId)?.name || `#${c.parentId}` : '—'}</td>
                  <td className="py-3 px-4 text-right font-medium">{c.productCount}</td>
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
      </div>

      {modal && (
        <div className="fixed inset-0 z-[var(--z-modal)] bg-black/40 flex items-start justify-center p-4 overflow-y-auto" onClick={() => { setModal(null); setForm(emptyForm); }}>
          <div className="w-full max-w-md rounded-[var(--radius-xl)] bg-[var(--color-surface)] shadow-[var(--shadow-lg)] mt-10" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-5 border-b border-[var(--color-border)]">
              <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold">
                {modal.category ? `Edit: ${modal.category.name}` : 'Add a new category'}
              </h3>
              <button onClick={() => { setModal(null); setForm(emptyForm); }} className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-[var(--color-card-bg-tint)]" aria-label="Close">
                <X size={18} />
              </button>
            </div>
            <form onSubmit={save} className="p-5 space-y-3">
              <input className={inputClass} placeholder="Category name *" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
              <select className={inputClass} value={form.parentId} onChange={(e) => setForm({ ...form, parentId: e.target.value })}>
                <option value="">No parent (top-level)</option>
                {categories.filter((c) => !modal.category || c.id !== modal.category.id).map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
              {error && <p className="text-sm text-[var(--color-error)]">{error}</p>}
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={() => { setModal(null); setForm(emptyForm); }}
                  className="flex-1 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)] py-2.5 text-sm font-semibold">Cancel</button>
                <button type="submit" disabled={saving} className="flex-1 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white py-2.5 text-sm font-semibold disabled:opacity-50">
                  {saving ? 'Saving...' : modal.category ? 'Save Changes' : 'Add Category'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}