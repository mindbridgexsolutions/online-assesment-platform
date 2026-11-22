#!/usr/bin/env bash
# One-command load test (Linux/Mac).  Usage:  ./run_load_test.sh <examId> [students] [rampupSeconds]
# Defaults can be overridden with environment variables, e.g.  HOST=10.0.0.5 ./run_load_test.sh 12 200 60
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
HOST="${HOST:-172.16.100.66}"
PORT="${PORT:-8080}"
EXAM_ID="${1:-${EXAM_ID:-}}"
STUDENTS="${2:-${STUDENTS:-50}}"
RAMPUP="${3:-${RAMPUP:-30}}"
SAVES="${SAVES:-4}"
JMETER="${JMETER_HOME:+$JMETER_HOME/bin/}jmeter"

[ -n "$EXAM_ID" ] || { echo "Usage: $0 <examId> [students=50] [rampupSeconds=30]"; exit 1; }
command -v "$JMETER" >/dev/null 2>&1 || { echo "JMeter not found. Install it or set JMETER_HOME=/path/to/apache-jmeter-5.6.3"; exit 1; }

echo "Target : http://$HOST:$PORT   exam $EXAM_ID"
echo "Load   : $STUDENTS students, ramp-up ${RAMPUP}s, $SAVES autosaves each"
echo "Use TEST students/exam only, never during a real exam."
read -r -p "Type YES to start: " ok; [ "$ok" = "YES" ] || { echo "Cancelled."; exit 0; }

OUT="$HERE/results-$(date +%Y%m%d-%H%M%S)"; mkdir -p "$OUT"
"$JMETER" -n -t "$HERE/exam_load.jmx" -Jhost="$HOST" -Jport="$PORT" -Jstudents="$STUDENTS" \
  -Jrampup="$RAMPUP" -Jsaves="$SAVES" -Jcsv="$HERE/students.csv" -JexamId="$EXAM_ID" \
  -l "$OUT/results.jtl" -j "$OUT/jmeter.log" -e -o "$OUT/report"
echo; python3 "$HERE/report.py" "$OUT/results.jtl" 2>/dev/null || true
echo "Full HTML report: $OUT/report/index.html"
