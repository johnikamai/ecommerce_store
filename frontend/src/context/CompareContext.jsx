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

/**
 * Categories of the selected products, kept in a separate key from the ids.
 *
 * The ids stay the single source of truth for what is selected; this is only
 * there so the product card can tell the shopper, at the moment they tick a
 * box, that they are mixing categories. It is safe for it to be empty or
 * stale - the Compare page re-derives the real categories from the catalogue.
 */
const CATEGORY_KEY = 'shopease_compare_categories';

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

function loadCategories() {
  try {
    const raw = localStorage.getItem(CATEGORY_KEY);
    const parsed = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

export function CompareProvider({ children }) {
  const [ids, setIds] = useState(loadIds);
  const [categories, setCategories] = useState(loadCategories);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(ids));
    } catch {
      // storage unavailable (private mode) — the comparison still works for
      // this session, it just will not survive a refresh.
    }
  }, [ids]);

  useEffect(() => {
    try {
      localStorage.setItem(CATEGORY_KEY, JSON.stringify(categories));
    } catch {
      // Not important enough to surface - the warning is a nicety.
    }
  }, [categories]);

  const forgetCategory = (productId) =>
    setCategories((prev) => {
      if (!(productId in prev)) return prev;
      const next = { ...prev };
      delete next[productId];
      return next;
    });

  const rememberCategory = (productId, category) => {
    if (!category) return;
    setCategories((prev) =>
      prev[productId] === category ? prev : { ...prev, [productId]: category },
    );
  };

  const toggle = (productId, category) => {
    if (ids.includes(productId)) {
      setIds((prev) => prev.filter((id) => id !== productId));
      forgetCategory(productId);
      return true;
    }
    if (ids.length >= COMPARE_LIMIT) {
      return false;
    }
    setIds((prev) => [...prev, productId]);
    rememberCategory(productId, category);
    return true;
  };

  const add = (productId, category) => {
    if (ids.includes(productId)) return true;
    if (ids.length >= COMPARE_LIMIT) return false;
    setIds((prev) => [...prev, productId]);
    rememberCategory(productId, category);
    return true;
  };

  const remove = (productId) => {
    setIds((prev) => prev.filter((id) => id !== productId));
    forgetCategory(productId);
  };

  const clear = () => {
    setIds([]);
    setCategories({});
  };

  const isSelected = (productId) => ids.includes(productId);
  const isFull = ids.length >= COMPARE_LIMIT;

  // Categories of the products currently selected, ignoring any that were
  // never recorded. A selection spanning more than one category is legal, it
  // just deserves a heads-up.
  const selectedCategories = ids.map((id) => categories[id]).filter(Boolean);
  const isMixedCategory = (category) => selectedCategories.some((c) => c !== category);

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
        isMixedCategory,
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
