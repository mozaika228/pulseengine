#!/usr/bin/env python3
import argparse
import re
import sys
from pathlib import Path


def parse_key_values(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    pair_re = re.compile(r"([A-Za-z0-9_]+)\s*=\s*([^\s]+)")
    for line in path.read_text(encoding="utf-8").splitlines():
        for match in pair_re.finditer(line):
            values[match.group(1)] = match.group(2)
    return values


def as_int(values: dict[str, str], key: str) -> int:
    if key not in values:
        raise RuntimeError(f"missing metric: {key}")
    return int(values[key])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--transport", required=True, type=Path)
    parser.add_argument("--out-report", required=True, type=Path)
    args = parser.parse_args()

    values = parse_key_values(args.transport)
    orders_processed = as_int(values, "aeron_orders_processed")
    md_fragments = as_int(values, "aeron_md_fragments")
    best_bid = as_int(values, "best_bid")
    best_ask = as_int(values, "best_ask")

    checks = [
        ("aeron_orders_processed", orders_processed, "==", 4),
        ("aeron_md_fragments", md_fragments, ">", 0),
        ("best_bid", best_bid, "==", 49900),
        ("best_ask", best_ask, "==", 50100),
    ]

    lines = [
        "# Transport Qualification Report",
        "",
        "| Metric | Value | Threshold | Status |",
        "|---|---:|---:|---|",
    ]
    failed = False
    for name, value, op, threshold in checks:
        if op == "==":
            ok = value == threshold
        else:
            ok = value > threshold
        status = "PASS" if ok else "FAIL"
        lines.append(f"| `{name}` | `{value}` | `{op} {threshold}` | **{status}** |")
        if not ok:
            failed = True

    args.out_report.parent.mkdir(parents=True, exist_ok=True)
    args.out_report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(args.out_report.read_text(encoding="utf-8"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
