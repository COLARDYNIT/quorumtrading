import { css } from 'styled-components';

// ============================================================================
// REFINED EFFECTS (Professional Terminal Aesthetic)
// ============================================================================
// Philosophy: Keep the terminal identity without the retro gimmicks.
// Clean, readable text. Subtle depth. Professional appearance.
// ============================================================================

// Scanlines - REMOVED for professional look
// The CRT scanline effect is too retro. We keep this as a no-op for backwards
// compatibility but it does nothing.
export const scanlines = css`
  /* Scanlines disabled for professional look */
`;

// Text glow - MINIMAL version
// Instead of glowing text everywhere, we use crisp text by default.
// This subtle version is only for intentional emphasis.
export const textGlow = css`
  text-shadow: var(--qt-text-glow);
`;

// Strong glow for headings - REDUCED
// Subtle glow that adds depth without being distracting
export const headingGlow = css`
  text-shadow: var(--qt-heading-glow);
`;

// Screen curvature - SIMPLIFIED
// Just clean borders and subtle shadow for depth
export const screenCurvature = css`
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
`;

// Flicker animation - REMOVED
// The constant flicker is distracting. Keeping as no-op for compatibility.
export const flicker = css`
  /* Flicker disabled for professional look */
`;

// Box glow - REFINED to subtle shadow
// Clean shadow that provides depth without the green glow
export const boxGlow = css`
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.3),
    0 4px 12px rgba(0, 0, 0, 0.15);
`;

// ============================================================================
// NEW PROFESSIONAL EFFECTS
// ============================================================================

// Subtle border highlight on hover/focus
export const borderGlow = css`
  border-color: ${p => p.theme.colors.primary};
  box-shadow: var(--qt-border-glow-shadow);
`;

// Clean elevation for cards
export const cardElevation = css`
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.2),
    0 2px 8px rgba(0, 0, 0, 0.1);
`;

// Focus ring for accessibility
export const focusRing = css`
  outline: 2px solid ${p => p.theme.colors.primary};
  outline-offset: 2px;
`;
