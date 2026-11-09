import type { ReactElement, ReactNode } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi } from 'vitest';
import { AuthProvider } from '@/lib/auth';
import { WorkspaceProvider } from '@/lib/workspace';
import { ConfirmProvider } from '@/components/ui/confirm';

/** A canned API answer. A plain value means 200 with that JSON body. */
export interface Reply {
  status: number;
  body?: unknown;
}

export const reply = (status: number, body?: unknown): Reply => ({ status, body });

export type Handler = unknown | Reply | ((ctx: { url: URL; body: unknown; init?: RequestInit }) => unknown);

export interface ApiCall {
  method: string;
  path: string;
  url: URL;
  body: unknown;
  headers: Record<string, string>;
}

function isReply(v: unknown): v is Reply {
  return !!v && typeof v === 'object' && 'status' in v && typeof (v as Reply).status === 'number' && Object.keys(v).every((k) => k === 'status' || k === 'body');
}

/**
 * Replaces fetch with a tiny router over the QueryAI API: keys are "METHOD /path" (without /api/v1, without query).
 * Unknown routes answer 404 so a test fails loudly instead of hanging. Every call is recorded.
 */
export function mockApi(routes: Record<string, Handler>) {
  const calls: ApiCall[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://localhost');
    const method = (init?.method ?? 'GET').toUpperCase();
    const path = url.pathname.replace(/^\/api\/v1/, '');
    const raw = init?.body;
    const body = raw instanceof FormData ? raw : typeof raw === 'string' ? JSON.parse(raw) : raw;
    calls.push({ method, path, url, body, headers: (init?.headers ?? {}) as Record<string, string> });

    const handler = routes[`${method} ${path}`];
    let result: unknown = handler === undefined ? reply(404, { code: 'NOT_FOUND', message: `No mock for ${method} ${path}` }) : handler;
    if (typeof result === 'function') result = await (result as (ctx: { url: URL; body: unknown; init?: RequestInit }) => unknown)({ url, body, init });
    const r: Reply = isReply(result) ? result : { status: 200, body: result };
    const text = r.body === undefined ? '' : JSON.stringify(r.body);
    return {
      ok: r.status >= 200 && r.status < 300,
      status: r.status,
      statusText: r.status === 200 ? 'OK' : 'Error',
      headers: { get: () => null },
      text: async () => text,
    } as unknown as Response;
  });
  vi.stubGlobal('fetch', fetchMock);
  return {
    fetch: fetchMock,
    calls,
    /** Calls to "METHOD /path". */
    callsTo: (key: string) => calls.filter((c) => `${c.method} ${c.path}` === key),
  };
}

/** Shows the current location so tests can assert on navigation. */
export function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname + location.search}</div>;
}

export function newQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } });
}

/**
 * Renders inside the app's real providers. {@code routes} lets a test mount the page under a path and add
 * destination pages to assert on navigation.
 */
export function renderApp(ui: ReactElement, opts: {
  route?: string;
  path?: string;
  auth?: boolean;
  workspace?: boolean;
  routes?: { path: string; element: ReactNode }[];
} = {}) {
  const { route = '/', path = '*', auth = false, workspace = false, routes = [] } = opts;
  const client = newQueryClient();
  let content: ReactNode = (
    <Routes>
      <Route path={path} element={ui} />
      {routes.map((r) => <Route key={r.path} path={r.path} element={r.element} />)}
    </Routes>
  );
  if (workspace) content = <WorkspaceProvider>{content}</WorkspaceProvider>;
  content = <ConfirmProvider>{content}</ConfirmProvider>;
  if (auth) content = <AuthProvider>{content}</AuthProvider>;
  const utils = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>
        {content}
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...utils, client };
}
