import { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { Layout, message } from 'antd';
import axios from 'axios';
import { axiosConfig } from './config';
import StockSearch from './components/StockSearch';
import StockList from './components/StockList';
import Favorites from './components/Favorites';
import Analysis from './components/Analysis';
import WelcomeScreen from './components/WelcomeScreen';
import StockChart from './components/StockChart';
import './App.css';
import { API_URL } from './config';

const { Header, Content } = Layout;

// Define router future flags to suppress warnings
const routerFutureConfig = {
  v7_startTransition: true,
  v7_relativeSplatPath: true
};

function App() {
  const [stocks, setStocks] = useState([]);
  const [selectedStock, setSelectedStock] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [favorites, setFavorites] = useState([]);
  const [favoritesError, setFavoritesError] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchFavorites = async () => {
    try {
      setLoading(true);
      const response = await axios.get('/api/favorites', axiosConfig);
      setFavorites(response.data);
    } catch (error) {
      message.error('Failed to fetch favorites');
      console.error('Error fetching favorites:', error);
    } finally {
      setLoading(false);
    }
  };

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
      const isFavorite = favorites.some(fav => fav.symbol === stock.symbol);
      if (isFavorite) {
        await axios.delete(`/api/favorites/${stock.symbol}`, axiosConfig);
        message.success('Removed from favorites');
      } else {
        await axios.post('/api/favorites', stock, axiosConfig);
        message.success('Added to favorites');
      }
      fetchFavorites();
    } catch (error) {
      message.error('Failed to update favorites');
      console.error('Error updating favorites:', error);
    }
  };

  useEffect(() => {
    fetchFavorites();
  }, []);

  return (
    <Router future={routerFutureConfig}>
      <Layout className="min-h-screen bg-[#1a1a1a]">
        <Header className="bg-[#1a1a1a] border-b border-[#333333] sticky top-0 z-50">
          {/* Header content here */}
        </Header>
        <Content className="p-6 bg-[#1a1a1a]">
          <Routes>
            <Route path="/" element={<StockSearch onSearch={handleSearch} isLoading={isLoading} onToggleFavorite={toggleFavorite} favorites={favorites} />} />
            <Route path="/favorites" element={<Favorites favorites={favorites} onToggleFavorite={toggleFavorite} loading={loading} onSelectStock={handleStockSelect} />} />
            <Route path="/analysis/:symbol" element={<Analysis />} />
            <Route path="/chart/:symbol" element={<StockChart />} />
          </Routes>
          
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
        </Content>
      </Layout>
    </Router>
  );
}

export default App;
