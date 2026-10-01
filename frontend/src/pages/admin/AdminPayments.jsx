import { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';

const STATUS_COLORS = {
  SUCCESS: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
  FAILED: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
  PENDING: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  REFUNDED: 'bg-[var(--color-info-bg)] text-[var(--color-info)]',
  CANCELLED: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
};

function Badge({ status }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${STATUS_COLORS[status] || 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]'}`}>{status}</span>;
}

export default function AdminPayments() {
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axiosClient.get('/payments')
      .then((res) => setPayments(res.data))
      .catch(() => setPayments([]))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">Loading payments...</p>;

  const totalCollected = payments.filter((p) => p.status === 'SUCCESS').reduce((s, p) => s + Number(p.amount || 0), 0);

  return (
    <div>
      <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold mb-6">Payments</h2>

      <div className="grid grid-cols-3 gap-4 mb-6">
        {[
          { label: 'Total Payments', value: payments.length },
          { label: 'Collected', value: `₹${totalCollected.toLocaleString('en-IN')}` },
          { label: 'Refunds', value: payments.filter((p) => p.status === 'REFUNDED').length },
        ].map((c) => (
          <div key={c.label} className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
            <p className="text-xs text-[var(--color-text-muted)] mb-1">{c.label}</p>
            <p className="font-[family-name:var(--font-heading)] text-2xl font-bold text-[var(--color-primary)]">{c.value}</p>
          </div>
        ))}
      </div>

      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden">
        {payments.length === 0 ? (
          <p className="p-6 text-sm text-[var(--color-text-muted)]">No payments recorded yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-[var(--color-text-muted)] border-b border-[var(--color-border)]">
                  <th className="py-3 px-4">#</th>
                  <th className="py-3 px-4">Order</th>
                  <th className="py-3 px-4">Customer</th>
                  <th className="py-3 px-4">Method</th>
                  <th className="py-3 px-4 text-right">Amount</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4">Date</th>
                </tr>
              </thead>
              <tbody>
                {payments.map((p) => (
                  <tr key={p.id} className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-card-bg-tint)]">
                    <td className="py-3 px-4 font-semibold">{p.id}</td>
                    <td className="py-3 px-4">#{p.orderId ?? p.order?.id ?? '—'}</td>
                    <td className="py-3 px-4">{p.customer?.name || p.customerName || '—'}</td>
                    <td className="py-3 px-4 text-[var(--color-text-secondary)]">{p.paymentMethod || p.method || '—'}</td>
                    <td className="py-3 px-4 text-right font-medium">₹{Number(p.amount || p.paymentAmount || 0).toLocaleString('en-IN')}</td>
                    <td className="py-3 px-4"><Badge status={p.status} /></td>
                    <td className="py-3 px-4 text-[var(--color-text-secondary)]">{p.paymentDate ? new Date(p.paymentDate).toLocaleString('en-IN') : new Date(p.paymentTime || p.createdAt).toLocaleString('en-IN')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}