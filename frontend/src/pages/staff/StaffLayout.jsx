import { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { LayoutDashboard, ListChecks, CheckCircle2, User, ArrowLeft } from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';

const NAV = [
  { to: '/staff/dashboard', labelKey: 'staff.portal.dashboard', icon: LayoutDashboard },
  { permission: 'TASK_VIEW', to: '/staff/tasks', labelKey: 'staff.portal.tasks', icon: ListChecks },
  { permission: 'PRICE_CHANGE_REQUEST', to: '/staff/approvals', labelKey: 'staff.portal.approvals', icon: CheckCircle2 },
  { to: '/staff/profile', labelKey: 'staff.portal.profile', icon: User },
];

export default function StaffLayout() {
  const { t } = useLanguage();
  const [permissions, setPermissions] = useState(null);
  const location = useLocation();
  useEffect(() => {
    let active=true;
    axiosClient.get('/staff/me').then(({ data }) => { if(active)setPermissions(data.permissions || []); }).catch(()=>{if(active)setPermissions([]);});

  return ()=>{active=false;};
  }, [location.pathname]);
  const permitted = item => !item.permission || permissions?.includes(item.permission) || localStorage.getItem('role')==='ADMIN';
  const current = NAV.find(item => item.to===location.pathname);

  return (
    <div className="container-x py-[var(--space-8)]">
      <div className="flex flex-col lg:flex-row gap-6">
        <aside className="lg:w-60 shrink-0">
          <div className="lg:sticky lg:top-[100px] rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-4">
            <p className="px-3 pb-2 text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">
              {t('staff.portal.section')}
            </p>
            <nav className="flex lg:flex-col gap-1 overflow-x-auto">
              {NAV.filter(permitted).map((item) => (
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
              {(permissions?.includes('PRODUCT_CREATE') || permissions?.includes('PRODUCT_VIEW')) && <NavLink to="/products">Product catalogue</NavLink>}
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
          {permissions===null ? <p>Loading permissions...</p> : current && !permitted(current) ? <p role="alert">You do not have permission to access this module.</p> : <Outlet />}
        </main>
      </div>
    </div>
  );
}
