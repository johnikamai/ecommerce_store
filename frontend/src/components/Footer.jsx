import { Link } from 'react-router-dom';

const CATEGORY_LINKS = ['Food', 'Electronics', 'Fashion', 'Beauty'];

export default function Footer() {
  const year = new Date().getFullYear();

  return (
    <footer className="mt-[var(--space-11)] bg-[var(--color-card-bg-tint)]">
      <div className="container-x py-[var(--space-9)] grid grid-cols-2 md:grid-cols-4 gap-8">
        <div className="col-span-2 md:col-span-1">
          <div className="flex items-center gap-2 mb-3">
            <span className="w-8 h-8 rounded-[var(--radius-md)] bg-gradient-premium flex items-center justify-center text-white font-bold">
              S
            </span>
            <span className="font-[family-name:var(--font-heading)] text-lg font-bold">ShopEase</span>
          </div>
          <p className="text-sm text-[var(--color-text-secondary)]">
            A calm, friendly place to shop. Earn loyalty points, get restock alerts, and discover what's bought together.
          </p>
        </div>

        <div>
          <h4 className="font-[family-name:var(--font-heading)] text-sm font-semibold mb-3">Shop</h4>
          <ul className="space-y-2 text-sm text-[var(--color-text-secondary)]">
            {CATEGORY_LINKS.map((c) => (
              <li key={c}>
                <Link to={`/products?category=${encodeURIComponent(c)}`} className="hover:text-[var(--color-primary)] transition-colors">
                  {c}
                </Link>
              </li>
            ))}
          </ul>
        </div>

        <div>
          <h4 className="font-[family-name:var(--font-heading)] text-sm font-semibold mb-3">Account</h4>
          <ul className="space-y-2 text-sm text-[var(--color-text-secondary)]">
            <li><Link to="/orders" className="hover:text-[var(--color-primary)] transition-colors">My Orders</Link></li>
            <li><Link to="/wishlist" className="hover:text-[var(--color-primary)] transition-colors">Wishlist</Link></li>
            <li><Link to="/cart" className="hover:text-[var(--color-primary)] transition-colors">Cart</Link></li>
          </ul>
        </div>

        <div>
          <h4 className="font-[family-name:var(--font-heading)] text-sm font-semibold mb-3">Perks</h4>
          <ul className="space-y-2 text-sm text-[var(--color-text-secondary)]">
            <li>1 point per ₹100 spent</li>
            <li>GOLD tier = 5% off</li>
            <li>Back-in-stock alerts</li>
            <li>+500 pts per successful referral</li>
          </ul>
        </div>
      </div>

      <div className="border-t border-[var(--color-border)]">
        <div className="container-x py-4 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-[var(--color-text-muted)]">
          <p>© {year} ShopEase. Demo only — not a real store.</p>
          <p>Built with the ShopEase design system.</p>
        </div>
      </div>
    </footer>
  );
}