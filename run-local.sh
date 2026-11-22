#!/usr/bin/env bash
# One-shot local run for macOS / Linux:  ./run-local.sh
# Checks Java 17+, prepares the PostgreSQL database, builds the app (Maven wrapper - no Maven install needed)
# and starts it with demo data on http://localhost:8080
set -euo pipefail
cd "$(dirname "$0")"
export PATH="/opt/homebrew/opt/postgresql@16/bin:/usr/local/opt/postgresql@16/bin:/opt/homebrew/bin:/usr/local/bin:/Applications/Postgres.app/Contents/Versions/latest/bin:$PATH"

say() { printf '\n==> %s\n' "$1"; }
die() { printf '\nERROR: %s\n' "$1" >&2; exit 1; }

say "1/4 Checking Java"
command -v java >/dev/null || die "Java not found. Install JDK 17 or newer, then run this script again."
JV=$(java -version 2>&1 | grep -m1 'version "' | sed -E 's/.*version "([0-9]+).*/\1/')
[ "${JV:-0}" -ge 17 ] || die "Java $JV found, but 17 or newer is required."
echo "Java $JV OK"

say "2/4 Checking PostgreSQL database"
command -v psql >/dev/null || die "psql not found. Install PostgreSQL (and start it), then run this script again."
check_db() { PGPASSWORD=assess_pw psql -h localhost -U assess -d assessment -tAc "select 1" >/dev/null 2>&1; }
if ! check_db; then
  echo "Database 'assessment' not ready - creating user and database..."
  PSQL_ADMIN="psql -h localhost postgres"
  $PSQL_ADMIN -tAc "select 1" >/dev/null 2>&1 || PSQL_ADMIN="psql postgres"
  $PSQL_ADMIN -tAc "select 1" >/dev/null 2>&1 || die "Cannot connect to PostgreSQL. Make sure it is running (e.g. 'brew services start postgresql@16')."
  $PSQL_ADMIN -tAc "select 1 from pg_roles where rolname='assess'" | grep -q 1 || $PSQL_ADMIN -c "CREATE USER assess WITH PASSWORD 'assess_pw'"
  $PSQL_ADMIN -tAc "select 1 from pg_database where datname='assessment'" | grep -q 1 || $PSQL_ADMIN -c "CREATE DATABASE assessment OWNER assess"
  check_db || die "Could not connect as user 'assess' (password assess_pw) to database 'assessment'."
fi
echo "Database OK"

say "3/4 Building (first run downloads dependencies - a few minutes)"
# Use the Maven installed on this machine if any, otherwise the bundled wrapper (downloads Maven once).
if command -v mvn >/dev/null; then MVN=mvn; else MVN=./mvnw; fi
$MVN -q -B -DskipTests package
[ -f target/assessment.war ] || die "Build failed: target/assessment.war was not created."

say "4/4 Starting the application"
echo "Open http://localhost:8080   Admin: admin / Admin@12345   Demo users password: Demo@12345"
echo "(press Ctrl+C to stop)"
SPRING_PROFILES_ACTIVE=dev SEED_DEMO=true exec java -Duser.timezone=UTC -jar target/assessment.war
