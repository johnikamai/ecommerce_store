import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { X, ShoppingCart, Scale, Trash2, Search, Plus } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useCompare } from '../context/CompareContext';
import { useCart } from '../context/CartContext';
import { useLanguage } from '../context/LanguageContext';
import ProductImage from '../components/ProductImage';

/**
 * Side-by-side comparison of the products ticked on the catalogue or a product
 * page.
 *
 * Rows are defined as data below rather than hand-written per attribute so that
 * adding a comparison dimension later is a one-line change, and so the "best
 * value" highlighting can be driven off the same definition instead of being
 * hand-maintained in a second place.
 *
 * "Best" is only ever claimed where best is unambiguous - cheapest, highest
 * rated, most sustainable. Price is the only row where a single winner is
 * always meaningful, so that is the one the table calls out.
 */
const ROWS = [
  { key: 'price', label: 'Price', best: 'lowest', format: (p) => `₹${p.price}`, raw: (p) => Number(p.price) },
  { key: 'category', label: 'Category', format: (p) => p.category || '—' },
  { key: 'averageRating', label: 'Rating', best: 'highest', format: (p) => (p.averageRating ? `${Number(p.averageRating).toFixed(1)} / 5` : 'No reviews yet'), raw: (p) => Number(p.averageRating || 0) },
  { key: 'reviewCount', label: 'Reviews', format: (p) => p.reviewCount ?? 0, raw: (p) => Number(p.reviewCount || 0) },
  { key: 'unitsSold', label: 'Units sold', format: (p) => p.unitsSold ?? 0, raw: (p) => Number(p.unitsSold || 0) },
  { key: 'description', labelKey: 'compare.description', format: (p) => p.description || '—' },
  {
    key: 'sustainabilityScore',
    label: 'Sustainability',
    format: (p) => (p.sustainabilityScore == null ? 'Not rated' : `${p.sustainabilityScore} / 100 — ${p.sustainabilityLabel}`),
    raw: (p) => Number(p.sustainabilityScore ?? -1),
  },
  {
    key: 'stockQuantity',
    label: 'Availability',
    format: (p, t) => (p.stockQuantity > 0 ? `${p.stockQuantity} ${t('compare.inStock')}` : t('compare.outOfStock')),
    raw: (p) => Number(p.stockQuantity || 0),
  },
];

function winners(row, products) {
  if (!row.best || products.length < 2) return new Set();
  const values = products.map(row.raw).filter((v) => Number.isFinite(v));
  // Nothing to compare when every product is unrated / unscored.
  if (values.every((v) => v <= 0)) return new Set();
  const target = row.best === 'lowest' ? Math.min(...values) : Math.max(...values);
  const ids = new Set();
  products.forEach((p, i) => {
    if (row.raw(p) === target) ids.add(p.id ?? i);
  });
  return ids;
}

