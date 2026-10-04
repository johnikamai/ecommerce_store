import { Link } from 'react-router-dom';
import { useLanguage } from '../context/LanguageContext';

// Category values, not chrome: these are API strings used as query params, so
// they must stay in English to keep matching product.category in the catalogue.
const CATEGORY_LINKS = ['Food', 'Electronics', 'Fashion', 'Beauty'];

export default function Footer() {
  const { t } = useLanguage();
  const year = new Date().getFullYear();

  return (
    <footer className="mt-[var(--space-11)] bg-[var(--color-card-bg-tint)]">
      <div className="container-x py-[var(--space-9)] grid grid-cols-2 md:grid-cols-4 gap-8">
        <div className="col-span-2 md:col-span-1">
          <div className="flex items-center gap-2 mb-3">
            <span className="w-8 h-8 rounded-[var(--radius-md)] bg-gradient-premium flex items-center justify-center text-white font-bold">
              S
            </span>
            <span className="font-[family-name:var(--font-heading)] text-lg font-bold">ShopEase</span>
          </div>
          <p className="text-sm text-[var(--color-text-secondary)]">
            {t('footer.blurb')}
          </p>
        </div>

        <div>
          <h4 className="font-[family-name:var(--font-heading)] text-sm font-semibold mb-3">{t('nav.shop')}</h4>
          <ul className="space-y-2 text-sm text-[var(--color-text-secondary)]">
            {CATEGORY_LINKS.map((c) => (
              <li key={c}>
                <Link to={`/products?category=${encodeURIComponent(c)}`} className="hover:text-[var(--color-primary)] transition-colors">
                  {c}
                </Link>
              </li>
            ))}
          </ul>
        </div>

        <div>
          <h4 className="font-[family-name:var(--font-heading)] text-sm font-semibold mb-3">{t('nav.account')}</h4>
          <ul className="space-y-2 text-sm text-[var(--color-text-secondary)]">
            <li><Link to="/orders" className="hover:text-[var(--color-primary)] transition-colors">{t('nav.orders')}</Link></li>
            <li><Link to="/wishlist" className="hover:text-[var(--color-primary)] transition-colors">{t('nav.wishlist')}</Link></li>
            <li><Link to="/cart" className="hover:text-[var(--color-primary)] transition-colors">{t('nav.cart')}</Link></li>
          </ul>
        </div>

        <div>
          <h4 className="font-[family-name:var(--font-heading)] text-sm font-semibold mb-3">{t('footer.perks')}</h4>
          <ul className="space-y-2 text-sm text-[var(--color-text-secondary)]">
            <li>{t('footer.perkPoints')}</li>
            <li>{t('footer.perkGold')}</li>
            <li>{t('footer.perkRestock')}</li>
            <li>{t('footer.perkReferral')}</li>
          </ul>
        </div>
      </div>

      <div className="border-t border-[var(--color-border)]">
        <div className="container-x py-4 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-[var(--color-text-muted)]">
          <p>{t('footer.disclaimer', { year })}</p>
          <p>{t('footer.builtWith')}</p>
        </div>
      </div>
    </footer>
  );
}