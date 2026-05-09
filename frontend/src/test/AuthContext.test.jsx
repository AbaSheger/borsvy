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
  beforeEach(() => vi.clearAllMocks());

  it('starts in loading state then resolves to logged out', async () => {
    axios.get.mockRejectedValueOnce(new Error('401'));
    const { getByText } = renderWithAuth();
    expect(getByText('loading')).toBeInTheDocument();
    await waitFor(() => expect(getByText('not logged in')).toBeInTheDocument());
  });

  it('restores user from /api/auth/me on mount', async () => {
    axios.get.mockResolvedValueOnce({ data: { email: 'alice@example.com' } });
    renderWithAuth();
    await waitFor(() =>
      expect(screen.getByText('logged in as alice@example.com')).toBeInTheDocument()
    );
  });

  it('login() sets user state', async () => {
    axios.get.mockRejectedValueOnce(new Error('401')); // /me on mount
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
  });

  it('logout() clears user state', async () => {
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
