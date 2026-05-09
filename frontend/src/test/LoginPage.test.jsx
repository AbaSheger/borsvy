import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '../context/ThemeContext';
import { AuthProvider } from '../context/AuthContext';

vi.mock('axios');
vi.mock('react-router-dom', async (importActual) => {
  const actual = await importActual();
  return { ...actual, useNavigate: () => mockNavigate };
});

// Mock GoogleLogin — we don't test OAuth flows here
vi.mock('@react-oauth/google', () => ({
  GoogleLogin: ({ onError }) => (
    <button data-testid="google-login" onClick={onError}>Google</button>
  ),
}));

const mockNavigate = vi.fn();
const mockLogin = vi.fn();
const mockLoginWithGoogle = vi.fn();

vi.mock('../context/AuthContext', async (importActual) => {
  const actual = await importActual();
  return {
    ...actual,
    useAuth: () => ({
      user: null,
      loading: false,
      login: mockLogin,
      loginWithGoogle: mockLoginWithGoogle,
      logout: vi.fn(),
      register: vi.fn(),
    }),
  };
});

import LoginPage from '../pages/LoginPage';

const wrap = () => render(
  <MemoryRouter>
    <ThemeProvider><LoginPage /></ThemeProvider>
  </MemoryRouter>
);

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockLogin.mockReset();
  });

  it('renders email and password inputs', () => {
    wrap();
    expect(screen.getByPlaceholderText('Email')).toBeTruthy();
    expect(screen.getByPlaceholderText('Password')).toBeTruthy();
  });

  it('renders Sign in button', () => {
    wrap();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeTruthy();
  });

  it('renders "Create one" link to /register', () => {
    wrap();
    const link = screen.getByText('Create one');
    expect(link.getAttribute('href')).toBe('/register');
  });

  it('renders "Sign in to BörsVy" heading', () => {
    wrap();
    expect(screen.getByText(/Sign in to BörsVy/i)).toBeTruthy();
  });

  it('calls login and navigates on success', async () => {
    mockLogin.mockResolvedValueOnce({});
    const user = userEvent.setup();
    wrap();

    await user.type(screen.getByPlaceholderText('Email'), 'user@example.com');
    await user.type(screen.getByPlaceholderText('Password'), 'secret123');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(mockLogin).toHaveBeenCalledWith('user@example.com', 'secret123'));
    expect(mockNavigate).toHaveBeenCalledWith('/');
  });

  it('does not call login when email is empty', async () => {
    const user = userEvent.setup();
    wrap();

    await user.type(screen.getByPlaceholderText('Password'), 'password');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(mockLogin).not.toHaveBeenCalled();
  });

  it('does not call login when password is empty', async () => {
    const user = userEvent.setup();
    wrap();

    await user.type(screen.getByPlaceholderText('Email'), 'user@example.com');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(mockLogin).not.toHaveBeenCalled();
  });

  it('does not navigate when login throws', async () => {
    mockLogin.mockRejectedValueOnce(new Error('Wrong password'));
    const user = userEvent.setup();
    wrap();

    await user.type(screen.getByPlaceholderText('Email'), 'user@example.com');
    await user.type(screen.getByPlaceholderText('Password'), 'wrong');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(mockLogin).toHaveBeenCalled());
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
