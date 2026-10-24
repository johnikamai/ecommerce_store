import { useState, useRef } from 'react';
import { Camera, Upload, X, Sparkles, Loader2, ShoppingCart, ArrowRight } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import ProductImage from './ProductImage';
import { extractImageFeatures, inferVisualCategory, rankProductsByVisualSimilarity } from '../utils/visualSearch';
import { useCart } from '../context/CartContext';
import { useLanguage } from '../context/LanguageContext';

export default function VisualSearchModal({ isOpen, onClose, catalog }) {
  const [preview, setPreview] = useState(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [results, setResults] = useState([]);
  const [detectedCategory, setDetectedCategory] = useState(null);
  const fileInputRef = useRef(null);
  const { addToCart } = useCart();
  const { formatCurrency } = useLanguage();
  const navigate = useNavigate();

  if (!isOpen) return null;

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = () => {
      const imgUrl = reader.result;
      setPreview(imgUrl);
      analyzeImage(imgUrl, file.name);
    };
    reader.readAsDataURL(file);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    const file = e.dataTransfer.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = () => {
      const imgUrl = reader.result;
      setPreview(imgUrl);
      analyzeImage(imgUrl, file.name);
    };
    reader.readAsDataURL(file);
  };

  const analyzeImage = (imgUrl, fileName) => {
    setAnalyzing(true);
    setResults([]);
    
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.src = imgUrl;
    img.onload = async () => {
      try {
        const features = await extractImageFeatures(img);
        const category = inferVisualCategory(features, fileName);
        setDetectedCategory(category);

        // F3 enhancement: send features to the backend for server-side matching.
        // Falls back to client-side ranking if the backend is unreachable.
        try {
          const { default: axiosClient } = await import('../utils/axiosClient');
          const { data } = await axiosClient.post('/products/visual-search', {
            dominantHue:  features.dominantHue,
            saturation:   features.saturation,
            brightness:   features.brightness,
            aspectRatio:  features.aspectRatio,
            category:     category || '',
          });
          // Backend returns { results: [...] } — map to the same shape as client-side
          if (data?.results?.length > 0) {
            setResults(data.results.map(r => ({
              ...r,
              score: r.similarity ?? 0,
            })));
            return;
          }
        } catch (apiErr) {
          console.warn('Visual search backend unavailable, using client-side fallback.', apiErr.message);
        }

        // Client-side fallback
        const matches = rankProductsByVisualSimilarity(features, catalog, category);
        setResults(matches);
      } catch (err) {
        console.error('Visual analysis failed', err);
      } finally {
        setAnalyzing(false);
      }
    };
  };


  const handleClear = () => {
    setPreview(null);
    setResults([]);
    setDetectedCategory(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Search by Image"
      className="fixed inset-0 z-[var(--z-modal)] bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto"
    >
      <div className="bg-[var(--color-card-bg)] border border-[var(--color-border)] rounded-[var(--radius-xl)] shadow-[var(--shadow-xl)] w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden animate-in fade-in duration-200">
        {/* Header */}
        <div className="p-4 border-b border-[var(--color-border)] flex items-center justify-between bg-[var(--color-card-bg-tint)]">
          <div className="flex items-center gap-2">
            <Camera className="text-[var(--color-primary)]" size={20} />
            <div>
              <h3 className="font-[family-name:var(--font-heading)] font-semibold text-base">
                Search by Image
              </h3>
              <p className="text-xs text-[var(--color-text-muted)]">
                Upload or drag a photo of a shoe, dress, bag, or tech to find matches
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-full hover:bg-[var(--color-border)] text-[var(--color-text-muted)] transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Content */}
        <div className="p-5 overflow-y-auto flex-1 space-y-5">
          {!preview ? (
            <div
              onDragOver={(e) => e.preventDefault()}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              className="border-2 border-dashed border-[var(--color-border)] hover:border-[var(--color-primary)] rounded-[var(--radius-lg)] p-8 text-center cursor-pointer transition-all bg-[var(--color-card-bg-tint)]/40 hover:bg-[var(--color-card-bg-tint)]"
            >
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleFileChange}
                className="hidden"
              />
              <div className="w-14 h-14 mx-auto rounded-full bg-[var(--color-primary-bg)] text-[var(--color-primary)] flex items-center justify-center mb-3">
                <Upload size={24} />
              </div>
              <p className="font-semibold text-sm text-[var(--color-text-primary)]">
                Click to upload or drag & drop a photo
              </p>
              <p className="text-xs text-[var(--color-text-muted)] mt-1">
                PNG, JPG, or WEBP (shoes, apparel, tech, accessories)
              </p>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="flex items-center gap-4 p-3 rounded-[var(--radius-md)] bg-[var(--color-card-bg-tint)] border border-[var(--color-border)]">
                <img
                  src={preview}
                  alt="Query"
                  className="w-16 h-16 object-cover rounded-[var(--radius-sm)] border border-[var(--color-border)]"
                />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-semibold text-[var(--color-text-primary)]">Your Photo</span>
                    {detectedCategory && (
                      <span className="text-[10px] px-2 py-0.5 rounded-full bg-[var(--color-primary-bg)] text-[var(--color-primary)] font-medium">
                        {detectedCategory}
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-[var(--color-text-muted)] mt-0.5">
                    {analyzing ? 'Analyzing visual colors & patterns...' : `${results.length} similar items found`}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={handleClear}
                  className="text-xs font-medium text-[var(--color-error)] hover:underline"
                >
                  Change Photo
                </button>
              </div>

              {analyzing && (
                <div className="py-12 text-center text-sm text-[var(--color-text-muted)] flex items-center justify-center gap-2">
                  <Loader2 className="animate-spin text-[var(--color-primary)]" size={18} />
                  Matching catalog patterns...
                </div>
              )}

              {!analyzing && results.length > 0 && (
                <div>
                  <h4 className="text-xs font-semibold text-[var(--color-text-muted)] uppercase tracking-wider mb-3">
                    Top Visually Similar Products
                  </h4>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {results.map(({ product, similarity }) => (
                      <div
                        key={product.id}
                        className="p-2.5 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-card-bg)] hover:border-[var(--color-primary)] transition-colors flex gap-3"
                      >
                        <Link
                          to={`/product/${product.id}`}
                          onClick={onClose}
                          className="w-16 h-16 rounded-[var(--radius-sm)] overflow-hidden bg-[var(--color-card-bg-tint)] shrink-0 block"
                        >
                          <ProductImage product={product} className="w-full h-full object-cover" />
                        </Link>
                        <div className="flex-1 min-w-0 flex flex-col justify-between">
                          <div>
                            <div className="flex items-center justify-between gap-1">
                              <span className="text-[10px] font-semibold text-[var(--color-primary)]">
                                {similarity}% match
                              </span>
                              <span className="text-[10px] text-[var(--color-text-muted)]">
                                {product.category}
                              </span>
                            </div>
                            <Link
                              to={`/product/${product.id}`}
                              onClick={onClose}
                              className="text-xs font-semibold text-[var(--color-text-primary)] hover:text-[var(--color-primary)] line-clamp-1 block mt-0.5"
                            >
                              {product.name}
                            </Link>
                            <p className="text-xs font-bold text-[var(--color-text-primary)]">
                              {formatCurrency(product.price)}
                            </p>
                          </div>
                          <div className="flex items-center gap-2 mt-2">
                            <button
                              type="button"
                              onClick={() => {
                                addToCart(product, 1);
                                onClose();
                                navigate('/cart');
                              }}
                              className="text-[11px] font-semibold py-1 px-2.5 rounded-[var(--radius-sm)] bg-[var(--color-primary)] text-white hover:bg-[var(--color-primary-hover)] transition-colors flex items-center gap-1"
                            >
                              <ShoppingCart size={11} /> Buy
                            </button>
                            <Link
                              to={`/product/${product.id}`}
                              onClick={onClose}
                              className="text-[11px] font-medium text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] flex items-center gap-0.5"
                            >
                              View <ArrowRight size={11} />
                            </Link>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
