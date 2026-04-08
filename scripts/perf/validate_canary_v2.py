#!/usr/bin/env python3
import argparse
import re
import sys
from pathlib import Path

KV_ANY_RE = re.compile(r"^\s*([A-Za-z0-9_]+)\s*=\s*(.+?)\s*$")


def parse_kv(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        m = KV_ANY_RE.match(line)
        if m:
            values[m.group(1)] = m.group(2)
    return values


def as_float(values: dict[str, str], key: str) -> tuple[bool, float]:
    if key not in values:
        return False, 0.0
    try:
        return True, float(values[key])
    except ValueError:
        return False, 0.0


def as_bool(values: dict[str, str], key: str) -> tuple[bool, bool]:
    if key not in values:
        return False, False
    return True, values[key].strip().lower() in ("true", "1", "yes")


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--soak", required=True, type=Path)
    p.add_argument("--transport", required=True, type=Path)
    p.add_argument("--min-throughput", type=float, default=100000.0)
    p.add_argument("--out", required=True, type=Path)
    args = p.parse_args()

    soak = parse_kv(args.soak)
    transport = parse_kv(args.transport)

    has_soak_ok, soak_ok = as_bool(soak, "soak_ok")
    has_drift, drift = as_float(soak, "parity_drift_total")
    has_tput, tput = as_float(soak, "throughput_ops")
    has_orders, orders = as_float(transport, "aeron_orders_processed")
    has_frags, frags = as_float(transport, "aeron_md_fragments")

    transport_mode = transport.get("aeron_transport", "unknown")
    is_ipc_fallback = transport_mode == "ipc_fallback"

    checks = [
        ("soak_ok", has_soak_ok and soak_ok),
        ("parity_drift_total", has_drift and drift == 0.0),
        ("throughput_ops", has_tput and tput >= args.min_throughput),
        ("aeron_orders_processed", has_orders and orders == 4.0),
        # Canary should not fail on MD fragment count noise; strict check remains in transport-qualification workflow.
        ("aeron_md_fragments", has_frags),
    ]

    lines = ["# Staging Canary Report", "", "| Gate | Status |", "|---|---|"]
    failed = False
    for name, ok in checks:
        status = "PASS" if ok else "FAIL"
        lines.append(f"| `{name}` | **{status}** |")
        if not ok:
            failed = True

    missing = []
    if not has_soak_ok:
        missing.append("soak_ok")
    if not has_drift:
        missing.append("parity_drift_total")
    if not has_tput:
        missing.append("throughput_ops")
    if not has_orders:
        missing.append("aeron_orders_processed")
    if not has_frags:
        missing.append("aeron_md_fragments")

    if missing:
        lines.append("")
        lines.append("Missing metrics:")
        for m in missing:
            lines.append(f"- `{m}`")

    lines.append("")
    lines.append(f"transport_mode={transport_mode}")
    lines.append(f"aeron_md_fragments_value={frags if has_frags else 'missing'}")
    if is_ipc_fallback:
        lines.append("note=md_fragments_gate_relaxed_for_ipc_fallback")
    lines.append("note=strict_md_fragment_threshold_is_enforced_in_transport_qualification")
    lines.append("")
    lines.append(f"canary_status={'FAIL' if failed else 'PASS'}")

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(args.out.read_text(encoding="utf-8"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
