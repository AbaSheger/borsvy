import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import axios from 'axios';

function StockDetail({ favorites, addToFavorites, removeFromFavorites }) {
  const { symbol } = useParams();
  const [stock, setStock] = useState(null);
  const [analysis, setAnalysis] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  const isFavorite = () => favorites.some(fav => fav.symbol === symbol);

  useEffect(() => {
    const fetchStockData = async () => {
      try {
        setIsLoading(true);
        // Fetch stock details
        const stockResponse = await axios.get(`http://localhost:8080/api/stocks/${symbol}/details`);
        setStock(stockResponse.data);
        
        // Fetch AI analysis
        const analysisResponse = await axios.get(`http://localhost:8080/api/analysis/${symbol}`);
        setAnalysis(analysisResponse.data);
      } catch (err) {
        setError('Failed to fetch stock data. Please try again later.');
        console.error('Error fetching stock data:', err);
      } finally {
        setIsLoading(false);
      }
    };

    fetchStockData();
  }, [symbol]);

  const handleFavoriteToggle = () => {
    if (stock) {
      if (isFavorite()) {
        removeFromFavorites(symbol);
      } else {
        addToFavorites(stock);
      }
    }
  };

  if (isLoading) {
    return (
      <div className="flex justify-center items-center py-16">
        <div className="animate-spin rounded-full h-16 w-16 border-t-2 border-b-2 border-blue-500"></div>
      </div>
    );
  }

  if (error || !stock) {
    return (
      <div className="bg-white rounded-lg shadow-md p-6 my-4">
        <h2 className="text-2xl font-semibold mb-4">Error</h2>
        <p className="text-red-600">{error || 'Stock not found'}</p>
        <Link to="/" className="text-blue-600 hover:underline mt-4 inline-block">
          Back to Home
        </Link>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-4">
        <Link to="/" className="text-blue-600 hover:underline">
          &larr; Back to Home
        </Link>
      </div>
      
      <div className="bg-white rounded-lg shadow-md p-6 mb-6">
        <div className="flex justify-between items-start">
          <div>
            <h1 className="text-3xl font-bold">{stock.symbol}</h1>
            <p className="text-xl text-gray-600">{stock.name}</p>
          </div>
          <button
            onClick={handleFavoriteToggle}
            className="text-yellow-500 hover:text-yellow-600 focus:outline-none"
          >
            {isFavorite() ? (
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-8 h-8">
                <path fillRule="evenodd" d="M10.788 3.21c.448-1.077 1.976-1.077 2.424 0l2.082 5.007 5.404.433c1.164.093 1.636 1.545.749 2.305l-4.117 3.527 1.257 5.273c.271 1.136-.964 2.033-1.96 1.425L12 18.354 7.373 21.18c-.996.608-2.231-.29-1.96-1.425l1.257-5.273-4.117-3.527c-.887-.76-.415-2.212.749-2.305l5.404-.433 2.082-5.006z" clipRule="evenodd" />
              </svg>
            ) : (
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-8 h-8">
                <path strokeLinecap="round" strokeLinejoin="round" d="M11.48 3.499a.562.562 0 011.04 0l2.125 5.111a.563.563 0 00.475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 00-.182.557l1.285 5.385a.562.562 0 01-.84.61l-4.725-2.885a.563.563 0 00-.586 0L6.982 20.54a.562.562 0 01-.84-.61l1.285-5.386a.562.562 0 00-.182-.557l-4.204-3.602a.563.563 0 01.321-.988l5.518-.442a.563.563 0 00.475-.345L11.48 3.5z" />
              </svg>
            )}
          </button>
        </div>
        
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-6">
          <div>
            <div className="mb-6">
              <h2 className="text-xl font-semibold mb-3">Price Information</h2>
              <div className="bg-gray-50 p-4 rounded-lg">
                <div className="flex justify-between mb-2">
                  <span className="text-gray-600">Current Price:</span>
                  <span className="font-bold">${stock.price.toFixed(2)}</span>
                </div>
                <div className="flex justify-between mb-2">
                  <span className="text-gray-600">Change:</span>
                  <span className={`font-bold ${stock.changePercent >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                    {stock.changePercent >= 0 ? '+' : ''}{stock.changePercent.toFixed(2)}%
                  </span>
                </div>
                <div className="flex justify-between mb-2">
                  <span className="text-gray-600">Open:</span>
                  <span>${stock.open.toFixed(2)}</span>
                </div>
                <div className="flex justify-between mb-2">
                  <span className="text-gray-600">High:</span>
                  <span>${stock.high.toFixed(2)}</span>
                </div>
                <div className="flex justify-between mb-2">
                  <span className="text-gray-600">Low:</span>
                  <span>${stock.low.toFixed(2)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Volume:</span>
                  <span>{stock.volume.toLocaleString()}</span>
                </div>
              </div>
            </div>
            
            <div>
              <h2 className="text-xl font-semibold mb-3">Company Information</h2>
              <div className="bg-gray-50 p-4 rounded-lg">
                <div className="flex justify-between mb-2">
                  <span className="text-gray-600">Industry:</span>
                  <span>{stock.industry}</span>
                </div>
                <div className="flex justify-between mb-2">
                  <span className="text-gray-600">Market Cap:</span>
                  <span>${(stock.marketCap / 1000000000).toFixed(2)}B</span>
                </div>
                <div className="flex justify-between mb-2">
                  <span className="text-gray-600">P/E Ratio:</span>
                  <span>{stock.peRatio.toFixed(2)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">52 Week Range:</span>
                  <span>${stock.low52Week.toFixed(2)} - ${stock.high52Week.toFixed(2)}</span>
                </div>
              </div>
            </div>
          </div>
          
          <div>
            <h2 className="text-xl font-semibold mb-3">✨ AI-Generated Analysis</h2>
            {analysis ? (
              <div className="bg-gray-50 p-4 rounded-lg">
                <div className="mb-4">
                  <h3 className="font-medium text-gray-800 mb-2">Technical Analysis</h3>
                  <p className="text-gray-700">{analysis.technical}</p>
                </div>
                <div>
                  <h3 className="font-medium text-gray-800 mb-2">Fundamental Analysis</h3>
                  <p className="text-gray-700">{analysis.fundamental}</p>
                </div>
                <div className="mt-4 text-sm text-gray-500">
                  <p>Analysis generated by Gemini 1.5 Pro AI on {new Date(analysis.timestamp).toLocaleDateString()}</p>
                </div>
              </div>
            ) : (
              <div className="bg-gray-50 p-4 rounded-lg text-gray-600">
                AI analysis is not available for this stock.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default StockDetail;