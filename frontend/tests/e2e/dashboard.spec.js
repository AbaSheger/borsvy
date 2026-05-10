import { expect, test } from '@playwright/test';

const stockDetails = {
  AAPL: {
    symbol: 'AAPL',
    name: 'Apple Inc.',
    industry: 'Technology',
    price: 293.34,
    change: 5.91,
    changePercent: 2.05,
    marketCap: 4308095,
    peRatio: 31.4,
    high52Week: 301.2,
    low52Week: 182.1,
  },
  MSFT: {
    symbol: 'MSFT',
    name: 'Microsoft Corp',
    industry: 'Technology',
    price: 415.14,
    change: -5.63,
    changePercent: -1.34,
    marketCap: 3083691,
    peRatio: 28.9,
    high52Week: 468.35,
    low52Week: 365.2,
  },
  NVDA: {
    symbol: 'NVDA',
    name: 'NVIDIA Corp',
    industry: 'Semiconductors',
    price: 215.55,
    change: 4.2,
    changePercent: 1.99,
    marketCap: 5229360,
    peRatio: 45.2,
    high52Week: 230.4,
    low52Week: 88.5,
  },
  BTC: {
    symbol: 'BTC',
    name: 'Bitcoin US Dollar',
    industry: 'Cryptocurrency',
    price: 80800,
    change: 1200,
    changePercent: 1.51,
    marketCap: null,
    peRatio: null,
    high52Week: 99000,
    low52Week: 52000,
  },
};

const priceHistory = [
  { timestamp: '2026-05-08T09:30:00', open: 410, high: 418, low: 409, price: 415.14, volume: 1000 },
  { timestamp: '2026-05-08T10:00:00', open: 415, high: 419, low: 414, price: 417.12, volume: 1200 },
  { timestamp: '2026-05-08T10:30:00', open: 417, high: 420, low: 416, price: 418.24, volume: 900 },
];

const navigateBySidebar = async (page, isMobile, name) => {
  if (isMobile) {
    await page.getByRole('button', { name: /open navigation/i }).click();
  }
  await page.getByRole('link', { name }).click();
};

test.beforeEach(async ({ page }) => {
  const holdings = [];
  const alerts = [];

  await page.route('**/api/auth/me', route => route.fulfill({ status: 401, json: { error: 'Not authenticated' } }));
  await page.route('**/api/favorites', route => route.fulfill({ json: [] }));
  await page.route('**/api/portfolio/holdings', async route => {
    const request = route.request();
    if (request.method() === 'GET') return route.fulfill({ json: holdings });
    if (request.method() === 'POST') {
      const body = request.postDataJSON();
      const holding = { id: Date.now(), ...body };
      holdings.unshift(holding);
      return route.fulfill({ json: holding });
    }
    return route.fallback();
  });
  await page.route('**/api/portfolio/holdings/*', route => {
    const id = Number(route.request().url().split('/').pop());
    const index = holdings.findIndex(holding => holding.id === id);
    if (index >= 0) holdings.splice(index, 1);
    return route.fulfill({ status: 200, body: '' });
  });
  await page.route('**/api/alerts', async route => {
    const request = route.request();
    if (request.method() === 'GET') return route.fulfill({ json: alerts });
    if (request.method() === 'POST') {
      const body = request.postDataJSON();
      const alert = { id: Date.now(), active: true, triggered: false, ...body };
      alerts.unshift(alert);
      return route.fulfill({ json: alert });
    }
    return route.fallback();
  });
  await page.route('**/api/alerts/*/toggle', route => {
    const id = Number(route.request().url().split('/api/alerts/')[1].split('/')[0]);
    const alert = alerts.find(item => item.id === id);
    if (alert) alert.active = !alert.active;
    return route.fulfill({ json: alert });
  });
  await page.route('**/api/alerts/*/triggered', route => {
    const id = Number(route.request().url().split('/api/alerts/')[1].split('/')[0]);
    const alert = alerts.find(item => item.id === id);
    if (alert) {
      alert.active = false;
      alert.triggered = true;
    }
    return route.fulfill({ json: alert });
  });
  await page.route('**/api/alerts/*', route => {
    const id = Number(route.request().url().split('/').pop());
    const index = alerts.findIndex(alert => alert.id === id);
    if (index >= 0) alerts.splice(index, 1);
    return route.fulfill({ status: 200, body: '' });
  });
  await page.route('**/api/stocks/*', route => {
    const symbol = route.request().url().split('/api/stocks/')[1].split(/[/?#]/)[0];
    if (symbol === 'market-overview') {
      return route.fulfill({
        json: ['AAPL', 'MSFT', 'NVDA', 'BTC'].map(marketSymbol => ({
          symbol: marketSymbol,
          name: stockDetails[marketSymbol].name,
          price: stockDetails[marketSymbol].price,
          changePercent: stockDetails[marketSymbol].changePercent,
        })),
      });
    }
    if (symbol === 'search') {
      return route.fulfill({ json: [
        { symbol: 'MSFT', name: 'Microsoft Corp', type: 'stock' },
        { symbol: 'AAPL', name: 'Apple Inc.', type: 'stock' },
      ] });
    }
    return route.fulfill({ json: stockDetails[symbol] || stockDetails.MSFT });
  });
  await page.route('**/api/analysis/*/price-history**', route => route.fulfill({ json: priceHistory }));
  await page.route('**/api/analysis/*/ai', route => route.fulfill({
    json: {
      price: stockDetails.MSFT.price,
      change: stockDetails.MSFT.change,
      changePercent: stockDetails.MSFT.changePercent,
      name: stockDetails.MSFT.name,
      industry: stockDetails.MSFT.industry,
      marketCap: stockDetails.MSFT.marketCap,
      peRatio: stockDetails.MSFT.peRatio,
      high52Week: stockDetails.MSFT.high52Week,
      low52Week: stockDetails.MSFT.low52Week,
      llm: {
        sentiment: 'NEUTRAL',
        confidence: 0.72,
        summary: 'Microsoft remains stable with balanced technical signals.',
        bullishPoints: ['Strong enterprise demand'],
        bearishRisks: ['Valuation pressure'],
        outlook: 'Moderate growth outlook.',
      },
      technical: { rsi: 56.2, macd: 1.12, sma20: 416.14, sma50: 415.12 },
    },
  }));
  await page.route('**/api/analysis/*/news**', route => route.fulfill({
    json: [
      { title: 'Microsoft market update', source: 'MarketWire', date: '2026-05-08', summary: 'Shares moved with the broader tech sector.', url: '#' },
    ],
  }));
});

test('market overview cards navigate to stock analysis', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('button', { name: /MSFT/i })).toBeVisible();
  await page.getByRole('button', { name: /MSFT/i }).click();

  await expect(page).toHaveURL(/\/analysis\/MSFT$/);
  await expect(page.getByRole('heading', { name: 'MSFT' })).toBeVisible();
  await expect(page.getByText('Microsoft Corp')).toBeVisible();
  await expect(page.getByText('$415.14')).toBeVisible();
});

