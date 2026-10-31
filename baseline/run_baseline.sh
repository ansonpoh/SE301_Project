#!/usr/bin/env bash
# Captures the behaviour of the ORIGINAL SVFRMS so the refactor can be compared to it.
# The original source is kept unchanged in baseline/legacy/.
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
RUNS="${2:-300}"
BASE="$(cd "$(dirname "$0")" && pwd)"
OUT="$BASE/expected"
WORK="$(mktemp -d)"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"

mkdir -p "$OUT"
"$JAVAC" -d "$WORK/classes" "$BASE/legacy/org/example/SVFRMS.java" "$BASE/src/org/example/BaselineHarness.java"

# 1. Deterministic baseline (run twice to prove it is reproducible).
for i in 1 2; do
  mkdir -p "$WORK/det$i"
  (cd "$WORK/det$i" && "$JAVA" -cp "$WORK/classes" org.example.BaselineHarness "$SEED" > stdout.txt 2>&1)
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

# 2. Stress the real concurrent legacy main() and check invariants each run.
"$BASE/stress.sh" "$RUNS" "Legacy concurrent main()" "$OUT/legacy-stress.txt" \
  "$JAVA" -cp "$WORK/classes" org.example.SVFRMS
rm -rf "$WORK"
