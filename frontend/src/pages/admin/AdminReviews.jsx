import { useState, useEffect } from 'react';
import { Star, Trash2 } from 'lucide-react';
import axiosClient from '../../api/axiosClient';
import { useLanguage } from '../../context/LanguageContext';

function Stars({ rating }) {
  return (
    <div className="flex items-center gap-0.5">
      {[1, 2, 3, 4, 5].map((n) => (
        <Star key={n} size={14} className={n <= rating ? 'fill-[var(--color-warning)] text-[var(--color-warning)]' : 'text-[var(--color-border)]'} />
      ))}
    </div>
  );
}

export default function AdminReviews() {
  const { t, formatDateTime } = useLanguage();
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    const res = await axiosClient.get('/admin/reviews');
    setReviews(res.data);
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const remove = async (review) => {
    if (!window.confirm(t('admin.reviews.deleteConfirm'))) return;
    try {
      await axiosClient.delete(`/admin/reviews/${review.id}`);
      load();
    } catch (err) {
      alert(err.response?.data || t('admin.reviews.deleteFailed'));
    }
  };

  if (loading) return <p className="py-12 text-center text-[var(--color-text-muted)]">{t('admin.reviews.loading')}</p>;

  return (
    <div>
      <h2 className="font-[family-name:var(--font-heading)] text-[28px] font-bold mb-6">{t('admin.nav.reviews')}</h2>

      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden">
        {reviews.length === 0 ? (
          <p className="p-6 text-sm text-[var(--color-text-muted)]">{t('admin.reviews.empty')}</p>
        ) : (
          <ul className="divide-y divide-[var(--color-border)]">
            {reviews.map((r) => (
              <li key={r.id} className="p-4 flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex items-center gap-3 mb-1">
                    <Stars rating={r.rating} />
                    <span className="text-xs text-[var(--color-text-muted)]">
                      {formatDateTime(r.createdAt)}
                    </span>
                  </div>
                  <p className="font-semibold text-sm">
                    {r.customer?.name || t('admin.reviews.customer')} {t('admin.reviews.on')} {r.product?.name}
                  </p>
                  <p className="text-sm text-[var(--color-text-secondary)]">{r.comment || '—'}</p>
                </div>
                <button onClick={() => remove(r)} aria-label={t('admin.reviews.deleteAria')}
                  className="w-8 h-8 shrink-0 rounded-[var(--radius-md)] flex items-center justify-center text-[var(--color-error)] hover:bg-[var(--color-error-bg)] transition-colors">
                  <Trash2 size={15} />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}