export default function Compare() {
  const { ids, remove, clear, add, count, isFull } = useCompare();
  const { addToCart } = useCart();
  const { t } = useLanguage();
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [catalog, setCatalog] = useState([]);
  const [search, setSearch] = useState('');
  const [highlightDiffs, setHighlightDiffs] = useState(false);
  const [diffsOnly, setDiffsOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    // One catalogue request, then pick out the ticked ids. Cheaper and more
    // consistent than N parallel per-product calls that could each return a
    // different price mid-render.
    axiosClient
      .get('/products/catalog')
      .then((res) => {
        if (cancelled) return;
        const all = res.data?.products || res.data || [];
        setCatalog(all);
        setProducts(ids.map((id) => all.find((p) => p.id === id)).filter(Boolean));
      })
      .catch(() => {
        if (!cancelled) setError('Could not load those products. Please try again.');
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [ids.join(','), count]);

  const matches = search.trim()
    ? catalog.filter((product) => product.name?.toLowerCase().includes(search.trim().toLowerCase())).slice(0, 6)
    : [];
  const visibleRows = ROWS.filter((row) => !diffsOnly || new Set(products.map((product) => row.format(product, t))).size > 1);
  const productPicker = (
    <div className="mb-5">
      <label className="block text-sm font-semibold mb-2" htmlFor="compare-product-search">{t('compare.selectProduct')}</label>
      <div className="flex items-center gap-2 px-3 h-11 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-card-bg)]">
        <Search size={16} className="text-[var(--color-text-muted)] shrink-0" />
        <input
          id="compare-product-search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t('compare.searchPlaceholder')}
          className="w-full bg-transparent outline-none text-sm"
        />
      </div>
      {search.trim() && (
        <div className="mt-2 divide-y divide-[var(--color-border)] rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-card-bg)]">
          {matches.length === 0 ? (
            <p className="p-3 text-sm text-[var(--color-text-muted)]">{t('compare.noMatches')}</p>
          ) : matches.map((product) => {
            const selected = ids.includes(product.id);
            return (
              <div key={product.id} className="flex items-center justify-between gap-3 p-2.5">
                <span className="min-w-0 truncate text-sm">{product.name}</span>
                <button
                  type="button"
                  onClick={() => { if (add(product.id)) setSearch(''); }}
                  disabled={selected || isFull}
                  title={isFull && !selected ? t('product.compareLimit') : undefined}
                  className="inline-flex shrink-0 items-center gap-1 rounded-[var(--radius-sm)] border border-[var(--color-border)] px-2.5 py-1.5 text-xs font-semibold text-[var(--color-primary)] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <Plus size={13} /> {selected ? t('compare.alreadyComparing') : t('compare.addSlot')}
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );

  if (count === 0) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16 text-center">
        <Scale className="w-12 h-12 mx-auto text-[var(--color-border)]" />
        <h1 className="font-[family-name:var(--font-heading)] text-2xl font-bold mt-4">{t('compare.title')}</h1>
        <p className="text-[var(--color-text-muted)] mt-2 mb-6">{t('compare.emptyNotice')}</p>
        {productPicker}
        <Link
          to="/products"
          className="inline-block mt-6 px-6 py-3 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-semibold hover:bg-[var(--color-primary-hover)]"
        >
          Browse products
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="font-[family-name:var(--font-heading)] text-2xl font-bold">{t('compare.title')}</h1>
        <button
          onClick={clear}
          className="flex items-center gap-1.5 text-sm text-[var(--color-text-muted)] hover:text-[var(--color-error)]"
        >
          <Trash2 className="w-4 h-4" /> Clear all
        </button>
      </div>

      {loading && <p className="text-[var(--color-text-muted)] text-sm">Loading…</p>}
      {error && <p className="text-[var(--color-error)] text-sm">{error}</p>}
      {productPicker}

      {!loading && !error && products.length > 1 && (
        <div className="flex flex-wrap gap-x-5 gap-y-2 mb-4 text-sm">
          <label className="inline-flex items-center gap-2 cursor-pointer">
            <input type="checkbox" checked={highlightDiffs} onChange={(event) => setHighlightDiffs(event.target.checked)} />
            {t('compare.highlightDiffs')}
          </label>
          <label className="inline-flex items-center gap-2 cursor-pointer">
            <input type="checkbox" checked={diffsOnly} onChange={(event) => setDiffsOnly(event.target.checked)} />
            {t('compare.diffsOnly')}
          </label>
        </div>
      )}

      {!loading && !error && products.length > 0 && (
        <>
          <div
            className="overflow-x-auto rounded-[var(--radius-lg)] shadow-[var(--shadow-sm)] bg-[var(--color-card-bg)]"
          >
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr>
                  <th className="p-4 text-left text-[var(--color-text-muted)] font-medium w-40">Product</th>
                  {products.map((p) => (
                    <th key={p.id} className="p-4 text-left align-top min-w-[200px]">
                      <div className="relative">
                        <button
                          onClick={() => remove(p.id)}
                          aria-label={`Remove ${p.name} from comparison`}
                          className="absolute top-0 right-0 p-1 rounded-full bg-[var(--color-card-bg-tint)] hover:bg-[var(--color-error-bg)] hover:text-[var(--color-error)]"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                        <Link to={`/product/${p.id}`} className="block w-24 mb-2">
                          <ProductImage product={p} alt={p.name} className="w-24 h-24 object-cover rounded-[var(--radius-md)]" />
                        </Link>
                        <Link to={`/product/${p.id}`} className="font-semibold hover:text-[var(--color-primary)] leading-snug">
                          {p.name}
                        </Link>
                      </div>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {visibleRows.map((row) => {
                  const best = winners(row, products);
                  const values = products.map((product) => row.format(product, t));
                  const differs = new Set(values).size > 1;
                  return (
                    <tr key={row.key} className="border-t-[var(--color-border)]">
                      <th scope="row" className="p-4 text-left font-medium text-[var(--color-text-muted)] align-top">
                        {row.labelKey ? t(row.labelKey) : row.label}
                      </th>
                      {products.map((p) => {
                        const isBest = best.has(p.id);
                        return (
                          <td
                            key={p.id}
                            className={
                              'p-4 align-top ' +
                              isBest
                                ? 'font-semibold text-[var(--color-success)] bg-[var(--color-success-bg)]'
                                : highlightDiffs && differs ? 'bg-[var(--color-warning-bg)]' : ''
                            }
                          >
                            {values[products.indexOf(p)]}
                            {isBest && row.best && (
                              <span className="block text-[10px] font-normal uppercase tracking-wide opacity-80">
                                {t(row.best === 'lowest' ? 'compare.bestPrice' : 'compare.bestValue')}
                              </span>
                            )}
                          </td>
                        );
                      })}
                    </tr>
                  );
                })}
                <tr className="border-t-[var(--color-border)]">
                  <th className="p-4 text-left font-medium text-[var(--color-text-muted)]">Actions</th>
                  {products.map((p) => (
                    <td key={p.id} className="p-4">
                      <button
                        onClick={() => {
                          addToCart(p, 1);
                          navigate('/cart');
                        }}
                        disabled={p.stockQuantity <= 0}
                        className="flex items-center gap-1.5 px-4 py-2 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-xs font-semibold hover:bg-[var(--color-primary-hover)] disabled:opacity-40 disabled:cursor-not-allowed"
                      >
                        <ShoppingCart className="w-3.5 h-3.5" />
                        {p.stockQuantity > 0 ? 'Add to cart' : 'Out of stock'}
                      </button>
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>
          </div>

          {diffsOnly && visibleRows.length === 0 && (
            <p className="py-4 text-sm text-[var(--color-text-muted)]">{t('compare.noDifferences')}</p>
          )}

          <p className="text-xs text-[var(--color-text-muted)] mt-4">
            Prices and stock are read live from the catalogue. Adding two or more of these to your cart
            also unlocks a bundle discount at checkout.
          </p>
        </>
      )}

      {!loading && !error && products.length < count && (
        <p className="text-sm text-[var(--color-text-muted)] mt-4">
          {count - products.length} selected product(s) are no longer in the catalogue and were left out.
        </p>
      )}
    </div>
  );
}
