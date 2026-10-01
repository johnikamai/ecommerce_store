import { NavLink, Outlet } from 'react-router-dom';
import {
  LayoutDashboard, Package, ShoppingBag, Users, Tags, TicketPercent,
  CreditCard, Star, BarChart3, ArrowLeft,
} from 'lucide-react';

const NAV = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/products', label: 'Products', icon: Package },
  { to: '/admin/orders', label: 'Orders', icon: ShoppingBag },
  { to: '/admin/customers', label: 'Customers', icon: Users },
  { to: '/admin/categories', label: 'Categories', icon: Tags },
  { to: '/admin/coupons', label: 'Coupons', icon: TicketPercent },
  { to: '/admin/payments', label: 'Payments', icon: CreditCard },
  { to: '/admin/reviews', label: 'Reviews', icon: Star },
  { to: '/admin/reports', label: 'Reports', icon: BarChart3 },
];

export default function AdminLayout() {
  return (
    <div className="container-x py-[var(--space-8)]">
      <div className="flex flex-col lg:flex-row gap-6">
        <aside className="lg:w-60 shrink-0">
          <div className="lg:sticky lg:top-[100px] rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-4">
            <nav className="flex lg:flex-col gap-1 overflow-x-auto">
              {NAV.map((item) => (
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
                  {item.label}
                </NavLink>
              ))}
            </nav>
            <div className="border-t border-[var(--color-border)] mt-4 pt-4">
              <NavLink
                to="/products"
                className="flex items-center gap-2.5 px-3 py-2 rounded-[var(--radius-md)] text-sm font-medium text-[var(--color-text-secondary)] hover:bg-[var(--color-card-bg-tint)] transition-colors"
              >
                <ArrowLeft size={17} />
                Back to shop
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