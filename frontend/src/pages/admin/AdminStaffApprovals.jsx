import { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';
import { APPROVAL_TYPES, labelKeyFor, STAFF_STATUSES } from '../../utils/staffLabels';

const inputStyle =
  'rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]';

export default function AdminStaffApprovals() {
  const { t, formatDate } = useLanguage();
  const [approvals, setApprovals] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);
  const [reasonById, setReasonById] = useState({});

  const load = async () => {
    const res = await axiosClient.get('/admin/staff/approvals');
    setApprovals(res.data);
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const decide = async (request, approve) => {
    setBusyId(request.id);
    try {
      await axiosClient.post(`/admin/staff/approvals/${request.id}/decision`, {
        approve,
        reason: reasonById[request.id] || null,
      });
      await load();
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-[family-name:var(--font-heading)] text-2xl font-bold">{t('admin.staff.approvals.title')}</h1>

      {loading ? (
        <p className="text-sm text-[var(--color-text-muted)]">…</p>
      ) : approvals.length === 0 ? (
        <p className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-6 text-sm text-[var(--color-text-muted)] shadow-[var(--shadow-sm)]">
          {t('admin.staff.approvals.empty')}
        </p>
      ) : (
        <ul className="flex flex-col gap-3">
          {approvals.map((request) => {
            const open = request.status === 'PENDING';
            return (
              <li key={request.id} className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-4 shadow-[var(--shadow-sm)]">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-semibold text-[var(--color-text)]">{t(labelKeyFor(APPROVAL_TYPES, request.type))}</span>
                  <span className="text-xs px-2 py-0.5 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]">
                    {t(labelKeyFor(STAFF_STATUSES, request.status))}
                  </span>
                </div>
                <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
                  {request.productName || `#${request.productId}`}
                  {request.requestedPrice != null ? ` · ${request.requestedPrice}` : ''}
                  {request.discountPercent != null ? ` · ${request.discountPercent}%` : ''}
                </p>
                <p className="text-xs text-[var(--color-text-muted)]">
                  {request.requestedByUsername}{request.createdAt ? ` · ${formatDate(request.createdAt)}` : ''}
                </p>
                {request.reason && <p className="mt-1 text-sm text-[var(--color-text-secondary)]">{request.reason}</p>}

                {open && (
                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    <input
                      className={`${inputStyle} flex-1 min-w-[200px]`}
                      placeholder={t('admin.staff.reasonPlaceholder')}
                      value={reasonById[request.id] || ''}
                      onChange={(e) => setReasonById((prev) => ({ ...prev, [request.id]: e.target.value }))}
                    />
                    <button disabled={busyId === request.id} onClick={() => decide(request, true)} className="px-3 py-2 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-semibold disabled:opacity-50">
                      {t('admin.staff.approve')}
                    </button>
                    <button disabled={busyId === request.id} onClick={() => decide(request, false)} className="px-3 py-2 rounded-[var(--radius-md)] border border-[var(--color-border)] text-sm font-semibold disabled:opacity-50">
                      {t('admin.staff.reject')}
                    </button>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
