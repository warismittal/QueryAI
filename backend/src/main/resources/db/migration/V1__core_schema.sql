-- QueryAI application database schema (PostgreSQL)
-- Application persistence only. Connected business databases are never stored here.

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    job_title       VARCHAR(120),
    system_role     VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (system_role IN ('USER', 'ADMIN')),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE workspaces (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(120) NOT NULL,
    description          VARCHAR(500),
    owner_id             BIGINT       NOT NULL REFERENCES users (id),
    monthly_query_quota  INTEGER      NOT NULL DEFAULT 1000 CHECK (monthly_query_quota >= 0),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE workspace_members (
    id            BIGSERIAL PRIMARY KEY,
    workspace_id  BIGINT      NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role          VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'ANALYST', 'VIEWER')),
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_workspace_member UNIQUE (workspace_id, user_id)
);
CREATE INDEX ix_workspace_members_user ON workspace_members (user_id);

CREATE TABLE data_sources (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT       NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name                VARCHAR(120) NOT NULL,
    db_type             VARCHAR(20)  NOT NULL CHECK (db_type IN ('POSTGRESQL', 'MYSQL')),
    host                VARCHAR(255) NOT NULL,
    port                INTEGER      NOT NULL CHECK (port BETWEEN 1 AND 65535),
    database_name       VARCHAR(128) NOT NULL,
    schema_name         VARCHAR(128),
    username            VARCHAR(128) NOT NULL,
    password_encrypted  TEXT         NOT NULL,
    ssl_mode            VARCHAR(20)  NOT NULL DEFAULT 'DISABLE' CHECK (ssl_mode IN ('DISABLE', 'PREFER', 'REQUIRE')),
    restricted_access   BOOLEAN      NOT NULL DEFAULT FALSE,
    max_rows            INTEGER      NOT NULL DEFAULT 5000 CHECK (max_rows BETWEEN 1 AND 50000),
    query_timeout_sec   INTEGER      NOT NULL DEFAULT 30 CHECK (query_timeout_sec BETWEEN 1 AND 600),
    status              VARCHAR(20)  NOT NULL DEFAULT 'UNTESTED' CHECK (status IN ('UNTESTED', 'CONNECTED', 'FAILED')),
    last_tested_at      TIMESTAMPTZ,
    last_error          TEXT,
    server_version      VARCHAR(255),
    created_by          BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_data_source_name UNIQUE (workspace_id, name)
);

