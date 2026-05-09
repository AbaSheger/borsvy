import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useDebounce } from '../hooks/useDebounce';

describe('useDebounce', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('returns initial value immediately', () => {
    const { result } = renderHook(() => useDebounce('hello', 300));
    expect(result.current).toBe('hello');
  });

  it('does not update before delay has passed', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useDebounce(value, 300),
      { initialProps: { value: 'hello' } }
    );

    rerender({ value: 'world' });
    act(() => vi.advanceTimersByTime(200));

    expect(result.current).toBe('hello');
  });

  it('updates after delay has passed', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useDebounce(value, 300),
      { initialProps: { value: 'hello' } }
    );

    rerender({ value: 'world' });
    act(() => vi.advanceTimersByTime(300));

    expect(result.current).toBe('world');
  });

  it('resets timer when value changes before delay expires', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useDebounce(value, 300),
      { initialProps: { value: 'a' } }
    );

    rerender({ value: 'ab' });
    act(() => vi.advanceTimersByTime(200));

    rerender({ value: 'abc' });
    act(() => vi.advanceTimersByTime(200));

    // 200ms after last change — should not have fired yet
    expect(result.current).toBe('a');

    act(() => vi.advanceTimersByTime(100));
    // Now 300ms after 'abc' was set — should update
    expect(result.current).toBe('abc');
  });

  it('works with numeric values', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useDebounce(value, 100),
      { initialProps: { value: 0 } }
    );

    rerender({ value: 42 });
    act(() => vi.advanceTimersByTime(100));

    expect(result.current).toBe(42);
  });

  it('works with zero delay', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useDebounce(value, 0),
      { initialProps: { value: 'start' } }
    );

    rerender({ value: 'end' });
    act(() => vi.advanceTimersByTime(0));

    expect(result.current).toBe('end');
  });
});
