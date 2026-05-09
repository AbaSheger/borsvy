import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Input, message, Popconfirm } from 'antd';
import { DeleteOutlined, LineChartOutlined, PlusOutlined } from '@ant-design/icons';
import axios from 'axios';
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { axiosConfig } from '../config';

const STORAGE_KEY = 'portfolioHoldings';
const COLORS = ['#2563eb', '#059669', '#d97706', '#dc2626', '#7c3aed', '#db2777', '#0891b2', '#ea580c'];

const fmt = (n) => n != null ? `$${n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '-';
const fmtPct = (n) => n != null ? `${n >= 0 ? '+' : ''}${n.toFixed(2)}%` : '-';

const AddHoldingForm = ({ onAdd }) => {
  const [form, setForm] = useState({ symbol: '', shares: '', buyPrice: '', buyDate: '' });

  const handleSubmit = async () => {
    if (!form.symbol || !form.shares || !form.buyPrice) {
      message.warning('Symbol, shares, and buy price are required');
      return;
    }

    const shares = parseFloat(form.shares);
    const buyPrice = parseFloat(form.buyPrice);

    if (Number.isNaN(shares) || shares <= 0 || Number.isNaN(buyPrice) || buyPrice <= 0) {
      message.warning('Shares and buy price must be positive numbers');
      return;
    }

    try {
      await onAdd({ symbol: form.symbol.toUpperCase(), shares, buyPrice, buyDate: form.buyDate || null });
      setForm({ symbol: '', shares: '', buyPrice: '', buyDate: '' });
    } catch {
      message.error('Failed to add holding');
    }
  };

  return (
    <div className="saas-panel p-5">
      <div className="flex items-center justify-between gap-3 mb-4">
        <div>
          <h2 className="text-sm font-semibold text-slate-950 dark:text-slate-50">Add holding</h2>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">Positions are stored locally in this browser.</p>
        </div>
        <Button type="primary" onClick={handleSubmit} icon={<PlusOutlined />}>Add</Button>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
        <Input placeholder="Symbol" value={form.symbol} onChange={e => setForm({ ...form, symbol: e.target.value.toUpperCase() })} />
        <Input type="number" placeholder="Shares" value={form.shares} onChange={e => setForm({ ...form, shares: e.target.value })} />
        <Input type="number" placeholder="Buy price" value={form.buyPrice} onChange={e => setForm({ ...form, buyPrice: e.target.value })} />
        <Input type="date" value={form.buyDate} onChange={e => setForm({ ...form, buyDate: e.target.value })} />
      </div>
    </div>
  );
};

const Metric = ({ label, value, tone = 'neutral' }) => {
  const color = tone === 'gain'
    ? 'text-emerald-600 dark:text-emerald-400'
    : tone === 'loss'
      ? 'text-rose-600 dark:text-rose-400'
      : 'text-slate-950 dark:text-slate-50';

  return (
    <div className="metric-card">
      <p className="text-xs text-slate-500 dark:text-slate-400">{label}</p>
      <p className={`mt-2 text-xl font-semibold tabular-nums ${color}`}>{value}</p>
    </div>
  );
};

const PortfolioPage = () => {
  const navigate = useNavigate();
  const [holdings, setHoldings] = useState([]);
  const [holdingsLoading, setHoldingsLoading] = useState(true);
  const [prices, setPrices] = useState({});

  const loadHoldings = useCallback(async () => {
    try {
      setHoldingsLoading(true);
      const response = await axios.get('/api/portfolio/holdings', axiosConfig);
      const serverHoldings = response.data || [];
      setHoldings(serverHoldings);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(serverHoldings));
    } catch {
      try {
        setHoldings(JSON.parse(localStorage.getItem(STORAGE_KEY)) || []);
      } catch {
        setHoldings([]);
      }
      message.warning('Using locally cached holdings because the server is unavailable');
    } finally {
      setHoldingsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadHoldings();
  }, [loadHoldings]);

  const addHolding = async (holding) => {
    const response = await axios.post('/api/portfolio/holdings', holding, axiosConfig);
    setHoldings(prev => [response.data, ...prev]);
    message.success(`${holding.symbol} added`);
  };

  const removeHolding = async (id) => {
    await axios.delete(`/api/portfolio/holdings/${id}`, axiosConfig);
    setHoldings(prev => prev.filter(holding => holding.id !== id));
  };

  const fetchPrices = useCallback(async () => {
    const symbols = [...new Set(holdings.map(holding => holding.symbol))];
    const results = {};

    await Promise.all(symbols.map(async symbol => {
      try {
        const response = await axios.get(`/api/stocks/${symbol}`, axiosConfig);
        results[symbol] = response.data?.price;
      } catch {
        results[symbol] = null;
      }
    }));

    setPrices(results);
  }, [holdings]);

  useEffect(() => {
    if (holdings.length > 0) fetchPrices();
  }, [holdings, fetchPrices]);

  useEffect(() => {
    if (holdings.length === 0) return undefined;
    const interval = setInterval(fetchPrices, 60000);
    return () => clearInterval(interval);
  }, [holdings, fetchPrices]);

  const rows = holdings.map(holding => {
    const current = prices[holding.symbol];
    const costBasis = holding.shares * holding.buyPrice;
    const currentValue = current != null ? holding.shares * current : null;
    const pnl = currentValue != null ? currentValue - costBasis : null;
    const pnlPct = pnl != null ? (pnl / costBasis) * 100 : null;
    return { ...holding, currentPrice: current, costBasis, currentValue, pnl, pnlPct };
  });

  const totalCost = rows.reduce((sum, row) => sum + row.costBasis, 0);
  const totalValue = rows.reduce((sum, row) => sum + (row.currentValue ?? row.costBasis), 0);
  const totalPnl = totalValue - totalCost;
  const totalPnlPct = totalCost > 0 ? (totalPnl / totalCost) * 100 : 0;
  const pieData = rows.filter(row => row.currentValue != null).map(row => ({ name: row.symbol, value: Number(row.currentValue.toFixed(2)) }));

  return (
    <div className="page-wrap space-y-6">
      <div>
        <p className="page-kicker">Portfolio</p>
        <h1 className="page-title mt-1">Holdings</h1>
        <p className="page-subtitle mt-1">Track value, cost basis, allocation, and unrealized return.</p>
      </div>

      <AddHoldingForm onAdd={addHolding} />

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <Metric label="Total value" value={fmt(totalValue)} />
        <Metric label="Cost basis" value={fmt(totalCost)} />
        <Metric label="Total P&L" value={fmt(totalPnl)} tone={totalPnl >= 0 ? 'gain' : 'loss'} />
        <Metric label="Return" value={fmtPct(totalPnlPct)} tone={totalPnlPct >= 0 ? 'gain' : 'loss'} />
      </div>

      {holdingsLoading ? (
        <div className="saas-panel p-10 text-center text-sm text-slate-500 dark:text-slate-400">Loading holdings...</div>
      ) : holdings.length === 0 ? (
        <div className="saas-panel p-10 text-center">
          <p className="text-sm font-medium text-slate-700 dark:text-slate-200">No holdings yet</p>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">Add your first position above to start tracking performance.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_360px] gap-5 items-start">
          <div className="saas-panel overflow-hidden">
            <table className="data-table">
              <thead>
                <tr>
                  <th className="text-left">Symbol</th>
                  <th className="text-right">Shares</th>
                  <th className="text-right">Buy price</th>
                  <th className="text-right">Current</th>
                  <th className="text-right">Value</th>
                  <th className="text-right">P&L</th>
                  <th className="text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(row => (
                  <tr key={row.id}>
                    <td>
                      <button onClick={() => navigate(`/analysis/${row.symbol}`)} className="font-semibold text-blue-600 dark:text-blue-400 hover:underline">
                        {row.symbol} <LineChartOutlined className="text-xs" />
                      </button>
                    </td>
                    <td className="text-right tabular-nums text-slate-700 dark:text-slate-200">{row.shares}</td>
                    <td className="text-right tabular-nums text-slate-700 dark:text-slate-200">{fmt(row.buyPrice)}</td>
                    <td className="text-right tabular-nums text-slate-700 dark:text-slate-200">{fmt(row.currentPrice)}</td>
                    <td className="text-right tabular-nums text-slate-700 dark:text-slate-200">{fmt(row.currentValue)}</td>
                    <td className={`text-right tabular-nums ${row.pnl == null ? 'text-slate-400' : row.pnl >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
                      <div>{fmt(row.pnl)}</div>
                      <div className="text-xs">{fmtPct(row.pnlPct)}</div>
                    </td>
                    <td className="text-right">
                      <Popconfirm title="Remove this holding?" onConfirm={() => removeHolding(row.id)} okText="Remove" cancelText="Cancel">
                        <button className="text-slate-400 hover:text-rose-500 transition-colors">
                          <DeleteOutlined />
                        </button>
                      </Popconfirm>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {pieData.length > 0 && (
            <div className="saas-panel p-5">
              <h2 className="text-sm font-semibold text-slate-950 dark:text-slate-50">Allocation</h2>
              <div className="mt-3 h-[260px]">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={pieData} cx="50%" cy="50%" outerRadius={82} dataKey="value">
                      {pieData.map((_, index) => <Cell key={index} fill={COLORS[index % COLORS.length]} />)}
                    </Pie>
                    <Tooltip formatter={(value) => fmt(value)} contentStyle={{ borderRadius: 8 }} />
                    <Legend formatter={(value) => <span className="text-xs text-slate-500">{value}</span>} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default PortfolioPage;
