import { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { Input, Button, Spin, Card, Empty, message, Tag, Tabs, Tooltip, Select, AutoComplete } from 'antd';
import { SearchOutlined, StarOutlined, StarFilled, HistoryOutlined, FireOutlined, FilterOutlined, CloseCircleOutlined, LineChartOutlined } from '@ant-design/icons';
import { axiosConfig } from '../config';
import { useDebounce } from '../hooks/useDebounce';

const { Option } = Select;

// Popular stock symbols for quick access
const POPULAR_STOCKS = [
  { symbol: 'AAPL', name: 'Apple Inc.' },
  { symbol: 'MSFT', name: 'Microsoft Corp.' },
  { symbol: 'GOOGL', name: 'Alphabet Inc.' },
  { symbol: 'AMZN', name: 'Amazon.com Inc.' },
  { symbol: 'TSLA', name: 'Tesla Inc.' },
  { symbol: 'META', name: 'Meta Platforms Inc.' },
  { symbol: 'NVDA', name: 'NVIDIA Corp.' },
  { symbol: 'BTC-USD', name: 'Bitcoin USD' },
];

const StockSearch = ({ onSearch, isLoading, onToggleFavorite, favorites = [] }) => {
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const debouncedSearchTerm = useDebounce(debouncedQuery, 500);
  const [searching, setSearching] = useState(false);
  const [results, setResults] = useState([]);
  const [error, setError] = useState(null);
  const [searchHistory, setSearchHistory] = useState(() => {
    const savedHistory = localStorage.getItem('searchHistory');
    return savedHistory ? JSON.parse(savedHistory) : [];
  });
  const [suggestions, setSuggestions] = useState([]);
  const [activeFilter, setActiveFilter] = useState('all');
  const [showSuggestions, setShowSuggestions] = useState(false);
  const suggestionsRef = useRef(null);
  
  // Handle outside clicks to close suggestions
  useEffect(() => {
    function handleClickOutside(event) {
      if (suggestionsRef.current && !suggestionsRef.current.contains(event.target)) {
        setShowSuggestions(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, []);

  // Load search history on component mount
  useEffect(() => {
    const savedHistory = localStorage.getItem('searchHistory');
    if (savedHistory) {
      try {
        const parsed = JSON.parse(savedHistory);
        setSearchHistory(parsed);
      } catch (e) {
        console.error('Failed to parse search history:', e);
        localStorage.removeItem('searchHistory');
      }
    }
  }, []);

  // Save search history to localStorage when it changes
  useEffect(() => {
    localStorage.setItem('searchHistory', JSON.stringify(searchHistory));
  }, [searchHistory]);

  // Fetch suggestions on debounced query change
  useEffect(() => {
    const fetchSuggestions = async () => {
      if (!debouncedSearchTerm || debouncedSearchTerm.length < 2) {
        setSuggestions([]);
        return;
      }

      try {
        // Changed from "q" to "query" to match backend API
        const response = await axios.get(`/api/stocks/autocomplete?query=${encodeURIComponent(debouncedSearchTerm.trim())}`, axiosConfig);
        setSuggestions(response.data || []);
      } catch (err) {
        console.error('Autocomplete error:', err);
        setSuggestions([]);
      }
    };

    fetchSuggestions();
  }, [debouncedSearchTerm]);

  const handleSearch = async (searchQuery = query) => {
    if (!searchQuery.trim()) {
      message.info('Please enter a search term');
      return;
    }

    try {
      setSearching(true);
      setError(null);
      
      // Add to search history (avoid duplicates)
      if (!searchHistory.some(item => item.query === searchQuery)) {
        setSearchHistory(prev => [{
          query: searchQuery,
          timestamp: new Date().toISOString(),
        }, ...prev].slice(0, 10)); // Keep only the 10 most recent searches
      } else {
        // Move to top if exists
        setSearchHistory(prev => [
          { query: searchQuery, timestamp: new Date().toISOString() },
          ...prev.filter(item => item.query !== searchQuery)
        ].slice(0, 10));
      }

      // Changed from "q" to "query" to match backend API
      const response = await axios.get(`/api/stocks/search?query=${encodeURIComponent(searchQuery.trim())}&type=${activeFilter}`, axiosConfig);
      const searchResults = response.data;
      
      setResults(searchResults);
      if (onSearch) {
        onSearch(searchResults);
      }
      
      if (searchResults.length === 0) {
        message.info('No results found');
      }
    } catch (err) {
      console.error('Search error:', err);
      setError('Failed to fetch results. Please try again.');
      setResults([]);
      if (onSearch) {
        onSearch([]);
      }
    } finally {
      setSearching(false);
      setShowSuggestions(false);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handleQueryChange = (e) => {
    const value = e.target.value;
    setQuery(value);
    setDebouncedQuery(value);
    if (value.length >= 2) {
      setShowSuggestions(true);
    } else {
      setShowSuggestions(false);
    }
  };

  const handleSuggestionClick = (suggestion) => {
    setQuery(suggestion.symbol);
    setShowSuggestions(false);
    handleSearch(suggestion.symbol);
  };

  const handleClearHistory = () => {
    setSearchHistory([]);
    localStorage.removeItem('searchHistory');
    message.success('Search history cleared');
  };

  const handlePopularStockClick = (stock) => {
    setQuery(stock.symbol);
    handleSearch(stock.symbol);
  };

  const isFavorite = (symbol) => {
    return favorites.some(fav => fav.symbol === symbol);
  };

  // Filter options
  const filterOptions = [
    { value: 'all', label: 'All' },
    { value: 'stock', label: 'Stocks' },
    { value: 'etf', label: 'ETFs' },
    { value: 'crypto', label: 'Crypto' },
    { value: 'forex', label: 'Forex' },
  ];

  // Tabs items for Ant Design v5 format
  const tabItems = [
    {
      key: 'popular',
      label: <span><FireOutlined /> Popular</span>,
      children: (
        <div className="flex flex-wrap gap-2 mt-2">
          {POPULAR_STOCKS.map(stock => (
            <Tag 
              key={stock.symbol}
              color="blue"
              className="cursor-pointer py-1 px-3 text-sm"
              onClick={() => handlePopularStockClick(stock)}
            >
              {stock.symbol}
            </Tag>
          ))}
        </div>
      )
    },
    {
      key: 'history',
      label: <span><HistoryOutlined /> Recent Searches</span>,
      children: (
        <div className="flex flex-wrap gap-2 mt-2">
          {searchHistory.length > 0 ? (
            searchHistory.map((item, index) => (
              <Tag
                key={index}
                className="cursor-pointer py-1 px-3 text-sm bg-gray-100 dark:bg-[#333333] text-gray-800 dark:text-gray-300"
                onClick={() => handleSearch(item.query)}
              >
                {item.query}
              </Tag>
            ))
          ) : (
            <span className="text-gray-500 dark:text-gray-400 text-sm">No recent searches</span>
          )}
        </div>
      ),
      extra: searchHistory.length > 0 && (
        <Button 
          type="text" 
          size="small" 
          onClick={handleClearHistory}
          className="text-gray-500 hover:text-red-500"
        >
          Clear
        </Button>
      )
    }
  ];

  return (
    <div className="bg-white dark:bg-[#1a1a1a] rounded-2xl shadow-md p-6 border border-gray-200 dark:border-[#333333] transition-all duration-300">
      <h2 className="text-2xl font-bold mb-6 text-gray-800 dark:text-gray-200 flex items-center">
        <LineChartOutlined className="mr-3 text-blue-500" /> Stock Search
      </h2>
      
      <div className="flex flex-col mb-6">
        <div className="flex items-center">
          <div className="relative flex-1" ref={suggestionsRef}>
            <Input
              placeholder="Search for stocks, ETFs, or companies..."
              value={query}
              onChange={handleQueryChange}
              onKeyPress={handleKeyPress}
              onFocus={() => query.length >= 2 && setShowSuggestions(true)}
              prefix={<SearchOutlined className="text-gray-400" />}
              suffix={query && <CloseCircleOutlined className="text-gray-400 cursor-pointer" onClick={() => setQuery('')} />}
              className="rounded-l-lg dark:bg-[#262626] dark:border-[#333333] dark:text-gray-200 h-12 text-base"
              size="large"
            />
            
            {/* Suggestions dropdown */}
            {showSuggestions && suggestions.length > 0 && (
              <div className="absolute w-full bg-white dark:bg-[#262626] border border-gray-200 dark:border-[#333333] shadow-lg rounded-b-lg mt-1 max-h-72 overflow-y-auto z-50">
                {suggestions.map((suggestion, index) => (
                  <div
                    key={`${suggestion.symbol}-${index}`}
                    className="px-4 py-3 cursor-pointer hover:bg-gray-100 dark:hover:bg-[#333333] transition-colors duration-200 flex justify-between items-center"
                    onClick={() => handleSuggestionClick(suggestion)}
                  >
                    <div>
                      <div className="font-medium text-gray-900 dark:text-gray-200">{suggestion.symbol}</div>
                      <div className="text-sm text-gray-600 dark:text-gray-400">{suggestion.name}</div>
                    </div>
                    <div className="text-xs bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-400 px-2 py-1 rounded">
                      {suggestion.type || 'Stock'}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
          
          <div className="ml-2">
            <Select
              defaultValue="all"
              value={activeFilter}
              onChange={setActiveFilter}
              className="dark:bg-[#262626] h-12 border-0 dark:border-[#333333]"
              style={{ width: 120 }}
              size="large"
              popupMatchSelectWidth={false} // Fixed deprecated property
              suffixIcon={<FilterOutlined />}
            >
              {filterOptions.map(option => (
                <Option key={option.value} value={option.value}>
                  {option.label}
                </Option>
              ))}
            </Select>
          </div>
          
          <Button
            type="primary"
            onClick={() => handleSearch()}
            loading={searching}
            className="rounded-r-lg bg-blue-500 hover:bg-blue-600 border-blue-500 h-12 px-6 ml-2"
            size="large"
          >
            Search
          </Button>
        </div>
        
        {/* Popular searches and history tabs with modern items API */}
        <div className="mt-5">
          <Tabs 
            defaultActiveKey="popular" 
            size="small" 
            className="dark:text-gray-300"
            items={tabItems}
          />
        </div>
      </div>

      {error && (
        <div className="bg-red-100 dark:bg-red-900/30 border border-red-400 dark:border-red-500 text-red-700 dark:text-red-300 px-4 py-3 rounded mb-4 animate-pulse">
          {error}
        </div>
      )}

      {searching && (
        <div className="flex justify-center py-12">
          <div className="flex flex-col items-center">
            <Spin size="large" />
            <p className="mt-4 text-gray-500 dark:text-gray-400">Searching for stocks...</p>
          </div>
        </div>
      )}

      {!searching && results.length === 0 && !error && (
        <div className="bg-gray-50 dark:bg-[#1d1d1d] rounded-lg p-8 mt-4 text-center border border-gray-200 dark:border-[#333333]">
          <Empty
            description={
              <span className="text-gray-500 dark:text-gray-400 text-base">
                Start searching for stocks, ETFs, or cryptocurrency to see results
              </span>
            }
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            className="my-8"
          />
          <p className="text-sm text-gray-500 dark:text-gray-400 max-w-md mx-auto">
            Try searching for a company name like "Apple" or a stock symbol like "AAPL", or browse the popular searches above.
          </p>
        </div>
      )}

      {/* Search stats - only show when we have results but not searching */}
      {!searching && results.length > 0 && (
        <div className="flex justify-between items-center px-1 py-2 mb-4 text-sm text-gray-500 dark:text-gray-400 border-b border-gray-200 dark:border-[#333333]">
          <span>Found {results.length} result{results.length !== 1 ? 's' : ''} for "{query}"</span>
          <span className="text-xs bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-400 px-2 py-1 rounded">
            {activeFilter === 'all' ? 'All Types' : filterOptions.find(o => o.value === activeFilter)?.label}
          </span>
        </div>
      )}
    </div>
  );
};

export default StockSearch;