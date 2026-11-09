#!/bin/bash
# Double-click this file in Finder (or run ./"Run QueryAI.command") to build and start QueryAI.
cd "$(dirname "$0")" || exit 1
export PATH="/usr/local/bin:/opt/homebrew/bin:/Applications/Docker.app/Contents/Resources/bin:$PATH"
mkdir -p logs

echo "==> Checking Docker..."
if ! docker info >/dev/null 2>&1; then
  echo "    Starting Docker Desktop..."
  open -a Docker
  for i in $(seq 1 90); do docker info >/dev/null 2>&1 && break; sleep 2; done
fi
docker info >/dev/null 2>&1 || { echo "Docker is not running. Start Docker Desktop and try again."; read -r -p "Press Enter to close"; exit 1; }

[ -f .env ] || cp .env.example .env

# v2 switches the database image to PostgreSQL + pgvector. The old demo volumes were created by a different
# PostgreSQL build, so they are recreated once (demo data is re-seeded automatically; nothing else is stored there).
if [ ! -f logs/.v2-database ]; then
  echo "==> One-time upgrade: recreating the databases for pgvector..."
  docker compose down -v --remove-orphans 2>&1 | tee logs/upgrade.log
  touch logs/.v2-database
fi

# v3 renames the sample business database to "commerce" and the starter account to analyst@queryai.app.
# The databases are recreated once and re-seeded automatically.
if [ ! -f logs/.v3-database ]; then
  echo "==> One-time upgrade: refreshing the sample workspace..."
  sed -i '' 's/Demo@12345/QueryAI@2026/g' .env 2>/dev/null || true
  docker compose down -v --remove-orphans 2>&1 | tee logs/upgrade.log
  touch logs/.v2-database logs/.v3-database
fi

echo "==> Building backend (Java). First time takes several minutes..."
docker compose build backend 2>&1 | tee logs/backend-build.log
BACK=${PIPESTATUS[0]}
echo "==> Building frontend (React + three.js)..."
docker compose build frontend 2>&1 | tee logs/frontend-build.log
FRONT=${PIPESTATUS[0]}
echo "backend=$BACK frontend=$FRONT" > logs/build-status.txt
if [ "$BACK" != "0" ] || [ "$FRONT" != "0" ]; then
  echo ""
  echo "BUILD FAILED (backend=$BACK, frontend=$FRONT). Logs saved in the logs folder."
  echo "Go back to Claude and type: done"
  read -r -p "Press Enter to close"; exit 1
fi

echo "==> Starting QueryAI..."
docker compose up -d 2>&1 | tee logs/up.log
echo "==> Waiting for the app to become ready (up to 5 minutes)..."
for i in $(seq 1 100); do
  if curl -fs http://localhost:18080/actuator/health >/dev/null 2>&1; then READY=1; break; fi
  sleep 3
done
docker compose ps -a > logs/ps.log 2>&1
docker compose logs --tail 400 > logs/runtime.log 2>&1

if [ "$READY" = "1" ]; then
  echo ""
  echo "QueryAI is running:  http://localhost:13000"
  echo "Sign in: analyst@queryai.app / QueryAI@2026"
  open http://localhost:13000
else
  echo ""
  echo "QueryAI did not start in time. Logs saved in the logs folder."
  echo "Go back to Claude and type: done"
fi
read -r -p "Press Enter to close this window (QueryAI keeps running)"
