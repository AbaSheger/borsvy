import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Button, Spin } from 'antd';
import { ArrowDownOutlined, ArrowUpOutlined, SearchOutlined, StarOutlined } from '@ant-design/icons';
import axios from 'axios';
import { axiosConfig } from '../config';
import { useTheme } from '../context/ThemeContext';

const POPULAR_SYMBOLS = ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA', 'META', 'NVDA', 'BTC'];

const fmtPrice = (value) =>
  value != null
    ? `$${Number(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    : '-';

const ChangeTag = ({ value }) => {
  if (value == null) return <span className="text-slate-400 dark:text-slate-500">-</span>;
  const pos = value >= 0;

  return (
    <span className={`inline-flex items-center justify-end gap-1 whitespace-nowrap text-xs font-semibold ${pos ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
      {pos ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
      {pos ? '+' : ''}{Number(value).toFixed(2)}%
    </span>
  );
};

const MarketOverview = () => {
  const [overview, setOverview] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    axios.get('/api/stocks/market-overview', axiosConfig)
      .then(response => setOverview(response.data || []))
      .catch(() => setOverview([]))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="saas-panel p-4 flex items-center justify-center min-h-24">
        <Spin size="small" />
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 xl:grid-cols-8 gap-3">
      {overview.map(item => (
        <button
          key={item.symbol}
          onClick={() => navigate(`/analysis/${item.symbol}`)}
          className="metric-card text-left hover:border-blue-300 dark:hover:border-blue-500/50 transition-colors"
        >
          <div className="flex items-center justify-between gap-2">
            <span className="text-sm font-semibold text-slate-950 dark:text-slate-50">{item.symbol}</span>
            <ChangeTag value={item.changePercent} />
          </div>
          <div className="mt-2 text-lg font-semibold tabular-nums text-slate-950 dark:text-slate-50">{fmtPrice(item.price)}</div>
        </button>
      ))}
    </div>
  );
};

const StockRow = ({ symbol, name, fallbackPrice, fallbackChange, compact = false }) => {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axios.get(`/api/stocks/${symbol}`, axiosConfig)
      .then(response => setData(response.data))
      .catch(() => setData(null))
      .finally(() => setLoading(false));
  }, [symbol]);

  const price = data?.price ?? fallbackPrice;
  const changePercent = data?.changePercent ?? fallbackChange;
  const openAnalysis = () => navigate(`/analysis/${symbol}`);

  return (
    <tr
      onClick={openAnalysis}
      className="cursor-pointer"
      tabIndex={0}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          openAnalysis();
        }
      }}
    >
      <td>
        <div className="text-left group min-w-0">
          <div className="font-semibold text-slate-950 dark:text-slate-50 group-hover:text-blue-600 dark:group-hover:text-blue-400">{symbol}</div>
          <div className="text-xs text-slate-500 dark:text-slate-400 truncate">{data?.name || name || 'Market instrument'}</div>
        </div>
      </td>
      <td className="text-right tabular-nums text-slate-700 dark:text-slate-200 whitespace-nowrap">
        {loading && price == null ? <Spin size="small" /> : fmtPrice(price)}
      </td>
      <td className="text-right"><ChangeTag value={changePercent} /></td>
      {!compact && (
        <td className="text-right">
          <Button
            size="small"
            onClick={(event) => {
              event.stopPropagation();
              openAnalysis();
            }}
          >
            Open
          </Button>
        </td>
      )}
    </tr>
  );
};

const WatchlistCard = ({ symbol, name, fallbackPrice, fallbackChange }) => {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axios.get(`/api/stocks/${symbol}`, axiosConfig)
      .then(response => setData(response.data))
      .catch(() => setData(null))
      .finally(() => setLoading(false));
  }, [symbol]);

  const price = data?.price ?? fallbackPrice;
  const changePercent = data?.changePercent ?? fallbackChange;
  const openAnalysis = () => navigate(`/analysis/${symbol}`);

  return (
    <button
      type="button"
      onClick={openAnalysis}
      className="w-full text-left px-4 py-3 border-t border-slate-200 dark:border-[#263142] hover:bg-slate-50 dark:hover:bg-[#1a2030] transition-colors"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="font-semibold text-slate-950 dark:text-slate-50">{symbol}</div>
          <div className="text-xs text-slate-500 dark:text-slate-400 truncate">{data?.name || name || 'Market instrument'}</div>
        </div>
        <ChangeTag value={changePercent} />
      </div>
      <div className="mt-3 flex items-center justify-between gap-3">
        <span className="text-[11px] font-semibold uppercase text-slate-500 dark:text-slate-400">Last price</span>
        <span className="text-sm font-semibold tabular-nums text-slate-950 dark:text-slate-50 whitespace-nowrap">
          {loading && price == null ? <Spin size="small" /> : fmtPrice(price)}
        </span>
      </div>
    </button>
  );
};

