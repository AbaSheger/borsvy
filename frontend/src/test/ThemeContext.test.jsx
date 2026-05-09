import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, useTheme } from '../context/ThemeContext';

// Helper component to expose theme values
const ThemeConsumer = () => {
  const { theme, toggleTheme } = useTheme();
  return (
    <div>
      <span data-testid="theme">{theme}</span>
      <button onClick={toggleTheme}>toggle</button>
    </div>
  );
};

describe('ThemeContext', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('light', 'dark');
  });

  it('defaults to "light" when no preference stored and system is light', () => {
    vi.spyOn(window, 'matchMedia').mockReturnValue({ matches: false });
    render(<ThemeProvider><ThemeConsumer /></ThemeProvider>);
    expect(screen.getByTestId('theme').textContent).toBe('light');
  });

  it('defaults to "dark" when system prefers dark and no preference stored', () => {
    vi.spyOn(window, 'matchMedia').mockReturnValue({ matches: true });
    render(<ThemeProvider><ThemeConsumer /></ThemeProvider>);
    expect(screen.getByTestId('theme').textContent).toBe('dark');
  });

  it('reads saved theme from localStorage', () => {
    localStorage.setItem('theme', 'dark');
    render(<ThemeProvider><ThemeConsumer /></ThemeProvider>);
    expect(screen.getByTestId('theme').textContent).toBe('dark');
  });

  it('toggleTheme switches from light to dark', async () => {
    localStorage.setItem('theme', 'light');
    const user = userEvent.setup();
    render(<ThemeProvider><ThemeConsumer /></ThemeProvider>);
    await user.click(screen.getByRole('button', { name: 'toggle' }));
    expect(screen.getByTestId('theme').textContent).toBe('dark');
  });

  it('toggleTheme switches from dark to light', async () => {
    localStorage.setItem('theme', 'dark');
    const user = userEvent.setup();
    render(<ThemeProvider><ThemeConsumer /></ThemeProvider>);
    await user.click(screen.getByRole('button', { name: 'toggle' }));
    expect(screen.getByTestId('theme').textContent).toBe('light');
  });

  it('applies theme class to document root', () => {
    localStorage.setItem('theme', 'dark');
    render(<ThemeProvider><ThemeConsumer /></ThemeProvider>);
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('persists theme change to localStorage', async () => {
    localStorage.setItem('theme', 'light');
    const user = userEvent.setup();
    render(<ThemeProvider><ThemeConsumer /></ThemeProvider>);
    await user.click(screen.getByRole('button', { name: 'toggle' }));
    expect(localStorage.getItem('theme')).toBe('dark');
  });

  it('useTheme throws when used outside ThemeProvider', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<ThemeConsumer />)).toThrow();
    spy.mockRestore();
  });
});