test('holistic app journey covers research, lazy analysis, portfolio, and alerts', async ({ page, isMobile }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

  await page.getByRole('button', { name: /MSFT/i }).click();
  await expect(page).toHaveURL(/\/analysis\/MSFT$/);
  await expect(page.getByRole('heading', { name: 'MSFT' })).toBeVisible();

  await page.getByRole('button', { name: 'AI Analysis' }).click();
  await expect(page.getByText('Microsoft remains stable with balanced technical signals.')).toBeVisible();

  await page.getByRole('button', { name: 'News' }).click();
  await expect(page.getByText('Microsoft market update')).toBeVisible();

  await navigateBySidebar(page, isMobile, 'Research');
  await expect(page.getByRole('heading', { name: 'Symbol search' })).toBeVisible();
  await page.getByPlaceholder(/Search stocks/i).fill('MSFT');
  await page.getByRole('button', { name: /^Search$/ }).click();
  await expect(page.getByText('Microsoft Corp').first()).toBeVisible();

  await navigateBySidebar(page, isMobile, 'Portfolio');
  await expect(page.getByRole('heading', { name: 'Holdings' })).toBeVisible();
  await page.getByPlaceholder('Symbol').fill('MSFT');
  await page.getByPlaceholder('Shares').fill('2');
  await page.getByPlaceholder('Buy price').fill('400');
  await page.getByRole('button', { name: 'Add' }).click();
  await expect(page.getByRole('button', { name: /MSFT/ })).toBeVisible();

  await navigateBySidebar(page, isMobile, 'Alerts');
  await expect(page.getByRole('heading', { name: /Price alerts/ })).toBeVisible();
  await page.getByPlaceholder('Symbol').fill('MSFT');
  await page.getByPlaceholder('Target price').fill('430');
  await page.getByRole('button', { name: 'Add alert' }).click();
  await expect(page.getByText('Price above')).toBeVisible();
  await expect(page.getByText('$430')).toBeVisible();
});

test('mobile core routes fit without horizontal overflow', async ({ page, isMobile }) => {
  test.skip(!isMobile, 'Mobile layout check runs only in mobile projects');

  const routes = ['/', '/search', '/portfolio', '/alerts', '/analysis/MSFT'];

  for (const route of routes) {
    await page.goto(route);
    await page.waitForLoadState('networkidle');

    const layout = await page.evaluate(() => ({
      viewportWidth: window.innerWidth,
      scrollWidth: document.documentElement.scrollWidth,
    }));

    expect(layout.scrollWidth).toBeLessThanOrEqual(layout.viewportWidth + 1);
  }
});
