import { useEffect, useRef, useState } from 'react';
import { Languages, Check } from 'lucide-react';
import { useLanguage } from '../context/LanguageContext';

/**
 * Language picker for the storefront.
 *
 * Shows each language in its own script (हिन्दी, Español) next to the English
 * name, because a shopper looking for their language scans for the script they
 * read, not for the name in English.
 *
 * Like the theme switcher this is a display preference rather than an account
 * feature, so it stays available to signed-out visitors too.
 */
export default function LanguageSwitcher() {
  const { locale, setLocale, languages, t } = useLanguage();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    const onKey = (e) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const current = languages.find((l) => l.code === locale) || languages[0];

  const choose = (code) => {
    setLocale(code);
    setOpen(false);
  };

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-label={t('nav.language')}
        title={t('nav.language')}
        aria-expanded={open}
        aria-haspopup="listbox"
        className="inline-flex items-center gap-2 px-2.5 py-2 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-[var(--color-card-bg)] hover:bg-[var(--color-card-bg-tint)] transition-colors"
      >
        <Languages size={16} className="text-[var(--color-text-secondary)]" />
        <span className="hidden sm:inline text-sm font-semibold text-[var(--color-text-secondary)]">
          {current.native}
        </span>
      </button>

      {open && (
        <div
          role="listbox"
          aria-label={t('nav.language')}
          className="absolute right-0 mt-2 w-48 rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] border-[1.5px] border-[var(--color-border)] shadow-[var(--shadow-lg)] p-1.5 z-[var(--z-dropdown)]"
        >
          {languages.map((l) => (
            <button
              key={l.code}
              type="button"
              role="option"
              aria-selected={locale === l.code}
              onClick={() => choose(l.code)}
              // The label is the language's own name, so it must not be
              // translated into the currently selected language.
              lang={l.code}
              className="w-full flex items-center gap-3 px-2.5 py-2 rounded-[var(--radius-md)] hover:bg-[var(--color-card-bg-tint)] transition-colors text-left"
            >
              <span className="flex-1">
                <span className="block text-sm font-medium text-[var(--color-text-primary)]">{l.native}</span>
                <span className="block text-[11px] text-[var(--color-text-muted)]">{l.label}</span>
              </span>
              {locale === l.code && <Check size={15} className="text-[var(--color-primary)]" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
