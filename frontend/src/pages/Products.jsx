import { useState, useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, Plus, ChevronDown, Sparkles, ArrowRight, Star, X, Loader2 } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import ProductCard from '../components/ProductCard';
import { useLanguage } from '../context/LanguageContext';

// Labels are translation keys, not text: the same list renders in all three
// locales, and `key` is what the sort logic and the URL param actually use.
const SORT_OPTIONS = [
  { key: 'default', labelKey: 'catalog.sort.popularity' },
  { key: 'rating', labelKey: 'catalog.sort.rating' },
  { key: 'low', labelKey: 'catalog.sort.priceLowHigh' },
  { key: 'high', labelKey: 'catalog.sort.priceHighLow' },
  { key: 'name', labelKey: 'catalog.sort.name' },
];

function SkeletonCard() {
  return (
    <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden animate-pulse">
      <div className="aspect-square bg-[var(--color-card-bg-tint)]" />
      <div className="p-[var(--space-4)] space-y-2">
        <div className="h-3 bg-[var(--color-card-bg-tint)] rounded-[var(--radius-sm)] w-1/3" />
        <div className="h-4 bg-[var(--color-card-bg-tint)] rounded-[var(--radius-sm)] w-3/4" />
        <div className="h-4 bg-[var(--color-card-bg-tint)] rounded-[var(--radius-sm)] w-1/2" />
      </div>
    </div>
  );
}

