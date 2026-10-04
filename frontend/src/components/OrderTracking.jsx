import { useEffect, useState } from 'react';
import { Package, Truck, MapPin, Check, Circle, RefreshCw, Home } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useLanguage } from '../context/LanguageContext';

/**
 * Per-status copy for the one-line "where is it now" summary.
 * Falls back to the plain status name for a state we have no copy for.
 */
const HEADLINES = {
  PLACED: 'tracking.awaitingHandover',
  PACKED: 'tracking.awaitingHandover',
  SHIPPED: 'tracking.onTheWay',
  OUT_FOR_DELIVERY: 'tracking.outForDelivery',
  DELIVERED: 'tracking.delivered',
  CANCELLED: 'tracking.cancelledNote',
};

const STEP_ICONS = {
  PLACED: Package,
  PACKED: Package,
  SHIPPED: Truck,
  OUT_FOR_DELIVERY: MapPin,
  DELIVERED: Home,
};

function Dot({ reached, active, cancelled }) {
  if (cancelled) {
    return (
      <span className="w-7 h-7 rounded-full flex items-center justify-center bg-[var(--color-error)] text-white shrink-0">
        <Circle size={12} fill="currentColor" />
      </span>
    );
  }
  if (reached) {
    return (
      <span className={`w-7 h-7 rounded-full flex items-center justify-center text-white shrink-0 ${active ? 'bg-[var(--color-primary)]' : 'bg-[var(--color-success)]'}`}>
        <Check size={15} />
      </span>
    );
  }
  return (
    <span className="w-7 h-7 rounded-full flex items-center justify-center bg-[var(--color-border)] text-[var(--color-text-muted)] shrink-0">
      <Circle size={8} fill="currentColor" />
    </span>
  );
}

/**
 * Live delivery tracker for one order.
 *
 * Polls only while the parcel is actually moving (PLACED -> OUT_FOR_DELIVERY).
 * A delivered or cancelled order has nothing left to poll for, so the interval
 * stops rather than continuing to hammer the API for the rest of the session.
 */
