# Licencias de terceros

Auditoría de la Etapa 11 (T11.3): qué viene dentro del APK, con qué licencia, y qué obligaciones trae. MediaForge es GPL-3.0 (ADR-003).

Última revisión: 2026-11-08. Para el APK de `arm64-v8a`; el de `x86_64` trae los mismos componentes.

## Resumen

- **Todo es compatible con GPL-3.0.**
  - El FFmpeg incluido está compilado con `--enable-gpl --enable-version3`, así que se distribuye como GPL-3.0-or-later.
  - Lo Apache-2.0 y LGPL-3.0 solo es compatible con GPL**v3**, y esa es la licencia de la app. Por eso se usa `--enable-version3`.
  - No hay componentes "nonfree": no está `libfdk-aac`.
- **Los binarios nativos vienen de youtubedl-android 0.18.1,** que a su vez los toma de paquetes de Termux. El APK no trae los textos de licencia de esas bibliotecas.
- **Pendiente antes del primer release público (Etapa 12):** ver "Obligaciones", más abajo.

## Código de la app y bibliotecas Java/Kotlin

| Componente | Versión | Licencia |
|---|---|---|
| MediaForge | 0.1.0 | GPL-3.0-only |
| youtubedl-android (`library`, `ffmpeg`, `common`) | 0.18.1 | GPL-3.0 |
| Kotlin stdlib, kotlinx.coroutines, kotlinx.serialization | 2.4.21 / 1.11.0 / 1.11.0 | Apache-2.0 |
| AndroidX: Core, Activity, Lifecycle, Compose (BOM 2026.09.00), Material 3, Navigation, Room, DataStore, AppCompat | ver `gradle/libs.versions.toml` | Apache-2.0 |
| Dagger / Hilt, AndroidX Hilt | 2.60.1 / 1.4.0 | Apache-2.0 |
| Coil 3 (con OkHttp y Okio) | 3.6.3 | Apache-2.0 |
| Jackson (`databind`, `annotations`), dependencia de youtubedl-android | 2.11.1 | Apache-2.0 |
| Apache Commons IO y Commons Compress, dependencias de youtubedl-android | 2.5 / 1.12 | Apache-2.0 |

## Motores (`jniLibs` y recursos de youtubedl-android)

| Componente | Versión | Licencia | Notas |
|---|---|---|---|
| yt-dlp (`res/raw/ytdlp`, zipapp) | 2025.11.12 | Unlicense | Se puede actualizar desde la app (ADR-012); sigue siendo Unlicense. |
| CPython (`libpython.zip.so`) | 3.12.11 | PSF-2.0 | |
| QuickJS (`libqjs.so`) | 2025-04-26 | MIT | |
| FFmpeg y FFprobe (`libffmpeg.zip.so`): libavcodec, libavformat, libavfilter, libavdevice, libavutil, libswscale, libswresample, libpostproc | 7.1.1 | GPL-3.0-or-later (por `--enable-gpl --enable-version3`) | |

### Bibliotecas que trae FFmpeg

