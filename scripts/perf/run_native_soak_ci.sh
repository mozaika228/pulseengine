#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "usage: run_native_soak_ci.sh <duration_sec> <seed> <metrics_port> <out_file>" >&2
  exit 2
fi

DURATION_SEC="$1"
SEED="$2"
METRICS_PORT="$3"
OUT_FILE="$4"

mkdir -p "$(dirname "$OUT_FILE")"

if [[ ! -f "cpp/build/libpulseengine_native.so" ]]; then
  echo "native library missing: cpp/build/libpulseengine_native.so" | tee "$OUT_FILE"
  exit 1
fi

echo "native_library=cpp/build/libpulseengine_native.so" | tee "$OUT_FILE"

echo "running_native_soak=true" | tee -a "$OUT_FILE"
mvn -B org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.classpathScope=compile \
  -Dexec.mainClass=io.pulseengine.app.NativeParitySoakTool \
  "-Dexec.args=${DURATION_SEC} ${SEED} ${METRICS_PORT}" \
  2>&1 | tee -a "$OUT_FILE"
