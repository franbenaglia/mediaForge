#!/usr/bin/env bash
# Verificación completa sin dispositivo: compilación, pruebas unitarias, lint y estilo.
# Es la puerta de calidad obligatoria antes de cerrar cualquier tarea (ver CLAUDE.md).
set -euo pipefail
cd "$(dirname "$0")/.."

timeout 1500 ./gradlew \
    assembleDebug \
    :core:domain:test \
    testDebugUnitTest \
    lintDebug \
    ktlintCheck \
    "$@"

echo "verify: OK"
