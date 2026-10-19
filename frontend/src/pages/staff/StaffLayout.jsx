import { NavLink, Outlet } from 'react-router-dom';
import { LayoutDashboard, ListChecks, CheckCircle2, User, ArrowLeft } from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';

const NAV = [
  { to: '/staff/dashboard', labelKey: 'staff.portal.dashboard', icon: LayoutDashboard },
  { to: '/staff/tasks', labelKey: 'staff.portal.tasks', icon: ListChecks },
  { to: '/staff/approvals', labelKey: 'staff.portal.approvals', icon: CheckCircle2 },
  { to: '/staff/profile', labelKey: 'staff.portal.profile', icon: User },
];

export default function StaffLayout() {
  const { t } = useLanguage();

  return (
    <div className="container-x py-[var(--space-8)]">
      <div className="flex flex-col lg:flex-row gap-6">
        <aside className="lg:w-60 shrink-0">
          <div className="lg:sticky lg:top-[100px] rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-4">
            <p className="px-3 pb-2 text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">
              {t('staff.portal.section')}
            </p>
            <nav className="flex lg:flex-col gap-1 overflow-x-auto">
              {NAV.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
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
                {t('staff.portal.backToShop')}
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
