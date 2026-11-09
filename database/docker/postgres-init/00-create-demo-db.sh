#!/bin/bash
# Runs once when the PostgreSQL container initialises an empty data directory.
# Creates the separate demo business database (commerce) next to the QueryAI app database.
set -euo pipefail

READER_PASSWORD="${DEMO_READER_PASSWORD:-reader_pass}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE DATABASE commerce;
  CREATE ROLE queryai_reader LOGIN PASSWORD '${READER_PASSWORD}';
EOSQL

for f in 01-schema.sql 02-data.sql 03-readonly-grants.sql; do
  echo "Loading demo/postgres/${f}"
  psql -v ON_ERROR_STOP=1 --quiet --username "$POSTGRES_USER" --dbname commerce -f "/demo/postgres/${f}"
done
