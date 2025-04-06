import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Card, Spin, message } from 'antd';
import { Line } from 'react-chartjs-2';
import axios from 'axios';
import { axiosConfig } from '../config';
import { useTheme } from '../context/ThemeContext';
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

const StockChart = ({ symbol: propSymbol, interval: propInterval = '1D' }) => {
  const { symbol: routeSymbol } = useParams();
  const symbol = propSymbol || routeSymbol;
  const [priceData, setPriceData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { theme } = useTheme();

  useEffect(() => {
    const fetchPriceData = async () => {
      if (!symbol || symbol === 'undefined') {
        setError('Invalid symbol');
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        const response = await axios.get(`/api/analysis/${symbol}/price-history?interval=${propInterval}`, axiosConfig);
        
        if (response.data && Array.isArray(response.data)) {
          setPriceData(response.data.filter(point => point && point.price && point.timestamp));
        } else {
          setError('Invalid price data format');
        }
      } catch (error) {
        console.error('Error fetching price data:', error);
        setError('Failed to fetch price data');
      } finally {
        setLoading(false);
      }
    };

    fetchPriceData();
  }, [symbol, propInterval]);

  if (loading) {
    return <Spin size="large" />;
  }

  if (error) {
    return <div className="text-red-500 text-center p-4">{error}</div>;
  }

  if (!priceData || priceData.length === 0) {
    return <div className="text-gray-400 text-center p-4">No price data available</div>;
  }

  const chartData = {
    labels: priceData.map(point => new Date(point.timestamp).toLocaleDateString()),
    datasets: [
      {
        label: 'Price',
        data: priceData.map(point => point.price),
        borderColor: theme === 'dark' ? 'rgb(75, 192, 192)' : 'rgb(53, 162, 235)',
        tension: 0.1,
        fill: true,
        backgroundColor: theme === 'dark' 
          ? 'rgba(75, 192, 192, 0.1)' 
          : 'rgba(53, 162, 235, 0.1)'
      }
    ]
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'top',
        labels: {
          color: theme === 'dark' ? '#e6e6e6' : '#1a1a1a'
        }
      },
      title: {
        display: true,
        text: `${symbol} Price History`,
        color: theme === 'dark' ? '#e6e6e6' : '#1a1a1a'
      }
    },
    scales: {
      y: {
        beginAtZero: false,
        grid: {
          color: theme === 'dark' ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)'
        },
        ticks: {
          color: theme === 'dark' ? '#e6e6e6' : '#1a1a1a'
        }
      },
      x: {
        grid: {
          color: theme === 'dark' ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)'
        },
        ticks: {
          color: theme === 'dark' ? '#e6e6e6' : '#1a1a1a'
        }
      }
    }
  };

  return (
    <div className="w-full h-full">
      <Line data={chartData} options={options} />
    </div>
  );
};

export default StockChart;