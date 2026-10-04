import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { MessageCircle, X, Send, Sparkles, Loader2 } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import ProductImage from './ProductImage';

const GREETING = {
  role: 'assistant',
  content:
    "Hi! I'm the ShopEase assistant. I search the live catalogue, so I understand plain requests like \"a dress for a beach wedding next week\" or \"gifts under 1500\". I won't invent products we don't stock.",
  chips: ['a dress for a beach wedding', 'gifts under 1500', 'eco friendly gym gear', 'something for travel'],
  products: [],
};

const money = (v) => `₹${Number(v ?? 0).toFixed(0)}`;

/**
 * Context-aware shopping assistant.
 *
 * The conversation is replayed with each request so the assistant can resolve
 * "what about something cheaper?" - but only prior *user* turns are trusted
 * server-side, since assistant text is model output and must not be able to
 * steer the next answer.
 */
export default function ChatAssistant() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([GREETING]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const listRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [messages, busy]);

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
          content: data.reply || 'Sorry, I could not answer that.',
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
          content:
            'I could not reach the catalogue just now. Please try again in a moment.',
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
      {/* Launcher */}
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-label={open ? 'Close shopping assistant' : 'Open shopping assistant'}
        aria-expanded={open}
        className="fixed bottom-5 right-5 z-[var(--z-drawer)] w-14 h-14 rounded-full bg-[var(--color-primary)] text-white shadow-[var(--shadow-lg)] flex items-center justify-center hover:bg-[var(--color-primary-hover)] transition-colors"
      >
        {open ? <X size={22} /> : <MessageCircle size={24} />}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Shopping assistant"
          className="fixed bottom-24 right-5 z-[var(--z-drawer)] w-[min(24rem,calc(100vw-2.5rem))] h-[min(32rem,calc(100vh-8rem))] rounded-[var(--radius-xl)] bg-[var(--color-card-bg)] border-[1.5px] border-[var(--color-border)] shadow-[var(--shadow-lg)] flex flex-col overflow-hidden"
        >
          {/* Header */}
          <div className="px-4 py-3 border-b border-[var(--color-border)] bg-[var(--color-card-bg-tint)] flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Sparkles size={16} className="text-[var(--color-primary)]" />
              <span className="font-[family-name:var(--font-heading)] font-semibold text-sm">
                Shopping assistant
              </span>
            </div>
            <span className="text-[11px] text-[var(--color-text-muted)]">
              {messages.some((m) => m.source === 'llm') ? 'AI + catalogue' : 'catalogue search'}
            </span>
          </div>

          {/* Transcript */}
          <div ref={listRef} className="flex-1 overflow-y-auto px-4 py-3 space-y-3" aria-live="polite">
            {messages.map((m, i) => (
              <div key={i} className="space-y-2">
                <div
                  className={
                    m.role === 'user'
                      ? 'ml-auto max-w-[85%] rounded-[var(--radius-lg)] rounded-br-sm bg-[var(--color-primary)] text-white px-3 py-2 text-sm whitespace-pre-wrap'
                      : 'max-w-[92%] rounded-[var(--radius-lg)] rounded-bl-sm bg-[var(--color-card-bg-tint)] px-3 py-2 text-sm text-[var(--color-text-primary)] whitespace-pre-wrap'
                  }
                >
                  {m.content}
                </div>

                {/* What the assistant extracted - shown so a wrong guess is visible */}
                {m.understood?.length > 0 && (
                  <p className="text-[11px] text-[var(--color-text-muted)] px-1">
                    I read that as: {m.understood.join(' · ')}
                  </p>
                )}

                {m.products?.length > 0 && (
                  <div className="space-y-1.5">
                    {m.products.map((p) => (
                      <Link
                        key={p.id}
                        to={`/product/${p.id}`}
                        onClick={() => setOpen(false)}
                        className="flex items-center gap-2.5 p-2 rounded-[var(--radius-md)] bg-[var(--color-card-bg)] border-[1.5px] border-[var(--color-border)] hover:border-[var(--color-primary)] transition-colors"
                      >
                        <div className="w-10 h-10 rounded-[var(--radius-sm)] overflow-hidden bg-[var(--color-card-bg-tint)] shrink-0">
                          <ProductImage product={p} className="w-full h-full object-cover" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="text-xs font-semibold truncate">{p.name}</p>
                          <p className="text-xs text-[var(--color-text-muted)]">
                            {p.category} · {money(p.price)}
                          </p>
                        </div>
                      </Link>
                    ))}
                  </div>
                )}

                {m.chips?.length > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {m.chips.map((c) => (
                      <button
                        key={c}
                        onClick={() => send(c)}
                        className="px-2.5 py-1 rounded-[var(--radius-full)] border-[1.5px] border-[var(--color-border)] text-[11px] font-medium text-[var(--color-text-secondary)] hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] transition-colors"
                      >
                        {c}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}

            {busy && (
              <div className="flex items-center gap-2 text-xs text-[var(--color-text-muted)]">
                <Loader2 size={13} className="animate-spin" /> Searching the catalogue...
              </div>
            )}
          </div>

          {/* Composer */}
          <form
            onSubmit={(e) => {
              e.preventDefault();
              send();
            }}
            className="p-3 border-t border-[var(--color-border)] flex items-center gap-2"
          >
            <input
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask for anything..."
              aria-label="Message the shopping assistant"
              maxLength={400}
              className="flex-1 px-3 py-2 rounded-[var(--radius-full)] bg-[var(--color-card-bg-tint)] outline-none text-sm focus:ring-2 focus:ring-[var(--color-primary)]"
            />
            <button
              type="submit"
              disabled={busy || !input.trim()}
              aria-label="Send message"
              className="w-10 h-10 rounded-full bg-[var(--color-primary)] text-white flex items-center justify-center disabled:opacity-50 transition-colors"
            >
              <Send size={16} />
            </button>
          </form>
        </div>
      )}
    </>
  );
}