export default function OrderTracking({ orderId, status }) {
  const { t, formatDateTime } = useLanguage();
  const [data, setData] = useState(null);
  const [error, setError] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const settled = status === 'DELIVERED' || status === 'CANCELLED';

  const load = async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    try {
      const res = await axiosClient.get(`/orders/${orderId}/tracking`);
      setData(res.data);
      setError(false);
    } catch {
      setError(true);
    } finally {
      setRefreshing(false);
    }
  };

  useEffect(() => {
    let timer;

    const tick = async () => {
      await load();
      // Only keep polling while the parcel is still in motion.
      if (!settled) timer = setTimeout(tick, 30000);
    };
    tick();

    return () => {
      if (timer) clearTimeout(timer);
    };
    // `settled` is derived from `status` and is the only value that should
    // restart the loop. `load` closes over a stable axios client.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderId, settled]);

  if (error && !data) {
    return (
      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg-tint)] p-4 text-sm text-[var(--color-text-muted)]">
        {t('tracking.trackingFailed')}
      </div>
    );
  }

  if (!data) {
    return (
      <div className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg-tint)] p-4 text-sm text-[var(--color-text-muted)]">
        {t('action.loading')}
      </div>
    );
  }

  const steps = data.steps || [];
  const events = data.events || [];
  const currentIndex = steps.findIndex((s) => s.status === data.status);
  const headlineKey = HEADLINES[data.status];

  return (
    <div className="rounded-[var(--radius-lg)] border-[1.5px] border-[var(--color-border)] bg-[var(--color-card-bg-tint)] p-4 mb-4">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="min-w-0">
          <p className="font-[family-name:var(--font-heading)] text-sm font-bold flex items-center gap-2">
            {t('tracking.title')}
            {data.trackable && (
              <span className="inline-flex items-center gap-1 rounded-full bg-[var(--color-success-bg)] px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-[var(--color-success)]">
                <span className="w-1.5 h-1.5 rounded-full bg-[var(--color-success)] animate-pulse" />
                {t('tracking.live')}
              </span>
            )}
          </p>
          {headlineKey && (
            <p className="text-xs text-[var(--color-text-secondary)] mt-0.5">{t(headlineKey)}</p>
          )}
        </div>
        <button
          type="button"
          onClick={() => load(true)}
          disabled={refreshing}
          title={t('tracking.refresh')}
          aria-label={t('tracking.refresh')}
          className="w-8 h-8 rounded-full flex items-center justify-center text-[var(--color-text-muted)] hover:bg-[var(--color-card-bg)] hover:text-[var(--color-primary)] transition-colors disabled:opacity-50 shrink-0"
        >
          <RefreshCw size={15} className={refreshing ? 'animate-spin' : ''} />
        </button>
      </div>

      {/* Courier details */}
      {(data.trackingNumber || data.carrier || data.expectedDelivery) && (
        <dl className="grid grid-cols-1 sm:grid-cols-3 gap-2 mb-4 text-xs">
          {data.trackingNumber && (
            <div className="bg-[var(--color-card-bg)] rounded-[var(--radius-md)] px-3 py-2">
              <dt className="text-[var(--color-text-muted)]">{t('tracking.number')}</dt>
              <dd className="font-mono font-semibold text-[var(--color-text-primary)] break-all">{data.trackingNumber}</dd>
            </div>
          )}
          {data.carrier && (
            <div className="bg-[var(--color-card-bg)] rounded-[var(--radius-md)] px-3 py-2">
              <dt className="text-[var(--color-text-muted)]">{t('tracking.carrier')}</dt>
              <dd className="font-semibold text-[var(--color-text-primary)]">{data.carrier}</dd>
            </div>
          )}
          {data.expectedDelivery && (
            <div className="bg-[var(--color-card-bg)] rounded-[var(--radius-md)] px-3 py-2">
              <dt className="text-[var(--color-text-muted)]">{t('tracking.expectedDelivery')}</dt>
              <dd className="font-semibold text-[var(--color-text-primary)]">{formatDateTime(data.expectedDelivery)}</dd>
            </div>
          )}
        </dl>
      )}

      {/* Milestone rail */}
      {steps.length > 0 && (
        <ol className="flex flex-wrap items-start gap-x-0 gap-y-3 mb-1">
          {steps.map((step, i) => {
            const Icon = STEP_ICONS[step.status];
            const isLast = i === steps.length - 1;
            const reached = step.reached;
            return (
              <li key={step.status} className="flex items-start">
                <div className="flex flex-col items-center w-24 sm:w-28">
                  <Dot reached={reached} active={i === currentIndex} cancelled={data.cancelled} />
                  <span className={`text-[11px] mt-1.5 text-center leading-tight ${reached ? 'font-semibold text-[var(--color-text-primary)]' : 'text-[var(--color-text-muted)]'}`}>
                    {t(`status.${step.status}`)}
                  </span>
                  {step.at && (
                    <span className="text-[10px] text-[var(--color-text-muted)] text-center leading-tight mt-0.5">
                      {formatDateTime(step.at)}
                    </span>
                  )}
                  {Icon && reached && (
                    <Icon size={12} className="mt-1 text-[var(--color-text-muted)]" aria-hidden="true" />
                  )}
                </div>
                {!isLast && (
                  <div className={`w-6 sm:w-8 h-0.5 mt-3.5 -mt-0.5 ${i < currentIndex ? 'bg-[var(--color-success)]' : 'bg-[var(--color-border)]'}`} />
                )}
              </li>
            );
          })}
        </ol>
      )}

      {/* Scan history */}
      {events.length > 0 && (
        <details className="mt-3 group">
          <summary className="cursor-pointer text-xs font-semibold text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">
            {t('tracking.history')} ({events.length})
          </summary>
          <ul className="mt-2 space-y-1.5 border-l-2 border-[var(--color-border)] pl-3">
            {[...events].reverse().map((e) => (
              <li key={e.id} className="text-xs">
                <span className="font-semibold text-[var(--color-text-primary)]">
                  {t(`status.${e.status}`)}
                </span>
                {e.note && <span className="text-[var(--color-text-secondary)]"> — {e.note}</span>}
                <span className="block text-[10px] text-[var(--color-text-muted)]">
                  {formatDateTime(e.createdAt)}
                </span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}
