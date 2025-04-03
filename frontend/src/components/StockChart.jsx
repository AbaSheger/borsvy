import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Card, Select, Spin, message } from 'antd';
import { Line } from 'react-chartjs-2';
import axios from 'axios';
import { axiosConfig } from '../config';
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

const { Option } = Select;

const StockChart = () => {
  const { symbol } = useParams();
  const [priceData, setPriceData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [interval, setInterval] = useState('1d');

  useEffect(() => {
    const fetchPriceData = async () => {
      try {
        setLoading(true);
        const response = await axios.get(`/api/stocks/${symbol}/price-history?interval=${interval}`, axiosConfig);
        setPriceData(response.data);
      } catch (error) {
        message.error('Failed to fetch price data');
        console.error('Error fetching price data:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchPriceData();
  }, [symbol, interval]);

  if (loading) {
    return <Spin size="large" />;
  }

  if (!priceData) {
    return <div>No price data available</div>;
  }

  const chartData = {
    labels: priceData.map(point => new Date(point.timestamp).toLocaleDateString()),
    datasets: [
      {
        label: 'Price',
        data: priceData.map(point => point.price),
        borderColor: 'rgb(75, 192, 192)',
        tension: 0.1
      }
    ]
  };

  const options = {
    responsive: true,
    plugins: {
      legend: {
        position: 'top',
      },
      title: {
        display: true,
        text: `${symbol} Price History`
      }
    }
  };

  return (
    <Card>
      <Select value={interval} onChange={setInterval} style={{ width: 120, marginBottom: 16 }}>
        <Option value="1d">1 Day</Option>
        <Option value="1w">1 Week</Option>
        <Option value="1m">1 Month</Option>
        <Option value="3m">3 Months</Option>
        <Option value="1y">1 Year</Option>
      </Select>
      <Line data={chartData} options={options} />
    </Card>
  );
};

export default StockChart;