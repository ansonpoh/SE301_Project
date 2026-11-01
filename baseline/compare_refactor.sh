#!/usr/bin/env bash
# Checks the REFACTORED system against the legacy baseline.
#
#   ./baseline/compare_refactor.sh [seed] [stress-runs]
#
# 1. Behaviour: runs the refactored code with the same seed and serial schedule
#    as the baseline, for every recorded seed and both layouts (the 4 legacy
#    chambers, and all six legacy crops), and diffs its normalized output with
#    baseline/expected/seed<N>[-all]-normalized.txt. Every one must be identical.
# 2. Concurrency: runs the real concurrent run.jar many times, with the default
#    chambers and with the built-in demo layout (src/main/resources/demo), and checks the same invariants the
#    legacy code failed. Summaries in baseline/results/.
#
#   SEEDS  seeds to compare (default: [seed] if given, else every seed recorded in expected/)
#
# Exits 1 if any seed/layout differs or its harness run fails.
# Needs a JDK 25 `java`/`javac` (set JAVA_HOME if the default on PATH is older).
set -euo pipefail

RUNS="${2:-300}"
BASE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$BASE")"
WORK="$(mktemp -d)"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
SUMMARY="$BASE/results/behaviour-summary.txt"
. "$BASE/env.sh"

# Seeds recorded for one layout, from names matched exactly (so seed1-all-... is never "seed 1-all").
recorded_seeds() {
  local pattern="$1"
  ls "$BASE/expected" | sed -nE "s/^seed([0-9]+)$pattern-normalized\.txt$/\1/p" | sort -n | tr '\n' ' '
}

(cd "$ROOT" && mvn -q -DskipTests package)
"$JAVAC" -d "$WORK/harness" -cp "$ROOT/target/classes" "$BASE/src/harness/RefactoredHarness.java"
CLASSPATH_ARG="$(cp_join "$ROOT/target/classes" "$WORK/harness")"

mkdir -p "$BASE/results"
: > "$SUMMARY"
PASSED=0; TOTAL=0

# compare <layout> <suffix> <seeds> [config-dir]
compare() {
  local layout="$1" suffix="$2" seeds="$3" dir="${4:-}" seed out code
  for seed in $seeds; do
    TOTAL=$((TOTAL + 1))
    out="$WORK/$layout-$seed"; mkdir -p "$out"
    code=0
    (cd "$out" && "$JAVA" -cp "$CLASSPATH_ARG" harness.RefactoredHarness "$seed" ${dir:+"$(native_path "$dir")"} > stdout.txt 2>&1) || code=$?
    if [ "$code" != 0 ]; then
      echo "seed $seed layout $layout: harness exit $code" >> "$SUMMARY"
      tail -5 "$out/stdout.txt" >> "$SUMMARY"
      continue
    fi
    "$PY" -I "$BASE/normalize.py" "$out/stdout.txt" "$out/archive/daily_report.txt" > "$out/normalized.txt"
    if diff -u "$BASE/expected/seed$seed$suffix-normalized.txt" "$out/normalized.txt" > "$out/diff.txt"; then
      PASSED=$((PASSED + 1))
      echo "seed $seed layout $layout: identical" >> "$SUMMARY"
    else
      echo "seed $seed layout $layout: DIFFERS" >> "$SUMMARY"
      head -40 "$out/diff.txt" >> "$SUMMARY"
    fi
    # Kept for reference: the original single-seed result file.
    if [ "$seed" = 42 ] && [ "$layout" = default ]; then
      cp "$out/normalized.txt" "$BASE/results/seed42-refactored-normalized.txt"
    fi
  done
}

compare default "" "${SEEDS:-${1:-$(recorded_seeds "")}}"
compare all "-all" "${SEEDS:-${1:-$(recorded_seeds "-all")}}" "$BASE/layouts/all"

grep -v ": identical$" "$SUMMARY" || true
echo "BEHAVIOUR: $PASSED/$TOTAL seed/layout runs IDENTICAL to the legacy baseline (details: baseline/results/behaviour-summary.txt)." | tee -a "$SUMMARY"

FARM_CHAMBERS="$ROOT/src/main/resources/chambers.csv" "$BASE/stress.sh" "$RUNS" "Refactored concurrent run.jar" "$BASE/results/refactored-stress.txt" \
  "$JAVA" -jar "$ROOT/target/run.jar"
# Same check with the spec 4.5 demo layout (7 chambers, including Kale, Bok Choy and Bell Pepper).
FARM_CONFIG="$ROOT/src/main/resources/demo" "$BASE/stress.sh" "$RUNS" "Refactored concurrent run.jar demo" "$BASE/results/refactored-demo-stress.txt" \
  "$JAVA" -jar "$ROOT/target/run.jar" "$ROOT/src/main/resources/demo"
rm -rf "$WORK"
[ "$PASSED" = "$TOTAL" ] || exit 1
