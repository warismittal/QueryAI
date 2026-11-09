import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { QUESTION_CATEGORIES } from '@/lib/questions';
import { QueryHero } from './QueryHero';

function Harness(props: Partial<Parameters<typeof QueryHero>[0]> & { initial?: string }) {
  const [value, setValue] = useState(props.initial ?? '');
  return (
    <QueryHero value={value} onChange={setValue} onSubmit={props.onSubmit ?? (() => {})} onPick={props.onPick ?? (() => {})}
      onManual={props.onManual ?? (() => {})} disabledReason={props.disabledReason} pending={props.pending ?? false}
      sourceName={props.sourceName} />
  );
}

describe('QueryHero', () => {
  it('invites a question with the editorial placeholder', () => {
    render(<Harness sourceName="Shop" />);
    expect(screen.getByRole('heading', { name: /What would you like to understand\?/ })).toBeInTheDocument();
    expect(screen.getByLabelText('Your question')).toHaveAttribute('placeholder', 'Ask your data something interesting…');
    expect(screen.getByText('Ask AI · Shop')).toBeInTheDocument();
  });

  it('only allows asking once something is typed, and Enter asks', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<Harness onSubmit={onSubmit} />);
    const ask = screen.getByRole('button', { name: 'Ask' });
    expect(ask).toBeDisabled();

    await user.type(screen.getByLabelText('Your question'), 'Who spends the most?');
    expect(ask).toBeEnabled();
    await user.keyboard('{Enter}');
    expect(onSubmit).toHaveBeenCalledTimes(1);
  });

  it('Shift+Enter adds a new line instead of asking', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<Harness onSubmit={onSubmit} />);
    const field = screen.getByLabelText('Your question');
    await user.type(field, 'first line{Shift>}{Enter}{/Shift}second line');
    expect(onSubmit).not.toHaveBeenCalled();
    expect(field).toHaveValue('first line\nsecond line');
  });

  it('shows the five emotional question themes and switches between them', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const themes = screen.getByRole('navigation', { name: 'Question themes' });
    for (const c of QUESTION_CATEGORIES) expect(themes).toHaveTextContent(c.title);
    expect(QUESTION_CATEGORIES.map((c) => c.title)).toEqual(
      ['Discover the Best', 'Find the Lowest', 'Understand the Trends', 'Explore Opportunities', 'Compare & Discover']);

    expect(screen.getByRole('button', { name: /Which product is performing the best\?/ })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Understand the Trends/ }));
    expect(screen.getByRole('button', { name: /Why are sales declining\?/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Which product is performing the best\?/ })).not.toBeInTheDocument();
  });

  it('asks a suggested question in one click', async () => {
    const user = userEvent.setup();
    const onPick = vi.fn();
    render(<Harness onPick={onPick} />);
    await user.click(screen.getByRole('button', { name: /Who are my most valuable customers\?/ }));
    expect(onPick).toHaveBeenCalledWith('Who are my most valuable customers?');
  });

  it('offers writing SQL by hand', async () => {
    const user = userEvent.setup();
    const onManual = vi.fn();
    render(<Harness onManual={onManual} />);
    await user.click(screen.getByRole('button', { name: /Write SQL yourself/ }));
    expect(onManual).toHaveBeenCalled();
  });

  it('explains why asking is unavailable and disables every entry point', () => {
    render(<Harness initial="anything" disabledReason="Connect an AI model to ask in plain English" />);
    const field = screen.getByLabelText('Your question');
    expect(field).toBeDisabled();
    expect(field).toHaveAttribute('placeholder', 'Connect an AI model to ask in plain English');
    expect(screen.getByRole('button', { name: 'Ask' })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Who are my most valuable customers\?/ })).toBeDisabled();
  });
});
