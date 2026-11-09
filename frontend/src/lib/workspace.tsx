import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from './api';
import type { DataSource, Workspace, WorkspaceRole } from './types';

const KEY = 'queryai.workspace';
const DS_KEY = 'queryai.datasource';

interface WorkspaceState {
  workspaces: Workspace[];
  workspace: Workspace | null;
  workspaceId: number | null;
  role: WorkspaceRole | null;
  setWorkspaceId: (id: number) => void;
  loading: boolean;
  dataSources: DataSource[];
  dataSourcesLoading: boolean;
  dataSourceId: number | null;
  setDataSourceId: (id: number | null) => void;
  can: (minimum: WorkspaceRole) => boolean;
}

const rank: Record<WorkspaceRole, number> = { OWNER: 4, ADMIN: 3, ANALYST: 2, VIEWER: 1 };
const WorkspaceContext = createContext<WorkspaceState | null>(null);

function read(key: string): number | null {
  try {
    const v = localStorage.getItem(key);
    return v ? Number(v) : null;
  } catch {
    return null;
  }
}
function write(key: string, value: number | null) {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, String(value));
  } catch {
    /* ignore */
  }
}

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const [selected, setSelected] = useState<number | null>(() => read(KEY));
  const [dsSelected, setDsSelected] = useState<number | null>(() => read(DS_KEY));

  const workspacesQuery = useQuery({ queryKey: ['workspaces'], queryFn: () => api.get<Workspace[]>('/workspaces') });
  const workspaces = useMemo(() => workspacesQuery.data ?? [], [workspacesQuery.data]);
  const workspace = workspaces.find((w) => w.id === selected) ?? workspaces[0] ?? null;
  const workspaceId = workspace?.id ?? null;

  const dsQuery = useQuery({
    queryKey: ['datasources', workspaceId],
    queryFn: () => api.get<DataSource[]>('/datasources', { workspaceId: workspaceId! }),
    enabled: workspaceId !== null,
  });
  const dataSources = useMemo(() => dsQuery.data ?? [], [dsQuery.data]);
  const dataSourceId = dataSources.find((d) => d.id === dsSelected)?.id ?? dataSources[0]?.id ?? null;

  useEffect(() => {
    if (workspace && workspace.id !== selected) setSelected(workspace.id);
  }, [workspace, selected]);

  const value = useMemo<WorkspaceState>(
    () => ({
      workspaces,
      workspace,
      workspaceId,
      role: workspace?.myRole ?? null,
      setWorkspaceId: (id) => {
        setSelected(id);
        write(KEY, id);
        setDsSelected(null);
        write(DS_KEY, null);
      },
      loading: workspacesQuery.isLoading,
      dataSources,
      dataSourcesLoading: dsQuery.isLoading,
      dataSourceId,
      setDataSourceId: (id) => {
        setDsSelected(id);
        write(DS_KEY, id);
      },
      can: (minimum) => !!workspace?.myRole && rank[workspace.myRole] >= rank[minimum],
    }),
    [workspaces, workspace, workspaceId, workspacesQuery.isLoading, dataSources, dsQuery.isLoading, dataSourceId],
  );
  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
}

export function useWorkspace() {
  const ctx = useContext(WorkspaceContext);
  if (!ctx) throw new Error('useWorkspace must be used inside WorkspaceProvider');
  return ctx;
}
