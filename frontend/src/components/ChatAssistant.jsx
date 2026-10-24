import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { MessageCircle, X, Send, Sparkles, Loader2, ShoppingCart, ExternalLink, ArrowRight } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import ProductImage from './ProductImage';
import { useLanguage } from '../context/LanguageContext';
import { useCart } from '../context/CartContext';

/**
 * Built from `t` rather than a module constant so the opening message follows
 * the active language instead of being frozen in English at import time.
 */
const buildGreeting = (t) => ({
  role: 'assistant',
  content: t('chat.greeting'),
  chips: [
    'I need a laptop for coding under ₹50,000',
    t('chat.greetingChipDress'),
    t('chat.greetingChipGifts'),
    t('chat.greetingChipEco'),
    t('chat.greetingChipTravel'),
  ],
  products: [],
});

/**
 * One transcript turn: the bubble plus the optional "what I understood" line,
 * product hits with "Why this fits" reason and direct Buy / Add to Cart links,
 * and suggestion chips.
 */
function MessageBubble({ message, onChipClick, onNavigate }) {
  const { t, formatCurrency } = useLanguage();
  const { addToCart } = useCart();
  const navigate = useNavigate();

  return (
    <div className="space-y-2.5">
      <div
        className={
          message.role === 'user'
            ? 'ml-auto max-w-[85%] rounded-[var(--radius-lg)] rounded-br-sm bg-[var(--color-primary)] text-white px-3.5 py-2.5 text-sm whitespace-pre-wrap'
            : 'max-w-[95%] rounded-[var(--radius-lg)] rounded-bl-sm bg-[var(--color-card-bg-tint)] px-3.5 py-2.5 text-sm text-[var(--color-text-primary)] whitespace-pre-wrap'
        }
      >
        {message.content}
      </div>

      {/* What the assistant extracted */}
      {message.understood?.length > 0 && (
        <p className="text-[11px] text-[var(--color-text-muted)] px-1">
          {t('chat.understood')} {message.understood.join(' · ')}
        </p>
      )}

      {/* Product recommendation cards */}
      {message.products?.length > 0 && (
        <div className="space-y-2 pt-1">
          {message.products.map((p) => (
            <div
              key={p.id}
              className="p-2.5 rounded-[var(--radius-md)] bg-[var(--color-card-bg)] border-[1.5px] border-[var(--color-border)] hover:border-[var(--color-primary)] transition-all shadow-[var(--shadow-xs)]"
            >
              <div className="flex items-start gap-2.5">
                <Link
                  to={`/product/${p.id}`}
                  onClick={onNavigate}
                  className="w-14 h-14 rounded-[var(--radius-sm)] overflow-hidden bg-[var(--color-card-bg-tint)] shrink-0 block"
                >
                  <ProductImage product={p} className="w-full h-full object-cover" />
                </Link>
                <div className="min-w-0 flex-1">
                  <Link
                    to={`/product/${p.id}`}
                    onClick={onNavigate}
                    className="text-xs font-semibold text-[var(--color-text-primary)] hover:text-[var(--color-primary)] line-clamp-1 block"
                  >
                    {p.name}
                  </Link>
                  <p className="text-xs font-bold text-[var(--color-primary)] mt-0.5">
                    {formatCurrency(p.price)}
                    {p.category && (
                      <span className="text-[10px] font-normal text-[var(--color-text-muted)] ml-1.5">
                        in {p.category}
                      </span>
                    )}
                  </p>
                  {/* Why this fits reason */}
                  {p.reason && (
                    <div className="mt-1.5 p-1.5 rounded-[var(--radius-sm)] bg-[var(--color-card-bg-tint)] text-[11px] text-[var(--color-text-secondary)] leading-tight">
                      <span className="font-semibold text-[var(--color-primary)]">Why it fits: </span>
                      {p.reason}
                    </div>
                  )}
                </div>
              </div>

              {/* Action buttons: Direct Buy Now and View */}
              <div className="mt-2.5 pt-2 border-t border-[var(--color-border)] flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={() => {
                    addToCart(p, 1);
                    onNavigate();
                    navigate('/cart');
                  }}
                  disabled={p.stockQuantity <= 0}
                  className="flex-1 inline-flex items-center justify-center gap-1 py-1.5 px-2 rounded-[var(--radius-sm)] bg-[var(--color-primary)] text-white text-[11px] font-semibold hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-50"
                >
                  <ShoppingCart size={12} />
                  {p.stockQuantity > 0 ? 'Buy / Add' : 'Out of Stock'}
                </button>
                <Link
                  to={`/product/${p.id}`}
                  onClick={onNavigate}
                  className="inline-flex items-center justify-center gap-1 py-1.5 px-2.5 rounded-[var(--radius-sm)] border border-[var(--color-border)] text-[11px] font-medium text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] hover:border-[var(--color-primary)] transition-colors"
                >
                  Details <ExternalLink size={11} />
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Suggestion Chips */}
      {message.chips?.length > 0 && (
        <div className="flex flex-wrap gap-1.5 pt-1">
          {message.chips.map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => onChipClick(c)}
              className="px-2.5 py-1 rounded-[var(--radius-full)] border-[1.5px] border-[var(--color-border)] text-[11px] font-medium text-[var(--color-text-secondary)] hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] transition-colors text-left"
            >
              {c}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * Context-aware shopping assistant.
 */
export default function ChatAssistant() {
  const { t } = useLanguage();
  const greeting = useMemo(() => buildGreeting(t), [t]);
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const listRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [messages, busy, greeting]);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  const send = async (text) => {
    const message = (text ?? input).trim();
    if (!message || busy) return;

    const history = messages.map((m) => ({ role: m.role, content: m.content }));
    setMessages((prev) => [...prev, { role: 'user', content: message, chips: [], products: [] }]);
    setInput('');
    setBusy(true);

    try {
      const res = await axiosClient.post('/assistant/chat', { message, history });
      const data = res.data || {};
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: data.reply || t('chat.fallback'),
          products: data.products || [],
          understood: data.understood || [],
          source: data.source,
          chips: data.chips || [],
        },
      ]);
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: t('chat.offline'),
          chips: [],
          products: [],
        },
      ]);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      {/* Floating Launcher */}
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-label={open ? t('chat.close') : t('chat.open')}
        aria-expanded={open}
        className="fixed bottom-5 right-5 z-[var(--z-drawer)] w-14 h-14 rounded-full bg-[var(--color-primary)] text-white shadow-[var(--shadow-lg)] flex items-center justify-center hover:bg-[var(--color-primary-hover)] transition-transform hover:scale-105"
      >
        {open ? <X size={22} /> : <MessageCircle size={24} />}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label={t('chat.title')}
          className="fixed bottom-24 right-5 z-[var(--z-drawer)] w-[min(26rem,calc(100vw-2rem))] h-[min(34rem,calc(100vh-8rem))] rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] border-[1.5px] border-[var(--color-border)] shadow-[var(--shadow-xl)] flex flex-col overflow-hidden"
        >
          {/* Header */}
          <div className="px-4 py-3 border-b border-[var(--color-border)] bg-[var(--color-card-bg-tint)] flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Sparkles size={16} className="text-[var(--color-primary)]" />
              <div>
                <span className="font-[family-name:var(--font-heading)] font-semibold text-sm block leading-none">
                  AI Shopping Assistant
                </span>
                <span className="text-[10px] text-[var(--color-text-muted)]">
                  Personalized recommendations & insights
                </span>
              </div>
            </div>
            <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-[var(--color-primary-bg)] text-[var(--color-primary)]">
              {messages.some((m) => m.source === 'llm') ? t('chat.sourceLlm') : t('chat.sourceSearch')}
            </span>
          </div>

          {/* Transcript */}
          <div ref={listRef} className="flex-1 overflow-y-auto px-4 py-3 space-y-3" aria-live="polite">
            {messages.length === 0 && (
              <MessageBubble
                message={greeting}
                onChipClick={send}
                onNavigate={() => setOpen(false)}
              />
            )}

            {messages.map((m, i) => (
              <MessageBubble
                key={i}
                message={m}
                onChipClick={send}
                onNavigate={() => setOpen(false)}
              />
            ))}

            {busy && (
              <div className="flex items-center gap-2 text-xs text-[var(--color-text-muted)] p-2">
                <Loader2 size={13} className="animate-spin text-[var(--color-primary)]" /> {t('chat.searching')}
              </div>
            )}
          </div>

          {/* Composer */}
          <form
            onSubmit={(e) => {
              e.preventDefault();
              send();
            }}
            className="p-3 border-t border-[var(--color-border)] flex items-center gap-2 bg-[var(--color-surface)]"
          >
            <input
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={t('chat.placeholder')}
              aria-label={t('chat.messageLabel')}
              maxLength={400}
              className="flex-1 px-3.5 py-2 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] outline-none text-sm focus:ring-2 focus:ring-[var(--color-primary)] border border-transparent focus:border-[var(--color-primary)]"
            />
            <button
              type="submit"
              disabled={busy || !input.trim()}
              aria-label={t('chat.send')}
              className="w-10 h-10 rounded-full bg-[var(--color-primary)] text-white flex items-center justify-center disabled:opacity-50 hover:bg-[var(--color-primary-hover)] transition-colors shrink-0"
            >
              <Send size={16} />
            </button>
          </form>
        </div>
      )}
    </>
  );
}
