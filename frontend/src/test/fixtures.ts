import type { AnalyzeResponse, DataSource, QueryResult, SchemaView, SystemInfo, User, Workspace } from '@/lib/types';

export const user: User = {
  id: 7, email: 'ada@example.com', fullName: 'Ada Lovelace', systemRole: 'USER', enabled: true, createdAt: '2026-01-01T00:00:00Z',
};

export const systemInfo = (overrides: Partial<SystemInfo> = {}): SystemInfo => ({
  version: '2.0.0', llmConfigured: true, supportedDatabases: ['POSTGRESQL', 'MYSQL'], demoEnabled: false,
  semanticSearch: true, pgvector: true, uploadsEnabled: true, guestAccess: false, ...overrides,
});

export const workspace = (overrides: Partial<Workspace> = {}): Workspace => ({
  id: 1, name: 'Acme', ownerId: 7, monthlyQueryQuota: 1000, myRole: 'OWNER', memberCount: 1, createdAt: '2026-01-01T00:00:00Z',
  ...overrides,
});

export const dataSource = (overrides: Partial<DataSource> = {}): DataSource => ({
  id: 5, workspaceId: 1, name: 'Shop', dbType: 'POSTGRESQL', host: 'db', port: 5432, databaseName: 'shop', schemaName: 'public',
  effectiveSchema: 'public', username: 'reader', hasPassword: true, sslMode: 'DISABLE', restrictedAccess: false, maxRows: 1000,
  queryTimeoutSec: 30, status: 'CONNECTED', canQuery: true, canManage: true, createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z', piiMaskingEnabled: true, ...overrides,
});

const column = (name: string, dataType = 'text') => ({ name, dataType, nullable: false, primaryKey: name === 'id' });

export const schema: SchemaView = {
  dataSourceId: 5, dbType: 'POSTGRESQL', schema: 'public', refreshedAt: '2026-01-01T00:00:00Z', tableCount: 2,
  tables: [
    { name: 'customers', type: 'TABLE', columns: [column('id', 'integer'), column('name'), column('email')], primaryKey: ['id'], foreignKeys: [], indexes: [] },
    { name: 'orders', type: 'TABLE', columns: [column('id', 'integer'), column('customer_id', 'integer'), column('total_amount', 'numeric')], primaryKey: ['id'], foreignKeys: [], indexes: [] },
  ],
  relationships: [],
} as SchemaView;

export const spendResult: QueryResult = {
  columns: [
    { name: 'name', sqlType: 'varchar', category: 'TEXT' },
    { name: 'spending', sqlType: 'numeric', category: 'NUMBER' },
  ],
  rows: [['Grace', 300], ['Ada', 200.5]],
  rowCount: 2, truncated: false, durationMs: 14, maxRows: 1000, maskedColumns: [],
} as QueryResult;

export const SPEND_SQL = 'SELECT c.name, SUM(o.total_amount) AS spending FROM customers c JOIN orders o ON o.customer_id = c.id GROUP BY c.name ORDER BY spending DESC';

export const analysis = (overrides: Partial<AnalyzeResponse> = {}): AnalyzeResponse => ({
  conversationId: 41, queryRunId: 99, status: 'ANSWERED', question: 'Who spends the most?', sql: SPEND_SQL,
  sqlExplanation: 'Sums order totals per customer', assumptions: [], confidence: 0.9, result: spendResult,
  interpretation: { summary: 'Grace is your most valuable customer.', insights: [], followUps: ['And last year?'], generatedBy: 'ai' },
  stages: [{ name: 'Intent analysis', status: 'OK', durationMs: 5 }], repairAttempts: 0, ...overrides,
} as AnalyzeResponse);
