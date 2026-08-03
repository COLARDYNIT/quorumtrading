/**
 * The backend serialises both `consensusDecision.finalAction` and
 * `agentDecision.suggestedAction` as JSON envelopes (e.g.
 * `{"action":"HOLD","symbol":"VRT","quantity":0.0,...}`) for richer payloads
 * — but older rows store a plain string ("BUY"). Anywhere the UI compares or
 * displays these as a single label, route through this helper so the
 * comparison happens on the canonical `action` field.
 */
export function parseActionLabel(value: string | undefined | null): string {
  if (value == null) return '';
  if (typeof value !== 'string') return String(value).toUpperCase();
  const trimmed = value.trim();
  if (!trimmed) return '';
  if (trimmed.startsWith('{')) {
    try {
      const parsed = JSON.parse(trimmed);
      const action = parsed?.action;
      return typeof action === 'string' ? action.toUpperCase() : '';
    } catch {
      return '';
    }
  }
  return trimmed.toUpperCase();
}
