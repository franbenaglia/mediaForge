#!/usr/bin/env bash
# APK de release. Requiere keystore.properties (no versionado). Ver docs/RELEASE.md cuando exista.
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/verify.sh
timeout 1500 ./gradlew assembleRelease "$@"
