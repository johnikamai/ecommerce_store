import { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';
import { DEPARTMENTS, labelKeyFor, STAFF_STATUSES } from '../../utils/staffLabels';

export default function StaffProfile() {
  const { t } = useLanguage();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axiosClient.get('/staff/me')
      .then((res) => setProfile(res.data))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="text-sm text-[var(--color-text-muted)]">…</p>;

  const permissions = profile?.permissions || [];
  const isLegacy = !profile || !profile.department;

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-[family-name:var(--font-heading)] text-2xl font-bold">{t('staff.portal.profile')}</h1>

      {isLegacy ? (
        <p className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-6 text-sm text-[var(--color-text-muted)] shadow-[var(--shadow-sm)]">
          {t('staff.profile.legacy')}
        </p>
      ) : (
        <>
          <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-5 shadow-[var(--shadow-sm)] flex flex-col gap-2">
            {profile.fullName && <div className="font-semibold text-[var(--color-text)]">{profile.fullName}</div>}
            {profile.email && <div className="text-sm text-[var(--color-text-secondary)]">{profile.email}</div>}
            <div className="text-sm text-[var(--color-text-secondary)]">
              {t('staff.profile.role')}: {t(labelKeyFor(DEPARTMENTS, profile.department))}
            </div>
            <div className="text-sm font-semibold text-[var(--color-primary)]">
              {t(labelKeyFor(STAFF_STATUSES, profile.status))}
            </div>
          </div>

          <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-5 shadow-[var(--shadow-sm)]">
            <h2 className="font-[family-name:var(--font-heading)] text-lg font-semibold">{t('staff.profile.permissions')}</h2>
            {permissions.length ? (
              <div className="mt-3 flex flex-wrap gap-2">
                {permissions.map((p) => (
                  <span key={p} className="text-xs px-2.5 py-1 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)] font-mono">{p}</span>
                ))}
              </div>
            ) : (
              <p className="mt-2 text-sm text-[var(--color-text-muted)]">{t('staff.profile.none')}</p>
            )}
          </div>
        </>
      )}
    </div>
  );
}
