# Runbook

## Start observability stack
1. Build Java classes and native library.
2. Run: `docker compose up --build`.
3. Prometheus: `http://localhost:9090`
4. Grafana: `http://localhost:3000` (`admin/admin`)

## Native soak run
- `NativeParitySoakTool <seconds> <seed> <metricsPort>`
- Metrics endpoint: `/metrics`

## Recovery procedure
1. `CoordinatedRecoveryTool capture <journal> <snapshot> <checkpoint>`
2. restart process
3. `CoordinatedRecoveryTool restore <journal> <snapshot> <checkpoint>`
4. confirm best bid/ask and replayed delta

## Incident checks
- `pulseengine_parity_drift_total > 0` means native/java divergence detected
- rising backpressure rejects means transport saturation
- replay lag growth indicates recovery bottleneck
