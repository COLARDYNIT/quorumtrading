/**
 * Shared keyframe animations for consistent landing-page-quality polish.
 * Import these into styled-components for professional micro-interactions.
 */
import { keyframes, css } from 'styled-components';

// === Entrance Animations ===

export const fadeIn = keyframes`
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
`;

export const fadeInUp = keyframes`
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
`;

export const fadeInDown = keyframes`
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
`;

export const slideInLeft = keyframes`
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
`;

export const slideInRight = keyframes`
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
`;

// === Loop Animations ===

export const pulse = keyframes`
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
`;

export const blink = keyframes`
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
`;

export const glowPulse = keyframes`
  0%, 100% {
    box-shadow: 0 0 20px var(--qt-glow-30), 0 0 40px var(--qt-glow-10);
  }
  50% {
    box-shadow: 0 0 30px var(--qt-glow-50), 0 0 60px var(--qt-glow-20);
  }
`;

// === Animation Presets (CSS snippets) ===

/**
 * Apply staggered fade-in-up animation to children.
 * Usage: ${staggeredFadeIn(0.1)} for 100ms delay between items
 */
export const staggeredFadeIn = (delayIncrement: number = 0.1) => css`
  opacity: 0;
  animation: ${fadeInUp} 0.5s ease-out forwards;

  &:nth-child(1) { animation-delay: ${delayIncrement * 0}s; }
  &:nth-child(2) { animation-delay: ${delayIncrement * 1}s; }
  &:nth-child(3) { animation-delay: ${delayIncrement * 2}s; }
  &:nth-child(4) { animation-delay: ${delayIncrement * 3}s; }
  &:nth-child(5) { animation-delay: ${delayIncrement * 4}s; }
  &:nth-child(6) { animation-delay: ${delayIncrement * 5}s; }
  &:nth-child(7) { animation-delay: ${delayIncrement * 6}s; }
  &:nth-child(8) { animation-delay: ${delayIncrement * 7}s; }
`;

/**
 * Card hover effect with lift and glow.
 * Extracts the landing page's polished hover behavior.
 */
export const cardHoverEffect = css`
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: var(--qt-shadow-card-hover);
  }
`;

/**
 * Card hover with custom accent color glow.
 */
export const cardHoverWithColor = (color: string) => css`
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;

  &:hover {
    transform: translateY(-4px);
    border-color: ${color};
    box-shadow: 0 0 20px ${color}33;
  }
`;

// === Box Shadow Presets ===

export const shadows = {
  card: 'var(--qt-shadow-card)',
  cardPremium: 'var(--qt-shadow-card-premium)',
  cardHover: 'var(--qt-shadow-card-hover)',
  glow: (color: string) => `0 0 20px ${color}33`,
};

// === Text Shadow Presets ===

export const textShadows = {
  primary: 'var(--qt-text-shadow-primary)',
  cyan: 'var(--qt-text-shadow-cyan)',
  amber: 'var(--qt-text-shadow-amber)',
  none: 'none',
};
