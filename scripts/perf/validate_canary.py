#!/usr/bin/env python3
import argparse
import re
import sys
from pathlib import Path

KV_RE = re.compile(r"^\s*([A-Za-z0-9_]+)\s*=\s*([-+]?[0-9]*\.?[0-9]+)\s*$")


def parse_kv(path: Path) -> dict[str, float]:
    values: dict[str, float] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        m = KV_RE.match(line)
        if m:
            values[m.group(1)] = float(m.group(2))
    return values


def metric(values: dict[str, float], key: str) -> float:
    if key not in values:
        raise RuntimeError(f"missing metric: {key}")
    return values[key]


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--soak", required=True, type=Path)
    p.add_argument("--transport", required=True, type=Path)
    p.add_argument("--min-throughput", type=float, default=100000.0)
    p.add_argument("--out", required=True, type=Path)
    args = p.parse_args()

    soak = parse_kv(args.soak)
    transport = parse_kv(args.transport)

    checks = [
        ("soak_ok", metric(soak, "soak_ok") == 1.0),
        ("parity_drift_total", metric(soak, "parity_drift_total") == 0.0),
        ("throughput_ops", metric(soak, "throughput_ops") >= args.min_throughput),
        ("aeron_orders_processed", metric(transport, "aeron_orders_processed") == 4.0),
        ("aeron_md_fragments", metric(transport, "aeron_md_fragments") > 0.0),
    ]

    lines = ["# Staging Canary Report", "", "| Gate | Status |", "|---|---|"]
    failed = False
    for name, ok in checks:
        status = "PASS" if ok else "FAIL"
        lines.append(f"| `{name}` | **{status}** |")
        if not ok:
            failed = True

    lines.append("")
    lines.append(f"canary_status={'FAIL' if failed else 'PASS'}")

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(args.out.read_text(encoding="utf-8"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
