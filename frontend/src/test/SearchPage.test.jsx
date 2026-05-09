import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import axios from 'axios';
import { ThemeProvider } from '../context/ThemeContext';
import SearchPage from '../pages/SearchPage';

vi.mock('axios');
vi.mock('react-router-dom', async (importActual) => {
  const actual = await importActual();
  return { ...actual, useNavigate: () => vi.fn(), useParams: () => ({}) };
});

// Stub out the full Analysis component — it has its own tests
vi.mock('../components/Analysis', () => ({
  default: ({ selectedStock }) => <div data-testid="analysis">{selectedStock?.symbol}</div>,
}));

const wrap = (ui) => render(
  <MemoryRouter>
    <ThemeProvider>{ui}</ThemeProvider>
  </MemoryRouter>
);

const SUGGESTIONS = [
  { symbol: 'AAPL', name: 'Apple Inc.', type: 'Common Stock' },
  { symbol: 'AAPLF', name: 'Apple Inc. (OTC)', type: 'OTC' },
];

const SEARCH_RESULTS = [
  { symbol: 'AAPL', name: 'Apple Inc.' },
  { symbol: 'AAPLF', name: 'Apple Inc. (OTC)' },
];

describe('SearchPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders search input', () => {
    wrap(<SearchPage />);
    expect(screen.getByPlaceholderText(/Search stocks/i)).toBeTruthy();
  });

  it('renders popular stocks section by default', () => {
    wrap(<SearchPage />);
    expect(screen.getByText('Popular')).toBeTruthy();
    expect(screen.getByText('AAPL')).toBeTruthy();
    expect(screen.getByText('NVDA')).toBeTruthy();
    expect(screen.getByText('TSLA')).toBeTruthy();
  });

  it('shows "Search" button', () => {
    wrap(<SearchPage />);
    expect(screen.getByRole('button', { name: 'Search' })).toBeTruthy();
  });

  it('fetches suggestions after typing 2+ characters', async () => {
    axios.get.mockResolvedValue({ data: SUGGESTIONS });
    const user = userEvent.setup();
    wrap(<SearchPage />);

    await user.type(screen.getByPlaceholderText(/Search stocks/i), 'AA');

    await waitFor(() => expect(axios.get).toHaveBeenCalledWith(
      expect.stringContaining('query=AA'),
      expect.any(Object)
    ));
  });

  it('does not fetch suggestions for single character', async () => {
    const user = userEvent.setup();
    wrap(<SearchPage />);
    await user.type(screen.getByPlaceholderText(/Search stocks/i), 'A');
    // Give debounce time to settle
    await new Promise(r => setTimeout(r, 400));
    expect(axios.get).not.toHaveBeenCalled();
  });

  it('shows autocomplete dropdown when suggestions arrive', async () => {
    axios.get.mockResolvedValue({ data: SUGGESTIONS });
    const user = userEvent.setup();
    wrap(<SearchPage />);

    await user.type(screen.getByPlaceholderText(/Search stocks/i), 'AA');

    await waitFor(() => {
      // Suggestion dropdown should show "Apple Inc."
      expect(screen.getAllByText('Apple Inc.').length).toBeGreaterThan(0);
    });
  });

  it('clicking a popular stock triggers search', async () => {
    axios.get.mockResolvedValue({ data: SEARCH_RESULTS });
    wrap(<SearchPage />);

    fireEvent.click(screen.getByRole('button', { name: /^AAPL/ }));

    await waitFor(() => expect(axios.get).toHaveBeenCalledWith(
      expect.stringContaining('query=AAPL'),
      expect.any(Object)
    ));
  });

  it('shows results list after search completes', async () => {
    axios.get.mockResolvedValue({ data: SEARCH_RESULTS });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ThemeProvider><SearchPage /></ThemeProvider>
      </MemoryRouter>
    );

    const input = screen.getByPlaceholderText(/Search stocks/i);
    await user.type(input, 'Apple');
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      // Results panel shows "2 results"
      expect(screen.getByText(/2 result/i)).toBeTruthy();
    });
  });

  it('shows Analysis component after search result selected', async () => {
    axios.get.mockResolvedValue({ data: SEARCH_RESULTS });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ThemeProvider><SearchPage /></ThemeProvider>
      </MemoryRouter>
    );

    const input = screen.getByPlaceholderText(/Search stocks/i);
    await user.type(input, 'Apple');
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => expect(screen.getByTestId('analysis')).toBeTruthy());
    // First result is auto-selected
    expect(screen.getByTestId('analysis').textContent).toBe('AAPL');
  });

  it('clears query when clear button is clicked', async () => {
    axios.get.mockResolvedValue({ data: [] });
    const user = userEvent.setup();
    wrap(<SearchPage />);

    const input = screen.getByPlaceholderText(/Search stocks/i);
    await user.type(input, 'Apple');

    // Clear button has a CloseOutlined icon with aria-label="close"
    const clearBtn = screen.getByRole('button', { name: /close/i });
    await user.click(clearBtn);

    expect(input.value).toBe('');
  });

  it('shows empty results on API failure', async () => {
    axios.get.mockRejectedValue(new Error('Network error'));
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ThemeProvider><SearchPage /></ThemeProvider>
      </MemoryRouter>
    );

    const input = screen.getByPlaceholderText(/Search stocks/i);
    await user.type(input, 'Apple');
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      // Popular section should still be visible
      expect(screen.getByText('Popular')).toBeTruthy();
    });
  });
});