const DashboardPage = ({ favorites = [] }) => {
  const { theme } = useTheme();
  const activeWatchlist = favorites.slice(0, 7);

  return (
    <div className="page-wrap space-y-6">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="page-kicker">Market workspace</p>
          <h1 className="page-title mt-1">Dashboard</h1>
          <p className="page-subtitle mt-1">Monitor price action, saved assets, and research entry points from one screen.</p>
        </div>
        <div className="flex items-center gap-2">
          <Link to="/search">
            <Button type="primary" icon={<SearchOutlined />}>Research symbol</Button>
          </Link>
          <Link to="/portfolio">
            <Button>Portfolio</Button>
          </Link>
        </div>
      </div>

      <MarketOverview />

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_360px] gap-5 items-start">
        <div className="saas-panel overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-200 dark:border-[#263142] flex items-center justify-between">
            <div>
              <h2 className="text-sm font-semibold text-slate-950 dark:text-slate-50 flex items-center gap-2">
                <StarOutlined className="text-amber-500" /> Watchlist
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">{favorites.length} saved instrument{favorites.length === 1 ? '' : 's'}</p>
            </div>
            <Link to="/favorites" className="text-sm font-medium text-blue-600 dark:text-blue-400">View all</Link>
          </div>

          {activeWatchlist.length === 0 ? (
            <div className="p-10 text-center">
              <p className="text-sm font-medium text-slate-700 dark:text-slate-200">No watchlist items yet</p>
              <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">Search for a symbol and star it to keep it here.</p>
              <Link to="/search" className="inline-flex mt-4">
                <Button type="primary" icon={<SearchOutlined />}>Find symbols</Button>
              </Link>
            </div>
          ) : (
            <>
              <div className="sm:hidden">
                {activeWatchlist.map(stock => (
                  <WatchlistCard
                    key={stock.symbol}
                    symbol={stock.symbol}
                    name={stock.name}
                    fallbackPrice={stock.price}
                    fallbackChange={stock.changePercent}
                  />
                ))}
              </div>
              <table className="data-table hidden sm:table">
                <colgroup>
                  <col className="w-auto" />
                  <col className="w-auto" />
                  <col className="w-auto" />
                  <col className="w-auto" />
                </colgroup>
                <thead>
                  <tr>
                    <th className="text-left">Instrument</th>
                    <th className="text-right">Last price</th>
                    <th className="text-right">Change</th>
                    <th className="text-right">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {activeWatchlist.map(stock => (
                    <StockRow
                      key={stock.symbol}
                      symbol={stock.symbol}
                      name={stock.name}
                      fallbackPrice={stock.price}
                      fallbackChange={stock.changePercent}
                    />
                  ))}
                </tbody>
              </table>
            </>
          )}
        </div>

        <div className="space-y-5">
          <div className="saas-panel p-5">
            <p className="page-kicker">Research queue</p>
            <h2 className="text-sm font-semibold text-slate-950 dark:text-slate-50 mt-1">Popular instruments</h2>
            <div className="mt-4 overflow-hidden border border-slate-200 dark:border-[#263142] rounded-lg">
              <table className="data-table">
                <colgroup>
                  <col className="w-[40%]" />
                  <col className="w-[32%]" />
                  <col className="w-[28%]" />
                </colgroup>
                <thead>
                  <tr>
                    <th className="text-left">Symbol</th>
                    <th className="text-right">Price</th>
                    <th className="text-right">Move</th>
                  </tr>
                </thead>
                <tbody>
                  {POPULAR_SYMBOLS.map(symbol => (
                    <StockRow key={symbol} symbol={symbol} compact />
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="saas-panel-muted p-5">
            <p className="page-kicker">Session</p>
            <div className="mt-3 grid grid-cols-2 gap-3">
              <div>
                <p className="text-xs text-slate-500 dark:text-slate-400">Theme</p>
                <p className="text-sm font-semibold text-slate-950 dark:text-slate-50 capitalize">{theme}</p>
              </div>
              <div>
                <p className="text-xs text-slate-500 dark:text-slate-400">Refresh</p>
                <p className="text-sm font-semibold text-slate-950 dark:text-slate-50">Live API</p>
              </div>
              <div>
                <p className="text-xs text-slate-500 dark:text-slate-400">Watchlist</p>
                <p className="text-sm font-semibold text-slate-950 dark:text-slate-50">{favorites.length}</p>
              </div>
              <div>
                <p className="text-xs text-slate-500 dark:text-slate-400">Coverage</p>
                <p className="text-sm font-semibold text-slate-950 dark:text-slate-50">Stocks, ETFs, crypto</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
