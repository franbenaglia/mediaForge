#!/usr/bin/env bash
# Pruebas instrumentadas en un TELÉFONO FÍSICO. Nunca en el emulador: cuelga la máquina.
# Antes de correrlo, avisar al usuario: el teléfono pide confirmar la instalación.
set -euo pipefail
cd "$(dirname "$0")/.."

devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ -z "$devices" ]]; then
    echo "No hay un teléfono conectado y autorizado (adb devices)." >&2
    exit 1
fi
if grep -q '^emulator-' <<<"$devices"; then
    echo "Hay un emulador conectado. Este proyecto no usa el emulador." >&2
    exit 1
fi

timeout 1800 ./gradlew connectedDebugAndroidTest "$@"
