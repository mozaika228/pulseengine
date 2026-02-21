#!/usr/bin/env python3
import argparse
import json
import re
import sys
from pathlib import Path


THRESHOLDS = {
    "jmh_core_mean_ns_max": 2500.0,
    "jmh_pipeline_mean_ns_max": 60000.0,
    "latency_p50_ns_max": 5000.0,
    "latency_p99_ns_max": 20000.0,
    "latency_p9999_ns_max": 100000.0,
    "pipeline_throughput_ops_min": 100000.0,
    # CI-hosted runners are noisy; this benchmark measures full insert batch case.
    "cpp_insert_ns_max": 1500000.0,
}


def load_jmh_score(path: Path) -> float:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not data:
        raise RuntimeError(f"no JMH entries in {path}")
    return float(data[0]["primaryMetric"]["score"])


def parse_key_value_text(path: Path) -> dict[str, float]:
    out: dict[str, float] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\s*([A-Za-z0-9_]+)\s*=\s*([-+]?[0-9]*\.?[0-9]+)\s*$", line)
        if m:
            out[m.group(1)] = float(m.group(2))
    return out


def load_cpp_insert_score(path: Path) -> float:
    data = json.loads(path.read_text(encoding="utf-8"))
    benchmarks = data.get("benchmarks", [])
    if not benchmarks:
        raise RuntimeError(f"no benchmark entries in {path}")

    # Pick the heaviest insert case to gate tail behavior.
    target_name = "BM_InsertLimitOrder/100000"
    for b in benchmarks:
        if b.get("name") == target_name:
            return float(b["real_time"])

    # fallback: max real_time among BM_InsertLimitOrder variants
    values = [float(b["real_time"]) for b in benchmarks if str(b.get("name", "")).startswith("BM_InsertLimitOrder/")]
    if not values:
        raise RuntimeError(f"BM_InsertLimitOrder entries not found in {path}")
    return max(values)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jmh-core", required=True, type=Path)
    parser.add_argument("--jmh-pipeline", required=True, type=Path)
    parser.add_argument("--latency-harness", required=True, type=Path)
    parser.add_argument("--pipeline-demo", required=True, type=Path)
    parser.add_argument("--cpp-bench", required=True, type=Path)
    parser.add_argument("--out-report", required=True, type=Path)
    args = parser.parse_args()

    jmh_core = load_jmh_score(args.jmh_core)
    jmh_pipeline = load_jmh_score(args.jmh_pipeline)
    latency = parse_key_value_text(args.latency_harness)
    pipeline = parse_key_value_text(args.pipeline_demo)
    cpp_insert = load_cpp_insert_score(args.cpp_bench)

    p50 = latency.get("latency_ns_p50")
    p99 = latency.get("latency_ns_p99")
    p9999 = latency.get("latency_ns_p9999")
    throughput = pipeline.get("throughput_ops")

    missing = []
    for key, value in [
        ("latency_ns_p50", p50),
        ("latency_ns_p99", p99),
        ("latency_ns_p9999", p9999),
        ("throughput_ops", throughput),
    ]:
        if value is None:
            missing.append(key)
    if missing:
        raise RuntimeError(f"missing metrics in outputs: {', '.join(missing)}")

    checks = [
        ("jmh_core_mean_ns", jmh_core, "<=", THRESHOLDS["jmh_core_mean_ns_max"]),
        ("jmh_pipeline_mean_ns", jmh_pipeline, "<=", THRESHOLDS["jmh_pipeline_mean_ns_max"]),
        ("latency_p50_ns", p50, "<=", THRESHOLDS["latency_p50_ns_max"]),
        ("latency_p99_ns", p99, "<=", THRESHOLDS["latency_p99_ns_max"]),
        ("latency_p9999_ns", p9999, "<=", THRESHOLDS["latency_p9999_ns_max"]),
        ("pipeline_throughput_ops", throughput, ">=", THRESHOLDS["pipeline_throughput_ops_min"]),
        ("cpp_insert_ns", cpp_insert, "<=", THRESHOLDS["cpp_insert_ns_max"]),
    ]

    lines = ["# Performance Regression Report", "", "| Metric | Value | Threshold | Status |", "|---|---:|---:|---|"]
    failed = False
    for name, value, op, threshold in checks:
        ok = (value <= threshold) if op == "<=" else (value >= threshold)
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
