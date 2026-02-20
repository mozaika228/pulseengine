# PulseEngine (baseline)

In-process single-symbol matching core for Java 21 with a staged Disruptor pipeline.

Implemented now:
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
- binary market-data publisher (zero-copy style flyweight buffer):
  - snapshot L2 message
  - incremental L2 delta message (only on change)
- snapshot L2 depth-N message (periodic + initial)
- incremental L3 message (`add/modify/cancel/trade`)
  - SBE schema-driven encoding/decoding (generated codecs)
- simple latency harness with HdrHistogram (`LatencyHarness`)
- simple throughput demo (`PipelineDemo`)
- binary feed demo with decoder (`BinaryFeedDemo`)

Run:
- `mvn -q -DskipTests compile`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.LatencyHarness"`
- `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.PipelineDemo"`
- PowerShell (SBE/Agrona runtime): `$env:MAVEN_OPTS='--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED'; mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=compile" "-Dexec.mainClass=io.pulseengine.app.BinaryFeedDemo"`

JMH benchmarks:
- `mvn -q -DskipTests test-compile`
- Core-only (`OrderBook` direct): `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=org.openjdk.jmh.Main" "-Dexec.args=CoreOrderBookBenchmark.iocCrossingOrder -wi 3 -i 5 -f 0 -tu ns"`
- Pipeline (`enableMd=false|true`): `mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=org.openjdk.jmh.Main" "-Dexec.args=PipelineBenchmark.iocCrossingOrder -p enableMd=false -wi 3 -i 5 -f 0 -tu ns"`
- For `enableMd=true` add PowerShell runtime flags: `$env:MAVEN_OPTS='--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED -Djmh.ignoreLock=true'`

SBE schema:
- `src/main/resources/sbe/market-data-schema.xml`

Not yet HFT-final:
- no Aeron/UDP transport
- no persistence/replay
- still not fully wait-free/garbage-free in all paths
