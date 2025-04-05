import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import axios from 'axios';
import { InfoCircleOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';

function StockDetails({ symbol }) {
  const [details, setDetails] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchDetails = async () => {
      try {
        setIsLoading(true);
        setError(null);
        const response = await axios.get(`http://localhost:8080/api/stocks/${symbol}/details`);
        setDetails(response.data);
      } catch (err) {
        setError('Failed to fetch stock details');
        console.error('Details error:', err);
      } finally {
        setIsLoading(false);
      }
    };

    if (symbol) {
      fetchDetails();
    }
  }, [symbol]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-6">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 text-red-500 font-semibold text-center">
        {error}
      </div>
    );
  }

  if (!details) {
    return (
      <div className="p-6 text-gray-500 font-semibold text-center">
        No details available
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      <div className="space-y-4">
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">Company Name</span>
          <span className="text-gray-900 font-semibold">{details.name}</span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">Industry</span>
          <span className="text-gray-900 font-semibold">{details.industry}</span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">Market Cap</span>
          <span className="text-gray-900 font-semibold">
            ${(details.marketCap / 1000000000).toFixed(2)}B
          </span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">Shares Outstanding</span>
          <span className="text-gray-900 font-semibold">
            {(details.sharesOutstanding / 1000000).toFixed(2)}M
          </span>
        </div>
      </div>

      <div className="space-y-4">
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">Current Price</span>
          <span className="text-gray-900 font-semibold">${details.price.toFixed(2)}</span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">Change</span>
          <span className={`font-semibold ${details.change >= 0 ? 'text-green-600' : 'text-red-600'}`}>
            {details.change >= 0 ? '+' : ''}{details.change.toFixed(2)}%
          </span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">High</span>
          <span className="text-gray-900 font-semibold">${details.high.toFixed(2)}</span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">Low</span>
          <span className="text-gray-900 font-semibold">${details.low.toFixed(2)}</span>
        </div>
      </div>

      <div className="space-y-4">
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">P/E Ratio</span>
          <span className="text-gray-900 font-semibold">
            {details.peRatio.toFixed(2)}
            <Tooltip title="Some financial metrics may be limited in the free data tier">
              <InfoCircleOutlined style={{ marginLeft: '4px', color: '#8c8c8c', fontSize: '14px' }} />
            </Tooltip>
          </span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">EPS</span>
          <span className="text-gray-900 font-semibold">${details.eps.toFixed(2)}</span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">Dividend Yield</span>
          <span className="text-gray-900 font-semibold">{details.dividendYield.toFixed(2)}%</span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">Beta</span>
          <span className="text-gray-900 font-semibold">{details.beta.toFixed(2)}</span>
        </div>
      </div>

      <div className="space-y-4">
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">Volume</span>
          <span className="text-gray-900 font-semibold">
            {(details.volume / 1000000).toFixed(2)}M
          </span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">Avg Volume</span>
          <span className="text-gray-900 font-semibold">
            {(details.avgVolume / 1000000).toFixed(2)}M
          </span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-gray-600 font-medium">Revenue (TTM)</span>
          <span className="text-gray-900 font-semibold">
            ${(details.revenue / 1000000000).toFixed(2)}B
          </span>
        </div>
      </div>
    </div>
  );
}

StockDetails.propTypes = {
  symbol: PropTypes.string.isRequired,
};

export default StockDetails; 