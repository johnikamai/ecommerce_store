import { useState, useEffect } from 'react';
import axiosClient from '../api/axiosClient';

const STATUS_STYLES = {
  PAID: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
  PENDING: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  FAILED: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
  REFUNDED: 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]',
};

export const MODE_LABELS = {
  UPI: 'UPI',
  CARD: 'Card',
  CASH: 'Cash on Delivery',
};

const money = (n) => `₹${Number(n || 0).toLocaleString('en-IN')}`;

/**
 * Printable digital receipt for a payment, loaded on demand from
 * GET /api/payments/{id}/receipt.
 */
export default function ReceiptModal({ paymentId, onClose }) {
  const [receipt, setReceipt] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setReceipt(null);
    setError('');
    axiosClient
      .get(`/payments/${paymentId}/receipt`)
      .then((res) => { if (!cancelled) setReceipt(res.data); })
      .catch((err) => { if (!cancelled) setError(err.response?.data || 'Could not load receipt'); });
    return () => { cancelled = true; };
  }, [paymentId]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/40 p-6 print:bg-white print:p-0"
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-xl p-6 my-8 print:shadow-none print:my-0"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between mb-4 print:hidden">
          <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold">Receipt</h3>
          <button type="button" onClick={onClose} className="text-sm text-[var(--color-text-muted)] hover:underline">Close</button>
        </div>

        {error && <p className="text-sm text-[var(--color-error)]">{error}</p>}
        {!receipt && !error && <p className="text-sm text-[var(--color-text-muted)]">Loading receipt...</p>}

        {receipt && (
          <div className="text-sm">
            <div className="flex items-center justify-between pb-3 border-b border-[var(--color-border)]">
              <div>
                <p className="text-xs text-[var(--color-text-muted)]">Receipt no.</p>
                <p className="font-semibold">{receipt.receiptNumber}</p>
              </div>
              <div className="text-right">
                <p className="text-xs text-[var(--color-text-muted)]">Order</p>
                <p className="font-semibold">#{receipt.orderId}</p>
              </div>
            </div>

            <div className="py-3 border-b border-[var(--color-border)]">
              <p className="text-xs text-[var(--color-text-muted)]">Billed to</p>
              <p className="font-medium">{receipt.customer?.name || '—'}</p>
              {receipt.customer?.email && <p className="text-xs text-[var(--color-text-muted)]">{receipt.customer.email}</p>}
              {receipt.shippingAddress && <p className="text-xs text-[var(--color-text-muted)] mt-1">{receipt.shippingAddress}</p>}
            </div>

            <ul className="divide-y divide-[var(--color-border)]">
              {receipt.items.map((item, i) => (
                <li key={i} className="flex items-start justify-between py-2.5">
                  <div>
                    <p className="text-[var(--color-text-secondary)]">{item.name}</p>
                    <p className="text-xs text-[var(--color-text-muted)]">{item.quantity} × {money(item.unitPrice)}</p>
                  </div>
                  <span className="font-medium shrink-0 pl-3">{money(item.lineTotal)}</span>
                </li>
              ))}
            </ul>

            <div className="pt-3 border-t border-[var(--color-border)] space-y-1.5">
              <div className="flex justify-between">
                <span className="text-[var(--color-text-muted)]">Subtotal</span>
                <span>{money(receipt.subtotal)}</span>
              </div>
              {Number(receipt.discount) > 0 && (
                <div className="flex justify-between text-[var(--color-success)]">
                  <span>Discount{receipt.couponCode ? ` (${receipt.couponCode})` : ''}</span>
                  <span>− {money(receipt.discount)}</span>
                </div>
              )}
              <div className="flex justify-between font-semibold text-base pt-1">
                <span>Total</span>
                <span>{money(receipt.total)}</span>
              </div>
              {receipt.paymentStatus !== 'PAID' && (
                <div className="flex justify-between text-[var(--color-warning)]">
                  <span>Balance due</span>
                  <span>{money(receipt.balanceDue)}</span>
                </div>
              )}
            </div>

            <div className="mt-4 pt-3 border-t border-[var(--color-border)] space-y-1.5 text-xs">
              <div className="flex justify-between">
                <span className="text-[var(--color-text-muted)]">Method</span>
                <span>{MODE_LABELS[receipt.paymentMode] || receipt.paymentMode || '—'}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-[var(--color-text-muted)]">Status</span>
                <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${STATUS_STYLES[receipt.paymentStatus] || 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]'}`}>
                  {receipt.paymentStatus || 'UNKNOWN'}
                </span>
              </div>
              {receipt.transactionRef && (
                <div className="flex justify-between">
                  <span className="text-[var(--color-text-muted)]">Transaction ref</span>
                  <span className="font-mono">{receipt.transactionRef}</span>
                </div>
              )}
              <div className="flex justify-between">
                <span className="text-[var(--color-text-muted)]">Issued</span>
                <span>{receipt.issuedAt}</span>
              </div>
            </div>

            <button
              type="button"
              onClick={() => window.print()}
              className="mt-5 w-full rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white py-2.5 text-sm font-semibold hover:opacity-90 print:hidden"
            >
              Print / Save as PDF
            </button>
          </div>
        )}
      </div>
    </div>
  );
}