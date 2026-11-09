import { lazy, Suspense, type ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from '@/lib/auth';
import { WorkspaceProvider } from '@/lib/workspace';
import { AppShell } from '@/components/layout/AppShell';
import { Spinner } from '@/components/ui/feedback';
import Landing from '@/pages/Landing';
import { LoginPage, RegisterPage } from '@/pages/Auth';
import Home from '@/pages/Home';
import NotFound from '@/pages/NotFound';

const Studio = lazy(() => import('@/pages/Studio'));
const Connections = lazy(() => import('@/pages/Connections'));
const SchemaExplorer = lazy(() => import('@/pages/SchemaExplorer'));
const History = lazy(() => import('@/pages/History'));
const SavedQueries = lazy(() => import('@/pages/SavedQueries'));
const Dashboards = lazy(() => import('@/pages/Dashboards'));
const DashboardView = lazy(() => import('@/pages/DashboardView'));
const Evaluation = lazy(() => import('@/pages/Evaluation'));
const WorkspaceSettings = lazy(() => import('@/pages/WorkspaceSettings'));
const Profile = lazy(() => import('@/pages/Profile'));
const Admin = lazy(() => import('@/pages/Admin'));

function FullPageSpinner() {
  return (
    <div className="flex h-full min-h-[50vh] items-center justify-center">
      <Spinner className="size-6" />
    </div>
  );
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <FullPageSpinner />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <>{children}</>;
}

function RequireAdmin({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (user?.systemRole !== 'ADMIN') return <Navigate to="/app" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        path="/app"
        element={
          <RequireAuth>
            <WorkspaceProvider>
              <AppShell />
            </WorkspaceProvider>
          </RequireAuth>
        }
      >
        <Route index element={<Home />} />
        {[
          ['studio', <Studio />],
          ['connections', <Connections />],
          ['schema', <SchemaExplorer />],
          ['history', <History />],
          ['saved', <SavedQueries />],
          ['dashboards', <Dashboards />],
          ['dashboards/:id', <DashboardView />],
          ['evaluation', <Evaluation />],
          ['workspace', <WorkspaceSettings />],
          ['profile', <Profile />],
        ].map(([path, el]) => (
          <Route key={path as string} path={path as string} element={<Suspense fallback={<FullPageSpinner />}>{el}</Suspense>} />
        ))}
        <Route
          path="admin"
          element={
            <RequireAdmin>
              <Suspense fallback={<FullPageSpinner />}>
                <Admin />
              </Suspense>
            </RequireAdmin>
          }
        />
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
