import { useState, useEffect, useCallback } from 'react';
import { Users, X, UserPlus, Trash2, Split, CheckCircle2, Copy, ChevronDown, ChevronUp, Sparkles } from 'lucide-react';
import { useLanguage } from '../context/LanguageContext';

const INR_PRECISE_FORMATTER = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/* ─────────────────────────── helpers ─────────────────────────── */

function generateAvatar(name) {
  const colors = [
    ['#7C6AE8', '#A78BFA'],
    ['#E2603F', '#F2A65A'],
    ['#0E7490', '#5EEAD4'],
    ['#2F855A', '#9CC98B'],
    ['#E2447A', '#F6A6C1'],
    ['#D97706', '#FCD34D'],
  ];
  const idx = name
    .split('')
    .reduce((acc, c) => acc + c.charCodeAt(0), 0) % colors.length;
  return colors[idx];
}

function Avatar({ name, size }) {
  const s = size || 36;
  const words = name.trim().split(/\s+/);
  const initials = words.length >= 2
    ? (words[0][0] + words[1][0]).toUpperCase()
    : words[0].slice(0, 2).toUpperCase();
  const [from, to] = generateAvatar(name || 'X');
  return (
    <span
      style={{
        width: s,
        height: s,
        borderRadius: '50%',
        background: 'linear-gradient(135deg, ' + from + ', ' + to + ')',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: s * 0.38,
        fontWeight: 700,
        color: '#fff',
        flexShrink: 0,
        boxShadow: '0 2px 8px ' + from + '55',
        fontFamily: 'var(--font-heading)',
      }}
    >
      {initials || '?'}
    </span>
  );
}

/* ─────────────────────────── main component ─────────────────────── */

