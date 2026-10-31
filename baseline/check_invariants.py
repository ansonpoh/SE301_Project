"""Detect concurrency defects in one run of the concurrent program (legacy or refactored).

Usage: python3 -I check_invariants.py <stdout.txt> <daily_report.txt>
Prints one line: OK | DEFECT: <reasons>

The counters are updated in a fixed way, so the final totals can be recomputed
from the archived alert list. If they don't match, an update was lost.
  water  = chambers * 20 iterations * 2  + per-alert water
  energy = chambers * 20 iterations * 1  + per-alert energy
  Generated Alerts == number of archived alerts == sum of chamber "Alerts="
These invariants only hold if every chamber thread finished, so a crashed thread
is reported on its own.

Per-alert costs come from src/main/resources/crops.csv (the legacy costs are the
same numbers). The chamber list comes from $FARM_CHAMBERS, default
baseline/legacy/chambers.csv (the 4 legacy chambers).
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHAMBERS_FILE = os.environ.get("FARM_CHAMBERS", os.path.join(ROOT, "baseline", "legacy", "chambers.csv"))
CROPS_FILE = os.path.join(ROOT, "src", "main", "resources", "crops.csv")


def csv_rows(path):
    with open(path, encoding="utf-8") as f:
        return [[c.strip() for c in l.split(",")] for l in f if l.strip() and not l.strip().startswith("#")]


CHAMBERS = {chamber: crop for chamber, crop in csv_rows(CHAMBERS_FILE)}
BASE_WATER, BASE_ENERGY = len(CHAMBERS) * 20 * 2, len(CHAMBERS) * 20 * 1


def alert_costs():
    """Full alert line (e.g. 'CH1 Tomato Low Humidity') -> (water, energy)."""
    rows = csv_rows(CROPS_FILE)
    costs = {}
    for chamber, crop in CHAMBERS.items():
        for rule_crop, _, _, _, _, water, energy, message in rows:
            if rule_crop in (crop, "*"):
                costs[chamber + " " + message.replace("{crop}", crop)] = (int(water), int(energy))
    return costs


COSTS = alert_costs()


def main(stdout_path, report_path):
    out = open(stdout_path, encoding="utf-8").read()
    lines = open(report_path, encoding="utf-8").read().splitlines()

    def total(label):
        return int(next(l for l in lines if l.startswith(label)).split(":")[1].split()[0])

    archived = lines[lines.index("--- archived alerts ---") + 1:] if "--- archived alerts ---" in lines else []
    summary_alerts = sum(int(m) for m in re.findall(r"Alerts=(\d+)", "\n".join(lines)))
    chambers = len(re.findall(r"^CH\d+ -> ", "\n".join(lines), re.M))

    problems = []
    crashes = re.findall(r'Exception in thread "[^"]+" (\S+)', out)  # legacy: thread died
    crashes += re.findall(r"ERROR: chamber \S+ failed: (\S+)", out)  # refactored: reported failure
    if crashes:
        problems.append("thread crashed: " + ", ".join(sorted(set(crashes))))
    else:
        exp_water = BASE_WATER + sum(COSTS.get(a, (0, 0))[0] for a in archived)
        exp_energy = BASE_ENERGY + sum(COSTS.get(a, (0, 0))[1] for a in archived)
        if total("Water Usage") != exp_water:
            problems.append(f"water {total('Water Usage')} != expected {exp_water} (lost update)")
        if total("Energy Usage") != exp_energy:
            problems.append(f"energy {total('Energy Usage')} != expected {exp_energy} (lost update)")
        if chambers != len(CHAMBERS):
            problems.append(f"only {chambers} chamber summaries (HashMap lost an entry)")
    gen = total("Generated Alerts")
    if gen != len(archived):
        problems.append(f"Generated Alerts {gen} != archived {len(archived)}")
    if not crashes and summary_alerts != len(archived):
        problems.append(f"chamber Alerts sum {summary_alerts} != archived {len(archived)}")
    if out.find("===== DAILY REPORT") < out.find("Archiving alerts"):
        problems.append("report printed BEFORE archive (ordering race)")

    print("OK" if not problems else "DEFECT: " + "; ".join(problems))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
