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
- top-of-book view (`TopOfBookView`) updated by market-data stage
- binary market-data publisher (SBE schema-driven):
  - snapshot L2 message
  - incremental L2 delta message (only on change)
  - snapshot L2 depth-N message (periodic + initial)
  - incremental L3 message (`add/modify/cancel/trade`)
- latency harness (`LatencyHarness`)
- throughput demo (`PipelineDemo`)
- binary feed demo with decoder (`BinaryFeedDemo`)

## Run
- `mvn -q -DskipTests=false verify`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.LatencyHarness"`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.PipelineDemo"`
- PowerShell (SBE/Agrona runtime): `$env:MAVEN_OPTS='--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED'; mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.BinaryFeedDemo"`

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

## Governance
- Changelog: `CHANGELOG.md`
- Contributing guide: `CONTRIBUTING.md`
- Security policy: `SECURITY.md`
- License: `LICENSE`

## Not yet HFT-final
- no Aeron/UDP transport
- no persistence/replay
- still not fully wait-free/garbage-free in all paths
