import { useState, useEffect, useCallback } from 'react';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';
import { ALL_PERMISSIONS, DEPARTMENTS, labelKeyFor, STAFF_STATUSES } from '../../utils/staffLabels';

const TABS = [
  { key: 'ALL', status: undefined, labelKey: 'admin.staff.tabAll' },
  { key: 'PENDING', status: 'PENDING', labelKey: 'admin.staff.tabPending' },
  { key: 'APPROVED', status: 'APPROVED', labelKey: 'admin.staff.tabApproved' },
  { key: 'SUSPENDED', status: 'SUSPENDED', labelKey: 'admin.staff.tabSuspended' },
];

const ACTION_LABELS = {
  approve: 'admin.staff.approve',
  reject: 'admin.staff.reject',
  requestInfo: 'admin.staff.requestInfo',
  suspend: 'admin.staff.suspend',
  reactivate: 'admin.staff.reactivate',
  deactivate: 'admin.staff.deactivate',
};

const ACTION_VALUES = {
  approve: 'APPROVE',
  reject: 'REJECT',
  requestInfo: 'REQUEST_INFO',
  suspend: 'SUSPEND',
  reactivate: 'REACTIVATE',
  deactivate: 'DEACTIVATE',
};

const STATUS_CLASS = {
  PENDING: 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]',
  MORE_INFO: 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]',
  APPROVED: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
  REJECTED: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
  SUSPENDED: 'bg-[var(--color-error-bg)] text-[var(--color-error)]',
  DEACTIVATED: 'bg-[var(--color-card-bg-tint)] text-[var(--color-text-muted)]',
};

const inputStyle =
  'rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]';

const emptyInvite = { fullName: '', email: '', phone: '', department: 'PRODUCT_MANAGER', password: '' };

function actionsForStatus(status) {
  switch (status) {
    case 'PENDING':
    case 'MORE_INFO':
      return ['approve', 'reject', 'requestInfo', 'suspend'];
    case 'APPROVED':
      return ['suspend', 'deactivate'];
    case 'SUSPENDED':
      return ['reactivate', 'deactivate'];
    case 'REJECTED':
    case 'DEACTIVATED':
      return ['approve'];
    default:
      return [];
  }
}

const REASON_ACTIONS = ['reject', 'requestInfo', 'suspend'];

