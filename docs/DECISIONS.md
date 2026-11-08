# Decisiones de arquitectura (ADR)

Formato: contexto → decisión → alternativas descartadas. Un ADR no se revierte sin consultar al usuario; si cambia, se agrega uno nuevo que lo reemplaza.

---

## ADR-001 · Motor de extracción: youtubedl-android 0.18.1 detrás de un adaptador

**Fecha:** 2026-11-06 · **Estado:** aceptada

**Contexto:** yt-dlp necesita Python, un runtime de JS (YouTube) y FFmpeg en Android. Detalle en [FEASIBILITY.md](FEASIBILITY.md) §3.

**Decisión:** usar los binarios de `io.github.junkfood02.youtubedl-android:{library,ffmpeg}:0.18.1` (Python 3.12.11, QuickJS 2025-04-26, FFmpeg 7.1.1). Toda la interacción con la librería queda en un único módulo adaptador (`:core:media`). El dominio solo ve `MediaExtractor`, `MediaDownloader` y `ExtractorEngine`. La salida de `--dump-json` se parsea con un parser propio, y la cancelación, el árbol de procesos y la actualización de yt-dlp son propios (FEASIBILITY §9).

**Descartadas:**
- Chaquopy + pip: queda como plan B. Python moderno, pero hay que integrar FFmpeg y QuickJS a mano y cancelar código Python en proceso es difícil.
- Paquetes de YTDLnis: acoplarían el proyecto al formato de otra app.

---

## ADR-002 · FFmpeg: el binario de youtubedl-android, también para el conversor

**Fecha:** 2026-11-06 · **Estado:** aceptada

**Decisión:** el conversor offline ejecuta `libffmpeg.so` (del APK) con `ProcessBuilder`, argumentos como lista y progreso por `-progress pipe:1`. No depende del código de yt-dlp, solo comparte el binario.

**Descartadas:** FFmpegKit (retirado), `ffmpegkit-maintained` (duplica ~35 MB y su mantenimiento no se puede verificar), FFmpegKitNext o compilación propia (costo alto; se reservan para cuando haga falta).

---

## ADR-003 · Licencia GPL-3.0 y repositorio público

**Fecha:** 2026-11-06 · **Estado:** aceptada (confirmada por el usuario)

**Contexto:** youtubedl-android es GPL-3.0 y el FFmpeg incluido está compilado con `--enable-gpl --enable-version3`.

**Decisión:** MediaForge es GPL-3.0. El código está en https://github.com/franbenaglia/mediaForge. Antes del primer release hay que agregar `THIRD_PARTY_NOTICES.md` y una pantalla de licencias.

---

## ADR-004 · SDK y arquitecturas

**Fecha:** 2026-11-06 · **Estado:** aceptada

**Decisión:** `minSdk 26`, `targetSdk 36` y `compileSdk 37`. AndroidX (core 1.19, Compose 1.12) exige compilar contra 37. El target queda en 36 porque es lo validado en la prueba técnica, y subirlo a 37 requiere revisar los cambios de comportamiento. ABIs: arm64-v8a y x86_64, con un APK por ABI (splits) desde que entren los binarios nativos; armeabi-v7a se evalúa más adelante.

---

## ADR-005 · Contratos de motores con `Flow` y cancelación por corrutina

**Fecha:** 2026-11-06 · **Estado:** aceptada

**Decisión:** `download()` y `convert()` devuelven `Flow<JobState>`. Para cancelar, se cancela la corrutina que colecta; la implementación mata el proceso nativo (y sus hijos) en `awaitClose`/`finally`. Los errores esperables son `MediaError` (sin excepciones). Para extraer info se usa `suspend` + `Outcome`.

**Descartada:** `cancel(id)` separado, como sugería main.md §6.5: duplica el estado de cancelación y abre carreras entre el ID y el proceso.

---

## ADR-006 · Módulos Gradle

**Fecha:** 2026-11-06 · **Estado:** aceptada

**Decisión:** se crean los módulos recién cuando tienen código real:
- `:app`: UI, ViewModels, servicios, DI.
- `:core:domain`: Kotlin JVM puro. Existe desde la Etapa 1.
- `:core:media`: adaptadores de yt-dlp y FFmpeg y runner de procesos (Etapa 3).
- `:core:data`: Room, DataStore y repositorios (Etapa 7/9).

