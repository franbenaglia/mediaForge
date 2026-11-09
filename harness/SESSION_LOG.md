# Registro de sesiones

Una entrada por sesión, la más reciente arriba. Solo lo necesario para continuar.

---

## 2026-11-08 · Etapa 10

**Objetivo:** funciones avanzadas (main.md §5.2, §2.3).

**Hecho:** conversor con recorte rápido o exacto, fotograma JPG/PNG, varios archivos y compresión (ADR-020); subtítulos incrustados y listas de reproducción públicas (ADR-021). Todo validado en el teléfono (T10.8).

**Problemas:** el teclado numérico no tenía ":" (ahora teclado completo); el teléfono rechazó el servicio en primer plano con "type none" y la app se cerraba: `JobService` ahora reintenta con `dataSync` y no se cae (causa sin confirmar, en Problemas conocidos).

**Verificación:** `./scripts/verify.sh` OK (426 pruebas).

---

## 2026-11-08 · Etapa 9

**Hecho:** T9.1–T9.10 (ADR-019). Historial en Room (v2, migración automática), ajustes con DataStore, pantallas Historial y Ajustes, Inicio con los predeterminados, límite de descargas en vivo, solo Wi‑Fi, carpeta de destino (SAF) con vuelta a la galería, limpieza de temporales.

**Teléfono:** el usuario validó todo en 24115RA8EG. El ajuste de idioma por app del sistema no guardaba la elección: se quitó y el idioma sigue al del teléfono.

**Verificación:** `./scripts/verify.sh` OK (372 pruebas).

---

## 2026-11-08 · Etapa 8

**Hecho:** T8.1–T8.3 (ADR-018). `SharedText.findUrl`, filtro `ACTION_SEND` con `singleTask`, `ShareIntents`, `ShareInbox`; Inicio carga y analiza el enlace compartido.

**Teléfono:** el usuario validó Chrome, la app de YouTube, otra app, app cerrada, abierta en otra pestaña y con una descarga en curso, texto sin enlace y giro de pantalla.

**Verificación:** `./scripts/verify.sh` OK (315 pruebas).

---

## 2026-11-08 · Etapa 7

**Hecho:** T7.1–T7.7 (ADR-017). `Job`/`JobStore`, `JobScheduler` con cola, límites y reintentos, Room en `:core:data`, `JobService` en primer plano con notificaciones, Inicio y Conversor encolan, pantalla Descargas.

**Teléfono:** `RoomJobStoreDeviceTest` OK. El usuario validó todos los pasos de T7.7.

**Problemas:** en la prueba no se podía encolar una segunda descarga (el campo quedaba bloqueado); se quitó el bloqueo en Inicio y Conversor.

**Verificación:** `./scripts/verify.sh` OK.

---

## 2026-11-08 · Etapa 6

**Hecho:** T6.1–T6.6. `LocalMedia` y `ConversionPlanner` (ADR-016), `ffprobe` y comando de FFmpeg, `FfmpegConverter`, `ExtractorInspector`, pantalla Conversor.

**Teléfono:** `ConverterDeviceTest` OK con todos los casos de §5.1 y cancelación. El usuario validó la pantalla: MP4 → MP3, remux, recodificar y cancelar, audio a WAV/FLAC.

**Problemas:** FFmpeg no arrancaba (`libc++_shared.so` está en la carpeta de Python); se agregaron las tres carpetas de librerías a `LD_LIBRARY_PATH`. Las extensiones de Java de VS Code levantaban su propio Gradle y colgaban la máquina; el usuario las desactivó para este proyecto.

**Verificación:** `./scripts/verify.sh` OK.

---

## 2026-11-08 · Etapa 5

**Hecho:** T5.1–T5.5. `AudioOptions` (ADR-015), comando de audio con portada y metadatos, pasos de audio en el parser, descargador con audio, modo "Solo audio" en Inicio.

