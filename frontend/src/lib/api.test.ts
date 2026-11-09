import { afterEach, describe, expect, it, vi } from 'vitest';
import { mockApi, reply } from '@/test/utils';
import { api, ApiError, errorMessage, onUnauthorized, piiPreview, tokenStore } from './api';

afterEach(() => onUnauthorized(() => {}));

describe('api client', () => {
  it('prefixes /api/v1, encodes query parameters and skips empty ones', async () => {
    const m = mockApi({ 'GET /history': { content: [] } });
    await api.get('/history', { workspaceId: 3, q: 'top customers', status: undefined, source: '' });
    const url = m.calls[0].url;
    expect(url.pathname).toBe('/api/v1/history');
    expect(url.searchParams.get('workspaceId')).toBe('3');
    expect(url.searchParams.get('q')).toBe('top customers');
    expect(url.searchParams.has('status')).toBe(false);
    expect(url.searchParams.has('source')).toBe(false);
  });

  it('sends the bearer token and JSON bodies', async () => {
    tokenStore.set('jwt-1');
    const m = mockApi({ 'POST /sql/execute': { status: 'SUCCESS' } });
    await api.post('/sql/execute', { sql: 'SELECT 1' });
    expect(m.calls[0].headers.Authorization).toBe('Bearer jwt-1');
    expect(m.calls[0].headers['Content-Type']).toBe('application/json');
    expect(m.calls[0].body).toEqual({ sql: 'SELECT 1' });
  });

  it('asks for masked results while an admin previews the analyst view', async () => {
    const m = mockApi({ 'GET /x': {} });
    piiPreview.set(true);
    await api.get('/x');
    piiPreview.set(false);
    await api.get('/x');
    expect(m.calls[0].headers['X-QueryAI-Pii-Preview']).toBe('masked');
    expect(m.calls[1].headers['X-QueryAI-Pii-Preview']).toBeUndefined();
  });

  it('notifies preview subscribers', () => {
    const listener = vi.fn();
    const unsubscribe = piiPreview.subscribe(listener);
    piiPreview.set(true);
    unsubscribe();
    piiPreview.set(false);
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('turns error responses into ApiError with code, message and field errors', async () => {
    mockApi({ 'POST /auth/register': reply(400, { code: 'VALIDATION_FAILED', message: 'Invalid input', fieldErrors: { email: 'Bad email' } }) });
    const error = await api.post('/auth/register', {}).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, code: 'VALIDATION_FAILED', message: 'Invalid input', fieldErrors: { email: 'Bad email' } });
    expect(errorMessage(error)).toBe('Invalid input');
  });

  it('reports the quota error from the server verbatim', async () => {
    mockApi({ 'POST /analytics/analyze': reply(429, { code: 'RATE_LIMITED', message: 'This workspace has used its monthly quota of 2 AI queries.' }) });
    const error = await api.post('/analytics/analyze', {}).catch((e) => e);
    expect(error).toMatchObject({ status: 429, code: 'RATE_LIMITED' });
    expect(errorMessage(error)).toContain('monthly quota');
  });

  it('signals an expired session only when a token was sent', async () => {
    const handler = vi.fn();
    onUnauthorized(handler);
    mockApi({ 'GET /auth/me': reply(401, { code: 'UNAUTHORIZED', message: 'Expired' }) });
    await api.get('/auth/me').catch(() => {});
    expect(handler).not.toHaveBeenCalled();

    tokenStore.set('old');
    await api.get('/auth/me').catch(() => {});
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('handles empty 204 responses', async () => {
    mockApi({ 'DELETE /uploads/3': reply(204) });
    await expect(api.delete('/uploads/3')).resolves.toBeUndefined();
  });

  it('explains when the server cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch'); }));
    const error = await api.get('/system/info').catch((e) => e);
    expect(error).toMatchObject({ status: 0, code: 'NETWORK' });
    expect((error as ApiError).message).toMatch(/Cannot reach the QueryAI server/);
  });

  it('sends uploads as multipart without a JSON content type', async () => {
    const m = mockApi({ 'POST /uploads': { id: 1 } });
    const form = new FormData();
    form.append('workspaceId', '1');
    await api.upload('/uploads', form);
    expect(m.calls[0].body).toBe(form);
    expect(m.calls[0].headers['Content-Type']).toBeUndefined();
  });
});
