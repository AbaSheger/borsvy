import React, { useState, useEffect, useRef } from 'react';
import PropTypes from 'prop-types';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  ArcElement,
  Filler,
  RadialLinearScale
} from 'chart.js';
import { Line, Doughnut } from 'react-chartjs-2';

// Register Chart.js components
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  ArcElement,
  Filler,
  RadialLinearScale
);

const AnalysisVisualization = ({ analysis, technicalData }) => {
  const [chartData, setChartData] = useState(null);
  const [sentimentData, setSentimentData] = useState(null);
  const [newsSentimentData, setNewsSentimentData] = useState(null);
  const [chartError, setChartError] = useState(null);
  const technicalChartRef = useRef(null);
  const sentimentChartRef = useRef(null);
  const newsSentimentChartRef = useRef(null);

  // Debug logging
  useEffect(() => {
    console.log('Analysis data received:', analysis);
    console.log('Technical data received:', technicalData);
  }, [analysis, technicalData]);

  // Cleanup charts on unmount or when analysis changes
  useEffect(() => {
    const cleanup = () => {
      if (technicalChartRef.current?.chartInstance) {
        technicalChartRef.current.chartInstance.destroy();
      }
      if (sentimentChartRef.current?.chartInstance) {
        sentimentChartRef.current.chartInstance.destroy();
      }
      if (newsSentimentChartRef.current?.chartInstance) {
        newsSentimentChartRef.current.chartInstance.destroy();
      }
    };

    // Only cleanup on unmount
    return cleanup;
  }, []); // Remove analysis dependency

  // Handle chart updates when data changes
  useEffect(() => {
    if (analysis) {
      try {
        // Technical Analysis Chart Data
        const technicalChartData = {
          labels: ['Price', 'SMA20', 'SMA50'],
          datasets: [
            {
              label: 'Technical Indicators',
              data: [
                analysis.technical?.price || 0,
                analysis.technical?.sma20 || 0,
                analysis.technical?.sma50 || 0
              ],
              borderColor: 'rgb(75, 192, 192)',
              tension: 0.1
            }
          ]
        };

        // Sentiment Gauge Data
        const sentimentValue = analysis.llm?.sentiment === 'positive' ? 1 : 
                             analysis.llm?.sentiment === 'negative' ? -1 : 0;
        const sentimentGaugeData = {
          labels: ['Positive', 'Neutral', 'Negative'],
          datasets: [{
            data: [
              sentimentValue === 1 ? 1 : 0,
              sentimentValue === 0 ? 1 : 0,
              sentimentValue === -1 ? 1 : 0
            ],
            backgroundColor: [
              'rgba(75, 192, 192, 0.5)',
              'rgba(255, 206, 86, 0.5)',
              'rgba(255, 99, 132, 0.5)'
            ],
            borderColor: [
              'rgba(75, 192, 192, 1)',
              'rgba(255, 206, 86, 1)',
              'rgba(255, 99, 132, 1)'
            ],
            borderWidth: 1
          }]
        };

        // News Sentiment Distribution Data
        const newsSentimentDistribution = {
          labels: ['Positive', 'Neutral', 'Negative'],
          datasets: [{
            data: [
              analysis.newsSentiment?.positiveCount || 0,
              analysis.newsSentiment?.neutralCount || 0,
              analysis.newsSentiment?.negativeCount || 0
            ],
            backgroundColor: [
              'rgba(75, 192, 192, 0.5)',
              'rgba(255, 206, 86, 0.5)',
              'rgba(255, 99, 132, 0.5)'
            ],
            borderColor: [
              'rgba(75, 192, 192, 1)',
              'rgba(255, 206, 86, 1)',
              'rgba(255, 99, 132, 1)'
            ],
            borderWidth: 1
          }]
        };

        console.log('Chart data prepared:', {
          technical: technicalChartData,
          sentiment: sentimentGaugeData,
          newsSentiment: newsSentimentDistribution
        });

        setChartData(technicalChartData);
        setSentimentData(sentimentGaugeData);
        setNewsSentimentData(newsSentimentDistribution);
        setChartError(null);
      } catch (error) {
        console.error('Error preparing chart data:', error);
        setChartError('Failed to prepare chart data');
      }
    }
  }, [analysis, technicalData]);

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'top',
        labels: {
          color: 'white',
          font: {
            size: 14,
            weight: 'bold'
          }
        }
      },
      title: {
        display: true,
        text: 'Technical Analysis',
        color: 'white',
        font: {
          size: 16,
          weight: 'bold'
        }
      }
    },
    scales: {
      y: {
        beginAtZero: true,
        ticks: {
          color: 'white',
          font: {
            size: 12,
            weight: 'bold'
          }
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        }
      },
      x: {
        ticks: {
          color: 'white',
          font: {
            size: 12,
            weight: 'bold'
          }
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        }
      }
    }
  };

  const sentimentOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'top',
        labels: {
          color: 'white',
          font: {
            size: 14,
            weight: 'bold'
          }
        }
      },
      title: {
        display: true,
        text: 'Market Sentiment',
        color: 'white',
        font: {
          size: 16,
          weight: 'bold'
        }
      }
    },
    scales: {
      r: {
        beginAtZero: true,
        ticks: {
          display: false
        },
        grid: {
          display: false
        },
        angleLines: {
          display: false
        },
        pointLabels: {
          display: false
        }
      }
    }
  };

  const newsSentimentOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'top',
        labels: {
          color: 'white',
          font: {
            size: 14,
            weight: 'bold'
          }
        }
      },
      title: {
        display: true,
        text: 'News Sentiment Distribution',
        color: 'white',
        font: {
          size: 16,
          weight: 'bold'
        }
      }
    },
    scales: {
      r: {
        beginAtZero: true,
        ticks: {
          display: false
        },
        grid: {
          display: false
        },
        angleLines: {
          display: false
        },
        pointLabels: {
          display: false
        }
      }
    }
  };

  if (!analysis) {
    return (
      <div className="text-center text-gray-400 py-8">
        No analysis data available
      </div>
    );
  }

  if (chartError) {
    return (
      <div className="text-center text-red-400 py-8">
        {chartError}
      </div>
    );
  }

  return (
    <div className="analysis-visualization">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 sm:gap-6">
        {/* Technical Analysis Section */}
        <div className="bg-gray-800 p-4 sm:p-5 rounded-2xl shadow-md">
          <h3 className="text-lg sm:text-xl font-semibold mb-4 sm:mb-5 text-white">Technical Analysis</h3>
          <div className="h-[250px] sm:h-[300px]">
            {chartData && <Line data={chartData} options={chartOptions} ref={technicalChartRef} />}
          </div>
          {analysis.technical?.signals && (
            <div className="mt-4 sm:mt-5">
              <h4 className="text-base sm:text-lg font-medium mb-3 text-white">Technical Signals</h4>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {Object.entries(analysis.technical.signals).map(([key, value]) => (
                  <div key={key} className="bg-gray-700 p-3 rounded-xl">
                    <span className="font-medium text-white">{key}:</span>{' '}
                    <span className={`${
                      value.toLowerCase().includes('bullish') ? 'text-green-500 font-bold' :
                      value.toLowerCase().includes('bearish') ? 'text-red-500 font-bold' :
                      'text-white'
                    }`}>{value}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Sentiment Analysis Section */}
        <div className="bg-gray-800 p-4 sm:p-5 rounded-2xl shadow-md">
          <h3 className="text-lg sm:text-xl font-semibold mb-4 sm:mb-5 text-white">Sentiment Analysis</h3>
          <div className="grid grid-cols-1 gap-4 sm:gap-5">
            <div className="h-[250px] sm:h-[300px]">
              {sentimentData && (
                <Doughnut 
                  data={sentimentData} 
                  options={sentimentOptions} 
                  ref={sentimentChartRef}
                />
              )}
            </div>
            {analysis.llm && (
              <div className="mt-3 sm:mt-4">
                <h4 className="text-base sm:text-lg font-medium mb-3 text-white">✨ AI Analysis</h4>
                <div className="bg-gray-700 p-4 rounded-xl">
                  <p className="mb-2 text-white">
                    <span className="font-medium">Sentiment:</span>{' '}
                    <span className={`${
                      analysis.llm.sentiment === 'positive' ? 'text-green-500 font-bold' :
                      analysis.llm.sentiment === 'negative' ? 'text-red-500 font-bold' :
                      'text-yellow-500 font-bold'
                    }`}>
                      {analysis.llm.sentiment.charAt(0).toUpperCase() + analysis.llm.sentiment.slice(1)}
                    </span>
                  </p>
                  <p className="text-white">
                    <span className="font-medium">Confidence:</span>{' '}
                    {(analysis.llm.confidence * 100).toFixed(1)}%
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Fundamental Analysis Section */}
        <div className="bg-gray-800 p-4 sm:p-5 rounded-2xl shadow-md">
          <h3 className="text-lg sm:text-xl font-semibold mb-4 sm:mb-5 text-white">Fundamental Analysis</h3>
          {analysis.fundamental?.signals && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {Object.entries(analysis.fundamental.signals).map(([key, value]) => (
                <div key={key} className="bg-gray-700 p-4 rounded-xl">
                  <h4 className="font-medium mb-2 text-white">{key}</h4>
                  <p className="text-white">{value}</p>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* News Sentiment Section */}
        <div className="bg-gray-800 p-4 sm:p-5 rounded-2xl shadow-md">
          <h3 className="text-lg sm:text-xl font-semibold mb-4 sm:mb-5 text-white">News Sentiment</h3>
          <div className="h-[250px] sm:h-[300px]">
            {newsSentimentData && <Doughnut data={newsSentimentData} options={newsSentimentOptions} ref={newsSentimentChartRef} />}
          </div>
          {analysis.newsSentiment && (
            <div className="mt-4 sm:mt-5">
              <div className="grid grid-cols-3 gap-3">
                <div className="bg-gray-700 p-3 rounded-xl text-center">
                  <span className="text-green-500 font-bold">Positive</span>
                  <p className="text-white mt-1">{analysis.newsSentiment.positiveCount}</p>
                </div>
                <div className="bg-gray-700 p-3 rounded-xl text-center">
                  <span className="text-yellow-500 font-bold">Neutral</span>
                  <p className="text-white mt-1">{analysis.newsSentiment.neutralCount}</p>
                </div>
                <div className="bg-gray-700 p-3 rounded-xl text-center">
                  <span className="text-red-500 font-bold">Negative</span>
                  <p className="text-white mt-1">{analysis.newsSentiment.negativeCount}</p>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Overall Summary Section */}
      {analysis.summary && (
        <div className="mt-6 sm:mt-8 bg-gray-800 p-5 sm:p-6 rounded-2xl shadow-md">
          <h3 className="text-lg sm:text-xl font-semibold mb-4 sm:mb-5 text-white">✨ Overall Analysis</h3>
          <div className="bg-gray-700 p-5 sm:p-6 rounded-xl whitespace-pre-line">
            <p className="text-base sm:text-lg text-white leading-relaxed">
              {analysis.summary}
            </p>
          </div>
        </div>
      )}
    </div>
  );
};

AnalysisVisualization.propTypes = {
  analysis: PropTypes.shape({
    technical: PropTypes.shape({
      price: PropTypes.number,
      sma20: PropTypes.number,
      sma50: PropTypes.number,
      signals: PropTypes.object
    }),
    fundamental: PropTypes.shape({
      signals: PropTypes.object
    }),
    llm: PropTypes.shape({
      sentiment: PropTypes.string,
      confidence: PropTypes.number
    }),
    newsSentiment: PropTypes.shape({
      positiveCount: PropTypes.number,
      neutralCount: PropTypes.number,
      negativeCount: PropTypes.number
    }),
    summary: PropTypes.string
  }),
  technicalData: PropTypes.object
};

export default AnalysisVisualization;