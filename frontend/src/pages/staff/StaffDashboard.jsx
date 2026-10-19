import { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';
import { DEPARTMENTS, labelKeyFor, STAFF_STATUSES } from '../../utils/staffLabels';

export default function StaffDashboard() {
  const { t, formatDate } = useLanguage();
  const [data, setData] = useState(null);
  const [notifications, setNotifications] = useState({ items: [], unread: 0 });
  const [loading, setLoading] = useState(true);

  const load = async () => {
    const [dash, notes] = await Promise.all([
      axiosClient.get('/staff/dashboard'),
      axiosClient.get('/staff/notifications'),
    ]);
    setData(dash.data);
    setNotifications(notes.data);
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const markAllRead = async () => {
    await axiosClient.put('/staff/notifications/read-all');
    load();
  };

  if (loading) return <p className="text-sm text-[var(--color-text-muted)]">…</p>;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-[family-name:var(--font-heading)] text-2xl font-bold">{t('staff.portal.dashboard')}</h1>
        {data?.fullName && (
          <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
            {data.fullName} · {t('staff.dashboard.department')}: {t(labelKeyFor(DEPARTMENTS, data.department))} · {t(labelKeyFor(STAFF_STATUSES, data.status))}
          </p>
        )}
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
        <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-4 shadow-[var(--shadow-sm)]">
          <div className="text-2xl font-bold text-[var(--color-primary)]">{data?.tasks?.open ?? 0}</div>
          <div className="text-xs text-[var(--color-text-secondary)]">{t('staff.dashboard.openTasks')}</div>
        </div>
        <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-4 shadow-[var(--shadow-sm)]">
          <div className="text-2xl font-bold text-[var(--color-text)]">{data?.tasks?.completed ?? 0}</div>
          <div className="text-xs text-[var(--color-text-secondary)]">{t('staff.taskStatus.COMPLETED')}</div>
        </div>
        <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-4 shadow-[var(--shadow-sm)]">
          <div className="text-2xl font-bold text-[var(--color-text)]">{notifications.unread ?? 0}</div>
          <div className="text-xs text-[var(--color-text-secondary)]">{t('staff.dashboard.unread')}</div>
        </div>
      </div>

      <TaskList tasks={data?.openTasks || []} />

      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)]">
        <div className="flex items-center justify-between p-4 border-b border-[var(--color-border)]">
          <h2 className="font-[family-name:var(--font-heading)] text-lg font-semibold">{t('staff.portal.notifications')}</h2>
          {notifications.unread > 0 && (
            <button onClick={markAllRead} className="text-xs font-semibold text-[var(--color-primary)]">
              {t('staff.notifications.markAllRead')}
            </button>
          )}
        </div>
        {notifications.items?.length ? (
          <ul className="divide-y divide-[var(--color-border)]">
            {notifications.items.map((n) => (
              <li key={n.id} className={`p-4 text-sm flex justify-between gap-3 ${n.isRead ? 'text-[var(--color-text-secondary)]' : 'text-[var(--color-text)] font-medium'}`}>
                <span>{n.message}</span>
                <span className="text-xs text-[var(--color-text-muted)] whitespace-nowrap">{n.sentAt ? formatDate(n.sentAt) : ''}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="p-4 text-sm text-[var(--color-text-muted)]">{t('staff.notifications.empty')}</p>
        )}
      </div>
    </div>
  );
}

function TaskList({ tasks }) {
  const { t } = useLanguage();
  return (
    <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)]">
      <h2 className="p-4 border-b border-[var(--color-border)] font-[family-name:var(--font-heading)] text-lg font-semibold">
        {t('staff.portal.tasks')}
      </h2>
      {tasks.length ? (
        <ul className="divide-y divide-[var(--color-border)]">
          {tasks.map((task) => (
            <li key={task.id} className="p-4">
              <div className="font-medium text-[var(--color-text)]">{task.title}</div>
              {task.description && <div className="text-sm text-[var(--color-text-secondary)]">{task.description}</div>}
            </li>
          ))}
        </ul>
      ) : (
        <p className="p-4 text-sm text-[var(--color-text-muted)]">{t('staff.dashboard.noTasks')}</p>
      )}
    </div>
  );
}
