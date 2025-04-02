import { useState, useEffect } from 'react';
import { useDebounce } from '../hooks/useDebounce';
import axios from 'axios';
import { API_URL } from '../config';

function SearchBar({ onSearch }) {
  const [query, setQuery] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const debouncedQuery = useDebounce(query, 500); // 500ms debounce

  useEffect(() => {
    const searchStocks = async () => {
      if (!debouncedQuery.trim()) {
        onSearch([]);
        return;
      }

      try {
        setIsLoading(true);
        setError(null);
        
        // Make the search less sensitive by adding wildcard-like behavior on backend requests
        // This simulates partial matching even if the backend doesn't support it natively
        const searchTerms = debouncedQuery.split(/\s+/).filter(term => term.length > 0);
        let results = [];
        
        // If specific enough (3+ chars), try the exact search first
        if (debouncedQuery.length >= 3) {
          const response = await axios.get(`${API_URL}/api/stocks/search?query=${encodeURIComponent(debouncedQuery)}`);
          results = response.data;
        }
        
        // If we have multiple terms or no results yet, try searching for individual terms
        if ((searchTerms.length > 1 || results.length === 0) && searchTerms.some(term => term.length >= 2)) {
          for (const term of searchTerms) {
            if (term.length >= 2) { // Only search terms with at least 2 characters
              const termResponse = await axios.get(`${API_URL}/api/stocks/search?query=${encodeURIComponent(term)}`);
              
              // Merge and remove duplicates
              if (termResponse.data.length > 0) {
                const newResults = [...results];
                for (const stock of termResponse.data) {
                  if (!newResults.some(existing => existing.symbol === stock.symbol)) {
                    newResults.push(stock);
                  }
                }
                results = newResults;
              }
            }
          }
        }
        
        // Try common stock symbols for very short queries (1-2 chars)
        if (debouncedQuery.length <= 2 && results.length === 0) {
          const commonPrefixes = ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'META', 'TSLA', 'NVDA', 'BRK', 'JPM', 'JNJ', 'WMT', 'V', 'PG'];
          const matchingCommon = commonPrefixes.filter(symbol => 
            symbol.toLowerCase().startsWith(debouncedQuery.toLowerCase())
          );
          
          for (const symbol of matchingCommon) {
            try {
              const symbolResponse = await axios.get(`${API_URL}/api/stocks/search?query=${encodeURIComponent(symbol)}`);
              results = [...results, ...symbolResponse.data];
            } catch (e) {
              console.error(`Error fetching common stock ${symbol}:`, e);
            }
          }
        }
        
        onSearch(results);
      } catch (err) {
        console.error('Search error:', err);
        if (err.response?.status === 429) {
          setError('Rate limit reached. Please wait a moment before trying again.');
        } else {
          setError('Failed to fetch search results. Please try again.');
        }
        onSearch([]);
      } finally {
        setIsLoading(false);
      }
    };

    searchStocks();
  }, [debouncedQuery, onSearch]);

  return (
    <div className="relative w-full">
      <div className="relative">
        <div className="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none">
          <svg className="w-4 h-4 text-gray-500" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 20 20">
            <path stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="m19 19-4-4m0-7A7 7 0 1 1 1 8a7 7 0 0 1 14 0Z"/>
          </svg>
        </div>
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by company name or ticker symbol..."
          className="w-full pl-10 pr-4 py-3 bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block"
        />
        {isLoading && (
          <div className="absolute right-3 top-3">
            <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-blue-500"></div>
          </div>
        )}
      </div>
      {error && (
        <div className="mt-2 text-sm text-red-500 bg-red-50 p-2 rounded">
          {error}
        </div>
      )}
    </div>
  );
}

export default SearchBar;