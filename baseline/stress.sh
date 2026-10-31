#!/usr/bin/env bash
# Runs a program many times and checks each run with check_invariants.py.
#
#   ./baseline/stress.sh <runs> <title> <summary-file> <command...>
#
# Each run happens in a fresh empty folder so it writes its own archive/daily_report.txt.
set -euo pipefail

RUNS="$1"; TITLE="$2"; SUMMARY="$3"; shift 3
BASE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"

: > "$WORK/results.txt"
for i in $(seq 1 "$RUNS"); do
  rm -rf "$WORK/run"; mkdir -p "$WORK/run"
  # perl alarm = portable 30s timeout (the legacy main() can hang if its control thread dies).
  (cd "$WORK/run" && perl -e 'alarm shift; exec @ARGV' 30 "$@" > stdout.txt 2>&1) || true
  if [ -f "$WORK/run/archive/daily_report.txt" ]; then
    python3 -I "$BASE/check_invariants.py" "$WORK/run/stdout.txt" "$WORK/run/archive/daily_report.txt" >> "$WORK/results.txt"
  else
    echo "DEFECT: no report written (hung or crashed, killed after 30s)" >> "$WORK/results.txt"
  fi
done

{
  echo "$TITLE: $RUNS runs, $(date)"
  echo "Runs with no detected defect: $(grep -c '^OK' "$WORK/results.txt" || true)"
  echo
  echo "Defect categories (one run can have several):"
  grep '^DEFECT' "$WORK/results.txt" | sed 's/^DEFECT: //' | tr ';' '\n' | sed 's/^ //; s/[0-9][0-9]*/N/g' | sort | uniq -c | sort -rn || echo "   none"
} > "$SUMMARY"
cat "$SUMMARY"
rm -rf "$WORK"
