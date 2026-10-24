import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Gift, Sparkles, ShoppingCart, Loader2 } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import ProductImage from '../components/ProductImage';
import { useCart } from '../context/CartContext';
import { useLanguage } from '../context/LanguageContext';

const OCCASIONS = [
  ['birthday', 'gift.occasion.birthday'],
  ['anniversary', 'gift.occasion.anniversary'],
  ['wedding', 'gift.occasion.wedding'],
  ['baby-shower', 'gift.occasion.babyShower'],
  ['housewarming', 'gift.occasion.housewarming'],
  ['graduation', 'gift.occasion.graduation'],
  ['festival', 'gift.occasion.festival'],
  ['thank-you', 'gift.occasion.thankYou'],
  ['corporate', 'gift.occasion.corporate'],
  ['just-because', 'gift.occasion.justBecause'],
];

const INTERESTS = [
  ['tech', 'gift.interest.tech'],
  ['fashion', 'gift.interest.fashion'],
  ['beauty', 'gift.interest.beauty'],
  ['home', 'gift.interest.home'],
  ['cooking', 'gift.interest.cooking'],
  ['fitness', 'gift.interest.fitness'],
  ['books', 'gift.interest.books'],
  ['music', 'gift.interest.music'],
  ['pets', 'gift.interest.pets'],
  ['kids', 'gift.interest.kids'],
  ['travel', 'gift.interest.travel'],
  ['gardening', 'gift.interest.gardening'],
];

const AGE_KEYS = {
  toddler: 'gift.age.toddler',
  child: 'gift.age.child',
  teen: 'gift.age.teen',
  adult: 'gift.age.adult',
  senior: 'gift.age.senior',
};

// Reason codes arrive from the API; the value is an interest, occasion or age
// band key that also needs translating before it is dropped into the sentence.
const REASON_KEYS = {
  interest: 'gift.reason.interest',
  occasion: 'gift.reason.occasion',
  age: 'gift.reason.age',
  gift: 'gift.reason.gift',
  budget: 'gift.reason.budget',
  rated: 'gift.reason.rated',
  popular: 'gift.reason.popular',
};

const INTEREST_KEYS = Object.fromEntries(INTERESTS);
const OCCASION_KEYS = Object.fromEntries(OCCASIONS);

const EMPTY_FORM = {
  occasion: 'birthday',
  age: '',
  interests: [],
  min: '500',
  max: '3000',
};

