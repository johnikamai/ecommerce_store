import { useState, useEffect } from 'react';
import { Plus, Search, Pencil, Trash2, X } from 'lucide-react';
import axiosClient from '../../api/axiosClient';

const PAGE_SIZE = 20;

const emptyForm = {
  name: '', description: '', category: '', price: '', stockQuantity: '', sustainabilityScore: '', imageUrl: '',
};

export default function AdminProducts() {
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [search, setSearch] = useState('');

  // Deleting a product is admin-only on the server, so hide the control too.
  // Rendering it for staff produced a button that always failed with 403.
  const [role, setRole] = useState(() => localStorage.getItem('role'));
  useEffect(() => {
    const sync = () => setRole(localStorage.getItem('role'));
    window.addEventListener('storage', sync);
    return () => window.removeEventListener('storage', sync);
  }, []);
  const isAdmin = role === 'ADMIN';
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState(null); // null | { product } for edit, {} for new
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');

  const load = async () => {
    try {
      const [p, c] = await Promise.all([
        axiosClient.get('/products'),
        axiosClient.get('/categories'),
      ]);
      setProducts(p.data);
      setCategories(c.data);
    } catch {
      setMessage({ type: 'error', text: 'Failed to load products' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const openModal = (product) => {
    setForm(product ? {
      name: product.name,
      description: product.description || '',
      category: product.category || '',
      price: product.price,
      stockQuantity: product.stockQuantity,
      reorderLevel: product.reorderLevel ?? '',
      sustainabilityScore: product.sustainabilityScore == null ? '' : product.sustainabilityScore,
      imageUrl: product.imageUrl || '',
    } : { ...emptyForm });
    setModal(product ? { product } : {});
  };

  const closeModal = () => { setModal(null); setForm(emptyForm); setMessage(''); };

  const save = async (e) => {
    e.preventDefault();
    setSaving(true);
    setMessage('');
    try {
      const body = {
        name: form.name,
        description: form.description,
        category: form.category,
        price: parseFloat(form.price),
        stockQuantity: parseInt(form.stockQuantity),
        reorderLevel: form.reorderLevel === '' ? null : parseInt(form.reorderLevel),
        sustainabilityScore: form.sustainabilityScore === '' ? null : parseInt(form.sustainabilityScore),
        imageUrl: form.imageUrl === '' ? null : form.imageUrl,
      };
      if (modal.product) {
        await axiosClient.put(`/products/${modal.product.id}`, body);
      } else {
        await axiosClient.post('/products', body);
      }
      closeModal();
      load();
    } catch (err) {
      setMessage({ type: 'error', text: err.response?.data || 'Save failed' });
    } finally {
      setSaving(false);
    }
  };

  const remove = async (product) => {
    if (!window.confirm(`Delete "${product.name}"? This cannot be undone.`)) return;
    try {
      await axiosClient.delete(`/products/${product.id}`);
      load();
    } catch (err) {
      alert(err.response?.data || 'Delete failed — product may be referenced by orders.');
    }
  };

  const filtered = products.filter(
    (p) => !search || p.name.toLowerCase().includes(search.toLowerCase()) || p.category.toLowerCase().includes(search.toLowerCase())
  );
  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const pageItems = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const inputClass = 'w-full px-3 py-2 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] outline-none text-sm';

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">Loading products...</p>;

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold">Products</h2>
        <button
          onClick={() => openModal(null)}
          className="inline-flex items-center gap-1.5 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-4 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
        >
          <Plus size={16} /> Add Product
        </button>
      </div>

      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden">
        <div className="p-4 border-b border-[var(--color-border)]">
          <div className="flex items-center gap-2 px-3 h-10 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] max-w-sm">
            <Search size={16} className="text-[var(--color-text-muted)]" />
            <input
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(1); }}
              placeholder={`Search ${products.length} products...`}
              className="w-full bg-transparent outline-none text-sm"
            />
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wide text-[var(--color-text-muted)] border-b border-[var(--color-border)]">
                <th className="py-3 px-4">Image</th>
                <th className="py-3 px-4">Product</th>
                <th className="py-3 px-4">Category</th>
                <th className="py-3 px-4 text-right">Price</th>
                <th className="py-3 px-4 text-right">Stock</th>
                <th className="py-3 px-4 text-center">Sust.</th>
                <th className="py-3 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {pageItems.map((p) => (
                <tr key={p.id} className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-card-bg-tint)]">
                  <td className="py-3 px-4">
                    {p.imageUrl ? (
                      <img src={p.imageUrl} alt={p.name} loading="lazy"
                        className="w-12 h-12 rounded-[var(--radius-md)] object-cover border border-[var(--color-border)]"
                        onError={(e) => { e.currentTarget.style.display = 'none'; }} />
                    ) : null}
                  </td>
                  <td className="py-3 px-4">
                    <div className="font-semibold text-[var(--color-text-primary)]">{p.name}</div>
                    <div className="text-xs text-[var(--color-text-muted)] max-w-xs truncate">{p.description}</div>
                  </td>
                  <td className="py-3 px-4 text-[var(--color-text-secondary)]">{p.category}</td>
                  <td className="py-3 px-4 text-right font-medium">₹{Number(p.price).toLocaleString('en-IN')}</td>
                  <td className={`py-3 px-4 text-right font-medium ${p.lowStock ? 'text-[var(--color-warning)]' : ''}`}>
                    {p.stockQuantity}
                  </td>
                  <td className="py-3 px-4 text-center text-xs">{p.sustainabilityScore ?? '—'}</td>
                  <td className="py-3 px-4">
                    <div className="flex justify-end gap-1.5">
<button onClick={() => openModal(p)} aria-label="Edit"
                          className="w-8 h-8 rounded-[var(--radius-md)] flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-border)] transition-colors">
                          <Pencil size={15} />
                        </button>
                        {isAdmin && (
                          <button onClick={() => remove(p)} aria-label="Delete"
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

        {pages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-[var(--color-border)] text-sm">
            <span className="text-[var(--color-text-muted)]">
              Page {page} of {pages} · {filtered.length} products
            </span>
            <div className="flex gap-1.5">
              <button disabled={page <= 1} onClick={() => setPage(page - 1)}
                className="px-3 py-1.5 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-xs font-semibold disabled:opacity-40">Prev</button>
              <button disabled={page >= pages} onClick={() => setPage(page + 1)}
                className="px-3 py-1.5 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-xs font-semibold disabled:opacity-40">Next</button>
            </div>
          </div>
        )}
      </div>

      {modal && (
        <div className="fixed inset-0 z-[var(--z-modal)] bg-black/40 flex items-start justify-center p-4 overflow-y-auto" onClick={closeModal}>
          <div className="w-full max-w-lg rounded-[var(--radius-xl)] bg-[var(--color-surface)] shadow-[var(--shadow-lg)] mt-10" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-5 border-b border-[var(--color-border)]">
              <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold">
                {modal.product ? `Edit: ${modal.product.name}` : 'Add a new product'}
              </h3>
              <button onClick={closeModal} aria-label="Close" className="w-8 h-8 rounded-full flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-card-bg-tint)]">
                <X size={18} />
              </button>
            </div>
            <form onSubmit={save} className="p-5 space-y-3">
              <input className={inputClass} placeholder="Name *" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
              <input className={inputClass} placeholder="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
              <select className={inputClass} value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} required>
                <option value="">Select category...</option>
                {categories.map((c) => <option key={c.id} value={c.name}>{c.name}</option>)}
              </select>
              <div className="grid grid-cols-3 gap-3">
                <input className={inputClass} placeholder="Price (₹) *" type="number" min="0" step="0.01" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} required />
                <input className={inputClass} placeholder="Stock *" type="number" min="0" value={form.stockQuantity} onChange={(e) => setForm({ ...form, stockQuantity: e.target.value })} required />
                <input className={inputClass} placeholder="Alert below (default 10)" type="number" min="0" value={form.reorderLevel} onChange={(e) => setForm({ ...form, reorderLevel: e.target.value })} />
              </div>
              <input className={inputClass} placeholder="Sustainability score (0-100, optional)" type="number" min="0" max="100" value={form.sustainabilityScore} onChange={(e) => setForm({ ...form, sustainabilityScore: e.target.value })} />
              <input className={inputClass} placeholder="Image URL (optional, e.g. /images/1.jpg)" value={form.imageUrl} onChange={(e) => setForm({ ...form, imageUrl: e.target.value })} />
              {message && <p className={`text-sm ${message.type === 'error' ? 'text-[var(--color-error)]' : 'text-[var(--color-success)]'}`}>{message.text}</p>}
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={closeModal} className="flex-1 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)] py-2.5 text-sm font-semibold">Cancel</button>
                <button type="submit" disabled={saving} className="flex-1 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white py-2.5 text-sm font-semibold disabled:opacity-50">
                  {saving ? 'Saving...' : modal.product ? 'Save Changes' : 'Add Product'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}