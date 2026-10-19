import { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';
import { labelKeyFor, TASK_PRIORITIES, TASK_STATUSES } from '../../utils/staffLabels';

const inputClass =
  'rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]';

export default function StaffTasks() {
  const { t, formatDate } = useLanguage();
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [drafts, setDrafts] = useState({});
  const [savedId, setSavedId] = useState(null);

  const load = async () => {
    const res = await axiosClient.get('/staff/tasks');
    setTasks(res.data);
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const draftFor = (task) => drafts[task.id] || { status: task.status, progressNote: task.progressNote || '' };
  const setDraft = (task, patch) => setDrafts((prev) => ({ ...prev, [task.id]: { ...draftFor(task), ...patch } }));

  const save = async (task) => {
    const draft = draftFor(task);
    await axiosClient.put(`/staff/tasks/${task.id}`, draft);
    setSavedId(task.id);
    setDrafts((prev) => { const next = { ...prev }; delete next[task.id]; return next; });
    load();
  };

  if (loading) return <p className="text-sm text-[var(--color-text-muted)]">…</p>;

  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-[family-name:var(--font-heading)] text-2xl font-bold">{t('staff.tasks.title')}</h1>

      {tasks.length === 0 ? (
        <p className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-6 text-sm text-[var(--color-text-muted)] shadow-[var(--shadow-sm)]">
          {t('staff.tasks.empty')}
        </p>
      ) : (
        <div className="flex flex-col gap-4">
          {tasks.map((task) => {
            const draft = draftFor(task);
            return (
              <div key={task.id} className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-4 shadow-[var(--shadow-sm)]">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="font-semibold text-[var(--color-text)]">{task.title}</h3>
                  <span className="text-xs px-2 py-0.5 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]">
                    {t(labelKeyFor(TASK_PRIORITIES, task.priority))}
                  </span>
                </div>
                {task.description && <p className="mt-1 text-sm text-[var(--color-text-secondary)]">{task.description}</p>}
                {task.dueDate && <p className="mt-1 text-xs text-[var(--color-text-muted)]">{t('staff.tasks.due')}: {formatDate(task.dueDate)}</p>}

                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <select className={inputClass} value={draft.status} onChange={(e) => setDraft(task, { status: e.target.value })} aria-label={t('staff.tasks.update')}>
                    {TASK_STATUSES.map((s) => (
                      <option key={s.value} value={s.value}>{t(s.labelKey)}</option>
                    ))}
                  </select>
                  <input
                    className={`${inputClass} flex-1 min-w-[180px]`}
                    placeholder={t('staff.tasks.notePlaceholder')}
                    value={draft.progressNote}
                    onChange={(e) => setDraft(task, { progressNote: e.target.value })}
                  />
                  <button
                    onClick={() => save(task)}
                    className="rounded-[var(--radius-md)] bg-[var(--color-primary)] px-4 py-2 text-sm font-semibold text-white hover:bg-[var(--color-primary-hover)]"
                  >
                    {savedId === task.id ? t('staff.tasks.saved') : t('staff.tasks.save')}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
