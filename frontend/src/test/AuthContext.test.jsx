import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act, waitFor } from '@testing-library/react';
import axios from 'axios';
import { AuthProvider, useAuth } from '../context/AuthContext';

vi.mock('axios');

// Test consumer component
const AuthConsumer = () => {
  const { user, loading } = useAuth();
  if (loading) return <div>loading</div>;
  return <div>{user ? `logged in as ${user.email}` : 'not logged in'}</div>;
};

const renderWithAuth = () =>
  render(
    <AuthProvider>
      <AuthConsumer />
    </AuthProvider>
  );

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('skips /api/auth/me when no session is known', async () => {
    const { getByText } = renderWithAuth();
    await waitFor(() => expect(getByText('not logged in')).toBeInTheDocument());
    expect(axios.get).not.toHaveBeenCalled();
  });

  it('restores user from /api/auth/me on mount', async () => {
    localStorage.setItem('borsvyAuthSession', '1');
    axios.get.mockResolvedValueOnce({ data: { email: 'alice@example.com' } });
    renderWithAuth();
    await waitFor(() =>
      expect(screen.getByText('logged in as alice@example.com')).toBeInTheDocument()
    );
  });

  it('login() sets user state', async () => {
    axios.post.mockResolvedValueOnce({ data: { email: 'bob@example.com' } });

    let authCtx;
    const Grabber = () => {
      authCtx = useAuth();
      return null;
    };
    render(<AuthProvider><Grabber /></AuthProvider>);
    await waitFor(() => expect(authCtx.loading).toBe(false));

    await act(async () => {
      await authCtx.login('bob@example.com', 'password123');
    });
    expect(authCtx.user.email).toBe('bob@example.com');
    expect(localStorage.getItem('borsvyAuthSession')).toBe('1');
  });

  it('logout() clears user state', async () => {
    localStorage.setItem('borsvyAuthSession', '1');
    axios.get.mockResolvedValueOnce({ data: { email: 'alice@example.com' } });
    axios.post.mockResolvedValueOnce({});

    let authCtx;
    const Grabber = () => {
      authCtx = useAuth();
      return null;
    };
    render(<AuthProvider><Grabber /></AuthProvider>);
    await waitFor(() => expect(authCtx.user?.email).toBe('alice@example.com'));

    await act(async () => {
      await authCtx.logout();
    });
    expect(authCtx.user).toBeNull();
    expect(localStorage.getItem('borsvyAuthSession')).toBeNull();
  });

  it('throws when useAuth is used outside AuthProvider', () => {
    // Suppress React's error boundary console noise
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    try {
      expect(() => render(<AuthConsumer />)).toThrow('useAuth must be used inside AuthProvider');
    } finally {
      consoleSpy.mockRestore();
    }
  });
});
