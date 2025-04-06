import { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Link, useLocation } from 'react-router-dom';
import { Layout, Menu, message } from 'antd';
import { HomeOutlined, StarOutlined, LineChartOutlined, MenuOutlined, CloseOutlined } from '@ant-design/icons';
import axios from 'axios';
import { axiosConfig } from './config';
import StockSearch from './components/StockSearch';
import StockList from './components/StockList';
import Favorites from './components/Favorites';
import Analysis from './components/Analysis';
import WelcomeScreen from './components/WelcomeScreen';
import StockChart from './components/StockChart';
import ThemeToggle from './components/ThemeToggle';
import './App.css';
import { API_URL } from './config';
import { useTheme } from './context/ThemeContext';

const { Header, Content, Sider } = Layout;

// Define router future flags to suppress warnings
const routerFutureConfig = {
  v7_startTransition: true,
  v7_relativeSplatPath: true
};

// Navigation component for sidebar
const SidebarNavigation = ({ collapsed, onClose }) => {
  const location = useLocation();
  const pathName = location.pathname;
  const { theme } = useTheme();
  
  const getSelectedKeys = () => {
    if (pathName === '/') return ['home'];
    if (pathName === '/favorites') return ['favorites'];
    if (pathName.includes('/analysis')) return ['analysis'];
    if (pathName.includes('/chart')) return ['chart'];
    return [];
  };

  return (
    <Menu
      theme={theme}
      mode="inline"
      selectedKeys={getSelectedKeys()}
      className={`${theme === 'dark' ? 'bg-[#1a1a1a] border-r border-[#333333]' : 'bg-white border-r border-gray-200'}`}
      items={[
        {
          key: 'home',
          icon: <HomeOutlined />,
          label: <Link to="/">Home</Link>,
        },
        {
          key: 'favorites',
          icon: <StarOutlined />,
          label: <Link to="/favorites">Favorites</Link>,
        }
      ]}
    />
  );
};