-- Explicit query grants used when a data source is marked restricted_access
CREATE TABLE data_source_grants (
    id              BIGSERIAL PRIMARY KEY,
    data_source_id  BIGINT      NOT NULL REFERENCES data_sources (id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    granted_by      BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_data_source_grant UNIQUE (data_source_id, user_id)
);

CREATE TABLE schema_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    data_source_id  BIGINT      NOT NULL UNIQUE REFERENCES data_sources (id) ON DELETE CASCADE,
    snapshot        JSONB       NOT NULL,
    table_count     INTEGER     NOT NULL,
    refreshed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE column_annotations (
    id              BIGSERIAL PRIMARY KEY,
    data_source_id  BIGINT       NOT NULL REFERENCES data_sources (id) ON DELETE CASCADE,
    table_name      VARCHAR(128) NOT NULL,
    column_name     VARCHAR(128) NOT NULL DEFAULT '',
    description     VARCHAR(1000) NOT NULL,
    updated_by      BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_column_annotation UNIQUE (data_source_id, table_name, column_name)
);

CREATE TABLE glossary_terms (
    id              BIGSERIAL PRIMARY KEY,
    data_source_id  BIGINT        NOT NULL REFERENCES data_sources (id) ON DELETE CASCADE,
    term            VARCHAR(120)  NOT NULL,
    definition      VARCHAR(2000) NOT NULL,
    sql_hint        VARCHAR(2000),
    created_by      BIGINT        REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_glossary_term UNIQUE (data_source_id, term)
);

CREATE TABLE conversations (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT       NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    data_source_id  BIGINT       NOT NULL REFERENCES data_sources (id) ON DELETE CASCADE,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_conversations_user ON conversations (user_id, workspace_id, updated_at DESC);

CREATE TABLE query_runs (
    id                 BIGSERIAL PRIMARY KEY,
    workspace_id       BIGINT       NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    data_source_id     BIGINT       REFERENCES data_sources (id) ON DELETE SET NULL,
    user_id            BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    conversation_id    BIGINT       REFERENCES conversations (id) ON DELETE SET NULL,
    source             VARCHAR(20)  NOT NULL CHECK (source IN ('AI', 'MANUAL', 'SAVED_QUERY', 'DASHBOARD', 'RERUN', 'EVALUATION')),
    question           TEXT,
    generated_sql      TEXT,
    final_sql          TEXT,
    validation_status  VARCHAR(20)  NOT NULL DEFAULT 'NOT_VALIDATED' CHECK (validation_status IN ('NOT_VALIDATED', 'VALID', 'WARNING', 'INVALID')),
    execution_status   VARCHAR(20)  NOT NULL DEFAULT 'NOT_EXECUTED' CHECK (execution_status IN ('NOT_EXECUTED', 'SUCCESS', 'FAILED', 'BLOCKED', 'CLARIFICATION_NEEDED')),
    row_count          INTEGER,
    truncated          BOOLEAN      NOT NULL DEFAULT FALSE,
    duration_ms        BIGINT,
    generation_ms      BIGINT,
    error_message      TEXT,
    explanation        TEXT,
    llm_model          VARCHAR(120),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_query_runs_workspace_created ON query_runs (workspace_id, created_at DESC);
CREATE INDEX ix_query_runs_user ON query_runs (user_id, created_at DESC);

CREATE TABLE conversation_messages (
    id               BIGSERIAL PRIMARY KEY,
    conversation_id  BIGINT      NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content          TEXT        NOT NULL,
    sql_text         TEXT,
    query_run_id     BIGINT      REFERENCES query_runs (id) ON DELETE SET NULL,
    metadata         JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_conversation_messages_conv ON conversation_messages (conversation_id, created_at);

CREATE TABLE query_feedback (
    id            BIGSERIAL PRIMARY KEY,
    query_run_id  BIGINT      NOT NULL REFERENCES query_runs (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating        VARCHAR(10) NOT NULL CHECK (rating IN ('UP', 'DOWN')),
    comment       VARCHAR(2000),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_query_feedback UNIQUE (query_run_id, user_id)
);

CREATE TABLE saved_queries (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT       NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    data_source_id   BIGINT       NOT NULL REFERENCES data_sources (id) ON DELETE CASCADE,
    owner_id         BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    name             VARCHAR(160) NOT NULL,
    description      VARCHAR(2000),
    folder           VARCHAR(120),
    question         TEXT,
    sql_text         TEXT         NOT NULL,
    chart_type       VARCHAR(20),
    visibility       VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE', 'WORKSPACE')),
    is_template      BOOLEAN      NOT NULL DEFAULT FALSE,
    current_version  INTEGER      NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_saved_queries_workspace ON saved_queries (workspace_id, updated_at DESC);

CREATE TABLE saved_query_versions (
    id               BIGSERIAL PRIMARY KEY,
    saved_query_id   BIGINT      NOT NULL REFERENCES saved_queries (id) ON DELETE CASCADE,
    version          INTEGER     NOT NULL,
    sql_text         TEXT        NOT NULL,
    note             VARCHAR(500),
    created_by       BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_saved_query_version UNIQUE (saved_query_id, version)
);

CREATE TABLE dashboards (
    id            BIGSERIAL PRIMARY KEY,
    workspace_id  BIGINT       NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    owner_id      BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    name          VARCHAR(160) NOT NULL,
    description   VARCHAR(2000),
    visibility    VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE', 'WORKSPACE')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE dashboard_widgets (
    id              BIGSERIAL PRIMARY KEY,
    dashboard_id    BIGINT       NOT NULL REFERENCES dashboards (id) ON DELETE CASCADE,
    saved_query_id  BIGINT       NOT NULL REFERENCES saved_queries (id) ON DELETE CASCADE,
    title           VARCHAR(160) NOT NULL,
    chart_type      VARCHAR(20)  NOT NULL CHECK (chart_type IN ('TABLE', 'BAR', 'LINE', 'PIE', 'SCATTER', 'METRIC')),
    x_column        VARCHAR(128),
    y_columns       VARCHAR(1000),
    pos_x           INTEGER      NOT NULL DEFAULT 0,
    pos_y           INTEGER      NOT NULL DEFAULT 0,
    width           INTEGER      NOT NULL DEFAULT 6,
    height          INTEGER      NOT NULL DEFAULT 4,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE audit_events (
    id             BIGSERIAL PRIMARY KEY,
    workspace_id   BIGINT       REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id        BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    user_email     VARCHAR(255),
    action         VARCHAR(60)  NOT NULL,
    resource_type  VARCHAR(60),
    resource_id    VARCHAR(60),
    details        JSONB,
    ip_address     VARCHAR(64),
    success        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_events_workspace ON audit_events (workspace_id, created_at DESC);
CREATE INDEX ix_audit_events_action ON audit_events (action, created_at DESC);

CREATE TABLE evaluation_cases (
    id              BIGSERIAL PRIMARY KEY,
    data_source_id  BIGINT      NOT NULL REFERENCES data_sources (id) ON DELETE CASCADE,
    question        TEXT        NOT NULL,
    expected_sql    TEXT        NOT NULL,
    created_by      BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE evaluation_runs (
    id              BIGSERIAL PRIMARY KEY,
    data_source_id  BIGINT      NOT NULL REFERENCES data_sources (id) ON DELETE CASCADE,
    triggered_by    BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    total_cases     INTEGER     NOT NULL,
    passed_cases    INTEGER     NOT NULL,
    results         JSONB       NOT NULL,
    llm_model       VARCHAR(120),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
