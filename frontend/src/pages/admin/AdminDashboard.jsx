import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import axiosClient from '../../api/axiosClient';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell,
} from 'recharts';

const STATUS_COLORS = {
  PLACED: '#7FB6E8',
  SHIPPED: '#FFC98B',
  DELIVERED: '#4CAF7D',
  CANCELLED: '#E0607A',
};

function Card({ label, value, color }) {
  return (
    <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
      <p className="text-xs text-[var(--color-text-muted)] mb-1">{label}</p>
      <p className="font-[family-name:var(--font-heading)] text-2xl font-bold" style={{ color }}>
        {value}
      </p>
    </div>
  );
}

export default function AdminDashboard() {
  const [summary, setSummary] = useState(null);
  const [salesReport, setSalesReport] = useState({});
  const [products, setProducts] = useState([]);
  const [alerts, setAlerts] = useState({ unread: 0, alerts: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [summaryRes, reportRes, productRes, alertRes] = await Promise.all([
          axiosClient.get('/admin/dashboard'),
          axiosClient.get('/admin/sales-report'),
          axiosClient.get('/products'),
          axiosClient.get('/admin/alerts'),
        ]);
        setSummary(summaryRes.data);
        setSalesReport(reportRes.data);
        setProducts(productRes.data);
        setAlerts(alertRes.data || { unread: 0, alerts: [] });
      } catch (err) {
        setError('Failed to load dashboard');
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const markAllRead = async () => {
    try {
      await axiosClient.put('/admin/alerts/read-all');
      setAlerts((prev) => ({
        unread: 0,
        alerts: (prev.alerts || []).map((a) => ({ ...a, isRead: true })),
      }));
    } catch {
      setError('Failed to mark alerts as read');
    }
  };

  const markRead = async (id) => {
    try {
      await axiosClient.put(`/admin/alerts/${id}/read`, { read: true });
      setAlerts((prev) => ({
        unread: Math.max(0, (prev.unread || 0) - 1),
        alerts: (prev.alerts || []).map((a) => (a.id === id ? { ...a, isRead: true } : a)),
      }));
    } catch {
      setError('Failed to mark alert as read');
    }
  };

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">Loading dashboard...</p>;
  if (error) return <p className="py-12 text-center text-[var(--color-error)]">{error}</p>;

  const statusChartData = Object.entries(salesReport).map(([status, count]) => ({ status, count }));
  // Use the server's own low-stock flag so the per-product reorder level applies.
  const lowStock = products.filter((p) => p.lowStock).sort((a, b) => a.stockQuantity - b.stockQuantity).slice(0, 8);

  const statCards = [
    { label: 'Total Revenue', value: `₹${summary.totalRevenue.toLocaleString('en-IN')}`, color: 'var(--color-primary)' },
    { label: 'Total Orders', value: summary.totalOrders, color: 'var(--color-info)' },
    { label: 'Total Products', value: summary.totalProducts, color: 'var(--color-success)' },
    { label: 'Total Customers', value: summary.totalCustomers, color: 'var(--color-secondary)' },
    { label: 'Low Stock Alerts', value: summary.lowStockAlerts, color: 'var(--color-warning)' },
  ];

  const ALERT_STYLES = {
    OUT_OF_STOCK: 'text-[var(--color-error)] bg-[var(--color-error-bg)]',
    LOW_STOCK: 'text-[var(--color-warning)] bg-[var(--color-warning-bg)]',
    RESTOCKED: 'text-[var(--color-success)] bg-[var(--color-success-bg)]',
  };

  return (
    <div>
      <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold mb-6">Dashboard</h2>

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4 mb-8">
        {statCards.map((card) => <Card key={card.label} {...card} />)}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 flex flex-col gap-6">
          <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-6">
          <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-4">Orders by Status</h3>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={statusChartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
              <XAxis dataKey="status" tick={{ fontSize: 12 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
              <Tooltip />
              <Bar dataKey="count" radius={[8, 8, 0, 0]}>
                {statusChartData.map((entry) => (
                  <Cell key={entry.status} fill={STATUS_COLORS[entry.status] || '#7C6AE8'} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
          </div>

          <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold flex items-center gap-2">
                Stock Alerts
                {alerts.unread > 0 && (
                  <span className="rounded-full bg-[var(--color-error-bg)] text-[var(--color-error)] text-xs font-bold px-2 py-0.5">
                    {alerts.unread} new
                  </span>
                )}
              </h3>
              {alerts.unread > 0 && (
                <button
                  type="button"
                  onClick={markAllRead}
                  className="text-xs font-semibold text-[var(--color-primary)] hover:underline"
                >
                  Mark all read
                </button>
              )}
            </div>
            {alerts.alerts.length === 0 ? (
              <p className="text-sm text-[var(--color-text-muted)]">No stock alerts yet.</p>
            ) : (
              <ul className="divide-y divide-[var(--color-border)] text-sm">
                {alerts.alerts.slice(0, 10).map((a) => (
                  <li
                    key={a.id}
                    className={`flex items-start justify-between gap-3 py-2.5 ${a.isRead ? 'opacity-60' : ''}`}
                  >
                    <div className="min-w-0">
                      <span className={`inline-block text-[10px] font-bold uppercase tracking-wide rounded px-1.5 py-0.5 mr-2 ${ALERT_STYLES[a.type] || ALERT_STYLES.LOW_STOCK}`}>
                        {String(a.type).replace('_', ' ')}
                      </span>
                      <span className="text-[var(--color-text-secondary)]">{a.productName}</span>
                      <p className="text-[var(--color-text-muted)] text-xs mt-0.5 truncate">{a.message}</p>
                    </div>
                    {!a.isRead && (
                      <button
                        type="button"
                        onClick={() => markRead(a.id)}
                        className="shrink-0 text-[10px] font-semibold text-[var(--color-primary)] hover:underline"
                      >
                        Dismiss
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold">Low Stock</h3>
            <Link to="/admin/products" className="text-xs font-semibold text-[var(--color-primary)] hover:underline">
              Manage
            </Link>
          </div>
          {lowStock.length === 0 ? (
            <p className="text-sm text-[var(--color-text-muted)]">All products are well stocked.</p>
          ) : (
            <ul className="divide-y divide-[var(--color-border)] text-sm">
              {lowStock.map((p) => (
                <li key={p.id} className="flex items-center justify-between py-2.5">
                  <span className="text-[var(--color-text-secondary)] truncate pr-3">{p.name}</span>
                  <span className={`shrink-0 font-semibold ${p.stockQuantity <= 0 ? 'text-[var(--color-error)]' : 'text-[var(--color-warning)]'}`}>
                    {p.stockQuantity <= 0 ? 'Out of stock' : `${p.stockQuantity} left`}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <Link
            to="/admin/reports"
            className="mt-4 block text-center rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)] px-3 py-2 text-xs font-semibold hover:bg-[var(--color-border)] transition-colors"
          >
            View Reports →
          </Link>
        </div>
      </div>
    </div>
  );
}