import { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';

function Kpi({ label, value, accent }) {
  return (
    <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
      <p className="text-xs text-[var(--color-text-muted)] mb-1">{label}</p>
      <p className={`font-[family-name:var(--font-heading)] text-2xl font-bold ${accent || 'text-[var(--color-primary)]'}`}>{value}</p>
    </div>
  );
}

export default function AdminReports() {
  const { t, formatCurrency, formatNumber } = useLanguage();
  const [sales, setSales] = useState(null);
  const [topProducts, setTopProducts] = useState([]);
  const [activity, setActivity] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([
      axiosClient.get('/admin/reports/sales'),
      axiosClient.get('/admin/reports/top-products'),
      axiosClient.get('/admin/reports/customer-activity'),
    ])
      .then(([s, t, a]) => {
        setSales(s.data);
        setTopProducts(t.data || []);
        setActivity(a.data || []);
        setError('');
      })
      .catch((err) => setError(err.response?.data || t('admin.reports.loadFailed')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">{t('admin.reports.loading')}</p>;

  if (error) {
    return (
      <div>
        <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold mb-6">{t('admin.nav.reports')}</h2>
        <p className="py-12 text-center text-[var(--color-error)]">{error}</p>
      </div>
    );
  }

  // /admin/reports/sales returns a summary object. Field names must match the
  // backend exactly: totalRevenue, totalOrders, avgOrderValue (not
  // "averageOrderValue"), pendingOrders, completedOrders, cancelledOrders.
  const kpis = [
    { label: t('admin.reports.totalRevenue'), value: formatCurrency(sales?.totalRevenue || 0) },
    { label: t('admin.reports.totalOrders'), value: formatNumber(sales?.totalOrders ?? 0) },
    { label: t('admin.reports.avgOrderValue'), value: formatCurrency(sales?.avgOrderValue || 0) },
    { label: t('admin.reports.pending'), value: formatNumber(sales?.pendingOrders ?? 0), accent: 'text-[var(--color-warning)]' },
    { label: t('admin.reports.completed'), value: formatNumber(sales?.completedOrders ?? 0), accent: 'text-[var(--color-success)]' },
    { label: t('admin.reports.cancelled'), value: formatNumber(sales?.cancelledOrders ?? 0), accent: 'text-[var(--color-error)]' },
  ];

  return (
    <div>
      <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold mb-6">{t('admin.nav.reports')}</h2>

      <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-3">{t('admin.reports.salesOverview')}</h3>
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4 mb-8">
        {kpis.map((k) => <Kpi key={k.label} {...k} />)}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-6">
          <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-4">{t('admin.reports.topProducts')}</h3>
          {topProducts.length === 0 ? (
            <p className="text-sm text-[var(--color-text-muted)]">{t('admin.reports.noSales')}</p>
          ) : (
            <ul className="divide-y divide-[var(--color-border)] text-sm">
              {topProducts.map((p, i) => (
                <li key={p.productId ?? i} className="flex items-center justify-between py-2.5">
                  <span className="flex items-center gap-3 min-w-0">
                    <span className="w-6 h-6 shrink-0 rounded-full bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)] text-xs font-bold flex items-center justify-center">{i + 1}</span>
                    <span className="font-medium truncate">{p.name || p.productName}</span>
                  </span>
                  <span className="shrink-0 text-[var(--color-text-secondary)]">
                    {t('admin.reports.sold', {
                      count: formatNumber(p.quantitySold ?? p.unitsSold),
                      amount: formatCurrency(p.revenue || p.totalRevenue || 0),
                    })}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-6">
          <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-4">{t('admin.reports.customerActivity')}</h3>
          {activity.length === 0 ? (
            <p className="text-sm text-[var(--color-text-muted)]">{t('admin.reports.noActivity')}</p>
          ) : (
            <ul className="divide-y divide-[var(--color-border)] text-sm">
              {activity.map((c) => (
                <li key={c.customerId ?? c.id} className="flex items-center justify-between py-2.5">
                  <span className="font-medium min-w-0 truncate pr-3">{c.name || c.customerName}</span>
                  <span className="shrink-0 text-[var(--color-text-secondary)]">
                    {t('admin.reports.orderStats', {
                      count: formatNumber(c.orderCount),
                      amount: formatCurrency(c.totalSpend || 0),
                    })}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}