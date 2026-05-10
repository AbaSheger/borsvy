import { useEffect, useState } from 'react';
import { BrowserRouter as Router, Link, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { Avatar, Button, Dropdown, Layout, Menu, message } from 'antd';
import {
  BellOutlined,
  CloseOutlined,
  CrownOutlined,
  FundOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuOutlined,
  RiseOutlined,
  SearchOutlined,
  StarOutlined,
  UserOutlined,
} from '@ant-design/icons';
import axios from 'axios';
import { axiosConfig } from './config';
import Favorites from './components/Favorites';
import Analysis from './components/Analysis';
import StockChart from './components/StockChart';
import ThemeToggle from './components/ThemeToggle';
import DashboardPage from './pages/DashboardPage';
import SearchPage from './pages/SearchPage';
import PortfolioPage from './pages/PortfolioPage';
import AlertsPage from './pages/AlertsPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ProfilePage from './pages/ProfilePage';
import './App.css';
import { useTheme } from './context/ThemeContext';
import { useAuth } from './context/AuthContext';

const { Header, Content, Sider } = Layout;

const routerFutureConfig = {
  v7_startTransition: true,
  v7_relativeSplatPath: true,
};

const pageMeta = {
  '/': ['Dashboard', 'Live market overview and watchlist'],
  '/search': ['Research', 'Search assets and open an analysis workspace'],
  '/favorites': ['Watchlist', 'Saved symbols and quick price checks'],
  '/portfolio': ['Portfolio', 'Track holdings, allocation, and return'],
  '/alerts': ['Alerts', 'Manage price conditions and notifications'],
  '/login': ['Sign in', 'Access your workspace'],
  '/register': ['Create account', 'Start a BorsVy workspace'],
  '/profile': ['Profile', 'Account and plan settings'],
};

const SidebarNavigation = ({ onNavigate }) => {
  const location = useLocation();
  const { theme } = useTheme();

  const getSelectedKeys = () => {
    if (location.pathname === '/') return ['home'];
    if (location.pathname === '/search') return ['search'];
    if (location.pathname === '/favorites') return ['favorites'];
    if (location.pathname === '/portfolio') return ['portfolio'];
    if (location.pathname === '/alerts') return ['alerts'];
    if (location.pathname.includes('/analysis')) return ['analysis'];
    return [];
  };

  return (
    <Menu
      theme={theme}
      mode="inline"
      selectedKeys={getSelectedKeys()}
      className={theme === 'dark' ? 'bg-transparent border-0 px-0 py-3' : 'bg-white border-0 px-0 py-3'}
      items={[
        { key: 'home', icon: <HomeOutlined />, label: <Link to="/" onClick={onNavigate}>Dashboard</Link> },
        { key: 'search', icon: <SearchOutlined />, label: <Link to="/search" onClick={onNavigate}>Research</Link> },
        { key: 'favorites', icon: <StarOutlined />, label: <Link to="/favorites" onClick={onNavigate}>Watchlist</Link> },
        { key: 'portfolio', icon: <FundOutlined />, label: <Link to="/portfolio" onClick={onNavigate}>Portfolio</Link> },
        { key: 'alerts', icon: <BellOutlined />, label: <Link to="/alerts" onClick={onNavigate}>Alerts</Link> },
      ]}
    />
  );
};

const UserMenu = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    message.success('Logged out');
    navigate('/');
  };

  if (!user) {
    return (
      <div className="flex items-center gap-2">
        <Button size="small" onClick={() => navigate('/login')}>Sign in</Button>
        <Button size="small" type="primary" onClick={() => navigate('/register')}>Start trial</Button>
      </div>
    );
  }

  const menuItems = [
    { key: 'profile', icon: <UserOutlined />, label: <span onClick={() => navigate('/profile')}>Profile</span> },
    user.isPro
      ? { key: 'plan', icon: <CrownOutlined className="text-yellow-400" />, label: <span className="text-yellow-400">Pro plan</span>, disabled: true }
      : { key: 'upgrade', icon: <CrownOutlined />, label: <span onClick={() => navigate('/pricing')}>Upgrade to Pro</span> },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: <span onClick={handleLogout} className="text-red-400">Sign out</span> },
  ];

  return (
    <Dropdown menu={{ items: menuItems }} placement="bottomRight">
      <button className="flex items-center gap-2 text-slate-600 dark:text-slate-300 hover:text-slate-950 dark:hover:text-white transition-colors px-2 py-1 rounded-md hover:bg-slate-100 dark:hover:bg-[#202838]">
        <Avatar size={24} icon={<UserOutlined />} className="bg-slate-200 text-slate-600 dark:bg-[#263142] dark:text-slate-200" />
        <span className="text-sm hidden sm:inline font-medium">{user.email.split('@')[0]}</span>
        {user.isPro && <CrownOutlined className="text-yellow-400 text-xs" />}
      </button>
    </Dropdown>
  );
};

