import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '../context/ThemeContext';
import NewsCard from '../components/NewsCard';

const wrap = (ui) => render(<ThemeProvider>{ui}</ThemeProvider>);

const makeArticle = (overrides = {}) => ({
  title: 'Apple reports record Q1 earnings',
  url: 'https://example.com/news/1',
  source: 'Reuters',
  date: '2024-01-15',
  summary: 'Apple posted record revenue for the first quarter.',
  thumbnail: '',
  ...overrides,
});

describe('NewsCard', () => {
  it('shows empty state when news is undefined', () => {
    wrap(<NewsCard />);
    expect(screen.getByText('No recent news available')).toBeTruthy();
  });

  it('shows empty state when news is an empty array', () => {
    wrap(<NewsCard news={[]} />);
    expect(screen.getByText('No recent news available')).toBeTruthy();
  });

  it('shows empty state when news is null', () => {
    wrap(<NewsCard news={null} />);
    expect(screen.getByText('No recent news available')).toBeTruthy();
  });

  it('renders article title as a link', () => {
    wrap(<NewsCard news={[makeArticle()]} />);
    const link = screen.getByText('Apple reports record Q1 earnings');
    expect(link.tagName.toLowerCase()).toBe('a');
    expect(link.getAttribute('href')).toBe('https://example.com/news/1');
  });

  it('renders source and date metadata', () => {
    wrap(<NewsCard news={[makeArticle()]} />);
    expect(screen.getByText('Reuters')).toBeTruthy();
    expect(screen.getByText('2024-01-15')).toBeTruthy();
  });

  it('renders summary text', () => {
    wrap(<NewsCard news={[makeArticle()]} />);
    expect(screen.getByText('Apple posted record revenue for the first quarter.')).toBeTruthy();
  });

  it('renders "Read more" link', () => {
    wrap(<NewsCard news={[makeArticle()]} />);
    const links = screen.getAllByText('Read more');
    expect(links.length).toBeGreaterThan(0);
    expect(links[0].getAttribute('href')).toBe('https://example.com/news/1');
  });

  it('shows POSITIVE sentiment badge', () => {
    wrap(<NewsCard news={[makeArticle({ sentiment: 'POSITIVE' })]} />);
    expect(screen.getByText('Positive')).toBeTruthy();
  });

  it('shows NEGATIVE sentiment badge', () => {
    wrap(<NewsCard news={[makeArticle({ sentiment: 'NEGATIVE' })]} />);
    expect(screen.getByText('Negative')).toBeTruthy();
  });

  it('shows NEUTRAL sentiment badge for unknown sentiment', () => {
    wrap(<NewsCard news={[makeArticle({ sentiment: 'NEUTRAL' })]} />);
    expect(screen.getByText('Neutral')).toBeTruthy();
  });

  it('renders multiple articles', () => {
    const articles = [
      makeArticle({ title: 'Article One', url: 'https://example.com/1' }),
      makeArticle({ title: 'Article Two', url: 'https://example.com/2' }),
      makeArticle({ title: 'Article Three', url: 'https://example.com/3' }),
    ];
    wrap(<NewsCard news={articles} />);
    expect(screen.getByText('Article One')).toBeTruthy();
    expect(screen.getByText('Article Two')).toBeTruthy();
    expect(screen.getByText('Article Three')).toBeTruthy();
  });

  it('renders "Recent News" heading', () => {
    wrap(<NewsCard news={[makeArticle()]} />);
    expect(screen.getByText('Recent News')).toBeTruthy();
  });
});
