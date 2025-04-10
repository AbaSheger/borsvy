import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Card, Spin, message } from 'antd';
import axios from 'axios';
import { axiosConfig } from '../config';
import StockChart from './StockChart';
import PeerComparison from './PeerComparison';
import NewsCard from './NewsCard';
import AnalysisVisualization from './AnalysisVisualization';
import { StarOutlined, StarFilled } from '@ant-design/icons';

const Analysis = ({ selectedStock }) => {
  const { symbol: routeSymbol } = useParams();
  const symbol = selectedStock?.symbol || routeSymbol;
  const [analysis, setAnalysis] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [interval, setInterval] = useState('1D');
  const [activeTab, setActiveTab] = useState('analysis');
  const [news, setNews] = useState([]);
  const [activeInterval, setActiveInterval] = useState('1D');
  const [currentPrice, setCurrentPrice] = useState(null);
  const [priceChange, setPriceChange] = useState(null);
  const [isFavorite, setIsFavorite] = useState(false);
  const [favorites, setFavorites] = useState([]);

  // Fetch favorites
  useEffect(() => {
    const fetchFavorites = async () => {
      try {
        const response = await axios.get('/api/favorites', axiosConfig);
        setFavorites(response.data);
        
        // Check if current stock is already a favorite
        if (symbol) {
          const isAlreadyFavorite = response.data.some(fav => fav.symbol === symbol);
          setIsFavorite(isAlreadyFavorite);
        }
      } catch (error) {
        console.error('Error fetching favorites:', error);
      }
    };

    fetchFavorites();
  }, [symbol]);

  // Toggle favorite
  const toggleFavorite = async () => {
    if (!analysis) return;
    
    try {
      const stockData = {
        symbol: symbol,
        name: analysis.name,
        price: currentPrice,
        change: priceChange,
        changePercent: priceChange
      };
      
      if (isFavorite) {
        await axios.delete(`/api/favorites/${symbol}`, axiosConfig);
        message.success(`${symbol} removed from favorites`);
        setIsFavorite(false);
      } else {
        await axios.post('/api/favorites', stockData, axiosConfig);
        message.success(`${symbol} added to favorites`);
        setIsFavorite(true);
      }
    } catch (error) {
      message.error('Failed to update favorites');
      console.error('Error updating favorites:', error);
    }
  };

  useEffect(() => {
    const fetchAnalysis = async () => {
      try {
        // Validate symbol
        if (!symbol || symbol === 'undefined') {
          setError('Invalid stock symbol');
          setLoading(false);
          return;
        }

        setLoading(true);
        const response = await axios.get(`/api/analysis/${symbol}`, axiosConfig);
        
        // Debug data received from API
        console.log('Raw API response data:', response.data);
        
        // Extract data from AI analysis if available
        let aiAnalysisPrice = null;
        let aiAnalysisChange = null;
        let aiAnalysisMarketCap = null;
        
        if (response.data.aiAnalysis) {
          console.log('AI Analysis data:', response.data.aiAnalysis);
          
          // More robust price extraction - first try exact price match patterns
          const exactPricePattern = /current(?:\s+price|\s+stock\s+price|\s+trading\s+price)?\s+(?:is|of)\s+\$?([\d,]+\.?\d*)/i;
          const exactPriceMatch = response.data.aiAnalysis.match(exactPricePattern);
          
          // If exact match fails, try to find any dollar amount in the first few sentences
          if (exactPriceMatch) {
            aiAnalysisPrice = parseFloat(exactPriceMatch[1].replace(/,/g, ''));
            console.log('Extracted exact price from AI analysis:', aiAnalysisPrice);
          } else {
            // Try finding any dollar amount in the first paragraph
            const firstParagraph = response.data.aiAnalysis.split('\n\n')[0];
            const priceMatch = firstParagraph.match(/\$\s*([\d,]+\.?\d*)/);
            if (priceMatch) {
              aiAnalysisPrice = parseFloat(priceMatch[1].replace(/,/g, ''));
              console.log('Extracted price from first paragraph:', aiAnalysisPrice);
            } else {
              // Last resort - find any dollar amount
              const anyPriceMatch = response.data.aiAnalysis.match(/\$\s*([\d,]+\.?\d*)/);
              if (anyPriceMatch) {
                aiAnalysisPrice = parseFloat(anyPriceMatch[1].replace(/,/g, ''));
                console.log('Extracted any price from AI analysis:', aiAnalysisPrice);
              }
            }
          }
          
          // Enhanced market cap extraction
          const marketCapPatterns = [
            /market\s+cap(?:italization)?\s+of\s+\$?\s*([\d,]+\.?\d*)\s+(trillion|billion|million|thousand|[TBMK])/i,
            /market\s+cap(?:italization)?\s+(?:is|at)\s+\$?\s*([\d,]+\.?\d*)\s+(trillion|billion|million|thousand|[TBMK])/i,
            /\$?\s*([\d,]+\.?\d*)\s+(trillion|billion|million|thousand|[TBMK])\s+market\s+cap/i
          ];
          
          let marketCapMatch = null;
          for (const pattern of marketCapPatterns) {
            const match = response.data.aiAnalysis.match(pattern);
            if (match) {
              marketCapMatch = match;
              break;
            }
          }
          
          if (marketCapMatch) {
            let capValue = parseFloat(marketCapMatch[1].replace(/,/g, ''));
            const unit = marketCapMatch[2].toLowerCase();
            
            if (unit.includes('trillion') || unit === 't') {
              capValue *= 1000000000000;
            } else if (unit.includes('billion') || unit === 'b') {
              capValue *= 1000000000;
            } else if (unit.includes('million') || unit === 'm') {
              capValue *= 1000000;
            } else if (unit.includes('thousand') || unit === 'k') {
              capValue *= 1000;
            }
            
            aiAnalysisMarketCap = capValue;
            console.log('Extracted market cap from AI analysis:', aiAnalysisMarketCap);
          }
          
          // Enhanced change percentage extraction
          const changePatterns = [
            /(up|down|increased|decreased|gained|lost|rose|fell|climbing|dropping|jumped|plunged).*?(\d+\.?\d*)%/i,
            /(\d+\.?\d*)%\s+(up|down|increase|decrease|gain|loss|rise|fall)/i,
            /change of\s+([+-]?\d+\.?\d*)%/i,
            /changed by\s+([+-]?\d+\.?\d*)%/i,
            /percent(?:age)? change of\s+([+-]?\d+\.?\d*)%?/i
          ];
          
          let changeMatch = null;
          for (const pattern of changePatterns) {
            const match = response.data.aiAnalysis.match(pattern);
            if (match) {
              changeMatch = match;
              break;
            }
          }
          
          if (changeMatch) {
            // Check if we have a numeric value with a sign first
            if (changeMatch[1].startsWith('+') || changeMatch[1].startsWith('-')) {
              aiAnalysisChange = parseFloat(changeMatch[1]);
            } else if (!isNaN(parseFloat(changeMatch[1]))) {
              // We have a percentage number
              let changeValue = parseFloat(changeMatch[1]);
              
              // Check if there's direction info
              if (changeMatch[2]) {
                if (changeMatch[2].match(/(down|decreased|decrease|lost|loss|fell|fall|dropping|plunged)/i)) {
                  changeValue = -changeValue;
                }
              } else if (changeMatch[0].match(/(down|decreased|decrease|lost|loss|fell|fall|dropping|plunged)/i)) {
                changeValue = -changeValue;
              }
              
              aiAnalysisChange = changeValue;
            }
            console.log('Extracted change % from AI analysis:', aiAnalysisChange);
          }
        }
        
        // Data validation and cleanup - prioritize data sources in this order:
        // 1. Selected stock props (passed from parent)
        // 2. Direct API response values
        // 3. Values extracted from AI analysis
        // 4. Default values
        
        const price = selectedStock?.price || response.data.price || aiAnalysisPrice || null;
        const change = selectedStock?.change || response.data.change || aiAnalysisChange || null;
        const changePercent = selectedStock?.changePercent || response.data.changePercent || aiAnalysisChange || null;
        
        // Format marketCap correctly - handle different formats coming from API
        let marketCap = null;
        if (selectedStock?.marketCap) {
          marketCap = selectedStock.marketCap;
          console.log('Market cap from selectedStock:', selectedStock.marketCap, typeof selectedStock.marketCap);
        } else if (response.data.marketCap) {
          console.log('Raw marketCap from API:', response.data.marketCap, typeof response.data.marketCap);
          
          // Some APIs might return market cap as a string with commas or K/M/B/T suffix
          if (typeof response.data.marketCap === 'string') {
            // Remove any non-numeric characters except decimal points
            const cleanValue = response.data.marketCap.replace(/[^0-9.]/g, '');
            marketCap = parseFloat(cleanValue);
            
            // Apply multiplier based on suffix if present
            if (response.data.marketCap.includes('T')) marketCap *= 1000000000000;
            else if (response.data.marketCap.includes('B')) marketCap *= 1000000000;
            else if (response.data.marketCap.includes('M')) marketCap *= 1000000;
            else if (response.data.marketCap.includes('K')) marketCap *= 1000;
            
            console.log('Converted string marketCap:', marketCap);
          } else {
            marketCap = response.data.marketCap;
            console.log('Numeric marketCap:', marketCap);
          }
        } else if (aiAnalysisMarketCap) {
          // Use market cap extracted from AI analysis
          marketCap = aiAnalysisMarketCap;
          console.log('Market cap from AI analysis:', aiAnalysisMarketCap);
        } else {
          // If we still don't have a market cap, try looking for companyOverview data
          if (response.data.companyOverview?.MarketCapitalization) {
            marketCap = parseFloat(response.data.companyOverview.MarketCapitalization);
            console.log('Market cap from companyOverview:', marketCap);
          } else {
            // Default value only as a last resort
            // Using a more realistic default for large companies (100B instead of 1B)
            marketCap = symbol === 'MSFT' || symbol === 'AAPL' || symbol === 'GOOGL' || symbol === 'AMZN' || symbol === 'META' ? 
              100000000000 : 1000000000;
            console.log('Using default market cap:', marketCap);
          }
        }
        
        console.log('Final processed values:', {
          price,
          change: changePercent,
          marketCap
        });
        
        // Set analysis data with properly validated values
        const analysisData = {
          ...response.data,
          price: price,
          change: change,
          changePercent: changePercent,
          marketCap: marketCap,
          volume: selectedStock?.volume || response.data.volume || null
        };
        setAnalysis(analysisData);
        
        // Set current price and price change with validation
        setCurrentPrice(price);
        if (changePercent !== null) {
          setPriceChange(changePercent);
        } else if (response.data.previousClose && price) {
          const calculatedChange = ((price - response.data.previousClose) / response.data.previousClose) * 100;
          setPriceChange(calculatedChange);
        }

        // Add debug logging to verify the data
        console.log('Data for display:', {
          currentPrice: price,
          priceChange: changePercent,
          marketCap: marketCap,
          priceType: typeof price,
          changeType: typeof changePercent,
          marketCapType: typeof marketCap
        });

        // Extract news from the response
        if (response.data.recentNews) {
          console.log('News data received:', response.data.recentNews);
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
        setError('Failed to fetch analysis data');
      } finally {
        setLoading(false);
      }
    };

    fetchAnalysis();
  }, [symbol, selectedStock]);

  const intervals = [
    { value: '1D', label: '1D' },
    { value: '1W', label: '1W' },
    { value: '1M', label: '1M' },
    { value: '3M', label: '3M' },
    { value: '1Y', label: '1Y' },
  ];

  if (loading) {
    return <Spin size="large" />;
  }

  if (error) {
    return <div className="text-red-500 text-center p-4">{error}</div>;
  }

  if (!analysis) {
    return <div>No analysis available</div>;
  }

  return (
    <div className="space-y-8 max-w-7xl mx-auto">
      {/* User guidance banner */}
      <div className="bg-blue-500/10 border border-blue-500/20 p-4 rounded-2xl">
        <div className="flex justify-between items-start">
          <div>
            <h3 className="text-lg font-semibold text-blue-400 mb-2 flex items-center">
              <span className="mr-2">ℹ️</span> 
              {analysis.name} ({symbol}) Analysis Dashboard
            </h3>
            <p className="text-gray-300">
              You're viewing comprehensive stock analysis for {analysis.name}. Explore price data, technical indicators, sentiment analysis, and news below.
            </p>
          </div>
          <button 
            onClick={toggleFavorite}
            className={`p-2 rounded-full transition-colors duration-300 ${
              isFavorite 
                ? 'bg-yellow-500/20 text-yellow-400 hover:bg-yellow-500/30' 
                : 'bg-[#262626] text-gray-400 hover:text-yellow-400 hover:bg-yellow-500/10'
            }`}
            aria-label={isFavorite ? "Remove from favorites" : "Add to favorites"}
          >
            {isFavorite ? (
              <StarFilled className="text-xl" />
            ) : (
              <StarOutlined className="text-xl" />
            )}
          </button>
        </div>
      </div>
      
      <div className="bg-[#1a1a1a] rounded-2xl shadow-lg p-4 sm:p-6 border border-[#333333]">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
          <div>
            <h2 className="text-2xl sm:text-3xl font-bold text-[#e6e6e6]">{analysis.name}</h2>
            <p className="text-gray-400 mt-1">{symbol}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            {intervals.map(interval => (
              <button
                key={interval.value}
                className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors duration-300 ${
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
        <div className="h-[300px] sm:h-[400px] mb-6 bg-[#1a1a1a] border border-[#333333] rounded-xl overflow-hidden">
          <StockChart 
            symbol={symbol} 
            interval={activeInterval} 
            theme="dark"
          />
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4 mb-6">
          <div className="bg-[#262626] rounded-xl p-4 border border-[#333333]">
            <p className="text-xs sm:text-sm font-medium text-gray-400">Current Price</p>
            <p className="text-xl sm:text-2xl font-bold text-[#e6e6e6]">
              ${currentPrice !== null && currentPrice !== undefined ? Number(currentPrice).toFixed(2) : 'N/A'}
            </p>
          </div>
          
          <div className={`rounded-xl p-4 border ${
            priceChange > 0 
              ? 'bg-green-500/10 border-green-500/20' 
              : priceChange < 0
                ? 'bg-red-500/10 border-red-500/20'
                : 'bg-[#262626] border-[#333333]'
          }`}>
            <p className="text-xs sm:text-sm font-bold text-gray-400">24h Change</p>
            <p className={`text-xl sm:text-2xl font-bold ${
              priceChange > 0 ? 'text-green-400' : 
              priceChange < 0 ? 'text-red-400' : 
              'text-gray-400'
            }`}>
              {priceChange !== null && priceChange !== undefined ? `${priceChange > 0 ? '+' : ''}${Number(priceChange).toFixed(2)}%` : 'N/A'}
            </p>
          </div>
          
          <div className="bg-[#262626] rounded-xl p-4 border border-[#333333]">
            <p className="text-xs sm:text-sm font-medium text-gray-400">Volume</p>
            <p className="text-xl sm:text-2xl font-bold text-[#e6e6e6]">
              {analysis?.volume ? formatNumber(Number(analysis.volume)) : 'N/A'}
            </p>
          </div>
          
          <div className="bg-[#262626] rounded-xl p-4 border border-[#333333]">
            <p className="text-xs sm:text-sm font-medium text-gray-400">Market Cap</p>
            <p className="text-xl sm:text-2xl font-bold text-[#e6e6e6]">
              {analysis?.marketCap !== null && analysis?.marketCap !== undefined ? formatMarketCap(Number(analysis.marketCap)) : 'N/A'}
            </p>
          </div>
        </div>
      </div>

      {/* Tab selector */}
      <div className="bg-[#1a1a1a] rounded-2xl shadow-lg border border-[#333333] overflow-hidden">
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

        <div className="p-4 sm:p-6">
          {activeTab === 'analysis' ? (
            <>
              <AnalysisVisualization 
                analysis={analysis}
              />
              {/* News Card */}
              <div className="mt-8">
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
  // Handle invalid or zero values
  if (!marketCap || isNaN(marketCap) || marketCap === 0) {
    return 'N/A';
  }
  
  console.log('Formatting market cap value:', marketCap, typeof marketCap);
  
  // Convert to trillions if value is greater than 1000 billion
  if (marketCap >= 1000000) {
    // For very large numbers (> 1000B), display as trillions
    return '$' + (marketCap / 1000000).toFixed(2) + ' T';
  }
  else if (marketCap > 1000) {
    // For large numbers > 1000 (billions), treat as billions
    return '$' + (marketCap / 1000).toFixed(2) + ' B';
  }
  else if (marketCap > 1) {
    // For medium numbers (1-1000), treat as millions
    return '$' + marketCap.toFixed(2) + ' M';
  }
  else {
    // For small numbers < 1, treat as thousands
    return '$' + (marketCap * 1000).toFixed(2) + ' K';
  }
}

export default Analysis;