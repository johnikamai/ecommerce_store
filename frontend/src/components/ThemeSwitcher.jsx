import { useEffect, useRef, useState } from 'react';
import { Palette, Check } from 'lucide-react';

const STORAGE_KEY = 'shopease-theme';
const DEFAULT_THEME = 'violet';

/**
 * The swatch shows each theme's real primary, secondary and background rather
 * than a generic colour chip, so the choice is recognisable before clicking.
 */
const THEMES = [
  { id: 'violet', name: 'Lavender', primary: '#7C6AE8', secondary: '#F6A6C1', bg: '#F8F6FB' },
  { id: 'midnight', name: 'Midnight', primary: '#6D8BFF', secondary: '#7BD0E8', bg: '#10131C' },
  { id: 'forest', name: 'Forest', primary: '#2F855A', secondary: '#9CC98B', bg: '#F3F7F2' },
  { id: 'sunset', name: 'Sunset', primary: '#E2603F', secondary: '#F2A65A', bg: '#FDF6F2' },
  { id: 'mono', name: 'Mono', primary: '#171717', secondary: '#737373', bg: '#FAFAFA' },
  { id: 'ocean', name: 'Ocean', primary: '#0E7490', secondary: '#5EEAD4', bg: '#F0F9FB' },
];

function applyTheme(id) {
  if (id === DEFAULT_THEME) {
    // Removing the attribute falls back to the @theme defaults rather than
    // leaving a copy of the lavender palette behind to drift out of sync.
    document.documentElement.removeAttribute('data-theme');
  } else {
    document.documentElement.setAttribute('data-theme', id);
  }
}

/**
 * Colour scheme picker for the storefront.
 *
 * The theme is applied by setting a data attribute on <html> and restoring from
 * localStorage on boot, so the choice survives reloads without a flash of the
 * wrong palette being a problem - the attribute is set before first paint by the
 * inline script in index.html.
 */
export default function ThemeSwitcher() {
  const [open, setOpen] = useState(false);
  const [theme, setTheme] = useState(DEFAULT_THEME);
  const ref = useRef(null);

  useEffect(() => {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved && THEMES.some((t) => t.id === saved)) {
      setTheme(saved);
      applyTheme(saved);
    }
  }, []);

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

  const choose = (id) => {
    setTheme(id);
    localStorage.setItem(STORAGE_KEY, id);
    applyTheme(id);
    setOpen(false);
  };

  const current = THEMES.find((t) => t.id === theme) || THEMES[0];

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-label="Change colour theme"
        aria-expanded={open}
        aria-haspopup="listbox"
        className="inline-flex items-center gap-2 px-2.5 py-2 rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-[var(--color-card-bg)] hover:bg-[var(--color-card-bg-tint)] transition-colors"
      >
        <span
          aria-hidden="true"
          className="w-4 h-4 rounded-full border border-[var(--color-border)]"
          style={{ background: `linear-gradient(135deg, ${current.primary} 50%, ${current.secondary} 50%)` }}
        />
        <span className="hidden sm:inline text-sm font-semibold text-[var(--color-text-secondary)]">
          {current.name}
        </span>
      </button>

      {open && (
        <div
          role="listbox"
          aria-label="Colour theme"
          className="absolute right-0 mt-2 w-56 rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] border-[1.5px] border-[var(--color-border)] shadow-[var(--shadow-lg)] p-1.5 z-[var(--z-dropdown)]"
        >
          {THEMES.map((t) => (
            <button
              key={t.id}
              type="button"
              role="option"
              aria-selected={theme === t.id}
              onClick={() => choose(t.id)}
              className="w-full flex items-center gap-3 px-2.5 py-2 rounded-[var(--radius-md)] hover:bg-[var(--color-card-bg-tint)] transition-colors text-left"
            >
              <span
                aria-hidden="true"
                className="w-7 h-7 rounded-full shrink-0 border border-[var(--color-border)]"
                style={{ background: `linear-gradient(135deg, ${t.primary} 50%, ${t.secondary} 50%)` }}
              />
              <span className="flex-1 text-sm font-medium text-[var(--color-text-primary)]">{t.name}</span>
              {theme === t.id && <Check size={15} className="text-[var(--color-primary)]" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export { THEMES, STORAGE_KEY, DEFAULT_THEME, applyTheme };
