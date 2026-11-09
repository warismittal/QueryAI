import type { ApiErrorBody } from './types';

const TOKEN_KEY = 'queryai.token';
const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';

export class ApiError extends Error {
  status: number;
  code: string;
  fieldErrors?: Record<string, string>;

  constructor(status: number, code: string, message: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

/** The session token: kept across browser restarts when "keep me signed in" is on, otherwise only for this tab. */
export const tokenStore = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token: string | null, remember = true) {
    try {
      localStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(TOKEN_KEY);
      if (token) (remember ? localStorage : sessionStorage).setItem(TOKEN_KEY, token);
    } catch {
      /* storage unavailable: session lasts until reload */
    }
  },
};

const PII_PREVIEW_KEY = 'queryai.piiPreview';
const piiListeners = new Set<() => void>();

/** Admins can preview what analysts see: results come back with personal data masked. */
export const piiPreview = {
  get(): boolean {
    try {
      return localStorage.getItem(PII_PREVIEW_KEY) === 'masked';
    } catch {
      return false;
    }
  },
  set(on: boolean) {
    try {
      if (on) localStorage.setItem(PII_PREVIEW_KEY, 'masked');
      else localStorage.removeItem(PII_PREVIEW_KEY);
    } catch {
      /* storage unavailable */
    }
    piiListeners.forEach((l) => l());
  },
  subscribe(listener: () => void) {
    piiListeners.add(listener);
    return () => { piiListeners.delete(listener); };
  },
};

let unauthorizedHandler: (() => void) | null = null;
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler;
}

type Query = Record<string, string | number | boolean | undefined | null>;

function buildUrl(path: string, query?: Query) {
  const params = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') params.set(k, String(v));
  });
  const qs = params.toString();
  return `${API_BASE}/api/v1${path}${qs ? `?${qs}` : ''}`;
}

async function request<T>(method: string, path: string, body?: unknown, query?: Query): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  const multipart = typeof FormData !== 'undefined' && body instanceof FormData;
  if (body !== undefined && !multipart) headers['Content-Type'] = 'application/json';
  if (piiPreview.get()) headers['X-QueryAI-Pii-Preview'] = 'masked';

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : multipart ? (body as FormData) : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'NETWORK', 'Cannot reach the QueryAI server. Check that the backend is running.');
  }

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  const data = text ? safeJson(text) : undefined;

  if (!response.ok) {
    const err = (data ?? {}) as Partial<ApiErrorBody>;
    if (response.status === 401 && token) unauthorizedHandler?.();
    const retry = response.headers.get('Retry-After');
    throw new ApiError(
      response.status,
      err.code ?? 'HTTP_' + response.status,
      err.message ?? (retry ? `Rate limited, retry in ${retry}s` : response.statusText || 'Request failed'),
      err.fieldErrors,
    );
  }
  return data as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export const api = {
  get: <T>(path: string, query?: Query) => request<T>('GET', path, undefined, query),
  post: <T>(path: string, body?: unknown, query?: Query) => request<T>('POST', path, body ?? {}, query),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body ?? {}),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body ?? {}),
  delete: <T = void>(path: string, query?: Query) => request<T>('DELETE', path, undefined, query),
  upload: <T>(path: string, form: FormData) => request<T>('POST', path, form),
};

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong';
}
