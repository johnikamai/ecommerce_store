import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Scale, X, Trash2, ArrowRight, ChevronDown, ChevronUp, Plus } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useCompare } from '../context/CompareContext';
import { useLanguage } from '../context/LanguageContext';
import ProductImage from './ProductImage';

export default function CompareDock() {
  const { ids, remove, clear, count, limit } = useCompare();
  const { t } = useLanguage();
  const location = useLocation();
  const [minimized, setMinimized] = useState(false);
  const [catalog, setCatalog] = useState([]);

  // Do not show the dock on the compare page itself to avoid duplication
  const isComparePage = location.pathname === '/compare';

  useEffect(() => {
    if (count === 0 || isComparePage) return;
    let cancelled = false;
    axiosClient
      .get('/products/catalog')
      .then((res) => {
        if (cancelled) return;
        const all = res.data?.products || res.data || [];
        setCatalog(all);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [count, isComparePage]);

  if (count === 0 || isComparePage) return null;

  const selectedProducts = ids
    .map((id) => catalog.find((p) => p.id === id) || { id, name: `Product #${id}`, price: '' })
    .slice(0, limit);

  const emptySlotsCount = Math.max(0, limit - selectedProducts.length);

  if (minimized) {
    return (
      <div className="fixed bottom-4 right-4 z-40 animate-in fade-in slide-in-from-bottom-2 duration-200">
        <button
          onClick={() => setMinimized(false)}
          className="flex items-center gap-2 px-4 py-2.5 rounded-full bg-[var(--color-primary)] text-white font-semibold text-xs shadow-lg hover:bg-[var(--color-primary-hover)] transition-all hover:scale-105"
          aria-label="Expand comparison dock"
        >
          <Scale className="w-4 h-4" />
           <span>{t('compare.dockTitle')} ({count}/{limit})</span>
          <ChevronUp className="w-3.5 h-3.5" />
        </button>
      </div>
    );
  }

  return (
    <aside
       aria-label={t('compare.dockTitle')}
      className="fixed bottom-0 left-0 right-0 z-40 bg-[var(--color-surface)]/95 backdrop-blur-md border-t border-[var(--color-border)] shadow-[0_-4px_20px_rgba(0,0,0,0.12)] transition-all duration-200"
    >
      <div className="max-w-6xl mx-auto px-4 py-3 flex flex-wrap items-center justify-between gap-3">
        {/* Header and status */}
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-full bg-[var(--color-primary)]/10 text-[var(--color-primary)] flex items-center justify-center font-bold">
            <Scale className="w-4 h-4" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold text-[var(--color-text-primary)]">
                {t('compare.dockTitle')}
              </span>
              <span className="text-xs px-2 py-0.5 rounded-full bg-[var(--color-card-bg-tint)] text-[var(--color-text-muted)] font-medium">
                {count}/{limit}
              </span>
            </div>
            <p className="text-[11px] text-[var(--color-text-muted)] hidden sm:block">
              {count < 2
                ? t('compare.addMoreHint', { limit })
                : t('compare.readyHint')}
            </p>
          </div>
        </div>

        {/* Selected Product Cards / Slots */}
        <div className="flex items-center gap-2 overflow-x-auto py-1 max-w-full">
          {selectedProducts.map((p) => (
            <div
              key={p.id}
              className="relative group flex items-center gap-2 p-1.5 pr-3 rounded-[var(--radius-md)] bg-[var(--color-card-bg)] border border-[var(--color-border)] shadow-sm shrink-0 min-w-[140px] max-w-[180px]"
            >
              <div className="w-10 h-10 rounded overflow-hidden bg-[var(--color-card-bg-tint)] shrink-0">
                <ProductImage product={p} alt={p.name} className="w-full h-full object-cover" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-xs font-medium text-[var(--color-text-primary)] truncate" title={p.name}>
                  {p.name}
                </p>
                {p.price && (
                  <p className="text-[11px] font-semibold text-[var(--color-primary)]">
                    ₹{p.price}
                  </p>
                )}
              </div>
              <button
                onClick={() => remove(p.id)}
                aria-label={`Remove ${p.name}`}
                title="Remove from comparison"
                className="p-1 rounded-full text-[var(--color-text-muted)] hover:text-[var(--color-error)] hover:bg-[var(--color-error-bg)] transition-colors"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          ))}

          {/* Empty placeholders */}
          {Array.from({ length: emptySlotsCount }).map((_, i) => (
            <Link
              key={`empty-${i}`}
              to="/products"
              title="Add another product"
              className="hidden md:flex items-center justify-center gap-1.5 h-12 px-3 rounded-[var(--radius-md)] border-2 border-dashed border-[var(--color-border)] text-[var(--color-text-muted)] hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] text-xs font-medium transition-colors shrink-0"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>{t('compare.addSlot', 'Add product')}</span>
            </Link>
          ))}
        </div>

        {/* Actions */}
        <div className="flex items-center gap-2 ml-auto">
          <button
            onClick={clear}
            className="flex items-center gap-1 px-2.5 py-1.5 text-xs text-[var(--color-text-muted)] hover:text-[var(--color-error)] transition-colors font-medium"
            title="Clear all selected products"
          >
            <Trash2 className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">{t('compare.clearAll', 'Clear all')}</span>
          </button>

          <Link
            to="/compare"
            className="flex items-center gap-1.5 px-4 py-2 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-xs font-semibold hover:bg-[var(--color-primary-hover)] shadow-sm transition-all hover:gap-2"
          >
            <span>{t('compare.compareNow', 'Compare Now')}</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </Link>

          <button
            onClick={() => setMinimized(true)}
            aria-label="Minimize comparison dock"
            title="Minimize"
            className="p-1.5 rounded text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] hover:bg-[var(--color-card-bg-tint)] transition-colors"
          >
            <ChevronDown className="w-4 h-4" />
          </button>
        </div>
      </div>
    </aside>
  );
}