export default function BillSplitModal({ open, onClose, total }) {
  const { t } = useLanguage();

  const [friends, setFriends] = useState([
    { id: 1, name: 'You', amount: '', paid: false, copied: false },
  ]);
  const [newName, setNewName] = useState('');
  const [mode, setMode] = useState('equal');
  const [showSummary, setShowSummary] = useState(false);
  const [splitDone, setSplitDone] = useState(false);

  useEffect(() => {
    if (open) {
      setFriends([{ id: 1, name: 'You', amount: '', paid: false, copied: false }]);
      setNewName('');
      setMode('equal');
      setShowSummary(false);
      setSplitDone(false);
    }
  }, [open]);

  const totalCents = Math.max(0, Math.round(total * 100));
  const baseShareCents = friends.length ? Math.floor(totalCents / friends.length) : 0;
  const remainderCents = friends.length ? totalCents % friends.length : 0;
  const equalShares = friends.map((_, index) => (baseShareCents + (index < remainderCents ? 1 : 0)) / 100);

  const customTotal = friends.reduce((s, f) => s + (parseFloat(f.amount) || 0), 0);
  const remaining = +(total - customTotal).toFixed(2);

  const effectiveAmount = (f, index) =>
    mode === 'equal' ? equalShares[index] : parseFloat(f.amount) || 0;

  const addFriend = useCallback(() => {
    const name = newName.trim();
    if (!name) return;
    setFriends((prev) => [
      ...prev,
      { id: Date.now(), name, amount: '', paid: false, copied: false },
    ]);
    setNewName('');
  }, [newName]);

  const removeFriend = (id) =>
    setFriends((prev) => prev.filter((f) => f.id !== id));

  const setAmount = (id, val) =>
    setFriends((prev) =>
      prev.map((f) => (f.id === id ? { ...f, amount: val } : f))
    );

  const togglePaid = (id) =>
    setFriends((prev) =>
      prev.map((f) => (f.id === id ? { ...f, paid: !f.paid } : f))
    );

  const copyAmount = (id) => {
    const f = friends.find((fr) => fr.id === id);
    if (!f) return;
    const amt = effectiveAmount(f, friends.findIndex((fr) => fr.id === id));
    navigator.clipboard.writeText(amt.toFixed(2)).catch(() => {});
    setFriends((prev) =>
      prev.map((fr) => (fr.id === id ? { ...fr, copied: true } : fr))
    );
    setTimeout(
      () =>
        setFriends((prev) =>
          prev.map((fr) => (fr.id === id ? { ...fr, copied: false } : fr))
        ),
      2000
    );
  };

  const isCustomValid = mode === 'custom' && Math.abs(remaining) < 0.01;
  const canConfirm = mode === 'equal' || isCustomValid;
  const paidCount = friends.filter((f) => f.paid).length;

  if (!open) return null;

  const BD = {
    backdrop: {
      position: 'fixed',
      inset: 0,
      zIndex: 400,
      background: 'rgba(10,8,20,0.55)',
      backdropFilter: 'blur(4px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '16px',
      animation: 'bsFadeIn 180ms ease',
    },
    panel: {
      background: 'var(--color-card-bg)',
      borderRadius: 'var(--radius-xl)',
      boxShadow: 'var(--shadow-lg)',
      width: '100%',
      maxWidth: 520,
      maxHeight: '90vh',
      overflowY: 'auto',
      animation: 'bsSlideUp 220ms cubic-bezier(.22,.68,0,1.2)',
    },
    header: {
      backgroundImage: 'var(--gradient-premium)',
      borderRadius: 'var(--radius-xl) var(--radius-xl) 0 0',
      padding: '20px 24px 18px',
      display: 'flex',
      alignItems: 'flex-start',
      justifyContent: 'space-between',
      gap: 12,
    },
    body: { padding: '20px 24px 24px' },
    modeBar: {
      display: 'flex',
      gap: 8,
      marginBottom: 20,
      background: 'var(--color-card-bg-tint)',
      borderRadius: 'var(--radius-md)',
      padding: 4,
    },
    addRow: { display: 'flex', gap: 8, marginBottom: 16 },
    friendsCol: { display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 20 },
    actionRow: { display: 'flex', gap: 10 },
  };

  return (
    <div onClick={onClose} style={BD.backdrop}>
      <div onClick={(e) => e.stopPropagation()} style={BD.panel}>

        {/* ── header ── */}
        <div style={BD.header}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Split size={20} color="#fff" />
              <h2 style={{ fontFamily: 'var(--font-heading)', color: '#fff', fontSize: 20, fontWeight: 700, margin: 0 }}>
                {t('split.title')}
              </h2>
              <Sparkles size={16} color="rgba(255,255,255,0.7)" />
            </div>
            <p style={{ color: 'rgba(255,255,255,0.75)', fontSize: 13, marginTop: 4, marginBottom: 0 }}>
              {t('split.subtitle')}
            </p>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 2 }}>
            <span style={{
              background: 'rgba(255,255,255,0.18)',
              border: '1px solid rgba(255,255,255,0.3)',
              borderRadius: 'var(--radius-full)',
              padding: '4px 12px',
              color: '#fff',
              fontSize: 14,
              fontWeight: 700,
              fontFamily: 'var(--font-heading)',
            }}>
              {INR_PRECISE_FORMATTER.format(total)}
            </span>
            <button
              onClick={onClose}
              aria-label={t('split.close')}
              style={{
                background: 'rgba(255,255,255,0.15)',
                border: 'none',
                borderRadius: '50%',
                width: 32,
                height: 32,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer',
                color: '#fff',
              }}
            >
              <X size={16} />
            </button>
          </div>
        </div>

        <div style={BD.body}>

          {/* ── mode toggle ── */}
          <div style={BD.modeBar}>
            {['equal', 'custom'].map((m) => (
              <button
                key={m}
                onClick={() => setMode(m)}
                style={{
                  flex: 1,
                  padding: '8px 0',
                  borderRadius: 'calc(var(--radius-md) - 2px)',
                  border: 'none',
                  cursor: 'pointer',
                  fontFamily: 'var(--font-heading)',
                  fontWeight: 600,
                  fontSize: 13,
                  transition: 'all 200ms ease',
                  background: mode === m ? 'var(--color-primary)' : 'transparent',
                  color: mode === m ? '#fff' : 'var(--color-text-muted)',
                  boxShadow: mode === m ? 'var(--shadow-sm)' : 'none',
                }}
              >
                {m === 'equal' ? `⚖️ ${t('split.equal')}` : `✏️ ${t('split.custom')}`}
              </button>
            ))}
          </div>

          {/* ── add friend ── */}
          <div style={BD.addRow}>
            <input
              id="split-friend-name"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && addFriend()}
              placeholder={t('split.friendPlaceholder')}
              style={{
                flex: 1,
                padding: '10px 14px',
                borderRadius: 'var(--radius-md)',
                border: '1.5px solid var(--color-border)',
                background: 'var(--color-card-bg)',
                color: 'var(--color-text-primary)',
                fontSize: 14,
                outline: 'none',
                fontFamily: 'var(--font-body)',
              }}
            />
            <button
              onClick={addFriend}
              disabled={!newName.trim()}
              id="split-add-friend-btn"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '10px 16px',
                borderRadius: 'var(--radius-md)',
                border: 'none',
                background: 'var(--color-primary)',
                color: '#fff',
                fontWeight: 600,
                fontSize: 13,
                cursor: newName.trim() ? 'pointer' : 'not-allowed',
                opacity: newName.trim() ? 1 : 0.5,
                whiteSpace: 'nowrap',
              }}
            >
              <UserPlus size={15} /> {t('split.addFriend')}
            </button>
          </div>

          {/* ── friends list ── */}
          <div style={BD.friendsCol}>
            {friends.map((f, idx) => {
              const amt = effectiveAmount(f, idx);
              return (
                <div
                  key={f.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '12px 14px',
                    borderRadius: 'var(--radius-md)',
                    border: '1.5px solid ' + (f.paid ? 'var(--color-success)' : 'var(--color-border)'),
                    background: f.paid ? 'var(--color-success-bg)' : 'var(--color-card-bg)',
                    transition: 'all 200ms ease',
                  }}
                >
                  <Avatar name={idx === 0 ? t('split.you') : f.name} size={36} />

                  <div style={{ flex: 1, minWidth: 0 }}>
                    <p style={{ fontWeight: 600, fontSize: 14, color: 'var(--color-text-primary)', margin: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {idx === 0 ? t('split.you') : f.name}
                      {idx === 0 && (
                        <span style={{ marginLeft: 6, fontSize: 11, background: 'var(--color-primary)', color: '#fff', borderRadius: 'var(--radius-full)', padding: '1px 7px', fontWeight: 600 }}>
                          {t('split.you')}
                        </span>
                      )}
                    </p>
                    {mode === 'equal' ? (
                      <p style={{ margin: 0, fontSize: 13, color: 'var(--color-text-muted)' }}>
                        {t('split.pays')}{' '}
                        <strong style={{ color: 'var(--color-primary)', fontFamily: 'var(--font-heading)' }}>
                          {INR_PRECISE_FORMATTER.format(amt)}
                        </strong>
                      </p>
                    ) : (
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder={t('split.amountPlaceholder')}
                        value={f.amount}
                        onChange={(e) => setAmount(f.id, e.target.value)}
                        style={{
                          marginTop: 4,
                          width: '100%',
                          padding: '5px 10px',
                          borderRadius: 'var(--radius-sm)',
                          border: '1.5px solid var(--color-border)',
                          background: 'var(--color-card-bg)',
                          color: 'var(--color-text-primary)',
                          fontSize: 13,
                          outline: 'none',
                          boxSizing: 'border-box',
                        }}
                      />
                    )}
                  </div>

                  {/* copy */}
                  <button
                    title={t('split.copyAmount')}
                    onClick={() => copyAmount(f.id)}
                    style={{
                      background: f.copied ? 'var(--color-success-bg)' : 'var(--color-card-bg-tint)',
                      border: '1px solid ' + (f.copied ? 'var(--color-success)' : 'var(--color-border)'),
                      borderRadius: 'var(--radius-sm)',
                      width: 32,
                      height: 32,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      cursor: 'pointer',
                      color: f.copied ? 'var(--color-success)' : 'var(--color-text-muted)',
                      flexShrink: 0,
                    }}
                  >
                    {f.copied ? <CheckCircle2 size={14} /> : <Copy size={14} />}
                  </button>

                  {/* paid toggle */}
                  <button
                    onClick={() => togglePaid(f.id)}
                    style={{
                      background: f.paid ? 'var(--color-success)' : 'var(--color-card-bg-tint)',
                      border: 'none',
                      borderRadius: 'var(--radius-sm)',
                      padding: '5px 10px',
                      cursor: 'pointer',
                      color: f.paid ? '#fff' : 'var(--color-text-muted)',
                      fontSize: 12,
                      fontWeight: 600,
                      flexShrink: 0,
                      display: 'flex',
                      alignItems: 'center',
                      gap: 4,
                    }}
                  >
                    {f.paid ? <><CheckCircle2 size={13} /> {t('receipt.status.PAID')}</> : t('split.markPaid')}
                  </button>

                  {/* remove */}
                  {idx !== 0 && (
                    <button
                      title={t('common.remove')}
                      aria-label={t('split.removeFriend', { name: f.name })}
                      onClick={() => removeFriend(f.id)}
                      style={{
                        background: 'var(--color-error-bg)',
                        border: 'none',
                        borderRadius: 'var(--radius-sm)',
                        width: 32,
                        height: 32,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        cursor: 'pointer',
                        color: 'var(--color-error)',
                        flexShrink: 0,
                      }}
                    >
                      <Trash2 size={14} />
                    </button>
                  )}
                </div>
              );
            })}
          </div>

          {/* ── custom mode: remaining indicator ── */}
          {mode === 'custom' && (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '10px 14px',
              borderRadius: 'var(--radius-md)',
              background: isCustomValid ? 'var(--color-success-bg)' : remaining < 0 ? 'var(--color-error-bg)' : 'var(--color-warning-bg)',
              marginBottom: 16,
            }}>
              <span style={{ fontSize: 13, fontWeight: 600 }}>
                {isCustomValid ? `✅ ${t('split.perfect')}` : remaining < 0 ? `⚠️ ${t('split.overBy')}` : `⏳ ${t('split.remaining')}`}
              </span>
              <span style={{ fontFamily: 'var(--font-heading)', fontWeight: 700, color: isCustomValid ? 'var(--color-success)' : remaining < 0 ? 'var(--color-error)' : 'var(--color-warning)' }}>
                {isCustomValid ? '' : INR_PRECISE_FORMATTER.format(Math.abs(remaining))}
              </span>
            </div>
          )}

          {/* ── equal split info bar ── */}
          {mode === 'equal' && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderRadius: 'var(--radius-md)', background: 'var(--color-info-bg)', marginBottom: 16 }}>
              <span style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>
                <strong style={{ color: 'var(--color-text-primary)' }}>{t('split.equalSummary', { count: friends.length })}</strong>
              </span>
              <Users size={16} color="var(--color-info)" />
            </div>
          )}

          {/* ── progress bar ── */}
          <div style={{ marginBottom: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: 'var(--color-text-muted)', marginBottom: 6 }}>
              <span>{t('split.paymentProgress')}</span>
              <span>{t('split.paidCount', { paid: paidCount, total: friends.length })}</span>
            </div>
            <div style={{ height: 8, borderRadius: 'var(--radius-full)', background: 'var(--color-border)', overflow: 'hidden' }}>
              <div style={{
                height: '100%',
                borderRadius: 'var(--radius-full)',
                width: friends.length > 0 ? ((paidCount / friends.length) * 100) + '%' : '0%',
                background: 'linear-gradient(90deg, var(--color-success), #6EE7B7)',
                transition: 'width 300ms ease',
              }} />
            </div>
          </div>

          {/* ── summary collapsible ── */}
          <button
            onClick={() => setShowSummary((v) => !v)}
            style={{
              width: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              background: 'var(--color-card-bg-tint)',
              border: '1.5px solid var(--color-border)',
              borderRadius: 'var(--radius-md)',
              padding: '10px 14px',
              cursor: 'pointer',
              marginBottom: showSummary ? 0 : 16,
              fontSize: 13,
              fontWeight: 600,
              color: 'var(--color-text-secondary)',
            }}
          >
            <span>📋 {t(showSummary ? 'split.hideSummary' : 'split.viewSummary')}</span>
            {showSummary ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
          </button>

          {showSummary && (
            <div style={{ border: '1.5px solid var(--color-border)', borderTop: 'none', borderRadius: '0 0 var(--radius-md) var(--radius-md)', marginBottom: 16, overflow: 'hidden' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                <thead>
                  <tr style={{ background: 'var(--color-card-bg-tint)' }}>
                    <th style={{ padding: '8px 14px', textAlign: 'left', fontWeight: 600, color: 'var(--color-text-muted)' }}>{t('split.friend')}</th>
                    <th style={{ padding: '8px 14px', textAlign: 'right', fontWeight: 600, color: 'var(--color-text-muted)' }}>{t('split.amount')}</th>
                    <th style={{ padding: '8px 14px', textAlign: 'center', fontWeight: 600, color: 'var(--color-text-muted)' }}>{t('split.status')}</th>
                  </tr>
                </thead>
                <tbody>
                  {friends.map((f, index) => (
                    <tr key={f.id} style={{ borderTop: '1px solid var(--color-border)', background: f.paid ? 'var(--color-success-bg)' : 'transparent' }}>
                      <td style={{ padding: '10px 14px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <Avatar name={index === 0 ? t('split.you') : f.name} size={24} />
                          <span style={{ color: 'var(--color-text-primary)', fontWeight: 500 }}>{index === 0 ? t('split.you') : f.name}</span>
                        </div>
                      </td>
                      <td style={{ padding: '10px 14px', textAlign: 'right', fontWeight: 700, fontFamily: 'var(--font-heading)', color: 'var(--color-primary)' }}>
                        {INR_PRECISE_FORMATTER.format(effectiveAmount(f, index))}
                      </td>
                      <td style={{ padding: '10px 14px', textAlign: 'center' }}>
                        {f.paid
                          ? <span style={{ color: 'var(--color-success)', fontWeight: 600, fontSize: 12 }}>✓ {t('receipt.status.PAID')}</span>
                          : <span style={{ color: 'var(--color-text-muted)', fontSize: 12 }}>{t('receipt.status.PENDING')}</span>}
                      </td>
                    </tr>
                  ))}
                  <tr style={{ borderTop: '2px solid var(--color-border)', background: 'var(--color-card-bg-tint)' }}>
                    <td style={{ padding: '10px 14px', fontWeight: 700, color: 'var(--color-text-primary)' }}>Total</td>
                    <td style={{ padding: '10px 14px', textAlign: 'right', fontWeight: 700, fontFamily: 'var(--font-heading)', color: 'var(--color-text-primary)' }}>
                      {INR_PRECISE_FORMATTER.format(total)}
                    </td>
                    <td style={{ padding: '10px 14px', textAlign: 'center', fontSize: 12, color: 'var(--color-text-muted)' }}>
                      {paidCount}/{friends.length}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          )}

          {/* ── action buttons ── */}
          <div style={BD.actionRow}>
            <button
              onClick={onClose}
              style={{
                flex: 1,
                padding: '12px 0',
                borderRadius: 'var(--radius-md)',
                border: '1.5px solid var(--color-border)',
                background: 'transparent',
                color: 'var(--color-text-secondary)',
                fontWeight: 600,
                fontSize: 14,
                cursor: 'pointer',
                fontFamily: 'var(--font-heading)',
              }}
            >
              {t('common.cancel')}
            </button>

            <button
              id="split-confirm-btn"
              disabled={!canConfirm}
              onClick={() => setSplitDone(true)}
              style={{
                flex: 2,
                padding: '12px 0',
                borderRadius: 'var(--radius-md)',
                border: 'none',
                backgroundImage: canConfirm ? 'var(--gradient-premium)' : 'none',
                background: canConfirm ? undefined : 'var(--color-border)',
                color: canConfirm ? '#fff' : 'var(--color-text-muted)',
                fontWeight: 700,
                fontSize: 14,
                cursor: canConfirm ? 'pointer' : 'not-allowed',
                fontFamily: 'var(--font-heading)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 8,
                boxShadow: canConfirm ? 'var(--shadow-md)' : 'none',
              }}
            >
              {splitDone
                ? <><CheckCircle2 size={16} /> {t('split.confirmed')}</>
                : <><Split size={15} /> {t('split.confirm')}</>}
            </button>
          </div>

          {splitDone && (
            <p style={{ textAlign: 'center', marginTop: 12, fontSize: 13, color: 'var(--color-success)', fontWeight: 600 }}>
              {t('split.shareHint')}
            </p>
          )}
        </div>
      </div>

      <style>{`
        @keyframes bsFadeIn { from { opacity: 0 } to { opacity: 1 } }
        @keyframes bsSlideUp { from { opacity: 0; transform: translateY(24px) scale(0.97) } to { opacity: 1; transform: translateY(0) scale(1) } }
      `}</style>
    </div>
  );
}
