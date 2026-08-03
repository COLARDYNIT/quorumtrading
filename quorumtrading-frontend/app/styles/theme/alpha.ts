/**
 * Derive a translucent tint from a theme colour.
 *
 * Pill and chip fills are tints of a hue. Writing them as `rgba(255, 176, 0, 0.06)` literals bakes
 * in the *dark* theme's amber, so the fill stays phosphor-on-white when the light theme rebrands
 * that hue to `#b37800` — the same for green (`#00ff41` → `#065a1f`) and cyan (`#00d9ff` →
 * `#0077aa`). Passing the token through here keeps the tint following whichever theme is active.
 *
 * Several older components still carry those literals; this exists so new code stops adding them.
 */
export function alpha(color: string, amount: number): string {
  const hex = color.trim();
  if (!hex.startsWith('#')) {
    // Already rgb()/rgba()/named — nothing safe to do, so hand it back untouched.
    return hex;
  }

  const digits = hex.slice(1);
  const full = digits.length === 3
    ? digits.split('').map(c => c + c).join('')
    : digits;

  if (full.length !== 6) {
    return hex;
  }

  const r = parseInt(full.slice(0, 2), 16);
  const g = parseInt(full.slice(2, 4), 16);
  const b = parseInt(full.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${amount})`;
}
