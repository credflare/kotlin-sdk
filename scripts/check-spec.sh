#!/bin/sh
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
spec=${SDK_SPEC_DIR:-"$root/../sdk-spec"}
node "$spec/scripts/generate.mjs" --target kotlin --package net.credflare.sdk --out "$root/sdk/src/main/kotlin/net/credflare/sdk/Spec.generated.kt" --check
for fixture in "$spec"/fixtures/*.json; do
  cmp -s "$fixture" "$root/example/src/androidTest/assets/$(basename "$fixture")" || { echo "Fixture out of date: $(basename "$fixture")"; exit 1; }
done
for rejection in "$spec"/fixtures/rejections/*.json; do
  cmp -s "$rejection" "$root/example/src/androidTest/assets/rejections/$(basename "$rejection")" || { echo "Rejection fixture out of date: $(basename "$rejection")"; exit 1; }
done
[ "$(ls "$spec"/fixtures/rejections/*.json | wc -l)" -eq "$(ls "$root"/example/src/androidTest/assets/rejections/*.json | wc -l)" ] || { echo "Rejection fixture set differs from sdk-spec"; exit 1; }
for trace in "$spec"/fixtures/liveness-traces/*.json; do
  cmp -s "$trace" "$root/sdk/src/test/resources/liveness-traces/$(basename "$trace")" || { echo "Liveness trace out of date: $(basename "$trace")"; exit 1; }
done
[ "$(ls "$spec"/fixtures/liveness-traces/*.json | wc -l)" -eq "$(ls "$root"/sdk/src/test/resources/liveness-traces/*.json | wc -l)" ] || { echo "Liveness trace set differs from sdk-spec"; exit 1; }
echo "Spec constants, fixtures, rejection fixtures and liveness traces match sdk-spec"
