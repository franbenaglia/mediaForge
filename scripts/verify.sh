#!/usr/bin/env bash
# Verificación completa sin dispositivo: compilación, pruebas unitarias, lint y estilo.
# Es la puerta de calidad obligatoria antes de cerrar cualquier tarea (ver CLAUDE.md).
#
# Corre en dos pasos y con prioridad baja para no colgar la máquina (15 GB de RAM).
# Al terminar detiene los daemons de Gradle y Kotlin; KEEP_DAEMON=1 los deja vivos.
set -euo pipefail
cd "$(dirname "$0")/.."

cleanup() {
    if [[ "${KEEP_DAEMON:-0}" != "1" ]]; then
        ./gradlew --stop -q >/dev/null 2>&1 || true
        pkill -f KotlinCompileDaemon >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

run() { nice -n 10 timeout 1500 ./gradlew "$@"; }

run assembleDebug :core:domain:test testDebugUnitTest ktlintCheck "$@"
run lintDebug "$@"

echo "verify: OK"
