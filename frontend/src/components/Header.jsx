import { useState } from 'react';
import { Link, useNavigate, useSearchParams, useLocation } from 'react-router-dom';
import { Search, Heart, ShoppingCart, LogOut, Menu, X } from 'lucide-react';
import { useCart } from '../context/CartContext';

export default function Header() {
  const token = localStorage.getItem('token');
  const role = localStorage.getItem('role');
  const { totalItems } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const isLoginPage = location.pathname === '/' || location.pathname === '/login';
  const [searchParams] = useSearchParams();
  const [q, setQ] = useState(searchParams.get('q') || '');
  const [menuOpen, setMenuOpen] = useState(false);

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

  const navLinks = (
    <>
      <Link to="/products" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">Shop</Link>
      {(role === 'ADMIN' || role === 'STAFF') && (
        <Link to="/admin" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">Dashboard</Link>
      )}
      <Link to="/account" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">My Account</Link>
      <Link to="/orders" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">My Orders</Link>
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

        {/* Desktop nav */}
        <nav className="hidden lg:flex items-center gap-6">
          {token ? navLinks : (
            !isLoginPage && (
              <Link to="/" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">Login</Link>
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
                placeholder="Search products..."
                className="w-full bg-transparent outline-none text-[15px] text-[var(--color-text-primary)] placeholder:text-[var(--color-text-muted)]"
              />
              {q && (
                <button type="button" onClick={() => setQ('')} aria-label="Clear search">
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
              aria-label="Wishlist"
              className="w-10 h-10 rounded-full flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-card-bg-tint)] hover:text-[var(--color-secondary)] transition-colors"
            >
              <Heart size={20} />
            </Link>
            <Link
              to="/cart"
              aria-label="Cart"
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
                title="My Account"
                className="w-10 h-10 rounded-full bg-[var(--color-card-bg-tint)] flex items-center justify-center text-[var(--color-primary)] font-[family-name:var(--font-heading)] font-bold hover:ring-2 hover:ring-[var(--color-primary)] transition-all"
              >
                {(localStorage.getItem('customerName') || localStorage.getItem('username') || 'U').charAt(0).toUpperCase()}
              </Link>
            </div>
            <button
              onClick={handleLogout}
              aria-label="Logout"
              className="w-10 h-10 rounded-full flex items-center justify-center text-[var(--color-text-secondary)] hover:bg-[var(--color-error-bg)] hover:text-[var(--color-error)] transition-colors"
            >
              <LogOut size={20} />
            </button>
          </div>
        )}

        {/* Mobile menu toggle */}
        {token && (
          <button
            onClick={() => setMenuOpen(!menuOpen)}
            aria-label="Menu"
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
              placeholder="Search products..."
              className="w-full bg-transparent outline-none text-[15px]"
            />
          </form>
          {navLinks}
        </div>
      )}
    </header>
  );
}