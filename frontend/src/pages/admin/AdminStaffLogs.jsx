import { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';

export default function AdminStaffLogs() {
  const { t, formatDate } = useLanguage();
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axiosClient.get('/admin/staff/logs')
      .then((res) => setLogs(res.data))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-[family-name:var(--font-heading)] text-2xl font-bold">{t('admin.staff.logs.title')}</h1>

      {loading ? (
        <p className="text-sm text-[var(--color-text-muted)]">…</p>
      ) : logs.length === 0 ? (
        <p className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-6 text-sm text-[var(--color-text-muted)] shadow-[var(--shadow-sm)]">
          {t('admin.staff.logs.empty')}
        </p>
      ) : (
        <ol className="relative flex flex-col gap-4 border-l border-[var(--color-border)] pl-5">
          {logs.map((log) => (
            <li key={log.id} className="relative">
              <span className="absolute -left-[1.6rem] top-1.5 h-2.5 w-2.5 rounded-full bg-[var(--color-primary)]" />
              <div className="text-sm font-semibold text-[var(--color-text)]">{log.action}</div>
              <div className="text-sm text-[var(--color-text-secondary)]">{log.details}</div>
              <div className="text-xs text-[var(--color-text-muted)]">
                {log.actorUsername}{log.createdAt ? ` · ${formatDate(log.createdAt)}` : ''}
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
