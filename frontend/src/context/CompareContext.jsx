import { createContext, useContext, useEffect, useState } from 'react';

/**
 * Products the shopper has ticked to compare.
 *
 * Only ids are stored, never whole product objects. A stored snapshot goes
 * stale the moment a price or stock level changes, and a comparison table that
 * quietly shows yesterday's price is worse than no table at all - the Compare
 * page re-reads the live catalogue and fills the rows from that.
 */
const CompareContext = createContext();

const STORAGE_KEY = 'shopease_compare';

/** Three columns is the most a table stays readable on a laptop. */
export const COMPARE_LIMIT = 3;

function loadIds() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.slice(0, COMPARE_LIMIT) : [];
  } catch {
    return [];
  }
}

export function CompareProvider({ children }) {
  const [ids, setIds] = useState(loadIds);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(ids));
    } catch {
      // storage unavailable (private mode) — the comparison still works for
      // this session, it just will not survive a refresh.
    }
  }, [ids]);

  const toggle = (productId) => {
    let rejected = false;
    setIds((prev) => {
      if (prev.includes(productId)) return prev.filter((id) => id !== productId);
      if (prev.length >= COMPARE_LIMIT) {
        rejected = true;
        return prev;
      }
      return [...prev, productId];
    });
    return !rejected;
  };

  const remove = (productId) => setIds((prev) => prev.filter((id) => id !== productId));
  const clear = () => setIds([]);
  const isSelected = (productId) => ids.includes(productId);
  const isFull = ids.length >= COMPARE_LIMIT;

  return (
    <CompareContext.Provider
      value={{ ids, toggle, remove, clear, isSelected, isFull, count: ids.length }}
    >
      {children}
    </CompareContext.Provider>
  );
}

export function useCompare() {
  return useContext(CompareContext);
}
