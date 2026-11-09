import { screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { QUESTION_CATEGORIES } from '@/lib/questions';
import { mockApi, renderApp } from '@/test/utils';
import { user } from '@/test/fixtures';
import Landing from './Landing';

// the three.js library → door → digital-world journey needs WebGL, which jsdom does not have
vi.mock('@/components/landing/LibraryHero', () => ({
  LibraryHero: ({ signedIn }: { signedIn: boolean }) => <section aria-label="Library journey">{signedIn ? 'signed in' : 'visitor'}</section>,
}));

describe('Landing page', () => {
  it('invites visitors to sign up or sign in', () => {
    mockApi({});
    renderApp(<Landing />, { auth: true });
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(within(nav).getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login');
    expect(within(nav).getByRole('link', { name: 'Get started' })).toHaveAttribute('href', '/register');
    expect(screen.getByRole('region', { name: 'Library journey' })).toHaveTextContent('visitor');
    expect(screen.getByRole('link', { name: /Create a free account/ })).toHaveAttribute('href', '/register');
    for (const link of screen.getAllByRole('link', { name: 'Sign in' })) expect(link).toHaveAttribute('href', '/login');
    expect(screen.queryByText(/demo/i)).not.toBeInTheDocument();
  });

  it('tells the story through the five question themes', () => {
    mockApi({});
    renderApp(<Landing />, { auth: true });
    expect(screen.getByRole('heading', { name: /Questions, not queries\./ })).toBeInTheDocument();
    for (const c of QUESTION_CATEGORIES) {
      expect(screen.getByText(c.title)).toBeInTheDocument();
      for (const q of c.questions) expect(screen.getByRole('link', { name: q })).toHaveAttribute('href', '/register');
    }
  });

  it('shows every step of the answer pipeline', () => {
    mockApi({});
    renderApp(<Landing />, { auth: true });
    const pipeline = screen.getByRole('region', { name: 'How an answer is made' });
    for (const step of ['Question', 'Semantic schema search', 'Validation & self-repair', 'Read-only run', 'Explanation']) {
      expect(within(pipeline).getByText(step)).toBeInTheDocument();
    }
  });

  it('sends signed-in people straight into Ask AI with the question filled in', async () => {
    localStorage.setItem('queryai.token', 'jwt');
    mockApi({ 'GET /auth/me': user });
    renderApp(<Landing />, { auth: true });

    expect(await screen.findByRole('link', { name: /Open workspace/ })).toHaveAttribute('href', '/app');
    expect(screen.getByRole('region', { name: 'Library journey' })).toHaveTextContent('signed in');
    const q = QUESTION_CATEGORIES[0].questions[0];
    expect(screen.getByRole('link', { name: q })).toHaveAttribute('href', `/app/studio?q=${encodeURIComponent(q)}`);
    expect(screen.queryByRole('link', { name: 'Sign in' })).not.toBeInTheDocument();
  });
});
