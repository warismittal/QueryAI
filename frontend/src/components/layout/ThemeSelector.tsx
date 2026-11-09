import * as Popover from '@radix-ui/react-dropdown-menu';
import { Check } from 'lucide-react';
import { THEMES, useTheme, type Theme } from '@/lib/theme';
import { cn } from '@/lib/utils';

function Swatch({ colors, className }: { colors: [string, string, string]; className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn('relative inline-block size-5 shrink-0 overflow-hidden rounded-full ring-1 ring-white/15', className)}
      style={{ background: `linear-gradient(135deg, ${colors[0]} 0%, ${colors[1]} 55%, ${colors[2]} 100%)` }}
    >
      <span className="absolute inset-0 rounded-full shadow-[inset_0_1px_1px_rgb(255_255_255/0.25)]" />
    </span>
  );
}

/** Premium theme picker: ombré swatches, names and a one-line mood for each theme. */
export function ThemeSelector({ compact }: { compact?: boolean }) {
  const { theme, setTheme } = useTheme();
  const active = THEMES.find((t) => t.id === theme) ?? THEMES[0];
  return (
    <Popover.Root>
      <Popover.Trigger
        className={cn('group inline-flex items-center gap-2 rounded-full border border-transparent px-1.5 py-1.5 text-xs text-fg-2 transition-colors hover:border-border hover:bg-surface-2/60 hover:text-fg',
          !compact && 'pr-3')}
        aria-label={`Theme: ${active.name}. Change theme`}
      >
        <Swatch colors={active.swatch} className="transition-transform duration-500 group-hover:rotate-180" />
        {!compact && <span className="hidden xl:inline">{active.name}</span>}
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content align="end" sideOffset={10}
          className="glass z-[60] w-64 rounded-2xl p-2 shadow-[0_30px_80px_-20px_rgb(0_0_0/0.7)] animate-fade">
          <p className="eyebrow px-2.5 pb-2 pt-1.5">Atmosphere</p>
          {THEMES.map((t) => (
            <Popover.Item key={t.id} onSelect={() => setTheme(t.id as Theme)}
              className={cn('flex cursor-pointer items-center gap-3 rounded-xl px-2.5 py-2 outline-none transition-colors data-[highlighted]:bg-surface-3/70',
                t.id === theme && 'bg-surface-2/80')}>
              <Swatch colors={t.swatch} className="size-7" />
              <span className="min-w-0 flex-1">
                <span className="block text-sm text-fg">{t.name}</span>
                <span className="block truncate text-[11px] text-muted">{t.note}</span>
              </span>
              {t.id === theme && <Check className="size-4 text-accent" />}
            </Popover.Item>
          ))}
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}
