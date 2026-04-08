#!/usr/bin/env python3
import argparse
import re
import shutil
import sys
from pathlib import Path


ROW_RE = re.compile(r"^\|\s*`([^`]+)`\s*\|\s*`?([^|`]+?)`?\s*\|\s*`?([^|`]+?)`?\s*\|\s*\*\*(PASS|FAIL)\*\*\s*\|\s*$")
KV_RE = re.compile(r"^\s*([A-Za-z0-9_]+)\s*=\s*([-+]?[0-9]*\.?[0-9]+)\s*$")


def parse_markdown_checks(path: Path) -> tuple[list[tuple[str, str, str, str]], list[str]]:
    checks: list[tuple[str, str, str, str]] = []
    failed: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        m = ROW_RE.match(line.strip())
        if not m:
            continue
        metric, value, threshold, status = m.groups()
        checks.append((metric, value.strip(), threshold.strip(), status))
        if status == "FAIL":
            failed.append(metric)
    if not checks:
        raise RuntimeError(f"no metric rows found in {path}")
    return checks, failed


def parse_recovery_seconds(path: Path) -> float:
    for line in path.read_text(encoding="utf-8").splitlines():
        m = KV_RE.match(line)
        if m and m.group(1) == "recovery_qualification_sec":
            return float(m.group(2))
    raise RuntimeError(f"missing recovery_qualification_sec in {path}")


def summarize_section(lines: list[str], title: str, checks: list[tuple[str, str, str, str]]) -> None:
    lines.append(f"## {title}")
    lines.append("")
    lines.append("| Metric | Value | Threshold | Status |")
    lines.append("|---|---:|---:|---|")
    for metric, value, threshold, status in checks:
        lines.append(f"| `{metric}` | `{value}` | `{threshold}` | **{status}** |")
    lines.append("")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--perf-report", required=True, type=Path)
    parser.add_argument("--soak-report", required=True, type=Path)
    parser.add_argument("--transport-report", required=True, type=Path)
    parser.add_argument("--recovery-metrics", required=True, type=Path)
    parser.add_argument("--max-recovery-sec", required=True, type=float)
    parser.add_argument("--out-report", required=True, type=Path)
    parser.add_argument("--out-pack-dir", required=True, type=Path)
    args = parser.parse_args()

    perf_checks, perf_failed = parse_markdown_checks(args.perf_report)
    soak_checks, soak_failed = parse_markdown_checks(args.soak_report)
    transport_checks, transport_failed = parse_markdown_checks(args.transport_report)

    recovery_sec = parse_recovery_seconds(args.recovery_metrics)
    recovery_ok = recovery_sec <= args.max_recovery_sec
    recovery_checks = [(
        "recovery_qualification_sec",
        f"{recovery_sec:.2f}",
        f"<= {args.max_recovery_sec:.2f}",
        "PASS" if recovery_ok else "FAIL",
    )]

    failed = []
    failed.extend([f"perf:{m}" for m in perf_failed])
    failed.extend([f"soak:{m}" for m in soak_failed])
    failed.extend([f"transport:{m}" for m in transport_failed])
    if not recovery_ok:
        failed.append("recovery:recovery_qualification_sec")

    lines = ["# Release Qualification Report", ""]
    summarize_section(lines, "Performance", perf_checks)
    summarize_section(lines, "Soak", soak_checks)
    summarize_section(lines, "Transport", transport_checks)
    summarize_section(lines, "Recovery", recovery_checks)

    lines.append("## Verdict")
    lines.append("")
    if failed:
        lines.append("release_qualification=FAIL")
        lines.append("")
        lines.append("Failed gates:")
        for gate in failed:
            lines.append(f"- `{gate}`")
    else:
        lines.append("release_qualification=PASS")

    args.out_report.parent.mkdir(parents=True, exist_ok=True)
    args.out_report.write_text("\n".join(lines) + "\n", encoding="utf-8")

    args.out_pack_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(args.perf_report, args.out_pack_dir / "perf-regression-report.md")
    shutil.copy2(args.soak_report, args.out_pack_dir / "soak-qualification-report.md")
    shutil.copy2(args.transport_report, args.out_pack_dir / "transport-qualification-report.md")
    shutil.copy2(args.recovery_metrics, args.out_pack_dir / "recovery-qualification-metrics.txt")
    shutil.copy2(args.out_report, args.out_pack_dir / "release-qualification-report.md")

    print(args.out_report.read_text(encoding="utf-8"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
