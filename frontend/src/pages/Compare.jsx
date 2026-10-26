import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { X, ShoppingCart, Scale, Trash2, Search, Plus, AlertTriangle, CheckCircle2, ExternalLink } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useCompare } from '../context/CompareContext';
import { useCart } from '../context/CartContext';
import { useLanguage } from '../context/LanguageContext';
import ProductImage from '../components/ProductImage';
import ComparisonVerdictCard from '../components/ComparisonVerdictCard';
import { generateComparisonVerdict } from '../utils/comparisonVerdict';

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
 * rated. No product is ever labelled "best overall", because that would need a
 * weighted score the catalogue does not provide.
 *
 * Builds the comparison rows from a product, using only values the catalogue
 * actually carries.
 *
 * The backend Product entity exposes name, description, category, price,
 * stockQuantity, imageUrl and sustainabilityScore/Label - and nothing else.
 * Fields the brief mentions but the data does not contain (brand,
 * per-product discount/final price, warranty, category specifications) are
 * deliberately NOT fabricated here: they resolve to t('compare.notSpecified')
 * so the table stays honest. Adding them for real would require new columns on
 * the backend, which is out of scope for this frontend task.
 */
function buildRows(t, formatCurrency) {
  const notSpecified = t('compare.notSpecified');
  return [
    {
      key: 'price',
      label: t('compare.finalPrice'),
      best: 'lowest',
      bestLabel: 'compare.bestPrice',
      format: (p) => formatCurrency(p.price),
      raw: (p) => Number(p.price),
    },
    {
      // The catalogue has no per-product discount, so the discount and final
      // price are the same as the listed price; we show a single honest row.
      key: 'discount',
      label: t('compare.discount'),
      format: () => notSpecified,
    },
    {
      key: 'brand',
      label: t('compare.brand'),
      format: (p) => p.brand || notSpecified,
    },
    {
      key: 'category',
      label: t('common.category'),
      format: (p) => p.category || notSpecified,
    },
    {
      key: 'averageRating',
      label: t('compare.rating'),
      best: 'highest',
      bestLabel: 'compare.bestRating',
      format: (p) => (Number(p.averageRating) > 0 ? `${Number(p.averageRating).toFixed(1)} / 5` : t('compare.noReviewsYet')),
      raw: (p) => Number(p.averageRating || 0),
    },
    {
      key: 'reviewCount',
      label: t('compare.reviews'),
      best: 'highest',
      bestLabel: 'compare.mostReviewed',
      format: (p) => (p.reviewCount ?? 0),
      raw: (p) => Number(p.reviewCount || 0),
    },
    {
      key: 'unitsSold',
      label: t('compare.unitsSold'),
      best: 'highest',
      bestLabel: 'compare.mostSold',
      format: (p) => (p.unitsSold ?? 0),
      raw: (p) => Number(p.unitsSold || 0),
    },
    {
      key: 'description',
      label: t('compare.description'),
      format: (p) => p.description || notSpecified,
    },
    {
      key: 'sustainabilityScore',
      label: t('compare.sustainability'),
      format: (p) => (p.sustainabilityScore == null
        ? notSpecified
        : `${p.sustainabilityScore} / 100 — ${p.sustainabilityLabel}`),
      raw: (p) => Number(p.sustainabilityScore ?? -1),
    },
    {
      key: 'specifications',
      label: t('compare.specifications'),
      format: (p) => p.specifications || notSpecified,
    },
    {
      key: 'warranty',
      label: t('compare.warranty'),
      format: () => notSpecified,
    },
    {
      key: 'stockQuantity',
      label: t('compare.stock'),
      format: (p) => (Number(p.stockQuantity) > 0 ? `${p.stockQuantity} ${t('compare.inStock')}` : t('compare.outOfStock')),
      raw: (p) => Number(p.stockQuantity || 0),
    },
  ];
}

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
  const { ids, remove, clear, add, count, isFull, limit } = useCompare();
  const { addToCart } = useCart();
  const { t, formatCurrency } = useLanguage();
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [catalog, setCatalog] = useState([]);
  const [search, setSearch] = useState('');
  const [highlightDiffs, setHighlightDiffs] = useState(false);
  const [diffsOnly, setDiffsOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const rows = useMemo(() => buildRows(t, formatCurrency), [t, formatCurrency]);
  const verdict = useMemo(() => generateComparisonVerdict(products), [products]);

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

  // Category coherence: warn when the picked products span more than one
  // category, since their specifications are not directly comparable.
  const categories = [...new Set(products.map((p) => p.category).filter(Boolean))];
  const mixedCategories = categories.length > 1;

  const visibleRows = rows.filter((row) => !diffsOnly || new Set(products.map((product) => row.format(product, t))).size > 1);
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
                  onClick={() => { if (add(product.id, product.category)) setSearch(''); }}
                  disabled={selected || isFull}
                  title={isFull && !selected ? t('compare.limitReached', { limit }) : undefined}
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
          {t('compare.browseProducts')}
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
          <Trash2 className="w-4 h-4" /> {t('compare.clearAll')}
        </button>
      </div>

      {loading && <p className="text-[var(--color-text-muted)] text-sm">{t('compare.loading')}</p>}
      {error && <p className="text-[var(--color-error)] text-sm">{error}</p>}
      {productPicker}

      {/* Category coherence: comparing across categories is allowed, but their
          specifications are not directly comparable, so say so. */}
      {!loading && !error && mixedCategories && (
        <p className="mb-4 flex items-start gap-2 rounded-[var(--radius-md)] bg-[var(--color-warning-bg)] text-[var(--color-warning)] px-3 py-2.5 text-sm">
          <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
          <span>{t('compare.mixedCategories', { categories: categories.join(', ') })}</span>
        </p>
      )}
      {!loading && !error && !mixedCategories && categories.length === 1 && products.length > 1 && (
        <p className="mb-4 flex items-start gap-2 rounded-[var(--radius-md)] bg-[var(--color-success-bg)] text-[var(--color-success)] px-3 py-2.5 text-sm">
          <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" />
          <span>{t('compare.sameCategory', { category: categories[0] })}</span>
        </p>
      )}

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
                  <th className="sticky left-0 z-10 bg-[var(--color-card-bg)] p-4 text-left text-[var(--color-text-muted)] font-medium w-36 min-w-[120px]">{t('compare.product')}</th>
                  {products.map((p) => (
                    <th key={p.id} className="p-4 text-left align-top min-w-[200px]">
                      <div className="relative">
                        <button
                          onClick={() => remove(p.id)}
                          aria-label={t('compare.removeFromCompareLabel', { name: p.name })}
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
                      <th scope="row" className="sticky left-0 z-10 bg-[var(--color-card-bg)] p-4 text-left font-medium text-[var(--color-text-muted)] align-top">
                        {row.label}
                      </th>
                      {products.map((p, i) => {
                        const isBest = best.has(p.id);
                        const value = values[i];
                        return (
                          <td
                            key={p.id}
                            className={
                              'p-4 align-top ' +
                              (isBest
                                ? 'font-semibold text-[var(--color-success)] bg-[var(--color-success-bg)]'
                                : highlightDiffs && differs ? 'bg-[var(--color-warning-bg)]' : '')
                            }
                          >
                            {value === t('compare.notSpecified')
                              ? <span className="text-[var(--color-text-muted)] italic">{value}</span>
                              : value}
                            {isBest && row.best && (
                              <span className="block text-[10px] font-normal uppercase tracking-wide opacity-80">
                                {t(row.bestLabel || 'compare.bestPrice')}
                              </span>
                            )}
                          </td>
                        );
                      })}
                    </tr>
                  );
                })}
                <tr className="border-t-[var(--color-border)]">
                  <th className="sticky left-0 z-10 bg-[var(--color-card-bg)] p-4 text-left font-medium text-[var(--color-text-muted)]">{t('compare.actions')}</th>
                  {products.map((p) => (
                    <td key={p.id} className="p-4">
                      <div className="flex flex-wrap items-center gap-2">
                        <button
                          onClick={() => {
                            addToCart(p, 1);
                            navigate('/cart');
                          }}
                          disabled={p.stockQuantity <= 0}
                          className="flex items-center gap-1.5 px-4 py-2 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-xs font-semibold hover:bg-[var(--color-primary-hover)] disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                          <ShoppingCart className="w-3.5 h-3.5" />
                          {p.stockQuantity > 0 ? t('compare.addToCart') : t('compare.outOfStock')}
                        </button>
                        <Link
                          to={`/product/${p.id}`}
                          className="flex items-center gap-1.5 px-4 py-2 rounded-[var(--radius-md)] border border-[var(--color-border)] text-xs font-semibold text-[var(--color-text-secondary)] hover:border-[var(--color-primary)] hover:text-[var(--color-primary)]"
                        >
                          <ExternalLink className="w-3.5 h-3.5" />
                          {t('compare.viewDetails')}
                        </Link>
                        <button
                          onClick={() => remove(p.id)}
                          aria-label={t('compare.removeFromCompareLabel', { name: p.name })}
                          className="flex items-center gap-1.5 px-3 py-2 rounded-[var(--radius-md)] text-xs font-semibold text-[var(--color-text-muted)] hover:bg-[var(--color-error-bg)] hover:text-[var(--color-error)]"
                        >
                          <X className="w-3.5 h-3.5" />
                          {t('compare.removeProduct')}
                        </button>
                      </div>
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>
          </div>

          <ComparisonVerdictCard verdict={verdict} />
          {diffsOnly && visibleRows.length === 0 && (
            <p className="py-4 text-sm text-[var(--color-text-muted)]">{t('compare.noDifferences')}</p>
          )}

          <p className="text-xs text-[var(--color-text-muted)] mt-4">
            {t('compare.liveNote')}
          </p>
        </>
      )}

      {!loading && !error && products.length < count && (
        <p className="text-sm text-[var(--color-text-muted)] mt-4">
          {t('compare.staleNotice', { count: count - products.length })}
        </p>
      )}
    </div>
  );
}
