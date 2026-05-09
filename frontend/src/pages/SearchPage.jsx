import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Spin } from 'antd';
import { SearchOutlined, FireOutlined, CloseOutlined, ArrowRightOutlined } from '@ant-design/icons';
import axios from 'axios';
import { axiosConfig } from '../config';
import { useDebounce } from '../hooks/useDebounce';
import Analysis from '../components/Analysis';
import { useTheme } from '../context/ThemeContext';

const POPULAR = [
  { symbol: 'AAPL', name: 'Apple Inc.' },
  { symbol: 'MSFT', name: 'Microsoft' },
  { symbol: 'NVDA', name: 'NVIDIA' },
  { symbol: 'GOOGL', name: 'Alphabet' },
  { symbol: 'AMZN', name: 'Amazon' },
  { symbol: 'TSLA', name: 'Tesla' },
  { symbol: 'META', name: 'Meta' },
  { symbol: 'BTC', name: 'Bitcoin' },
];

const SearchPage = ({ favorites = [], onToggleFavorite }) => {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [suggestionsOpen, setSuggestionsOpen] = useState(false);
  const [results, setResults] = useState([]);
  const [selected, setSelected] = useState(null);
  const [searching, setSearching] = useState(false);
  const debouncedQuery = useDebounce(query, 280);
  const inputRef = useRef(null);
  const suggestionsRef = useRef(null);
  const { theme } = useTheme();
  const dark = theme === 'dark';

  // Fetch autocomplete suggestions
  useEffect(() => {
    if (debouncedQuery.length < 2) { setSuggestions([]); return; }
    axios.get(`/api/stocks/search?query=${encodeURIComponent(debouncedQuery)}`, axiosConfig)
      .then(r => setSuggestions(r.data || []))
      .catch(() => setSuggestions([]));
  }, [debouncedQuery]);

  // Close suggestions on outside click
  useEffect(() => {
    const handler = (e) => {
      if (suggestionsRef.current && !suggestionsRef.current.contains(e.target)) {
        setSuggestionsOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const runSearch = useCallback(async (q) => {
    const term = (q || query).trim();
    if (!term) return;
    setSearching(true);
    setSuggestionsOpen(false);
    try {
      const r = await axios.get(`/api/stocks/search?query=${encodeURIComponent(term)}`, axiosConfig);
      const data = r.data || [];
      setResults(data);
      if (data.length > 0) setSelected(data[0]);
    } catch {
      setResults([]);
    } finally {
      setSearching(false);
    }
  }, [query]);

  const handleKey = (e) => {
    if (e.key === 'Enter') runSearch();
    if (e.key === 'Escape') { setSuggestionsOpen(false); }
  };

  const pickSuggestion = (s) => {
    setQuery(s.symbol);
    setSuggestionsOpen(false);
    runSearch(s.symbol);
  };

  const card = 'saas-panel';

  const hoverRow = dark ? 'hover:bg-[#202838]' : 'hover:bg-slate-50';

  const isFav = (sym) => favorites.some(f => f.symbol === sym);

  return (
    <div className="page-wrap space-y-5">
      <div>
        <p className="page-kicker">Research</p>
        <h1 className="page-title mt-1">Symbol search</h1>
        <p className="page-subtitle mt-1">Find stocks, ETFs, and crypto, then open a full analysis workspace.</p>
      </div>

      {/* Search bar */}
      <div className="relative" ref={suggestionsRef}>
        <div className={`flex items-center gap-3 px-4 py-3 rounded-lg border transition-colors ${
          dark
            ? 'bg-[#111111] border-[#2a2a2a] focus-within:border-blue-500/60'
            : 'bg-white border-slate-200 focus-within:border-blue-400'
        } shadow-sm`}>
          <SearchOutlined className="text-zinc-400 text-lg flex-shrink-0" />
          <input
            ref={inputRef}
            value={query}
            onChange={e => { setQuery(e.target.value); setSuggestionsOpen(e.target.value.length >= 2); }}
            onFocus={() => query.length >= 2 && setSuggestionsOpen(true)}
            onKeyDown={handleKey}
            placeholder="Search stocks, ETFs, crypto — try 'Apple' or 'NVDA'"
            className={`flex-1 bg-transparent outline-none text-base ${
              dark ? 'text-zinc-100 placeholder-zinc-500' : 'text-slate-800 placeholder-slate-400'
            }`}
            autoComplete="off"
          />
          {query && (
            <button onClick={() => { setQuery(''); setResults([]); setSelected(null); setSuggestions([]); }}
              className="text-zinc-500 hover:text-zinc-300 transition-colors">
              <CloseOutlined />
            </button>
          )}
          {searching
            ? <Spin size="small" />
            : (
              <button onClick={() => runSearch()}
                className="bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium px-4 py-1.5 rounded-md transition-colors">
                Search
              </button>
            )
          }
        </div>

        {/* Autocomplete dropdown */}
        {suggestionsOpen && suggestions.length > 0 && (
          <div className={`absolute w-full mt-1 rounded-lg border shadow-xl z-50 overflow-hidden ${
            dark ? 'bg-[#141414] border-[#2a2a2a]' : 'bg-white border-slate-200'
          }`}>
            {suggestions.slice(0, 8).map((s, i) => (
              <button key={`${s.symbol}-${i}`}
                className={`w-full flex items-center justify-between px-4 py-2.5 transition-colors text-left ${hoverRow}`}
                onClick={() => pickSuggestion(s)}>
                <div className="flex items-center gap-3">
                  <span className={`font-bold text-sm w-16 ${dark ? 'text-zinc-100' : 'text-slate-800'}`}>{s.symbol}</span>
                  <span className={`text-sm truncate max-w-[240px] ${dark ? 'text-zinc-400' : 'text-slate-500'}`}>{s.name}</span>
                </div>
                {s.type && (
                  <span className={`text-xs px-2 py-0.5 rounded-md flex-shrink-0 ${
                    dark ? 'bg-[#202838] text-zinc-400' : 'bg-slate-100 text-slate-500'
                  }`}>{s.type}</span>
                )}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* No query — show popular stocks */}
      {!results.length && !searching && (
        <div>
          <p className={`text-xs font-semibold uppercase tracking-wider mb-3 flex items-center gap-1.5 ${dark ? 'text-zinc-500' : 'text-slate-400'}`}>
            <FireOutlined /> Popular
          </p>
          <div className="flex flex-wrap gap-2">
            {POPULAR.map(s => (
              <button key={s.symbol}
                onClick={() => { setQuery(s.symbol); runSearch(s.symbol); }}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-sm font-medium border transition-colors ${
                  dark
                    ? 'bg-[#111111] border-[#2a2a2a] text-zinc-200 hover:border-blue-500/50 hover:text-blue-400'
                    : 'bg-white border-slate-200 text-slate-700 hover:border-blue-400 hover:text-blue-600'
                }`}>
                {s.symbol}
                <span className={`text-xs ${dark ? 'text-zinc-500' : 'text-slate-400'}`}>{s.name}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Search results + analysis */}
      {(results.length > 0 || selected) && (
        <div className="grid grid-cols-1 lg:grid-cols-[260px_1fr] gap-4 items-start">

          {/* Results list */}
          <div className={`${card} overflow-hidden`}>
            <div className={`px-4 py-3 border-b text-xs font-semibold uppercase tracking-wider ${
              dark ? 'border-[#222] text-zinc-500' : 'border-slate-100 text-slate-400'
            }`}>
              {results.length} result{results.length !== 1 ? 's' : ''}
            </div>
            {results.map(r => (
              <button key={r.symbol}
                onClick={() => setSelected(r)}
                className={`w-full flex items-center justify-between px-4 py-3 border-b transition-colors text-left last:border-b-0 ${
                  selected?.symbol === r.symbol
                    ? dark ? 'bg-blue-500/10 border-blue-500/20' : 'bg-blue-50 border-blue-100'
                    : dark ? 'border-[#1e1e1e] hover:bg-[#161616]' : 'border-slate-50 hover:bg-slate-50'
                }`}>
                <div>
                  <div className={`font-bold text-sm ${
                    selected?.symbol === r.symbol
                      ? 'text-blue-400'
                      : dark ? 'text-zinc-100' : 'text-slate-800'
                  }`}>{r.symbol}</div>
                  <div className={`text-xs mt-0.5 truncate max-w-[160px] ${dark ? 'text-zinc-500' : 'text-slate-400'}`}>
                    {r.name || '—'}
                  </div>
                </div>
                {selected?.symbol === r.symbol && (
                  <ArrowRightOutlined className="text-blue-400 text-xs flex-shrink-0" />
                )}
              </button>
            ))}
          </div>

          {/* Analysis panel */}
          <div className="min-w-0">
            {selected
              ? <Analysis selectedStock={selected} favorites={favorites} onToggleFavorite={onToggleFavorite} />
              : (
                <div className={`${card} p-12 text-center`}>
                  <p className={dark ? 'text-zinc-500' : 'text-slate-400'}>Select a stock to view analysis</p>
                </div>
              )
            }
          </div>
        </div>
      )}
    </div>
  );
};

export default SearchPage;
