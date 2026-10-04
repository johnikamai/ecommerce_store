import { useState, useEffect, useRef } from 'react';
import { Link, useNavigate, useSearchParams, useLocation } from 'react-router-dom';
import { Search, Heart, ShoppingCart, LogOut, Menu, X, Scale, ChevronDown } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { useCompare } from '../context/CompareContext';
import { useLanguage } from '../context/LanguageContext';
import axiosClient from '../api/axiosClient';
import ThemeSwitcher from './ThemeSwitcher';
import LanguageSwitcher from './LanguageSwitcher';
import ProductImage from './ProductImage';

/** Thumbnails shown per category tile in the mega menu. */
const MEGA_TILES_PER_CATEGORY = 3;

export default function Header() {
  const token = localStorage.getItem('token');
  const role = localStorage.getItem('role');
  const { totalItems } = useCart();
  const { count: compareCount } = useCompare();
  const { t } = useLanguage();
  const navigate = useNavigate();
  const location = useLocation();
  const isLoginPage = location.pathname === '/' || location.pathname === '/login';
  const [searchParams] = useSearchParams();
  const [q, setQ] = useState(searchParams.get('q') || '');
  const [menuOpen, setMenuOpen] = useState(false);
  const [megaOpen, setMegaOpen] = useState(false);
  const [megaGroups, setMegaGroups] = useState(null);
  const megaTimer = useRef(null);

  const submitSearch = (e) => {
    e.preventDefault();
    navigate(`/products${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ''}`);
    setMenuOpen(false);
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('role');
    localStorage.removeItem('username');
    localStorage.removeItem('customerId');
    localStorage.removeItem('customerName');
    navigate('/');
  };

  // The mega menu needs a product photo per category, and the catalogue is the
  // only endpoint that carries both a category and an image in one payload. It
  // is fetched once, on first open, rather than on every page load - 600-odd
  // products is a heavy response to pull for a menu nobody has hovered yet.
  useEffect(() => {
    if (!megaOpen || megaGroups) return;
    let cancelled = false;
    axiosClient
      .get('/products/catalog')
      .then((res) => {
        if (cancelled) return;
        const products = Array.isArray(res.data) ? res.data : res.data?.products || [];
        const byCategory = new Map();
        for (const p of products) {
          if (!p.category) continue;
          if (!byCategory.has(p.category)) byCategory.set(p.category, []);
          byCategory.get(p.category).push(p);
        }
        const groups = [...byCategory.entries()]
          .map(([category, items]) => ({
            category,
            // Best sellers first: what people actually bought is a better
            // showcase for a category than whatever happens to be row one.
            items: items
              .slice()
              .sort((a, b) => (b.unitsSold || 0) - (a.unitsSold || 0))
              .slice(0, MEGA_TILES_PER_CATEGORY),
          }))
          .sort((a, b) => b.items.length - a.items.length);
        setMegaGroups(groups);
      })
      .catch(() => {
        // A failed menu must not take the header down with it; the category
        // links below still work without photos.
        if (!cancelled) setMegaGroups([]);
      });
    return () => {
      cancelled = true;
    };
  }, [megaOpen, megaGroups]);

  // Small delay so the panel does not flicker shut while the pointer crosses
  // the gap between the trigger and the dropdown.
  const openMega = () => {
    clearTimeout(megaTimer.current);
    setMegaOpen(true);
  };
  const closeMegaSoon = () => {
    clearTimeout(megaTimer.current);
    megaTimer.current = setTimeout(() => setMegaOpen(false), 180);
  };
  const goToCategory = (category) => {
    setMegaOpen(false);
    navigate(`/products?category=${encodeURIComponent(category)}`);
  };

  // "Shop" is deliberately not in this list: on desktop it is the mega menu
  // trigger instead, and the mobile panel renders it separately.
  const navLinks = (
    <>
      {(role === 'ADMIN' || role === 'STAFF') && (
        <Link to="/admin" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">{t('nav.dashboard')}</Link>
      )}
      <Link to="/account" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">{t('nav.account')}</Link>
      <Link to="/orders" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">{t('nav.orders')}</Link>
    </>
  );

  return (
    <header className="sticky top-0 z-[var(--z-sticky-header)] bg-[var(--color-surface)] shadow-[var(--shadow-xs)]">
      <div className="container-x flex items-center gap-6 h-[76px]">
        {/* Logo */}
        <Link to="/products" className="flex items-center gap-2 shrink-0">
          <span className="w-9 h-9 rounded-[var(--radius-lg)] bg-gradient-premium flex items-center justify-center text-white font-[family-name:var(--font-heading)] font-bold">
            S
          </span>
          <span className="font-[family-name:var(--font-heading)] text-xl font-bold text-[var(--color-text-primary)]">
            ShopEase
          </span>
        </Link>

        {/* Desktop nav. "Shop" opens the mega menu. */}
        <nav className="hidden lg:flex items-center gap-6">
          {token ? (
            <>
              <div
                className="relative"
                onMouseEnter={openMega}
                onMouseLeave={closeMegaSoon}
              >
                <button
                  onClick={() => setMegaOpen((v) => !v)}
                  aria-expanded={megaOpen}
                  className="flex items-center gap-1 text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors"
                >
                  {t('nav.shop')}
                  <ChevronDown
                    size={15}
                    className={'transition-transform ' + (megaOpen ? 'rotate-180' : '')}
                  />
                </button>

                {megaOpen && (
                  <div className="absolute left-0 top-full pt-3 w-[min(92vw,1000px)] z-50">
                    <div className="bg-[var(--color-surface)] rounded-[var(--radius-lg)] shadow-[var(--shadow-lg)] border-[var(--color-border)] border p-6">
                      {megaGroups === null ? (
                        <p className="text-sm text-[var(--color-text-muted)] py-8 text-center">
                          Loading categories…
                        </p>
                      ) : megaGroups.length === 0 ? (
                        <p className="text-sm text-[var(--color-text-muted)] py-8 text-center">
                          Categories are unavailable right now.
                        </p>
                      ) : (
                        <div className="grid grid-cols-4 gap-x-6 gap-y-5">
                          {megaGroups.map((group) => (
                            <div key={group.category}>
                              <Link
                                to={`/products?category=${encodeURIComponent(group.category)}`}
                                onClick={() => setMegaOpen(false)}
                                className="font-[family-name:var(--font-heading)] font-semibold text-sm text-[var(--color-text-primary)] hover:text-[var(--color-primary)] transition-colors"
                              >
                                {group.category}
                              </Link>
                              <div className="mt-2 space-y-1.5">
                                {group.items.map((p) => (
                                  <Link
                                    key={p.id}
                                    to={`/product/${p.id}`}
                                    onClick={() => setMegaOpen(false)}
                                    className="flex items-center gap-2 group/item"
                                  >
                                    <ProductImage
                                      product={p}
                                      className="w-9 h-9 rounded-[var(--radius-sm)] object-cover shrink-0 group-hover/item:scale-105 transition-transform"
                                    />
                                    <span className="text-xs text-[var(--color-text-secondary)] group-hover/item:text-[var(--color-primary)] line-clamp-2 transition-colors">
                                      {p.name}
                                    </span>
                                  </Link>
                                ))}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}

                      {/* Promo strip */}
                      <div className="mt-6 pt-5 border-t-[var(--color-border)] grid grid-cols-3 gap-4">
                        <Link
                          to="/products?sort=unitsSold"
                          onClick={() => setMegaOpen(false)}
                          className="p-3 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] hover:bg-[var(--color-primary)] hover:text-white transition-colors group"
                        >
                          <p className="text-xs font-semibold">Best sellers</p>
                          <p className="text-[11px] opacity-70 mt-0.5">What everyone is buying</p>
                        </Link>
                        <Link
                          to="/products?minRating=4"
                          onClick={() => setMegaOpen(false)}
                          className="p-3 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] hover:bg-[var(--color-primary)] hover:text-white transition-colors"
                        >
                          <p className="text-xs font-semibold">Top rated</p>
                          <p className="text-[11px] opacity-70 mt-0.5">4 stars and up</p>
                        </Link>
                        <Link
                          to="/products?sustainable=true"
                          onClick={() => setMegaOpen(false)}
                          className="p-3 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] hover:bg-[var(--color-primary)] hover:text-white transition-colors"
                        >
                          <p className="text-xs font-semibold">Eco picks</p>
                          <p className="text-[11px] opacity-70 mt-0.5">Highest sustainability scores</p>
                        </Link>
                      </div>
                    </div>
                  </div>
                )}
              </div>
              {navLinks}
            </>
          ) : (
            !isLoginPage && (
              <Link to="/" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">{t('nav.login')}</Link>
            )
          )}
        </nav>

        {/* Search */}
        {token && (
          <form onSubmit={submitSearch} className="hidden md:flex flex-1 max-w-[480px] ml-auto">
            <div className="flex-1 flex items-center gap-2 px-4 h-11 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] focus-within:bg-[var(--color-surface)] focus-within:shadow-[var(--shadow-sm)] focus-within:border-[1.5px] focus-within:border-[var(--color-primary)] transition-all">
              <Search size={18} className="text-[var(--color-text-muted)] shrink-0" />
              <input
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder={t('nav.searchPlaceholder')}
                className="w-full bg-transparent outline-none text-[15px] text-[var(--color-text-primary)] placeholder:text-[var(--color-text-muted)]"
              />
              {q && (
                <button type="button" onClick={() => setQ('')} aria-label={t('nav.clearSearch')}>
                  <X size={16} className="text-[var(--color-text-muted)]" />
                </button>
              )}
            </div>
          </form>
        )}

        {/* Actions */}
        {token && (
          <div className="flex items-center gap-2 ml-auto md:ml-0">
            <Link
              to="/wishlist"
              aria-label={t('nav.wishlist')}
              className="w-10 h-10 rounded-full flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-card-bg-tint)] hover:text-[var(--color-secondary)] transition-colors"
            >
              <Heart size={20} />
            </Link>
            <Link
              to="/compare"
              aria-label={`Compare products${compareCount ? ` (${compareCount} selected)` : ''}`}
              title="Compare products"
              className="relative w-10 h-10 rounded-full flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-card-bg-tint)] hover:text-[var(--color-primary)] transition-colors"
            >
              <Scale size={20} />
              {compareCount > 0 && (
                <span className="absolute -top-0.5 -right-0.5 bg-[var(--color-primary)] text-white text-xs font-bold rounded-full min-w-5 h-5 px-1 flex items-center justify-center">
                  {compareCount}
                </span>
              )}
            </Link>
            <Link
              to="/cart"
              aria-label={t('nav.cart')}
              className="relative w-10 h-10 rounded-full flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-card-bg-tint)] hover:text-[var(--color-primary)] transition-colors"
            >
              <ShoppingCart size={20} />
              {totalItems > 0 && (
                <span className="absolute -top-0.5 -right-0.5 bg-[var(--color-error)] text-white text-xs font-bold rounded-full min-w-5 h-5 px-1 flex items-center justify-center">
                  {totalItems}
                </span>
              )}
            </Link>
            <div className="hidden sm:flex items-center gap-2 pl-2">
              <Link
                to="/account"
                title={t('nav.account')}
                className="w-10 h-10 rounded-full bg-[var(--color-card-bg-tint)] flex items-center justify-center text-[var(--color-primary)] font-[family-name:var(--font-heading)] font-bold hover:ring-2 hover:ring-[var(--color-primary)] transition-all"
              >
                {(localStorage.getItem('customerName') || localStorage.getItem('username') || 'U').charAt(0).toUpperCase()}
              </Link>
            </div>
            <button
              onClick={handleLogout}
              aria-label={t('nav.logout')}
              className="w-10 h-10 rounded-full flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-error-bg)] hover:text-[var(--color-error)] transition-colors"
            >
              <LogOut size={20} />
            </button>
          </div>
        )}

        {/* Theme and language pickers sit outside the login check so guests can
            set their preferences too - they are display settings, not account
            features. */}
        <div className={`flex items-center gap-2 ${token ? '' : 'ml-auto md:ml-0'}`}>
          <LanguageSwitcher />
          <ThemeSwitcher />
        </div>

        {/* Mobile menu toggle */}
        {token && (
          <button
            onClick={() => setMenuOpen(!menuOpen)}
            aria-label={t('nav.menu')}
            className="lg:hidden w-10 h-10 rounded-full flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-card-bg-tint)]"
          >
            {menuOpen ? <X size={22} /> : <Menu size={22} />}
          </button>
        )}
      </div>

      {/* Mobile panel */}
      {token && menuOpen && (
        <div className="lg:hidden border-t border-[var(--color-border)] bg-[var(--color-surface)] px-6 py-4 flex flex-col gap-4 shadow-[var(--shadow-lg)]">
          <form onSubmit={submitSearch} className="flex items-center gap-2 px-4 h-11 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)]">
            <Search size={18} className="text-[var(--color-text-muted)]" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder={t('nav.searchPlaceholder')}
              className="w-full bg-transparent outline-none text-[15px]"
            />
          </form>
          {navLinks}
          <Link to="/products" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">{t('nav.shop')}</Link>
          {megaGroups?.map((group) => (
            <Link
              key={group.category}
              to={`/products?category=${encodeURIComponent(group.category)}`}
              onClick={() => setMenuOpen(false)}
              className="text-sm text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors pl-3"
            >
              {group.category}
            </Link>
          ))}
        </div>
      )}
    </header>
  );
}