import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import axios from 'axios';
import { InfoCircleOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';

const formatMarketCap = (value) => {
  // Convert to number and handle invalid values
  const numValue = Number(value);
  if (isNaN(numValue) || numValue === 0) return 'N/A';
  
  if (numValue >= 1e12) {
    return `$${(numValue / 1e12).toFixed(2)}T`;
  } else if (numValue >= 1e9) {
    return `$${(numValue / 1e9).toFixed(2)}B`;
  } else if (numValue >= 1e6) {
    return `$${(numValue / 1e6).toFixed(2)}M`;
  } else if (numValue >= 1e3) {
    return `$${(numValue / 1e3).toFixed(2)}K`;
  } else {
    return `$${numValue.toFixed(2)}`;
  }
};

function PeerComparison({ symbol }) {
  const [peerData, setPeerData] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let isMounted = true;

    const fetchPeerData = async () => {
      if (!symbol) return;

      try {
        setIsLoading(true);
        setError(null);
        const response = await axios.get(`http://localhost:8080/api/stocks/${symbol}/peers`);
        if (isMounted && response.data) {
          setPeerData(response.data);
        }
      } catch (err) {
        console.error('Peer comparison error:', err);
        if (isMounted) {
          if (err.response?.status === 404) {
            setError('Peer comparison data is not available for this stock.');
          } else {
            setError('Failed to fetch peer comparison data');
          }
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    fetchPeerData();

    return () => {
      isMounted = false;
    };
  }, [symbol]);

  if (isLoading) {
    return (
      <div className="bg-white p-6 rounded-lg shadow-sm">
        <h2 className="text-xl font-bold text-gray-900 mb-6 flex items-center">
          <span className="mr-2 text-blue-500">🏢</span>
          Industry Comparison
        </h2>
        <div className="animate-pulse space-y-4">
          <div className="h-24 bg-gray-100 rounded-lg"></div>
          <div className="h-64 bg-gray-100 rounded-lg"></div>
          <div className="grid grid-cols-2 gap-4">
            <div className="h-20 bg-gray-100 rounded-lg"></div>
            <div className="h-20 bg-gray-100 rounded-lg"></div>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white p-6 rounded-lg shadow-sm">
        <h2 className="text-xl font-bold text-gray-900 mb-6 flex items-center">
          <span className="mr-2 text-blue-500">🏢</span>
          Industry Comparison
        </h2>
        <div className="text-gray-600 text-center py-8">
          {error}
        </div>
      </div>
    );
  }

  if (error || !peerData || !peerData.peers || peerData.peers.length === 0) {
    return (
      <div className="bg-[#1a1a1a] p-6 rounded-xl border border-[#333333]">
        <h2 className="text-xl font-bold text-[#e6e6e6] mb-4 flex items-center">
          <span className="mr-2 text-blue-400">🏢</span>
          Industry Comparison
        </h2>
        <div className="bg-[#262626] p-6 rounded-lg border border-[#333333]">
          <p className="text-[#e6e6e6] font-medium text-center">
            {error || "No industry peers data available for this stock."}
          </p>
          <p className="text-gray-400 text-sm text-center mt-2">
            This may be because the stock is in a unique industry or we don't have enough data.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="bg-[#1a1a1a] p-6 rounded-xl border border-[#333333]">
        <h2 className="text-xl font-bold text-[#e6e6e6] mb-4 flex items-center">
          <span className="mr-2 text-blue-400">🏢</span>
          Industry Peers
        </h2>
        
        <div className="overflow-x-auto">
          <table className="min-w-full">
            <thead className="bg-[#262626] border-b border-[#333333]">
              <tr>
                <th className="px-3 sm:px-6 py-3 text-left text-xs sm:text-sm font-semibold text-gray-400">Company</th>
                <th className="px-3 sm:px-6 py-3 text-right text-xs sm:text-sm font-semibold text-gray-400">Price</th>
                <th className="px-3 sm:px-6 py-3 text-right text-xs sm:text-sm font-semibold text-gray-400">Change</th>
                <th className="px-3 sm:px-6 py-3 text-right text-xs sm:text-sm font-semibold text-gray-400">P/E Ratio</th>
                <th className="px-3 sm:px-6 py-3 text-right text-xs sm:text-sm font-semibold text-gray-400">Market Cap</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#333333]">
              {peerData.peers.map((peer) => (
                <tr key={peer.symbol} className="hover:bg-[#262626]">
                  <td className="px-3 sm:px-6 py-3 text-xs sm:text-sm text-[#e6e6e6]">{peer.name}</td>
                  <td className="px-3 sm:px-6 py-3 text-xs sm:text-sm text-right text-[#e6e6e6]">
                    ${(peer.price || 0).toFixed(2)}
                  </td>
                  <td className={`px-3 sm:px-6 py-3 text-xs sm:text-sm text-right ${
                    (peer.change || 0) >= 0 ? 'text-green-400' : 'text-red-400'
                  }`}>
                    {(peer.change || 0) >= 0 ? '+' : ''}{(peer.change || 0).toFixed(2)}%
                  </td>
                  <td className="px-3 sm:px-6 py-3 text-xs sm:text-sm text-right text-[#e6e6e6]">
                    {peer.peRatio ? peer.peRatio.toFixed(2) : 'N/A'}
                  </td>
                  <td className="px-3 sm:px-6 py-3 text-xs sm:text-sm text-right text-[#e6e6e6]">
                    {formatMarketCap(peer.marketCap || 0)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 sm:gap-6 mt-4 sm:mt-6">
          <div className="rounded-lg overflow-hidden shadow-sm border border-[#333333]">
            <div className="bg-[#262626] px-3 sm:px-4 py-2 border-b border-[#333333]">
              <h3 className="text-blue-400 text-sm sm:text-base font-semibold">Industry Average P/E</h3>
            </div>
            <div className="p-3 sm:p-4 bg-[#1a1a1a]">
              <div className="text-xl sm:text-2xl font-bold text-[#e6e6e6]">{peerData.avgPE?.toFixed(2) || 'N/A'}</div>
              <div className="text-xs sm:text-sm text-gray-400 mt-1">
                {peerData.currentPE && peerData.avgPE ? (
                  peerData.currentPE > peerData.avgPE ? 
                    `Above industry average by ${((peerData.currentPE / peerData.avgPE - 1) * 100).toFixed(1)}%` : 
                    `Below industry average by ${((1 - peerData.currentPE / peerData.avgPE) * 100).toFixed(1)}%`
                ) : (
                  'Comparison not available'
                )}
              </div>
            </div>
          </div>
          
          <div className={`rounded-lg overflow-hidden shadow-sm border ${
            Number(peerData.relativeStrength) >= 0 
              ? 'border-green-500/20' 
              : 'border-red-500/20'
          }`}>
            <div className={`px-4 py-2 border-b ${
              Number(peerData.relativeStrength) >= 0 
                ? 'bg-green-500/10 border-green-500/20' 
                : 'bg-red-500/10 border-red-500/20'
            }`}>
              <h3 className={`font-semibold ${
                Number(peerData.relativeStrength) >= 0 
                  ? 'text-green-400' 
                  : 'text-red-400'
              }`}>
                Relative Performance
              </h3>
            </div>
            <div className="p-4 bg-[#1a1a1a]">
              <div className={`text-2xl font-bold ${
                Number(peerData.relativeStrength) >= 0 
                  ? 'text-green-400' 
                  : 'text-red-400'
              }`}>
                {peerData.relativeStrength !== null ? (
                  <>
                    {Number(peerData.relativeStrength) >= 0 ? '+' : ''}
                    {peerData.relativeStrength.toFixed(2)}%
                  </>
                ) : 'N/A'}
              </div>
              <div className="text-sm text-gray-400 mt-1">
                {peerData.relativeStrength !== null ? (
                  Number(peerData.relativeStrength) >= 0 ?
                    'Outperforming industry peers' :
                    'Underperforming industry peers'
                ) : 'Performance comparison not available'}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

PeerComparison.propTypes = {
  symbol: PropTypes.string
};

export default PeerComparison;