// AppContent component that will be rendered inside the Router
const AppContent = () => {
  const [stocks, setStocks] = useState([]);
  const [selectedStock, setSelectedStock] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [favorites, setFavorites] = useState([]);
  const [favoritesError, setFavoritesError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(window.innerWidth < 768);
  const location = useLocation(); // Now this is being used safely inside the Router context
  
  // Clear selected stock when navigating to favorites page
  useEffect(() => {
    if (location.pathname === '/favorites') {
      setSelectedStock(null);
    }
  }, [location.pathname]);

  // Sidebar collapsed state handler
  const toggleSidebar = () => {
    setSidebarCollapsed(!sidebarCollapsed);
  };

  // Update sidebar collapsed state on window resize
  useEffect(() => {
    const handleResize = () => {
      setSidebarCollapsed(window.innerWidth < 768);
    };
    
    window.addEventListener('resize', handleResize);
    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, []);

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
    <Layout className="min-h-screen">
      <Sider 
        trigger={null}
        collapsible
        collapsed={sidebarCollapsed}
        collapsedWidth={0}
        width={250}
        className="fixed h-full z-30 left-0 top-0 dark:bg-[#1a1a1a] bg-white dark:border-[#333333] border-gray-200 border-r"
      >
        <div className="p-4 h-16 flex items-center border-b border-[#333333]">
          <Link to="/" className="flex items-center">
            <div className="transform transition-all duration-300">
              <div className="bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500 p-0.5 rounded-lg shadow-md">
                <div className="bg-[#1a1a1a] rounded-md px-3 py-1">
                  <span className="text-xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-purple-400 tracking-tight">
                    Bö<span className="text-yellow-400">rs</span>vy
                  </span>
                </div>
              </div>
            </div>
          </Link>
        </div>
        <SidebarNavigation collapsed={sidebarCollapsed} onClose={() => setSidebarCollapsed(true)} />
      </Sider>

      <Layout className={`transition-all duration-300 ${sidebarCollapsed ? 'ml-0' : 'ml-[250px]'}`}>
        <Header className="sticky top-0 z-20 flex items-center h-16 px-4 bg-white dark:bg-[#1a1a1a] border-b border-gray-200 dark:border-[#333333]">
          <button
            onClick={toggleSidebar}
            className="mr-4 text-gray-400 hover:text-white transition-colors"
          >
            {sidebarCollapsed ? <MenuOutlined /> : <CloseOutlined />}
          </button>
          <div className="flex-1 flex justify-between items-center">
            <div className="md:hidden">
              {!sidebarCollapsed && (
                <div className="transform transition-all duration-300">
                  <div className="bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500 p-0.5 rounded-lg shadow-md">
                    <div className="bg-[#1a1a1a] rounded-md px-3 py-1">
                      <span className="text-xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-purple-400 tracking-tight">
                        Bö<span className="text-yellow-400">rs</span>vy
                      </span>
                    </div>
                  </div>
                </div>
              )}
            </div>
            <div className="flex items-center">
              <ThemeToggle />
            </div>
          </div>
        </Header>

        <Content className="p-4 sm:p-6 bg-slate-100 dark:bg-[#1a1a1a]">
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

          {/* Only show welcome screen on home route when no stocks or selected stock */}
          {!stocks.length && !selectedStock && location.pathname === '/' ? (
            <div className="flex justify-center items-center min-h-[calc(100vh-300px)] -mt-8">
              <WelcomeScreen />
            </div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-4 gap-4 sm:gap-8 mt-4 sm:mt-8">
              {/* Stock list on the left */}
              <div className="lg:col-span-1">
                {stocks.length > 0 ? (
                  <>
                    <StockList
                      stocks={stocks}
                      selectedStock={selectedStock}
                      onSelectStock={handleStockSelect}
                      favorites={favorites}
                      onToggleFavorite={toggleFavorite}
                      isLoading={isLoading}
                    />
                    
                    {/* Quick access favorites in a horizontal scroll */}
                    <div className="mt-4 bg-[#1a1a1a] rounded-2xl shadow-md p-4 border border-[#333333]">
                      <div className="flex items-center justify-between mb-2">
                        <h3 className="text-lg font-semibold text-[#e6e6e6] flex items-center">
                          <StarOutlined className="mr-2 text-yellow-400" /> Quick Favorites
                        </h3>
                      </div>
                      {favorites.length > 0 ? (
                        <div className="overflow-x-auto pb-2 flex-nowrap hide-scrollbar">
                          <div className="flex space-x-2">
                            {favorites.map(stock => (
                              <button
                                key={stock.symbol}
                                onClick={() => handleStockSelect(stock)}
                                className={`flex-shrink-0 px-3 py-2 rounded-lg border ${
                                  selectedStock?.symbol === stock.symbol 
                                    ? 'bg-blue-500/20 border-blue-500 text-blue-400' 
                                    : 'bg-[#262626] border-[#333333] text-gray-300 hover:border-blue-500/50'
                                }`}
                              >
                                {stock.symbol}
                              </button>
                            ))}
                          </div>
                        </div>
                      ) : (
                        <p className="text-gray-500 text-sm">No favorites yet</p>
                      )}
                      <div className="mt-2 text-right">
                        <Link to="/favorites" className="text-xs text-blue-400 hover:text-blue-300">
                          View all favorites →
                        </Link>
                      </div>
                    </div>
                  </>
                ) : null}
              </div>
              
              {/* Stock analysis on the right */}
              <div className="lg:col-span-3">
                {selectedStock && <Analysis selectedStock={selectedStock} />}
              </div>
            </div>
          )}
        </Content>
      </Layout>
    </Layout>
  );
};

// Main App function - only responsible for setting up the Router
function App() {
  return (
    <Router future={routerFutureConfig}>
      <AppContent />
    </Router>
  );
}

export default App;
