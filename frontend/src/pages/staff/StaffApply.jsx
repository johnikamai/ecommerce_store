import { useState } from 'react';
import { Link } from 'react-router-dom';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';
import { DEPARTMENTS, labelKeyFor, STAFF_STATUSES } from '../../utils/staffLabels';

const inputClass =
  'w-full rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]';

const emptyForm = { fullName: '', email: '', phone: '', department: 'PRODUCT_MANAGER', experience: '', reason: '', password: '' };

export default function StaffApply() {
  const { t } = useLanguage();
  const [form, setForm] = useState(emptyForm);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const [trackEmail, setTrackEmail] = useState('');
  const [statusResult, setStatusResult] = useState(null);
  const [looking, setLooking] = useState(false);
  const [notFound, setNotFound] = useState(false);

  const setField = (key) => (e) => setForm((prev) => ({ ...prev, [key]: e.target.value }));

  const apply = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await axiosClient.post('/staff/apply', form);
      setSubmitted(true);
      setForm(emptyForm);
    } catch (err) {
      setError(err.response?.data || t('common.somethingWentWrong'));
    } finally {
      setBusy(false);
    }
  };

  const checkStatus = async (e) => {
    e.preventDefault();
    setLooking(true);
    setStatusResult(null);
    setNotFound(false);
    try {
      const res = await axiosClient.get('/staff/application-status', { params: { email: trackEmail } });
      if (res.data?.found) setStatusResult(res.data);
      else setNotFound(true);
    } catch {
      setNotFound(true);
    } finally {
      setLooking(false);
    }
  };

  return (
    <div className="container-x py-[var(--space-8)] max-w-2xl">
      <h1 className="font-[family-name:var(--font-heading)] text-2xl font-bold text-[var(--color-text)]">
        {t('staff.apply.title')}
      </h1>
      <p className="mt-1 text-sm text-[var(--color-text-secondary)]">{t('staff.apply.subtitle')}</p>

      {submitted ? (
        <div className="mt-6 rounded-[var(--radius-lg)] bg-[var(--color-success-bg)] p-4 text-sm text-[var(--color-success)]">
          {t('staff.apply.success')}
        </div>
      ) : (
        <form onSubmit={apply} className="mt-6 rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-5 shadow-[var(--shadow-sm)] flex flex-col gap-3">
          <input className={inputClass} placeholder={t('staff.apply.fullName')} value={form.fullName} onChange={setField('fullName')} required />
          <input className={inputClass} type="email" placeholder={t('staff.apply.email')} value={form.email} onChange={setField('email')} required />
          <input className={inputClass} placeholder={t('staff.apply.phone')} value={form.phone} onChange={setField('phone')} />
          <select className={inputClass} value={form.department} onChange={setField('department')} aria-label={t('staff.apply.department')}>
            <option value="" disabled>{t('staff.apply.chooseDept')}</option>
            {DEPARTMENTS.map((d) => (
              <option key={d.value} value={d.value}>{t(d.labelKey)}</option>
            ))}
          </select>
          <textarea className={inputClass} rows={3} placeholder={t('staff.apply.experience')} value={form.experience} onChange={setField('experience')} />
          <textarea className={inputClass} rows={3} placeholder={t('staff.apply.reason')} value={form.reason} onChange={setField('reason')} />
          <input className={inputClass} type="password" placeholder={t('staff.apply.password')} value={form.password} onChange={setField('password')} required />
          {error && <p className="text-sm text-[var(--color-error)]">{error}</p>}
          <button type="submit" disabled={busy}
            className="rounded-[var(--radius-md)] bg-[var(--color-primary)] px-4 py-2.5 text-sm font-semibold text-white hover:bg-[var(--color-primary-hover)] disabled:opacity-50">
            {busy ? t('staff.apply.submitting') : t('staff.apply.submit')}
          </button>
        </form>
      )}

      <div className="mt-8 rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] p-5 shadow-[var(--shadow-sm)]">
        <h2 className="font-[family-name:var(--font-heading)] text-lg font-semibold">{t('staff.apply.trackTitle')}</h2>
        <form onSubmit={checkStatus} className="mt-3 flex flex-col sm:flex-row gap-3">
          <input className={inputClass} type="email" placeholder={t('staff.apply.email')} value={trackEmail} onChange={(e) => setTrackEmail(e.target.value)} required />
          <button type="submit" disabled={looking} className="rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] px-4 py-2.5 text-sm font-semibold text-[var(--color-text-secondary)] whitespace-nowrap disabled:opacity-50">
            {t('staff.apply.check')}
          </button>
        </form>
        {notFound && <p className="mt-3 text-sm text-[var(--color-text-muted)]">{t('staff.apply.notFound')}</p>}
        {statusResult && (
          <div className="mt-3 text-sm text-[var(--color-text-secondary)] flex flex-col gap-0.5">
            <span className="font-semibold text-[var(--color-text)]">{statusResult.fullName}</span>
            <span>{t('staff.dashboard.department')}: {t(labelKeyFor(DEPARTMENTS, statusResult.department))}</span>
            <span>{t(labelKeyFor(STAFF_STATUSES, statusResult.status))}</span>
            {statusResult.decisionReason && <span>{statusResult.decisionReason}</span>}
          </div>
        )}
      </div>

      <p className="mt-6 text-sm text-[var(--color-text-secondary)]">
        <Link to="/?view=staff" className="text-[var(--color-primary)] font-semibold">{t('staff.apply.signIn')}</Link>
      </p>
    </div>
  );
}