**Teléfono:** `MediaLibraryDeviceTest` guardó los seis formatos de audio. El usuario validó MP3 320 kbps (aviso, portada, título), M4A sin recodificar y Opus.

**Problemas:** una instalación se canceló porque nadie tocó "Instalar" a tiempo; desde entonces se avisa antes y se espera la confirmación.

**Verificación:** `./scripts/verify.sh` OK.

---

## 2026-11-07 · Etapa 4

**Hecho:** T4.1–T4.8. Miniatura (Coil), `VideoOptions`, comando y parser de progreso, `YtDlpDownloader`, `MediaStoreLibrary`, panel de descarga en Inicio y "Detalles técnicos" en los errores. 189 pruebas en la JVM.

**Teléfono:** `MediaLibraryDeviceTest` OK. El usuario descargó YouTube y Dailymotion, los reprodujo y canceló sin parciales.

**Problemas:** el clasificador de permisos bloqueó una instalación hasta que el usuario la autorizó. Reinstalar el APK de prueba borra el yt-dlp actualizado. `run-as` no tiene red. El teclado se abría solo al terminar una descarga (arreglado, falta confirmar).

**Verificación:** `./scripts/verify.sh` OK.

---

## 2026-11-07 · Etapa 3

**Hecho:** T3.2–T3.9. `:core:media` con youtubedl-android 0.18.1, parser de `--dump-json`, clasificador de errores, `ProcessRunner` con cancelación del árbol de procesos, `YtDlpExtractor`, actualizador de yt-dlp con firma OpenPGP verificada (ADR-014) y botón "Actualizar motor" en Inicio. 146 pruebas en la JVM + 5 instrumentadas.

**Teléfono (24115RA8EG, Android 15):** `EngineDeviceTest` 5/5; el usuario validó T3.8 y T3.9 a mano. Fixtures reales de YouTube y Dailymotion.

**Problemas:** faltaba el permiso `INTERNET` (DNS fallaba en el teléfono). El yt-dlp del APK da 403 en Dailymotion hasta actualizar. La tarjeta no muestra miniatura (T4.1).

**Verificación:** `./scripts/verify.sh` OK.

---

## 2026-11-07 · Etapa 2

**Hecho:** T2.1 (navegación tipada con barra inferior), T2.3 (componentes y formateadores), T3.1 (`UrlValidator`, adelantada porque la necesita T2.2) y T2.2 (pantalla Inicio). 61 pruebas.

**Problemas:** la máquina se colgó durante `verify.sh`. Se limitó la memoria de Gradle, `verify.sh` corre en dos pasos con `nice` y se desactivó la importación de Gradle de la extensión de Java de VS Code (generaba `bin/` y otro build en paralelo).

**Verificación:** `./scripts/verify.sh` OK (pico de 8,9 GB usados en total).

---

## 2026-11-06 · Etapas 0 y 1

**Objetivo:** investigación técnica y estructura del proyecto.

**Hecho:**
- Investigación de yt-dlp, FFmpeg, Chaquopy, YTDLnis/Seal, límites de foreground services y verificación de desarrolladores → `docs/FEASIBILITY.md`.
- Prueba técnica en `spikes/engine-spike/` (emulador API 36): YouTube info OK, descarga 403 con yt-dlp viejo, actualización OK, FFmpeg con todos los codificadores, conversiones offline OK, Vimeo exige login.
- Etapa 1: repo, Gradle, `:app` + `:core:domain`, contratos, `FileNames` (14 pruebas), scripts, harness y ADR-001 a ADR-013.

**Decisiones del usuario:** GPL-3.0 con repo público en GitHub (franbenaglia/mediaForge); Dailymotion reemplaza a Vimeo; no usar el emulador; pedir permiso antes de instalar en el teléfono.

**Problemas:** el emulador colgó la máquina y se eliminó. El disco estaba al 98%; se liberaron ~16 GB (cachés de Gradle y de Android Studio, emulador).

**Verificación:** `./scripts/verify.sh` OK.
