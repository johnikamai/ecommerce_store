import { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import ReceiptModal, { MODE_LABELS } from '../../components/ReceiptModal';

// Keys must match the PaymentStatus enum on the backend:
// PENDING | PAID | FAILED | REFUNDED
const STATUS_STYLES = {
  PAID: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
  PENDING: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  FAILED: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
  REFUNDED: 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]',
};

function StatusChip({ status }) {
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${STATUS_STYLES[status] || 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]'}`}>
      {status || 'UNKNOWN'}
    </span>
  );
}

const money = (n) => `₹${Number(n || 0).toLocaleString('en-IN')}`;


export default function AdminPayments() {
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [receiptFor, setReceiptFor] = useState(null);

  const load = async () => {
    try {
      const res = await axiosClient.get('/payments');
      setPayments(res.data || []);
      setError('');
    } catch {
      setError('Failed to load payments');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const setStatus = async (payment, next) => {
    setBusyId(payment.id);
    try {
      await axiosClient.put(`/payments/${payment.id}/status`, { status: next });
      await load();
    } catch (err) {
      setError(err.response?.data || `Could not set ${next}`);
      await load();
    } finally {
      setBusyId(null);
    }
  };

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">Loading payments...</p>;

  // Collected counts only genuinely-paid money. A refunded payment has left the
  // business, so REFUNDED is deliberately excluded rather than netted out here.
  const collected = payments
    .filter((p) => p.paymentStatus === 'PAID')
    .reduce((s, p) => s + Number(p.amount || 0), 0);

  const refunded = payments.filter((p) => p.paymentStatus === 'REFUNDED');
  const refundedTotal = refunded.reduce((s, p) => s + Number(p.amount || 0), 0);
  const awaiting = payments.filter((p) => p.paymentStatus === 'PENDING').length;

  return (
    <div>
      <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold mb-6">Payments</h2>

      {error && (
        <p className="mb-4 rounded-[var(--radius-md)] bg-[var(--color-error-bg)] text-[var(--color-error)] px-3 py-2 text-sm">
          {error}
        </p>
      )}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        {[
          { label: 'Total Payments', value: payments.length, color: 'var(--color-primary)' },
          { label: 'Collected', value: money(collected), color: 'var(--color-success)' },
          { label: 'Awaiting Collection', value: awaiting, color: 'var(--color-warning)' },
          { label: 'Refunded', value: money(refundedTotal), color: 'var(--color-text-secondary)' },
        ].map((c) => (
          <div key={c.label} className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5">
            <p className="text-xs text-[var(--color-text-muted)] mb-1">{c.label}</p>
            <p className="font-[family-name:var(--font-heading)] text-2xl font-bold" style={{ color: c.color }}>{c.value}</p>
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
                  <th className="py-3 px-4">Ref</th>
                  <th className="py-3 px-4">Date</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {payments.map((p) => (
                  <tr key={p.id} className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-card-bg-tint)]">
                    <td className="py-3 px-4 font-semibold">{p.id}</td>
                    <td className="py-3 px-4">
                      <span className="font-medium">#{p.orderId ?? '—'}</span>
                      {p.orderStatus && (
                        <span className="block text-xs text-[var(--color-text-muted)]">{p.orderStatus}</span>
                      )}
                    </td>
                    <td className="py-3 px-4">
                      <span className="block truncate max-w-[160px]">{p.customerName || '—'}</span>
                      {p.customerEmail && (
                        <span className="block text-xs text-[var(--color-text-muted)] truncate max-w-[160px]">{p.customerEmail}</span>
                      )}
                    </td>
                    <td className="py-3 px-4 text-[var(--color-text-secondary)]">{MODE_LABELS[p.paymentMode] || p.paymentMode || '—'}</td>
                    <td className="py-3 px-4 text-right font-medium">{money(p.amount)}</td>
                    <td className="py-3 px-4"><StatusChip status={p.paymentStatus} /></td>
                    <td className="py-3 px-4 text-xs text-[var(--color-text-muted)] font-mono">{p.transactionRef || '—'}</td>
                    <td className="py-3 px-4 text-[var(--color-text-secondary)] whitespace-nowrap">
                      {p.transactionDate ? new Date(p.transactionDate).toLocaleString('en-IN') : '—'}
                    </td>
                    <td className="py-3 px-4 text-right whitespace-nowrap">
                      <button
                        type="button"
                        onClick={() => setReceiptFor(p.id)}
                        className="text-xs font-semibold text-[var(--color-primary)] hover:underline mr-3"
                      >
                        Receipt
                      </button>
                      {(p.nextStatuses || []).map((s) => (
                        <button
                          key={s}
                          type="button"
                          disabled={busyId === p.id}
                          onClick={() => setStatus(p, s)}
                          className="text-xs font-semibold text-[var(--color-text-secondary)] hover:underline mr-2 disabled:opacity-40"
                        >
                          {s}
                        </button>
                      ))}
                      {(p.nextStatuses || []).length === 0 && (
                        <span className="text-xs text-[var(--color-text-muted)]">final</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {receiptFor != null && <ReceiptModal paymentId={receiptFor} onClose={() => setReceiptFor(null)} />}
    </div>
  );
}
