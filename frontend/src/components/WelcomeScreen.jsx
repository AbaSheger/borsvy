import PropTypes from 'prop-types';
import { InfoCircleOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useTheme } from '../context/ThemeContext';

function WelcomeScreen() {
  const navigate = useNavigate();
  const { theme } = useTheme();
  
  // Static popular stocks data
  const popularStocks = [
    { symbol: 'AAPL', name: 'Apple Inc.' },
    { symbol: 'MSFT', name: 'Microsoft Corporation' },
    { symbol: 'GOOGL', name: 'Alphabet Inc.' },
    { symbol: 'AMZN', name: 'Amazon.com Inc.' },
    { symbol: 'META', name: 'Meta Platforms Inc.' },
    { symbol: 'TSLA', name: 'Tesla Inc.' }
  ];

  // Function to handle stock click
  const handleStockClick = (symbol) => {
    navigate(`/analysis/${symbol}`);
  };

  return (
    <div className="w-full max-w-7xl mx-auto">
      <div className="text-center space-y-12">
        <div>
          <h2 className={`text-5xl font-bold ${theme === 'dark' ? 'text-white' : 'text-gray-800'} mb-6`}>
            Welcome to 
            <div className="inline-block transform hover:scale-105 transition-all duration-300 ml-3">
              <div className="bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500 p-1 rounded-2xl shadow-lg">
                <div className={`${theme === 'dark' ? 'bg-[#1a1a1a]' : 'bg-white'} rounded-xl px-4 py-2`}>
                  <span className="text-4xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-purple-400 tracking-tight">
                    Bö<span className="text-yellow-400">rs</span>vy
                  </span>
                </div>
              </div>
            </div>
          </h2>
          <p className={`text-xl ${theme === 'dark' ? 'text-gray-300' : 'text-gray-600'} max-w-2xl mx-auto`}>
            Your intelligent companion for stock market analysis and insights. Start by searching for a stock or explore our trending picks.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8 max-w-5xl mx-auto">
          <div className="bg-gray-800/50 p-6 rounded-xl border border-gray-700 backdrop-blur-sm">
            <h3 className="text-xl font-semibold text-white mb-4">Real-time Data</h3>
            <p className="text-gray-400">Get instant access to live market data and updates</p>
          </div>
          <div className="bg-gray-800/50 p-6 rounded-xl border border-gray-700 backdrop-blur-sm">
            <h3 className="text-xl font-semibold text-white mb-4">Smart Analytics</h3>
            <p className="text-gray-400">Advanced tools for market analysis and insights</p>
          </div>
          <div className="bg-gray-800/50 p-6 rounded-xl border border-gray-700 backdrop-blur-sm">
            <h3 className="text-xl font-semibold text-white mb-4">Market Intelligence</h3>
            <p className="text-gray-400">Stay informed with comprehensive market research</p>
          </div>
        </div>

        <div>
          <h3 className={`text-2xl font-bold ${theme === 'dark' ? 'text-white' : 'text-gray-800'} mb-8`}>Popular Stocks</h3>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-4 max-w-5xl mx-auto">
            {popularStocks.map(stock => (
              <button
                key={stock.symbol}
                onClick={() => handleStockClick(stock.symbol)}
                className={`group relative p-4 ${
                  theme === 'dark' 
                    ? 'bg-gray-800 border-gray-700' 
                    : 'bg-white border-gray-200'
                } rounded-xl border 
                hover:border-blue-500 hover:shadow-lg hover:shadow-blue-500/20 
                transition-all duration-300 overflow-hidden transform hover:-translate-y-1`}
              >
                <div className="absolute inset-0 bg-gradient-to-br from-blue-500/10 to-purple-500/10 
                              opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
                <div className="relative">
                  <span className={`text-xl font-bold ${
                    theme === 'dark' 
                      ? 'text-white group-hover:text-blue-400' 
                      : 'text-gray-800 group-hover:text-blue-600'
                  } transition-colors duration-300`}>
                    {stock.symbol}
                  </span>
                  <div className="absolute -bottom-1 left-0 w-0 h-0.5 bg-blue-500 
                                group-hover:w-full transition-all duration-300" />
                </div>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

export default WelcomeScreen;