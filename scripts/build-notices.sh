#!/usr/bin/env bash
# Regenera app/src/main/assets/third_party_notices.txt desde las fuentes originales (T12.4).
# Hay que correrlo si cambia youtubedl-android o una dependencia. Baja ~400 MB la primera vez
# (quedan en build/notices/cache).
set -euo pipefail
cd "$(dirname "$0")/.."
python3 -I scripts/build_notices.py
