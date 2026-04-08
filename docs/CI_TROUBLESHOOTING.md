# CI Troubleshooting

## Native soak step fails with exit code 1 and no stack trace

Symptom:

- workflow log shows only `Picked up JAVA_TOOL_OPTIONS ...`
- step exits with code `1`

Typical root cause:

- command output was redirected to a file with `>` and the diagnostic details were not visible in logs.

Use the robust workflow and script:

- `.github/workflows/finalization-smoke-v2.yml`
- `scripts/perf/run_native_soak_ci.sh`

They ensure:

- native library presence check before run
- stdout/stderr captured via `tee`
- soak output artifact persisted for debugging
