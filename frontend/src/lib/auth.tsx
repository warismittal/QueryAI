import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, onUnauthorized, tokenStore } from './api';
import type { AuthResponse, User } from './types';

interface AuthState {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string, remember?: boolean) => Promise<User>;
  guest: () => Promise<User>;
  register: (fullName: string, email: string, password: string) => Promise<User>;
  logout: (reason?: string) => void;
  refreshUser: () => Promise<void>;
  sessionMessage: string | null;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState<boolean>(() => !!tokenStore.get());
  const [sessionMessage, setSessionMessage] = useState<string | null>(null);

  const logout = useCallback(
    (reason?: string) => {
      tokenStore.set(null);
      setUser(null);
      queryClient.clear();
      setSessionMessage(reason ?? null);
    },
    [queryClient],
  );

  useEffect(() => {
    onUnauthorized(() => logout('Your session expired. Please sign in again.'));
  }, [logout]);

  const refreshUser = useCallback(async () => {
    if (!tokenStore.get()) {
      setLoading(false);
      return;
    }
    try {
      setUser(await api.get<User>('/auth/me'));
    } catch {
      tokenStore.set(null);
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshUser();
  }, [refreshUser]);

  const accept = useCallback((res: AuthResponse, remember = true) => {
    tokenStore.set(res.token, remember);
    setUser(res.user);
    setSessionMessage(null);
    return res.user;
  }, []);

  const login = useCallback(
    async (email: string, password: string, remember = true) =>
      accept(await api.post<AuthResponse>('/auth/login', { email, password }), remember),
    [accept],
  );
  const guest = useCallback(async () => accept(await api.post<AuthResponse>('/auth/guest'), false), [accept]);
  const register = useCallback(
    async (fullName: string, email: string, password: string) =>
      accept(await api.post<AuthResponse>('/auth/register', { fullName, email, password })),
    [accept],
  );

  const value = useMemo(
    () => ({ user, loading, login, guest, register, logout, refreshUser, sessionMessage }),
    [user, loading, login, guest, register, logout, refreshUser, sessionMessage],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
