import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { mockApi, renderApp, reply } from '@/test/utils';
import { dataSource, systemInfo, workspace } from '@/test/fixtures';
import { CsvUpload } from './CsvUpload';

const base = (overrides: Record<string, unknown> = {}) => ({
  'GET /workspaces': [workspace()],
  'GET /datasources': [dataSource()],
  'GET /system/info': systemInfo(),
  'GET /uploads': [],
  ...overrides,
});

const fileInput = (container: HTMLElement) => container.querySelector('input[type="file"]') as HTMLInputElement;
const csv = (name: string, text = 'region,amount\nNorth,10\n') => new File([text], name, { type: 'text/csv' });

describe('CsvUpload', () => {
  it('explains when uploads are switched off on the server', async () => {
    mockApi(base({ 'GET /system/info': systemInfo({ uploadsEnabled: false }) }));
    renderApp(<CsvUpload />, { workspace: true });
    expect(await screen.findByText(/CSV uploads are switched off/)).toBeInTheDocument();
  });

  it('lists the workspace uploads', async () => {
    mockApi(base({
      'GET /uploads': [{ id: 3, dataSourceId: 9, table: 'sales_2025', originalFile: 'Sales 2025.csv', rowCount: 1200, columnCount: 4,
        sizeBytes: 2048, createdAt: new Date().toISOString() }],
    }));
    renderApp(<CsvUpload />, { workspace: true });
    expect(await screen.findByText('sales_2025')).toBeInTheDocument();
    expect(screen.getByText(/1,200 rows · Sales 2025.csv/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete sales_2025' })).toBeInTheDocument();
  });

  it('turns a chosen file into a table and shows the inferred column types', async () => {
    const api = mockApi(base({
      'POST /uploads': {
        id: 4, dataSourceId: 9, dataSourceName: 'Uploaded files', schema: 'uploads_w1', table: 'my_sales', rowCount: 2, replaced: false,
        columns: [{ name: 'region', header: 'region', type: 'TEXT' }, { name: 'amount', header: 'amount', type: 'NUMERIC' }],
      },
    }));
    const u = userEvent.setup();
    const { container } = renderApp(<CsvUpload />, { workspace: true });
    await screen.findByText('Drop a CSV here, or choose a file');
    await waitFor(() => expect(screen.getByRole('button', { name: /Drop a CSV here/ })).toBeEnabled());

    await u.upload(fileInput(container), csv('My Sales.csv'));
    expect(screen.getByText('My Sales.csv')).toBeInTheDocument();
    expect(screen.getByLabelText('Table name')).toHaveValue('my_sales');

    await u.click(screen.getByLabelText('Replace the table if it already exists'));
    await u.click(screen.getByRole('button', { name: /Create table/ }));

    expect(await screen.findByText('Created')).toBeInTheDocument();
    expect(screen.getByText('decimal')).toBeInTheDocument();
    expect(screen.getByText(/2 rows · 2 columns · Uploaded files/)).toBeInTheDocument();

    const form = api.callsTo('POST /uploads')[0].body as FormData;
    expect(form).toBeInstanceOf(FormData);
    expect((form.get('file') as File).name).toBe('My Sales.csv');
    expect(form.get('workspaceId')).toBe('1');
    expect(form.get('tableName')).toBe('my_sales');
    expect(form.get('replace')).toBe('true');
    // multipart bodies must not be sent as JSON
    expect(api.callsTo('POST /uploads')[0].headers['Content-Type']).toBeUndefined();
  });

  it('ignores files that are not CSV', async () => {
    const api = mockApi(base());
    const { container } = renderApp(<CsvUpload />, { workspace: true });
    await screen.findByText('Drop a CSV here, or choose a file');
    fireEvent.change(fileInput(container), { target: { files: [csv('report.xlsx')] } });
    expect(screen.queryByLabelText('Table name')).not.toBeInTheDocument();
    expect(api.callsTo('POST /uploads')).toHaveLength(0);
  });

  it('keeps the form when the server reports a name conflict', async () => {
    mockApi(base({ 'POST /uploads': reply(409, { code: 'CONFLICT', message: "A table named 'my_sales' already exists." }) }));
    const u = userEvent.setup();
    const { container } = renderApp(<CsvUpload />, { workspace: true });
    await screen.findByText('Drop a CSV here, or choose a file');
    await waitFor(() => expect(screen.getByRole('button', { name: /Drop a CSV here/ })).toBeEnabled());
    await u.upload(fileInput(container), csv('my_sales.csv'));
    await u.click(screen.getByRole('button', { name: /Create table/ }));
    await waitFor(() => expect(screen.getByRole('button', { name: /Create table/ })).toBeEnabled());
    expect(screen.getByLabelText('Table name')).toHaveValue('my_sales');
    expect(screen.queryByText('Created')).not.toBeInTheDocument();
  });

  it('viewers cannot upload', async () => {
    mockApi(base({ 'GET /workspaces': [workspace({ myRole: 'VIEWER' })] }));
    renderApp(<CsvUpload />, { workspace: true });
    await screen.findByText('Drop a CSV here, or choose a file');
    await waitFor(() => expect(screen.getByRole('button', { name: /Drop a CSV here/ })).toBeDisabled());
  });
});
