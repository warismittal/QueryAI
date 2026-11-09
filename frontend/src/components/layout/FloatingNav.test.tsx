import { act, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { FloatingNav, PRIMARY_NAV } from './FloatingNav';

function renderNav(variant: 'landing' | 'app', route = '/app') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <FloatingNav variant={variant} right={<button>Account</button>} />
    </MemoryRouter>,
  );
}

describe('FloatingNav', () => {
  it('shows the seven primary destinations in order', () => {
    renderNav('app');
    const nav = screen.getByRole('navigation', { name: 'Main' });
    const labels = within(nav).getAllByRole('link').map((a) => a.textContent).filter((t) => t && PRIMARY_NAV.some((n) => n.label === t));
    expect(labels).toEqual(['Home', 'Explore', 'Ask AI', 'Visualizations', 'Saved Queries', 'History', 'Settings']);
    expect(within(nav).getByRole('link', { name: 'Ask AI' })).toHaveAttribute('href', '/app/studio');
    expect(within(nav).getByRole('link', { name: 'Visualizations' })).toHaveAttribute('href', '/app/dashboards');
  });

  it('marks the current page in the workspace', () => {
    renderNav('app', '/app/studio');
    expect(screen.getByRole('link', { name: 'Ask AI' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current');
  });

  it('starts as a soft glass capsule on the landing page and condenses on scroll', () => {
    renderNav('landing', '/');
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(nav.className).toContain('glass-soft');

    act(() => {
      Object.defineProperty(window, 'scrollY', { configurable: true, value: 200 });
      fireEvent.scroll(window);
    });
    expect(nav.className).not.toContain('glass-soft');
    expect(nav.className).toContain('glass');
    Object.defineProperty(window, 'scrollY', { configurable: true, value: 0 });
  });

  it('is always condensed inside the workspace', () => {
    renderNav('app');
    expect(screen.getByRole('navigation', { name: 'Main' }).className).not.toContain('glass-soft');
  });

  it('opens a full-screen menu on small screens and closes it after navigating', async () => {
    const user = userEvent.setup();
    renderNav('app');
    await user.click(screen.getByRole('button', { name: 'Open menu' }));
    const dialog = screen.getByRole('dialog', { name: 'Menu' });
    expect(within(dialog).getAllByRole('link')).toHaveLength(PRIMARY_NAV.length);

    await user.click(within(dialog).getByRole('link', { name: 'History' }));
    expect(screen.queryByRole('dialog', { name: 'Menu' })).not.toBeInTheDocument();
  });

  it('closes the menu with the close button', async () => {
    const user = userEvent.setup();
    renderNav('app');
    await user.click(screen.getByRole('button', { name: 'Open menu' }));
    await user.click(screen.getByRole('button', { name: 'Close menu' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
