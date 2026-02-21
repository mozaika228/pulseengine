# PulseEngine

[![CI](https://github.com/mozaika228/pulseengine/actions/workflows/ci.yml/badge.svg)](https://github.com/mozaika228/pulseengine/actions/workflows/ci.yml)
[![Nightly Performance](https://github.com/mozaika228/pulseengine/actions/workflows/nightly-performance.yml/badge.svg)](https://github.com/mozaika228/pulseengine/actions/workflows/nightly-performance.yml)
[![Coverage](https://codecov.io/gh/mozaika228/pulseengine/graph/badge.svg)](https://codecov.io/gh/mozaika228/pulseengine)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

In-process single-symbol matching core for Java 21 with a staged Disruptor pipeline.

## Maturity
- Current release line: `0.2.0-SNAPSHOT`
- CI, unit tests, coverage, and JMH automation are enabled.

## Implemented now
- price-time priority matching (single symbol)
- limit, market, stop-market orders
- TIF: GTC, IOC, FOK
- partial fills
- SMP policy: cancel aggressor
- optional iceberg (`peakSize` in `OrderRequest`)
- order/stop object pooling in matching core (lower GC pressure)
- staged pipeline: `risk -> match -> market-data` on LMAX Disruptor
- market data can run async on a dedicated ring/thread (`match -> md`) with L2 batching per batch boundary
- non-blocking submit APIs are available (`trySubmit*`) to support wait-free/backpressure-aware ingress
- top-of-book view (`TopOfBookView`) updated by market-data stage
- binary market-data publisher (SBE schema-driven):
  - snapshot L2 message
  - incremental L2 delta message (only on change)
  - snapshot L2 depth-N message (periodic + initial)
  - incremental L3 message (`add/modify/cancel/trade`)
- latency harness (`LatencyHarness`)
- throughput demo (`PipelineDemo`)
- binary feed demo with decoder (`BinaryFeedDemo`)
- Aeron IPC transport demo (`AeronIpcDemo`) for order ingress and market-data dissemination
- append-only command journal and replay utility (`JournalReplayDemo`)
- journal integrity tooling with CRC32 verification/repair (`JournalRecoveryTool`)
- full order-book state snapshotting with checksum validation and fast restore APIs (`saveStateSnapshot/loadStateSnapshot`)
- JNI bridge for native C++ matching hot path (`NativeOrderBook`)
- JNI ingress adapter for Java pipeline integration (`NativeIngressAdapter`)

## Run
- `mvn -q -DskipTests=false verify`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.LatencyHarness"`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.PipelineDemo"`
- PowerShell (SBE/Agrona runtime): `$env:MAVEN_OPTS='--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED'; mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.BinaryFeedDemo"`
- PowerShell (Aeron IPC): `$env:MAVEN_OPTS='--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED'; mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.AeronIpcDemo"`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.JournalReplayDemo"`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.JournalRecoveryTool" "-Dexec.args=verify target/orders.journal.bin"`

## High-performance C++ core (experimental)
- Java wrapper: `src/main/java/io/pulseengine/jni/NativeOrderBook.java`
- Backward-compatible wrapper: `src/main/java/io/pulseengine/jni/NativeMatchingEngine.java`
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
- Integration demo:
  - `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.NativePipelineDemo"`
  - `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.NativeDisruptorDemo"`
  - pass `-Djava.library.path=<path-to-native-lib>` to JVM if required
- Java vs JNI benchmark:
  - `mvn -q -DskipTests test-compile`
  - `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=org.openjdk.jmh.Main" "-Dexec.args=NativeVsJavaBenchmark.* -wi 3 -i 5 -f 0 -tu ns"`
- C++ Google Benchmark (insert-limit):
  - `./cpp/build/order_book_insert_bench --benchmark_format=json --benchmark_out=cpp/build/order_book_insert_bench.json`

## Early numbers
- Code footprint (core Java + C++ sources): Java `3760` LOC, C++ `220` LOC, C++ share `5.53%`.
- Latency/throughput snapshot:

| Path | Tool | Scenario | Result |
|---|---|---|---|
| Java order book | JMH | `NativeVsJavaBenchmark.javaOrderBookMarketMatch` | `0.008 ops/ns` (~`125 ns/op`) |
| C++ order book | Google Benchmark | `BM_InsertLimitOrder` | pending CI artifact (`cpp-benchmark-results`) |

## Tests and coverage
- Unit tests: `src/test/java/io/pulseengine/core/OrderBookTest.java`
- JaCoCo report: `target/site/jacoco/index.html`

## JMH benchmarks
- `mvn -q -DskipTests test-compile`
- Core-only: `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=org.openjdk.jmh.Main" "-Dexec.args=CoreOrderBookBenchmark.iocCrossingOrder -wi 3 -i 5 -f 0 -tu ns"`
- Pipeline: `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=org.openjdk.jmh.Main" "-Dexec.args=PipelineBenchmark.iocCrossingOrder -p enableMd=false -wi 3 -i 5 -f 0 -tu ns"`

## CI automation
- Build/test/coverage: `.github/workflows/ci.yml`
- Nightly performance regression checks: `.github/workflows/nightly-performance.yml`
- C++ native build + Google Benchmark run: `.github/workflows/ci.yml` (`cpp-benchmark` job)

## Governance
- Changelog: `CHANGELOG.md`
- Contributing guide: `CONTRIBUTING.md`
- Security policy: `SECURITY.md`
- License: `LICENSE`

## Not yet HFT-final
- UDP/multicast transport profiles are not added yet (Aeron IPC is implemented)
- persistence/replay has file-journal + CRC/recovery + full snapshot/restore, but still lacks journal+snapshot coordinated incremental catch-up tooling
- still not fully wait-free/garbage-free in all paths (data structures and selected transport paths still use spin/heap fallback)
- JNI prototype still uses `std::map`/`std::list`; not yet cache-optimal contiguous book layout
