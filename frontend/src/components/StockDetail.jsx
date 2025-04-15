import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import axios from 'axios';
import { axiosConfig } from '../config';

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
        const stockResponse = await axios.get(`/api/stocks/${symbol}/details`, axiosConfig);
        setStock(stockResponse.data);
        
        // Fetch AI analysis
        const analysisResponse = await axios.get(`/api/analysis/${symbol}`, axiosConfig);
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
      <div className="bg-white rounded-2xl shadow-lg p-6 my-4">
        <h2 className="text-2xl font-semibold mb-4">Error</h2>
        <p className="text-red-600">{error || 'Stock not found'}</p>
        <Link to="/" className="text-blue-600 hover:underline mt-4 inline-block">
          Back to Home
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4">
      <div className="mb-6">
        <Link to="/" className="text-blue-600 hover:underline inline-flex items-center">
          <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-1" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M9.707 16.707a1 1 0 01-1.414 0l-6-6a1 1 0 010-1.414l6-6a1 1 0 011.414 1.414L5.414 9H17a1 1 0 110 2H5.414l4.293 4.293a1 1 0 010 1.414z" clipRule="evenodd" />
          </svg>
          Back to Home
        </Link>
      </div>
      
      <div className="bg-white rounded-2xl shadow-lg p-4 sm:p-6 mb-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-gray-100 pb-4 sm:pb-6 mb-4 sm:mb-6">
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold">{stock.symbol}</h1>
            <p className="text-lg text-gray-600">{stock.name}</p>
          </div>
          <button
            onClick={handleFavoriteToggle}
            className="text-yellow-500 hover:text-yellow-600 focus:outline-none self-start sm:self-center"
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
        
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-6">
          <div className="space-y-4 sm:space-y-6">
            <div>
              <h2 className="text-xl font-semibold mb-3">Price Information</h2>
              <div className="bg-gray-50 p-4 rounded-xl">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <p className="text-sm text-gray-600">Current Price</p>
                    <p className="text-lg font-bold">${stock.price.toFixed(2)}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">Change</p>
                    <p className={`text-lg font-bold ${stock.changePercent >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                      {stock.changePercent >= 0 ? '+' : ''}{stock.changePercent.toFixed(2)}%
                    </p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">Open</p>
                    <p className="text-base font-medium">${stock.open.toFixed(2)}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">High</p>
                    <p className="text-base font-medium">${stock.high.toFixed(2)}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">Low</p>
                    <p className="text-base font-medium">${stock.low.toFixed(2)}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">Volume</p>
                    <p className="text-base font-medium">{stock.volume.toLocaleString()}</p>
                  </div>
                </div>
              </div>
            </div>
            
            <div>
              <h2 className="text-xl font-semibold mb-3">Company Information</h2>
              <div className="bg-gray-50 p-4 rounded-xl">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <p className="text-sm text-gray-600">Industry</p>
                    <p className="text-base font-medium">{stock.industry}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">Market Cap</p>
                    <p className="text-base font-medium">${(stock.marketCap / 1000000000).toFixed(2)}B</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">P/E Ratio</p>
                    <p className="text-base font-medium">{stock.peRatio.toFixed(2)}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">52 Week Range</p>
                    <p className="text-base font-medium">${stock.low52Week.toFixed(2)} - ${stock.high52Week.toFixed(2)}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
          
          <div>
            <h2 className="text-xl font-semibold mb-3">✨ AI-Generated Analysis</h2>
            {analysis ? (
              <div className="bg-gray-50 p-4 rounded-xl h-full">
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
              <div className="bg-gray-50 p-4 rounded-xl text-gray-600 h-full flex items-center justify-center">
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