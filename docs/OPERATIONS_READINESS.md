# Operations Readiness

This document defines operational readiness for PulseEngine.

## Health Signals

Primary metrics:

- `pulseengine_parity_drift_total`
- `pulseengine_backpressure_rejects_total`
- `pulseengine_replay_lag_records`
- `pulseengine_soak_throughput_ops`
- `pulseengine_best_bid`
- `pulseengine_best_ask`

Alert rules are provided in `ops/prometheus/alerts.yml`.

## Recovery CLI Scenarios

1. Capture state:
- `CoordinatedRecoveryTool capture <journal> <snapshot> <checkpoint>`

2. Restart service process.

3. Restore state and catch up:
- `CoordinatedRecoveryTool restore <journal> <snapshot> <checkpoint>`

4. Validate recovered state:
- compare best bid/ask outputs
- verify replay delta count
- verify parity gates still pass

## Staging Canary Profile

Workflow: `.github/workflows/staging-canary.yml`

Canary checks:

- soak parity (`soak_ok`, `parity_drift_total`)
- minimum throughput floor
- UDP transport sanity (`aeron_orders_processed`, `aeron_md_fragments`)

Artifact:

- `staging-canary-report`

Promotion rule:

- Canary must be `PASS` before production release publication.
