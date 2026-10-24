import { useState, useMemo } from 'react';
import { Plus, Check, ShoppingBag, Sparkles, Tag, ArrowRight } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import ProductImage from './ProductImage';
import { useCart } from '../context/CartContext';
import { useLanguage } from '../context/LanguageContext';

export default function SmartBundleCard({ bundle }) {
  const { addToCart } = useCart();
  const { formatCurrency } = useLanguage();
  const navigate = useNavigate();

  // Track selection: main product is mandatory, accessories can be toggled
  const [selectedIds, setSelectedIds] = useState(() =>
    bundle?.allItems ? new Set(bundle.allItems.map((p) => p.id)) : new Set()
  );

  const selectedProducts = useMemo(() => {
    return (bundle?.allItems || []).filter((p) => selectedIds.has(p.id));
  }, [bundle, selectedIds]);

  const regularTotal = useMemo(() => {
    return selectedProducts.reduce((sum, p) => sum + Number(p.price || 0), 0);
  }, [selectedProducts]);

  if (!bundle || !bundle.accessories || bundle.accessories.length === 0) return null;

  const toggleSelect = (id) => {
    if (id === bundle.mainProduct.id) return; // main product cannot be unticked
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  // Apply bundle discount only when full or partial bundle (at least 2 items) is selected
  const isBundleQualified = selectedProducts.length >= 2;
  const discountAmount = isBundleQualified
    ? Math.round((regularTotal * bundle.discountPercent) / 100)
    : 0;
  const finalPrice = regularTotal - discountAmount;

  const handleAddBundleToCart = () => {
    selectedProducts.forEach((p) => {
      addToCart(p, 1);
    });
    navigate('/cart');
  };

  return (
    <section className="mt-12 rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] border-[1.5px] border-[var(--color-border)] p-6 md:p-8 shadow-[var(--shadow-sm)]">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-full bg-[var(--color-primary-bg)] text-[var(--color-primary)] flex items-center justify-center">
            <Sparkles size={18} />
          </div>
          <div>
            <h3 className="font-[family-name:var(--font-heading)] text-lg md:text-xl font-bold">
              Frequently Bought Together (Smart Bundle)
            </h3>
            <p className="text-xs text-[var(--color-text-muted)]">
              Complementary setup curated for this product with exclusive bundle savings
            </p>
          </div>
        </div>
        {isBundleQualified && (
          <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-[var(--color-success-bg)] text-[var(--color-success)] text-xs font-bold uppercase tracking-wider">
            <Tag size={12} /> Save {bundle.discountPercent}% Instant Bundle Discount
          </span>
        )}
      </div>

      {/* Items layout with + signs */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-center">
        {/* Items chain */}
        <div className="lg:col-span-8 flex flex-wrap sm:flex-nowrap items-center gap-3 md:gap-4 overflow-x-auto pb-2">
          {bundle.allItems.map((item, idx) => {
            const isMain = item.id === bundle.mainProduct.id;
            const isSelected = selectedIds.has(item.id);

            return (
              <div key={item.id} className="flex items-center gap-3 shrink-0">
                <div
                  onClick={() => toggleSelect(item.id)}
                  className={`w-40 sm:w-44 p-3 rounded-[var(--radius-lg)] border transition-all cursor-pointer relative bg-[var(--color-surface)] ${
                    isSelected
                      ? 'border-[var(--color-primary)] shadow-[var(--shadow-xs)]'
                      : 'border-[var(--color-border)] opacity-60'
                  }`}
                >
                  <div className="relative aspect-square rounded-[var(--radius-md)] overflow-hidden bg-[var(--color-card-bg-tint)] mb-2.5">
                    <ProductImage product={item} className="w-full h-full object-cover" />
                    <div
                      className={`absolute top-2 left-2 w-5 h-5 rounded flex items-center justify-center text-white text-xs ${
                        isSelected ? 'bg-[var(--color-primary)]' : 'bg-gray-400'
                      }`}
                    >
                      {isSelected && <Check size={12} />}
                    </div>
                    {isMain && (
                      <span className="absolute bottom-1 right-1 bg-black/70 text-white text-[9px] font-semibold px-1.5 py-0.5 rounded">
                        This item
                      </span>
                    )}
                  </div>
                  <p className="text-xs font-semibold text-[var(--color-text-primary)] line-clamp-1">
                    {item.name}
                  </p>
                  <p className="text-xs font-bold text-[var(--color-primary)] mt-0.5">
                    {formatCurrency(item.price)}
                  </p>
                  <p className="text-[10px] text-[var(--color-text-muted)] truncate">
                    {item.category}
                  </p>
                </div>

                {idx < bundle.allItems.length - 1 && (
                  <div className="text-[var(--color-text-muted)] font-bold text-lg hidden sm:block shrink-0">
                    <Plus size={20} />
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Pricing & Add Bundle Action Card */}
        <div className="lg:col-span-4 rounded-[var(--radius-lg)] bg-[var(--color-card-bg-tint)] p-5 border border-[var(--color-border)] flex flex-col justify-between h-full">
          <div>
            <span className="text-xs font-semibold text-[var(--color-text-muted)] uppercase tracking-wide">
              Bundle Total ({selectedProducts.length} items)
            </span>
            <div className="flex items-baseline gap-2 mt-1">
              <span className="font-[family-name:var(--font-heading)] text-2xl font-bold text-[var(--color-text-primary)]">
                {formatCurrency(finalPrice)}
              </span>
              {discountAmount > 0 && (
                <span className="text-sm line-through text-[var(--color-text-muted)]">
                  {formatCurrency(regularTotal)}
                </span>
              )}
            </div>

            {discountAmount > 0 ? (
              <p className="text-xs font-semibold text-[var(--color-success)] mt-1.5 flex items-center gap-1">
                <span>You save {formatCurrency(discountAmount)} with this bundle</span>
              </p>
            ) : (
              <p className="text-xs text-[var(--color-text-muted)] mt-1">
                Add 1 or more items to unlock the bundle discount
              </p>
            )}
          </div>

          <button
            type="button"
            onClick={handleAddBundleToCart}
            className="w-full mt-5 inline-flex items-center justify-center gap-2 py-3 px-4 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-all shadow-[var(--shadow-sm)] hover:shadow-[var(--shadow-md)]"
          >
            <ShoppingBag size={16} />
            Add Bundle to Cart
          </button>
        </div>
      </div>
    </section>
  );
}
