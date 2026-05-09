import { describe, it, expect } from 'vitest';

/**
 * Tests for the inline utility functions in Analysis.jsx.
 * These are copy-pasted here to test in isolation.
 *
 * fmtCompact is critical — it's the function displaying market cap.
 * Bugs here make Market Cap always show '—' or wrong values.
 */

const fmt = (n, decimals = 2) =>
  n != null ? `$${Number(n).toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}` : '—';

const fmtCompact = (n) => {
  if (n == null) return '—';
  if (n >= 1e6) return `$${(n / 1e6).toFixed(2)}T`;
  if (n >= 1e3) return `$${(n / 1e3).toFixed(2)}B`;
  return `$${n.toFixed(2)}M`;
};

describe('fmtCompact (market cap formatter)', () => {
  it('returns — for null', () => {
    expect(fmtCompact(null)).toBe('—');
  });

  it('returns — for undefined', () => {
    expect(fmtCompact(undefined)).toBe('—');
  });

  it('formats trillions correctly — Finnhub sends millions', () => {
    // Finnhub marketCapitalization is in millions
    // Apple ~$3T = 3,000,000 in Finnhub units
    expect(fmtCompact(3_000_000)).toBe('$3.00T');
  });

  it('formats billions correctly', () => {
    // $50B = 50,000 in Finnhub units
    expect(fmtCompact(50_000)).toBe('$50.00B');
  });

  it('formats millions (small cap) correctly', () => {
    // $500M = 500 in Finnhub units
    expect(fmtCompact(500)).toBe('$500.00M');
  });

  it('BUG: returns $0.00M for zero, not —', () => {
    // If Finnhub returns 0 for market cap (unknown/missing),
    // fmtCompact returns '$0.00M' instead of '—'
    // The frontend StatBox will show '$0.00M' rather than '—'
    // This is a bug — the frontend should check > 0 before displaying
    expect(fmtCompact(0)).toBe('$0.00M');
    // This documents the bug: should be '—' but is '$0.00M'
  });

  it('handles boundary at exactly 1000 (= $1B)', () => {
    expect(fmtCompact(1000)).toBe('$1.00B');
  });

  it('handles boundary at exactly 1_000_000 (= $1T)', () => {
    expect(fmtCompact(1_000_000)).toBe('$1.00T');
  });

  it('handles very large value (>$10T)', () => {
    expect(fmtCompact(10_000_000)).toBe('$10.00T');
  });
});

describe('fmt (price formatter)', () => {
  it('formats normal price', () => {
    expect(fmt(175.5)).toBe('$175.50');
  });

  it('returns — for null', () => {
    expect(fmt(null)).toBe('—');
  });

  it('formats zero as $0.00 not —', () => {
    expect(fmt(0)).toBe('$0.00');
  });

  it('formats large price with commas', () => {
    expect(fmt(3500.0)).toBe('$3,500.00');
  });

  it('respects decimal parameter', () => {
    expect(fmt(100.123, 0)).toBe('$100');
  });
});
