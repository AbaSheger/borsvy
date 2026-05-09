import { useNavigate } from 'react-router-dom';
import { Popconfirm } from 'antd';
import { DeleteOutlined, LineChartOutlined, StarFilled } from '@ant-design/icons';
import axios from 'axios';
import { message } from 'antd';
import { axiosConfig } from '../config';
import { useTheme } from '../context/ThemeContext';

const Favorites = ({ favorites = [], onToggleFavorite, loading }) => {
  const navigate = useNavigate();
  const { theme } = useTheme();
  const dark = theme === 'dark';

  const removeFavorite = async (symbol) => {
    try {
      await axios.delete(`/api/favorites/${symbol}`, axiosConfig);
      message.success(`${symbol} removed from favorites`);
      onToggleFavorite?.();
    } catch {
      message.error('Failed to remove favorite');
    }
  };

  const card = dark
    ? 'bg-[#111111] border border-[#222222] rounded-2xl'
    : 'bg-white border border-slate-200 rounded-2xl';

  if (!favorites.length) {
    return (
      <div className={`${card} p-12 text-center`}>
        <StarFilled className="text-3xl text-zinc-600 mb-3" />
        <p className={`font-medium mb-1 ${dark ? 'text-zinc-300' : 'text-slate-700'}`}>No favorites yet</p>
        <p className={`text-sm ${dark ? 'text-zinc-500' : 'text-slate-400'}`}>
          Search for stocks and star them to add to your watchlist
        </p>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto space-y-3">
      <h1 className={`text-2xl font-bold mb-5 flex items-center gap-2 ${dark ? 'text-zinc-100' : 'text-slate-800'}`}>
        <StarFilled className="text-yellow-400" /> Watchlist
        <span className={`text-base font-normal ${dark ? 'text-zinc-500' : 'text-slate-400'}`}>
          ({favorites.length})
        </span>
      </h1>

      {favorites.map(stock => {
        const pos = (stock.changePercent ?? 0) >= 0;
        return (
          <div key={stock.symbol}
            className={`${card} p-4 flex items-center justify-between gap-4 hover:border-blue-500/40 transition-colors`}>
            <button
              onClick={() => navigate(`/analysis/${stock.symbol}`)}
              className="flex items-center gap-4 flex-1 text-left min-w-0">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className={`font-bold ${dark ? 'text-zinc-100' : 'text-slate-800'}`}>{stock.symbol}</span>
                  <LineChartOutlined className="text-blue-400 text-xs" />
                </div>
                <p className={`text-xs truncate ${dark ? 'text-zinc-500' : 'text-slate-400'}`}>{stock.name}</p>
              </div>
            </button>

            <div className="text-right flex-shrink-0">
              <div className={`font-bold tabular-nums ${dark ? 'text-zinc-100' : 'text-slate-800'}`}>
                ${(stock.price ?? 0).toFixed(2)}
              </div>
              <div className={`text-xs font-semibold ${pos ? 'text-emerald-400' : 'text-rose-400'}`}>
                {pos ? '+' : ''}{(stock.changePercent ?? 0).toFixed(2)}%
              </div>
            </div>

            <Popconfirm
              title="Remove from favorites?"
              onConfirm={() => removeFavorite(stock.symbol)}
              okText="Remove"
              cancelText="Cancel">
              <button className={`text-zinc-500 hover:text-rose-400 transition-colors p-1`}>
                <DeleteOutlined />
              </button>
            </Popconfirm>
          </div>
        );
      })}
    </div>
  );
};

export default Favorites;
