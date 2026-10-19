import { useState, useEffect, useCallback } from 'react';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';
import { labelKeyFor, TASK_PRIORITIES, TASK_STATUSES } from '../../utils/staffLabels';

const inputStyle =
  'rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]';

const emptyTask = { title: '', description: '', assignedToUserId: '', priority: 'MEDIUM', dueDate: '' };

export default function AdminStaffTasks() {
  const { t, formatDate } = useLanguage();
  const [tasks, setTasks] = useState([]);
  const [staff, setStaff] = useState([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ ...emptyTask });
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');

  const load = useCallback(async () => {
    const [tRes, sRes] = await Promise.all([
      axiosClient.get('/admin/staff/tasks'),
      axiosClient.get('/admin/staff/assignable'),
    ]);
    setTasks(tRes.data);
    setStaff(sRes.data);
    setLoading(false);
  }, []);

  useEffect(() => { load(); }, [load]);

  const create = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await axiosClient.post('/admin/staff/tasks', {
        ...form,
        assignedToUserId: form.assignedToUserId ? Number(form.assignedToUserId) : null,
        dueDate: form.dueDate || null,
      });
      setForm({ ...emptyTask });
      setOpen(false);
      setToast(t('admin.staff.tasks.created'));
      setTimeout(() => setToast(''), 2500);
      load();
    } catch (err) {
      setError(err.response?.data || t('common.somethingWentWrong'));
    }
  };

  const reassign = async (task, userId) => {
    await axiosClient.put(`/admin/staff/tasks/${task.id}/reassign`, null, { params: { userId } });
    load();
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-[family-name:var(--font-heading)] text-2xl font-bold">{t('admin.staff.tasks.title')}</h1>
        <button onClick={() => setOpen((v) => !v)} className="rounded-[var(--radius-md)] bg-[var(--color-primary)] px-4 py-2 text-sm font-semibold text-white hover:bg-[var(--color-primary-hover)]">
          {t('admin.staff.tasks.new')}
        </button>
      </div>

      {toast && <p className="rounded-[var(--radius-md)] bg-[var(--color-success-bg)] p-3 text-sm text-[var(--color-success)]">{toast}</p>}

      {open && (
        <form onSubmit={create} className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-4 shadow-[var(--shadow-sm)] flex flex-col gap-3">
          <input className={inputStyle} placeholder={t('admin.staff.tasks.taskTitle')} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
          <textarea className={inputStyle} rows={3} placeholder={t('admin.staff.tasks.description')} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          <div className="grid sm:grid-cols-3 gap-3">
            <select className={inputStyle} value={form.assignedToUserId} onChange={(e) => setForm({ ...form, assignedToUserId: e.target.value })} required>
              <option value="" disabled>{t('admin.staff.tasks.assignee')}</option>
              {staff.map((s) => <option key={s.userId} value={s.userId}>{s.fullName || s.username}</option>)}
            </select>
            <select className={inputStyle} value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })} aria-label={t('admin.staff.tasks.priority')}>
              {TASK_PRIORITIES.map((p) => <option key={p.value} value={p.value}>{t(p.labelKey)}</option>)}
            </select>
            <input className={inputStyle} type="date" value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} aria-label={t('admin.staff.tasks.due')} />
          </div>
          {error && <p className="text-sm text-[var(--color-error)]">{error}</p>}
          <div className="flex justify-end gap-3">
            <button type="button" onClick={() => setOpen(false)} className="px-4 py-2 text-sm text-[var(--color-text-secondary)]">{t('common.cancel')}</button>
            <button type="submit" className="px-4 py-2 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-semibold hover:bg-[var(--color-primary-hover)]">
              {t('admin.staff.tasks.new')}
            </button>
          </div>
        </form>
      )}

      {loading ? (
        <p className="text-sm text-[var(--color-text-muted)]">…</p>
      ) : tasks.length === 0 ? (
        <p className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-6 text-sm text-[var(--color-text-muted)] shadow-[var(--shadow-sm)]">
          {t('admin.staff.tasks.empty')}
        </p>
      ) : (
        <ul className="flex flex-col gap-3">
          {tasks.map((task) => (
            <li key={task.id} className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-4 shadow-[var(--shadow-sm)]">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-semibold text-[var(--color-text)]">{task.title}</span>
                <div className="flex flex-wrap gap-2 text-xs">
                  <span className="px-2 py-0.5 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]">{t(labelKeyFor(TASK_PRIORITIES, task.priority))}</span>
                  <span className="px-2 py-0.5 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]">{t(labelKeyFor(TASK_STATUSES, task.status))}</span>
                </div>
              </div>
              {task.description && <p className="mt-1 text-sm text-[var(--color-text-secondary)]">{task.description}</p>}
              <p className="mt-1 text-xs text-[var(--color-text-muted)]">
                {t('admin.staff.tasks.assignee')}: {task.assignedTo?.username || '—'}
                {task.dueDate ? ` · ${t('admin.staff.tasks.due')}: ${formatDate(task.dueDate)}` : ''}
              </p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
