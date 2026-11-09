#!/bin/bash
# Double-click this file in Finder (or run ./"Run Tests.command") to run every QueryAI test inside Docker:
#   backend  — unit tests + integration tests (Testcontainers: PostgreSQL/pgvector and MySQL 8)
#   frontend — Vitest + Testing Library page and component tests
# Nothing needs to be installed except Docker. Logs go to the logs folder.
cd "$(dirname "$0")" || exit 1
ROOT="$(pwd)"
export PATH="/usr/local/bin:/opt/homebrew/bin:/Applications/Docker.app/Contents/Resources/bin:$PATH"
mkdir -p logs

echo "==> Checking Docker..."
if ! docker info >/dev/null 2>&1; then
  echo "    Starting Docker Desktop..."
  open -a Docker
  for i in $(seq 1 90); do docker info >/dev/null 2>&1 && break; sleep 2; done
fi
docker info >/dev/null 2>&1 || { echo "Docker is not running. Start Docker Desktop and try again."; read -r -p "Press Enter to close"; exit 1; }

echo "==> Backend tests (Maven in Docker; first run downloads dependencies and test images, ~5-10 min)..."
# The Maven container starts sibling containers through the Docker socket; Testcontainers reaches their
# published ports through host.docker.internal.
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$ROOT/backend":/src -w /src \
  -v queryai-m2:/root/.m2 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e TESTCONTAINERS_RYUK_DISABLED=false \
  --add-host host.docker.internal:host-gateway \
  maven:3.9-eclipse-temurin-21 \
  mvn -B -ntp verify -Dstyle.color=never 2>&1 | tee logs/tests-backend.log
BACK=${PIPESTATUS[0]}

# keep the per-class reports next to the log for quick reading
rm -rf logs/test-reports 2>/dev/null
mkdir -p logs/test-reports
cp backend/target/surefire-reports/*.txt logs/test-reports/ 2>/dev/null
cp backend/target/failsafe-reports/*.txt logs/test-reports/ 2>/dev/null

echo "==> Frontend tests (Vitest in Docker)..."
docker run --rm \
  -v "$ROOT/frontend":/app -w /app \
  -v /app/node_modules \
  node:22-alpine \
  sh -c "npm install --no-audit --no-fund --loglevel=error && { npx vitest run --reporter=verbose; T=\$?; echo '==> Type-checking the tests'; npx tsc -p tsconfig.test.json || echo 'TEST TYPECHECK FAILED'; exit \$T; }" 2>&1 | tee logs/tests-frontend.log
FRONT=${PIPESTATUS[0]}

echo "backend=$BACK frontend=$FRONT" > logs/tests-status.txt
echo ""
if [ "$BACK" = "0" ] && [ "$FRONT" = "0" ]; then
  echo "ALL TESTS PASSED (backend and frontend)."
else
  echo "SOME TESTS FAILED (backend=$BACK, frontend=$FRONT). Logs saved in the logs folder."
fi
echo "Go back to Claude and type: done"
read -r -p "Press Enter to close"
