# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog,
and this project follows Semantic Versioning.

## [Unreleased]
### Added
- Native JNI path extended with iceberg limit orders and partial-fill visible quantity refresh.
- Added Prometheus metrics server for soak/runtime observability (`PrometheusMetricsServer`).
- Added ops stack and docs (`docker-compose.yml`, Prometheus, Grafana dashboard, `docs/ARCHITECTURE.md`, `docs/RUNBOOK.md`).
- Full order-book state snapshot store with CRC32 and fast restore support.
- Engine-level snapshot APIs: `saveStateSnapshot` and `loadStateSnapshot`.
- Snapshot integrity unit tests.
- JNI + C++ prototype for matching hot path (`insertLimitOrder`, `matchMarketOrder`, `publishL2Update`).
- Native ingress adapter and demo for integrating JNI core with Java top-of-book updates.
- JMH benchmark scaffold for Java vs JNI hot-path comparisons.
- `NativeOrderBook` JNI API as primary Java/C++ integration entrypoint.
- Native Disruptor pipeline demo (`NativeDisruptorPipeline` / `NativeDisruptorDemo`).
- C++ Google Benchmark target (`order_book_insert_bench`) and CI automation job (`cpp-benchmark`).
- Coordinated recovery flow: snapshot capture + checkpoint metadata + journal incremental catch-up replay.
- Coordinated recovery CLI tool (`CoordinatedRecoveryTool`) and integration test coverage.
- Aeron UDP transport profile for unicast ingress + multicast market-data dissemination (`AeronUdpMulticastDemo`).
- Bounded backpressure handling in Aeron transport sinks/gateways (no unbounded spin loops).
- Lower-allocation journal replay path by reusing a single little-endian record view.
- Native C++ order book migrated from `std::map/std::list` to contiguous level vectors with intrusive order queues.
- Native C++ order book switched to fixed-capacity ladders and preallocated node freelist (no runtime container growth in matching hot path).
- `MatchingBackend` switch (`JAVA|NATIVE`) added, with `NATIVE` default in `NativePipelineDemo`.
- CI native smoke now executes Java demo on the default native backend after C++ build.
- ABI/API freeze foundations: `native_api_version` + min-compatible API + binary layout version/hash compatibility checks.
- Added JNI ABI compatibility test coverage (`NativeAbiCompatibilityTest`).
- Added randomized parity fuzz test for Java vs native matching on shared command subset (`NativeParityFuzzTest`).
- Added long-run parity soak harness (`NativeParitySoakTool`) for 6h+ drift detection.
- Added restart parity coverage for coordinated recovery (`snapshot + catch-up + restart + re-snapshot + second restart`).
- Added CI performance gate validator (`scripts/perf/validate_perf.py`) with blocking thresholds for JMH, latency percentiles, throughput, and C++ benchmark.
- Added per-PR performance regression report artifact (`perf-regression-report`).
- Added pre-trade risk gateway (RiskCheckedEngineGateway) with explicit reject codes for rate-limit, fat-finger, and position-limit checks.
- Added deterministic unit tests for risk controls (RiskValidationTest).
- Added native insert status contract (explicit overflow/invalid reject codes) and capacity-aware JNI constructor (`NativeOrderBook(int maxLevels, int maxOrders)`).
- Bumped JNI/native API compatibility version to `2` and added native overflow status test coverage.
- Replaced Java `TreeMap`/`ArrayDeque` core book structures with fixed-capacity ladders, preallocated level pools, and fixed stop queues.
- Added Java core capacity reject coverage (`RejectCode.CAPACITY_EXCEEDED`) for deterministic overflow behavior.
- Added Java allocation harness and blocking CI thresholds for core/pipeline allocation-rate regressions.
- Added blocking soak qualification workflow (`native parity soak + recovery qualification`) with markdown reporting.
- Added transport qualification workflow for Aeron UDP multicast demo with blocking gate report.
- Marked native C++ core as production-ready in documentation after passing qualification gates.

## [0.2.0] - 2026-02-20
### Added
- Single-symbol matching engine with price-time priority, IOC/FOK/GTC, market and stop-market orders.
- Iceberg support and SMP cancel-aggressor policy.
- Disruptor-based staged pipeline with async market-data ring.
- SBE schema and generated codecs for L2/L3 market-data messages.
- Unit tests for core order book behavior.
- JMH benchmarks for core-only and pipeline modes.
- GitHub Actions CI for build/test/coverage and JMH smoke.
- Apache-2.0 license, contribution guide, and security policy.

### Changed
- Project promoted from baseline naming to PulseEngine release line.
- Version bumped to `0.2.0-SNAPSHOT`.