export default function AdminStaff() {
  const { t, formatDate } = useLanguage();
  const [summary, setSummary] = useState(null);
  const [staff, setStaff] = useState([]);
  const [activeTab, setActiveTab] = useState('ALL');
  const [query, setQuery] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [pending, setPending] = useState(null);
  const [reason, setReason] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [role, setRole] = useState({ department: 'PRODUCT_MANAGER', permissions: [] });
  const [inviteOpen, setInviteOpen] = useState(false);
  const [invite, setInvite] = useState({ ...emptyInvite });
  const [inviteError, setInviteError] = useState('');
  const [toast, setToast] = useState('');

  const load = useCallback(async () => {
    const tab = TABS.find((x) => x.key === activeTab);
    const [s, list] = await Promise.all([
      axiosClient.get('/admin/staff/summary'),
      axiosClient.get('/admin/staff', { params: query ? { q: query } : tab.status ? { status: tab.status } : {} }),
    ]);
    setSummary(s.data);
    setStaff(list.data);
  }, [activeTab, query]);

  useEffect(() => { load(); }, [load]);

  const flash = (msg) => {
    setToast(msg);
    setTimeout(() => setToast(''), 2500);
  };

  const decide = async (profile, action) => {
    setBusyId(profile.id);
    try {
      await axiosClient.post(`/admin/staff/${profile.id}/decision`, { action: ACTION_VALUES[action], reason: reason || null });
      setPending(null);
      setReason('');
      await load();
    } finally {
      setBusyId(null);
    }
  };

  const saveRole = async (profile) => {
    setBusyId(profile.id);
    try {
      await axiosClient.put(`/admin/staff/${profile.id}/role`, role);
      setEditingId(null);
      await load();
    } finally {
      setBusyId(null);
    }
  };

  const startEdit = (profile) => {
    setEditingId(profile.id);
    setRole({ department: profile.department, permissions: profile.permissions || [] });
  };

  const togglePermission = (perm) => {
    setRole((prev) => ({
      ...prev,
      permissions: prev.permissions.includes(perm)
        ? prev.permissions.filter((p) => p !== perm)
        : [...prev.permissions, perm],
    }));
  };

  const submitInvite = async (e) => {
    e.preventDefault();
    setInviteError('');
    try {
      await axiosClient.post('/admin/staff/invite', invite);
      setInviteOpen(false);
      setInvite({ ...emptyInvite });
      flash(t('admin.staff.created'));
      await load();
    } catch (err) {
      setInviteError(err.response?.data || t('common.somethingWentWrong'));
    }
  };

  const actionsFor = (profile) => {
    if (profile.status === 'PENDING' || profile.status === 'MORE_INFO') return ['approve', 'reject', 'requestInfo', 'suspend'];
    if (profile.status === 'APPROVED') return ['suspend', 'deactivate'];
    if (profile.status === 'SUSPENDED') return ['reactivate', 'deactivate'];
    return ['approve'];
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-[family-name:var(--font-heading)] text-2xl font-bold">{t('admin.staff.title')}</h1>
        <button
          onClick={() => { setInvite({ ...emptyInvite }); setInviteError(''); setInviteOpen(true); }}
          className="rounded-[var(--radius-md)] bg-[var(--color-primary)] px-4 py-2 text-sm font-semibold text-white hover:bg-[var(--color-primary-hover)]"
        >
          {t('admin.staff.invite')}
        </button>
      </div>

      {summary && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <SummaryCard label={t('admin.staff.summary.staff')} value={summary.staff?.total} />
          <SummaryCard label={t('admin.staff.summary.pending')} value={summary.staff?.pending} />
          <SummaryCard label={t('admin.staff.summary.tasks')} value={summary.tasks?.todo} />
          <SummaryCard label={t('admin.staff.summary.approvals')} value={summary.approvals?.pending} />
        </div>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <div className="flex gap-1 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] p-1">
          {TABS.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`px-3 py-1.5 rounded-[var(--radius-sm)] text-sm font-medium ${
                activeTab === tab.key ? 'bg-[var(--color-card-bg)] text-[var(--color-text)]' : 'text-[var(--color-text-secondary)]'
              }`}
            >
              {t(tab.labelKey)}
            </button>
          ))}
        </div>
        <input
          className={`${inputStyle} flex-1 min-w-[200px]`}
          placeholder={t('admin.staff.searchPlaceholder')}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </div>

      {toast && <p className="rounded-[var(--radius-md)] bg-[var(--color-success-bg)] p-3 text-sm text-[var(--color-success)]">{toast}</p>}

      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden">
        {staff.length === 0 ? (
          <p className="p-6 text-sm text-[var(--color-text-muted)]">{t('admin.staff.empty')}</p>
        ) : (
          <>
          <div className="hidden md:grid grid-cols-[2fr_2fr_1fr_1fr_auto] gap-3 px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)] border-b border-[var(--color-border)]">
            <span>{t('admin.staff.colName')}</span>
            <span>{t('admin.staff.colEmail')}</span>
            <span>{t('admin.staff.colDept')}</span>
            <span>{t('admin.staff.colStatus')}</span>
            <span className="text-right">{t('admin.staff.colActions')}</span>
          </div>
          <ul className="divide-y divide-[var(--color-border)]">
            {staff.map((profile) => (
              <li key={profile.id} className="p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-semibold text-[var(--color-text)]">{profile.fullName}</span>
                      <span className={`text-xs px-2 py-0.5 rounded-[var(--radius-md)] ${STATUS_CLASS[profile.status] || ''}`}>
                        {t(labelKeyFor(STAFF_STATUSES, profile.status))}
                      </span>
                    </div>
                    <div className="text-sm text-[var(--color-text-secondary)]">{profile.user?.email}</div>
                    <div className="text-xs text-[var(--color-text-muted)]">
                      {t('admin.staff.colDept')}: {t(labelKeyFor(DEPARTMENTS, profile.department))}
                      {profile.createdAt ? ` · ${formatDate(profile.createdAt)}` : ''}
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {actionsFor(profile).map((action) => (
                      <button
                        key={action}
                        disabled={busyId === profile.id}
                        onClick={() => {
                          if (action === 'reject' || action === 'requestInfo' || action === 'suspend') {
                            setPending({ id: profile.id, action });
                            setReason('');
                          } else {
                            decide(profile, action);
                          }
                        }}
                        className="px-3 py-1.5 rounded-[var(--radius-md)] text-xs font-semibold border border-[var(--color-border)] hover:bg-[var(--color-card-bg-tint)] disabled:opacity-50"
                      >
                        {t(ACTION_LABELS[action])}
                      </button>
                    ))}
                    <button
                      onClick={() => startEdit(profile)}
                      className="px-3 py-1.5 rounded-[var(--radius-md)] text-xs font-semibold border border-[var(--color-border)] hover:bg-[var(--color-card-bg-tint)]"
                    >
                      {t('admin.staff.permissions')}
                    </button>
                  </div>
                </div>

                {pending?.id === profile.id && (
                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    <input className={`${inputStyle} flex-1 min-w-[200px]`} placeholder={t('admin.staff.reasonPlaceholder')} value={reason} onChange={(e) => setReason(e.target.value)} />
                    <button onClick={() => decide(profile, pending.action)} className="px-3 py-2 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-semibold">
                      {t(ACTION_LABELS[pending.action])}
                    </button>
                    <button onClick={() => setPending(null)} className="text-sm text-[var(--color-text-secondary)]">{t('common.cancel')}</button>
                  </div>
                )}

                {editingId === profile.id && (
                  <div className="mt-3 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] p-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <select className={inputStyle} value={role.department} onChange={(e) => setRole({ ...role, department: e.target.value })}>
                        {DEPARTMENTS.map((d) => <option key={d.value} value={d.value}>{t(d.labelKey)}</option>)}
                      </select>
                      <button onClick={() => saveRole(profile)} className="px-3 py-2 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-semibold">
                        {t('admin.staff.saveRole')}
                      </button>
                      <button onClick={() => setEditingId(null)} className="text-sm text-[var(--color-text-secondary)]">{t('common.cancel')}</button>
                    </div>
                    <div className="mt-3 grid grid-cols-2 sm:grid-cols-3 gap-2">
                      {ALL_PERMISSIONS.map((perm) => (
                        <label key={perm} className="flex items-center gap-2 text-xs font-mono text-[var(--color-text-secondary)]">
                          <input type="checkbox" checked={role.permissions.includes(perm)} onChange={() => togglePermission(perm)} />
                          {perm}
                        </label>
                      ))}
                    </div>
                  </div>
                )}
              </li>
            ))}
          </ul>
          </>
        )}
      </div>

      {inviteOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-lg rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] p-5 shadow-[var(--shadow-lg)]">
            <h2 className="font-[family-name:var(--font-heading)] text-lg font-semibold">{t('admin.staff.inviteTitle')}</h2>
            <form onSubmit={submitInvite} className="mt-4 flex flex-col gap-3">
              <input className={inputStyle} placeholder={t('staff.apply.fullName')} value={invite.fullName} onChange={(e) => setInvite({ ...invite, fullName: e.target.value })} required />
              <input className={inputStyle} type="email" placeholder={t('staff.apply.email')} value={invite.email} onChange={(e) => setInvite({ ...invite, email: e.target.value })} required />
              <input className={inputStyle} placeholder={t('staff.apply.phone')} value={invite.phone} onChange={(e) => setInvite({ ...invite, phone: e.target.value })} />
              <select className={inputStyle} value={invite.department} onChange={(e) => setInvite({ ...invite, department: e.target.value })}>
                {DEPARTMENTS.map((d) => <option key={d.value} value={d.value}>{t(d.labelKey)}</option>)}
              </select>
              <input className={inputStyle} type="password" placeholder={t('staff.apply.password')} value={invite.password} onChange={(e) => setInvite({ ...invite, password: e.target.value })} required />
              {inviteError && <p className="text-sm text-[var(--color-error)]">{inviteError}</p>}
              <div className="flex justify-end gap-3 mt-1">
                <button type="button" onClick={() => setInviteOpen(false)} className="px-4 py-2 text-sm text-[var(--color-text-secondary)]">{t('common.cancel')}</button>
                <button type="submit" className="px-4 py-2 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-semibold hover:bg-[var(--color-primary-hover)]">
                  {t('admin.staff.invite')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function SummaryCard({ label, value }) {
  return (
    <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-4 shadow-[var(--shadow-sm)]">
      <div className="text-2xl font-bold text-[var(--color-primary)]">{value ?? 0}</div>
      <div className="text-xs text-[var(--color-text-secondary)]">{label}</div>
    </div>
  );
}
