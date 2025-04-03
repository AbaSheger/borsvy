import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Card, Spin, message } from 'antd';
import axios from 'axios';
import { axiosConfig } from '../config';
import StockChart from './StockChart';
import PeerComparison from './PeerComparison';
import NewsCard from './NewsCard';
import AnalysisVisualization from './AnalysisVisualization';

const Analysis = () => {
  const { symbol } = useParams();
  const [analysis, setAnalysis] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [interval, setInterval] = useState('1D');
  const [activeTab, setActiveTab] = useState('analysis');
  const [news, setNews] = useState([]);
  const [activeInterval, setActiveInterval] = useState('1D');

  useEffect(() => {
    const fetchAnalysis = async () => {
      try {
        setLoading(true);
        const response = await axios.get(`/api/stocks/${symbol}/analysis`, axiosConfig);
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
      } catch (error) {
        message.error('Failed to fetch analysis');
        console.error('Error fetching analysis:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchAnalysis();
  }, [symbol]);

  const intervals = [
    { value: '1D', label: '1D' },
    { value: '1W', label: '1W' },
    { value: '1M', label: '1M' },
    { value: '3M', label: '3M' },
    { value: '1Y', label: '1Y' },
  ];

  // Calculate change percentage
  const calculateChangePercentage = () => {
    if (analysis?.previousClose && analysis?.price) {
      return ((analysis.price - analysis.previousClose) / analysis.previousClose * 100).toFixed(2);
    }
    return analysis?.changePercent?.toFixed(2) || 'N/A';
  };

  const changePercentage = calculateChangePercentage();
  const isPositiveChange = parseFloat(changePercentage) >= 0;

  if (loading) {
    return <Spin size="large" />;
  }

  if (!analysis) {
    return <div>No analysis available</div>;
  }

  return (
    <div className="space-y-6">
      <div className="bg-[#1a1a1a] rounded-xl shadow-sm p-6 border border-[#333333]">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-2xl font-bold text-[#e6e6e6]">{analysis.name}</h2>
            <p className="text-gray-400 mt-1">{symbol}</p>
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
            symbol={symbol} 
            interval={activeInterval} 
            theme="dark"
          />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          <div className="bg-[#262626] rounded-lg p-4 border border-[#333333]">
            <p className="text-sm font-medium text-gray-400">Current Price</p>
            <p className="text-2xl font-bold text-[#e6e6e6]">
              ${analysis.price?.toFixed(2) || 'N/A'}
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
              {analysis.volume ? formatNumber(analysis.volume) : 'N/A'}
            </p>
          </div>
          
          <div className="bg-[#262626] rounded-lg p-4 border border-[#333333]">
            <p className="text-sm font-medium text-gray-400">Market Cap</p>
            <p className="text-2xl font-bold text-[#e6e6e6]">
              {analysis.marketCap ? formatMarketCap(analysis.marketCap) : 'N/A'}
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
            <PeerComparison symbol={symbol} />
          )}
        </div>
      </div>
    </div>
  );
};

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

export default Analysis;