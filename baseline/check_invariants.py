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
baseline/legacy/chambers.csv (the 4 legacy chambers). Set $FARM_CONFIG to the
folder passed to run.jar: as in FarmConfigLoader, a crops.csv or chambers.csv in
it replaces the file above.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIG_DIR = os.environ.get("FARM_CONFIG")


def config_file(name, default):
    """The override in $FARM_CONFIG if it has one, else the default (same rule as FarmConfigLoader)."""
    override = os.path.join(CONFIG_DIR, name) if CONFIG_DIR else None
    return override if override and os.path.isfile(override) else default


CHAMBERS_FILE = config_file("chambers.csv",
                            os.environ.get("FARM_CHAMBERS", os.path.join(ROOT, "baseline", "legacy", "chambers.csv")))
CROPS_FILE = config_file("crops.csv", os.path.join(ROOT, "src", "main", "resources", "crops.csv"))


def read_config_text(path):
    """Decode a config file exactly as FarmConfigLoader.decode does, so every file
    the program accepts (UTF-8, a BOM, UTF-16, or UTF-16 appended with PowerShell
    ">>") is read the same way here, and anything else fails the same way."""
    with open(path, "rb") as f:
        data = f.read()
    # Split at each UTF-16 BOM (never valid UTF-8) and decode every part in its own encoding.
    boms = {b"\xff\xfe": "utf-16-le", b"\xfe\xff": "utf-16-be"}
    parts, encoding, start, i = [], "utf-8", 0, 0
    while i + 1 < len(data):
        if data[i:i + 2] in boms:
            parts.append(data[start:i].decode(encoding))
            encoding, start = boms[data[i:i + 2]], i + 2
            i = start
        else:
            i += 1 if encoding == "utf-8" else 2
    parts.append(data[start:].decode(encoding))
    return "".join(parts).replace("\ufeff", "")


def csv_rows(path, columns=0):
    """With columns, the last column keeps its commas (the crops.csv message), as in FarmConfigLoader."""
    lines = read_config_text(path).splitlines()
    return [[c.strip() for c in l.split(",", columns - 1)] for l in lines if l.strip() and not l.strip().startswith("#")]


CHAMBERS = {chamber: crop for chamber, crop in csv_rows(CHAMBERS_FILE)}
BASE_WATER, BASE_ENERGY = len(CHAMBERS) * 20 * 2, len(CHAMBERS) * 20 * 1


def alert_costs():
    """Full alert line (e.g. 'CH1 Tomato Low Humidity') -> (water, energy)."""
    rows = csv_rows(CROPS_FILE, 9)
    costs = {}
    for chamber, crop in CHAMBERS.items():
        for rule_crop, _, _, _, _, water, energy, _severity, message in rows:
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
    # An alert text with no matching crops.csv row means the chamber list or the
    # rules don't match this run (a setup error), not a lost update, so stop here
    # instead of reporting a false defect. Sensor Failure alerts cost nothing, and
    # "null" is an archive slot lost by the legacy ArrayList race.
    unpriced = sorted({a for a in archived if a not in COSTS and "Sensor Failure" not in a and a != "null"})
    if unpriced:
        sys.exit(f"check_invariants: no crops.csv rule for {unpriced[:3]} (chambers: {CHAMBERS_FILE}, crops: {CROPS_FILE})")

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
