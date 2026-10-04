import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { DICTIONARIES, DEFAULT_LOCALE, LANGUAGES } from '../i18n/dictionaries';

const STORAGE_KEY = 'shopease.locale';

const LanguageContext = createContext(null);

/**
 * Picks the initial locale: an explicit previous choice wins, otherwise match
 * the browser's language, otherwise English. Anything we do not have a
 * dictionary for is ignored rather than shown half-translated.
 */
function detectLocale() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved && DICTIONARIES[saved]) return saved;
  } catch {
    // private mode / disabled storage: fall through to browser detection
  }

  const candidates = typeof navigator !== 'undefined'
    ? [navigator.language, ...(navigator.languages || [])]
    : [];

  for (const tag of candidates) {
    const base = String(tag || '').slice(0, 2).toLowerCase();
    if (DICTIONARIES[base]) return base;
  }
  return DEFAULT_LOCALE;
}

export function LanguageProvider({ children }) {
  const [locale, setLocale] = useState(detectLocale);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, locale);
    } catch {
      // Preference simply will not persist; the session still works.
    }
    // Tells screen readers and the browser which language the chrome is in.
    document.documentElement.lang = locale;
  }, [locale]);

  const value = useMemo(() => {
    const dict = DICTIONARIES[locale] || DICTIONARIES[DEFAULT_LOCALE];

    /**
     * Looks up a key, falling back to English and then to the key itself.
     * Returning the key rather than an empty string makes a missing
     * translation obvious in the UI instead of silently blank.
     */
    const t = (key, vars) => {
      let text = dict[key] ?? DICTIONARIES[DEFAULT_LOCALE][key] ?? key;
      if (vars) {
        for (const [name, replacement] of Object.entries(vars)) {
          text = text.replaceAll(`{${name}}`, String(replacement));
        }
      }
      return text;
    };

    // en-IN keeps the Indian digit grouping (12,34,567) in every language,
    // because the storefront prices in rupees regardless of the UI locale.
    const numberLocale = 'en-IN';

    const formatCurrency = (value) => {
      const amount = Number(value);
      if (!Number.isFinite(amount)) return `₹${value ?? ''}`;
      return new Intl.NumberFormat(numberLocale, {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 0,
      }).format(amount);
    };

    const formatNumber = (value) => {
      const amount = Number(value);
      return Number.isFinite(amount) ? new Intl.NumberFormat(numberLocale).format(amount) : String(value ?? '');
    };

    const formatDate = (value) => {
      if (!value) return '';
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) return String(value);
      return new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(date);
    };

    const formatDateTime = (value) => {
      if (!value) return '';
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) return String(value);
      return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
    };

    return {
      locale,
      setLocale,
      t,
      formatCurrency,
      formatNumber,
      formatDate,
      formatDateTime,
      languages: LANGUAGES,
    };
  }, [locale]);

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
}

export function useLanguage() {
  const ctx = useContext(LanguageContext);
  if (!ctx) {
    throw new Error('useLanguage must be used inside <LanguageProvider>');
  }
  return ctx;
}

/**
 * Convenience wrapper for components that only need the lookup function, so
 * they do not have to destructure the whole context.
 */
export function useT() {
  return useLanguage().t;
}
