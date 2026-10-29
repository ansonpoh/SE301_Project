#!/usr/bin/env bash
# Captures the behaviour of the ORIGINAL SVFRMS so the refactor can be compared to it.
#
#   ./baseline/run_baseline.sh [seed] [stress-runs]
#
# Produces, in baseline/expected/:
#   seed<N>-stdout.txt        raw stdout of the deterministic (serial, seeded) run
#   seed<N>-daily_report.txt  the archive/daily_report.txt it wrote
#   seed<N>-normalized.txt    the file to diff against after refactoring
#   legacy-stress.txt         invariant check of <stress-runs> runs of the real concurrent main()
#
# Needs a JDK 25 `java`/`javac` (set JAVA_HOME if the default on PATH is older).
set -euo pipefail

SEED="${1:-42}"
RUNS="${2:-200}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE="$ROOT/baseline"
OUT="$BASE/expected"
WORK="$(mktemp -d)"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"

mkdir -p "$OUT"
(cd "$ROOT" && mvn -q -DskipTests package)
"$JAVAC" -d "$WORK/harness" -cp "$ROOT/target/classes" "$BASE/src/org/example/BaselineHarness.java"
CP="$ROOT/target/classes:$WORK/harness"

# 1. Deterministic baseline (run twice to prove it is reproducible).
for i in 1 2; do
  mkdir -p "$WORK/det$i"
  (cd "$WORK/det$i" && "$JAVA" -cp "$CP" org.example.BaselineHarness "$SEED" > stdout.txt 2>&1)
  python3 -I "$BASE/normalize.py" "$WORK/det$i/stdout.txt" "$WORK/det$i/archive/daily_report.txt" > "$WORK/det$i/normalized.txt"
done
if diff -q "$WORK/det1/normalized.txt" "$WORK/det2/normalized.txt" > /dev/null; then
  echo "Deterministic baseline reproducible across 2 runs (seed $SEED)."
else
  echo "WARNING: two seeded runs differ:"; diff "$WORK/det1/normalized.txt" "$WORK/det2/normalized.txt" | head -20
fi
cp "$WORK/det1/stdout.txt" "$OUT/seed$SEED-stdout.txt"
cp "$WORK/det1/archive/daily_report.txt" "$OUT/seed$SEED-daily_report.txt"
cp "$WORK/det1/normalized.txt" "$OUT/seed$SEED-normalized.txt"

# 2. Stress the real concurrent main() and check invariants each run.
: > "$WORK/stress.txt"
for i in $(seq 1 "$RUNS"); do
  mkdir -p "$WORK/s"; rm -rf "$WORK/s/archive"
  # perl alarm = portable 30s timeout: if the control thread dies, legacy main() can hang forever.
  (cd "$WORK/s" && perl -e 'alarm shift; exec @ARGV' 30 "$JAVA" -jar "$ROOT/target/run.jar" > stdout.txt 2>&1) || true
  if [ -f "$WORK/s/archive/daily_report.txt" ]; then
    python3 -I "$BASE/check_invariants.py" "$WORK/s/stdout.txt" "$WORK/s/archive/daily_report.txt" >> "$WORK/stress.txt"
  else
    echo "DEFECT: no report written (hung or crashed, killed after 30s)" >> "$WORK/stress.txt"
  fi
done
{
  echo "Legacy concurrent main(): $RUNS runs, $(date)"
  echo "Runs with no detected defect: $(grep -c '^OK' "$WORK/stress.txt" || true)"
  echo
  echo "Defect categories (one run can have several):"
  grep '^DEFECT' "$WORK/stress.txt" | sed 's/^DEFECT: //' | tr ';' '\n' | sed 's/^ //; s/[0-9][0-9]*/N/g' | sort | uniq -c | sort -rn
} > "$OUT/legacy-stress.txt"
cat "$OUT/legacy-stress.txt"
rm -rf "$WORK"