El motor de trabajos (cola, reintentos) se decide en la Etapa 7: puede vivir en `:core:data` o en un `:core:jobs`.

**Descartada:** crear de entrada todos los módulos de main.md §6.4 vacíos.

---

## ADR-007 · Harness reducido

**Fecha:** 2026-11-06 · **Estado:** aceptada

**Contexto:** main.md §13.1 propone 7 archivos de harness y 7 de docs, con solapamientos (NEXT_STEPS/TASKS, SESSION_LOG/CHANGELOG/git log). Muchos archivos que mantener sincronizados se desactualizan rápido.

**Decisión:**
- `CLAUDE.md`: entrada, reglas, puerta de calidad (QUALITY_GATES) y recuperación (RECOVERY).
- `harness/STATE.md`: estado, verificación, bloqueos y problemas conocidos (PROJECT_STATE + KNOWN_ISSUES).
- `harness/TASKS.md`: tareas con ID, prioridad, dependencias y criterio de aceptación (NEXT_STEPS + TASKS).
- `harness/SESSION_LOG.md`.
- `docs/FEASIBILITY.md`, `docs/ARCHITECTURE.md` y `docs/DECISIONS.md`.
- `docs/TESTING.md`, `docs/RELEASE.md` y `CHANGELOG.md` se crean cuando haya contenido real (Etapas 3, 12 y primer release).
- `main.md` cumple el rol de REQUIREMENTS.

---

## ADR-008 · Plataformas prioritarias: YouTube, Dailymotion y TikTok

**Fecha:** 2026-11-06 · **Estado:** aceptada (decidida por el usuario)

**Contexto:** en la prueba técnica, Vimeo exigió iniciar sesión incluso para un video público de Blender.

**Decisión:** las prioritarias son YouTube, Dailymotion y TikTok. Vimeo sale de las prioritarias: funciona si yt-dlp lo soporta, sin garantía. Esto ajusta main.md §2.1.

---

## ADR-009 · Sin emulador: validación en teléfono físico con permiso

**Fecha:** 2026-11-06 · **Estado:** aceptada (pedido del usuario)

**Decisión:** el emulador cuelga la máquina y fue desinstalado. Las pruebas en la JVM son la base; las instrumentadas corren en un teléfono físico, solo con aviso y permiso del usuario (`scripts/test-device.sh` rechaza emuladores).

---

## ADR-010 · Idiomas: inglés por defecto y español

**Fecha:** 2026-11-06 · **Estado:** aceptada

**Decisión:** `values/` en inglés (portfolio y landing en inglés) y `values-es/` en español. Todo texto visible va en recursos.

---

## ADR-011 · Inyección de dependencias con Hilt

**Fecha:** 2026-11-06 · **Estado:** aceptada

**Decisión:** Hilt (con KSP), igual que en otros proyectos del autor. Tiene integración estándar con ViewModel, WorkManager y servicios.

**Descartadas:** Koin (resuelve en runtime, sin chequeo en compilación) y DI manual (escala mal con servicios y workers).

---

## ADR-012 · Sin PO tokens ni login en el MVP

**Fecha:** 2026-11-06 · **Estado:** aceptada

**Decisión:** no se generan PO tokens de YouTube ni se manejan cookies o credenciales en el MVP. Si un contenido los requiere, la app muestra `LoginRequired` o `AccessDenied` con una explicación. Se reevalúa si los formatos básicos de YouTube dejan de funcionar sin ellos.

---

## ADR-013 · Estilo de código: ktlint (android_studio) con comas finales

**Fecha:** 2026-11-06 · **Estado:** aceptada

**Decisión:** ktlint 14.2.0 (plugin Gradle) en todos los subproyectos, con estilo `android_studio`, líneas de 120 y comas finales permitidas. `./gradlew ktlintFormat` corrige el formato. Es parte de `verify.sh`.

---

## ADR-014 · Verificación de firmas OpenPGP sin Bouncy Castle

**Fecha:** 2026-11-07 · **Estado:** aceptada

