# Deploying QueryAI live

QueryAI ships as **one container** (`Dockerfile` at the repo root): the React app is built and served by the Spring Boot
backend, so a single web service + one PostgreSQL database is all you need. The sample e-commerce dataset and CSV uploads
live in that same database (schemas `commerce` and `uploads_w<id>`), read through a least-privilege `queryai_reader` role.

You need: a GitHub account (push this repo), a hosting account (Render or Railway) and a free **Groq** API key
(console.groq.com → API Keys) for the AI.

---

## Option A — Render (free)

1. Push the project to a GitHub repository.
2. Render dashboard → **New → Blueprint** → select the repository → **Apply**.
   `render.yaml` creates:
   - `queryai-db` — free PostgreSQL (pgvector is available on Render),
   - `queryai` — free Docker web service with every variable pre-filled (secrets are generated).
3. Open the `queryai` service → **Environment** → set `OPENAI_API_KEY` to your Groq key → **Save** (it redeploys).
4. First boot takes a few minutes (image build + sample dataset load). When **Logs** show
   `Loaded the sample e-commerce dataset` and `Started QueryAiApplication`, open the service URL.
5. Click **Continue as guest** (opens the starter workspace as an analyst, PII masked) or create an account.

Free-tier notes: the service sleeps after 15 minutes without traffic (first request then takes ~50 s), and Render's
free database expires after 30 days unless upgraded. MySQL is not available on Render; everything else is.

## Option B — Railway (~$5/month, no sleeping)

1. Railway → **New project → Deploy from GitHub repo** (it detects `railway.json` and the root `Dockerfile`).
2. **+ New → Database → PostgreSQL** in the same project.
3. In the QueryAI service → **Variables**, add (use *Add reference* for the `${{Postgres.*}}` values):

   | Variable | Value |
   |---|---|
   | `QUERYAI_DB_HOST` | `${{Postgres.PGHOST}}` |
   | `QUERYAI_DB_PORT` | `${{Postgres.PGPORT}}` |
   | `QUERYAI_DB_NAME` | `${{Postgres.PGDATABASE}}` |
   | `QUERYAI_DB_USERNAME` | `${{Postgres.PGUSER}}` |
   | `QUERYAI_DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
   | `QUERYAI_JWT_SECRET` | output of `openssl rand -base64 48` |
   | `QUERYAI_CREDENTIAL_KEY` | output of `openssl rand -base64 32` |
   | `DEMO_READER_PASSWORD` | any strong password |
   | `OPENAI_API_KEY` | your Groq key |
   | `OPENAI_BASE_URL` | `https://api.groq.com/openai` |
   | `OPENAI_MODEL` | `llama-3.3-70b-versatile` |
   | `QUERYAI_WAREHOUSE_ENABLED` / `QUERYAI_WAREHOUSE_BOOTSTRAP_DEMO` | `true` / `true` |
   | `QUERYAI_DEMO_ENABLED` / `QUERYAI_DEMO_PUBLIC` | `true` / `true` |
   | `QUERYAI_DEMO_PG_HOST` / `_PORT` / `_DATABASE` | same references as the DB host / port / name |
   | `QUERYAI_DEMO_PG_SCHEMA` / `QUERYAI_DEMO_PG_USERNAME` | `commerce` / `queryai_reader` |
   | `QUERYAI_DEMO_MYSQL_ENABLED` | `false` |

4. **Settings → Networking → Generate domain**. Railway's PostgreSQL image includes pgvector.

## Option C — any VM (Oracle Cloud free tier, a VPS)

`git clone`, `cp .env.example .env`, add your key, `docker compose up -d --build`. The full multi-container stack
(PostgreSQL + pgvector, MySQL, backend, nginx frontend) runs exactly as on a laptop; put a reverse proxy with HTTPS
(Caddy) in front of port 13000.

## After deploying

- Put the URL at the top of `README.md` and record the walkthrough video:
  `cd scripts/demo && npm install && node record-demo.mjs --url https://<your-app>`.
- Measure accuracy against the live deployment:
  `node scripts/benchmark/run-benchmark.mjs --url https://<your-app>` and paste `benchmark/results/latest.md` into the
  README's benchmark section. Add a `GROQ_API_KEY` repository secret to run it in CI on every push.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Banner “AI model not connected” | `OPENAI_API_KEY` missing or wrong; check the service logs for `LLM` warnings |
| Sample connection fails with an authentication error | the host did not allow `CREATE ROLE`; set `QUERYAI_DEMO_PG_USERNAME` and `DEMO_READER_PASSWORD` to the database's own user and password |
| `Out of memory` on a 512 MB instance | keep `JAVA_OPTS` from the Dockerfile; lower `QUERYAI_DB_POOL_SIZE` |
| Uploads say “not configured” | set `QUERYAI_WAREHOUSE_ENABLED=true` |
