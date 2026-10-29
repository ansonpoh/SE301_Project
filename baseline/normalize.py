"""Turn one run's stdout + daily_report.txt into a stable text file for diffing.

Usage: python3 -I normalize.py <stdout.txt> <daily_report.txt> > normalized.txt

What is removed or reshaped (and why):
  - "Timestamp: ..." in the report   -> changes every run
  - "Archive report saved to: <abs path>" -> depends on working directory
  - ANSI colour codes                 -> cosmetic
  - CONTROL lines                     -> their position and numeric values depend on
                                         control-thread timing, so they are reported
                                         as per-chamber, per-device counts instead.
Everything else (alert lines in order, report totals, chamber summary, archived
alert list) is kept exactly.
"""
import collections
import re
import sys

ANSI = re.compile(r"\x1b\[[0-9;]*m")
CONTROL = re.compile(r"^\s*\[(\w+)\]\[CONTROL\] (.+ Activated)$")


def main(stdout_path, report_path):
    control_counts = collections.Counter()
    print("##### STDOUT (control lines removed) #####")
    with open(stdout_path, encoding="utf-8") as f:
        for raw in f:
            line = ANSI.sub("", raw.rstrip("\n"))
            if "[CONTROL]" in line:
                m = CONTROL.match(line)
                if m:
                    control_counts[(m.group(1), m.group(2))] += 1
                continue
            if line.startswith("Archive report saved to:"):
                line = "Archive report saved to: <path>"
            print(line)

    print("##### CONTROL ACTIONS (count per chamber/device) #####")
    for (chamber, action), n in sorted(control_counts.items()):
        print(f"{chamber} {action}: {n}")

    print("##### daily_report.txt #####")
    with open(report_path, encoding="utf-8") as f:
        for raw in f:
            line = raw.rstrip("\n")
            if line.startswith("Timestamp: "):
                line = "Timestamp: <removed>"
            print(line)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
