-- QueryAI v2: semantic schema search (pgvector), PII masking policies and CSV upload tracking.

-- pgvector is used when the server provides it (pgvector/pgvector images, Render, Supabase, RDS ...).
-- Embeddings are stored as REAL[] so the schema also works on a plain PostgreSQL; when the extension exists,
-- similarity search runs inside the database with the cosine-distance operator (embedding::vector <=> query).
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pgvector extension is not available (%); semantic search will rank in the application', SQLERRM;
END
$$;

CREATE TABLE schema_embeddings (
    id              BIGSERIAL PRIMARY KEY,
    data_source_id  BIGINT       NOT NULL REFERENCES data_sources (id) ON DELETE CASCADE,
    table_name      VARCHAR(256) NOT NULL,
    model           VARCHAR(160) NOT NULL,
    content_hash    VARCHAR(64)  NOT NULL,
    content         TEXT         NOT NULL,
    embedding       REAL[]       NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_schema_embedding UNIQUE (data_source_id, table_name, model)
);
CREATE INDEX ix_schema_embeddings_source ON schema_embeddings (data_source_id, model);

-- PII handling: masking is on by default for every data source; admins can switch it off per source.
ALTER TABLE data_sources ADD COLUMN pii_masking_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Manual overrides of the automatic PII column detection (auto-detected columns need no row).
CREATE TABLE pii_policies (
    id              BIGSERIAL PRIMARY KEY,
    data_source_id  BIGINT       NOT NULL REFERENCES data_sources (id) ON DELETE CASCADE,
    table_name      VARCHAR(128) NOT NULL,
    column_name     VARCHAR(128) NOT NULL,
    pii_type        VARCHAR(30)  NOT NULL,
    masked          BOOLEAN      NOT NULL,
    updated_by      BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_pii_policy UNIQUE (data_source_id, table_name, column_name)
);

-- Files uploaded into the workspace warehouse (one row per created table).
CREATE TABLE uploaded_tables (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT       NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    data_source_id  BIGINT       NOT NULL REFERENCES data_sources (id) ON DELETE CASCADE,
    table_name      VARCHAR(128) NOT NULL,
    original_file   VARCHAR(255) NOT NULL,
    row_count       BIGINT       NOT NULL,
    column_count    INTEGER      NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    uploaded_by     BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_uploaded_table UNIQUE (data_source_id, table_name)
);
