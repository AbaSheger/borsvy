import { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { Spin } from 'antd';
import axios from 'axios';
import { axiosConfig } from '../config';
import { createChart, CandlestickSeries, HistogramSeries, LineSeries } from 'lightweight-charts';

const StockChart = ({ symbol: propSymbol, interval: propInterval = '1D' }) => {
  const { symbol: routeSymbol } = useParams();
  const symbol = propSymbol || routeSymbol;
  const [priceData, setPriceData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const containerRef = useRef(null);
  const chartRef = useRef(null);

  useEffect(() => {
    const fetchPriceData = async () => {
      if (!symbol || symbol === 'undefined') {
        setError('Invalid symbol');
        setLoading(false);
        return;
      }
      try {
        setLoading(true);
        const response = await axios.get(
          `/api/analysis/${symbol}/price-history?interval=${propInterval}`,
          axiosConfig
        );
        if (response.data && Array.isArray(response.data)) {
          setPriceData(response.data.filter(p => p && p.price != null && p.timestamp));
        } else {
          setError('Invalid price data format');
        }
      } catch (err) {
        console.error('Error fetching price data:', err);
        setError('Failed to fetch price data');
      } finally {
        setLoading(false);
      }
    };
    fetchPriceData();
  }, [symbol, propInterval]);

  useEffect(() => {
    if (!containerRef.current || !priceData || priceData.length === 0) return;

    // Destroy previous chart instance
    if (chartRef.current) {
      chartRef.current.remove();
      chartRef.current = null;
    }

    const chart = createChart(containerRef.current, {
      layout: {
        background: { color: '#1a1a1a' },
        textColor: '#d1d4dc',
      },
      grid: {
        vertLines: { color: '#2B2B43' },
        horzLines: { color: '#2B2B43' },
      },
      crosshair: { mode: 1 },
      rightPriceScale: { borderColor: '#485c7b' },
      timeScale: {
        borderColor: '#485c7b',
        timeVisible: true,
        secondsVisible: false,
      },
      width: containerRef.current.clientWidth || 600,
      height: containerRef.current.clientHeight || 400,
    });
    chartRef.current = chart;

    // Convert timestamp strings to Unix seconds for lightweight-charts
    const toTime = (ts) => {
      const d = new Date(ts);
      return Math.floor(d.getTime() / 1000);
    };

    // Determine if data has meaningful OHLC (open != close for most bars)
    const hasOhlc = priceData.some(p => p.open && p.open !== p.price);

    // Candlestick series (main pane)
    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#26a69a',
      downColor: '#ef5350',
      borderVisible: false,
      wickUpColor: '#26a69a',
      wickDownColor: '#ef5350',
    });

    const candleData = priceData.map(p => ({
      time: toTime(p.timestamp),
      open: p.open || p.price,
      high: p.high || p.price,
      low: p.low || p.price,
      close: p.price,
    }));
    candleSeries.setData(candleData);

    // SMA calculation helper
    const calcSMA = (data, period) => {
      const result = [];
      for (let i = period - 1; i < data.length; i++) {
        const sum = data.slice(i - period + 1, i + 1).reduce((acc, p) => acc + p.price, 0);
        result.push({ time: toTime(data[i].timestamp), value: sum / period });
      }
      return result;
    };

    // SMA20 overlay
    if (priceData.length >= 20) {
      const sma20Series = chart.addSeries(LineSeries, {
        color: '#3b82f6',
        lineWidth: 1,
        title: 'SMA20',
        priceLineVisible: false,
        lastValueVisible: false,
      });
      sma20Series.setData(calcSMA(priceData, 20));
    }

    // SMA50 overlay
    if (priceData.length >= 50) {
      const sma50Series = chart.addSeries(LineSeries, {
        color: '#f97316',
        lineWidth: 1,
        title: 'SMA50',
        priceLineVisible: false,
        lastValueVisible: false,
      });
      sma50Series.setData(calcSMA(priceData, 50));
    }

    // Volume histogram (separate pane at index 1)
    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: 'volume' },
      priceScaleId: 'volume',
    }, 1);
    volumeSeries.priceScale().applyOptions({
      scaleMargins: { top: 0.7, bottom: 0 },
    });

    const volumeData = priceData.map(p => ({
      time: toTime(p.timestamp),
      value: p.volume || 0,
      color: p.price >= (p.open || p.price) ? 'rgba(38,166,154,0.5)' : 'rgba(239,83,80,0.5)',
    }));
    volumeSeries.setData(volumeData);

    chart.timeScale().fitContent();

    // Resize handler
    const handleResize = () => {
      if (containerRef.current && chartRef.current) {
        chartRef.current.applyOptions({
          width: containerRef.current.clientWidth,
          height: containerRef.current.clientHeight,
        });
      }
    };
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      if (chartRef.current) {
        chartRef.current.remove();
        chartRef.current = null;
      }
    };
  }, [priceData]);

  if (loading) return (
    <div className="flex items-center justify-center w-full h-full">
      <Spin size="large" />
    </div>
  );

  if (error) return (
    <div className="text-red-500 text-center p-4">{error}</div>
  );

  if (!priceData || priceData.length === 0) return (
    <div className="text-gray-400 text-center p-4">No price data available</div>
  );

  return <div ref={containerRef} className="w-full" style={{ height: '400px' }} />;
};

export default StockChart;
