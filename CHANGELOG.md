# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog,
and this project follows Semantic Versioning.

## [Unreleased]
### Added
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
