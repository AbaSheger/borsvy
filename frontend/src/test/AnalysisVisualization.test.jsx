import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import AnalysisVisualization from '../components/AnalysisVisualization';

// Mock ThemeContext
vi.mock('../context/ThemeContext', () => ({
  useTheme: () => ({ theme: 'dark' }),
}));

describe('AnalysisVisualization', () => {
  it('renders nothing when analysis is null', () => {
    const { container } = render(<AnalysisVisualization analysis={null} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows "No analysis data available" when all fields are empty', () => {
    render(<AnalysisVisualization analysis={{}} />);
    expect(screen.getByText('No analysis data available')).toBeInTheDocument();
  });

  it('renders AI Analysis section with summary', () => {
    const analysis = {
      llm: {
        sentiment: 'POSITIVE',
        confidence: 0.85,
        summary: 'Apple is a strong buy.\n\nStrong fundamentals.',
        bullishPoints: ['Revenue growth', 'Strong brand'],
        bearishRisks: ['Macro headwinds'],
        outlook: 'Positive short-term outlook.',
      },
    };
    render(<AnalysisVisualization analysis={analysis} />);

    expect(screen.getByText('AI Analysis')).toBeInTheDocument();
    expect(screen.getByText('Apple is a strong buy.')).toBeInTheDocument();
    expect(screen.getByText('Strong fundamentals.')).toBeInTheDocument();
  });

  it('displays Bullish badge for POSITIVE sentiment', () => {
    const analysis = {
      llm: { sentiment: 'POSITIVE', confidence: 0.9, summary: 'Summary text' },
    };
    render(<AnalysisVisualization analysis={analysis} />);
    expect(screen.getByText('Bullish')).toBeInTheDocument();
    expect(screen.getByText('90% confidence')).toBeInTheDocument();
  });

  it('displays Bearish badge for NEGATIVE sentiment', () => {
    const analysis = {
      llm: { sentiment: 'NEGATIVE', confidence: 0.7, summary: 'Bad outlook' },
    };
    render(<AnalysisVisualization analysis={analysis} />);
    expect(screen.getByText('Bearish')).toBeInTheDocument();
  });

  it('displays Neutral badge for NEUTRAL sentiment', () => {
    const analysis = {
      llm: { sentiment: 'NEUTRAL', summary: 'Mixed signals' },
    };
    render(<AnalysisVisualization analysis={analysis} />);
    expect(screen.getByText('Neutral')).toBeInTheDocument();
  });

  it('renders bullish and bearish signal lists', () => {
    const analysis = {
      llm: {
        summary: 'Summary',
        bullishPoints: ['Point A', 'Point B'],
        bearishRisks: ['Risk X'],
      },
    };
    render(<AnalysisVisualization analysis={analysis} />);
    expect(screen.getByText('Bullish Signals')).toBeInTheDocument();
    expect(screen.getByText('Key Risks')).toBeInTheDocument();
    expect(screen.getByText('Point A')).toBeInTheDocument();
    expect(screen.getByText('Risk X')).toBeInTheDocument();
  });

  it('renders Outlook section when provided', () => {
    const analysis = {
      llm: { summary: 'Summary', outlook: 'Stock looks bullish over next quarter.' },
    };
    render(<AnalysisVisualization analysis={analysis} />);
    expect(screen.getByText('Outlook')).toBeInTheDocument();
    expect(screen.getByText('Stock looks bullish over next quarter.')).toBeInTheDocument();
  });

  it('renders Technical Indicators section', () => {
    const analysis = {
      technical: { rsi: 65.4, macd: 1.23, sma20: 180.5, sma50: 175.0 },
    };
    render(<AnalysisVisualization analysis={analysis} />);
    expect(screen.getByText('Technical Indicators')).toBeInTheDocument();
    expect(screen.getByText('RSI (14)')).toBeInTheDocument();
    expect(screen.getByText('MACD')).toBeInTheDocument();
    expect(screen.getByText('SMA 20')).toBeInTheDocument();
    expect(screen.getByText('SMA 50')).toBeInTheDocument();
  });

  it('shows Overbought note when RSI > 70', () => {
    const analysis = { technical: { rsi: 78.0 } };
    render(<AnalysisVisualization analysis={analysis} />);
    expect(screen.getByText('Overbought')).toBeInTheDocument();
  });

  it('shows Oversold note when RSI < 30', () => {
    const analysis = { technical: { rsi: 22.5 } };
    render(<AnalysisVisualization analysis={analysis} />);
    expect(screen.getByText('Oversold')).toBeInTheDocument();
  });

  it('accepts top-level fields as fallback (non-llm format)', () => {
    const analysis = {
      sentiment: 'POSITIVE',
      confidence: 0.75,
      summary: 'Top level summary',
      bullishPoints: ['Growth'],
      bearishRisks: [],
      rsi: 55,
    };
    render(<AnalysisVisualization analysis={analysis} />);
    expect(screen.getByText('Top level summary')).toBeInTheDocument();
    expect(screen.getByText('Bullish')).toBeInTheDocument();
  });
});
