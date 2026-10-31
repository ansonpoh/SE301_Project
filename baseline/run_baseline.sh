#!/usr/bin/env bash
# Captures the behaviour of the ORIGINAL SVFRMS so the refactor can be compared to it.
# The original source is kept unchanged in baseline/legacy/.
#
#   ./baseline/run_baseline.sh [seed] [stress-runs]
#   SEEDS="$(seq 1 20)" RUNS=0 ./baseline/run_baseline.sh             # more seeds, no stress rerun
#   SEEDS="$(seq 1 20) 42" LAYOUT=all RUNS=0 ./baseline/run_baseline.sh  # all six legacy crops
#
#   SEEDS   seeds to record (default: [seed], else 42)
#   LAYOUT  default = the 4 legacy chambers (CH1-CH4); all = baseline/legacy/chambers-all.csv
#           (adds Spinach and Cucumber, which the legacy main() never schedules)
#   RUNS    legacy stress runs (default 300); 0 skips them and keeps expected/legacy-stress.txt
#
# Produces, in baseline/expected/:
#   seed<N>-normalized.txt      the file to diff against after refactoring (default layout)
#   seed<N>-all-normalized.txt  the same for LAYOUT=all
#   seed42-stdout.txt, seed42-daily_report.txt  raw output of default-layout seed 42 only
#   legacy-stress.txt           invariant check of <RUNS> runs of the real concurrent main()
#
# Needs a JDK 25 `java`/`javac` (set JAVA_HOME if the default on PATH is older).
set -euo pipefail

SEEDS="${SEEDS:-${1:-42}}"
RUNS="${RUNS:-${2:-300}}"
LAYOUT="${LAYOUT:-default}"
BASE="$(cd "$(dirname "$0")" && pwd)"
OUT="$BASE/expected"
WORK="$(mktemp -d)"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
. "$BASE/env.sh"

case "$LAYOUT" in
  default) SUFFIX=""; CHAMBER_ARGS=() ;;
  all) SUFFIX="-all"; CHAMBER_ARGS=("$(native_path "$BASE/legacy/chambers-all.csv")") ;;
  *) echo "Unknown LAYOUT '$LAYOUT' (default or all)" >&2; exit 2 ;;
esac

mkdir -p "$OUT"
"$JAVAC" -d "$WORK/classes" "$BASE/legacy/org/example/SVFRMS.java" "$BASE/src/org/example/BaselineHarness.java"

# 1. Deterministic baseline per seed (run twice to prove it is reproducible).
for SEED in $SEEDS; do
  for i in 1 2; do
    rm -rf "$WORK/det$i"; mkdir -p "$WORK/det$i"
    (cd "$WORK/det$i" && "$JAVA" -cp "$(cp_join "$WORK/classes")" org.example.BaselineHarness "$SEED" "${CHAMBER_ARGS[@]}" > stdout.txt 2>&1)
    "$PY" -I "$BASE/normalize.py" "$WORK/det$i/stdout.txt" "$WORK/det$i/archive/daily_report.txt" > "$WORK/det$i/normalized.txt"
  done
  if diff -q "$WORK/det1/normalized.txt" "$WORK/det2/normalized.txt" > /dev/null; then
    echo "Deterministic baseline reproducible across 2 runs (seed $SEED, layout $LAYOUT)."
  else
    echo "WARNING: two seeded runs differ (seed $SEED, layout $LAYOUT):"; diff "$WORK/det1/normalized.txt" "$WORK/det2/normalized.txt" | head -20
  fi
  cp "$WORK/det1/normalized.txt" "$OUT/seed$SEED$SUFFIX-normalized.txt"
  # Raw files hold timestamps and absolute paths, so only the original seed-42 pair is kept.
  if [ "$SEED" = 42 ] && [ "$LAYOUT" = default ]; then
    cp "$WORK/det1/stdout.txt" "$OUT/seed$SEED-stdout.txt"
    cp "$WORK/det1/archive/daily_report.txt" "$OUT/seed$SEED-daily_report.txt"
  fi
done

# 2. Stress the real concurrent legacy main() and check invariants each run.
if [ "$RUNS" = 0 ]; then
  echo "Legacy stress skipped (RUNS=0); expected/legacy-stress.txt unchanged."
else
  "$BASE/stress.sh" "$RUNS" "Legacy concurrent main()" "$OUT/legacy-stress.txt" \
    "$JAVA" -cp "$WORK/classes" org.example.SVFRMS
fi
rm -rf "$WORK"
