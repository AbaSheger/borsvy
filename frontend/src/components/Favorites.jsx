import PropTypes from 'prop-types';
import { useTheme } from '../context/ThemeContext';

function Favorites({ favorites, onSelectStock, onToggleFavorite }) {
  const { theme } = useTheme();
  
  // Better handling of empty, null, or undefined favorites array
  if (!favorites || !Array.isArray(favorites) || favorites.length === 0) {
    return (
      <div className={`${theme === 'dark' ? 'bg-[#1a1a1a] border-[#333333] text-[#e6e6e6]' : 'bg-white border-gray-200 text-gray-800'} rounded-xl p-6 border shadow-sm`}>
        <p className="text-center font-medium">No favorite stocks yet</p>
        <p className={`${theme === 'dark' ? 'text-gray-400' : 'text-gray-500'} text-center text-sm mt-2`}>Add stocks to your favorites for quick access</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {favorites.map((stock) => {
        // Null safety with default values
        const symbol = stock?.symbol || 'Unknown';
        const name = stock?.name || 'Unknown Company';
        const price = typeof stock?.price === 'number' ? stock.price : 0;
        const change = typeof stock?.change === 'number' ? stock.change : 0;
        const changePercent = typeof stock?.changePercent === 'number' ? stock.changePercent : 0;
        
        return (
          <div
            key={symbol}
            className={`${theme === 'dark' ? 'bg-[#1a1a1a] border-[#333333]' : 'bg-white border-gray-200'} rounded-xl border p-4 hover:border-blue-500 
                     hover:shadow-lg hover:shadow-blue-500/10 transition-all duration-300 cursor-pointer`}
            onClick={() => onSelectStock(stock)}
          >
            <div className="flex justify-between items-start">
              <div>
                <h3 className={`text-lg font-bold ${theme === 'dark' ? 'text-[#e6e6e6]' : 'text-gray-800'}`}>{symbol}</h3>
                <p className={`${theme === 'dark' ? 'text-gray-400' : 'text-gray-500'} font-medium`}>{name}</p>
              </div>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleFavorite(stock);
                }}
                className="text-red-500 hover:text-red-600 p-1 transition-colors duration-300"
                aria-label={`Remove ${symbol} from favorites`}
              >
                ❤️
              </button>
            </div>
            <div className="flex justify-between items-center mt-3">
              <span className={`text-xl font-bold ${theme === 'dark' ? 'text-[#e6e6e6]' : 'text-gray-800'}`}>${price.toFixed(2)}</span>
              <div className={`px-3 py-1 rounded-full text-sm font-bold ${
                change >= 0 
                  ? 'bg-green-500/10 text-green-400' 
                  : 'bg-red-500/10 text-red-400'
              }`}>
                <span>{change >= 0 ? '↑' : '↓'}</span>
                <span>{Math.abs(change).toFixed(2)}</span>
                <span> ({changePercent.toFixed(2)}%)</span>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

Favorites.propTypes = {
  favorites: PropTypes.array,
  onSelectStock: PropTypes.func.isRequired,
  onToggleFavorite: PropTypes.func.isRequired
};

export default Favorites;