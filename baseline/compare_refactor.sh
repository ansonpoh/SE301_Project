#!/usr/bin/env bash
# Checks the REFACTORED system against the legacy baseline.
#
#   ./baseline/compare_refactor.sh [seed] [stress-runs]
#
# 1. Behaviour: runs the refactored code with the same seed and serial schedule
#    as the baseline, and diffs its normalized output with
#    baseline/expected/seed<N>-normalized.txt. Must be identical.
# 2. Concurrency: runs the real concurrent run.jar many times, with the default
#    chambers and with the demo/ layout, and checks the same invariants the
#    legacy code failed. Summaries in baseline/results/.
#
# Needs a JDK 25 `java`/`javac` (set JAVA_HOME if the default on PATH is older).
set -euo pipefail

SEED="${1:-42}"
RUNS="${2:-300}"
BASE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$BASE")"
WORK="$(mktemp -d)"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
EXPECTED="$BASE/expected/seed$SEED-normalized.txt"
. "$BASE/env.sh"

(cd "$ROOT" && mvn -q -DskipTests package)
"$JAVAC" -d "$WORK/harness" -cp "$ROOT/target/classes" "$BASE/src/harness/RefactoredHarness.java"

mkdir -p "$WORK/det" "$BASE/results"
(cd "$WORK/det" && "$JAVA" -cp "$(cp_join "$ROOT/target/classes" "$WORK/harness")" harness.RefactoredHarness "$SEED" > stdout.txt 2>&1)
"$PY" -I "$BASE/normalize.py" "$WORK/det/stdout.txt" "$WORK/det/archive/daily_report.txt" > "$BASE/results/seed$SEED-refactored-normalized.txt"

if diff -u "$EXPECTED" "$BASE/results/seed$SEED-refactored-normalized.txt" > "$WORK/diff.txt"; then
  echo "BEHAVIOUR: IDENTICAL to legacy baseline (seed $SEED)."
else
  echo "BEHAVIOUR: DIFFERS from legacy baseline (seed $SEED):"; head -40 "$WORK/diff.txt"
fi

FARM_CHAMBERS="$ROOT/src/main/resources/chambers.csv" "$BASE/stress.sh" "$RUNS" "Refactored concurrent run.jar" "$BASE/results/refactored-stress.txt" \
  "$JAVA" -jar "$ROOT/target/run.jar"
# Same check with the spec 4.5 demo layout (7 chambers, including Kale, Bok Choy and Bell Pepper).
FARM_CONFIG="$ROOT/demo" "$BASE/stress.sh" "$RUNS" "Refactored concurrent run.jar demo" "$BASE/results/refactored-demo-stress.txt" \
  "$JAVA" -jar "$ROOT/target/run.jar" "$ROOT/demo"
rm -rf "$WORK"
