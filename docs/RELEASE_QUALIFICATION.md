# Release Qualification

This document defines release-blocking gates and the release qualification pack artifacts.

## Release-Blocking SLO Gates

| Domain | Metric | Threshold | Evidence |
|---|---|---|---|
| Core performance | `jmh_core_mean_ns` | `<= 2500` ns | `perf-regression-report.md` |
| Pipeline performance | `jmh_pipeline_mean_ns` | `<= 60000` ns | `perf-regression-report.md` |
| Latency | `latency_p50_ns` | `<= 5000` ns | `perf-regression-report.md` |
| Latency | `latency_p99_ns` | `<= 20000` ns | `perf-regression-report.md` |
| Latency | `latency_p9999_ns` | `<= 100000` ns | `perf-regression-report.md` |
| Throughput | `pipeline_throughput_ops` | `>= 100000` ops/s | `perf-regression-report.md` |
| Allocation | `core_alloc_bytes_per_op` | `<= 8` B/op | `perf-regression-report.md` |
| Allocation | `pipeline_core_alloc_bytes_per_op` | `<= 8` B/op | `perf-regression-report.md` |
| Native benchmark | `cpp_insert_ns` | `<= 1500000` ns | `perf-regression-report.md` |
| Soak stability | `soak_ok` | `== true` | `soak-qualification-report.md` |
| Soak parity | `parity_drift_total` | `== 0` | `soak-qualification-report.md` |
| Transport | `aeron_orders_processed` | `== 4` | `transport-qualification-report.md` |
| Transport | `aeron_md_fragments` | `> 0` | `transport-qualification-report.md` |
| Recovery runtime | `recovery_qualification_sec` | `<= 900` sec | `recovery-qualification-metrics.txt` |

Any failed gate blocks release promotion.

## Release Qualification Pack

The workflow `.github/workflows/release-qualification.yml` builds one artifact:

- `release-qualification-pack`

Artifact contents:

- `release-qualification-report.md`
- `perf-regression-report.md`
- `soak-qualification-report.md`
- `transport-qualification-report.md`
- `recovery-qualification-metrics.txt`

## Required Procedure

1. Run `Release Qualification` workflow on the release tag.
2. Verify `release_qualification=PASS` in `release-qualification-report.md`.
3. Attach `release-qualification-pack` artifact reference in release notes.
4. Do not publish release if any gate fails.
