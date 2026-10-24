import { useState, useEffect } from 'react';
import { useCart } from '../context/CartContext';

/**
 * F9: Budget Shopping Mode
 *
 * A floating budget tracker that the user can activate on the Products page.
 * The user sets a total budget; as they add items to the cart the remaining
 * amount is shown with a colour-coded progress bar.
 *
 * Usage: <BudgetMode />  (rendered once inside Products.jsx)
 */
export default function BudgetMode() {
  const [active,      setActive]      = useState(false);
  const [budget,      setBudget]      = useState('');
  const [inputBudget, setInputBudget] = useState('');
  const [editing,     setEditing]     = useState(false);
  const { cartItems = [] } = useCart();

  // Compute cart total from cartItems (each item has price + quantity)
  const cartTotal = cartItems.reduce((sum, item) => {
    const price = parseFloat(item.price ?? item.product?.price ?? 0);
    const qty   = parseInt(item.quantity ?? item.qty ?? 1, 10);
    return sum + price * qty;
  }, 0);

  const budgetNum  = parseFloat(budget) || 0;
  const remaining  = budgetNum - cartTotal;
  const pct        = budgetNum > 0 ? Math.min(100, (cartTotal / budgetNum) * 100) : 0;
  const overBudget = remaining < 0;

  const barColor = pct < 60 ? '#16a34a' : pct < 85 ? '#d97706' : '#dc2626';

  const handleSet = () => {
    const v = parseFloat(inputBudget);
    if (!isNaN(v) && v > 0) {
      setBudget(v.toString());
      setEditing(false);
    }
  };

  if (!active) {
    return (
      <button
        onClick={() => { setActive(true); setEditing(true); }}
        style={{
          display: 'flex', alignItems: 'center', gap: 6,
          padding: '8px 16px', borderRadius: '20px',
          border: '1.5px solid #6C2BD9',
          background: 'transparent', color: '#6C2BD9',
          cursor: 'pointer', fontWeight: 600, fontSize: '0.85rem',
          transition: 'all 0.2s',
        }}
        onMouseEnter={e => { e.currentTarget.style.background = '#6C2BD9'; e.currentTarget.style.color = '#fff'; }}
        onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = '#6C2BD9'; }}
        title="Enable Budget Shopping Mode"
      >
        💰 Budget Mode
      </button>
    );
  }

  return (
    <div style={{
      background: 'var(--card)',
      border: `1.5px solid ${overBudget ? '#dc2626' : '#6C2BD9'}`,
      borderRadius: '12px',
      padding: '14px 18px',
      minWidth: '260px',
      boxShadow: '0 4px 20px rgba(108,43,217,0.12)',
      transition: 'border-color 0.3s',
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontWeight: 700, fontSize: '0.88rem' }}>💰 Budget Mode</span>
        <button
          onClick={() => { setActive(false); setBudget(''); }}
          style={{ background: 'none', border: 'none', cursor: 'pointer',
                   color: 'var(--muted)', fontSize: '1rem', lineHeight: 1, padding: 2 }}
          title="Disable budget mode"
        >✕</button>
      </div>

      {editing || !budget ? (
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <span style={{ fontSize: '0.9rem' }}>₹</span>
          <input
            type="number"
            min="1"
            value={inputBudget}
            onChange={e => setInputBudget(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSet()}
            placeholder="Enter your budget"
            autoFocus
            style={{
              flex: 1, padding: '6px 10px', borderRadius: '6px',
              border: '1px solid var(--border)',
              background: 'var(--bg)', color: 'var(--text)', fontSize: '0.88rem',
            }}
          />
          <button onClick={handleSet} style={{
            padding: '6px 12px', borderRadius: '6px',
            background: '#6C2BD9', color: '#fff', border: 'none',
            cursor: 'pointer', fontWeight: 600, fontSize: '0.82rem',
          }}>Set</button>
        </div>
      ) : (
        <>
          {/* Progress bar */}
          <div style={{ background: 'var(--border)', borderRadius: 4, height: 8, marginBottom: 8, overflow: 'hidden' }}>
            <div style={{
              height: '100%', borderRadius: 4,
              width: `${Math.min(100, pct)}%`,
              background: barColor,
              transition: 'width 0.4s, background 0.4s',
            }} />
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.82rem' }}>
            <span style={{ color: 'var(--muted)' }}>
              Budget: <strong>₹{budgetNum.toLocaleString('en-IN')}</strong>
            </span>
            <span style={{ color: overBudget ? '#dc2626' : '#16a34a', fontWeight: 700 }}>
              {overBudget
                ? `⚠️ Over by ₹${Math.abs(remaining).toLocaleString('en-IN')}`
                : `₹${remaining.toLocaleString('en-IN')} left`
              }
            </span>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4, fontSize: '0.78rem', color: 'var(--muted)' }}>
            <span>Cart: ₹{cartTotal.toLocaleString('en-IN')}</span>
            <button
              onClick={() => { setEditing(true); setInputBudget(budget); }}
              style={{ background: 'none', border: 'none', cursor: 'pointer',
                       color: '#6C2BD9', fontSize: '0.78rem', padding: 0, textDecoration: 'underline' }}
            >Change budget</button>
          </div>
        </>
      )}
    </div>
  );
}
