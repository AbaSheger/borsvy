import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import axios from 'axios';
import Favorites from '../components/Favorites';

vi.mock('axios');
vi.mock('../context/ThemeContext', () => ({
  useTheme: () => ({ theme: 'dark' }),
}));
// Suppress antd message calls
vi.mock('antd', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, message: { success: vi.fn(), error: vi.fn() } };
});

const renderFavorites = (props = {}) =>
  render(
    <MemoryRouter>
      <Favorites {...props} />
    </MemoryRouter>
  );

describe('Favorites', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows empty state when no favorites', () => {
    renderFavorites({ favorites: [] });
    expect(screen.getByText('No favorites yet')).toBeInTheDocument();
    expect(screen.getByText(/Search for stocks and star them/)).toBeInTheDocument();
  });

  it('renders watchlist heading with count', () => {
    const favorites = [
      { symbol: 'AAPL', name: 'Apple Inc.', price: 175.5, changePercent: 1.23 },
      { symbol: 'MSFT', name: 'Microsoft', price: 380.0, changePercent: -0.5 },
    ];
    renderFavorites({ favorites });
    expect(screen.getByText('Watchlist')).toBeInTheDocument();
    expect(screen.getByText('(2)')).toBeInTheDocument();
  });

  it('renders each favorite with symbol, price and change', () => {
    const favorites = [
      { symbol: 'AAPL', name: 'Apple Inc.', price: 175.5, changePercent: 1.23 },
    ];
    renderFavorites({ favorites });
    expect(screen.getByText('AAPL')).toBeInTheDocument();
    expect(screen.getByText('Apple Inc.')).toBeInTheDocument();
    expect(screen.getByText('$175.50')).toBeInTheDocument();
    expect(screen.getByText('+1.23%')).toBeInTheDocument();
  });

  it('shows negative change in rose color class', () => {
    const favorites = [
      { symbol: 'TSLA', name: 'Tesla', price: 200.0, changePercent: -3.5 },
    ];
    renderFavorites({ favorites });
    const changeEl = screen.getByText('-3.50%');
    expect(changeEl).toHaveClass('text-rose-400');
  });

  it('shows positive change in emerald color class', () => {
    const favorites = [
      { symbol: 'NVDA', name: 'NVIDIA', price: 900.0, changePercent: 5.0 },
    ];
    renderFavorites({ favorites });
    const changeEl = screen.getByText('+5.00%');
    expect(changeEl).toHaveClass('text-emerald-400');
  });

  it('calls DELETE and onToggleFavorite after confirming remove', async () => {
    axios.delete.mockResolvedValueOnce({});
    const onToggleFavorite = vi.fn();
    const favorites = [
      { symbol: 'AAPL', name: 'Apple Inc.', price: 175.5, changePercent: 1.23 },
    ];
    renderFavorites({ favorites, onToggleFavorite });

    // Click the delete button to open Popconfirm (antd DeleteOutlined has aria-label="delete")
    const deleteBtn = screen.getByRole('button', { name: /delete/i });
    fireEvent.click(deleteBtn);

    // Confirm the popconfirm
    const removeBtn = await screen.findByText('Remove');
    fireEvent.click(removeBtn);

    await waitFor(() => {
      expect(axios.delete).toHaveBeenCalledWith('/api/favorites/AAPL', expect.any(Object));
      expect(onToggleFavorite).toHaveBeenCalled();
    });
  });
});
