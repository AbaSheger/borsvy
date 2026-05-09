import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Spin, message } from 'antd';
import { StarOutlined, StarFilled, ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons';
import axios from 'axios';
import { axiosConfig } from '../config';
import StockChart from './StockChart';
import PeerComparison from './PeerComparison';
import NewsCard from './NewsCard';
import AnalysisVisualization from './AnalysisVisualization';
import { useTheme } from '../context/ThemeContext';

const fmt = (n, decimals = 2) =>
  n != null ? `$${Number(n).toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}` : '—';

const fmtCompact = (n) => {
  if (n == null) return '—';
  if (n >= 1e6) return `$${(n / 1e6).toFixed(2)}T`;
  if (n >= 1e3) return `$${(n / 1e3).toFixed(2)}B`;
  return `$${n.toFixed(2)}M`;
};

const StatBox = ({ label, value, dark }) => (
  <div className={`px-4 py-3 rounded-lg ${dark ? 'bg-[#11151d] border border-[#263142]' : 'bg-slate-50 border border-slate-100'}`}>
    <p className={`text-xs mb-0.5 ${dark ? 'text-zinc-500' : 'text-slate-400'}`}>{label}</p>
    <p className={`text-sm font-semibold ${dark ? 'text-zinc-100' : 'text-slate-800'}`}>{value}</p>
  </div>
);

const TABS = ['Chart', 'AI Analysis', 'News', 'Peers'];
const INTERVALS = ['1D', '1W', '1M', '3M', '1Y'];

const Analysis = ({ selectedStock, favorites = [], onToggleFavorite }) => {
  const { symbol: routeSymbol } = useParams();
  const symbol = selectedStock?.symbol || routeSymbol;

  const [analysis, setAnalysis] = useState(null);
  const [aiAnalysis, setAiAnalysis] = useState(null);
  const [news, setNews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [aiLoading, setAiLoading] = useState(false);
  const [newsLoading, setNewsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [tab, setTab] = useState('Chart');
  const [interval, setInterval] = useState('1D');
  const [isFavorite, setIsFavorite] = useState(false);
  const { theme } = useTheme();
  const dark = theme === 'dark';

  useEffect(() => {
    if (symbol) {
      setIsFavorite(favorites.some(f => f.symbol === symbol));
    }
  }, [symbol, favorites]);

  useEffect(() => {
    if (!symbol || symbol === 'undefined') return;
    setLoading(true);
    setError(null);
    setAnalysis(null);
    setAiAnalysis(null);
    setNews([]);
    setTab('Chart');

    axios.get(`/api/stocks/${symbol}`, axiosConfig)
      .then(res => {
        const data = res.data || {};
        setAnalysis({
          stock: data,
          price: data.price,
          change: data.change,
          changePercent: data.changePercent,
          name: data.name,
          industry: data.industry,
          marketCap: data.marketCap,
          peRatio: data.peRatio,
          high52Week: data.high52Week,
          low52Week: data.low52Week,
        });
      })
      .catch(() => setError('Failed to load stock details'))
      .finally(() => setLoading(false));
  }, [symbol]);

  useEffect(() => {
    if (tab !== 'AI Analysis' || aiAnalysis || aiLoading || !symbol || symbol === 'undefined') return;

    setAiLoading(true);
    axios.get(`/api/analysis/${symbol}/ai`, axiosConfig)
      .then(res => setAiAnalysis(res.data))
      .catch(() => message.error('Failed to load AI analysis'))
      .finally(() => setAiLoading(false));
  }, [tab, aiAnalysis, aiLoading, symbol]);

  useEffect(() => {
    if (tab !== 'News' || news.length > 0 || newsLoading || !symbol || symbol === 'undefined') return;

    setNewsLoading(true);
    axios.get(`/api/analysis/${symbol}/news?limit=5`, axiosConfig)
      .then(res => {
        const rawNews = res.data || [];
        setNews(rawNews.map(a => ({
          title: a.title || 'No title',
          url: a.url || '#',
          source: a.source || '',
          date: a.date || a.publishedDate || '',
          summary: a.summary || '',
          thumbnail: a.thumbnail || '',
        })));
      })
      .catch(() => message.error('Failed to load news'))
      .finally(() => setNewsLoading(false));
  }, [tab, news.length, newsLoading, symbol]);

  const toggleFavorite = async () => {
    if (!analysis) return;
    try {
      if (isFavorite) {
        await axios.delete(`/api/favorites/${symbol}`, axiosConfig);
        message.success(`${symbol} removed from favorites`);
        setIsFavorite(false);
      } else {
        await axios.post('/api/favorites', {
          symbol,
          name: analysis.name,
          price: analysis.price || 0,
          change: analysis.change || 0,
          changePercent: analysis.changePercent || 0,
        }, axiosConfig);
        message.success(`${symbol} added to favorites`);
        setIsFavorite(true);
      }
      onToggleFavorite?.();
    } catch {
      message.error('Failed to update favorites');
    }
  };

  const card = 'saas-panel';

  if (loading) return (
    <div className={`${card} flex items-center justify-center p-20`}>
      <Spin size="large" />
    </div>
  );

  if (error) return (
    <div className={`${card} p-8 text-center`}>
      <p className="text-rose-400">{error}</p>
    </div>
  );

  if (!analysis) return null;

  const price = analysis.price;
  const change = analysis.change;
  const changePercent = analysis.changePercent;
  const positive = changePercent >= 0;

  return (
    <div className="space-y-4">
      {/* Stock header */}
      <div className={`${card} p-5`}>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div>
            <div className="flex items-center gap-3 mb-1">
              <h1 className={`text-2xl font-bold ${dark ? 'text-zinc-50' : 'text-slate-900'}`}>{symbol}</h1>
              {analysis.industry && (
                <span className={`text-xs px-2.5 py-1 rounded-lg font-medium ${
                  dark ? 'bg-blue-500/10 text-blue-400 border border-blue-500/20' : 'bg-blue-50 text-blue-600 border border-blue-100'
                }`}>{analysis.industry}</span>
              )}
            </div>
            {analysis.name && (
              <p className={`text-sm ${dark ? 'text-zinc-400' : 'text-slate-500'}`}>{analysis.name}</p>
            )}
          </div>

          <div className="flex items-center gap-3">
            <div className="text-right">
              <div className={`text-3xl font-bold tabular-nums ${dark ? 'text-zinc-50' : 'text-slate-900'}`}>
                {fmt(price)}
              </div>
              {changePercent != null && (
                <div className={`flex items-center justify-end gap-1 text-sm font-semibold mt-0.5 ${positive ? 'text-emerald-400' : 'text-rose-400'}`}>
                  {positive ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
                  {positive ? '+' : ''}{Number(changePercent).toFixed(2)}%
                  {change != null && (
                    <span className="font-normal opacity-70">
                      ({positive ? '+' : ''}{fmt(change)})
                    </span>
                  )}
                </div>
              )}
            </div>
            <button onClick={toggleFavorite}
              className={`p-2.5 rounded-md border transition-all ${
                isFavorite
                  ? dark ? 'bg-yellow-500/15 border-yellow-500/30 text-yellow-400' : 'bg-yellow-50 border-yellow-200 text-yellow-500'
                  : dark ? 'bg-[#11151d] border-[#263142] text-zinc-400 hover:text-yellow-400 hover:border-yellow-500/30' : 'bg-slate-50 border-slate-200 text-slate-400 hover:text-yellow-500'
              }`}>
              {isFavorite ? <StarFilled /> : <StarOutlined />}
            </button>
          </div>
        </div>

        {/* Quick stats */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 mt-4">
          <StatBox label="Market Cap" value={fmtCompact(analysis.marketCap)} dark={dark} />
          <StatBox label="P/E Ratio" value={analysis.peRatio > 0 ? analysis.peRatio?.toFixed(1) : '—'} dark={dark} />
          <StatBox label="52W High" value={fmt(analysis.high52Week)} dark={dark} />
          <StatBox label="52W Low" value={fmt(analysis.low52Week)} dark={dark} />
        </div>
      </div>

      {/* Tabs */}
      <div className={`flex gap-1 p-1 rounded-lg ${dark ? 'bg-[#151922] border border-[#263142]' : 'bg-slate-100'}`}>
        {TABS.map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`flex-1 text-sm font-medium py-2 rounded-lg transition-all ${
              tab === t
                ? dark ? 'bg-[#202838] text-zinc-100 shadow-sm' : 'bg-white text-slate-800 shadow-sm'
                : dark ? 'text-zinc-500 hover:text-zinc-300' : 'text-slate-400 hover:text-slate-600'
            }`}>
            {t}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {tab === 'Chart' && (
        <div className={`${card} p-1 overflow-hidden`}>
          {/* Interval selector */}
          <div className="flex gap-1 p-2">
            {INTERVALS.map(i => (
              <button key={i} onClick={() => setInterval(i)}
                className={`px-3 py-1 text-xs font-semibold rounded-lg transition-colors ${
                  interval === i
                    ? 'bg-blue-500 text-white'
                    : dark ? 'text-zinc-400 hover:text-zinc-200 hover:bg-[#202838]' : 'text-slate-400 hover:text-slate-700 hover:bg-slate-100'
                }`}>
                {i}
              </button>
            ))}
          </div>
          <StockChart symbol={symbol} interval={interval} />
        </div>
      )}

      {tab === 'AI Analysis' && (
        aiLoading
          ? <div className={`${card} flex items-center justify-center p-16`}><Spin /></div>
          : <AnalysisVisualization analysis={aiAnalysis} />
      )}

      {tab === 'News' && (
        newsLoading
          ? <div className={`${card} flex items-center justify-center p-16`}><Spin /></div>
          : <NewsCard news={news} />
      )}

      {tab === 'Peers' && (
        <div className={card}>
          <PeerComparison symbol={symbol} />
        </div>
      )}
    </div>
  );
};

export default Analysis;
