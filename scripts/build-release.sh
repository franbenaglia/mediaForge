#!/usr/bin/env bash
# APKs de release firmados (T12.2). Requiere keystore.properties (no versionado): ver docs/RELEASE.md.
# Deja en app/build/outputs/apk/release/ un APK por arquitectura y SHA256SUMS.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ ! -f keystore.properties ]]; then
    echo "Falta keystore.properties (ver docs/RELEASE.md). Sin él los APKs saldrían sin firmar." >&2
    exit 1
fi

./scripts/verify.sh
cleanup() { ./gradlew --stop -q >/dev/null 2>&1 || true; }
trap cleanup EXIT
nice -n 10 timeout 1500 ./gradlew assembleRelease "$@"

out=app/build/outputs/apk/release
apksigner=$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/*/apksigner | sort -V | tail -1)
for apk in "$out"/*-release.apk; do
    "$apksigner" verify --print-certs "$apk" | head -3
done
(cd "$out" && sha256sum ./*-release.apk | sed 's| \./| |' > SHA256SUMS && cat SHA256SUMS)
echo "build-release: OK"
