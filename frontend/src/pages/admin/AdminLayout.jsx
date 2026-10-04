import { NavLink, Outlet } from 'react-router-dom';
import { useState, useEffect } from 'react';
import {
  LayoutDashboard, Package, ShoppingBag, Users, Tags, TicketPercent,
  CreditCard, Star, BarChart3, ArrowLeft,
} from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';

/**
 * adminOnly items are hidden from STAFF rather than left visible-but-broken:
 * staff cannot issue refunds, edit discounts or change prices, so offering those
 * links only produces a 403. The server enforces this regardless - hiding a link
 * is presentation, not security.
 */
const NAV = [
  { to: '/admin', labelKey: 'admin.nav.dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/products', labelKey: 'admin.nav.products', icon: Package },
  { to: '/admin/orders', labelKey: 'admin.nav.orders', icon: ShoppingBag },
  { to: '/admin/customers', labelKey: 'admin.nav.customers', icon: Users },
  { to: '/admin/categories', labelKey: 'admin.nav.categories', icon: Tags },
  { to: '/admin/coupons', labelKey: 'admin.nav.coupons', icon: TicketPercent, adminOnly: true },
  { to: '/admin/payments', labelKey: 'admin.nav.payments', icon: CreditCard, adminOnly: true },
  { to: '/admin/reviews', labelKey: 'admin.nav.reviews', icon: Star },
  { to: '/admin/reports', labelKey: 'admin.nav.reports', icon: BarChart3, adminOnly: true },
];

export default function AdminLayout() {
  const { t } = useLanguage();
  const [role, setRole] = useState(() => localStorage.getItem('role'));

  useEffect(() => {
    const sync = () => setRole(localStorage.getItem('role'));
    window.addEventListener('storage', sync);
    return () => window.removeEventListener('storage', sync);
  }, []);

  const visibleNav = NAV.filter((item) => !item.adminOnly || role === 'ADMIN');

  return (
    <div className="container-x py-[var(--space-8)]">
      <div className="flex flex-col lg:flex-row gap-6">
        <aside className="lg:w-60 shrink-0">
          <div className="lg:sticky lg:top-[100px] rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-4">
            <nav className="flex lg:flex-col gap-1 overflow-x-auto">
              {visibleNav.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `flex items-center gap-2.5 shrink-0 px-3 py-2 rounded-[var(--radius-md)] text-sm font-medium transition-colors ${
                      isActive
                        ? 'bg-[var(--color-primary)] text-white'
                        : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-card-bg-tint)]'
                    }`
                  }
                >
                  <item.icon size={17} />
                  {t(item.labelKey)}
                </NavLink>
              ))}
            </nav>
            <div className="border-t border-[var(--color-border)] mt-4 pt-4">
              <NavLink
                to="/products"
                className="flex items-center gap-2.5 px-3 py-2 rounded-[var(--radius-md)] text-sm font-medium text-[var(--color-text-secondary)] hover:bg-[var(--color-card-bg-tint)] transition-colors"
              >
                <ArrowLeft size={17} />
                {t('admin.nav.backToShop')}
              </NavLink>
            </div>
          </div>
        </aside>
        <main className="flex-1 min-w-0">
          <Outlet />
        </main>
      </div>
    </div>
  );
}