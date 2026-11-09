-- Least-privilege, read-only login that QueryAI uses to query the demo database.
-- QueryAI enforces read-only transactions itself; this role is defence in depth.
GRANT CONNECT ON DATABASE commerce TO queryai_reader;
GRANT USAGE ON SCHEMA public TO queryai_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO queryai_reader;
ALTER ROLE queryai_reader SET default_transaction_read_only = on;
ALTER ROLE queryai_reader SET statement_timeout = '60s';
