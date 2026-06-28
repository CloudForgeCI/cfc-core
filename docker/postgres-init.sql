-- CloudForge PostgreSQL initialization
-- Runs once on first startup (empty data volume).
-- Creates databases and roles for all applications.
--
-- NOTE: PostgreSQL does not support "CREATE DATABASE IF NOT EXISTS".
-- These statements run on a fresh volume (guaranteed by docker-entrypoint-initdb.d
-- only executing when the data directory is empty). To reset:
--   docker compose down -v && docker compose up -d postgres-main

-- ── Gitea ──────────────────────────────────────────────────────────────────
CREATE DATABASE gitea;
CREATE ROLE gitea WITH LOGIN PASSWORD 'gitea_pass';
GRANT ALL PRIVILEGES ON DATABASE gitea TO gitea;

-- ── Drone CI ───────────────────────────────────────────────────────────────
CREATE DATABASE drone;
CREATE ROLE drone WITH LOGIN PASSWORD 'drone_pass';
GRANT ALL PRIVILEGES ON DATABASE drone TO drone;

-- ── Metabase ───────────────────────────────────────────────────────────────
CREATE DATABASE metabase;
CREATE ROLE metabase WITH LOGIN PASSWORD 'metabase_pass';
GRANT ALL PRIVILEGES ON DATABASE metabase TO metabase;

-- ── Apache Superset ────────────────────────────────────────────────────────
CREATE DATABASE superset;
CREATE ROLE superset WITH LOGIN PASSWORD 'superset_pass';
GRANT ALL PRIVILEGES ON DATABASE superset TO superset;

-- ── Mattermost ─────────────────────────────────────────────────────────────
CREATE DATABASE mattermost;
CREATE ROLE mattermost WITH LOGIN PASSWORD 'mattermost_pass';
GRANT ALL PRIVILEGES ON DATABASE mattermost TO mattermost;

-- ── Drupal ─────────────────────────────────────────────────────────────────
CREATE DATABASE drupal;
CREATE ROLE drupal WITH LOGIN PASSWORD 'drupal_pass';
GRANT ALL PRIVILEGES ON DATABASE drupal TO drupal;

-- Grant cfc_admin full access across all databases
ALTER USER cfc_admin WITH SUPERUSER;
