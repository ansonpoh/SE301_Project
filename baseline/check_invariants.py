"""Detect concurrency defects in a run of the ORIGINAL concurrent main().

Usage: python3 -I check_invariants.py <stdout.txt> <daily_report.txt>
Prints one line: OK | DEFECT: <reasons>

The legacy code updates its counters in a fixed way, so the final totals can be
recomputed from the archived alert list. If they don't match, an update was lost.
  water  = 4 chambers * 20 iterations * 2  + per-alert water
  energy = 4 chambers * 20 iterations * 1  + per-alert energy
  Generated Alerts == number of archived alerts == sum of chamber "Alerts="
These invariants only hold if every chamber thread finished, so a crashed thread
is reported on its own.
"""
import re
import sys

WATER = {"Tomato Low Humidity": 20, "Strawberry Low Humidity": 25, "Tomato Low Soil Moisture": 50}
ENERGY = {"Tomato High Temp": 10, "Tomato Low Temp": 15, "Strawberry High Temp": 10, "Strawberry Low Temp": 15}
BASE_WATER, BASE_ENERGY = 4 * 20 * 2, 4 * 20 * 1


def main(stdout_path, report_path):
    out = open(stdout_path, encoding="utf-8").read()
    lines = open(report_path, encoding="utf-8").read().splitlines()

    def total(label):
        return int(next(l for l in lines if l.startswith(label)).split(":")[1].split()[0])

    archived = lines[lines.index("--- archived alerts ---") + 1:] if "--- archived alerts ---" in lines else []
    summary_alerts = sum(int(m) for m in re.findall(r"Alerts=(\d+)", "\n".join(lines)))
    chambers = len(re.findall(r"^CH\d -> ", "\n".join(lines), re.M))

    problems = []
    crashes = re.findall(r'Exception in thread "[^"]+" (\S+)', out)  # legacy: thread died
    crashes += re.findall(r"ERROR: chamber \S+ failed: (\S+)", out)  # refactored: reported failure
    if crashes:
        problems.append("thread crashed: " + ", ".join(sorted(set(crashes))))
    else:
        exp_water = BASE_WATER + sum(v for k, v in WATER.items() for a in archived if a.endswith(k))
        exp_energy = BASE_ENERGY + sum(v for k, v in ENERGY.items() for a in archived if a.endswith(k))
        if total("Water Usage") != exp_water:
            problems.append(f"water {total('Water Usage')} != expected {exp_water} (lost update)")
        if total("Energy Usage") != exp_energy:
            problems.append(f"energy {total('Energy Usage')} != expected {exp_energy} (lost update)")
        if chambers != 4:
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
