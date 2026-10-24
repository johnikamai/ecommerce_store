import { useEffect, useState } from 'react';
import { Sparkles, ThumbsUp, AlertTriangle } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useLanguage } from '../context/LanguageContext';

// The API returns aspect keys so the digest can be translated. Every key is
// listed literally here, which also keeps the i18n usage check honest.
const ASPECT_KEYS = {
  battery: 'product.ai.aspects.battery',
  sound: 'product.ai.aspects.sound',
  camera: 'product.ai.aspects.camera',
  display: 'product.ai.aspects.display',
  performance: 'product.ai.aspects.performance',
  build: 'product.ai.aspects.build',
  comfort: 'product.ai.aspects.comfort',
  fit: 'product.ai.aspects.fit',
  delivery: 'product.ai.aspects.delivery',
  packaging: 'product.ai.aspects.packaging',
  value: 'product.ai.aspects.value',
  durability: 'product.ai.aspects.durability',
  ease: 'product.ai.aspects.ease',
  taste: 'product.ai.aspects.taste',
  scent: 'product.ai.aspects.scent',
  looks: 'product.ai.aspects.looks',
  service: 'product.ai.aspects.service',
  insulation: 'product.ai.aspects.insulation',
};

const CONFIDENCE_KEYS = {
  high: 'product.ai.confidence.high',
  medium: 'product.ai.confidence.medium',
  low: 'product.ai.confidence.low',
};

/**
 * Pros/cons digest for a product's written reviews.
 *
 * The sentence is assembled here from structured data rather than rendered
 * from the English summary string the API also returns, so a Hindi or Spanish
 * shopper reads a translated digest instead of English prose. Verbatim review
 * text stays untouched by design.
 */
export default function ReviewAiSummary({ productId, refreshKey = 0 }) {
  const { t } = useLanguage();
  const [data, setData] = useState(null);
  const [state, setState] = useState('loading');

  useEffect(() => {
    let active = true;
    setState('loading');
    axiosClient.get(`/reviews/product/${productId}/ai-summary`)
      .then((res) => {
        if (!active) return;
        setData(res.data);
        setState('ready');
      })
      .catch(() => {
        if (active) setState('failed');
      });
    return () => { active = false; };
  }, [productId, refreshKey]);

  if (state === 'loading') {
    return (
      <div className="mb-5 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-card-bg-tint)] p-4">
        <div className="flex items-center gap-2 text-sm text-[var(--color-text-muted)]">
          <Sparkles size={16} className="text-[var(--color-primary)]" />
          {t('product.ai.loading')}
        </div>
      </div>
    );
  }

  if (state === 'failed') {
    return (
      <p className="mb-5 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-card-bg-tint)] p-4 text-sm text-[var(--color-text-muted)]">
        {t('product.ai.unavailable')}
      </p>
    );
  }

  if (!data) {
    return null;
  }

  if (data.reviewCount === 0) {
    return (
      <p className="mb-5 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-card-bg-tint)] p-4 text-sm text-[var(--color-text-muted)]">
        {t('product.ai.none')}
      </p>
    );
  }

  const aspectLabel = (aspect) => (ASPECT_KEYS[aspect] ? t(ASPECT_KEYS[aspect]) : aspect);
  const hasWritten = data.commentedCount > 0;

  let narrative;
  if (!hasWritten) {
    narrative = t('product.ai.ratingsOnly');
  } else {
    const parts = [t('product.ai.intro', {
      rating: Number(data.averageRating || 0).toFixed(1),
      count: data.reviewCount,
    })];
    if (data.pros.length > 0) {
      parts.push(t('product.ai.praise', { items: data.pros.map((p) => aspectLabel(p.aspect)).join(', ') }));
    }
    if (data.cons.length > 0) {
      parts.push(t('product.ai.complain', { items: data.cons.map((c) => aspectLabel(c.aspect)).join(', ') }));
    }
    narrative = parts.join(' ');
  }

  return (
    <div className="mb-5 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-card-bg-tint)] p-4">
      <div className="flex flex-wrap items-center gap-2">
        <Sparkles size={16} className="text-[var(--color-primary)]" />
        <h4 className="text-sm font-semibold">{t('product.ai.title')}</h4>
        <span className="text-[11px] px-2 py-0.5 rounded-full bg-[var(--color-surface)] text-[var(--color-text-muted)]">
          {t('product.ai.badge', { count: data.reviewCount })}
        </span>
        {hasWritten && CONFIDENCE_KEYS[data.confidence] && (
          <span className="text-[11px] px-2 py-0.5 rounded-full bg-[var(--color-surface)] text-[var(--color-text-muted)]">
            {t(CONFIDENCE_KEYS[data.confidence])}
          </span>
        )}
      </div>

      <p className="mt-2 text-sm text-[var(--color-text-secondary)]">{narrative}</p>

      {hasWritten && (data.pros.length > 0 || data.cons.length > 0) && (
        <div className="mt-3 grid sm:grid-cols-2 gap-3">
          {data.pros.length > 0 && (
            <div>
              <p className="flex items-center gap-1 text-xs font-semibold text-[var(--color-success)] mb-1.5">
                <ThumbsUp size={13} />
                {t('product.ai.pros')}
              </p>
              <ul className="flex flex-wrap gap-1.5">
                {data.pros.map((item) => (
                  <li
                    key={item.aspect}
                    className="text-xs px-2 py-1 rounded-full bg-[var(--color-success-bg)] text-[var(--color-success)]"
                  >
                    {aspectLabel(item.aspect)} · {t('product.ai.mentions', { count: item.mentions })}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {data.cons.length > 0 && (
            <div>
              <p className="flex items-center gap-1 text-xs font-semibold text-[var(--color-warning)] mb-1.5">
                <AlertTriangle size={13} />
                {t('product.ai.cons')}
              </p>
              <ul className="flex flex-wrap gap-1.5">
                {data.cons.map((item) => (
                  <li
                    key={item.aspect}
                    className="text-xs px-2 py-1 rounded-full bg-[var(--color-warning-bg)] text-[var(--color-warning)]"
                  >
                    {aspectLabel(item.aspect)} · {t('product.ai.mentions', { count: item.mentions })}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      <p className="mt-3 text-[11px] text-[var(--color-text-muted)]">{t('product.ai.disclaimer')}</p>
    </div>
  );
}
