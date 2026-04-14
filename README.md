# PulseEngine

[![CI](https://github.com/mozaika228/pulseengine/actions/workflows/ci.yml/badge.svg)](https://github.com/mozaika228/pulseengine/actions/workflows/ci.yml)
[![Nightly Performance](https://github.com/mozaika228/pulseengine/actions/workflows/nightly-performance.yml/badge.svg)](https://github.com/mozaika228/pulseengine/actions/workflows/nightly-performance.yml)
[![Coverage](https://codecov.io/gh/mozaika228/pulseengine/graph/badge.svg)](https://codecov.io/gh/mozaika228/pulseengine)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

> Production-ready HFT matching engine with nanosecond-class execution and deterministic recovery.

PulseEngine is built for teams that need exchange-style matching behavior without starting from a research prototype.

## At a glance
- `~143 ns/op` Java matching path (`NativeVsJavaBenchmark.javaOrderBookMarketMatch`, JMH)
- `~6.49M ops/s` pipeline throughput (qualification harness)
- `p99.99 ~10.9 us` latency in final smoke profile
- journal + CRC + replay + snapshot/restore + coordinated catch-up
- JNI/C++ native hot path with ABI compatibility checks

## Why PulseEngine?
- Fast by default: low-latency matching path with fixed-capacity structures
- Predictable under load: explicit overflow/reject behavior instead of silent degradation
- Recovery-ready: restart-safe state restoration and replay tooling included
- Qualification-gated: perf, soak, recovery, and transport checks are release blockers

## How it works (simplified)
Order flow:

Ingress -> Risk -> Matching -> Market Data -> Journal

## Maturity
- Current release line: `1.0.2`
- CI, unit tests, coverage, and JMH automation are enabled.

## Production Status Statement
PulseEngine `1.0.2` is production-ready for single-symbol in-process deployment with gated CI qualification.

Included in release scope:
- deterministic matching core (Java + JNI/C++) with explicit capacity rejects
- journaling + CRC verification/repair + replay
- full snapshot/restore + coordinated catch-up replay (`snapshot + checkpoint + journal`)
- Aeron IPC and Aeron UDP/multicast transport qualification gates
- release qualification artifacts and blocking performance/soak/recovery checks

Current boundary:
- single-symbol engine profile; horizontal scale is achieved by symbol sharding across engine instances.

## Performance
PulseEngine is optimized for ultra-low latency matching:

- ~143 ns per operation on the Java path (`NativeVsJavaBenchmark.javaOrderBookMarketMatch`, JMH)
- native path integrated through JNI and tuned for lower latency under production qualification gates

Designed for environments where every microsecond matters.

## Implemented now
### Matching
- price-time priority matching (single symbol)
- limit, market, stop-market orders
- TIF: GTC, IOC, FOK
- partial fills
- SMP policy: cancel aggressor
- optional iceberg (`peakSize` in `OrderRequest`)
- fixed-capacity price ladders + preallocated order/stop pools in Java matching core (no TreeMap/ArrayDeque in the hot path)

### Pipeline
- staged pipeline: `risk -> match -> market-data` on LMAX Disruptor
- market data can run async on a dedicated ring/thread (`match -> md`) with L2 batching per batch boundary
- non-blocking submit APIs are available (`trySubmit*`) to support wait-free/backpressure-aware ingress
- top-of-book view (`TopOfBookView`) updated by market-data stage

### Market Data
- binary market-data publisher (SBE schema-driven)
- snapshot L2 message
- incremental L2 delta message (only on change)
- snapshot L2 depth-N message (periodic + initial)
- incremental L3 message (`add/modify/cancel/trade`)
- Aeron IPC transport demo (`AeronIpcDemo`) for order ingress and market-data dissemination
- Aeron UDP transport profile demo (`AeronUdpMulticastDemo`) for unicast ingress + multicast market data

### Recovery
- append-only command journal and replay utility (`JournalReplayDemo`)
- journal integrity tooling with CRC32 verification/repair (`JournalRecoveryTool`)
- full order-book state snapshotting with checksum validation and fast restore APIs (`saveStateSnapshot/loadStateSnapshot`)
- coordinated recovery tooling: snapshot + journal checkpoint + incremental catch-up replay

### Native
- JNI bridge for native C++ matching hot path (`NativeOrderBook`)
- JNI ingress adapter for Java pipeline integration (`NativeIngressAdapter`)

## Run
- `mvn -q -DskipTests=false verify`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.LatencyHarness"`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.PipelineDemo"`
- PowerShell (SBE/Agrona runtime): `$env:MAVEN_OPTS='--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED'; mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.BinaryFeedDemo"`
- PowerShell (Aeron IPC): `$env:MAVEN_OPTS='--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED'; mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.AeronIpcDemo"`
- PowerShell (Aeron UDP/multicast): `$env:MAVEN_OPTS='--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED'; mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.AeronUdpMulticastDemo"`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.JournalReplayDemo"`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.JournalRecoveryTool" "-Dexec.args=verify target/orders.journal.bin"`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.CoordinatedRecoveryTool" "-Dexec.args=capture target/orders.journal.bin target/orders.snapshot.bin target/orders.checkpoint.bin"`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.CoordinatedRecoveryTool" "-Dexec.args=restore target/orders.journal.bin target/orders.snapshot.bin target/orders.checkpoint.bin"`

## High-performance C++ core (production-ready)
- Java wrapper: `src/main/java/io/pulseengine/jni/NativeOrderBook.java`
- Backward-compatible wrapper: `src/main/java/io/pulseengine/jni/NativeMatchingEngine.java`
- ABI/layout contract: `src/main/java/io/pulseengine/jni/NativeOrderBinaryLayout.java` + runtime JNI compatibility check
- Native core: `cpp/src/native_order_book.hpp`
- JNI bridge: `cpp/src/pulseengine_native.cpp`
- CMake project: `cpp/CMakeLists.txt`
- Build example (Linux/macOS):
  - `cd cpp && cmake .. && make -j`
- Build example (PowerShell):
  - `cmake -S cpp -B cpp/build -DCMAKE_BUILD_TYPE=Release -DPULSEENGINE_BUILD_BENCHMARKS=ON`
  - `cmake --build cpp/build --config Release`
- Runtime:
  - place `pulseengine_native.dll`/`libpulseengine_native.so` on `java.library.path`
  - use `io.pulseengine.jni.NativeOrderBook` in the Java pipeline ingress/hot path
  - startup validates native ABI (`native_api_version`, `native_min_compatible_api_version`) and binary layout hash/version (current client API: `2`)
- Integration demo:
  - `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.NativePipelineDemo"`
  - `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.NativeDisruptorDemo"`
  - pass `-Djava.library.path=<path-to-native-lib>` to JVM if required
  - backend switch: `-Dpulseengine.matchingBackend=JAVA|NATIVE` (default: `NATIVE`)
- Java vs JNI benchmark:
  - `mvn -q -DskipTests test-compile`
  - `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=org.openjdk.jmh.Main" "-Dexec.args=NativeVsJavaBenchmark.* -wi 3 -i 5 -f 0 -tu ns"`
- C++ Google Benchmark (insert-limit):
  - `./cpp/build/order_book_insert_bench --benchmark_format=json --benchmark_out=cpp/build/order_book_insert_bench.json`
- Native insert status API: `tryInsertLimitOrder` / `tryInsertLimitIceberg` returns explicit reject codes (`INVALID_QTY`, `BOOK_LEVELS_FULL`, `ORDER_POOL_EXHAUSTED`) instead of silent fallback.
- Property/fuzz parity test (Java vs native):
  - `mvn -q -Dtest=NativeParityFuzzTest test`
- Soak parity tool (default 6h):
  - `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.NativeParitySoakTool" "-Dexec.args=21600 20260221"`

## Final Benchmark Table (Java vs C++)
- Code footprint (core Java + C++ sources): Java `3760` LOC, C++ `220` LOC, C++ share `5.53%`.
- Latency/throughput snapshot:

| Path | Tool | Scenario | Result |
|---|---|---|---|
| Java order book | JMH | `NativeVsJavaBenchmark.javaOrderBookMarketMatch` | `0.007 ops/ns` (~`143 ns/op`) |
| C++ order book | Google Benchmark | `BM_InsertLimitOrder/100000` | `656650.28 ns/op` (latest CI sample) |

## Tests and coverage
- Unit tests: `src/test/java/io/pulseengine/core/OrderBookTest.java`
- JaCoCo report: `target/site/jacoco/index.html`

## JMH benchmarks
- `mvn -q -DskipTests test-compile`
- Core-only: `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=org.openjdk.jmh.Main" "-Dexec.args=CoreOrderBookBenchmark.iocCrossingOrder -wi 3 -i 5 -f 0 -tu ns"`
- Pipeline: `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=org.openjdk.jmh.Main" "-Dexec.args=PipelineBenchmark.iocCrossingOrder -p enableMd=false -wi 3 -i 5 -f 0 -tu ns"`

## CI automation
- Build/test/coverage: `.github/workflows/ci.yml`
- Nightly performance regression checks: `.github/workflows/nightly-performance.yml` (JMH + latency + allocation + C++ benchmark + perf report)
- Soak qualification workflow: `.github/workflows/soak-qualification.yml` (native parity soak + recovery qualification gates)
- Transport qualification workflow: `.github/workflows/transport-qualification.yml` (Aeron UDP multicast demo + strict transport gate report)
- Staging canary workflow: `.github/workflows/staging-canary.yml` (short soak + transport sanity + canary report)
- Finalization smoke workflows: `.github/workflows/finalization-smoke.yml`, `.github/workflows/finalization-smoke-v2.yml`
- Release qualification workflow: `.github/workflows/release-qualification.yml` (release-blocking SLO gates + `release-qualification-pack`)
- Gated release publishing: `.github/workflows/release-gated-publish.yml` (publishes only after qualification pass)
- Native backend smoke uses `NativePipelineDemo` with default backend (`NATIVE`) in CI.
- Release/perf gates are blocking and produce markdown artifacts for audit.

## Governance
- Changelog: `CHANGELOG.md`
- Contributing guide: `CONTRIBUTING.md`
- Security policy: `SECURITY.md`
- License: `LICENSE`

## Qualification Status
- Java and native matching paths run on fixed-capacity hot-path structures with explicit overflow rejects.
- Blocking CI gates cover latency, throughput, allocation-rate, native benchmark regressions, soak parity, recovery, transport qualification, and staging canary checks.
- Release publication is gated by qualification status and private advisory security policy is enforced.









