#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
port="${CONTRACT_PORT:-3118}"
PORT="$port" node scripts/contract-server.mjs example/src/androidTest/assets > /dev/null &
server=$!
trap 'kill "$server" 2>/dev/null || true; adb reverse --remove "tcp:$port" > /dev/null 2>&1 || true' EXIT
for _ in $(seq 1 50); do
  curl -sf "http://127.0.0.1:$port/stats" > /dev/null && break
  sleep 0.2
done
adb reverse "tcp:$port" "tcp:$port"
adb install -r -t example/build/outputs/apk/debug/example-debug.apk
adb install -r -t example/build/outputs/apk/androidTest/debug/example-debug-androidTest.apk
output="$(adb shell am instrument -w -e contract_base "http://127.0.0.1:$port/api/v1" net.credflare.kotlindemo.test/net.credflare.kotlindemo.ContractInstrumentation)"
echo "$output"
grep -q '^PASS:' <<< "$output"