| Licencia | Bibliotecas |
|---|---|
| GPL-2.0-or-later | x264, x265, Xvid (`libxvidcore`), vid.stab, Rubber Band, FFTW 3 |
| LGPL (2.1 o 3.0, -or-later) | LAME (`libmp3lame`), mpg123 (`libmpg123`, `libout123`, `libsyn123`), Game Music Emu (`libgme`), SoX Resampler (`libsoxr`), FriBidi, GLib/GIO/GObject, GnuTLS, Nettle (`libnettle`, `libhogweed`), GMP, libidn2, libunistring, libiconv/libcharset, libbluray, libudfread, libssh, v4l-utils (`libv4l*`) |
| Apache-2.0 | OpenCORE AMR (`libopencore-amrnb`, `libopencore-amrwb`), VisualOn AMR-WB (`libvo-amrwbenc`), cargador ICD de OpenCL (`libOpenCL`) |
| MPL-2.0 | SRT (`libsrt`), ZeroMQ (`libzmq`) |
| BSD (2 o 3 cláusulas) | libaom, dav1d, rav1e, SVT-AV1 (BSD-3-Clause-Clear), libvpx, libwebp/sharpyuv, Opus, Ogg, Vorbis, Theora, libopenmpt, libsamplerate, PCRE2, VMAF (BSD-2-Clause-Patent) |
| MIT / ISC / similares | HarfBuzz, Fontconfig, libxml2, Little CMS (`liblcms2`), libffi, libass (ISC), libsodium (ISC), Brotli |
| Otras permisivas | FreeType (FTL, o GPL-2.0), Graphite2 (LGPL-2.1 o MPL-2.0), libpng (libpng-2.0), zlib (Zlib), XZ (`liblzma`, 0BSD), bzip2 (bzip2-1.0.6), zimg (WTFPL) |
| Compatibilidad de Termux | `libandroid-glob`, `libandroid-support`: piezas de libc para Android que agrega Termux |

### Bibliotecas que trae Python

| Licencia | Bibliotecas |
|---|---|
| Apache-2.0 | OpenSSL 3 (`libssl`, `libcrypto`) |
| Apache-2.0 WITH LLVM-exception | `libc++_shared` |
| GPL-3.0-or-later | GNU Readline (`libreadline`, `libhistory`), GDBM (`libgdbm`, `libgdbm_compat`) |
| MIT / X11 / similares | ncurses (`libncurses*`, `libform*`, `libmenu*`, `libpanel*`, `libtinfo`, `libtic`), Expat, libffi |
| BSD-2-Clause | `libcrypt` (implementación propia de Termux, no libxcrypt) |
| Dominio público | SQLite |
| Otras permisivas | zlib, XZ (`liblzma`), bzip2 |
| Compatibilidad de Termux | `libandroid-support`, `libandroid-posix-semaphore` |

Las bibliotecas del sistema (`libandroid`, `libmediandk`, `libOpenSLES`) son de Android y no se distribuyen con el APK.

## Obligaciones

1. **Código fuente correspondiente (GPL-3.0 §6, LGPL, MPL-2.0 §3.2).** Resuelto (ADR-023):
   - `scripts/package-gpl-sources.sh` arma `mediaforge-<versión>-gpl-sources.tar.gz`, que se publica en cada GitHub Release.
   - Contiene las fuentes originales de cada componente GPL, LGPL y MPL, verificadas por SHA-256 (`distribution/gpl-sources.tsv`).
   - También van las recetas y el sistema de construcción de Termux en la misma instantánea, y youtubedl-android 0.18.1.
2. **Avisos de copyright y textos de licencia** (Apache-2.0, BSD, MIT, ISC, MPL, FTL). Hay que incluir el texto de cada licencia y los avisos de copyright de cada biblioteca. **Pendiente (Etapa 12):** juntarlos de los paquetes de origen en `THIRD_PARTY_NOTICES.md` y mostrarlos en la app. Hoy la pantalla "Licencias" lista los componentes y su licencia, y enlaza a este documento.
3. **Avisos de modificación (Apache-2.0 §4).** No se modificó ningún componente; si alguna vez se parchea, hay que avisarlo.
4. **Sin restricciones adicionales.** La app no agrega términos que contradigan la GPL (sin DRM, sin cuentas, sin condiciones de uso extra).

## Cómo se armó este inventario

- **Dependencias Java/Kotlin:** `gradle/libs.versions.toml` y los POM de youtubedl-android.
- **Bibliotecas nativas:** se listó el contenido de `libffmpeg.zip.so` y `libpython.zip.so` de los AAR de youtubedl-android 0.18.1 (caché de Gradle).
- **Licencias:** son las que declara cada proyecto. Hay que confirmarlas contra los avisos de copyright cuando se junten (obligación 2).
