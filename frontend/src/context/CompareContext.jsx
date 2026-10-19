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

/** Four columns is standard across modern e-commerce and fits responsive layouts. */
export const COMPARE_LIMIT = 4;

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
    if (ids.includes(productId)) {
      setIds((prev) => prev.filter((id) => id !== productId));
      return true;
    }
    if (ids.length >= COMPARE_LIMIT) {
      return false;
    }
    setIds((prev) => [...prev, productId]);
    return true;
  };

  const add = (productId) => {
    if (ids.includes(productId)) return true;
    if (ids.length >= COMPARE_LIMIT) return false;
    setIds((prev) => [...prev, productId]);
    return true;
  };

  const remove = (productId) => setIds((prev) => prev.filter((id) => id !== productId));
  const clear = () => setIds([]);
  const isSelected = (productId) => ids.includes(productId);
  const isFull = ids.length >= COMPARE_LIMIT;

  return (
    <CompareContext.Provider
      value={{
        ids,
        toggle,
        add,
        remove,
        clear,
        isSelected,
        isFull,
        count: ids.length,
        limit: COMPARE_LIMIT,
      }}
    >
      {children}
    </CompareContext.Provider>
  );
}

export function useCompare() {
  return useContext(CompareContext);
}
