#!/usr/bin/env python3
import argparse
import re
import sys
from pathlib import Path


def parse_key_values(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"^\s*([A-Za-z0-9_]+)\s*=\s*(.+?)\s*$", line)
        if match:
            values[match.group(1)] = match.group(2)
    return values


def as_float(values: dict[str, str], key: str) -> float:
    if key not in values:
        raise RuntimeError(f"missing metric: {key}")
    return float(values[key])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--soak", required=True, type=Path)
    parser.add_argument("--min-duration-sec", required=True, type=float)
    parser.add_argument("--min-throughput-ops", required=True, type=float)
    parser.add_argument("--out-report", required=True, type=Path)
    args = parser.parse_args()

    values = parse_key_values(args.soak)
    soak_ok = values.get("soak_ok", "false").lower() == "true"
    drift = as_float(values, "parity_drift_total") if "parity_drift_total" in values else 0.0
    duration = as_float(values, "duration_sec")
    ops = as_float(values, "ops")
    throughput = as_float(values, "throughput_ops")
    seed = values.get("seed", "unknown")

    checks = [
        ("soak_ok", 1.0 if soak_ok else 0.0, "==", 1.0),
        ("parity_drift_total", drift, "==", 0.0),
        ("duration_sec", duration, ">=", args.min_duration_sec),
        ("ops", ops, ">=", 1.0),
        ("throughput_ops", throughput, ">=", args.min_throughput_ops),
    ]

    lines = [
        "# Soak Qualification Report",
        "",
        f"seed=`{seed}`",
        "",
        "| Metric | Value | Threshold | Status |",
        "|---|---:|---:|---|",
    ]
    failed = False
    for name, value, op, threshold in checks:
        if op == "==":
            ok = value == threshold
        else:
            ok = value >= threshold
        status = "PASS" if ok else "FAIL"
        lines.append(f"| `{name}` | `{value:.2f}` | `{op} {threshold:.2f}` | **{status}** |")
        if not ok:
            failed = True

    args.out_report.parent.mkdir(parents=True, exist_ok=True)
    args.out_report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(args.out_report.read_text(encoding="utf-8"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())