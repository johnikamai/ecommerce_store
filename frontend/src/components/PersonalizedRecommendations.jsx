import { useEffect, useState } from 'react';
import { Sparkles } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import ProductCard from './ProductCard';
import { useLanguage } from '../context/LanguageContext';

/**
 * "Picked for you" rail.
 *
 * Backed by GET /api/recommendations/personalized, which ranks the catalog
 * against the signed-in shopper's own category spend, price band and the
 * products they have already bought. Already-bought and out-of-stock items are
 * filtered server-side.
 *
 * Renders nothing at all when the shopper has no purchase history yet: an empty
 * personalised rail would just advertise that we know nothing about them, and
 * the catalog below it is a better use of the space.
 */
export default function PersonalizedRecommendations({ limit = 4, headingClass = '' }) {
  const { t } = useLanguage();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    axiosClient
      .get(`/recommendations/personalized?limit=${limit}`)
      .then((res) => {
        if (active) setItems(Array.isArray(res.data) ? res.data : []);
      })
      .catch(() => {
        // A failed recommendation call must never break the page it sits on.
        if (active) setItems([]);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [limit]);

  if (loading) {
    return (
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-5 animate-pulse">
        {Array.from({ length: limit }).map((_, i) => (
          <div key={i} className="h-72 rounded-[var(--radius-lg)] bg-[var(--color-card-bg-tint)]" />
        ))}
      </div>
    );
  }

  if (items.length === 0) return null;

  return (
    <div>
      <div className={`flex items-center gap-2 mb-1 ${headingClass}`}>
        <Sparkles size={20} className="text-[var(--color-primary)]" />
        <h3 className="font-[family-name:var(--font-heading)] text-[24px] font-semibold">
          {t('recs.personalized.title')}
        </h3>
      </div>
      <p className="text-sm text-[var(--color-text-muted)] mb-5">
        {t('recs.personalized.subtitle')}
      </p>

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-5">
        {items.map((item) => (
          <div key={item.id} className="flex flex-col">
            <ProductCard product={item} />
            {item.reasonCode && (
              <p className="text-[11px] text-[var(--color-text-muted)] mt-1.5 px-1 leading-snug">
                {t(item.reasonCode, item.reasonParams)}
              </p>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
