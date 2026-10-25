import { useState, useCallback } from 'react';
import axiosClient from '../api/axiosClient';

/**
 * F8: Product Compatibility Checker component.
 *
 * Lets the user search for a second product by name and checks whether
 * it is compatible with the currently viewed product.  Calls
 * GET /api/compatibility and renders a verdict card.
 */
export default function CompatibilityChecker({ currentProduct, catalog = [] }) {
  const [query,   setQuery]   = useState('');
  const [picked,  setPicked]  = useState(null);   // { id, name, category }
  const [result,  setResult]  = useState(null);
  const [loading, setLoading] = useState(false);
  const [error,   setError]   = useState('');

  const suggestions = query.length > 1
    ? catalog
        .filter(p => p.id !== currentProduct?.id &&
                     p.name?.toLowerCase().includes(query.toLowerCase()))
        .slice(0, 8)
    : [];

  const check = useCallback(async (secondProduct) => {
    if (!currentProduct?.id || !secondProduct?.id) return;
    setLoading(true);
    setError('');
    setResult(null);
    try {
      const { data } = await axiosClient.get('/compatibility', { params: {
        productA: currentProduct.id, productB: secondProduct.id,
      } });
      setResult(data);
    } catch (e) {
      setError('Could not check compatibility. Please try again.');
    } finally {
      setLoading(false);
    }
  }, [currentProduct]);

  const select = (product) => {
    setPicked(product);
    setQuery(product.name);
    check(product);
  };

  const scoreColor = (score) => {
    if (score >= 70) return '#16a34a';
    if (score >= 45) return '#d97706';
    return '#dc2626';
  };

  return (
    <div style={{
      background: 'var(--card)',
      border: '1px solid var(--border)',
      borderRadius: '12px',
      padding: '20px',
      marginTop: '24px',
    }}>
      <h3 style={{ margin: '0 0 4px', fontSize: '1rem', fontWeight: 700 }}>
        🔌 Compatibility Checker
      </h3>
      <p style={{ margin: '0 0 14px', color: 'var(--muted)', fontSize: '0.85rem' }}>
        Search for a second product to check if it works with <strong>{currentProduct?.name}</strong>.
      </p>

      <div style={{ position: 'relative' }}>
        <input
          value={query}
          onChange={e => { setQuery(e.target.value); setPicked(null); setResult(null); }}
          placeholder="Search a product (e.g. USB-C Hub, Laptop Bag…)"
          style={{
            width: '100%', boxSizing: 'border-box',
            padding: '10px 14px', borderRadius: '8px',
            border: '1px solid var(--border)',
            background: 'var(--bg)', color: 'var(--text)',
            fontSize: '0.9rem',
          }}
        />
        {suggestions.length > 0 && !picked && (
          <ul style={{
            position: 'absolute', top: '100%', left: 0, right: 0,
            background: 'var(--card)', border: '1px solid var(--border)',
            borderRadius: '8px', marginTop: 4, padding: 0,
            listStyle: 'none', zIndex: 50,
            boxShadow: '0 4px 16px rgba(0,0,0,0.12)',
            maxHeight: '240px', overflowY: 'auto',
          }}>
            {suggestions.map(s => (
              <li key={s.id}
                  onClick={() => select(s)}
                  style={{
                    padding: '10px 14px', cursor: 'pointer',
                    borderBottom: '1px solid var(--border)',
                    fontSize: '0.88rem',
                  }}
                  onMouseEnter={e => e.currentTarget.style.background = 'var(--bg)'}
                  onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
              >
                <strong>{s.name}</strong>
                <span style={{ color: 'var(--muted)', marginLeft: 8, fontSize: '0.8rem' }}>
                  {s.category}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {loading && (
        <p style={{ marginTop: 12, color: 'var(--muted)', fontSize: '0.88rem' }}>
          ⏳ Checking compatibility…
        </p>
      )}
      {error && (
        <p style={{ marginTop: 12, color: '#dc2626', fontSize: '0.88rem' }}>{error}</p>
      )}

      {result && !loading && (
        <div style={{
          marginTop: 16,
          padding: '16px',
          borderRadius: '10px',
          border: `2px solid ${scoreColor(result.score)}22`,
          background: `${scoreColor(result.score)}0a`,
        }}>
          {/* Score ring */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 12 }}>
            <div style={{
              width: 56, height: 56, borderRadius: '50%',
              border: `4px solid ${scoreColor(result.score)}`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              flexShrink: 0,
              fontWeight: 800, fontSize: '1rem', color: scoreColor(result.score),
            }}>
              {result.assessment === 'UNKNOWN' ? '?' : result.compatible ? '✓' : '×'}
            </div>
            <div>
              <div style={{ fontWeight: 700, marginBottom: 2 }}>
                {result.assessment === 'UNKNOWN' ? 'Specifications insufficient' : result.compatible ? 'Shared interface found' : 'Specification conflict'}
              </div>
              <div style={{ fontSize: '0.85rem', color: 'var(--muted)' }}>
                {result.verdict}
              </div>
            </div>
          </div>
          <ul style={{ margin: 0, paddingLeft: 18, fontSize: '0.85rem', color: 'var(--text)' }}>
            {result.reasons?.map((r, i) => <li key={i} style={{ marginBottom: 4 }}>{r}</li>)}
          </ul>
        </div>
      )}
    </div>
  );
}
