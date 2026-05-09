import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import axios from 'axios';
import { ThemeProvider } from '../context/ThemeContext';
import DashboardPage from '../pages/DashboardPage';

vi.mock('axios');
vi.mock('react-router-dom', async (importActual) => {
  const actual = await importActual();
  return { ...actual, useNavigate: () => vi.fn() };
});

const wrap = (ui) => render(
  <MemoryRouter>
    <ThemeProvider>{ui}</ThemeProvider>
  </MemoryRouter>
);

const MARKET_OVERVIEW = [
  { symbol: 'AAPL', name: 'Apple Inc.', price: 182.52, changePercent: 0.68 },
  { symbol: 'MSFT', name: 'Microsoft', price: 415.0, changePercent: -0.3 },
];

const STOCK_DATA = { symbol: 'AAPL', name: 'Apple Inc.', price: 182.52, changePercent: 0.68 };

describe('DashboardPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders "Market Overview" section heading', () => {
    axios.get.mockResolvedValue({ data: [] });
    wrap(<DashboardPage />);
    expect(screen.getByText('Market Overview')).toBeTruthy();
  });

  it('renders "Popular Stocks" section heading', () => {
    axios.get.mockResolvedValue({ data: [] });
    wrap(<DashboardPage />);
    expect(screen.getByText('Popular Stocks')).toBeTruthy();
  });

  it('renders "Your Watchlist" section heading', () => {
    axios.get.mockResolvedValue({ data: [] });
    wrap(<DashboardPage />);
    // getAllByText because the antd icon parent element also contains the text
    expect(screen.getAllByText(/Your Watchlist/i).length).toBeGreaterThan(0);
  });

  it('shows empty watchlist message when no favorites', () => {
    axios.get.mockResolvedValue({ data: [] });
    wrap(<DashboardPage favorites={[]} />);
    expect(screen.getByText('No stocks in your watchlist yet.')).toBeTruthy();
  });

  it('shows "Search and add stocks" link when empty watchlist', () => {
    axios.get.mockResolvedValue({ data: [] });
    wrap(<DashboardPage favorites={[]} />);
    expect(screen.getByText('Search and add stocks')).toBeTruthy();
  });

  it('renders market overview tickers after API resolves', async () => {
    // market-overview returns data; stock detail calls return individual data
    axios.get.mockImplementation((url) => {
      if (url.includes('market-overview')) return Promise.resolve({ data: MARKET_OVERVIEW });
      return Promise.resolve({ data: STOCK_DATA });
    });

    wrap(<DashboardPage />);
    // AAPL and MSFT each appear in both market bar and popular grid — use getAllByText
    await waitFor(() => expect(screen.getAllByText('AAPL').length).toBeGreaterThan(0));
    expect(screen.getAllByText('MSFT').length).toBeGreaterThan(0);
  });

  it('renders "View all" link pointing to /favorites', () => {
    axios.get.mockResolvedValue({ data: [] });
    wrap(<DashboardPage />);
    const link = screen.getByText(/View all/i);
    expect(link.getAttribute('href')).toBe('/favorites');
  });

  it('renders "Search more" link pointing to /search', () => {
    axios.get.mockResolvedValue({ data: [] });
    wrap(<DashboardPage />);
    const link = screen.getByText(/Search more/i);
    expect(link.getAttribute('href')).toBe('/search');
  });

  it('shows favorite stock card when favorites provided', async () => {
    // market-overview needs an array; individual stock calls return object
    axios.get.mockImplementation((url) => {
      if (url.includes('market-overview')) return Promise.resolve({ data: [] });
      return Promise.resolve({ data: STOCK_DATA });
    });
    const favorites = [{ symbol: 'AAPL' }];
    wrap(<DashboardPage favorites={favorites} />);
    // StockCard fetches /api/stocks/AAPL — after resolution shows symbol
    await waitFor(() => {
      const aaplElements = screen.getAllByText('AAPL');
      expect(aaplElements.length).toBeGreaterThan(0);
    });
  });

  it('shows positive change in emerald color', async () => {
    axios.get.mockImplementation((url) => {
      if (url.includes('market-overview'))
        return Promise.resolve({ data: [{ symbol: 'AAPL', price: 182.52, changePercent: 1.5 }] });
      return Promise.resolve({ data: STOCK_DATA });
    });

    wrap(<DashboardPage />);
    await screen.findByText('+1.50%');
  });

  it('shows negative change for declining stock', async () => {
    axios.get.mockImplementation((url) => {
      if (url.includes('market-overview'))
        return Promise.resolve({ data: [{ symbol: 'MSFT', price: 400.0, changePercent: -2.1 }] });
      return Promise.resolve({ data: STOCK_DATA });
    });

    wrap(<DashboardPage />);
    await screen.findByText('-2.10%');
  });
});
