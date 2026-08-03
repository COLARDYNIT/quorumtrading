import { calculateDurationDays, getDurationWarning } from '../backtestDuration';

describe('calculateDurationDays', () => {
  it('returns 0 for empty start date', () => {
    expect(calculateDurationDays('', '2025-06-01')).toBe(0);
  });

  it('returns 0 for empty end date', () => {
    expect(calculateDurationDays('2025-01-01', '')).toBe(0);
  });

  it('returns 0 for invalid dates', () => {
    expect(calculateDurationDays('not-a-date', '2025-06-01')).toBe(0);
  });

  it('returns 0 when end is before start', () => {
    expect(calculateDurationDays('2025-06-01', '2025-01-01')).toBe(0);
  });

  it('returns 0 when dates are equal', () => {
    expect(calculateDurationDays('2025-01-01', '2025-01-01')).toBe(0);
  });

  it('calculates days correctly for a short period', () => {
    expect(calculateDurationDays('2025-01-01', '2025-01-15')).toBe(14);
  });

  it('calculates days correctly for 90 days', () => {
    // 2025-01-01 to 2025-04-01 = 90 days
    expect(calculateDurationDays('2025-01-01', '2025-04-01')).toBe(90);
  });

  it('calculates days correctly for a year', () => {
    // 2025-01-01 to 2026-01-01 = 365 days
    expect(calculateDurationDays('2025-01-01', '2026-01-01')).toBe(365);
  });

  it('handles datetime-local format', () => {
    expect(calculateDurationDays('2025-01-01T00:00', '2025-01-15T12:00')).toBe(14);
  });
});

describe('getDurationWarning', () => {
  it('returns none for short backtests (< 90 days)', () => {
    const result = getDurationWarning('2025-01-01', '2025-03-01');
    expect(result.level).toBe('none');
    expect(result.title).toBe('');
    expect(result.message).toBe('');
  });

  it('returns none for empty dates', () => {
    const result = getDurationWarning('', '');
    expect(result.level).toBe('none');
  });

  it('returns mild for 90 days', () => {
    const result = getDurationWarning('2025-01-01', '2025-04-01');
    expect(result.level).toBe('mild');
    expect(result.days).toBe(90);
    expect(result.title).toBe('Backtest Duration Notice');
    expect(result.message).toContain('90 days');
  });

  it('returns mild for 179 days (boundary)', () => {
    // 2025-01-01 to 2025-06-30 = 180 days, so 179 should be mild
    const result = getDurationWarning('2025-01-01', '2025-06-29');
    expect(result.level).toBe('mild');
  });

  it('returns moderate for 180 days', () => {
    const result = getDurationWarning('2025-01-01', '2025-06-30');
    expect(result.level).toBe('moderate');
    expect(result.days).toBe(180);
    expect(result.title).toBe('Long Backtest Duration');
    expect(result.message).toContain('180 days');
  });

  it('returns moderate for 364 days (boundary)', () => {
    const result = getDurationWarning('2025-01-01', '2025-12-31');
    expect(result.level).toBe('moderate');
    expect(result.days).toBe(364);
  });

  it('returns severe for 365 days', () => {
    const result = getDurationWarning('2025-01-01', '2026-01-01');
    expect(result.level).toBe('severe');
    expect(result.days).toBe(365);
    expect(result.title).toBe('Extended Backtest Duration');
    expect(result.message).toContain('365 days');
    expect(result.message).toContain('12 months');
  });

  it('returns severe for very long backtest', () => {
    const result = getDurationWarning('2024-01-01', '2026-01-01');
    expect(result.level).toBe('severe');
    expect(result.days).toBe(731);
    expect(result.message).toContain('24 months');
  });

  it('all non-none levels have a title and message', () => {
    const levels = [
      getDurationWarning('2025-01-01', '2025-04-01'), // mild
      getDurationWarning('2025-01-01', '2025-07-01'), // moderate
      getDurationWarning('2025-01-01', '2026-01-01'), // severe
    ];

    for (const w of levels) {
      expect(w.title).not.toBe('');
      expect(w.message).not.toBe('');
      expect(w.message).toContain('AI');
    }
  });
});
