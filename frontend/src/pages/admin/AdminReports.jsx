import { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';

function Kpi({ label, value, accent }) {
  return (
    <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
      <p className="text-xs text-[var(--color-text-muted)] mb-1">{label}</p>
      <p className={`font-[family-name:var(--font-heading)] text-2xl font-bold ${accent || 'text-[var(--color-primary)]'}`}>{value}</p>
    </div>
  );
}

export default function AdminReports() {
  const [sales, setSales] = useState([]);
  const [topProducts, setTopProducts] = useState([]);
  const [activity, setActivity] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      axiosClient.get('/admin/reports/sales'),
      axiosClient.get('/admin/reports/top-products'),
      axiosClient.get('/admin/reports/customer-activity'),
    ])
      .then(([s, t, a]) => {
        setSales(s.data);
        setTopProducts(t.data);
        setActivity(a.data);
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">Loading reports...</p>;

  const salesArray = Array.isArray(sales) ? sales : (sales?.items || []);

  const kpis = Array.isArray(sales)
    ? [
        { label: 'Total Revenue', value: `₹${salesArray.reduce((s, r) => s + Number(r.totalAmount || 0), 0).toLocaleString('en-IN')}` },
        { label: 'Total Orders', value: salesArray.length },
        { label: 'Avg Order Value', value: salesArray.length ? `₹${Math.round(salesArray.reduce((s, r) => s + Number(r.totalAmount || 0), 0) / salesArray.length).toLocaleString('en-IN')}` : '—' },
      ]
    : [
        { label: 'Total Revenue', value: `₹${Number(sales?.totalRevenue || 0).toLocaleString('en-IN')}` },
        { label: 'Total Orders', value: sales?.totalOrders ?? sales?.orderCount ?? '—' },
        { label: 'Avg Order Value', value: `₹${Number(sales?.averageOrderValue || 0).toLocaleString('en-IN')}` },
      ];

  return (
    <div>
      <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold mb-6">Reports</h2>

      <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-3">Sales Overview</h3>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        {kpis.map((k) => <Kpi key={k.label} {...k} />)}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-6">
          <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-4">Top Products</h3>
          {topProducts.length === 0 ? (
            <p className="text-sm text-[var(--color-text-muted)]">No sales yet.</p>
          ) : (
            <ul className="divide-y divide-[var(--color-border)] text-sm">
              {topProducts.map((p, i) => (
                <li key={p.productId ?? i} className="flex items-center justify-between py-2.5">
                  <span className="flex items-center gap-3 min-w-0">
                    <span className="w-6 h-6 shrink-0 rounded-full bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)] text-xs font-bold flex items-center justify-center">{i + 1}</span>
                    <span className="font-medium truncate">{p.name || p.productName}</span>
                  </span>
                  <span className="shrink-0 text-[var(--color-text-secondary)]">
                    {p.quantitySold ?? p.unitsSold} sold · ₹{Number(p.revenue || p.totalRevenue || 0).toLocaleString('en-IN')}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-6">
          <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-4">Customer Activity</h3>
          {activity.length === 0 ? (
            <p className="text-sm text-[var(--color-text-muted)]">No customer activity yet.</p>
          ) : (
            <ul className="divide-y divide-[var(--color-border)] text-sm">
              {activity.map((c) => (
                <li key={c.customerId ?? c.id} className="flex items-center justify-between py-2.5">
                  <span className="font-medium min-w-0 truncate pr-3">{c.name || c.customerName}</span>
                  <span className="shrink-0 text-[var(--color-text-secondary)]">
                    {c.orderCount} orders · ₹{Number(c.totalSpend || 0).toLocaleString('en-IN')}
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