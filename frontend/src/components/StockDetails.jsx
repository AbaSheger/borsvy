import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import axios from 'axios';
import { InfoCircleOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import { useTheme } from '../context/ThemeContext';

function StockDetails({ symbol }) {
  const [details, setDetails] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const { theme } = useTheme();

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
        <div className="animate-spin rounded-full h-10 w-10 border-4 border-blue-500 border-t-transparent"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 text-red-500 font-semibold text-center rounded-xl bg-red-500/10 border border-red-500/20">
        {error}
      </div>
    );
  }

  if (!details) {
    return (
      <div className="p-6 text-gray-500 font-semibold text-center rounded-xl bg-gray-500/10 border border-gray-500/20">
        No details available
      </div>
    );
  }

  // Group data for better organization
  const priceMetrics = [
    { label: 'Current Price', value: `$${details.price.toFixed(2)}` },
    { label: 'Change', value: `${details.change >= 0 ? '+' : ''}${details.change.toFixed(2)}%`, 
      className: details.change >= 0 ? 'text-green-600' : 'text-red-600' },
    { label: 'High', value: `$${details.high.toFixed(2)}` },
    { label: 'Low', value: `$${details.low.toFixed(2)}` },
  ];

  const companyMetrics = [
    { label: 'Company Name', value: details.name },
    { label: 'Industry', value: details.industry },
    { label: 'Market Cap', value: `$${(details.marketCap / 1000000000).toFixed(2)}B` },
    { label: 'Shares Outstanding', value: `${(details.sharesOutstanding / 1000000).toFixed(2)}M` },
  ];

  const financialMetrics = [
    { label: 'P/E Ratio', value: details.peRatio.toFixed(2), hasInfo: true },
    { label: 'EPS', value: `$${details.eps.toFixed(2)}` },
    { label: 'Dividend Yield', value: `${details.dividendYield.toFixed(2)}%` },
    { label: 'Beta', value: details.beta.toFixed(2) },
  ];

  const volumeMetrics = [
    { label: 'Volume', value: `${(details.volume / 1000000).toFixed(2)}M` },
    { label: 'Avg Volume', value: `${(details.avgVolume / 1000000).toFixed(2)}M` },
    { label: 'Revenue (TTM)', value: `$${(details.revenue / 1000000000).toFixed(2)}B` },
  ];

  // Helper component for metrics display
  const MetricCard = ({ title, metrics }) => (
    <div className={`${theme === 'dark' ? 'bg-[#262626] border-[#333333]' : 'bg-white border-gray-200'} rounded-xl p-4 border`}>
      <h3 className={`text-lg font-semibold mb-3 ${theme === 'dark' ? 'text-[#e6e6e6]' : 'text-gray-800'}`}>{title}</h3>
      <div className="space-y-3">
        {metrics.map((metric, index) => (
          <div key={index} className="flex justify-between items-center">
            <span className={`${theme === 'dark' ? 'text-gray-400' : 'text-gray-600'} font-medium`}>{metric.label}</span>
            <span className={`font-semibold ${metric.className || (theme === 'dark' ? 'text-[#e6e6e6]' : 'text-gray-800')} flex items-center`}>
              {metric.value}
              {metric.hasInfo && (
                <Tooltip title="Some financial metrics may be limited in the free data tier">
                  <InfoCircleOutlined style={{ marginLeft: '4px', color: theme === 'dark' ? '#8c8c8c' : '#a3a3a3', fontSize: '14px' }} />
                </Tooltip>
              )}
            </span>
          </div>
        ))}
      </div>
    </div>
  );

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 sm:gap-5">
      <MetricCard title="Price Information" metrics={priceMetrics} />
      <MetricCard title="Company Information" metrics={companyMetrics} />
      <MetricCard title="Financial Metrics" metrics={financialMetrics} />
      <MetricCard title="Volume & Revenue" metrics={volumeMetrics} />
    </div>
  );
}

StockDetails.propTypes = {
  symbol: PropTypes.string.isRequired,
};

export default StockDetails;