export default function GiftFinder() {
  const { t, formatCurrency } = useLanguage();
  const { addToCart } = useCart();
  const [form, setForm] = useState(EMPTY_FORM);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const toggleInterest = (value) => {
    setForm((current) => ({
      ...current,
      interests: current.interests.includes(value)
        ? current.interests.filter((item) => item !== value)
        : [...current.interests, value],
    }));
  };

  const submit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      const res = await axiosClient.post('/gift-finder/suggest', {
        occasion: form.occasion || null,
        ageYears: form.age === '' ? null : Number(form.age),
        interests: form.interests,
        budgetMin: form.min === '' ? null : Number(form.min),
        budgetMax: form.max === '' ? null : Number(form.max),
      });
      setResult(res.data);
    } catch {
      setError(t('gift.error'));
      setResult(null);
    } finally {
      setLoading(false);
    }
  };

  const reset = () => {
    setForm(EMPTY_FORM);
    setResult(null);
    setError('');
  };

  const reasonText = (reason) => {
    const key = REASON_KEYS[reason.code];
    if (!key) return null;
    let value = reason.value;
    if (reason.code === 'interest' && value) value = t(INTEREST_KEYS[value] || value);
    if (reason.code === 'occasion' && value) value = t(OCCASION_KEYS[value] || value);
    if (reason.code === 'age' && value) value = t(AGE_KEYS[value] || value);
    return t(key, value ? { value } : undefined);
  };

  const inputClass = 'mt-1 w-full rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2.5 text-sm outline-none focus:border-[var(--color-primary)]';

  return (
    <div className="container-x py-[var(--space-8)]">
      <div className="flex items-start gap-3 mb-6">
        <span className="w-11 h-11 rounded-[var(--radius-lg)] bg-[var(--color-card-bg-tint)] flex items-center justify-center text-[var(--color-primary)] shrink-0">
          <Gift size={22} />
        </span>
        <div>
          <h1 className="font-[family-name:var(--font-heading)] text-[32px] font-bold leading-tight">
            {t('gift.title')}
          </h1>
          <p className="text-sm text-[var(--color-text-muted)] mt-1 max-w-2xl">{t('gift.subtitle')}</p>
        </div>
      </div>

      <form onSubmit={submit} className="rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] p-5 sm:p-6">
        <div className="grid sm:grid-cols-2 gap-4">
          <label className="block">
            <span className="text-xs font-semibold text-[var(--color-text-secondary)]">{t('gift.occasion')}</span>
            <select
              value={form.occasion}
              onChange={(event) => setForm({ ...form, occasion: event.target.value })}
              className={inputClass}
            >
              <option value="">{t('gift.anyOccasion')}</option>
              {OCCASIONS.map(([value, key]) => (
                <option key={value} value={value}>{t(key)}</option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="text-xs font-semibold text-[var(--color-text-secondary)]">{t('gift.age')}</span>
            <input
              type="number"
              min="0"
              max="120"
              value={form.age}
              onChange={(event) => setForm({ ...form, age: event.target.value })}
              className={inputClass}
            />
            <span className="block mt-1 text-[11px] text-[var(--color-text-muted)]">{t('gift.ageHint')}</span>
          </label>
        </div>

        <fieldset className="mt-5">
          <legend className="text-xs font-semibold text-[var(--color-text-secondary)]">{t('gift.interests')}</legend>
          <div className="mt-2 flex flex-wrap gap-2">
            {INTERESTS.map(([value, key]) => {
              const selected = form.interests.includes(value);
              return (
                <button
                  key={value}
                  type="button"
                  aria-pressed={selected}
                  onClick={() => toggleInterest(value)}
                  className={`rounded-full px-3 py-1.5 text-xs font-semibold border-[1.5px] transition-colors ${
                    selected
                      ? 'bg-[var(--color-primary)] text-white border-[var(--color-primary)]'
                      : 'bg-[var(--color-surface)] text-[var(--color-text-secondary)] border-[var(--color-border)] hover:border-[var(--color-primary)]'
                  }`}
                >
                  {t(key)}
                </button>
              );
            })}
          </div>
        </fieldset>

        <div className="mt-5">
          <span className="text-xs font-semibold text-[var(--color-text-secondary)]">{t('gift.budget')}</span>
          <div className="grid grid-cols-2 gap-3 max-w-md">
            <label className="block">
              <span className="text-[11px] text-[var(--color-text-muted)]">{t('gift.min')}</span>
              <input
                type="number"
                min="0"
                value={form.min}
                onChange={(event) => setForm({ ...form, min: event.target.value })}
                className={inputClass}
              />
            </label>
            <label className="block">
              <span className="text-[11px] text-[var(--color-text-muted)]">{t('gift.max')}</span>
              <input
                type="number"
                min="0"
                value={form.max}
                onChange={(event) => setForm({ ...form, max: event.target.value })}
                className={inputClass}
              />
            </label>
          </div>
        </div>

        <div className="mt-6 flex flex-wrap gap-2">
          <button
            type="submit"
            disabled={loading}
            className="inline-flex items-center justify-center gap-2 min-h-[44px] rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-60"
          >
            {loading ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
            {loading ? t('gift.finding') : t('gift.find')}
          </button>
          <button
            type="button"
            onClick={reset}
            className="min-h-[44px] rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] px-5 text-sm font-semibold text-[var(--color-text-secondary)] hover:bg-[var(--color-card-bg-tint)] transition-colors"
          >
            {t('gift.reset')}
          </button>
        </div>

        {error && <p className="mt-4 text-sm text-[var(--color-error)]">{error}</p>}
      </form>

      {result && (
        <section className="mt-8">
          <div className="flex flex-wrap items-end justify-between gap-2 mb-4">
            <h2 className="font-[family-name:var(--font-heading)] text-xl font-bold">{t('gift.results')}</h2>
            <p className="text-xs text-[var(--color-text-muted)]">
              {t('gift.considered', { count: result.considered })}
            </p>
          </div>

          {result.suggestions.length === 0 ? (
            <p className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg-tint)] p-6 text-sm text-[var(--color-text-muted)]">
              {t('gift.empty')}
            </p>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
              {result.suggestions.map((item) => (
                <article
                  key={item.id}
                  className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden flex flex-col"
                >
                  <Link to={`/product/${item.id}`} className="block aspect-[4/3] bg-[var(--color-card-bg-tint)] overflow-hidden">
                    <ProductImage
                      product={item}
                      className="w-full h-full object-cover hover:scale-105 transition-transform duration-300"
                    />
                  </Link>
                  <div className="p-4 flex flex-col flex-1">
                    <p className="text-[11px] uppercase tracking-wide text-[var(--color-text-muted)]">{item.category}</p>
                    <Link
                      to={`/product/${item.id}`}
                      className="mt-0.5 font-[family-name:var(--font-heading)] font-semibold line-clamp-2 hover:text-[var(--color-primary)] transition-colors"
                    >
                      {item.name}
                    </Link>
                    <div className="mt-1 flex items-center gap-2">
                      <p className="font-[family-name:var(--font-heading)] text-lg font-bold">{formatCurrency(item.price)}</p>
                      {item.averageRating != null && (
                        <p className="text-xs text-[var(--color-text-muted)]">★ {item.averageRating.toFixed(1)}</p>
                      )}
                    </div>

                    {item.reasons.length > 0 && (
                      <div className="mt-3">
                        <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">
                          {t('gift.why')}
                        </p>
                        <ul className="mt-1.5 flex flex-wrap gap-1.5">
                          {item.reasons.slice(0, 3).map((reason, index) => {
                            const text = reasonText(reason);
                            if (!text) return null;
                            return (
                              <li
                                key={`${reason.code}-${index}`}
                                className="text-[11px] px-2 py-0.5 rounded-full bg-[var(--color-card-bg-tint)] text-[var(--color-text-secondary)]"
                              >
                                {text}
                              </li>
                            );
                          })}
                        </ul>
                      </div>
                    )}

                    <div className="mt-auto flex gap-2 pt-4">
                      <Link
                        to={`/product/${item.id}`}
                        className="flex-1 text-center rounded-[var(--radius-md)] border-[1.5px] border-[var(--color-border)] px-3 py-2 text-xs font-semibold hover:bg-[var(--color-card-bg-tint)] transition-colors"
                      >
                        {t('gift.view')}
                      </Link>
                      <button
                        type="button"
                        onClick={() => addToCart(item, 1)}
                        className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-3 py-2 text-xs font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
                      >
                        <ShoppingCart size={14} />
                        {t('gift.addToCart')}
                      </button>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}

          <p className="mt-4 text-[11px] text-[var(--color-text-muted)]">{t('gift.disclaimer')}</p>
        </section>
      )}
    </div>
  );
}