**Contexto:** el actualizador (T3.7) tiene que verificar `SHA2-256SUMS.sig` de yt-dlp, una firma OpenPGP v4 con RSA y SHA-512. Bouncy Castle (bcpg + bcprov) suma ~8 MB para una sola verificación.

**Decisión:** un verificador mínimo propio (`OpenPgp`), que parsea solo clave RSA v4 y firma v4 binaria y verifica con `java.security.Signature`. Todo lo que no entiende lo rechaza. La clave de yt-dlp se embebe y se fija por huella (`AC0C BBE6 848D 6A87 3464 AF4E 57CF 6593 3B5A 7581`). Está probado con la firma real del release 2026.10.26, verificada antes con `gpg`, y con una clave de prueba para los casos de falla.

**Descartadas:** Bouncy Castle (tamaño); confiar solo en el SHA-256 de GitHub (no protege si se compromete el release); el actualizador de youtubedl-android (no verifica nada y borra la versión anterior).

**Riesgo:** si yt-dlp rota su clave, las actualizaciones fallan con un error explícito hasta publicar un APK con la clave nueva. Es el comportamiento seguro.

---

## ADR-015 · Audio: AAC como M4A, copia sin recodificar y bitrates acotados

**Fecha:** 2026-11-07 · **Estado:** aceptada

**Contexto:** main.md §4.1 pide MP3, M4A, AAC, Opus, OGG, WAV y FLAC, y §4.2 pide bitrates solo donde tengan sentido. yt-dlp (`-x`) copia el audio sin recodificar cuando el códec de origen coincide con el de salida y, en ese caso, ignora `--audio-quality`.

**Decisión:**
- AAC se ofrece como M4A (AAC en contenedor MP4). El `.aac` suelto (ADTS) no admite portada ni metadatos y duplicaría la opción.
- Si el sitio tiene el audio en el códec de salida (AAC → M4A, Opus → Opus), se copia y no se ofrece bitrate: elegirlo no cambiaría nada y sería una opción simulada.
- Bitrates por formato: MP3 128/192/256/320, M4A 128/192/256, Opus 96/128/160, OGG 128/192/256. WAV y FLAC no tienen.
- "Automático" es el menor bitrate de la lista que iguala o supera al original. Si se elige uno mayor que el original, la UI avisa que no mejora la calidad.
- Para recodificar se parte de Opus cuando existe (rinde más que AAC al mismo bitrate). Sin pistas de audio sueltas (Dailymotion), se usa el combinado más liviano.

**Descartadas:** ofrecer `.aac` aparte; mostrar bitrates también cuando se copia.

---

## ADR-016 · Conversor: copia de la entrada, decisión por pista y libx264

**Fecha:** 2026-11-08 · **Estado:** aceptada

**Contexto:** el conversor (main.md §5) trabaja sobre archivos elegidos con el selector de Android, que entrega URIs `content://`. FFmpeg es un proceso aparte: no puede abrir esas URIs y Android no le hereda descriptores de archivo abiertos. En el teléfono, `h264_mediacodec` falla desde el binario ("Failed to create encoder"), porque necesita la máquina virtual de Android.

**Decisión:**
- La entrada se copia a `noBackupFilesDir/mediaforge/jobs/<id>/` (espacio previo: tamaño de entrada + salida estimada + 100 MB) y se borra al terminar, fallar o cancelar. Leerla sin copiar queda como mejora (T6.7).
- `ffprobe` (JSON) describe la entrada después de copiarla; para mostrar el plan antes de convertir se usa `MediaExtractor` sobre la URI, sin copiar.
- Cada pista se decide por separado: se copia si el contenedor de destino admite el códec y no se pidió cambiar la resolución; si no, se recodifica. Destino de audio: el video se descarta (`-vn`) y el audio se copia si ya está en el códec de salida (ADR-015).
- Recodificación: H.264 (libx264 `veryfast`, CRF 23) + AAC 160 kbps para MP4, MKV y MOV; VP9 (`realtime`) + Opus 128 kbps para WebM.
- Una conversión a la vez hasta la Etapa 7.

**Descartadas:** MediaCodec (no se puede usar desde el binario; requeriría un pipeline propio con `MediaCodec`/`MediaMuxer`, se reevalúa si la velocidad no alcanza); pasar la entrada por un pipe (MP4 con el índice al final no se puede leer sin buscar).
