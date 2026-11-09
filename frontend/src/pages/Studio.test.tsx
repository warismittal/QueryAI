import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { mockApi, renderApp } from '@/test/utils';
import { analysis, dataSource, schema, SPEND_SQL, spendResult, systemInfo, workspace } from '@/test/fixtures';
import Studio from './Studio';

// Monaco and Recharts need a real browser; simple stand-ins keep the page logic under test
vi.mock('@/components/sql/SqlEditor', () => ({
  SqlEditor: ({ value, onChange }: { value: string; onChange: (v: string) => void }) => (
    <textarea aria-label="SQL editor" value={value} onChange={(e) => onChange(e.target.value)} />
  ),
}));
vi.mock('@/components/results/ResultChart', () => ({ ResultChart: () => <div data-testid="chart" /> }));

function routes(overrides: Record<string, unknown> = {}) {
  return {
    'GET /workspaces': [workspace()],
    'GET /datasources': [dataSource()],
    'GET /system/info': systemInfo(),
    'GET /datasources/5/schema': schema,
    'GET /saved-queries': [],
    'GET /conversations': [],
    'POST /analytics/analyze': analysis(),
    'POST /sql/execute': { queryRunId: 100, status: 'SUCCESS', result: spendResult },
    'POST /sql/validate': { status: 'VALID', issues: [], referencedTables: ['customers'], parameters: [], hasLimit: false, databaseChecked: true },
    ...overrides,
  };
}

const conversation = () => screen.getByRole('region', { name: 'Conversation' });
const answer = () => screen.getByRole('region', { name: 'Answer' });

async function askFromHero(u: ReturnType<typeof userEvent.setup>, question: string) {
  const field = await screen.findByLabelText('Your question');
  await waitFor(() => expect(field).toBeEnabled());
  await u.type(field, question);
  await u.keyboard('{Enter}');
}

describe('Ask AI (Studio)', () => {
  it('opens on the question hero', async () => {
    mockApi(routes());
    renderApp(<Studio />, { workspace: true });
    expect(await screen.findByRole('heading', { name: /What would you like to understand\?/ })).toBeInTheDocument();
    expect(await screen.findByText('Ask AI · Shop')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Answer' })).not.toBeInTheDocument();
  });

  it('puts the answer in its own panel, never on top of the conversation', async () => {
    const api = mockApi(routes());
    const u = userEvent.setup();
    renderApp(<Studio />, { workspace: true });
    await askFromHero(u, 'Who spends the most?');

    // the conversation keeps the question, the AI's explanation and the composer
    await within(conversation()).findByText('Grace is your most valuable customer.');
    expect(within(conversation()).getByText('Who spends the most?')).toBeInTheDocument();
    expect(within(conversation()).getByLabelText('Question')).toBeInTheDocument();
    expect(within(conversation()).queryByRole('table')).not.toBeInTheDocument();
    expect(within(conversation()).queryByLabelText('SQL editor')).not.toBeInTheDocument();

    // the rows live in the separate Answer panel, which is showing the answer tab
    const table = within(answer()).getByRole('table');
    expect(within(table).getByText('Grace')).toBeInTheDocument();
    expect(within(answer()).getByRole('tab', { name: /Answer/ })).toHaveAttribute('aria-selected', 'true');
    expect(within(answer()).queryByLabelText('Question')).not.toBeInTheDocument();

    expect(api.callsTo('POST /analytics/analyze')[0].body).toMatchObject({ dataSourceId: 5, question: 'Who spends the most?' });
  });

  it('shows the generated SQL in the SQL tab and runs edits from there', async () => {
    const api = mockApi(routes());
    const u = userEvent.setup();
    renderApp(<Studio />, { workspace: true });
    await askFromHero(u, 'Who spends the most?');
    await within(answer()).findByRole('table');

    await u.click(within(answer()).getByRole('tab', { name: /SQL/ }));
    const editor = within(answer()).getByLabelText('SQL editor');
    expect(editor).toHaveValue(SPEND_SQL);

    await u.clear(editor);
    await u.type(editor, 'SELECT name FROM customers');
    await u.click(within(answer()).getByRole('button', { name: /Run/ }));

    await waitFor(() => expect(api.callsTo('POST /sql/execute')).toHaveLength(1));
    expect(api.callsTo('POST /sql/execute')[0].body).toMatchObject({ dataSourceId: 5, sql: 'SELECT name FROM customers' });
    // a successful run switches back to the answer
    await waitFor(() => expect(within(answer()).getByRole('tab', { name: /Answer/ })).toHaveAttribute('aria-selected', 'true'));
  });

  it('a suggested question asks in one click', async () => {
    const api = mockApi(routes());
    const u = userEvent.setup();
    renderApp(<Studio />, { workspace: true });
    const suggestion = await screen.findByRole('button', { name: /Who are my most valuable customers\?/ });
    await waitFor(() => expect(suggestion).toBeEnabled());
    await u.click(suggestion);
    await waitFor(() => expect(api.callsTo('POST /analytics/analyze')).toHaveLength(1));
    expect(api.callsTo('POST /analytics/analyze')[0].body).toMatchObject({ question: 'Who are my most valuable customers?' });
  });

  it('explains blocked SQL instead of showing results', async () => {
    mockApi(routes({ 'POST /analytics/analyze': analysis({ status: 'BLOCKED', result: undefined, interpretation: undefined, sql: 'DELETE FROM orders', error: 'Only SELECT statements are allowed' }) }));
    const u = userEvent.setup();
    renderApp(<Studio />, { workspace: true });
    await askFromHero(u, 'Delete all orders');
    expect(await within(answer()).findByText('Query blocked by validation')).toBeInTheDocument();
    expect(within(conversation()).getByText(/did not pass validation/)).toBeInTheDocument();
    expect(within(answer()).queryByRole('table')).not.toBeInTheDocument();
  });

  it('offers SQL by hand when no AI model is connected', async () => {
    mockApi(routes({ 'GET /system/info': systemInfo({ llmConfigured: false }) }));
    const u = userEvent.setup();
    renderApp(<Studio />, { workspace: true });
    const field = await screen.findByLabelText('Your question');
    await waitFor(() => expect(field).toHaveAttribute('placeholder', expect.stringMatching(/Connect an AI model/)));
    expect(field).toBeDisabled();

    await u.click(screen.getByRole('button', { name: /Write SQL yourself/ }));
    expect(within(answer()).getByLabelText('SQL editor')).toBeInTheDocument();
    expect(within(answer()).getByRole('tab', { name: /SQL/ })).toHaveAttribute('aria-selected', 'true');
  });

  it('asks a question passed in the link (from the landing page)', async () => {
    const api = mockApi(routes());
    renderApp(<Studio />, { workspace: true, route: '/app/studio?q=Which%20month%20was%20best%3F', path: '/app/studio' });
    await waitFor(() => expect(api.callsTo('POST /analytics/analyze')).toHaveLength(1));
    expect(api.callsTo('POST /analytics/analyze')[0].body).toMatchObject({ question: 'Which month was best?' });
  });

  it('asks for a connection when the workspace has none', async () => {
    mockApi(routes({ 'GET /datasources': [] }));
    renderApp(<Studio />, { workspace: true });
    expect(await screen.findByText('Connect a database to start asking questions')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Add a connection' })).toHaveAttribute('href', '/app/connections');
  });
});
