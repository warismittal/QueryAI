import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { QueryResult } from '@/lib/types';
import { ResultsTable, useTableState } from './ResultsTable';

function result(rows: QueryResult['rows'], maskedColumns: string[] = []): QueryResult {
  return {
    columns: [
      { name: 'name', sqlType: 'varchar', category: 'TEXT' },
      { name: 'email', sqlType: 'varchar', category: 'TEXT' },
      { name: 'spend', sqlType: 'numeric', category: 'NUMBER' },
    ],
    rows, rowCount: rows.length, truncated: false, durationMs: 12, maxRows: 1000, maskedColumns,
  } as QueryResult;
}

function Harness({ data }: { data: QueryResult }) {
  const state = useTableState(data);
  return <ResultsTable result={data} state={state} />;
}

const bodyRows = () => within(screen.getAllByRole('rowgroup')[1]).getAllByRole('row');
const firstNames = () => bodyRows().map((r) => within(r).getAllByRole('cell')[1].textContent);

describe('ResultsTable', () => {
  const data = result([['Grace', 'g•••@example.com', 300], ['Ada', 'a•••@example.com', 200.5], ['Linus', null, 0]], ['email']);

  it('renders every row, numbers and nulls', () => {
    render(<Harness data={data} />);
    expect(bodyRows()).toHaveLength(3);
    expect(screen.getByText('200.5')).toBeInTheDocument();
    expect(screen.getByText('null')).toBeInTheDocument();
    expect(screen.getByText('3 rows')).toBeInTheDocument();
  });

  it('marks masked personal-data columns with a lock', () => {
    render(<Harness data={data} />);
    const header = screen.getByRole('button', { name: /Sort by email/ });
    expect(within(header).getByLabelText('masked personal data')).toBeInTheDocument();
    expect(within(screen.getByRole('button', { name: /Sort by name/ })).queryByLabelText('masked personal data')).not.toBeInTheDocument();
  });

  it('sorts ascending, descending, then back to the original order', async () => {
    const user = userEvent.setup();
    render(<Harness data={data} />);
    const sortSpend = screen.getByRole('button', { name: /Sort by spend/ });
    await user.click(sortSpend);
    expect(firstNames()).toEqual(['Linus', 'Ada', 'Grace']);
    await user.click(sortSpend);
    expect(firstNames()).toEqual(['Grace', 'Ada', 'Linus']);
    await user.click(sortSpend);
    expect(firstNames()).toEqual(['Grace', 'Ada', 'Linus']);
  });

  it('filters rows across all columns', async () => {
    const user = userEvent.setup();
    render(<Harness data={data} />);
    await user.type(screen.getByLabelText('Filter rows'), 'lin');
    expect(firstNames()).toEqual(['Linus']);
    expect(screen.getByText('1 of 3 rows')).toBeInTheDocument();
    await user.clear(screen.getByLabelText('Filter rows'));
    await user.type(screen.getByLabelText('Filter rows'), 'nobody');
    expect(screen.getByText('No rows match the filter.')).toBeInTheDocument();
  });

  it('paginates large results', async () => {
    const user = userEvent.setup();
    const many = result(Array.from({ length: 60 }, (_, i) => [`user${i}`, null, i]));
    render(<Harness data={many} />);
    expect(bodyRows()).toHaveLength(50);
    expect(screen.getByText('1 / 2')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(bodyRows()).toHaveLength(10);
    expect(screen.getByText('2 / 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
  });
});
