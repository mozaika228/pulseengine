# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog,
and this project follows Semantic Versioning.

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
