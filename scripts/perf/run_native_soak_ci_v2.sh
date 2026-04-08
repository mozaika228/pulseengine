#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "usage: run_native_soak_ci_v2.sh <duration_sec> <seed> <metrics_port> <out_file> [max_levels] [max_orders] [limit_percent]" >&2
  exit 2
fi

DURATION_SEC="$1"
SEED="$2"
METRICS_PORT="$3"
OUT_FILE="$4"
MAX_LEVELS="${5:-4096}"
MAX_ORDERS="${6:-1000000}"
LIMIT_PERCENT="${7:-50}"

mkdir -p "$(dirname "$OUT_FILE")"

if [[ ! -f "cpp/build/libpulseengine_native.so" ]]; then
  echo "native library missing: cpp/build/libpulseengine_native.so" | tee "$OUT_FILE"
  exit 1
fi

echo "native_library=cpp/build/libpulseengine_native.so" | tee "$OUT_FILE"
echo "native_max_levels=${MAX_LEVELS}" | tee -a "$OUT_FILE"
echo "native_max_orders=${MAX_ORDERS}" | tee -a "$OUT_FILE"
echo "limit_percent=${LIMIT_PERCENT}" | tee -a "$OUT_FILE"

echo "running_native_soak_v2=true" | tee -a "$OUT_FILE"
mvn -B org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.classpathScope=compile \
  -Dexec.mainClass=io.pulseengine.app.NativeParitySoakToolV2 \
  "-Dexec.args=${DURATION_SEC} ${SEED} ${METRICS_PORT} ${MAX_LEVELS} ${MAX_ORDERS} ${LIMIT_PERCENT}" \
  2>&1 | tee -a "$OUT_FILE"
