'use client';

import { useState, useEffect } from 'react';

const SPINNER_FRAMES = ['⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'];

/**
 * Braille spinner hook — cycles through Unicode braille characters to show a
 * terminal-style "thinking" indicator.
 *
 * @param active  Whether the spinner should be animating
 * @param intervalMs  Frame interval in milliseconds (default 80)
 * @returns The current spinner frame character
 */
export function useBrailleSpinner(active: boolean, intervalMs = 80): string {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    if (!active) {
      setIndex(0);
      return;
    }
    const id = setInterval(() => {
      setIndex(prev => (prev + 1) % SPINNER_FRAMES.length);
    }, intervalMs);
    return () => clearInterval(id);
  }, [active, intervalMs]);

  return SPINNER_FRAMES[index];
}
