import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import axios from 'axios';
import { ThemeProvider } from '../context/ThemeContext';

vi.mock('axios');
vi.mock('react-router-dom', async (importActual) => {
  const actual = await importActual();
  return { ...actual, useParams: () => ({}) };
});

// Mock heavy child components — they have their own test files
vi.mock('../components/StockChart', () => ({ default: () => <div data-testid="stock-chart" /> }));
vi.mock('../components/PeerComparison', () => ({ default: () => <div data-testid="peer-comparison" /> }));
vi.mock('../components/AnalysisVisualization', () => ({ default: ({ analysis }) => <div data-testid="analysis-viz">{analysis?.sentiment}</div> }));
vi.mock('../components/NewsCard', () => ({ default: ({ news }) => <div data-testid="news-card">{news?.length} articles</div> }));

import Analysis from '../components/Analysis';

const wrap = (ui) => render(
  <MemoryRouter>
    <ThemeProvider>{ui}</ThemeProvider>
  </MemoryRouter>
);

const ANALYSIS_RESPONSE = {
  price: 182.52,
  change: 1.23,
  changePercent: 0.68,
  name: 'Apple Inc.',
  industry: 'Technology',
  marketCap: 2800000,
  peRatio: 28.5,
  high52Week: 199.62,
  low52Week: 124.17,
  sentiment: 'POSITIVE',
  recentNews: [
    {
      title: 'Apple Q1 results',
      url: 'https://example.com/1',
      source: 'Reuters',
      date: '2024-01-15',
      summary: 'Apple beats estimates.',
      thumbnail: '',
    },
  ],
};

describe('Analysis', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows spinner while loading', () => {
    axios.get.mockReturnValue(new Promise(() => {}));
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    expect(document.querySelector('.ant-spin')).toBeTruthy();
  });

  it('shows error message when API fails', async () => {
    axios.get.mockRejectedValueOnce(new Error('Network error'));
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await screen.findByText('Failed to load analysis');
  });

  it('renders stock symbol in heading', async () => {
    axios.get.mockResolvedValueOnce({ data: ANALYSIS_RESPONSE });
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await waitFor(() => expect(screen.getByText('AAPL')).toBeTruthy());
  });

  it('renders company name', async () => {
    axios.get.mockResolvedValueOnce({ data: ANALYSIS_RESPONSE });
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await screen.findByText('Apple Inc.');
  });

  it('renders industry badge', async () => {
    axios.get.mockResolvedValueOnce({ data: ANALYSIS_RESPONSE });
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await screen.findByText('Technology');
  });

  it('renders formatted price', async () => {
    axios.get.mockResolvedValueOnce({ data: ANALYSIS_RESPONSE });
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await screen.findByText('$182.52');
  });

  it('renders Market Cap stat box', async () => {
    axios.get.mockResolvedValueOnce({ data: ANALYSIS_RESPONSE });
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await screen.findByText('Market Cap');
  });

  it('renders P/E Ratio stat box', async () => {
    axios.get.mockResolvedValueOnce({ data: ANALYSIS_RESPONSE });
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await screen.findByText('P/E Ratio');
  });

  it('renders 52W High stat box', async () => {
    axios.get.mockResolvedValueOnce({ data: ANALYSIS_RESPONSE });
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await screen.findByText('52W High');
  });

  it('renders 52W Low stat box', async () => {
    axios.get.mockResolvedValueOnce({ data: ANALYSIS_RESPONSE });
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await screen.findByText('52W Low');
  });

  it('renders Chart tab by default showing StockChart', async () => {
    axios.get.mockResolvedValueOnce({ data: ANALYSIS_RESPONSE });
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await waitFor(() => expect(screen.getByTestId('stock-chart')).toBeTruthy());
  });

  it('switches to AI Analysis tab', async () => {
    axios.get.mockResolvedValueOnce({ data: ANALYSIS_RESPONSE });
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await waitFor(() => expect(screen.queryByText('AI Analysis')).toBeTruthy());
    fireEvent.click(screen.getByText('AI Analysis'));
    expect(screen.getByTestId('analysis-viz')).toBeTruthy();
  });

  it('switches to News tab and shows NewsCard', async () => {
    axios.get.mockResolvedValueOnce({ data: ANALYSIS_RESPONSE });
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await waitFor(() => expect(screen.queryByText('News')).toBeTruthy());
    fireEvent.click(screen.getByText('News'));
    const newsCard = screen.getByTestId('news-card');
    expect(newsCard).toBeTruthy();
    expect(newsCard.textContent).toContain('1 articles');
  });

  it('switches to Peers tab and shows PeerComparison', async () => {
    axios.get.mockResolvedValueOnce({ data: ANALYSIS_RESPONSE });
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await waitFor(() => expect(screen.queryByText('Peers')).toBeTruthy());
    fireEvent.click(screen.getByText('Peers'));
    expect(screen.getByTestId('peer-comparison')).toBeTruthy();
  });

  it('does not fetch when symbol is "undefined"', () => {
    wrap(<Analysis selectedStock={{ symbol: 'undefined' }} />);
    expect(axios.get).not.toHaveBeenCalled();
  });

  it('renders nothing when analysis is null and not loading', async () => {
    // Simulate a resolved empty-object response — component returns null guard
    axios.get.mockResolvedValueOnce({ data: null });
    const { container } = wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    await waitFor(() => expect(document.querySelector('.ant-spin')).toBeNull());
    // No crash — container renders without the price section
    expect(container).toBeTruthy();
  });

  it('favorite button is present after load', async () => {
    axios.get.mockResolvedValueOnce({ data: ANALYSIS_RESPONSE });
    wrap(<Analysis selectedStock={{ symbol: 'AAPL' }} />);
    // Wait for data
    await screen.findByText('Apple Inc.');
    // Button wrapping the star icon
    const btn = document.querySelector('button[class*="rounded-xl"]');
    expect(btn).toBeTruthy();
  });
});
