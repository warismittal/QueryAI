// TypeScript mirrors of the backend DTOs (com.queryai.*Dtos).

export type SystemRole = 'USER' | 'ADMIN';
export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'ANALYST' | 'VIEWER';
export type DatabaseType = 'POSTGRESQL' | 'MYSQL';
export type SslMode = 'DISABLE' | 'PREFER' | 'REQUIRE';
export type Visibility = 'PRIVATE' | 'WORKSPACE';
export type ValidationStatus = 'NOT_VALIDATED' | 'VALID' | 'WARNING' | 'INVALID';
export type ExecutionStatus = 'NOT_EXECUTED' | 'SUCCESS' | 'FAILED' | 'BLOCKED' | 'CLARIFICATION_NEEDED';
export type QuerySource = 'AI' | 'MANUAL' | 'SAVED_QUERY' | 'DASHBOARD' | 'RERUN' | 'EVALUATION';
export type ChartType = 'TABLE' | 'BAR' | 'LINE' | 'PIE' | 'SCATTER' | 'METRIC';
export type ColumnCategory = 'NUMBER' | 'TEXT' | 'DATE' | 'BOOLEAN' | 'OTHER';

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string>;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface User {
  id: number;
  email: string;
  fullName: string;
  jobTitle?: string;
  systemRole: SystemRole;
  enabled: boolean;
  createdAt: string;
  lastLoginAt?: string;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface Workspace {
  id: number;
  name: string;
  description?: string;
  ownerId: number;
  monthlyQueryQuota: number;
  myRole?: WorkspaceRole;
  memberCount: number;
  createdAt: string;
}

export interface Member {
  userId: number;
  email: string;
  fullName: string;
  role: WorkspaceRole;
  joinedAt: string;
}

export interface Usage {
  workspaceId: number;
  periodStart: string;
  aiQueriesUsed: number;
  totalQueries: number;
  quota: number;
  remaining: number;
}

export interface DataSource {
  id: number;
  workspaceId: number;
  name: string;
  dbType: DatabaseType;
  host: string;
  port: number;
  databaseName: string;
  schemaName?: string;
  effectiveSchema: string;
  username: string;
  hasPassword: boolean;
  sslMode: SslMode;
  restrictedAccess: boolean;
  maxRows: number;
  queryTimeoutSec: number;
  status: 'UNTESTED' | 'CONNECTED' | 'FAILED';
  lastTestedAt?: string;
  lastError?: string;
  serverVersion?: string;
  canQuery: boolean;
  canManage: boolean;
  createdAt: string;
  updatedAt: string;
  piiMaskingEnabled?: boolean;
}

export interface DiagnosticStep {
  name: string;
  status: 'OK' | 'FAILED' | 'WARNING' | 'SKIPPED';
  message: string;
  durationMs: number;
}

export interface ConnectionTestResult {
  success: boolean;
  serverVersion?: string;
  latencyMs: number;
  tableCount: number;
  availableSchemas: string[];
  steps: DiagnosticStep[];
  hint?: string;
}

export interface Grants {
  restrictedAccess: boolean;
  users: { userId: number; email: string; fullName: string }[];
}

export interface ForeignKeyMeta {
  name: string;
  columns: string[];
  referencedTable: string;
  referencedColumns: string[];
}

export interface IndexMeta {
  name: string;
  unique: boolean;
  columns: string[];
}

export interface ColumnView {
  name: string;
  dataType: string;
  nullable: boolean;
  defaultValue?: string;
  description?: string;
  primaryKey: boolean;
  references?: string;
  /** Personal-data type detected or configured for the column (e.g. EMAIL), absent when none. */
  pii?: PiiType;
  /** True when values are masked for members below workspace admin. */
  masked?: boolean;
}

export interface TableView {
  name: string;
  type: 'TABLE' | 'VIEW';
  description?: string;
  rowEstimate?: number;
  columns: ColumnView[];
  primaryKey: string[];
  foreignKeys: ForeignKeyMeta[];
  indexes: IndexMeta[];
}

export interface SchemaView {
  dataSourceId: number;
  dbType: DatabaseType;
  schema: string;
  refreshedAt: string;
  tableCount: number;
  tables: TableView[];
  relationships: { fromTable: string; fromColumns: string[]; toTable: string; toColumns: string[]; name: string }[];
}

export interface GlossaryTerm {
  id: number;
  dataSourceId: number;
  term: string;
  definition: string;
  sqlHint?: string;
  updatedAt: string;
}

export interface ResultColumn {
  name: string;
  sqlType: string;
  category: ColumnCategory;
}

export type Cell = string | number | boolean | null;

export interface QueryResult {
  columns: ResultColumn[];
  rows: Cell[][];
  rowCount: number;
  truncated: boolean;
  durationMs: number;
  maxRows: number;
  /** Result columns whose values were masked because they contain personal data. */
  maskedColumns?: string[];
}

export interface ValidationIssue {
  severity: 'ERROR' | 'WARNING' | 'INFO';
  code: string;
  message: string;
}

export interface SqlValidationResult {
  status: ValidationStatus;
  issues: ValidationIssue[];
  referencedTables: string[];
  parameters: string[];
  hasLimit: boolean;
  normalizedSql: string;
  databaseChecked: boolean;
  estimatedCost?: number;
  estimatedRows?: number;
}

export interface ChartRecommendation {
  type: ChartType;
  xColumn?: string;
  yColumns: string[];
  reason: string;
  alternatives: ChartType[];
}

export interface ExecuteResponse {
  queryRunId: number;
  status: ExecutionStatus;
  validation: SqlValidationResult;
  result?: QueryResult;
  error?: string;
  chart?: ChartRecommendation;
}

export interface IntentAnalysis {
  intentType: string;
  summary: string;
  metrics?: string[];
  dimensions?: string[];
  filters?: string[];
  timeRange?: string;
  keywords?: string[];
  needsClarification: boolean;
  clarificationQuestion?: string;
}

export interface Interpretation {
  summary: string;
  insights: string[];
  followUps: string[];
  generatedBy: 'ai' | 'system';
}

export interface Stage {
  name: string;
  status: 'OK' | 'FAILED' | 'SKIPPED';
  durationMs: number;
  detail?: string;
}

export type AnalysisStatus = 'ANSWERED' | 'GENERATED' | 'CLARIFICATION_NEEDED' | 'BLOCKED' | 'FAILED';

export interface AnalyzeResponse {
  conversationId: number;
  queryRunId: number;
  status: AnalysisStatus;
  question: string;
  intent?: IntentAnalysis;
  tablesUsed?: string[];
  sql?: string;
  sqlExplanation?: string;
  assumptions: string[];
  confidence?: number;
  clarificationQuestion?: string;
  validation?: SqlValidationResult;
  result?: QueryResult;
  interpretation?: Interpretation;
  chart?: ChartRecommendation;
  stages: Stage[];
  model?: string;
  repairAttempts: number;
  error?: string;
}

export interface Conversation {
  id: number;
  workspaceId: number;
  dataSourceId: number;
  title: string;
  messageCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationMessage {
  id: number;
  role: 'USER' | 'ASSISTANT';
  content: string;
  sql?: string;
  queryRunId?: number;
  metadata?: Record<string, unknown>;
  createdAt: string;
}

export interface QueryRun {
  id: number;
  workspaceId: number;
  dataSourceId?: number;
  dataSourceName?: string;
  userId?: number;
  conversationId?: number;
  source: QuerySource;
  question?: string;
  generatedSql?: string;
  finalSql?: string;
  validationStatus: ValidationStatus;
  executionStatus: ExecutionStatus;
  rowCount?: number;
  truncated: boolean;
  durationMs?: number;
  generationMs?: number;
  errorMessage?: string;
  explanation?: string;
  llmModel?: string;
  myFeedback?: 'UP' | 'DOWN';
  createdAt: string;
}

export interface SavedQuery {
  id: number;
  workspaceId: number;
  dataSourceId: number;
  dataSourceName?: string;
  ownerId?: number;
  ownerName?: string;
  name: string;
  description?: string;
  folder?: string;
  question?: string;
  sql: string;
  chartType?: string;
  visibility: Visibility;
  template: boolean;
  currentVersion: number;
  parameters: string[];
  canEdit: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SavedQueryVersion {
  version: number;
  sql: string;
  note?: string;
  createdBy?: number;
  createdAt: string;
}

export interface DashboardSummary {
  id: number;
  workspaceId: number;
  ownerId?: number;
  ownerName?: string;
  name: string;
  description?: string;
  visibility: Visibility;
  widgetCount: number;
  canEdit: boolean;
  updatedAt: string;
}

export interface Widget {
  id: number;
  savedQueryId: number;
  savedQueryName?: string;
  title: string;
  chartType: ChartType;
  xColumn?: string;
  yColumns: string[];
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface DashboardDetail {
  dashboard: DashboardSummary;
  widgets: Widget[];
}

export interface AuditEvent {
  id: number;
  workspaceId?: number;
  userId?: number;
  userEmail?: string;
  action: string;
  resourceType?: string;
  resourceId?: string;
  details?: Record<string, unknown>;
  ipAddress?: string;
  success: boolean;
  createdAt: string;
}

export interface Overview {
  workspaceId: number;
  measuredAt: string;
  windowDays: number;
  dataSourceCount: number;
  connectedDataSources: number;
  dataSources: { id: number; name: string; dbType: DatabaseType; status: string }[];
  queriesInWindow: number;
  executedInWindow: number;
  successRate?: number;
  avgExecutionMs?: number;
  savedQueryCount: number;
  dashboardCount: number;
  positiveFeedback: number;
  negativeFeedback: number;
  usage: Usage;
  activity: { day: string; total: number; succeeded: number; failed: number }[];
  recentQueries: QueryRun[];
  recentActivity: AuditEvent[];
  llmConfigured: boolean;
  llmDescription?: string;
}

export interface SystemInfo {
  version: string;
  llmConfigured: boolean;
  llmProvider?: string;
  supportedDatabases: DatabaseType[];
  demoEnabled: boolean;
  semanticSearch?: boolean;
  embeddingModel?: string;
  pgvector?: boolean;
  uploadsEnabled?: boolean;
  /** A shared starter workspace can be opened without an account (public deployments). */
  guestAccess?: boolean;
}

export type PiiType = 'EMAIL' | 'PHONE' | 'PERSON_NAME' | 'ADDRESS' | 'POSTAL_CODE' | 'DATE_OF_BIRTH' | 'NATIONAL_ID'
  | 'PAYMENT_CARD' | 'BANK_ACCOUNT' | 'IP_ADDRESS' | 'CREDENTIAL' | 'OTHER';

export interface PiiColumn {
  table: string;
  column: string;
  piiType: PiiType;
  label: string;
  masked: boolean;
  source: 'auto' | 'manual';
}

export interface PiiOverview {
  dataSourceId: number;
  maskingEnabled: boolean;
  youSeeRawValues: boolean;
  columns: PiiColumn[];
}

export interface SchemaSearchMatch {
  table: string;
  score: number;
  description?: string;
}

export interface SchemaSearchResponse {
  query: string;
  engine: 'pgvector' | 'in-app' | 'off';
  model?: string;
  matches: SchemaSearchMatch[];
}

export interface UploadColumn {
  name: string;
  header: string;
  type: 'BOOLEAN' | 'BIGINT' | 'NUMERIC' | 'DATE' | 'TIMESTAMP' | 'TEXT';
}

export interface UploadResult {
  id: number;
  dataSourceId: number;
  dataSourceName: string;
  schema: string;
  table: string;
  rowCount: number;
  columns: UploadColumn[];
  replaced: boolean;
}

export interface UploadedTable {
  id: number;
  dataSourceId: number;
  table: string;
  originalFile: string;
  rowCount: number;
  columnCount: number;
  sizeBytes: number;
  createdAt: string;
}

export interface EvaluationCase {
  id: number;
  dataSourceId: number;
  question: string;
  expectedSql: string;
  createdAt: string;
}

export interface EvaluationRun {
  id: number;
  dataSourceId: number;
  totalCases: number;
  passedCases: number;
  accuracy: number;
  llmModel?: string;
  results: {
    caseId: number;
    question: string;
    passed: boolean;
    generatedSql?: string;
    reason: string;
    expectedRows?: number;
    actualRows?: number;
    generationMs?: number;
  }[];
  createdAt: string;
}

export interface PlatformStats {
  users: number;
  enabledUsers: number;
  workspaces: number;
  dataSources: number;
  queriesLast24h: number;
  queriesLast30d: number;
  llmConfigured: boolean;
  llmDescription: string;
  measuredAt: string;
}
