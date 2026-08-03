'use client';

import { useState, useEffect, useCallback } from 'react';

/**
 * Hook for managing contextual help state with smart defaults.
 *
 * Behavior:
 * - New users (no items): Info box expanded by default
 * - After minimizing: Collapses to [?] icon, state persisted
 * - Clicking [?]: Re-expands the info box
 *
 * @param key - Unique key for localStorage persistence (e.g., 'research', 'strategies')
 * @param hasItems - Whether the user has any items in this section
 * @returns Object with expansion state and controls
 *
 * @example
 * ```tsx
 * const { isExpanded, toggle, showHelpIcon } = useContextualHelp('research', researchList.length > 0);
 *
 * return (
 *   <>
 *     <Header>
 *       <Title>Market Research</Title>
 *       {showHelpIcon && <HelpIcon onClick={toggle} />}
 *     </Header>
 *     {isExpanded && <InfoBox onMinimize={toggle} />}
 *   </>
 * );
 * ```
 */
export function useContextualHelp(key: string, hasItems: boolean) {
  // Track if we've loaded from localStorage yet (for SSR safety)
  const [isHydrated, setIsHydrated] = useState(false);

  // Default to expanded for new users, will be updated after hydration
  const [isExpanded, setIsExpanded] = useState(true);

  // Load persisted state from localStorage after hydration
  useEffect(() => {
    if (typeof window !== 'undefined') {
      const storageKey = `contextual-help-${key}-collapsed`;
      const wasCollapsed = localStorage.getItem(storageKey) === 'true';

      // Show expanded if: no items AND not manually collapsed before
      const shouldExpand = !hasItems && !wasCollapsed;
      setIsExpanded(shouldExpand);
      setIsHydrated(true);
    }
  }, [key, hasItems]);

  // Toggle expansion and persist state
  const toggle = useCallback(() => {
    setIsExpanded(prev => {
      const newValue = !prev;
      if (typeof window !== 'undefined') {
        const storageKey = `contextual-help-${key}-collapsed`;
        // Store collapsed state (inverted from expanded)
        localStorage.setItem(storageKey, String(!newValue));
      }
      return newValue;
    });
  }, [key]);

  // Expand the help (for programmatic use)
  const expand = useCallback(() => {
    setIsExpanded(true);
    if (typeof window !== 'undefined') {
      const storageKey = `contextual-help-${key}-collapsed`;
      localStorage.setItem(storageKey, 'false');
    }
  }, [key]);

  // Collapse the help (for programmatic use)
  const collapse = useCallback(() => {
    setIsExpanded(false);
    if (typeof window !== 'undefined') {
      const storageKey = `contextual-help-${key}-collapsed`;
      localStorage.setItem(storageKey, 'true');
    }
  }, [key]);

  return {
    /** Whether the info box should be shown expanded */
    isExpanded: isHydrated ? isExpanded : !hasItems,
    /** Toggle between expanded and collapsed states */
    toggle,
    /** Expand the info box */
    expand,
    /** Collapse the info box */
    collapse,
    /** Whether to show the [?] help icon (when collapsed) */
    showHelpIcon: isHydrated ? !isExpanded : hasItems,
    /** Whether hydration is complete (for avoiding flicker) */
    isHydrated,
  };
}
