import { useState, useEffect, useRef } from 'react';
import PropTypes from 'prop-types';
import axios from 'axios';
import { InfoCircleOutlined, SearchOutlined, LoadingOutlined, FireOutlined, StarOutlined, StarFilled, BarChartOutlined } from '@ant-design/icons';
import { Tooltip, Input, Spin, ConfigProvider, List, Card, Button, message, Badge } from 'antd';
import debounce from 'lodash/debounce';
import { API_URL, axiosConfig } from '../config';
import { useNavigate } from 'react-router-dom';

function StockSearch({ onSearch, isLoading, popularStocks, onToggleFavorite, favorites }) {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const [isSearching, setIsSearching] = useState(false);
  const [lastSearchTime, setLastSearchTime] = useState(0);
  const searchRef = useRef(null);
  const navigate = useNavigate();

  // Debounced search function with rate limiting
  const debouncedSearch = useRef(
    debounce(async (searchQuery) => {
      if (!searchQuery.trim()) {
        setSuggestions([]);
        return;
      }

      // Rate limiting: Ensure at least 1 second between API calls
      const now = Date.now();
      const timeSinceLastSearch = now - lastSearchTime;
      if (timeSinceLastSearch < 1000) {
        await new Promise(resolve => setTimeout(resolve, 1000 - timeSinceLastSearch));
      }

      try {
        setIsSearching(true);
        const response = await axios.get(`${API_URL}/api/stocks/search?query=${searchQuery}`, axiosConfig);
        setSuggestions(response.data);
        setLastSearchTime(Date.now());
      } catch (error) {
        console.error('Error fetching suggestions:', error);
        if (error.response?.status === 429) {
          // If rate limited, wait longer before next request
          await new Promise(resolve => setTimeout(resolve, 2000));
        }
        setSuggestions([]);
      } finally {
        setIsSearching(false);
      }
    }, 500) // Increased debounce time to 500ms
  ).current;

  // Handle input changes
  const handleInputChange = (e) => {
    const value = e.target.value;
    setQuery(value);
    setSelectedIndex(-1);
    setShowSuggestions(true);
    debouncedSearch(value);
  };

  // Handle search with rate limiting
  const handleSearch = async () => {
    if (!query.trim()) return;

    // Rate limiting: Ensure at least 1 second between API calls
    const now = Date.now();
    const timeSinceLastSearch = now - lastSearchTime;
    if (timeSinceLastSearch < 1000) {
      await new Promise(resolve => setTimeout(resolve, 1000 - timeSinceLastSearch));
    }

    try {
      setIsSearching(true);
      const response = await axios.get(`${API_URL}/api/stocks/search?query=${query}`, axiosConfig);
      onSearch(response.data);
      setShowSuggestions(false);
      setLastSearchTime(Date.now());
    } catch (error) {
      console.error('Error searching stocks:', error);
      if (error.response?.status === 429) {
        // If rate limited, wait longer before next request
        await new Promise(resolve => setTimeout(resolve, 2000));
      }
    } finally {
      setIsSearching(false);
    }
  };

  // Handle suggestion selection
  const handleSuggestionSelect = (stock) => {
    setQuery(stock.symbol);
    setShowSuggestions(false);
    onSearch([stock]);
  };

  // Handle keyboard navigation
  const handleKeyDown = (e) => {
    if (!showSuggestions) return;

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setSelectedIndex(prev => 
          prev < suggestions.length - 1 ? prev + 1 : prev
        );
        break;
      case 'ArrowUp':
        e.preventDefault();
        setSelectedIndex(prev => prev > 0 ? prev - 1 : prev);
        break;
      case 'Enter':
        e.preventDefault();
        if (selectedIndex >= 0 && suggestions[selectedIndex]) {
          handleSuggestionSelect(suggestions[selectedIndex]);
        } else {
          handleSearch();
        }
        break;
      case 'Escape':
        setShowSuggestions(false);
        break;
      default:
        break;
    }
  };

  // Close suggestions when clicking outside
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (searchRef.current && !searchRef.current.contains(event.target)) {
        setShowSuggestions(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleStockClick = (symbol) => {
    navigate(`/analysis/${symbol}`);
  };

  return (
    <div className="relative" ref={searchRef}>
      <div className="relative max-w-5xl mx-auto px-4 py-12">
        {/* Logo Section */}
        <div className="text-center mb-16">
          <div className="inline-block transform hover:scale-105 transition-all duration-300">
            <div className="bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500 p-1 rounded-2xl shadow-2xl">
              <div className="bg-[#1a1a1a] rounded-xl p-6">
                <h1 className="text-5xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-purple-400 tracking-tight">
                  Bö<span className="text-yellow-400">rs</span>vy
                </h1>
                <p className="text-gray-400 mt-2 text-sm">Your Gateway to Smart Trading</p>
              </div>
            </div>
          </div>
        </div>

        {/* Search Instructions */}
        <div className="mb-4 text-center bg-blue-500/10 border border-blue-500/20 p-3 rounded-xl">
          <p className="text-blue-400 font-medium">
            <InfoCircleOutlined className="mr-1" /> Search for a stock symbol or company name, then click on a result to view detailed analysis
          </p>
        </div>

        {/* Search Section */}
        <div className="relative mb-12">
          <div className="relative">
            <Input
              size="large"
              placeholder="Search for stocks (e.g., AAPL, MSFT)"
              value={query}
              onChange={handleInputChange}
              onKeyDown={handleKeyDown}
              onFocus={() => setShowSuggestions(true)}
              prefix={
                isSearching ? (
                  <LoadingOutlined className="text-blue-400 text-xl" />
                ) : (
                  <SearchOutlined className="text-blue-400 text-xl" />
                )
              }
              suffix={
                <Tooltip title="Type a stock symbol or company name to see results">
                  <InfoCircleOutlined className="text-gray-400" />
                </Tooltip>
              }
              className="w-full h-16 text-lg rounded-xl shadow-lg hover:shadow-blue-500/20 transition-all duration-300"
              style={{ backgroundColor: '#262626', color: '#e6e6e6', borderColor: '#333333' }}
            />
          </div>
        </div>

        {/* Suggestions Dropdown */}
        {showSuggestions && suggestions.length > 0 && (
          <div className="absolute z-10 w-full mt-2 bg-[#1a1a1a] rounded-xl shadow-2xl border border-[#333333] max-h-96 overflow-y-auto backdrop-blur-lg">
            <div className="bg-blue-500/20 px-4 py-2 border-b border-[#333333]">
              <p className="text-blue-400 text-sm font-medium flex items-center">
                <BarChartOutlined className="mr-1" /> 
                Click on a stock to view detailed analysis and charts
              </p>
            </div>
            {suggestions.map((stock, index) => (
              <div
                key={stock.symbol}
                className={`px-6 py-4 cursor-pointer hover:bg-[#262626] transition-colors duration-150
                          ${index === selectedIndex ? 'bg-[#262626]' : ''}`}
                onClick={() => handleSuggestionSelect(stock)}
              >
                <div className="flex justify-between items-center">
                  <div>
                    <div className="font-bold text-[#e6e6e6] text-lg">{stock.symbol}</div>
                    <div className="text-sm text-gray-400">{stock.name}</div>
                  </div>
                  <div className="text-right">
                    <div className="font-semibold text-[#e6e6e6] text-lg">
                      ${stock.price?.toFixed(2) || 'N/A'}
                    </div>
                    {stock.changePercent !== undefined && (
                      <div className={`text-sm font-bold ${
                        stock.changePercent >= 0 ? 'text-green-400' : 'text-red-400'
                      }`}>
                        {stock.changePercent >= 0 ? '+' : ''}{stock.changePercent.toFixed(2)}%
                      </div>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Quick Access Section */}
        <div className="mt-16">
          <div className="flex items-center justify-between mb-8">
            <div className="flex items-center space-x-2">
              <FireOutlined className="text-orange-500 text-2xl" />
              <h3 className="text-2xl font-bold text-[#e6e6e6]">Trending Stocks</h3>
            </div>
            <Tooltip title="Click on any stock to see detailed analysis, charts, and news">
              <InfoCircleOutlined className="text-gray-400 text-xl" />
            </Tooltip>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-4">
            {['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'META', 'TSLA'].map(symbol => (
              <button
                key={symbol}
                onClick={() => {
                  setQuery(symbol);
                  handleSearch();
                }}
                className="group relative p-4 bg-[#1a1a1a] rounded-xl border border-[#333333] 
                         hover:border-blue-500 hover:shadow-lg hover:shadow-blue-500/20 
                         transition-all duration-300 overflow-hidden transform hover:-translate-y-1"
              >
                <div className="absolute inset-0 bg-gradient-to-br from-blue-500/10 to-purple-500/10 
                              opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
                <div className="relative">
                  <span className="text-xl font-bold text-[#e6e6e6] group-hover:text-blue-400 
                                 transition-colors duration-300">{symbol}</span>
                  <div className="absolute -bottom-1 left-0 w-0 h-0.5 bg-blue-500 
                                group-hover:w-full transition-all duration-300" />
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* Features Section */}
        <div className="mt-16 grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="bg-[#1a1a1a]/50 p-6 rounded-xl border border-[#333333] backdrop-blur-sm">
            <StarOutlined className="text-yellow-400 text-2xl mb-4" />
            <h4 className="text-[#e6e6e6] font-semibold mb-2">Real-time Data</h4>
            <p className="text-gray-400 text-sm">Get instant access to live market data and updates</p>
          </div>
          <div className="bg-[#1a1a1a]/50 p-6 rounded-xl border border-[#333333] backdrop-blur-sm">
            <FireOutlined className="text-orange-400 text-2xl mb-4" />
            <h4 className="text-[#e6e6e6] font-semibold mb-2">Smart Analytics</h4>
            <p className="text-gray-400 text-sm">Advanced tools for market analysis and insights</p>
          </div>
          <div className="bg-[#1a1a1a]/50 p-6 rounded-xl border border-[#333333] backdrop-blur-sm">
            <InfoCircleOutlined className="text-blue-400 text-2xl mb-4" />
            <h4 className="text-[#e6e6e6] font-semibold mb-2">Market Intelligence</h4>
            <p className="text-gray-400 text-sm">Stay informed with comprehensive market research</p>
          </div>
        </div>

        {/* Search Results */}
        <div className="mt-16">
          {suggestions.length > 0 && (
            <div className="mb-4 bg-blue-500/10 border border-blue-500/20 p-3 rounded-xl">
              <p className="text-blue-400 font-medium flex items-center">
                <BarChartOutlined className="mr-2" /> 
                Click on any stock card below to view detailed analysis, charts, and latest news
              </p>
            </div>
          )}
          <List
            grid={{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4 }}
            dataSource={isLoading ? [] : suggestions}
            renderItem={(stock) => (
              <List.Item>
                <Badge.Ribbon text="Click for Analysis" color="blue">
                  <Card
                    hoverable
                    onClick={() => handleStockClick(stock.symbol)}
                    className="transition-all duration-300 hover:shadow-lg hover:shadow-blue-500/20 border border-[#333333]"
                    cover={
                      <div className="h-24 bg-gradient-to-r from-blue-500/20 to-purple-500/20 flex items-center justify-center">
                        <span className="text-3xl font-bold text-white">{stock.symbol}</span>
                      </div>
                    }
                    actions={[
                      <Tooltip title="View detailed analysis" key="analysis">
                        <Button 
                          type="text" 
                          icon={<BarChartOutlined />} 
                          onClick={(e) => {
                            e.stopPropagation();
                            handleStockClick(stock.symbol);
                          }}
                        />
                      </Tooltip>,
                      <Tooltip title={favorites.some(f => f.symbol === stock.symbol) ? "Remove from favorites" : "Add to favorites"} key="favorite">
                        <Button
                          type="text"
                          icon={favorites.some(f => f.symbol === stock.symbol) ? <StarFilled style={{ color: '#faad14' }} /> : <StarOutlined />}
                          onClick={(e) => {
                            e.stopPropagation();
                            onToggleFavorite(stock);
                          }}
                        />
                      </Tooltip>
                    ]}
                  >
                    <Card.Meta
                      title={
                        <div className="flex justify-between items-center">
                          <span>{stock.symbol}</span>
                          <span className={`text-sm font-bold ${
                            stock.changePercent >= 0 ? 'text-green-500' : 'text-red-500'
                          }`}>
                            {stock.changePercent >= 0 ? '+' : ''}{stock.changePercent?.toFixed(2)}%
                          </span>
                        </div>
                      }
                      description={
                        <div>
                          <p className="truncate">{stock.name}</p>
                          <p className="font-semibold">${stock.price?.toFixed(2) || 'N/A'}</p>
                        </div>
                      }
                    />
                  </Card>
                </Badge.Ribbon>
              </List.Item>
            )}
          />
        </div>
      </div>
    </div>
  );
}

StockSearch.propTypes = {
  onSearch: PropTypes.func.isRequired,
  isLoading: PropTypes.bool,
  popularStocks: PropTypes.array,
  onToggleFavorite: PropTypes.func.isRequired,
  favorites: PropTypes.array.isRequired
};

export default StockSearch;