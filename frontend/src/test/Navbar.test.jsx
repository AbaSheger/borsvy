import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '../context/ThemeContext';
import Navbar from '../components/Navbar';

const wrap = () =>
  render(
    <MemoryRouter>
      <ThemeProvider>
        <Navbar />
      </ThemeProvider>
    </MemoryRouter>
  );

describe('Navbar', () => {
  it('renders links to "/" and "/favorites"', () => {
    wrap();
    const hrefs = screen.getAllByRole('link').map((l) => l.getAttribute('href'));
    expect(hrefs).toContain('/');
    expect(hrefs).toContain('/favorites');
  });

  it('mobile menu links are not in the DOM initially', () => {
    wrap();
    // Only 1 "Home" link (desktop) before menu button is clicked
    expect(screen.getAllByText('Home')).toHaveLength(1);
    expect(screen.getAllByText('Favorites')).toHaveLength(1);
  });

  it('clicking the hamburger button reveals mobile Home and Favorites', async () => {
    const user = userEvent.setup();
    wrap();
    await user.click(screen.getByRole('button'));
    // desktop + mobile = 2 of each
    expect(screen.getAllByText('Home')).toHaveLength(2);
    expect(screen.getAllByText('Favorites')).toHaveLength(2);
  });

  it('clicking a mobile link closes the mobile menu', async () => {
    const user = userEvent.setup();
    wrap();
    await user.click(screen.getByRole('button'));
    // Click the second Home link (the mobile one)
    await user.click(screen.getAllByText('Home')[1]);
    expect(screen.getAllByText('Home')).toHaveLength(1);
  });

  it('clicking mobile Favorites link closes the menu', async () => {
    const user = userEvent.setup();
    wrap();
    await user.click(screen.getByRole('button'));
    await user.click(screen.getAllByText('Favorites')[1]);
    expect(screen.getAllByText('Favorites')).toHaveLength(1);
  });

  it('hamburger button is present and clickable', () => {
    wrap();
    expect(screen.getByRole('button')).toBeTruthy();
  });
});
