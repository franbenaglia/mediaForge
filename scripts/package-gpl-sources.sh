#!/usr/bin/env bash
# Arma el archivo de fuentes correspondientes de los binarios GPL, LGPL y MPL del APK (T12.4,
# ADR-023), para publicarlo junto a cada GitHub Release. Uso: ./scripts/package-gpl-sources.sh 1.0.0
#
# Incluye:
#   upstream/   las fuentes originales de cada biblioteca, verificadas por SHA-256
#               (distribution/gpl-sources.tsv);
#   termux/     las recetas y scripts de construcción de termux-packages en la misma instantánea;
#   youtubedl-android/   el código del paquete que reúne esos binarios (0.18.1).
# Ningún archivo bajado se ejecuta: solo se descarga, se verifica y se empaqueta.
set -euo pipefail
cd "$(dirname "$0")/.."

version=${1:?Uso: $0 <versión de MediaForge>}
manifest=distribution/gpl-sources.tsv
termux_commit=70d29feb942cc00333d29204a76244e7fea0d6db
ytdl_tag=0.18.1
name="mediaforge-${version}-gpl-sources"
work="build/gpl-sources"
dir="$work/$name"

rm -rf "$dir"
mkdir -p "$dir/upstream"

fetch() { curl -fL --retry 3 --retry-delay 5 --connect-timeout 30 -o "$2" "$1"; }

# 1. Fuentes originales, cada una contra su SHA-256 del manifiesto.
packages=()
while IFS=$'\t' read -r pkg ver license url sha; do
    [[ -z "$pkg" || "$pkg" == \#* ]] && continue
    file="$dir/upstream/${url##*/}"
    echo "== $pkg $ver ($license)"
    fetch "$url" "$file"
    actual=$(sha256sum "$file" | cut -d' ' -f1)
    if [[ "$actual" != "$sha" ]]; then
        echo "SHA-256 distinto para $pkg: esperado $sha, bajado $actual" >&2
        exit 1
    fi
    [[ "$pkg" == *-patch ]] || packages+=("$pkg")
done < "$manifest"

# 2. Recetas de Termux (solo esos paquetes, más el sistema de construcción).
git clone -q --filter=blob:none --no-checkout https://github.com/termux/termux-packages.git "$work/termux-src"
git -C "$work/termux-src" sparse-checkout set --no-cone /build-package.sh /scripts/ \
    "${packages[@]/#//packages/}"
git -C "$work/termux-src" checkout -q "$termux_commit"
mkdir -p "$dir/termux"
git -C "$work/termux-src" archive "$termux_commit" build-package.sh scripts \
    "${packages[@]/#/packages/}" | tar -x -C "$dir/termux"
rm -rf "$work/termux-src"

# 3. youtubedl-android en el tag publicado.
fetch "https://github.com/yausername/youtubedl-android/archive/refs/tags/${ytdl_tag}.tar.gz" \
    "$dir/youtubedl-android-${ytdl_tag}.tar.gz"

cp "$manifest" "$dir/"
cat > "$dir/README.txt" <<EOF
MediaForge ${version}: corresponding source for the GPL, LGPL and MPL components of the APK.

The native binaries inside the APK (FFmpeg, its libraries, and Python's GPL libraries) come from
youtubedl-android ${ytdl_tag}, which packages Termux builds. This archive contains:

- upstream/: the original source of each component, as listed in gpl-sources.tsv
  (package, version, license, URL, SHA-256). readline83-001 is the official patch Termux applies.
- termux/: termux-packages at commit ${termux_commit} (2025-10-17): the build system
  (build-package.sh, scripts/) and the recipe of each package (packages/<name>/, with its patches).
  Build with: ./scripts/run-docker.sh ./build-package.sh <package>
- youtubedl-android-${ytdl_tag}.tar.gz: the library that bundles those binaries (BUILD_FFMPEG.md and
  BUILD_PYTHON.md explain how the zips are made).

MediaForge's own source code: https://github.com/franbenaglia/mediaForge (tag v${version}).
License inventory and obligations: docs/LICENSES.md in that repository.
EOF

tar -C "$work" -czf "$work/$name.tar.gz" "$name"
(cd "$work" && sha256sum "$name.tar.gz" > "$name.tar.gz.sha256")
du -h "$work/$name.tar.gz"
echo "package-gpl-sources: OK -> $work/$name.tar.gz"
