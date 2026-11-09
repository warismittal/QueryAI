import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ThemeSelector } from './ThemeSelector';

describe('ThemeSelector', () => {
  it('lists every theme with its mood and marks the active one', async () => {
    const user = userEvent.setup();
    render(<ThemeSelector />);
    const trigger = screen.getByRole('button', { name: /Theme: Midnight/ });
    trigger.focus();
    await user.keyboard('{Enter}');

    const items = await screen.findAllByRole('menuitem');
    expect(items).toHaveLength(6);
    expect(screen.getByText('Deep sea & bioluminescence')).toBeInTheDocument();
    expect(screen.getByText('Midnight navy to azure')).toBeInTheDocument();
  });

  it('switches the whole document to the picked theme', async () => {
    const user = userEvent.setup();
    render(<ThemeSelector />);
    screen.getByRole('button', { name: /Change theme/ }).focus();
    await user.keyboard('{Enter}');
    await user.click(await screen.findByRole('menuitem', { name: /Marine/ }));

    expect(document.documentElement.dataset.theme).toBe('marine');
    expect(screen.getByRole('button', { name: /Theme: Marine/ })).toBeInTheDocument();

    screen.getByRole('button', { name: /Change theme/ }).focus();
    await user.keyboard('{Enter}');
    await user.click(await screen.findByRole('menuitem', { name: /Ombré Blue/ }));
    expect(document.documentElement.dataset.theme).toBe('azure');
  });
});
