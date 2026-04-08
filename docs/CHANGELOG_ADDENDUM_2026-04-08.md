# Changelog Addendum (2026-04-08)

This addendum captures closeout changes introduced after the latest root changelog update.

## Added

- `.github/SECURITY.md` with private advisory-based security reporting (no email channel).
- `release-gated-publish` workflow for automatic gated release publication after qualification pass.
- `staging-canary` workflow with blocking canary checks and artifact evidence.
- Prometheus alert rules in `ops/prometheus/alerts.yml`.
- `finalization-smoke` workflow producing `finalization-proof-pack` on push.
- operations and status docs:
  - `docs/OPERATIONS_READINESS.md`
  - `docs/HFT_FINAL_STATUS.md`
  - `docs/RELEASE_PUBLISHING.md`
  - `docs/FINAL_CLOSEOUT.md`
