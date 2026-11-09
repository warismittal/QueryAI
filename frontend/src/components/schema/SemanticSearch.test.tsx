import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { mockApi, renderApp, reply } from '@/test/utils';
import { SemanticSearch } from './SemanticSearch';

const response = (engine: string) => ({
  query: 'customer happiness', engine, model: 'local-hash-512',
  matches: [
    { table: 'product_reviews', score: 0.52, description: 'Ratings and comments left by customers' },
    { table: 'customers', score: 0.36 },
  ],
});

describe('SemanticSearch', () => {
  it('ranks tables by meaning and shows the pgvector engine', async () => {
    const api = mockApi({ 'GET /datasources/7/schema/search': response('pgvector') });
    const u = userEvent.setup();
    renderApp(<SemanticSearch dataSourceId={7} onSelect={() => {}} />);

    expect(screen.getByText('Results ranked by similarity appear here.')).toBeInTheDocument();
    await u.type(screen.getByLabelText('Search by meaning'), 'customer happiness');
    await u.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('pgvector · cosine distance')).toBeInTheDocument();
    expect(screen.getByText('product_reviews')).toBeInTheDocument();
    expect(screen.getByText('Ratings and comments left by customers')).toBeInTheDocument();
    expect(screen.getByText('0.52')).toBeInTheDocument();
    expect(screen.getByText('local-hash-512')).toBeInTheDocument();
    expect(api.callsTo('GET /datasources/7/schema/search')[0].url.searchParams.get('q')).toBe('customer happiness');
  });

  it('labels the in-app fallback engine', async () => {
    mockApi({ 'GET /datasources/7/schema/search': response('in-app') });
    const u = userEvent.setup();
    renderApp(<SemanticSearch dataSourceId={7} onSelect={() => {}} />);
    await u.click(screen.getByRole('button', { name: 'running low on stock' }));
    expect(await screen.findByText('in-app cosine similarity')).toBeInTheDocument();
  });

  it('example chips search immediately and results open the table', async () => {
    const api = mockApi({ 'GET /datasources/7/schema/search': response('pgvector') });
    const onSelect = vi.fn();
    const u = userEvent.setup();
    renderApp(<SemanticSearch dataSourceId={7} onSelect={onSelect} />);

    await u.click(screen.getByRole('button', { name: 'customer happiness' }));
    expect(screen.getByLabelText('Search by meaning')).toHaveValue('customer happiness');
    await u.click(await screen.findByRole('button', { name: /product_reviews/ }));
    expect(onSelect).toHaveBeenCalledWith('product_reviews');
    expect(api.callsTo('GET /datasources/7/schema/search')).toHaveLength(1);
  });

  it('does not search for blank input and reports server errors', async () => {
    mockApi({ 'GET /datasources/7/schema/search': reply(503, { code: 'UNAVAILABLE', message: 'Embeddings service is down' }) });
    const u = userEvent.setup();
    renderApp(<SemanticSearch dataSourceId={7} onSelect={() => {}} />);
    expect(screen.getByRole('button', { name: 'Search' })).toBeDisabled();

    await u.type(screen.getByLabelText('Search by meaning'), 'refunds{Enter}');
    expect(await screen.findByText('Embeddings service is down')).toBeInTheDocument();
  });
});