const AppContent = () => {
  const [favorites, setFavorites] = useState([]);
  const [loading, setLoading] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(window.innerWidth < 768);
  const location = useLocation();
  const { theme } = useTheme();
  const currentMeta = location.pathname.startsWith('/analysis')
    ? ['Analysis', 'Detailed chart, AI summary, news, and peers']
    : pageMeta[location.pathname] || ['Workspace', 'Market intelligence'];

  useEffect(() => {
    const handleResize = () => setSidebarCollapsed(window.innerWidth < 768);
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  useEffect(() => {
    document.body.classList.toggle('dark', theme === 'dark');
  }, [theme]);

  const closeSidebarOnMobile = () => {
    if (window.innerWidth < 768) {
      setSidebarCollapsed(true);
    }
  };

  const fetchFavorites = async () => {
    try {
      setLoading(true);
      const response = await axios.get('/api/favorites', axiosConfig);
      setFavorites(response.data);
    } catch {
      // Favorites are optional for anonymous/local use.
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchFavorites();
  }, []);

  return (
    <Layout className="min-h-screen app-shell">
      <Sider
        trigger={null}
        collapsible
        collapsed={sidebarCollapsed}
        collapsedWidth={0}
        width={256}
        className={`fixed h-full z-30 left-0 top-0 ${theme === 'dark' ? 'app-sidebar' : 'bg-white border-r border-slate-200 shadow-sm'}`}
      >
        <div className={`h-16 px-4 flex items-center border-b ${theme === 'dark' ? 'border-[#243044]' : 'border-slate-200'}`}>
          <Link to="/" className="flex items-center gap-3 min-w-0">
            <div className="h-9 w-9 rounded-md bg-blue-600 text-white grid place-items-center shadow-sm">
              <RiseOutlined />
            </div>
            <div className="min-w-0">
              <div className={`text-base font-semibold leading-tight ${theme === 'dark' ? 'text-white' : 'text-slate-950'}`}>BorsVy</div>
              <div className={`text-[11px] leading-tight ${theme === 'dark' ? 'text-slate-400' : 'text-slate-500'}`}>Market intelligence</div>
            </div>
          </Link>
        </div>
        <div className={`px-4 pt-4 pb-2 text-[11px] font-semibold uppercase ${theme === 'dark' ? 'text-slate-500' : 'text-slate-400'}`}>Workspace</div>
        <SidebarNavigation onNavigate={closeSidebarOnMobile} />
      </Sider>

      <Layout className={`transition-all duration-300 ${sidebarCollapsed ? 'ml-0' : 'ml-[256px]'}`}>
        <Header className="sticky top-0 z-20 flex items-center h-16 px-4 app-header">
          <button
            onClick={() => setSidebarCollapsed(prev => !prev)}
            className="mr-4 h-9 w-9 rounded-md grid place-items-center text-slate-500 dark:text-slate-400 hover:text-slate-950 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-[#202838] transition-colors"
            aria-label={sidebarCollapsed ? 'Open navigation' : 'Close navigation'}
          >
            {sidebarCollapsed ? <MenuOutlined /> : <CloseOutlined />}
          </button>
          <div className="flex-1 flex justify-between items-center min-w-0">
            <div className="min-w-0">
              <div className="text-sm font-semibold text-slate-950 dark:text-slate-50 truncate">{currentMeta[0]}</div>
              <div className="text-xs text-slate-500 dark:text-slate-400 truncate hidden sm:block">{currentMeta[1]}</div>
            </div>
            <div className="flex items-center gap-3">
              <ThemeToggle />
              <UserMenu />
            </div>
          </div>
        </Header>

        <Content className="p-4 sm:p-6 app-content min-h-[calc(100vh-64px)]">
          <Routes>
            <Route path="/" element={<DashboardPage favorites={favorites} />} />
            <Route path="/search" element={<SearchPage favorites={favorites} onToggleFavorite={fetchFavorites} />} />
            <Route path="/favorites" element={<Favorites favorites={favorites} onToggleFavorite={fetchFavorites} loading={loading} />} />
            <Route path="/portfolio" element={<PortfolioPage />} />
            <Route path="/alerts" element={<AlertsPage />} />
            <Route path="/analysis/:symbol" element={<Analysis favorites={favorites} onToggleFavorite={fetchFavorites} />} />
            <Route path="/chart/:symbol" element={<StockChart />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/profile" element={<ProfilePage />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
};

function App() {
  const { theme } = useTheme();

  useEffect(() => {
    document.body.classList.toggle('dark', theme === 'dark');
  }, [theme]);

  return (
    <Router future={routerFutureConfig}>
      <AppContent />
    </Router>
  );
}

export default App;
