# Architecture Overview

## Stages
1. Gateway ingress (`Aeron IPC/UDP` or JNI direct)
2. Risk/validation stage
3. Matching core (`JAVA` or `NATIVE` backend)
4. Market data dissemination (SBE / Aeron)
5. Persistence (`journal + snapshot + coordinated catch-up`)

## Native Core
- `NativeOrderBook` JNI entrypoint
- fixed-capacity ladder for bid/ask
- intrusive queue + preallocated node freelist
- iceberg refresh + partial fills in C++ path

## Latency Budget (Target)
- End-to-end p99: `< 1 us`
- End-to-end p99.99: `< 10 us`
- Stretch target for micro-bench path: `< 100 ns` per core match operation

## Extension Guide
### New order type
1. Add codec fields (Aeron/SBE)
2. Add Java validation mapping
3. Add C++ matching handling in `native_order_book.hpp`
4. Add parity fuzz scenario and perf regression check

### New transport
1. Add channel/profile in `AeronChannels`
2. Add gateway/ingress adapter
3. Add smoke demo + CI gate

### Recovery flow
1. capture snapshot + checkpoint
2. replay journal from checkpoint record
3. verify parity on restart test
