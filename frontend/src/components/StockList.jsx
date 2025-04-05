import PropTypes from 'prop-types';

function StockList({ stocks, onSelectStock, favorites, onToggleFavorite, isLoading = false }) {
  // Improved loading state with more visual feedback
  if (isLoading) {
    return (
      <div className="bg-[#1a1a1a] rounded-xl shadow-sm p-8 border border-[#333333]">
        <div className="flex flex-col items-center justify-center">
          <div className="animate-spin rounded-full h-10 w-10 border-4 border-blue-500 border-t-transparent mb-4"></div>
          <span className="text-[#e6e6e6] font-semibold">Searching for stocks...</span>
          <span className="text-gray-400 text-sm mt-2">This may take a moment due to API rate limits</span>
        </div>
      </div>
    );
  }

  // More robust empty check that also handles undefined or null arrays
  if (!stocks || !Array.isArray(stocks) || stocks.length === 0) {
    return (
      <div className="bg-[#1a1a1a] rounded-xl shadow-sm p-8 border border-[#333333]">
        <div className="text-center">
          <p className="text-[#e6e6e6] font-bold text-lg">No stocks found</p>
          <p className="text-gray-400 text-sm mt-2">
            Try searching for a stock symbol like "AAPL" or "MSFT"
          </p>
        </div>
      </div>
    );
  }

  // Remove duplicates based on symbol
  const uniqueStocks = stocks.reduce((acc, current) => {
    const exists = acc.find(item => item.symbol === current.symbol);
    if (!exists) {
      acc.push(current);
    }
    return acc;
  }, []);

  return (
    <div className="space-y-4">
      <div className="text-sm font-bold text-gray-400 mb-4">
        Found {uniqueStocks.length} stock{uniqueStocks.length !== 1 ? 's' : ''}
      </div>
      
      {uniqueStocks.map((stock) => (
        <div
          key={stock.symbol}
          onClick={() => onSelectStock(stock)}
          className="bg-[#1a1a1a] rounded-xl p-6 border border-[#333333] hover:border-blue-500 
                   cursor-pointer transition-all duration-300 hover:shadow-lg hover:shadow-blue-500/10
                   flex flex-col gap-3"
        >
          <div className="flex justify-between items-start">
            <div className="flex-1">
              <h3 className="text-xl font-bold text-[#e6e6e6]">{stock.symbol}</h3>
              <p className="text-gray-400 text-sm mt-1">{stock.name}</p>
            </div>
            <button
              onClick={(e) => {
                e.stopPropagation();
                onToggleFavorite(stock);
              }}
              className={`p-2 transition-colors duration-300 rounded-full hover:bg-[#333333] ${
                favorites.some(f => f.symbol === stock.symbol)
                  ? 'text-red-500 hover:text-red-600'
                  : 'text-gray-400 hover:text-red-500'
              }`}
            >
              {favorites.some(f => f.symbol === stock.symbol) ? '❤️' : '🤍'}
            </button>
          </div>
          
          <div className="flex justify-between items-center mt-2">
            <span className="text-2xl font-bold text-[#e6e6e6]">
              ${stock.price?.toFixed(2) || 'N/A'}
            </span>
            {stock.changePercent !== undefined && (
              <div className={`px-4 py-2 rounded-full text-sm font-bold ${
                stock.changePercent >= 0 
                  ? 'bg-green-500/10 text-green-400'
                  : 'bg-red-500/10 text-red-400'
              }`}>
                {stock.changePercent >= 0 ? '+' : ''}{stock.changePercent.toFixed(2)}%
              </div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}

StockList.propTypes = {
  stocks: PropTypes.array,
  onSelectStock: PropTypes.func.isRequired,
  favorites: PropTypes.array.isRequired,
  onToggleFavorite: PropTypes.func.isRequired,
  isLoading: PropTypes.bool
};

export default StockList;