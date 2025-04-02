import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import axios from 'axios';
// Import Chart.js components - you'll need to install these packages
// npm install react-chartjs-2 chart.js
import { Line } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js';

// Register Chart.js components
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

function StockChart({ symbol, interval, theme = 'dark' }) {
  const [chartData, setChartData] = useState({ labels: [], prices: [] });
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let isMounted = true;

    const fetchChartData = async () => {
      if (!symbol) return;

      try {
        setIsLoading(true);
        setError(null);
        const response = await axios.get(`http://localhost:8080/api/stocks/${symbol}/price-history?interval=${interval}`);
        
        // Debug logging
        console.log('Raw API response:', response);
        console.log('Response data:', response.data);

        // Check if we have the data we need
        if (!response.data) {
          console.error('No data in response');
          throw new Error('No data received from API');
        }

        if (isMounted) {
          let formattedData;
          
          // Handle array response structure
          if (Array.isArray(response.data) && response.data.length > 0) {
            // Extract timestamps and prices from the response data
            formattedData = {
              labels: response.data.map(item => {
                const date = new Date(item.timestamp);
                return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
              }),
              prices: response.data.map(item => item.price)
            };

            console.log('Formatted chart data:', formattedData);
            setChartData(formattedData);
          } else {
            console.error('Unexpected response structure:', response.data);
            throw new Error('Unexpected response structure');
          }
        }
      } catch (err) {
        console.error('Chart data error:', err);
        if (isMounted) {
          setError(err.message || 'Failed to fetch chart data');
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    fetchChartData();

    return () => {
      isMounted = false;
    };
  }, [symbol, interval]);

  const createGradient = (ctx, chartArea) => {
    const gradient = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
    if (theme === 'dark') {
      gradient.addColorStop(0, 'rgba(96, 165, 250, 0.2)');
      gradient.addColorStop(1, 'rgba(96, 165, 250, 0.02)');
    } else {
      gradient.addColorStop(0, 'rgba(59, 130, 246, 0.2)');
      gradient.addColorStop(1, 'rgba(59, 130, 246, 0)');
    }
    return gradient;
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {
      intersect: false,
      mode: 'index',
    },
    plugins: {
      legend: {
        display: false,
      },
      tooltip: {
        enabled: true,
        backgroundColor: theme === 'dark' ? '#1a1a1a' : 'rgba(255, 255, 255, 0.9)',
        titleColor: theme === 'dark' ? '#e6e6e6' : '#1f2937',
        bodyColor: theme === 'dark' ? '#e6e6e6' : '#1f2937',
        borderColor: theme === 'dark' ? '#333333' : '#e5e7eb',
        borderWidth: 1,
        padding: 8,
        cornerRadius: 6,
        titleFont: {
          size: 12,
          weight: 'bold',
        },
        bodyFont: {
          size: 11,
        },
        displayColors: false,
        callbacks: {
          title: (tooltipItems) => {
            return tooltipItems[0].label;
          },
          label: (context) => {
            return `$${context.parsed.y.toFixed(2)}`;
          },
        },
      },
    },
    scales: {
      x: {
        grid: {
          display: true,
          color: theme === 'dark' ? 'rgba(96, 165, 250, 0.1)' : '#f3f4f6',
          drawBorder: false,
        },
        ticks: {
          color: theme === 'dark' ? '#9ca3af' : '#6b7280',
          font: {
            size: 10,
          },
          maxRotation: 45,
          minRotation: 45,
          maxTicksLimit: 6,
        },
        border: {
          display: false,
        },
      },
      y: {
        position: 'right',
        grid: {
          color: theme === 'dark' ? 'rgba(96, 165, 250, 0.1)' : '#f3f4f6',
          drawBorder: false,
        },
        ticks: {
          color: theme === 'dark' ? '#9ca3af' : '#6b7280',
          font: {
            size: 10,
          },
          callback: (value) => `$${value.toFixed(2)}`,
          padding: 8,
        },
        border: {
          display: false,
        },
      },
    },
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="animate-spin rounded-full h-8 w-8 border-2 border-blue-500 border-t-transparent"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-full">
        <p className="text-red-400">{error}</p>
      </div>
    );
  }

  const data = {
    labels: chartData.labels,
    datasets: [
      {
        label: 'Price',
        data: chartData.prices,
        borderColor: '#60a5fa',
        backgroundColor: function(context) {
          const chart = context.chart;
          const {ctx, chartArea} = chart;
          if (!chartArea) {
            return null;
          }
          return createGradient(ctx, chartArea);
        },
        borderWidth: 2.5,
        tension: 0.4,
        fill: true,
        pointRadius: 1,
        pointHoverRadius: 6,
        pointBackgroundColor: '#60a5fa',
        pointBorderColor: 'transparent',
        pointHoverBackgroundColor: '#60a5fa',
        pointHoverBorderColor: '#ffffff',
        pointHoverBorderWidth: 2,
      }
    ]
  };

  return (
    <div className="h-full p-2">
      <div className="h-full bg-[#262626] rounded-lg p-4">
        <Line options={options} data={data} />
      </div>
      <div className="text-xs text-gray-400 text-center mt-1">
        <span className="italic">Data current as of {new Date().toLocaleDateString()}</span>
      </div>
    </div>
  );
}

StockChart.propTypes = {
  symbol: PropTypes.string.isRequired,
  interval: PropTypes.string.isRequired,
  theme: PropTypes.oneOf(['light', 'dark'])
};

export default StockChart;