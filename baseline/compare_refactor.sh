#!/usr/bin/env bash
# Checks the REFACTORED system against the legacy baseline.
#
#   ./baseline/compare_refactor.sh [seed] [stress-runs]
#
# 1. Behaviour: runs the refactored code with the same seed and serial schedule
#    as the baseline, and diffs its normalized output with
#    baseline/expected/seed<N>-normalized.txt. Must be identical.
# 2. Concurrency: runs the real concurrent run.jar many times and checks the
#    same invariants the legacy code failed. Summary in baseline/results/.
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

(cd "$ROOT" && mvn -q -DskipTests package)
"$JAVAC" -d "$WORK/harness" -cp "$ROOT/target/classes" "$BASE/src/harness/RefactoredHarness.java"

mkdir -p "$WORK/det" "$BASE/results"
# baseline/legacy comes first on the classpath, so its chambers.csv (CH1-CH4 only)
# overrides the app's one: the run then covers exactly the chambers the baseline ran.
(cd "$WORK/det" && "$JAVA" -cp "$BASE/legacy:$ROOT/target/classes:$WORK/harness" harness.RefactoredHarness "$SEED" > stdout.txt 2>&1)
python3 -I "$BASE/normalize.py" "$WORK/det/stdout.txt" "$WORK/det/archive/daily_report.txt" > "$BASE/results/seed$SEED-refactored-normalized.txt"

if diff -u "$EXPECTED" "$BASE/results/seed$SEED-refactored-normalized.txt" > "$WORK/diff.txt"; then
  echo "BEHAVIOUR: IDENTICAL to legacy baseline (seed $SEED)."
else
  echo "BEHAVIOUR: DIFFERS from legacy baseline (seed $SEED):"; head -40 "$WORK/diff.txt"
fi

FARM_CHAMBERS="$ROOT/src/main/resources/chambers.csv" "$BASE/stress.sh" "$RUNS" "Refactored concurrent run.jar" "$BASE/results/refactored-stress.txt" \
  "$JAVA" -jar "$ROOT/target/run.jar"
rm -rf "$WORK"
