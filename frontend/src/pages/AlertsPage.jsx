import { useCallback, useEffect, useRef, useState } from 'react';
import { Badge, Button, Input, message, Popconfirm, Select, Switch } from 'antd';
import { BellOutlined, CheckCircleOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import axios from 'axios';
import { axiosConfig } from '../config';

const STORAGE_KEY = 'priceAlerts';

const requestNotificationPermission = async () => {
  if (!('Notification' in window)) {
    message.warning('Browser notifications are not supported');
    return false;
  }

  if (Notification.permission === 'granted') return true;
  return (await Notification.requestPermission()) === 'granted';
};

const AlertsTable = ({ title, icon, alerts, triggered = false, onToggle, onRemove }) => (
  <div className="saas-panel overflow-hidden">
    <div className="px-5 py-4 border-b border-slate-200 dark:border-[#263142] flex items-center justify-between">
      <h2 className="text-sm font-semibold text-slate-950 dark:text-slate-50 flex items-center gap-2">
        {icon} {title}
      </h2>
      <span className="text-xs text-slate-500 dark:text-slate-400">{alerts.length} alert{alerts.length === 1 ? '' : 's'}</span>
    </div>

    {alerts.length === 0 ? (
      <div className="p-8 text-center text-sm text-slate-500 dark:text-slate-400">No alerts in this view</div>
    ) : (
      <table className="data-table">
        <thead>
          <tr>
            <th className="text-left">Symbol</th>
            <th className="text-left">Condition</th>
            <th className="text-right">Target</th>
            {!triggered && <th className="text-center">Active</th>}
            <th className="text-right">Action</th>
          </tr>
        </thead>
        <tbody>
          {alerts.map(alert => (
            <tr key={alert.id} className={triggered ? 'opacity-70' : ''}>
              <td className="font-semibold text-slate-950 dark:text-slate-50">{alert.symbol}</td>
              <td className="text-slate-600 dark:text-slate-300">Price {alert.direction}</td>
              <td className={`text-right tabular-nums ${triggered ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-700 dark:text-slate-200'}`}>
                ${alert.targetPrice}
              </td>
              {!triggered && (
                <td className="text-center">
                  <Switch checked={alert.active} onChange={() => onToggle(alert.id)} size="small" />
                </td>
              )}
              <td className="text-right">
                <Popconfirm title="Delete alert?" onConfirm={() => onRemove(alert.id)} okText="Delete" cancelText="Cancel">
                  <button className="text-slate-400 hover:text-rose-500 transition-colors">
                    <DeleteOutlined />
                  </button>
                </Popconfirm>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    )}
  </div>
);

const AlertsPage = () => {
  const [alerts, setAlerts] = useState([]);
  const [alertsLoading, setAlertsLoading] = useState(true);
  const [form, setForm] = useState({ symbol: '', targetPrice: '', direction: 'above' });
  const intervalRef = useRef(null);

  const loadAlerts = useCallback(async () => {
    try {
      setAlertsLoading(true);
      const response = await axios.get('/api/alerts', axiosConfig);
      const serverAlerts = response.data || [];
      setAlerts(serverAlerts);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(serverAlerts));
    } catch {
      try {
        setAlerts(JSON.parse(localStorage.getItem(STORAGE_KEY)) || []);
      } catch {
        setAlerts([]);
      }
      message.warning('Using locally cached alerts because the server is unavailable');
    } finally {
      setAlertsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAlerts();
  }, [loadAlerts]);

  const addAlert = async (alert) => {
    const response = await axios.post('/api/alerts', alert, axiosConfig);
    setAlerts(prev => [response.data, ...prev]);
  };

  const removeAlert = async (id) => {
    await axios.delete(`/api/alerts/${id}`, axiosConfig);
    setAlerts(prev => prev.filter(alert => alert.id !== id));
  };

  const toggleAlert = async (id) => {
    const response = await axios.patch(`/api/alerts/${id}/toggle`, {}, axiosConfig);
    setAlerts(prev => prev.map(alert => alert.id === id ? response.data : alert));
  };

  const markTriggered = useCallback(async (id) => {
    const response = await axios.patch(`/api/alerts/${id}/triggered`, {}, axiosConfig);
    setAlerts(prev => prev.map(alert => alert.id === id ? response.data : alert));
  }, []);

  const checkAlerts = useCallback(async () => {
    const active = alerts.filter(alert => alert.active && !alert.triggered);
    if (!active.length) return;

    const symbols = [...new Set(active.map(alert => alert.symbol))];
    const prices = {};

    await Promise.all(symbols.map(async symbol => {
      try {
        const response = await axios.get(`/api/stocks/${symbol}`, axiosConfig);
        prices[symbol] = response.data?.price;
      } catch {
        prices[symbol] = null;
      }
    }));

    for (const alert of active) {
      const price = prices[alert.symbol];
      if (price == null) continue;

      const isTriggered = alert.direction === 'above' ? price >= alert.targetPrice : price <= alert.targetPrice;
      if (!isTriggered) continue;

      await markTriggered(alert.id);
      if (Notification.permission === 'granted') {
        new Notification('BorsVy Price Alert', {
          body: `${alert.symbol} is ${alert.direction} $${alert.targetPrice} (current: $${price.toFixed(2)})`,
          icon: '/favicon.ico',
        });
      }
      message.success(`Alert triggered: ${alert.symbol} ${alert.direction} $${alert.targetPrice}`);
    }
  }, [alerts, markTriggered]);

  useEffect(() => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    intervalRef.current = setInterval(checkAlerts, 60000);
    return () => clearInterval(intervalRef.current);
  }, [checkAlerts]);

  const handleAdd = async () => {
    if (!form.symbol || !form.targetPrice) {
      message.warning('Symbol and target price are required');
      return;
    }

    const targetPrice = parseFloat(form.targetPrice);
    if (Number.isNaN(targetPrice) || targetPrice <= 0) {
      message.warning('Target price must be a positive number');
      return;
    }

    const granted = await requestNotificationPermission();
    if (!granted) message.warning('Notifications blocked. Alerts will still appear in this tab.');

    try {
      await addAlert({ symbol: form.symbol.toUpperCase(), targetPrice, direction: form.direction });
      setForm({ symbol: '', targetPrice: '', direction: 'above' });
      message.success('Alert added');
    } catch {
      message.error('Failed to add alert');
    }
  };

  const activeAlerts = alerts.filter(alert => !alert.triggered);
  const triggeredAlerts = alerts.filter(alert => alert.triggered);
  const activeCount = activeAlerts.filter(alert => alert.active).length;

  return (
    <div className="page-wrap max-w-5xl space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="page-kicker">Alerts</p>
          <h1 className="page-title mt-1 flex items-center gap-2">
            Price alerts
            {activeCount > 0 && <Badge count={activeCount} color="blue" />}
          </h1>
          <p className="page-subtitle mt-1">Conditions are checked every 60 seconds while the page is open.</p>
        </div>
      </div>

      <div className="saas-panel p-5">
        <div className="flex items-center justify-between gap-3 mb-4">
          <div>
            <h2 className="text-sm font-semibold text-slate-950 dark:text-slate-50">New alert</h2>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">Create a browser notification for a target price.</p>
          </div>
          <Button type="primary" onClick={handleAdd} icon={<PlusOutlined />}>Add alert</Button>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-[160px_160px_1fr] gap-3">
          <Input placeholder="Symbol" value={form.symbol} onChange={e => setForm({ ...form, symbol: e.target.value.toUpperCase() })} />
          <Select
            value={form.direction}
            onChange={direction => setForm({ ...form, direction })}
            options={[
              { value: 'above', label: 'Goes above' },
              { value: 'below', label: 'Goes below' },
            ]}
          />
          <Input type="number" placeholder="Target price" value={form.targetPrice} onChange={e => setForm({ ...form, targetPrice: e.target.value })} />
        </div>
      </div>

      {alertsLoading ? (
        <div className="saas-panel p-8 text-center text-sm text-slate-500 dark:text-slate-400">Loading alerts...</div>
      ) : (
        <AlertsTable
          title="Active alerts"
          icon={<BellOutlined className="text-blue-500" />}
          alerts={activeAlerts}
          onToggle={toggleAlert}
          onRemove={removeAlert}
        />
      )}

      {triggeredAlerts.length > 0 && (
        <AlertsTable
          title="Triggered alerts"
          icon={<CheckCircleOutlined className="text-emerald-500" />}
          alerts={triggeredAlerts}
          triggered
          onRemove={removeAlert}
        />
      )}
    </div>
  );
};

export default AlertsPage;
