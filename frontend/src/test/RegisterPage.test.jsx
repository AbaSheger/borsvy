import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '../context/ThemeContext';

vi.mock('axios');
vi.mock('react-router-dom', async (importActual) => {
  const actual = await importActual();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('@react-oauth/google', () => ({
  GoogleLogin: () => <button data-testid="google-login">Google</button>,
}));

const mockNavigate = vi.fn();
const mockRegister = vi.fn();
const mockLoginWithGoogle = vi.fn();

vi.mock('../context/AuthContext', async (importActual) => {
  const actual = await importActual();
  return {
    ...actual,
    useAuth: () => ({
      user: null,
      loading: false,
      login: vi.fn(),
      loginWithGoogle: mockLoginWithGoogle,
      logout: vi.fn(),
      register: mockRegister,
    }),
  };
});

import RegisterPage from '../pages/RegisterPage';

const wrap = () => render(
  <MemoryRouter>
    <ThemeProvider><RegisterPage /></ThemeProvider>
  </MemoryRouter>
);

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRegister.mockReset();
  });

  it('renders email and password inputs', () => {
    wrap();
    expect(screen.getByPlaceholderText('Email')).toBeTruthy();
    expect(screen.getByPlaceholderText(/Password/i)).toBeTruthy();
  });

  it('renders "Create account" button', () => {
    wrap();
    expect(screen.getByRole('button', { name: /create account/i })).toBeTruthy();
  });

  it('renders "Sign in" link to /login', () => {
    wrap();
    const link = screen.getByText('Sign in');
    expect(link.getAttribute('href')).toBe('/login');
  });

  it('renders "Terms of Service" and "Privacy Policy" links', () => {
    wrap();
    expect(screen.getByText('Terms of Service').getAttribute('href')).toBe('/terms');
    expect(screen.getByText('Privacy Policy').getAttribute('href')).toBe('/privacy');
  });

  it('does not register when email is empty', async () => {
    const user = userEvent.setup();
    wrap();
    await user.type(screen.getByPlaceholderText(/Password/i), 'password123');
    await user.click(screen.getByRole('button', { name: /create account/i }));
    expect(mockRegister).not.toHaveBeenCalled();
  });

  it('does not register when password is too short', async () => {
    const user = userEvent.setup();
    wrap();
    await user.type(screen.getByPlaceholderText('Email'), 'user@example.com');
    await user.type(screen.getByPlaceholderText(/Password/i), 'short');
    // Agree to terms
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /create account/i }));
    expect(mockRegister).not.toHaveBeenCalled();
  });

  it('does not register when terms not accepted', async () => {
    const user = userEvent.setup();
    wrap();
    await user.type(screen.getByPlaceholderText('Email'), 'user@example.com');
    await user.type(screen.getByPlaceholderText(/Password/i), 'password123');
    // Don't click checkbox
    await user.click(screen.getByRole('button', { name: /create account/i }));
    expect(mockRegister).not.toHaveBeenCalled();
  });

  it('calls register and navigates on success', async () => {
    mockRegister.mockResolvedValueOnce({});
    const user = userEvent.setup();
    wrap();

    await user.type(screen.getByPlaceholderText('Email'), 'new@example.com');
    await user.type(screen.getByPlaceholderText(/Password/i), 'password123');
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => expect(mockRegister).toHaveBeenCalledWith('new@example.com', 'password123'));
    expect(mockNavigate).toHaveBeenCalledWith('/');
  });

  it('does not navigate when register throws', async () => {
    mockRegister.mockRejectedValueOnce(new Error('Email taken'));
    const user = userEvent.setup();
    wrap();

    await user.type(screen.getByPlaceholderText('Email'), 'taken@example.com');
    await user.type(screen.getByPlaceholderText(/Password/i), 'password123');
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => expect(mockRegister).toHaveBeenCalled());
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
