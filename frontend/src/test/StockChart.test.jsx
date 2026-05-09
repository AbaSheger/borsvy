import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import axios from 'axios';

vi.mock('axios');
vi.mock('react-router-dom', () => ({ useParams: () => ({}) }));

// lightweight-charts creates a DOM canvas — mock it entirely
vi.mock('lightweight-charts', () => ({
  createChart: vi.fn(() => ({
    addSeries: vi.fn(() => ({ setData: vi.fn(), priceScale: vi.fn(() => ({ applyOptions: vi.fn() })) })),
    timeScale: vi.fn(() => ({ fitContent: vi.fn() })),
    applyOptions: vi.fn(),
    remove: vi.fn(),
  })),
  CandlestickSeries: {},
  HistogramSeries: {},
  LineSeries: {},
}));

import StockChart from '../components/StockChart';

const makePoint = (price, timestamp = '2024-01-01T10:00:00') => ({
  price, timestamp, open: price, high: price + 1, low: price - 1, volume: 1000,
});

describe('StockChart', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows spinner while loading', () => {
    axios.get.mockReturnValue(new Promise(() => {})); // never resolves
    render(<StockChart symbol="AAPL" interval="1D" />);
    // Antd Spin renders a loading indicator
    expect(document.querySelector('.ant-spin')).toBeTruthy();
  });

  it('shows error when API fails', async () => {
    axios.get.mockRejectedValueOnce(new Error('Network error'));
    render(<StockChart symbol="AAPL" interval="1D" />);
    await screen.findByText('Failed to fetch price data');
  });

  it('shows "no data" message for empty response', async () => {
    axios.get.mockResolvedValueOnce({ data: [] });
    render(<StockChart symbol="AAPL" interval="1D" />);
    await screen.findByText('No price data available');
  });

  it('shows "invalid symbol" for undefined symbol', async () => {
    render(<StockChart symbol="undefined" interval="1D" />);
    await screen.findByText('Invalid symbol');
  });

  it('renders chart div when valid data is returned', async () => {
    const data = [
      makePoint(150.0, '2024-01-01T09:00:00'),
      makePoint(151.0, '2024-01-01T10:00:00'),
      makePoint(152.0, '2024-01-01T11:00:00'),
    ];
    axios.get.mockResolvedValueOnce({ data });

    const { container } = render(<StockChart symbol="AAPL" interval="1D" />);
    // Wait for loading to complete
    await waitFor(() => expect(container.querySelector('.ant-spin')).toBeNull());
    // The chart container div should be present
    expect(container.querySelector('div[style]')).toBeTruthy();
  });

  it('renders chart for data points where price is 0 (penny stocks/crypto)', async () => {
    // price=0 must NOT be filtered — filter uses `p.price != null` now
    const data = [
      { price: 0, timestamp: '2024-01-01T09:00:00', open: 0, high: 0.1, low: 0, volume: 500 },
      { price: 0, timestamp: '2024-01-01T10:00:00', open: 0, high: 0.1, low: 0, volume: 500 },
    ];
    axios.get.mockResolvedValueOnce({ data });

    const { container } = render(<StockChart symbol="PENNYSTOCK" interval="1D" />);

    // Should render the chart container, not the "no data" message
    await waitFor(() => expect(container.querySelector('div[style]')).toBeTruthy());
    expect(screen.queryByText('No price data available')).toBeNull();
  });

  it('passes correct interval to API', async () => {
    axios.get.mockResolvedValueOnce({ data: [] });
    render(<StockChart symbol="MSFT" interval="1W" />);

    await waitFor(() => {
      expect(axios.get).toHaveBeenCalledWith(
        expect.stringContaining('interval=1W'),
        expect.any(Object)
      );
    });
  });

  it('refetches when interval changes', async () => {
    axios.get.mockResolvedValue({ data: [] });
    const { rerender } = render(<StockChart symbol="AAPL" interval="1D" />);
    await waitFor(() => expect(axios.get).toHaveBeenCalledTimes(1));

    rerender(<StockChart symbol="AAPL" interval="1M" />);
    await waitFor(() => expect(axios.get).toHaveBeenCalledTimes(2));

    expect(axios.get).toHaveBeenLastCalledWith(
      expect.stringContaining('interval=1M'),
      expect.any(Object)
    );
  });
});