function Hero({ onCategory }) {
  const { t } = useLanguage();
  return (
    <section className="rounded-[var(--radius-xl)] bg-gradient-hero p-8 md:p-12 mb-[var(--space-9)] overflow-hidden relative">
      <div className="max-w-2xl relative z-10">
        <span className="inline-flex items-center gap-1.5 rounded-full bg-white/70 px-3 py-1 text-xs font-semibold text-[var(--color-primary)] mb-4">
          <Sparkles size={14} /> {t('catalog.hero.badge')}
        </span>
        <h1 className="font-[family-name:var(--font-heading)] text-[32px] md:text-[40px] font-bold leading-[1.15] text-[var(--color-text-primary)] mb-3">
          {t('catalog.hero.title')}
        </h1>
        <p className="text-[var(--color-text-secondary)] text-[17px] mb-6 max-w-lg">
          {t('catalog.hero.subtitle')}
        </p>
        <div className="flex flex-wrap gap-3">
          <a
            href="#grid"
            className="inline-flex items-center gap-2 min-h-[44px] rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-6 py-3 text-sm font-semibold hover:bg-[var(--color-primary-hover)] hover:shadow-[var(--shadow-md)] hover:-translate-y-0.5 transition-all"
          >
            {t('catalog.hero.cta')} <ArrowRight size={16} />
          </a>
          <button
            onClick={() => onCategory('Groceries & Food')}
            className="inline-flex items-center min-h-[44px] rounded-[var(--radius-md)] bg-white text-[var(--color-primary)] px-6 py-3 text-sm font-semibold hover:bg-[var(--color-white)] hover:shadow-[var(--shadow-md)] transition-all"
          >
            {t('catalog.hero.browseGroceries')}
          </button>
        </div>
      </div>
      {/* Decorative price cards */}
      <div className="absolute -right-6 top-1/2 -translate-y-1/2 hidden lg:flex flex-col gap-4 rotate-3">
        {[
          { label: t('catalog.hero.cardGoldTier'), value: t('catalog.hero.cardGoldValue') },
          { label: t('catalog.hero.cardRefer'), value: t('catalog.hero.cardReferValue') },
          { label: t('catalog.hero.cardBackInStock'), value: t('catalog.hero.cardAlerts') },
        ].map((card) => (
          <div key={card.label} className="rounded-[var(--radius-lg)] bg-white shadow-[var(--shadow-md)] px-5 py-3">
            <p className="text-xs text-[var(--color-text-muted)]">{card.label}</p>
            <p className="font-[family-name:var(--font-heading)] font-bold text-[var(--color-text-primary)]">{card.value}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

function Products() {
  const { t } = useLanguage();
  const [searchParams, setSearchParams] = useSearchParams();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || '');
  const [selectedCategory, setSelectedCategory] = useState(searchParams.get('category') || 'All');
  const [sort, setSort] = useState('default');
  const [sortOpen, setSortOpen] = useState(false);
  const [minPrice, setMinPrice] = useState('');
  const [maxPrice, setMaxPrice] = useState('');

  // Facets beyond price. There is no brand field on the catalogue, so brand
  // filtering is deliberately absent rather than guessed from the product name.
  const [minRating, setMinRating] = useState(0);
  const [inStockOnly, setInStockOnly] = useState(false);
  const [minSustainability, setMinSustainability] = useState(0);

  // Natural-language search. The server owns ranking, typo correction and
  // synonym expansion; the client only holds the resulting id order so the
  // already-loaded catalogue can still be filtered by the facets above.
  const [rankedIds, setRankedIds] = useState(null);
  const [searchMeta, setSearchMeta] = useState({ correctedQuery: '', suggestions: [], minPrice: null, maxPrice: null });
  const [searching, setSearching] = useState(false);

  // Admin add form
  const role = localStorage.getItem('role');
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('');
  const [price, setPrice] = useState('');
  const [stockQuantity, setStockQuantity] = useState('');
  const [sustainabilityScore, setSustainabilityScore] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [formError, setFormError] = useState('');

  const fetchProducts = async () => {
    try {
      const response = await axiosClient.get('/products/catalog');
      setProducts(response.data);
      setError('');
    } catch (err) {
      setError(t('catalog.loadError'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProducts();
  }, []);

  // Every filter lives in the URL, not just the search box, so a filtered view
  // can be bookmarked or shared and the mega menu can deep-link into one
  // (see the "Top rated" / "Eco picks" promos in the header).
  useEffect(() => {
    setSearchQuery(searchParams.get('q') || '');
    setSelectedCategory(searchParams.get('category') || 'All');
    setSort(searchParams.get('sort') || 'default');
    const rating = Number(searchParams.get('minRating') || 0);
    setMinRating(Number.isFinite(rating) ? rating : 0);
    setInStockOnly(searchParams.get('inStock') === 'true');
    const sustain = Number(searchParams.get('sustainable') || 0);
    setMinSustainability(Number.isFinite(sustain) ? sustain : 0);
  }, [searchParams]);

  /**
   * Writes the current filter state back to the URL. Only non-default values
   * are written so a plain browse stays at a clean /products instead of
   * trailing a string of empty parameters.
   */
  const syncUrl = (overrides = {}) => {
    const next = {
      q: searchQuery.trim(),
      category: selectedCategory !== 'All' ? selectedCategory : '',
      sort: sort !== 'default' ? sort : '',
      minRating: minRating > 0 ? String(minRating) : '',
      inStock: inStockOnly ? 'true' : '',
      sustainable: minSustainability > 0 ? String(minSustainability) : '',
      minPrice,
      maxPrice,
      ...overrides,
    };
    const params = new URLSearchParams();
    Object.entries(next).forEach(([key, value]) => {
      if (value !== '' && value !== null && value !== undefined && value !== false) {
        params.set(key, String(value));
      }
    });
    setSearchParams(params, { replace: true });
  };

  /**
   * Runs the natural-language search, debounced.
   *
   * Typing "wireless earbuds" fires eight keystrokes; without the debounce that
   * is eight full-catalog scans plus eight chances for responses to arrive out of
   * order and repaint stale results.
   */
  useEffect(() => {
    const query = searchQuery.trim();
    if (!query) {
      setRankedIds(null);
      setSearchMeta({ correctedQuery: '', suggestions: [], minPrice: null, maxPrice: null });
      return undefined;
    }

    let cancelled = false;
    setSearching(true);
    const timer = setTimeout(async () => {
      try {
        const res = await axiosClient.get('/products/search', { params: { q: query } });
        if (cancelled) return;
        const rows = res.data.results || [];
        setRankedIds(rows.map((r) => r.id));
        setSearchMeta({
          correctedQuery: res.data.correctedQuery || '',
          suggestions: res.data.suggestions || [],
          minPrice: res.data.minPrice ?? null,
          maxPrice: res.data.maxPrice ?? null,
        });
        // A budget in the phrasing ("earbuds under 3000") becomes a real price
        // filter, so the control visibly reflects what was understood.
        if (res.data.maxPrice != null) setMaxPrice(String(res.data.maxPrice));
        if (res.data.minPrice != null) setMinPrice(String(res.data.minPrice));
      } catch {
        if (!cancelled) setRankedIds(null);
      } finally {
        if (!cancelled) setSearching(false);
      }
    }, 300);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [searchQuery]);

  const categories = ['All', ...new Set(products.map((p) => p.category))];

  // id -> relevance position. A Map lookup instead of Array.includes keeps the
  // filter and the sort linear rather than quadratic over 600-odd products.
  const rankIndex = useMemo(() => {
    const map = new Map();
    if (rankedIds) rankedIds.forEach((id, i) => map.set(id, i));
    return map;
  }, [rankedIds]);

  const filteredProducts = products.filter((product) => {
    // Text relevance comes from the server. `rankedIds === null` while a search is
    // in flight, so the grid keeps showing the previous set instead of flashing
    // empty on every keystroke.
    const matchesSearch = !rankedIds || rankIndex.has(product.id);
    const matchesCategory = selectedCategory === 'All' || product.category === selectedCategory;
    const price = Number(product.price);
    const matchesMinPrice = minPrice === '' || price >= Number(minPrice);
    const matchesMaxPrice = maxPrice === '' || price <= Number(maxPrice);
    const matchesRating = minRating === 0 || Number(product.averageRating || 0) >= minRating;
    const matchesStock = !inStockOnly || Number(product.stockQuantity || 0) > 0;
    const matchesSustainability =
      minSustainability === 0 || Number(product.sustainabilityScore || 0) >= minSustainability;
    return (
      matchesSearch &&
      matchesCategory &&
      matchesMinPrice &&
      matchesMaxPrice &&
      matchesRating &&
      matchesStock &&
      matchesSustainability
    );
  });

  const sortedProducts = [...filteredProducts].sort((a, b) => {
    if (sort === 'low') return a.price - b.price;
    if (sort === 'high') return b.price - a.price;
    if (sort === 'name') return a.name.localeCompare(b.name);
    if (sort === 'rating') return (b.averageRating || 0) - (a.averageRating || 0);
    // With an active search and no explicit choice, keep the server's relevance
    // order. Re-sorting by popularity here would throw the ranking away and make
    // a good typo correction look broken.
    if (rankedIds) return rankIndex.get(a.id) - rankIndex.get(b.id);
    return (b.unitsSold || 0) - (a.unitsSold || 0);
  });

  /** Facets currently narrowing the grid, rendered as removable chips. */
  const activeChips = [];
  if (selectedCategory !== 'All') {
    activeChips.push({ key: 'category', label: selectedCategory, clear: () => selectCategory('All') });
  }
  if (minPrice !== '') {
    activeChips.push({ key: 'min', label: t('catalog.chip.min', { value: minPrice }), clear: () => setMinPrice('') });
  }
  if (maxPrice !== '') {
    activeChips.push({ key: 'max', label: t('catalog.chip.max', { value: maxPrice }), clear: () => setMaxPrice('') });
  }
  if (minRating > 0) {
    activeChips.push({ key: 'rating', label: t('catalog.chip.rating', { value: minRating }), clear: () => { setMinRating(0); syncUrl({ minRating: '' }); } });
  }
  if (inStockOnly) {
    activeChips.push({ key: 'stock', label: t('catalog.chip.inStock'), clear: () => { setInStockOnly(false); syncUrl({ inStock: '' }); } });
  }
  if (minSustainability > 0) {
    activeChips.push({
      key: 'sustainability',
      label: t('catalog.chip.eco', { value: minSustainability }),
      clear: () => { setMinSustainability(0); syncUrl({ sustainable: '' }); },
    });
  }

  const clearAllFilters = () => {
    selectCategory('All');
    setMinPrice('');
    setMaxPrice('');
    setMinRating(0);
    setInStockOnly(false);
    setMinSustainability(0);
    setSearchQuery('');
    setSearchParams({}, { replace: true });
  };

  const selectCategory = (cat) => {
    setSelectedCategory(cat);
    // Overrides rather than a bare replace, so changing category keeps the
    // other filters (and the search text) instead of silently discarding them.
    syncUrl({ category: cat === 'All' ? '' : cat });
  };

  const handleAddProduct = async (e) => {
    e.preventDefault();
    setFormError('');
    try {
      await axiosClient.post('/products', {
        name,
        description,
        category,
        price: parseFloat(price),
        stockQuantity: parseInt(stockQuantity),
        sustainabilityScore: sustainabilityScore === '' ? null : parseInt(sustainabilityScore),
        imageUrl: imageUrl === '' ? null : imageUrl,
      });
      setName(''); setDescription(''); setCategory(''); setPrice(''); setStockQuantity(''); setSustainabilityScore(''); setImageUrl('');
      setShowForm(false);
      fetchProducts();
      setSelectedCategory('All');
    } catch (err) {
      setFormError(t('catalog.addProductFailed'));
    }
  };

  const browsing = searchQuery || selectedCategory !== 'All';

  return (
    <div className="container-x py-[var(--space-8)]">
      {!browsing && <Hero onCategory={selectCategory} />}

      {/* Controls */}
      <div className="flex flex-col gap-4 mb-[var(--space-6)]">
        <h2 id="grid" className="font-[family-name:var(--font-heading)] text-[32px] font-bold">
          {browsing ? t('catalog.titleProducts') : t('catalog.titleShopByCategory')}
        </h2>

        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 px-4 h-11 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] focus-within:bg-[var(--color-surface)] focus-within:shadow-[var(--shadow-sm)] focus-within:border-[1.5px] focus-within:border-[var(--color-primary)] transition-all w-full sm:w-72">
            <Search size={18} className="text-[var(--color-text-muted)] shrink-0" />
            <input
              value={searchQuery}
              onChange={(e) => { setSearchQuery(e.target.value); setSearchParams(e.target.value ? { q: e.target.value } : {}, { replace: true }); }}
              placeholder={t('catalog.searchPlaceholder')}
              aria-label={t('catalog.searchAria')}
              className="w-full bg-transparent outline-none text-[15px]"
            />
            {searching && <Loader2 size={16} className="text-[var(--color-text-muted)] animate-spin shrink-0" />}
          </div>

          <div className="flex flex-wrap gap-2">
            {categories.map((cat) => (
              <button
                key={cat}
                onClick={() => selectCategory(cat)}
                className={`px-4 py-2 rounded-[var(--radius-full)] text-sm font-medium transition-colors ${
                  selectedCategory === cat
                    ? 'bg-[var(--color-primary)] text-white'
                    : 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)] hover:bg-[var(--color-border)]'
                }`}
              >
                {cat === 'All' ? t('catalog.all') : cat}
              </button>
            ))}
          </div>

          {role === 'ADMIN' && (
            <button
              onClick={() => setShowForm(!showForm)}
              className="inline-flex items-center gap-1.5 min-h-[44px] rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-4 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
            >
              <Plus size={16} /> {showForm ? t('common.cancel') : t('catalog.addProduct')}
            </button>
          )}
        </div>

        {/* Price filter */}
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] px-4 h-11">
            <span className="text-xs font-medium text-[var(--color-text-muted)] uppercase tracking-wide">{t('catalog.price')}</span>
            <input
              type="number"
              min="0"
              placeholder={t('catalog.min')}
              value={minPrice}
              onChange={(e) => setMinPrice(e.target.value)}
              className="w-24 bg-transparent outline-none text-sm border-l border-[var(--color-border)] pl-3"
            />
            <span className="text-[var(--color-text-muted)]">–</span>
            <input
              type="number"
              min="0"
              placeholder={t('catalog.max')}
              value={maxPrice}
              onChange={(e) => setMaxPrice(e.target.value)}
              className="w-24 bg-transparent outline-none text-sm"
            />
            {(minPrice !== '' || maxPrice !== '') && (
              <button
                onClick={() => { setMinPrice(''); setMaxPrice(''); }}
                className="text-xs text-[var(--color-primary)] font-semibold hover:underline"
              >
                {t('common.clear')}
              </button>
            )}
          </div>

          {/* Rating facet */}
          <label className="flex items-center gap-2 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] px-4 h-11">
            <span className="text-xs font-medium text-[var(--color-text-muted)] uppercase tracking-wide">{t('catalog.rating')}</span>
            <select
              value={minRating}
              onChange={(e) => { const v = Number(e.target.value); setMinRating(v); syncUrl({ minRating: v > 0 ? String(v) : '' }); }}
              className="bg-transparent outline-none text-sm"
            >
              <option value={0}>{t('common.any')}</option>
              <option value={4}>{t('catalog.ratingUp', { value: 4 })}</option>
              <option value={3}>{t('catalog.ratingUp', { value: 3 })}</option>
            </select>
          </label>

          {/* Sustainability facet - the catalogue scores every product 0-100. */}
          <label className="flex items-center gap-2 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] px-4 h-11">
            <span className="text-xs font-medium text-[var(--color-text-muted)] uppercase tracking-wide">{t('catalog.eco')}</span>
            <select
              value={minSustainability}
              onChange={(e) => { const v = Number(e.target.value); setMinSustainability(v); syncUrl({ sustainable: v > 0 ? String(v) : '' }); }}
              className="bg-transparent outline-none text-sm"
            >
              <option value={0}>{t('common.any')}</option>
              <option value={60}>{t('catalog.scoreUp', { value: 60 })}</option>
              <option value={75}>{t('catalog.scoreUp', { value: 75 })}</option>
              <option value={90}>{t('catalog.scoreUp', { value: 90 })}</option>
            </select>
          </label>

          <label className="flex items-center gap-2 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] px-4 h-11 cursor-pointer">
            <input
              type="checkbox"
              checked={inStockOnly}
              onChange={(e) => { setInStockOnly(e.target.checked); syncUrl({ inStock: e.target.checked ? 'true' : '' }); }}
              className="accent-[var(--color-primary)] w-4 h-4"
            />
            <span className="text-sm">{t('catalog.inStockOnly')}</span>
          </label>
        </div>

        {/* Search understanding: only shown when the engine actually changed
            something, so it never becomes noise the shopper learns to ignore. */}
        {searchMeta.correctedQuery && searchMeta.correctedQuery.toLowerCase() !== searchQuery.trim().toLowerCase() && (
          <p className="text-sm text-[var(--color-text-secondary)]">
            {t('catalog.showingResultsFor')}{' '}
            <button
              onClick={() => setSearchQuery(searchMeta.correctedQuery)}
              className="font-semibold text-[var(--color-primary)] hover:underline"
            >
              {searchMeta.correctedQuery}
            </button>
          </p>
        )}

        {searchMeta.suggestions.length > 0 && (
          <p className="text-xs text-[var(--color-text-muted)]">
            {t('catalog.didYouMean')}{' '}
            {searchMeta.suggestions.map((s) => (
              <button
                key={s}
                onClick={() => setSearchQuery(s)}
                className="text-[var(--color-primary)] hover:underline mr-2"
              >
                {s}
              </button>
            ))}
          </p>
        )}

        {activeChips.length > 0 && (
          <div className="flex flex-wrap items-center gap-2">
            {activeChips.map((chip) => (
              <button
                key={chip.key}
                onClick={chip.clear}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] border-[1.5px] border-[var(--color-border)] text-xs font-medium text-[var(--color-text-secondary)] hover:border-[var(--color-primary)] transition-colors"
              >
                {chip.label}
                <X size={13} />
              </button>
            ))}
            <button
              onClick={clearAllFilters}
              className="text-xs font-semibold text-[var(--color-primary)] hover:underline"
            >
              {t('catalog.clearAll')}
            </button>
          </div>
        )}
      </div>

      {/* Sort + count */}
      <div className="flex items-center justify-between mb-[var(--space-5)]">
        <p className="text-sm text-[var(--color-text-muted)]">
          {sortedProducts.length === 1
            ? t('catalog.countOne', { count: sortedProducts.length })
            : t('catalog.countOther', { count: sortedProducts.length })}
        </p>
        <div className="relative">
          <button
            onClick={() => setSortOpen(!sortOpen)}
            className="inline-flex items-center gap-2 min-h-[44px] rounded-[var(--radius-md)] bg-white text-[var(--color-text-primary)] px-4 text-sm font-semibold shadow-[var(--shadow-xs)] hover:shadow-[var(--shadow-sm)] transition-shadow"
          >
            {t('catalog.sortPrefix')} {SORT_OPTIONS.find((o) => o.key === sort)?.labelKey && t(SORT_OPTIONS.find((o) => o.key === sort).labelKey)}
            {sort === 'rating' && <Star size={14} className="text-[var(--color-accent)]" />}
            <ChevronDown size={16} className={`transition-transform ${sortOpen ? 'rotate-180' : ''}`} />
          </button>
          {sortOpen && (
            <div className="absolute right-0 mt-2 w-56 rounded-[var(--radius-lg)] bg-[var(--color-surface)] shadow-[var(--shadow-lg)] overflow-hidden z-[var(--z-dropdown)]">
              {SORT_OPTIONS.map((opt) => (
                <button
                  key={opt.key}
                  onClick={() => { setSort(opt.key); setSortOpen(false); syncUrl({ sort: opt.key === 'default' ? '' : opt.key }); }}
                  className={`block w-full text-left px-4 py-2.5 text-sm hover:bg-[var(--color-card-bg-tint)] transition-colors ${
                    sort === opt.key ? 'text-[var(--color-primary)] font-semibold' : 'text-[var(--color-text-secondary)]'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Admin form */}
      {role === 'ADMIN' && showForm && (
        <form onSubmit={handleAddProduct} className="mb-8 p-6 rounded-[var(--radius-xl)] bg-[var(--color-card-bg-tint)] max-w-md">
          <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-4">Add a new product</h3>
          <div className="space-y-3">
            <input placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} className="w-full px-4 py-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] focus:shadow-[var(--shadow-glow-primary)] outline-none transition-shadow" />
            <input placeholder="Description" value={description} onChange={(e) => setDescription(e.target.value)} className="w-full px-4 py-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] outline-none" />
            <input placeholder="Category" value={category} onChange={(e) => setCategory(e.target.value)} className="w-full px-4 py-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] outline-none" />
            <input placeholder="Price" type="number" value={price} onChange={(e) => setPrice(e.target.value)} className="w-full px-4 py-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] outline-none" />
            <input placeholder="Stock Quantity" type="number" value={stockQuantity} onChange={(e) => setStockQuantity(e.target.value)} className="w-full px-4 py-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] outline-none" />
            <input placeholder="Image URL (optional — e.g. /images/1.png or any https://... photo)" value={imageUrl} onChange={(e) => setImageUrl(e.target.value)} className="w-full px-4 py-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] outline-none" />
            <div>
              <input placeholder="Sustainability Score (0-100)" type="number" min="0" max="100" value={sustainabilityScore} onChange={(e) => setSustainabilityScore(e.target.value)} className="w-full px-4 py-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] outline-none" />
              <p className="text-xs text-[var(--color-text-muted)] mt-1">Optional. ≥80 Eco-Friendly, ≥50 Moderate, &lt;50 High Impact.</p>
            </div>
          </div>
          {formError && <p className="text-[var(--color-error)] text-sm mt-2">{formError}</p>}
          <button type="submit" className="mt-4 w-full rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white py-3 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors">
            Add Product
          </button>
        </form>
      )}

      {/* Grid */}
      {loading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-5">
          {[...Array(10)].map((_, i) => <SkeletonCard key={i} />)}
        </div>
      ) : error ? (
        <div className="py-20 text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-[var(--color-error-bg)] mb-4 text-2xl">!</div>
          <h4 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-1">Something went wrong.</h4>
          <p className="text-[var(--color-text-muted)] mb-4">{error}</p>
          <button onClick={() => { setLoading(true); fetchProducts(); }} className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors">
            Retry
          </button>
        </div>
      ) : sortedProducts.length === 0 ? (
        <div className="py-20 text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-[var(--color-card-bg-tint)] mb-4 text-2xl">⌕</div>
          <h4 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-1">No results for "{searchQuery}"</h4>
          <p className="text-[var(--color-text-muted)] mb-4">Try clearing the search or picking another category.</p>
          <button onClick={() => selectCategory('All')} className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors">
            Clear filters
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-5">
          {sortedProducts.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              defaultRating={product.averageRating}
              defaultReviewCount={product.reviewCount}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default Products;