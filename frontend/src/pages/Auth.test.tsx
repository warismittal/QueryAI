import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { mockApi, renderApp, reply } from '@/test/utils';
import { systemInfo, user } from '@/test/fixtures';
import { LoginPage, RegisterPage } from './Auth';

// the WebGL library scene is not available in jsdom
vi.mock('@/components/landing/SceneBackdrop', () => ({ SceneBackdrop: () => null }));

const appRoutes = [
  { path: '/app', element: <p>Workspace home</p> },
  { path: '/app/connections', element: <p>Connections page</p> },
];

describe('LoginPage', () => {
  it('signs in, stores the session and opens the workspace', async () => {
    const api = mockApi({
      'GET /system/info': systemInfo(),
      'POST /auth/login': { token: 'jwt-123', user },
    });
    const u = userEvent.setup();
    renderApp(<LoginPage />, { route: '/login', path: '/login', auth: true, routes: appRoutes });

    expect(screen.getByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(screen.queryByText(/demo/i)).not.toBeInTheDocument();
    await u.type(screen.getByLabelText('Email'), 'ada@example.com');
    await u.type(screen.getByLabelText('Password'), 'Passw0rd!');
    await u.click(screen.getByRole('button', { name: /Sign in/ }));

    expect(await screen.findByText('Workspace home')).toBeInTheDocument();
    expect(api.callsTo('POST /auth/login')[0].body).toEqual({ email: 'ada@example.com', password: 'Passw0rd!' });
    expect(localStorage.getItem('queryai.token')).toBe('jwt-123');
  });

  it('validates the form before calling the server', async () => {
    const api = mockApi({ 'GET /system/info': systemInfo() });
    const u = userEvent.setup();
    renderApp(<LoginPage />, { route: '/login', path: '/login', auth: true });

    await u.click(screen.getByRole('button', { name: /Sign in/ }));
    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(api.callsTo('POST /auth/login')).toHaveLength(0);
  });

  it('explains wrong credentials without leaking which part was wrong', async () => {
    mockApi({
      'GET /system/info': systemInfo(),
      'POST /auth/login': reply(401, { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' }),
    });
    const u = userEvent.setup();
    renderApp(<LoginPage />, { route: '/login', path: '/login', auth: true });
    await u.type(screen.getByLabelText('Email'), 'ada@example.com');
    await u.type(screen.getByLabelText('Password'), 'wrong-pass1');
    await u.click(screen.getByRole('button', { name: /Sign in/ }));
    expect(await screen.findByText('Incorrect email or password.')).toBeInTheDocument();
    expect(localStorage.getItem('queryai.token')).toBeNull();
  });

  it('opens the shared workspace as a guest when the server allows it', async () => {
    const api = mockApi({ 'GET /system/info': systemInfo({ guestAccess: true }), 'POST /auth/guest': { token: 'jwt-guest', user } });
    const u = userEvent.setup();
    renderApp(<LoginPage />, { route: '/login', path: '/login', auth: true, routes: appRoutes });
    await u.click(await screen.findByRole('button', { name: 'Continue as guest' }));
    expect(await screen.findByText('Workspace home')).toBeInTheDocument();
    expect(api.callsTo('POST /auth/guest')).toHaveLength(1);
    // guest sessions last only for the tab
    expect(sessionStorage.getItem('queryai.token')).toBe('jwt-guest');
    expect(localStorage.getItem('queryai.token')).toBeNull();
  });

  it('shows no guest option on private installations', async () => {
    const api = mockApi({ 'GET /system/info': systemInfo() });
    renderApp(<LoginPage />, { route: '/login', path: '/login', auth: true });
    await waitFor(() => expect(api.callsTo('GET /system/info')).toHaveLength(1));
    expect(screen.queryByRole('button', { name: 'Continue as guest' })).not.toBeInTheDocument();
  });

  it('keeps the session only for the tab when "keep me signed in" is off', async () => {
    mockApi({ 'GET /system/info': systemInfo(), 'POST /auth/login': { token: 'jwt-tab', user } });
    const u = userEvent.setup();
    renderApp(<LoginPage />, { route: '/login', path: '/login', auth: true, routes: appRoutes });
    await u.type(screen.getByLabelText('Email'), 'ada@example.com');
    await u.type(screen.getByLabelText('Password'), 'Passw0rd!');
    await u.click(screen.getByLabelText('Keep me signed in'));
    await u.click(screen.getByRole('button', { name: /Sign in/ }));
    expect(await screen.findByText('Workspace home')).toBeInTheDocument();
    expect(sessionStorage.getItem('queryai.token')).toBe('jwt-tab');
    expect(localStorage.getItem('queryai.token')).toBeNull();
  });

  it('can reveal the password while typing', async () => {
    mockApi({ 'GET /system/info': systemInfo() });
    const u = userEvent.setup();
    renderApp(<LoginPage />, { route: '/login', path: '/login', auth: true });
    const field = screen.getByLabelText('Password');
    expect(field).toHaveAttribute('type', 'password');
    await u.click(screen.getByRole('button', { name: 'Show password' }));
    expect(field).toHaveAttribute('type', 'text');
  });

  it('links to registration', () => {
    mockApi({ 'GET /system/info': systemInfo() });
    renderApp(<LoginPage />, { route: '/login', path: '/login', auth: true });
    expect(screen.getByRole('link', { name: 'Create an account' })).toHaveAttribute('href', '/register');
  });
});

describe('RegisterPage', () => {
  async function fill(u: ReturnType<typeof userEvent.setup>, values: { name?: string; email?: string; password?: string; confirm?: string }) {
    if (values.name) await u.type(screen.getByLabelText('Full name'), values.name);
    if (values.email) await u.type(screen.getByLabelText('Work email'), values.email);
    if (values.password) await u.type(screen.getByLabelText('Password'), values.password);
    if (values.confirm) await u.type(screen.getByLabelText('Confirm password'), values.confirm);
    await u.click(screen.getByRole('button', { name: /Create account/ }));
  }

  it('creates the account and goes straight to connecting a database', async () => {
    const api = mockApi({ 'POST /auth/register': { token: 'jwt-new', user } });
    const u = userEvent.setup();
    renderApp(<RegisterPage />, { route: '/register', path: '/register', auth: true, routes: appRoutes });
    await fill(u, { name: 'Ada Lovelace', email: 'ada@example.com', password: 'Passw0rd1', confirm: 'Passw0rd1' });

    expect(await screen.findByText('Connections page')).toBeInTheDocument();
    expect(api.callsTo('POST /auth/register')[0].body).toEqual({ fullName: 'Ada Lovelace', email: 'ada@example.com', password: 'Passw0rd1' });
  });

  it('enforces the password rule and the confirmation', async () => {
    const api = mockApi({});
    const u = userEvent.setup();
    renderApp(<RegisterPage />, { route: '/register', path: '/register', auth: true });
    await fill(u, { name: 'Ada', email: 'ada@example.com', password: 'short', confirm: 'different' });
    expect(await screen.findByText('At least 8 characters')).toBeInTheDocument();

    await u.clear(screen.getByLabelText('Password'));
    await u.type(screen.getByLabelText('Password'), 'longenough');
    await u.click(screen.getByRole('button', { name: /Create account/ }));
    expect(await screen.findByText('Include at least one digit')).toBeInTheDocument();

    await u.clear(screen.getByLabelText('Password'));
    await u.type(screen.getByLabelText('Password'), 'Passw0rd1');
    await u.click(screen.getByRole('button', { name: /Create account/ }));
    expect(await screen.findByText('Passwords do not match')).toBeInTheDocument();
    expect(api.callsTo('POST /auth/register')).toHaveLength(0);
  });

  it('shows field errors returned by the server', async () => {
    mockApi({
      'POST /auth/register': reply(409, { code: 'EMAIL_TAKEN', message: 'An account with this email already exists',
        fieldErrors: { email: 'Already registered' } }),
    });
    const u = userEvent.setup();
    renderApp(<RegisterPage />, { route: '/register', path: '/register', auth: true });
    await fill(u, { name: 'Ada Lovelace', email: 'ada@example.com', password: 'Passw0rd1', confirm: 'Passw0rd1' });
    expect(await screen.findByText('Already registered')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('An account with this email already exists')).toBeInTheDocument());
  });
});
