import { Sparkles, Award, Zap, DollarSign, CheckCircle, ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useLanguage } from '../context/LanguageContext';

export default function ComparisonVerdictCard({ verdict }) {
  const { formatCurrency } = useLanguage();

  if (!verdict) return null;

  return (
    <div className="mb-6 rounded-[var(--radius-xl)] bg-gradient-hero border-[1.5px] border-[var(--color-primary)]/20 p-5 md:p-6 shadow-[var(--shadow-md)]">
      {/* Header */}
      <div className="flex items-center gap-2 mb-3">
        <div className="w-7 h-7 rounded-full bg-[var(--color-primary)] text-white flex items-center justify-center">
          <Sparkles size={16} />
        </div>
        <div>
          <h3 className="font-[family-name:var(--font-heading)] text-base md:text-lg font-bold text-[var(--color-text-primary)]">
            AI Product Comparison Verdict
          </h3>
          <p className="text-xs text-[var(--color-text-muted)]">
            Synthesized recommendation based on price, performance specs, and customer satisfaction
          </p>
        </div>
      </div>

      {/* Main summary message */}
      <div className="p-3.5 rounded-[var(--radius-md)] bg-[var(--color-card-bg)] border border-[var(--color-border)] text-sm text-[var(--color-text-secondary)] mb-4 leading-relaxed">
        <p dangerouslySetInnerHTML={{
          __html: verdict.summary.replace(/\*\*(.*?)\*\*/g, '<strong class="text-[var(--color-primary)] font-semibold">$1</strong>')
        }} />
      </div>

      {/* Structured breakdowns per product */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {verdict.productStrengths.map(({ product, badge, isLowest, isTopPerf, isBestVal, reason }) => (
          <div
            key={product.id}
            className={`p-3.5 rounded-[var(--radius-md)] border transition-all ${
              isTopPerf || isBestVal
                ? 'bg-[var(--color-card-bg)] border-[var(--color-primary)]/40 shadow-[var(--shadow-xs)]'
                : 'bg-[var(--color-card-bg)]/80 border-[var(--color-border)]'
            }`}
          >
            <div className="flex items-center justify-between gap-1 mb-1.5">
              <span
                className={`text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider ${
                  isTopPerf
                    ? 'bg-purple-100 text-purple-700'
                    : isLowest
                    ? 'bg-green-100 text-green-700'
                    : 'bg-blue-100 text-blue-700'
                }`}
              >
                {badge}
              </span>
              <span className="text-xs font-bold text-[var(--color-text-primary)]">
                {formatCurrency(product.price)}
              </span>
            </div>

            <h4 className="font-semibold text-xs text-[var(--color-text-primary)] line-clamp-1 mb-1">
              {product.name}
            </h4>

            <p className="text-[11px] text-[var(--color-text-muted)] leading-tight">
              {reason}
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}
