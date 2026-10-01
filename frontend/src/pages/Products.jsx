import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, Plus, ChevronDown, Sparkles, ArrowRight, Star } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import ProductCard from '../components/ProductCard';

const SORT_OPTIONS = [
  { key: 'default', label: 'Popularity' },
  { key: 'rating', label: 'Top Rated' },
  { key: 'low', label: 'Price: Low to High' },
  { key: 'high', label: 'Price: High to Low' },
  { key: 'name', label: 'Name: A–Z' },
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
  return (
    <section className="rounded-[var(--radius-xl)] bg-gradient-hero p-8 md:p-12 mb-[var(--space-9)] overflow-hidden relative">
      <div className="max-w-2xl relative z-10">
        <span className="inline-flex items-center gap-1.5 rounded-full bg-white/70 px-3 py-1 text-xs font-semibold text-[var(--color-primary)] mb-4">
          <Sparkles size={14} /> New season, good finds
        </span>
        <h1 className="font-[family-name:var(--font-heading)] text-[32px] md:text-[40px] font-bold leading-[1.15] text-[var(--color-text-primary)] mb-3">
          ShopEase — a calmer way to shop.
        </h1>
        <p className="text-[var(--color-text-secondary)] text-[17px] mb-6 max-w-lg">
          Earn loyalty points, discover what's bought together, and get restock alerts. All in one friendly store.
        </p>
        <div className="flex flex-wrap gap-3">
          <a
            href="#grid"
            className="inline-flex items-center gap-2 min-h-[44px] rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-6 py-3 text-sm font-semibold hover:bg-[var(--color-primary-hover)] hover:shadow-[var(--shadow-md)] hover:-translate-y-0.5 transition-all"
          >
            Start Shopping <ArrowRight size={16} />
          </a>
          <button
            onClick={() => onCategory('Groceries & Food')}
            className="inline-flex items-center min-h-[44px] rounded-[var(--radius-md)] bg-white text-[var(--color-primary)] px-6 py-3 text-sm font-semibold hover:bg-[var(--color-white)] hover:shadow-[var(--shadow-md)] transition-all"
          >
            Browse Groceries & Food
          </button>
        </div>
      </div>
      {/* Decorative price cards */}
      <div className="absolute -right-6 top-1/2 -translate-y-1/2 hidden lg:flex flex-col gap-4 rotate-3">
        {[
          { label: 'GOLD tier', value: '5% off' },
          { label: 'Refer a friend', value: '+500 pts' },
          { label: 'Back in stock', value: 'Alerts' },
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
      setError('We couldn\'t load products right now');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProducts();
  }, []);

  useEffect(() => {
    setSearchQuery(searchParams.get('q') || '');
    setSelectedCategory(searchParams.get('category') || 'All');
  }, [searchParams]);

  const categories = ['All', ...new Set(products.map((p) => p.category))];

  const filteredProducts = products.filter((product) => {
    const matchesSearch =
      !searchQuery ||
      product.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (product.description || '').toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCategory = selectedCategory === 'All' || product.category === selectedCategory;
    const price = Number(product.price);
    const matchesMinPrice = minPrice === '' || price >= Number(minPrice);
    const matchesMaxPrice = maxPrice === '' || price <= Number(maxPrice);
    return matchesSearch && matchesCategory && matchesMinPrice && matchesMaxPrice;
  });

  const sortedProducts = [...filteredProducts].sort((a, b) => {
    if (sort === 'low') return a.price - b.price;
    if (sort === 'high') return b.price - a.price;
    if (sort === 'name') return a.name.localeCompare(b.name);
    if (sort === 'rating') return (b.averageRating || 0) - (a.averageRating || 0);
    return (b.unitsSold || 0) - (a.unitsSold || 0);
  });

  const selectCategory = (cat) => {
    setSelectedCategory(cat);
    setSearchParams(cat === 'All' ? {} : { category: cat }, { replace: true });
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
      setFormError('Failed to add product. Are you logged in as an admin?');
    }
  };

  const browsing = searchQuery || selectedCategory !== 'All';

  return (
    <div className="container-x py-[var(--space-8)]">
      {!browsing && <Hero onCategory={selectCategory} />}

      {/* Controls */}
      <div className="flex flex-col gap-4 mb-[var(--space-6)]">
        <h2 id="grid" className="font-[family-name:var(--font-heading)] text-[32px] font-bold">
          {browsing ? 'Products' : 'Shop by category'}
        </h2>

        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 px-4 h-11 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] focus-within:bg-[var(--color-surface)] focus-within:shadow-[var(--shadow-sm)] focus-within:border-[1.5px] focus-within:border-[var(--color-primary)] transition-all w-full sm:w-72">
            <Search size={18} className="text-[var(--color-text-muted)] shrink-0" />
            <input
              value={searchQuery}
              onChange={(e) => { setSearchQuery(e.target.value); setSearchParams(e.target.value ? { q: e.target.value } : {}, { replace: true }); }}
              placeholder="Search products..."
              className="w-full bg-transparent outline-none text-[15px]"
            />
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
                {cat}
              </button>
            ))}
          </div>

          {role === 'ADMIN' && (
            <button
              onClick={() => setShowForm(!showForm)}
              className="inline-flex items-center gap-1.5 min-h-[44px] rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-4 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
            >
              <Plus size={16} /> {showForm ? 'Cancel' : 'Add Product'}
            </button>
          )}
        </div>

        {/* Price filter */}
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] px-4 h-11">
            <span className="text-xs font-medium text-[var(--color-text-muted)] uppercase tracking-wide">Price</span>
            <input
              type="number"
              min="0"
              placeholder="Min"
              value={minPrice}
              onChange={(e) => setMinPrice(e.target.value)}
              className="w-24 bg-transparent outline-none text-sm border-l border-[var(--color-border)] pl-3"
            />
            <span className="text-[var(--color-text-muted)]">–</span>
            <input
              type="number"
              min="0"
              placeholder="Max"
              value={maxPrice}
              onChange={(e) => setMaxPrice(e.target.value)}
              className="w-24 bg-transparent outline-none text-sm"
            />
            {(minPrice !== '' || maxPrice !== '') && (
              <button
                onClick={() => { setMinPrice(''); setMaxPrice(''); }}
                className="text-xs text-[var(--color-primary)] font-semibold hover:underline"
              >
                Clear
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Sort + count */}
      <div className="flex items-center justify-between mb-[var(--space-5)]">
        <p className="text-sm text-[var(--color-text-muted)]">
          {sortedProducts.length} product{sortedProducts.length !== 1 ? 's' : ''}
        </p>
        <div className="relative">
          <button
            onClick={() => setSortOpen(!sortOpen)}
            className="inline-flex items-center gap-2 min-h-[44px] rounded-[var(--radius-md)] bg-white text-[var(--color-text-primary)] px-4 text-sm font-semibold shadow-[var(--shadow-xs)] hover:shadow-[var(--shadow-sm)] transition-shadow"
          >
            Sort: {SORT_OPTIONS.find((o) => o.key === sort)?.label}
            {sort === 'rating' && <Star size={14} className="text-[var(--color-accent)]" />}
            <ChevronDown size={16} className={`transition-transform ${sortOpen ? 'rotate-180' : ''}`} />
          </button>
          {sortOpen && (
            <div className="absolute right-0 mt-2 w-56 rounded-[var(--radius-lg)] bg-[var(--color-surface)] shadow-[var(--shadow-lg)] overflow-hidden z-[var(--z-dropdown)]">
              {SORT_OPTIONS.map((opt) => (
                <button
                  key={opt.key}
                  onClick={() => { setSort(opt.key); setSortOpen(false); }}
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