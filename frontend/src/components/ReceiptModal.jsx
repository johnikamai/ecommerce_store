import { useState, useEffect } from 'react';
import axiosClient from '../api/axiosClient';
import { useLanguage } from '../context/LanguageContext';

const STATUS_STYLES = {
  PAID: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
  PENDING: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  FAILED: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
  REFUNDED: 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]',
};

// Same set of statuses as STATUS_STYLES, so the pill colour and its label stay
// in step. Enumerated rather than built as t(`receipt.status.${...}`) so the
// keys stay visible to scripts/check-i18n.mjs.
const STATUS_KEYS = {
  PAID: 'receipt.status.PAID',
  PENDING: 'receipt.status.PENDING',
  FAILED: 'receipt.status.FAILED',
  REFUNDED: 'receipt.status.REFUNDED',
  UNKNOWN: 'receipt.status.UNKNOWN',
};

// Exported for AdminPayments, which shows the same mode enum in its table.
// Keys rather than text so both callers render in the active language.
export const MODE_LABEL_KEYS = {
  UPI: 'cart.payment.upi',
  CARD: 'cart.payment.card',
  CASH: 'cart.payment.cash',
};

/**
 * Printable digital receipt for a payment, loaded on demand from
 * GET /api/payments/{id}/receipt.
 */
export default function ReceiptModal({ paymentId, onClose }) {
  const { t, formatCurrency } = useLanguage();
  const [receipt, setReceipt] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setReceipt(null);
    setError('');
    axiosClient
      .get(`/payments/${paymentId}/receipt`)
      .then((res) => { if (!cancelled) setReceipt(res.data); })
      .catch((err) => { if (!cancelled) setError(err.response?.data || 'receipt.loadFailed'); });
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
          <h3 className="font-[family-name:var(--font-heading)] text-lg font-semibold">{t('receipt.title')}</h3>
          <button type="button" onClick={onClose} className="text-sm text-[var(--color-text-muted)] hover:underline">{t('common.close')}</button>
        </div>

        {error && <p className="text-sm text-[var(--color-error)]">{t(error)}</p>}
        {!receipt && !error && <p className="text-sm text-[var(--color-text-muted)]">{t('action.loading')}</p>}

        {receipt && (
          <div className="text-sm">
            <div className="flex items-center justify-between pb-3 border-b border-[var(--color-border)]">
              <div>
                <p className="text-xs text-[var(--color-text-muted)]">{t('receipt.number')}</p>
                <p className="font-semibold">{receipt.receiptNumber}</p>
              </div>
              <div className="text-right">
                <p className="text-xs text-[var(--color-text-muted)]">{t('receipt.order')}</p>
                <p className="font-semibold">#{receipt.orderId}</p>
              </div>
            </div>

            <div className="py-3 border-b border-[var(--color-border)]">
              <p className="text-xs text-[var(--color-text-muted)]">{t('receipt.billedTo')}</p>
              <p className="font-medium">{receipt.customer?.name || '—'}</p>
              {receipt.customer?.email && <p className="text-xs text-[var(--color-text-muted)]">{receipt.customer.email}</p>}
              {receipt.shippingAddress && <p className="text-xs text-[var(--color-text-muted)] mt-1">{receipt.shippingAddress}</p>}
            </div>

            <ul className="divide-y divide-[var(--color-border)]">
              {receipt.items.map((item, i) => (
                <li key={i} className="flex items-start justify-between py-2.5">
                  <div>
                    <p className="text-[var(--color-text-secondary)]">{item.name}</p>
                    <p className="text-xs text-[var(--color-text-muted)]">{item.quantity} × {formatCurrency(item.unitPrice)}</p>
                  </div>
                  <span className="font-medium shrink-0 pl-3">{formatCurrency(item.lineTotal)}</span>
                </li>
              ))}
            </ul>

            <div className="pt-3 border-t border-[var(--color-border)] space-y-1.5">
              <div className="flex justify-between">
                <span className="text-[var(--color-text-muted)]">{t('receipt.subtotal')}</span>
                <span>{formatCurrency(receipt.subtotal)}</span>
              </div>
              {Number(receipt.discount) > 0 && (
                <div className="flex justify-between text-[var(--color-success)]">
                  <span>{t('receipt.discount')}{receipt.couponCode ? ` (${receipt.couponCode})` : ''}</span>
                  <span>− {formatCurrency(receipt.discount)}</span>
                </div>
              )}
              <div className="flex justify-between font-semibold text-base pt-1">
                <span>{t('receipt.total')}</span>
                <span>{formatCurrency(receipt.total)}</span>
              </div>
              {receipt.paymentStatus !== 'PAID' && (
                <div className="flex justify-between text-[var(--color-warning)]">
                  <span>{t('receipt.balanceDue')}</span>
                  <span>{formatCurrency(receipt.balanceDue)}</span>
                </div>
              )}
            </div>

            <div className="mt-4 pt-3 border-t border-[var(--color-border)] space-y-1.5 text-xs">
              <div className="flex justify-between">
                <span className="text-[var(--color-text-muted)]">{t('receipt.method')}</span>
                <span>{t(MODE_LABEL_KEYS[receipt.paymentMode] || receipt.paymentMode || 'receipt.unknown')}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-[var(--color-text-muted)]">{t('receipt.status')}</span>
                <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${STATUS_STYLES[receipt.paymentStatus] || 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]'}`}>
                  {t(STATUS_KEYS[receipt.paymentStatus] || STATUS_KEYS.UNKNOWN)}
                </span>
              </div>
              {receipt.transactionRef && (
                <div className="flex justify-between">
                  <span className="text-[var(--color-text-muted)]">{t('receipt.transactionRef')}</span>
                  <span className="font-mono">{receipt.transactionRef}</span>
                </div>
              )}
              <div className="flex justify-between">
                <span className="text-[var(--color-text-muted)]">{t('receipt.issued')}</span>
                <span>{receipt.issuedAt}</span>
              </div>
            </div>

            <button
              type="button"
              onClick={() => window.print()}
              className="mt-5 w-full rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white py-2.5 text-sm font-semibold hover:opacity-90 print:hidden"
            >
              {t('receipt.print')}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}