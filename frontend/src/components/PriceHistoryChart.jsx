import { useEffect, useState } from 'react';
import axiosClient from '../api/axiosClient';

/**
 * F7: Product Price History Graph
 *
 * Calls GET /api/products/{id}/price-history and renders an inline SVG
 * line chart showing price over time.  No external charting library required.
 */
export default function PriceHistoryChart({ productId }) {
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState('');

  useEffect(() => {
    if (!productId) return;
    setLoading(true);
    axiosClient.get(`/products/${productId}/price-history`)
      .then(({ data }) => { setHistory(data); setError(''); })
      .catch(() => setError('Could not load price history.'))
      .finally(() => setLoading(false));
  }, [productId]);

  if (loading) return (
    <div style={{ padding: '20px', color: 'var(--muted)', fontSize: '0.88rem' }}>
      ⏳ Loading price history…
    </div>
  );
  if (error) return (
    <div style={{ padding: '20px', color: '#dc2626', fontSize: '0.88rem' }}>{error}</div>
  );
  if (history.length === 0) return (
    <div style={{
      padding: '20px',
      background: 'var(--card)',
      border: '1px solid var(--border)',
      borderRadius: '12px',
      color: 'var(--muted)',
      fontSize: '0.88rem',
      textAlign: 'center',
    }}>
      📊 No price history recorded yet. History is tracked from this point onwards.
    </div>
  );

  /* -------- SVG chart geometry -------- */
  const W = 600, H = 200, PX = 40, PY = 20;
  const prices   = history.map(h => parseFloat(h.newPrice));
  const minP     = Math.min(...prices);
  const maxP     = Math.max(...prices);
  const range    = maxP - minP || 1;
  const chartW   = W - PX * 2;
  const chartH   = H - PY * 2;

  const pts = history.map((h, i) => {
    const x = PX + (i / Math.max(history.length - 1, 1)) * chartW;
    const y = PY + chartH - ((parseFloat(h.newPrice) - minP) / range) * chartH;
    return [x, y, h];
  });

  const polyline = pts.map(([x, y]) => `${x},${y}`).join(' ');
  const fillPath = `M${pts[0][0]},${pts[0][1]} ` +
                   pts.map(([x, y]) => `L${x},${y}`).join(' ') +
                   ` L${pts[pts.length - 1][0]},${PY + chartH} L${pts[0][0]},${PY + chartH} Z`;

  const latest = history[history.length - 1];
  const first  = history[0];
  const pctChange = (((parseFloat(latest.newPrice) - parseFloat(first.newPrice))
                        / parseFloat(first.newPrice)) * 100).toFixed(1);
  const isDown = parseFloat(pctChange) < 0;

  const fmtDate = (iso) => {
    const d = new Date(iso);
    return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: '2-digit' });
  };

  return (
    <div style={{
      background: 'var(--card)',
      border: '1px solid var(--border)',
      borderRadius: '12px',
      padding: '20px',
      marginTop: '24px',
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 700 }}>📊 Price History</h3>
          <p style={{ margin: '2px 0 0', color: 'var(--muted)', fontSize: '0.82rem' }}>
            {history.length} data point{history.length !== 1 ? 's' : ''}
          </p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontWeight: 800, fontSize: '1.2rem' }}>
            ₹{parseFloat(latest.newPrice).toLocaleString('en-IN')}
          </div>
          <div style={{ fontSize: '0.82rem', color: isDown ? '#16a34a' : '#dc2626', fontWeight: 600 }}>
            {isDown ? '▼' : '▲'} {Math.abs(pctChange)}% since first recorded
          </div>
        </div>
      </div>

      <div style={{ overflowX: 'auto' }}>
        <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', minWidth: 320, maxHeight: 200 }}>
          <defs>
            <linearGradient id="phGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#6C2BD9" stopOpacity="0.25" />
              <stop offset="100%" stopColor="#6C2BD9" stopOpacity="0.02" />
            </linearGradient>
          </defs>

          {/* Grid lines */}
          {[0, 0.25, 0.5, 0.75, 1].map((t, i) => {
            const y = PY + t * chartH;
            const val = maxP - t * range;
            return (
              <g key={i}>
                <line x1={PX} y1={y} x2={W - PX} y2={y}
                      stroke="var(--border)" strokeWidth="1" strokeDasharray="4 4" />
                <text x={PX - 4} y={y + 4} textAnchor="end"
                      fontSize="10" fill="var(--muted)">
                  ₹{Math.round(val).toLocaleString('en-IN')}
                </text>
              </g>
            );
          })}

          {/* Fill area */}
          <path d={fillPath} fill="url(#phGrad)" />

          {/* Line */}
          <polyline points={polyline} fill="none" stroke="#6C2BD9" strokeWidth="2.5"
                    strokeLinejoin="round" strokeLinecap="round" />

          {/* Data points */}
          {pts.map(([x, y, h], i) => (
            <g key={i}>
              <circle cx={x} cy={y} r="5" fill="#6C2BD9" stroke="white" strokeWidth="2" />
              <title>
                {fmtDate(h.changedAt)} — ₹{parseFloat(h.newPrice).toLocaleString('en-IN')}
                {h.reason ? ` (${h.reason})` : ''}
              </title>
            </g>
          ))}
        </svg>
      </div>

      {/* Legend */}
      <div style={{
        display: 'flex', gap: 16, marginTop: 8, flexWrap: 'wrap',
        fontSize: '0.78rem', color: 'var(--muted)',
      }}>
        <span>🟢 Lowest: ₹{minP.toLocaleString('en-IN')}</span>
        <span>🔴 Highest: ₹{maxP.toLocaleString('en-IN')}</span>
        <span>📅 From {fmtDate(first.changedAt)} to {fmtDate(latest.changedAt)}</span>
      </div>

      {/* Price change table */}
      {history.length > 1 && (
        <details style={{ marginTop: 12 }}>
          <summary style={{ cursor: 'pointer', fontSize: '0.82rem', color: 'var(--muted)' }}>
            View all {history.length} price changes
          </summary>
          <table style={{ width: '100%', marginTop: 8, fontSize: '0.8rem', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border)' }}>
                <th style={{ textAlign: 'left', padding: '4px 8px' }}>Date</th>
                <th style={{ textAlign: 'right', padding: '4px 8px' }}>Price</th>
                <th style={{ textAlign: 'left', padding: '4px 8px' }}>Reason</th>
              </tr>
            </thead>
            <tbody>
              {[...history].reverse().map((h, i) => (
                <tr key={i} style={{ borderBottom: '1px solid var(--border)' }}>
                  <td style={{ padding: '4px 8px', color: 'var(--muted)' }}>{fmtDate(h.changedAt)}</td>
                  <td style={{ padding: '4px 8px', textAlign: 'right', fontWeight: 600 }}>
                    ₹{parseFloat(h.newPrice).toLocaleString('en-IN')}
                  </td>
                  <td style={{ padding: '4px 8px', color: 'var(--muted)' }}>{h.reason || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      )}
    </div>
  );
}
