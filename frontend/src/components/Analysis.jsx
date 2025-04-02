import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import axios from 'axios';
import StockChart from './StockChart';
import PeerComparison from './PeerComparison';
import NewsCard from './NewsCard';
import AnalysisVisualization from './AnalysisVisualization';
import { API_URL } from '../config';

function Analysis({ selectedStock }) {
  const [analysis, setAnalysis] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [interval, setInterval] = useState('1D');
  const [activeTab, setActiveTab] = useState('analysis');
  const [news, setNews] = useState([]);
  const [activeInterval, setActiveInterval] = useState('1D');

  useEffect(() => {
    let isMounted = true;

    const fetchAnalysis = async () => {
      if (!selectedStock?.symbol) return;

      try {
        setIsLoading(true);
        setError(null);
        const response = await axios.get(`${API_URL}/api/stocks/${selectedStock.symbol}/analysis`);
        if (isMounted) {
          setAnalysis(response.data);
          
          // Extract news from the response
          if (response.data.recentNews) {
            console.log('News data received:', response.data.recentNews);
            // Ensure each news article has all required fields
            const formattedNews = response.data.recentNews.map(article => ({
              title: article.title || 'No title',
              url: article.url || '#',
              source: article.source || 'Unknown source',
              date: article.date || 'No date',
              summary: article.summary || 'No summary available',
              thumbnail: article.thumbnail || 'https://placehold.co/150x150/1a1a1a/666666/png?text=No+Image'
            }));
            setNews(formattedNews);
          } else {
            console.log('No news data in response:', response.data);
            setNews([]);
          }
        }
      } catch (err) {
        console.error('Analysis error:', err);
        if (isMounted) {
          setError('Failed to fetch analysis data');
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    fetchAnalysis();

    return () => {
      isMounted = false;
    };
  }, [selectedStock?.symbol, activeInterval]);

  const intervals = [
    { value: '1D', label: '1D' },
    { value: '1W', label: '1W' },
    { value: '1M', label: '1M' },
    { value: '3M', label: '3M' },
    { value: '1Y', label: '1Y' },
  ];

  // Calculate change percentage
  const calculateChangePercentage = () => {
    if (selectedStock?.previousClose && selectedStock?.price) {
      return ((selectedStock.price - selectedStock.previousClose) / selectedStock.previousClose * 100).toFixed(2);
    }
    return selectedStock?.changePercent?.toFixed(2) || 'N/A';
  };

  const changePercentage = calculateChangePercentage();
  const isPositiveChange = parseFloat(changePercentage) >= 0;

  if (!selectedStock) {
    return (
      <div className="bg-[#1a1a1a] rounded-xl shadow-sm p-6 border border-[#333333]">
        <p className="text-gray-400 text-center font-medium">Select a stock to view analysis</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="bg-[#1a1a1a] rounded-xl shadow-sm p-6 border border-[#333333]">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-2xl font-bold text-[#e6e6e6]">{selectedStock.name}</h2>
            <p className="text-gray-400 mt-1">{selectedStock.symbol}</p>
          </div>
          <div className="flex space-x-2">
            {intervals.map(interval => (
              <button
                key={interval.value}
                className={`px-3 py-1 rounded-lg text-sm font-medium transition-colors duration-300 ${
                  activeInterval === interval.value 
                    ? 'bg-blue-500 text-white'
                    : 'bg-[#262626] text-gray-400 hover:text-[#e6e6e6]'
                }`}
                onClick={() => setActiveInterval(interval.value)}
              >
                {interval.label}
              </button>
            ))}
          </div>
        </div>

        {/* Chart Section */}
        <div className="h-[400px] mb-6 bg-[#1a1a1a] border border-[#333333] rounded-lg overflow-hidden">
          <StockChart 
            symbol={selectedStock.symbol} 
            interval={activeInterval} 
            theme="dark"
          />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          <div className="bg-[#262626] rounded-lg p-4 border border-[#333333]">
            <p className="text-sm font-medium text-gray-400">Current Price</p>
            <p className="text-2xl font-bold text-[#e6e6e6]">
              ${selectedStock.price?.toFixed(2) || 'N/A'}
            </p>
          </div>
          
          <div className={`rounded-lg p-4 border ${
            isPositiveChange 
              ? 'bg-green-500/10 border-green-500/20' 
              : 'bg-red-500/10 border-red-500/20'
          }`}>
            <p className="text-sm font-bold text-gray-400">24h Change</p>
            <p className={`text-2xl font-bold ${
              isPositiveChange ? 'text-green-400' : 'text-red-400'
            }`}>
              {isPositiveChange ? '+' : ''}{changePercentage}%
            </p>
          </div>
          
          <div className="bg-[#262626] rounded-lg p-4 border border-[#333333]">
            <p className="text-sm font-medium text-gray-400">Volume</p>
            <p className="text-2xl font-bold text-[#e6e6e6]">
              {selectedStock.volume ? formatNumber(selectedStock.volume) : 'N/A'}
            </p>
          </div>
          
          <div className="bg-[#262626] rounded-lg p-4 border border-[#333333]">
            <p className="text-sm font-medium text-gray-400">Market Cap</p>
            <p className="text-2xl font-bold text-[#e6e6e6]">
              {selectedStock.marketCap ? formatMarketCap(selectedStock.marketCap) : 'N/A'}
            </p>
          </div>
        </div>
      </div>

      {/* Tab selector */}
      <div className="bg-[#1a1a1a] rounded-xl shadow-md border border-[#333333] overflow-hidden">
        <div className="flex border-b border-[#333333]">
          <button
            className={`flex-1 py-3 px-4 text-center font-semibold ${
              activeTab === 'analysis' 
                ? 'text-blue-400 border-b-2 border-blue-500 bg-blue-500/10' 
                : 'text-gray-400 hover:text-[#e6e6e6] hover:bg-[#262626]'
            }`}
            onClick={() => setActiveTab('analysis')}
          >
            Stock Analysis
          </button>
          <button
            className={`flex-1 py-3 px-4 text-center font-semibold ${
              activeTab === 'peers' 
                ? 'text-blue-400 border-b-2 border-blue-500 bg-blue-500/10' 
                : 'text-gray-400 hover:text-[#e6e6e6] hover:bg-[#262626]'
            }`}
            onClick={() => setActiveTab('peers')}
          >
            Industry Comparison
          </button>
        </div>

        <div className="p-6">
          {activeTab === 'analysis' ? (
            <>
              <AnalysisVisualization 
                analysis={analysis}
              />
              {/* News Card */}
              <div className="mt-6">
                <NewsCard news={news} />
              </div>
            </>
          ) : (
            <PeerComparison symbol={selectedStock.symbol} />
          )}
        </div>
      </div>
    </div>
  );
}

// Helper functions for formatting
function formatNumber(num) {
  if (num >= 1000000000) {
    return (num / 1000000000).toFixed(2) + 'B';
  }
  if (num >= 1000000) {
    return (num / 1000000).toFixed(2) + 'M';
  }
  if (num >= 1000) {
    return (num / 1000).toFixed(2) + 'K';
  }
  return num.toString();
}

function formatMarketCap(marketCap) {
  if (marketCap >= 1000000) {
    return '$' + (marketCap / 1000000).toFixed(2) + ' T';
  }
  if (marketCap >= 1000) {
    return '$' + (marketCap / 1000).toFixed(2) + ' B';
  }
  return '$' + marketCap.toFixed(2) + ' M';
}

Analysis.propTypes = {
  selectedStock: PropTypes.shape({
    symbol: PropTypes.string.isRequired,
    name: PropTypes.string,
    price: PropTypes.number,
    change: PropTypes.number,
    changePercent: PropTypes.number,
    previousClose: PropTypes.number,
    volume: PropTypes.number,
    marketCap: PropTypes.number,
    industry: PropTypes.string,
  }),
};

export default Analysis;