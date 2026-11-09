import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import * as Dropdown from '@radix-ui/react-dropdown-menu';
import { ChevronsUpDown, Database, FlaskConical, LogOut, Plus, Shield, Sparkles, Upload, User as UserIcon } from 'lucide-react';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useWorkspace } from '@/lib/workspace';
import type { SystemInfo } from '@/lib/types';
import { cn } from '@/lib/utils';
import { FloatingNav } from './FloatingNav';

const itemClass = 'flex cursor-pointer items-center gap-2.5 rounded-xl px-2.5 py-2 text-sm text-fg-2 outline-none transition-colors data-[highlighted]:bg-surface-3/70 data-[highlighted]:text-fg [&_svg]:size-4 [&_svg]:text-muted';

function WorkspaceSwitcher() {
  const { workspaces, workspace, setWorkspaceId } = useWorkspace();
  const navigate = useNavigate();
  return (
    <Dropdown.Root>
      <Dropdown.Trigger className="hidden max-w-48 items-center gap-2 rounded-full border border-border/70 bg-surface-2/50 py-1 pl-1 pr-2.5 text-left transition-colors hover:border-border-strong md:flex"
        aria-label="Switch workspace">
        <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-linear-to-br from-accent to-accent-2 text-[11px] font-semibold text-accent-fg">
          {workspace?.name?.[0]?.toUpperCase() ?? '·'}
        </span>
        <span className="truncate text-xs text-fg">{workspace?.name ?? 'Loading…'}</span>
        <ChevronsUpDown className="size-3.5 shrink-0 text-muted" />
      </Dropdown.Trigger>
      <Dropdown.Portal>
        <Dropdown.Content align="end" sideOffset={10} className="glass z-[60] w-64 rounded-2xl p-2 shadow-2xl animate-fade">
          <Dropdown.Label className="eyebrow px-2.5 pb-1.5 pt-1">Workspaces</Dropdown.Label>
          {workspaces.map((w) => (
            <Dropdown.Item key={w.id} onSelect={() => setWorkspaceId(w.id)}
              className={cn(itemClass, 'justify-between', w.id === workspace?.id && 'text-fg')}>
              <span className="truncate">{w.name}</span>
              <span className="text-[11px] text-muted">{w.myRole?.toLowerCase()}</span>
            </Dropdown.Item>
          ))}
          <Dropdown.Separator className="my-1.5 h-px bg-border" />
          <Dropdown.Item onSelect={() => navigate('/app/workspace?new=1')} className={itemClass}><Plus /> New workspace</Dropdown.Item>
        </Dropdown.Content>
      </Dropdown.Portal>
    </Dropdown.Root>
  );
}

function AccountMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const initials = user?.fullName.split(' ').map((p) => p[0]).slice(0, 2).join('') ?? '';
  return (
    <Dropdown.Root>
      <Dropdown.Trigger className="flex size-9 items-center justify-center rounded-full border border-border/80 bg-surface-3/70 text-xs font-semibold text-fg-2 transition-colors hover:border-accent/50 hover:text-fg"
        aria-label="Account and more">
        {initials}
      </Dropdown.Trigger>
      <Dropdown.Portal>
        <Dropdown.Content align="end" sideOffset={10} className="glass z-[60] w-64 rounded-2xl p-2 shadow-2xl animate-fade">
          <div className="px-2.5 pb-2 pt-1.5">
            <p className="truncate text-sm text-fg">{user?.fullName}</p>
            <p className="truncate text-xs text-muted">{user?.email}</p>
          </div>
          <Dropdown.Separator className="my-1 h-px bg-border" />
          <Dropdown.Item onSelect={() => navigate('/app/connections')} className={itemClass}><Database /> Connections</Dropdown.Item>
          <Dropdown.Item onSelect={() => navigate('/app/connections#upload')} className={itemClass}><Upload /> Upload a CSV</Dropdown.Item>
          <Dropdown.Item onSelect={() => navigate('/app/evaluation')} className={itemClass}><FlaskConical /> Accuracy evaluation</Dropdown.Item>
          {user?.systemRole === 'ADMIN' && (
            <Dropdown.Item onSelect={() => navigate('/app/admin')} className={itemClass}><Shield /> Administration</Dropdown.Item>
          )}
          <Dropdown.Item onSelect={() => navigate('/app/profile')} className={itemClass}><UserIcon /> Profile</Dropdown.Item>
          <Dropdown.Separator className="my-1 h-px bg-border" />
          <Dropdown.Item onSelect={() => { logout(); navigate('/login'); }} className={cn(itemClass, 'text-danger data-[highlighted]:text-danger [&_svg]:text-danger')}>
            <LogOut /> Sign out
          </Dropdown.Item>
        </Dropdown.Content>
      </Dropdown.Portal>
    </Dropdown.Root>
  );
}

export function AppShell() {
  const location = useLocation();
  const fullBleed = location.pathname.startsWith('/app/studio');
  const { user } = useAuth();
  const info = useQuery({ queryKey: ['system-info'], queryFn: () => api.get<SystemInfo>('/system/info'), staleTime: 300_000 });

  return (
    <div className="relative min-h-screen">
      <FloatingNav
        variant="app"
        right={<><WorkspaceSwitcher /><AccountMenu /></>}
        extra={
          <div className="grid grid-cols-2 gap-2 text-sm">
            <Link to="/app/connections" className="rounded-xl bg-surface-2 px-3 py-2 text-fg-2">Connections</Link>
            <Link to="/app/evaluation" className="rounded-xl bg-surface-2 px-3 py-2 text-fg-2">Evaluation</Link>
            <Link to="/app/profile" className="rounded-xl bg-surface-2 px-3 py-2 text-fg-2">Profile</Link>
            {user?.systemRole === 'ADMIN' && <Link to="/app/admin" className="rounded-xl bg-surface-2 px-3 py-2 text-fg-2">Admin</Link>}
          </div>
        }
      />
      <div className={cn('flex min-h-screen flex-col', fullBleed ? 'pt-[76px]' : 'pt-24')}>
        {info.data && !info.data.llmConfigured && !fullBleed && (
          <div className="mx-auto mb-2 w-full max-w-7xl px-4 sm:px-8">
            <div className="flex items-start gap-3 rounded-2xl border border-warning/25 bg-warning-soft/60 px-4 py-3 text-xs text-fg-2 backdrop-blur">
              <Sparkles className="mt-0.5 size-4 shrink-0 text-warning" />
              <p>
                <span className="font-semibold text-warning">AI model not connected.</span>{' '}
                Plain-English questions are paused until an administrator connects an AI model. Writing SQL, exploring schemas,
                charts and dashboards all work as usual.
              </p>
            </div>
          </div>
        )}
        <main className={cn('min-h-0 flex-1', !fullBleed && 'px-4 pb-16 sm:px-8')}>
          <div key={location.pathname} className={cn('animate-page', !fullBleed && 'mx-auto max-w-7xl', fullBleed && 'lg:h-[calc(100vh-76px)]')}>
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
