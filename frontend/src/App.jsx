import { useState, useEffect } from 'react';
import axios from 'axios';
import StockSearch from './components/StockSearch';
import StockList from './components/StockList';
import Favorites from './components/Favorites';
import Analysis from './components/Analysis';
import WelcomeScreen from './components/WelcomeScreen';
import { ConfigProvider } from 'antd';
import './App.css';
import { API_URL } from './config';

function App() {
  const [stocks, setStocks] = useState([]);
  const [selectedStock, setSelectedStock] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [favorites, setFavorites] = useState([]);
  const [favoritesError, setFavoritesError] = useState(null);

  // Only fetch favorites on component mount
  useEffect(() => {
    const fetchFavorites = async () => {
      try {
        const favoritesResponse = await axios.get(`${API_URL}/api/favorites`);
        setFavorites(favoritesResponse.data);
      } catch (err) {
        console.error('Error fetching favorites:', err);
        if (err.response?.status === 403 || err.message.includes('Network Error')) {
          setError('Cannot connect to the backend server. Please ensure it is running.');
        }
      }
    };

    fetchFavorites();
  }, []);

  const handleSearch = async (searchResults) => {
    try {
      setStocks(searchResults);
      setSelectedStock(null);
      setError(null);
    } catch (err) {
      console.error('Search error:', err);
      setError('Failed to fetch search results. Please try again.');
      setStocks([]);
      setSelectedStock(null);
    }
  };

  const handleStockSelect = (stock) => {
    setSelectedStock(stock);
    setError(null);
  };

  const toggleFavorite = async (stock) => {
    try {
      const isFavorite = favorites.some(f => f.symbol === stock.symbol);
      if (isFavorite) {
        await axios.delete(`${API_URL}/api/favorites/${stock.symbol}`);
        setFavorites(favorites.filter(f => f.symbol !== stock.symbol));
      } else {
        await axios.post(`${API_URL}/api/favorites`, stock);
        setFavorites([...favorites, stock]);
      }
      setFavoritesError(null);
    } catch (err) {
      console.error('Error toggling favorite:', err);
      setFavoritesError('Failed to update favorites. Please try again.');
    }
  };

  return (
    <ConfigProvider
      theme={{
        token: {
          colorBgContainer: '#1a1a1a',
          colorBgLayout: '#111111',
          colorText: '#e6e6e6',
          colorTextSecondary: '#a3a3a3',
          colorBorder: '#333333',
          colorPrimary: '#3b82f6',
          colorPrimaryHover: '#60a5fa',
          colorPrimaryActive: '#2563eb',
          colorBgElevated: '#1a1a1a',
          colorBgSpotlight: '#262626',
          colorBgMask: 'rgba(0, 0, 0, 0.75)',
        },
        components: {
          Card: {
            colorBgContainer: '#1a1a1a',
            colorBorder: '#333333',
          },
          Table: {
            colorBgContainer: '#1a1a1a',
            colorBgHeader: '#262626',
            colorBorder: '#333333',
            colorText: '#e6e6e6',
            colorTextSecondary: '#a3a3a3',
          },
          Button: {
            colorBgContainer: '#262626',
            colorBorder: '#333333',
            colorText: '#e6e6e6',
            colorPrimary: '#3b82f6',
            colorPrimaryHover: '#60a5fa',
            colorPrimaryActive: '#2563eb',
          },
          Input: {
            colorBgContainer: '#262626',
            colorBorder: '#333333',
            colorText: '#e6e6e6',
            colorTextPlaceholder: '#666666',
          },
          Select: {
            colorBgContainer: '#262626',
            colorBorder: '#333333',
            colorText: '#e6e6e6',
            colorTextPlaceholder: '#666666',
          },
          Modal: {
            colorBgContainer: '#1a1a1a',
            colorBgMask: 'rgba(0, 0, 0, 0.75)',
            colorText: '#e6e6e6',
            colorTextSecondary: '#a3a3a3',
          },
          Drawer: {
            colorBgContainer: '#1a1a1a',
            colorBgMask: 'rgba(0, 0, 0, 0.75)',
            colorText: '#e6e6e6',
            colorTextSecondary: '#a3a3a3',
          },
          Tooltip: {
            colorBgContainer: '#262626',
            colorText: '#e6e6e6',
          },
          Dropdown: {
            colorBgContainer: '#1a1a1a',
            colorText: '#e6e6e6',
          },
          Menu: {
            colorBgContainer: '#1a1a1a',
            colorText: '#e6e6e6',
            colorTextSecondary: '#a3a3a3',
          },
          Tabs: {
            colorBgContainer: '#1a1a1a',
            colorText: '#e6e6e6',
            colorTextSecondary: '#a3a3a3',
          },
          Tag: {
            colorBgContainer: '#262626',
            colorText: '#e6e6e6',
            colorBorder: '#333333',
          },
          Alert: {
            colorBgContainer: '#262626',
            colorText: '#e6e6e6',
            colorTextSecondary: '#a3a3a3',
          },
          Badge: {
            colorBgContainer: '#262626',
            colorText: '#e6e6e6',
          },
          Progress: {
            colorBgContainer: '#262626',
            colorText: '#e6e6e6',
          },
          Timeline: {
            colorBgContainer: '#1a1a1a',
            colorText: '#e6e6e6',
            colorTextSecondary: '#a3a3a3',
          },
          Spin: {
            colorBgContainer: '#1a1a1a',
            colorText: '#e6e6e6',
          },
        },
      }}
    >
      <div className="min-h-screen bg-[#111111]">
        <div className="container mx-auto px-4 py-8">
          <StockSearch onSearch={handleSearch} isLoading={isLoading} />
          
          {error && (
            <div className="bg-red-900/50 border border-red-500 rounded-xl p-4 mt-4">
              <p className="text-red-200 font-medium">{error}</p>
            </div>
          )}

          {favoritesError && (
            <div className="bg-red-900/50 border border-red-500 rounded-xl p-4 mt-4">
              <p className="text-red-200 font-medium">{favoritesError}</p>
            </div>
          )}

          {!stocks.length && !selectedStock ? (
            <div className="flex justify-center items-center min-h-[calc(100vh-300px)] -mt-8">
              <WelcomeScreen />
            </div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-4 gap-4 sm:gap-8 mt-4 sm:mt-8">
              <div className="lg:col-span-1">
                {stocks.length > 0 ? (
                  <StockList
                    stocks={stocks}
                    selectedStock={selectedStock}
                    onSelectStock={handleStockSelect}
                    favorites={favorites}
                    onToggleFavorite={toggleFavorite}
                    isLoading={isLoading}
                  />
                ) : null}
              </div>
              
              <div className="lg:col-span-3 space-y-4 sm:space-y-8">
                <Favorites
                  favorites={favorites}
                  onSelectStock={setSelectedStock}
                  onToggleFavorite={toggleFavorite}
                />
                
                {selectedStock && <Analysis selectedStock={selectedStock} />}
              </div>
            </div>
          )}
        </div>
      </div>
    </ConfigProvider>
  );
}

export default App;
