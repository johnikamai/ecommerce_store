import { clearSession } from '../utils/session';
import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  LogIn, UserPlus, Sparkles, ShieldCheck, KeyRound, Eye, EyeOff,
  ShoppingBag, Heart, Award, Mail, Check,
} from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useLanguage } from '../context/LanguageContext';

/**
 * Normalises an auth failure into something renderable.
 *
 * A missing response means the request never reached the API, so the wording is
 * ours. Otherwise the server's own string is passed through untouched: backend
 * messages are not translated, and t() returns an unknown key verbatim, so the
 * same value can be either a key or a plain message at the call site.
 */
function authError(err, fallbackKey = 'auth.error.generic') {
  if (!err?.response) return 'auth.error.offline';
  const msg = err?.response?.data;
  return typeof msg === 'string' ? msg : fallbackKey;
}

const persist = (data) => {
  localStorage.setItem('token', data.token);
  localStorage.setItem('role', data.role);
  localStorage.setItem('username', data.username);
  if (data.customerId) localStorage.setItem('customerId', String(data.customerId));
  if (data.customerName) localStorage.setItem('customerName', data.customerName);
  window.dispatchEvent(new Event('session-change'));
};

const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

// Shared, branded password field: eye toggle to reveal + optional strength checklist.
function PasswordInput({ id, value, onChange, placeholder, autoComplete, showStrength }) {
  const { t } = useLanguage();
  const [visible, setVisible] = useState(false);

  const checks = {
    len: value.length >= 8,
    lower: /[a-z]/.test(value),
    upper: /[A-Z]/.test(value),
    digit: /\d/.test(value),
    special: /[^A-Za-z0-9]/.test(value),
  };
  const passed = Object.values(checks).filter(Boolean).length;
  // A map rather than t(`auth.pw.${...}`) so the keys stay statically visible
  // to scripts/check-i18n.mjs.
  const strengthKey = passed >= 5 ? 'auth.pw.strong' : passed >= 3 ? 'auth.pw.medium' : 'auth.pw.weak';
  const strengthColor = passed >= 5 ? 'text-[var(--color-success)]' : passed >= 3 ? 'text-[var(--color-warning)]' : 'text-[var(--color-error)]';

  const rows = [
    { key: 'len', label: t('auth.pw.len') },
    { key: 'upper', label: t('auth.pw.upper') },
    { key: 'lower', label: t('auth.pw.lower') },
    { key: 'digit', label: t('auth.pw.digit') },
    { key: 'special', label: t('auth.pw.special') },
  ];

  return (
    <div>
      <div className="relative">
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          autoComplete={autoComplete}
          className="w-full px-4 py-3 pr-12 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] focus:shadow-[var(--shadow-glow-primary)] outline-none transition-shadow"
        />
        <button
          type="button"
          onClick={() => setVisible(!visible)}
          aria-label={visible ? t('auth.pw.hide') : t('auth.pw.show')}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] hover:text-[var(--color-primary)] transition-colors"
        >
          {visible ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </div>

      {showStrength && value.length > 0 && (
        <div className="mt-2.5 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] p-3 space-y-1">
          <div className="flex items-center justify-between text-xs font-semibold">
            <span className="text-[var(--color-text-muted)]">{t('auth.pw.strength')}</span>
            <span className={strengthColor}>{t(strengthKey)}</span>
          </div>
          <div className="flex gap-1">
            {[1, 2, 3, 4, 5].map((i) => (
              <span
                key={i}
                className={`h-1 flex-1 rounded-full transition-colors ${
                  i <= passed
                    ? passed >= 5 ? 'bg-[var(--color-success)]' : passed >= 3 ? 'bg-[var(--color-warning)]' : 'bg-[var(--color-error)]'
                    : 'bg-[var(--color-border)]'
                }`}
              />
            ))}
          </div>
          <ul className="grid grid-cols-1 sm:grid-cols-2 gap-x-3 gap-y-0.5 pt-1">
            {rows.map((r) => (
              <li key={r.key} className={`flex items-center gap-1.5 text-xs ${checks[r.key] ? 'text-[var(--color-success)] font-medium' : 'text-[var(--color-text-muted)]'}`}>
                <Check size={12} className={checks[r.key] ? 'opacity-100' : 'opacity-25'} />
                {r.label}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function BrandPanel() {
  const { t } = useLanguage();
  return (
    <div className="hidden md:flex flex-col gap-5">
      <span className="inline-flex items-center gap-1.5 w-fit rounded-full bg-white/70 px-3 py-1 text-xs font-semibold text-[var(--color-primary)]">
        <Sparkles size={14} /> ShopEase
      </span>
      <h1 className="font-[family-name:var(--font-heading)] text-[40px] leading-[1.15] font-bold text-[var(--color-text-primary)]">
        {t('auth.brand.line1')}
        <br />
        {t('auth.brand.line2')}
        <br />
        <span className="bg-[linear-gradient(135deg,#7C6AE8,#F6A6C1)] bg-clip-text text-transparent">{t('auth.brand.line3')}</span>
      </h1>
      <p className="text-[var(--color-text-secondary)] max-w-xs">
        {t('auth.brand.sub')}
      </p>
      <ul className="space-y-2.5 text-sm text-[var(--color-text-secondary)]">
        <li className="flex items-center gap-2.5"><span className="w-7 h-7 rounded-full bg-white shadow-[var(--shadow-sm)] flex items-center justify-center text-[var(--color-primary)]"><ShoppingBag size={14} /></span> {t('auth.brand.point1')}</li>
        <li className="flex items-center gap-2.5"><span className="w-7 h-7 rounded-full bg-white shadow-[var(--shadow-sm)] flex items-center justify-center text-[var(--color-secondary)]"><Heart size={14} /></span> {t('auth.brand.point2')}</li>
        <li className="flex items-center gap-2.5"><span className="w-7 h-7 rounded-full bg-white shadow-[var(--shadow-sm)] flex items-center justify-center text-[var(--color-accent)]"><Award size={14} /></span> {t('auth.brand.point3')}</li>
      </ul>
    </div>
  );
}

function AdminContent() {
  const { t } = useLanguage();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();
  const errorRef = useRef(null);

  useEffect(() => {
    if (error) errorRef.current?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, [error]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!username.trim() || !password) {
      setError('auth.credentialsRequired');
      return;
    }
    setBusy(true);
    try {
      const res = await axiosClient.post('/auth/login', { username, password });
      if (res.data.role !== 'ADMIN' && res.data.role !== 'STAFF') {
        setError('auth.error.notStaff');
        return;
      }
      persist(res.data);
      navigate('/admin');
    } catch (err) {
      if (mode === 'register' && err.response?.status === 503) setStep('otp');
      setError(authError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="flex items-center gap-3 mb-5">
        <span className="w-11 h-11 rounded-[var(--radius-lg)] bg-gradient-premium flex items-center justify-center text-white shadow-md">
          <ShieldCheck size={20} />
        </span>
        <div>
          <h3 className="font-[family-name:var(--font-heading)] text-lg font-bold text-[var(--color-text-primary)]">{t('auth.admin.title')}</h3>
          <p className="text-xs text-[var(--color-text-muted)]">{t('auth.admin.sub')}</p>
        </div>
      </div>

      <div>
        <label htmlFor="admin-user" className="block text-[13px] font-semibold mb-2 text-[var(--color-text-secondary)]">{t('auth.username')}</label>
        <input
          id="admin-user"
          type="text"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="admin1"
          autoComplete="username"
          className="w-full px-4 py-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] focus:shadow-[var(--shadow-glow-primary)] outline-none transition-shadow"
        />
      </div>
      <div>
        <label htmlFor="admin-pass" className="block text-[13px] font-semibold mb-2 text-[var(--color-text-secondary)]">{t('auth.password')}</label>
        <PasswordInput
          id="admin-pass"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="••••••••"
          autoComplete="current-password"
        />
      </div>

      {error && (
        <p ref={errorRef} className="text-sm text-[var(--color-error)] bg-[var(--color-error-bg)] rounded-[var(--radius-md)] px-4 py-2.5" role="alert">{t(error)}</p>
      )}

      <button
        type="submit"
        disabled={busy}
        className="w-full min-h-[48px] rounded-[var(--radius-md)] bg-gradient-premium text-white text-sm font-semibold shadow-[var(--shadow-sm)] hover:shadow-[var(--shadow-md)] hover:-translate-y-0.5 transition-all disabled:opacity-60 disabled:cursor-not-allowed"
      >
        <span className="inline-flex items-center justify-center gap-2">
          <ShieldCheck size={16} />
          {busy ? t('auth.pleaseWait') : t('auth.admin.signIn')}
        </span>
      </button>
    </form>
  );
}

function CustomerContent() {
  const { t } = useLanguage();
  const [mode, setMode] = useState('login'); // 'login' | 'register'
  const [step, setStep] = useState('form'); // 'form' | 'otp'
  const [resetMode, setResetMode] = useState(false);
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [devOtp, setDevOtp] = useState('');
  const [emailSentTo, setEmailSentTo] = useState('');
  const [emailDelivered, setEmailDelivered] = useState(false);
  // Pre-filled from ?ref=CODE so the invite link works, and still editable in
  // case the code was passed on by word of mouth.
  const [referralCode, setReferralCode] = useState(() =>
    new URLSearchParams(window.location.search).get('ref') || ''
  );
  const [otp, setOtp] = useState(['', '', '', '', '', '']);
  const [otpError, setOtpError] = useState('');
  const otpRefs = useRef([]);
  const errorRef = useRef(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (step === 'otp') otpRefs.current[0]?.focus();
  }, [step]);

  useEffect(() => {
    if (error) errorRef.current?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, [error]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!username.trim() || !password) {
      setError('auth.credentialsRequired');
      return;
    }
    if (mode === 'register') {
      if (!EMAIL_RE.test(email)) {
        setError('auth.error.invalidEmail');
        return;
      }
      const checks = [
        password.length >= 8,
        /[a-z]/.test(password),
        /[A-Z]/.test(password),
        /\d/.test(password),
        /[^A-Za-z0-9]/.test(password),
      ];
      if (!checks.every(Boolean)) {
        setError('auth.error.passwordWeak');
        return;
      }
    }
    setBusy(true);
    try {
      if (mode === 'login') {
        const res = await axiosClient.post('/auth/login', { username, password });
        if (res.data.role === 'ADMIN' || res.data.role === 'STAFF') {
          setError('auth.error.isStaff');
          return;
        }
        persist(res.data);
        navigate('/products');
      } else {
        const res = await axiosClient.post('/auth/register', { username, email, password });
        setDevOtp(res.data.devOtp || '');
        setEmailSentTo(res.data.emailSentTo || email);
        setEmailDelivered(!!res.data.emailDelivered);
        setStep('otp');
      }
    } catch (err) {
      if (mode === 'register' && err.response?.status === 503) setStep('otp');
      setError(authError(err));
    } finally {
      setBusy(false);
    }
  };

  const handleOtpChange = (i, value) => {
    const digit = value.replace(/\D/g, '');
    const next = [...otp];
    next[i] = digit;
    setOtp(next);
    if (digit && i < 5) otpRefs.current[i + 1]?.focus();
  };

  const handleOtpKeyDown = (i, e) => {
    if (e.key === 'Backspace' && !otp[i] && i > 0) otpRefs.current[i - 1]?.focus();
  };

  const handleVerify = async () => {
    const code = otp.join('');
    if (code.length !== 6) {
      setOtpError('auth.error.otpRequired');
      return;
    }
    setBusy(true);
    setOtpError('');
    try {
      const res = await axiosClient.post('/auth/verify-otp', {
        username,
        otp: code,
        // Optional. An unknown code is ignored server-side, so this is safe to
        // send even if the friend mistyped it.
        referralCode: referralCode.trim() || undefined,
      });
      persist(res.data);
      navigate('/products');
    } catch (err) {
      setOtpError(authError(err, 'auth.error.verifyFailed'));
    } finally {
      setBusy(false);
    }
  };

  const handleResend = async () => {
    try {
      const res = await axiosClient.post('/auth/resend-otp', { username });
      setEmailDelivered(!!res.data.emailDelivered);
      setDevOtp(res.data.devOtp || '');
      setOtp(['', '', '', '', '', '']);
      setOtpError('');
      otpRefs.current[0]?.focus();
    } catch (err) {
      setOtpError(authError(err, 'auth.error.resendFailed'));
    }
  };

  const headerIcon = step === 'otp'
    ? <KeyRound size={20} />
    : mode === 'register'
      ? <UserPlus size={20} />
      : <LogIn size={20} />;

  const headerText = step === 'otp'
    ? t('auth.otp.title')
    : mode === 'register'
      ? t('auth.register.title')
      : t('auth.login.title');

  const headerSub = step === 'otp'
    ? t('auth.otp.sub', { email: emailSentTo || email || username })
    : mode === 'register'
      ? t('auth.register.sub')
      : t('auth.login.sub');

  if (resetMode) {
    return <ForgotPasswordForm onBack={() => setResetMode(false)} />;
  }

  return (
    <form
      onSubmit={(e) => { e.preventDefault(); if (step === 'otp') handleVerify(); else handleSubmit(e); }}
      className="space-y-4"
    >
      <div className="flex items-center gap-3 mb-5">
        <span className="w-11 h-11 rounded-[var(--radius-lg)] bg-gradient-promo flex items-center justify-center text-[var(--color-secondary)] shadow-md">
          {headerIcon}
        </span>
        <div>
          <h3 className="font-[family-name:var(--font-heading)] text-lg font-bold text-[var(--color-text-primary)]">{headerText}</h3>
          <p className="text-xs text-[var(--color-text-muted)]">{headerSub}</p>
        </div>
      </div>

      {step === 'form' ? (
        <>
          <div className="flex rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] p-1">
            {(['login', 'register']).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => { setMode(m); setError(''); }}
                className={`flex-1 py-2.5 rounded-[var(--radius-full)] text-sm font-semibold transition-all ${
                  mode === m
                    ? 'bg-white shadow-[var(--shadow-sm)] text-[var(--color-primary)]'
                    : 'text-[var(--color-text-muted)] hover:text-[var(--color-text-secondary)]'
                }`}
              >
                {m === 'login' ? t('auth.tab.login') : t('auth.tab.register')}
              </button>
            ))}
          </div>

          {mode === 'login' && (
            <p className="text-center text-xs text-[var(--color-text-muted)] -mt-1">
              {t('auth.noAccountBefore')}{' '}
              <span className="font-semibold text-[var(--color-primary)]">{t('auth.tab.register')}</span>{' '}
              {t('auth.noAccountAfter')}
            </p>
          )}

          <div>
            <label htmlFor="cust-user" className="block text-[13px] font-semibold mb-2 text-[var(--color-text-secondary)]">{t('auth.usernameOrEmail')}</label>
            <input
              id="cust-user"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder={mode === 'login' ? t('auth.usernamePlaceholderLogin') : t('auth.usernamePlaceholderRegister')}
              autoComplete="username"
              className="w-full px-4 py-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] focus:shadow-[var(--shadow-glow-primary)] outline-none transition-shadow"
            />
          </div>

          {mode === 'register' && (
            <div>
              <label htmlFor="cust-email" className="block text-[13px] font-semibold mb-2 text-[var(--color-text-secondary)]">{t('auth.email')}</label>
              <div className="relative">
                <Mail size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)]" />
                <input
                  id="cust-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  autoComplete="email"
                  className="w-full pl-10 pr-4 py-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] focus:shadow-[var(--shadow-glow-primary)] outline-none transition-shadow"
                />
              </div>
              <p className="text-xs text-[var(--color-text-muted)] mt-1.5">{t('auth.emailOtpNote')}</p>
            </div>
          )}

          <div>
            <label htmlFor="cust-pass" className="block text-[13px] font-semibold mb-2 text-[var(--color-text-secondary)]">{t('auth.password')}</label>
            <PasswordInput
              id="cust-pass"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              showStrength={mode === 'register'}
            />
          </div>

          {mode === 'register' && (
            <div>
              <label htmlFor="cust-ref" className="block text-[13px] font-semibold mb-2 text-[var(--color-text-secondary)]">
                {t('auth.referralCode')} <span className="font-normal text-[var(--color-text-muted)]">({t('auth.optional')})</span>
              </label>
              <input
                id="cust-ref"
                type="text"
                value={referralCode}
                onChange={(e) => setReferralCode(e.target.value.toUpperCase())}
                placeholder="JOHN-A1B2C3"
                autoComplete="off"
                className="w-full min-h-[48px] rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-4 text-sm focus:outline-none focus:border-[var(--color-primary)]"
              />
              <p className="mt-1.5 text-xs text-[var(--color-text-muted)]">
                {t('auth.referralNote')}
              </p>
            </div>
          )}

          {error && (
            <p ref={errorRef} className="text-sm text-[var(--color-error)] bg-[var(--color-error-bg)] rounded-[var(--radius-md)] px-4 py-2.5" role="alert">{t(error)}</p>
          )}

          <button
            type="submit"
            className="w-full min-h-[48px] rounded-[var(--radius-md)] bg-gradient-premium text-white text-sm font-semibold shadow-[var(--shadow-sm)] hover:shadow-[var(--shadow-md)] hover:-translate-y-0.5 transition-all disabled:opacity-60 disabled:cursor-not-allowed"
          >
            <span className="inline-flex items-center justify-center gap-2">
              {mode === 'login' ? <LogIn size={16} /> : <UserPlus size={16} />}
              {busy
                ? t('auth.pleaseWait')
                : mode === 'login'
                  ? t('auth.tab.login')
                  : t('auth.tab.register')}
            </span>
          </button>

          {mode === 'login' && (
            <div className="text-center">
              <button
                type="button"
                onClick={() => setResetMode(true)}
                className="text-sm font-semibold text-[var(--color-primary)] hover:underline"
              >
                {t('auth.forgotPassword')}
              </button>
            </div>
          )}
        </>
      ) : (
        <>
{emailDelivered ? (
          <p className="text-sm bg-[var(--color-info-bg)] text-[var(--color-info)] rounded-[var(--radius-md)] px-4 py-2.5">
            {t('auth.otp.sentBefore')}{' '}
            <span className="font-semibold">{emailSentTo}</span>{' '}
            {t('auth.otp.sentAfter')}
          </p>
        ) : devOtp ? (
          <p className="text-sm bg-[var(--color-warning-bg)] text-[var(--color-warning)] rounded-[var(--radius-md)] px-4 py-2.5">
            {t('auth.otp.demo')}{' '}
            <span className="font-bold tracking-[0.2em]">{devOtp}</span>
          </p>
        ) : null}

          <div className="flex justify-center gap-2">
            {otp.map((digit, i) => (
              <input
                key={i}
                ref={(el) => { otpRefs.current[i] = el; }}
                type="text"
                inputMode="numeric"
                maxLength={1}
                value={digit}
                onChange={(e) => handleOtpChange(i, e.target.value)}
                onKeyDown={(e) => handleOtpKeyDown(i, e)}
                aria-label={t('auth.otp.digitLabel', { n: i + 1 })}
                className="w-12 h-14 text-center text-xl font-bold rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white text-[var(--color-text-primary)] outline-none focus:border-[var(--color-primary)] focus:shadow-[var(--shadow-glow-primary)] transition-shadow"
              />
            ))}
          </div>

          {otpError && (
            <p className="text-sm text-[var(--color-error)] bg-[var(--color-error-bg)] rounded-[var(--radius-md)] px-4 py-2.5 text-center" role="alert">{t(otpError)}</p>
          )}

          <button
            type="submit"
            className="w-full min-h-[48px] rounded-[var(--radius-md)] bg-gradient-premium text-white text-sm font-semibold shadow-[var(--shadow-sm)] hover:shadow-[var(--shadow-md)] hover:-translate-y-0.5 transition-all disabled:opacity-60 disabled:cursor-not-allowed"
          >
            <span className="inline-flex items-center justify-center gap-2">
              <KeyRound size={16} />
              {busy ? t('auth.otp.verifying') : t('auth.otp.verify')}
            </span>
          </button>

          <div className="flex items-center justify-center gap-4 text-xs">
            <button type="button" onClick={handleResend} className="text-[var(--color-primary)] font-semibold hover:underline">
              {t('auth.otp.resend')}
            </button>
            <button
              type="button"
              onClick={() => { setStep('form'); setOtp(['', '', '', '', '', '']); setOtpError(''); }}
              className="text-[var(--color-text-muted)] hover:text-[var(--color-text-secondary)]"
            >
              {t('auth.otp.changeDetails')}
            </button>
          </div>
        </>
      )}
    </form>
  );
}

function ForgotPasswordForm({ onBack }) {
  const { t } = useLanguage();
  const [step, setStep] = useState('request'); // 'request' | 'reset' | 'done'
  const [identifier, setIdentifier] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [devOtp, setDevOtp] = useState('');
  const [emailSentTo, setEmailSentTo] = useState('');
  const [emailDelivered, setEmailDelivered] = useState(false);
  const errorRef = useRef(null);

  useEffect(() => {
    if (error) errorRef.current?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, [error]);

  const handleRequest = async (e) => {
    e.preventDefault();
    setError('');
    if (!identifier.trim()) {
      setError('auth.reset.identifierRequired');
      return;
    }
    setBusy(true);
    try {
      const res = await axiosClient.post('/auth/forgot-password', { username: identifier });
      setDevOtp(res.data.devOtp || '');
      setEmailSentTo(res.data.emailSentTo || identifier);
      setEmailDelivered(!!res.data.emailDelivered);
      setStep('reset');
    } catch (err) {
      if (mode === 'register' && err.response?.status === 503) setStep('otp');
      setError(authError(err));
    } finally {
      setBusy(false);
    }
  };

  const handleReset = async (e) => {
    e.preventDefault();
    setError('');
    if (!/^\d{6}$/.test(otp)) {
      setError('auth.error.otpRequired');
      return;
    }
    setBusy(true);
    try {
      await axiosClient.post('/auth/reset-password', { username: identifier, otp, newPassword });
      setStep('done');
    } catch (err) {
      if (mode === 'register' && err.response?.status === 503) setStep('otp');
      setError(authError(err));
    } finally {
      setBusy(false);
    }
  };

  const btnClass = 'w-full min-h-[48px] rounded-[var(--radius-md)] bg-gradient-premium text-white text-sm font-semibold shadow-[var(--shadow-sm)] hover:shadow-[var(--shadow-md)] hover:-translate-y-0.5 transition-all disabled:opacity-60 disabled:cursor-not-allowed';
  const errClass = 'text-sm text-[var(--color-error)] bg-[var(--color-error-bg)] rounded-[var(--radius-md)] px-4 py-2.5';

  return (
    <>
      <div className="flex items-center gap-3 mb-5">
        <span className="w-11 h-11 rounded-[var(--radius-lg)] bg-gradient-promo flex items-center justify-center text-[var(--color-secondary)] shadow-md">
          <KeyRound size={20} />
        </span>
        <div>
          <h3 className="font-[family-name:var(--font-heading)] text-lg font-bold text-[var(--color-text-primary)]">{t('auth.reset.title')}</h3>
          <p className="text-xs text-[var(--color-text-muted)]">{t('auth.reset.sub')}</p>
        </div>
      </div>

      {step === 'request' && (
        <form onSubmit={handleRequest} className="space-y-4">
          <div>
            <label htmlFor="reset-id" className="block text-[13px] font-semibold mb-2 text-[var(--color-text-secondary)]">{t('auth.usernameOrEmail')}</label>
            <input
              id="reset-id"
              type="text"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              placeholder={t('auth.reset.identifierPlaceholder')}
              autoComplete="username"
              className="w-full px-4 py-3 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white focus:border-[var(--color-primary)] focus:shadow-[var(--shadow-glow-primary)] outline-none transition-shadow"
            />
          </div>
          {error && <p ref={errorRef} className={errClass} role="alert">{t(error)}</p>}
          <button type="submit" disabled={busy} className={btnClass}>
            <span className="inline-flex items-center justify-center gap-2">
              <KeyRound size={16} />
              {busy ? t('auth.pleaseWait') : t('auth.reset.send')}
            </span>
          </button>
          <button type="button" onClick={onBack} className="w-full text-center text-xs text-[var(--color-text-muted)] hover:text-[var(--color-text-secondary)]">
            {t('auth.backToSignIn')}
          </button>
        </form>
      )}

      {step === 'reset' && (
        <form onSubmit={handleReset} className="space-y-4">
          {devOtp && (
            <p className="text-sm bg-[var(--color-warning-bg)] text-[var(--color-warning)] rounded-[var(--radius-md)] px-4 py-2.5">
              {emailDelivered
                ? t('auth.reset.emailedCode', { email: emailSentTo || t('auth.reset.yourInbox') })
                : t('auth.reset.demoMode')}
              {' '}<span className="font-bold tracking-[0.2em]">{devOtp}</span>
            </p>
          )}
          <div>
            <label htmlFor="reset-otp" className="block text-[13px] font-semibold mb-2 text-[var(--color-text-secondary)]">{t('auth.reset.codeLabel')}</label>
            <input
              id="reset-otp"
              type="text"
              inputMode="numeric"
              value={otp}
              onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder="000000"
              className="w-full px-4 py-3 tracking-[0.4em] text-center text-lg font-bold rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-white text-[var(--color-text-primary)] outline-none focus:border-[var(--color-primary)] focus:shadow-[var(--shadow-glow-primary)] transition-shadow"
            />
          </div>
          <div>
            <label htmlFor="reset-pass" className="block text-[13px] font-semibold mb-2 text-[var(--color-text-secondary)]">{t('auth.reset.newPassword')}</label>
            <PasswordInput id="reset-pass" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="••••••••" autoComplete="new-password" showStrength />
          </div>
          {error && <p ref={errorRef} className={errClass} role="alert">{t(error)}</p>}
          <button type="submit" disabled={busy} className={btnClass}>
            <span className="inline-flex items-center justify-center gap-2">
              <KeyRound size={16} />
              {busy ? t('auth.pleaseWait') : t('auth.reset.submit')}
            </span>
          </button>
          <button type="button" onClick={onBack} className="w-full text-center text-xs text-[var(--color-text-muted)] hover:text-[var(--color-text-secondary)]">
            {t('auth.backToSignIn')}
          </button>
        </form>
      )}

      {step === 'done' && (
        <div className="space-y-4">
          <p className="text-sm text-[var(--color-success)] bg-[var(--color-success-bg)] rounded-[var(--radius-md)] px-4 py-2.5" role="alert">
            {t('auth.reset.success')}
          </p>
          <button type="button" onClick={onBack} className={btnClass}>
            <span className="inline-flex items-center justify-center gap-2">
              <LogIn size={16} />
              {t('auth.backToSignIn')}
            </span>
          </button>
        </div>
      )}
    </>
  );
}

function Login() {
  const { t } = useLanguage();
  const [view, setView] = useState('customer'); // 'customer' | 'admin'

  // Reaching the login page always means "not signed in" — clear any leftover
  // token/identity from earlier sessions so it can't be sent with the login
  // request and break it (a stale token 403s auth endpoints).
  useEffect(() => {
    clearSession();
  }, []);

  return (
    <div className="relative min-h-[calc(100vh-76px)] overflow-hidden flex items-center justify-center px-4 py-10">
      {/* Decorative background */}
      <div className="pointer-events-none absolute -top-24 -left-24 w-[420px] h-[420px] rounded-full bg-gradient-premium opacity-20 blur-[90px]" />
      <div className="pointer-events-none absolute -bottom-32 -right-20 w-[460px] h-[460px] rounded-full bg-gradient-promo opacity-40 blur-[100px]" />
      <div className="pointer-events-none absolute top-1/3 right-1/4 w-72 h-72 rounded-full bg-[var(--color-info-bg)] opacity-50 blur-[80px]" />

      <div className="relative w-full max-w-4xl grid md:grid-cols-[1fr_1.15fr] gap-10 items-center">
        <BrandPanel />

        <div className="rounded-[var(--radius-xl)] bg-[var(--color-surface)] border-[1.5px] border-[var(--color-border)] shadow-[var(--shadow-lg)] p-6 sm:p-8">
          <div className="flex rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] p-1 mb-6">
            {(['customer', 'admin']).map((v) => (
              <button
                key={v}
                type="button"
                onClick={() => setView(v)}
                className={`flex-1 py-2.5 rounded-[var(--radius-full)] text-sm font-semibold transition-all ${
                  view === v
                    ? 'bg-white shadow-[var(--shadow-sm)] text-[var(--color-primary)]'
                    : 'text-[var(--color-text-muted)] hover:text-[var(--color-text-secondary)]'
                }`}
              >
                {v === 'customer' ? t('auth.view.customer') : t('auth.view.admin')}
              </button>
            ))}
          </div>

          {view === 'admin' ? <AdminContent /> : <CustomerContent />}


        </div>
      </div>
    </div>
  );
}

export default Login;