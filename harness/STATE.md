# Estado del proyecto

**Última actualización:** 2026-11-09

## Próximo paso

Etapa 11 cerrada: el MVP cumple los criterios de main.md §15 en el APK con R8 (T11.8). Etapa 12 (distribución): tareas T12.1–T12.9 en `TASKS.md`. MediaForge 1.0.0 publicado (2026-11-09): release v1.0.0 en GitHub y landing en https://franbenaglia.github.io/mediaForgeLanding/. Falta T12.9: bajar el APK desde la landing en el teléfono, como un usuario. Quedan pendientes, P3: T6.7 (leer la entrada sin copiarla) y T7.8 (pausa real de descargas).

## Etapas

- [x] 0. Investigación técnica → [docs/FEASIBILITY.md](../docs/FEASIBILITY.md)
- [x] 1. Harness y estructura
- [x] 2. Interfaz base
- [x] 3. Motor de extracción (todo validado en el teléfono)
- [x] 4. Descarga de video (validada en el teléfono: YouTube y Dailymotion)
- [x] 5. Extracción de audio (validada en el teléfono: MP3, M4A, Opus)
- [x] 6. Conversor multimedia (validado en el teléfono: MP4 → MP3, remux, recodificar y cancelar, WAV/FLAC)
- [x] 7. Gestor de trabajos (validado en el teléfono: varias a la vez, notificación con Cancelar, cerrar desde recientes, reintentar)
- [x] 8. Android Sharesheet (validado en el teléfono: Chrome, YouTube, otras apps, app cerrada o abierta, texto sin enlace, giro)
- [x] 9. Historial y configuración (validado en el teléfono: historial, ajustes, solo Wi‑Fi, carpeta de destino, temporales)
- [x] 10. Funciones avanzadas (validado en el teléfono: recorte, fotogramas, varios archivos, compresión, subtítulos, listas)
- [x] 11. Calidad (validado en el teléfono: R8, seguridad, licencias, pruebas instrumentadas, StrictMode, accesibilidad, prueba final del MVP)
- [ ] 12. Distribución (en curso)

## Implementado

- Proyecto Gradle (`:app`, `:core:domain`), Hilt, tema Material 3 claro/oscuro.
- Navegación tipada con barra inferior (Inicio, Descargas, Conversor, Historial, Ajustes). Las cinco pantallas funcionan.
- Componentes: `MediaCard` (miniatura como slot), `JobRow` (estado, progreso, tamaño y velocidad), `EmptyState`, `ErrorState`; textos de estados y errores; `Formatters` (bytes, duración).
- Contratos de dominio: `MediaExtractor`, `MediaDownloader`, `MediaConverter`, `ExtractorEngine`, `JobState`, `MediaError`.
- `FileNames`: saneado de nombres y nombres únicos, con 14 pruebas.
- `UrlValidator`: solo http/https, sin credenciales, sin direcciones locales, nada que empiece con `-`, hosts internacionales y búsqueda de URL en texto compartido, con 20 pruebas. Hecho antes de tiempo (Etapa 3) porque lo necesita T2.2.
- Pantalla Inicio (descarga, T4.7): elegir calidad, nombre, descargar con progreso, cancelar, guardar en la galería y abrir/compartir. Desde la Etapa 7 encola en la cola de trabajos.
- Pantalla Inicio: campo de enlace, pegar (extrae la URL de un texto), borrar y analizar. `HomeViewModel` con estados vacío/escribiendo/inválido/listo y motivo del error. "Analizar" ejecuta el análisis real: cargando → `MediaCard` con la cantidad de formatos, o error con reintento. Cambiar el enlace cancela el análisis en curso (y el proceso). Miniatura con Coil (T4.1). Si el error es "motor desactualizado", ofrece "Actualizar motor" y reintenta solo (T3.9, validado en el teléfono).
- `:core:media`: youtubedl-android 0.18.1 (Python, FFmpeg, QuickJS), un APK por ABI y `EngineInitializer` (una sola vez, fuera del hilo principal, chequea antes el espacio libre; estado observable). Todavía nadie lo llama: lo usa T3.6.
- `YtDlpInfoParser`: `--dump-json` → `MediaInfo` con la lista completa de formatos (descarta storyboards), tamaños exactos o aproximados, playlists rechazadas, JSON inválido como error. 13 pruebas.
- `YtDlpErrorClassifier`: stderr → `MediaError` (login, acceso denegado, bot check, DRM, red, espacio, sitio no soportado, motor desactualizado). Un 403 con el aviso de "versión de más de 90 días" sugiere actualizar. El detalle se guarda sin query strings. 17 pruebas.
- `ProcessRunner`: argumentos en lista vía `sh -c 'echo $$; exec "$@"'` (PID real, sin interpretar argumentos), stdout/stderr en streaming, timeout, y al cancelar mata hijos y proceso (buscados en `/proc`). 9 pruebas en la JVM con procesos reales.
- `YtDlpExtractor` (implementa `MediaExtractor`, registrado en Hilt): valida URL → `ensureReady` → `yt-dlp --dump-json` con `ProcessRunner` → parser o clasificador. Usa el mismo entorno que la librería, armado en `YoutubeDlNativeEngine.runtime()`. 9 pruebas.
- `YtDlpUpdater` (implementa `ExtractorEngine`): última versión permitida por `_update_spec` → `SHA2-256SUMS` con firma OpenPGP de la clave fijada de yt-dlp (`OpenPgp`, sin Bouncy Castle) → SHA-256 del zipapp → prueba de `--version` → activa y conserva la anterior (`YtDlpVersions`). El motor usa la versión activa o la del APK. 22 pruebas.
- Descarga de video (T4.3–T4.5): comando con formatos concretos, `YtDlpProgressParser` (varias partes como un avance, merge, metadatos) y `YtDlpDownloader` (registrado en Hilt; carpeta privada por trabajo que se borra al fallar o cancelar).
- `MediaLibrary` / `MediaStoreLibrary` (T4.6): copia a `Movies/MediaForge` o `Music/MediaForge` con `IS_PENDING` (Android 10+) o permiso de escritura (8–9), nombre único, y borra la copia privada.
- Audio (Etapa 5, ADR-015): `AudioOptions` (copia sin recodificar, bitrates, automático), comando `-x` con portada y metadatos (mutagen 1.47 en el APK), modo "Solo audio" en Inicio con avisos de calidad. Se guarda en `Music/MediaForge`.
- Conversor (Etapa 6, ADR-016): `ConversionPlanner` (copia o recodifica por pista), `FfmpegConverter` (copia la entrada a una carpeta privada, `ffprobe`, FFmpeg con progreso y cancelación), `ExtractorInspector` (análisis rápido con `MediaExtractor`) y pantalla Conversor. FFmpeg usa las librerías de Python, FFmpeg y aria2c en `LD_LIBRARY_PATH`.
- Gestor de trabajos (Etapa 7, ADR-017): `JobScheduler` (dominio; 2 descargas y 1 conversión a la vez, reintento automático por red, recuperación al abrir la app), Room en `:core:data`, `JobService` en primer plano con notificaciones, pantalla Descargas. Inicio y Conversor encolan y dejan seguir agregando trabajos.
- Conversor avanzado (Etapa 10, ADR-020): recorte con corte rápido o exacto, fotograma JPG/PNG, varios archivos a la vez, compresión por calidad.
- Subtítulos incrustados al descargar video (ADR-021); si fallan, el video se guarda sin ellos y se avisa.
- Listas de reproducción públicas (ADR-021): hasta 200 elementos, mismo modo y calidad, un trabajo por elemento.
- Historial y ajustes (Etapa 9, ADR-019): tabla `history` (Room v2), DataStore, pantallas Historial y Ajustes, solo Wi‑Fi, carpeta de destino con el selector del sistema, limpieza de temporales. El idioma sigue al del teléfono.
- Compartir enlaces (Etapa 8, ADR-018): `ACTION_SEND` de texto, `SharedText.findUrl`, `ShareInbox`; la app pasa a Inicio y analiza sola.
- `VideoOptions` (dominio): una calidad por altura con formatos concretos y tamaño estimado (T4.2).
- `scripts/verify.sh`, `scripts/test-device.sh`, `scripts/build-release.sh`.

## Última verificación

| Comando | Resultado | Fecha |
|---|---|---|
| `./scripts/verify.sh` | OK: 426 pruebas (132 dominio + 129 app + 151 media + 14 data), lint sin errores, ktlint OK | 2026-11-08 |
| T10.8 manual en 24115RA8EG | OK: todos los pasos | 2026-11-08 |
| Arranque en frío, debug, 24115RA8EG (`am start -W`) | 1,51–2,06 s antes de T11.6; 1,42–1,61 s después | 2026-11-08 |
| Memoria recodificando un video largo (debug) | App: 157 MB PSS (heap Java 14 MB, nativo 16 MB), estable; FFmpeg: 120 MB RSS, pico 124 MB, estable | 2026-11-08 |
| T11.8 manual en 24115RA8EG ("MediaForge R8", instalación limpia) | OK: los criterios de main.md §15 y TikTok | 2026-11-09 |
| T11.7 manual en 24115RA8EG | OK: texto al 200 % y TalkBack | 2026-11-09 |
| `AppDeviceTest` en 24115RA8EG | OK 3/3 (con el permiso de ventanas en segundo plano de HyperOS) | 2026-11-08 |
| APK debug arm64-v8a / x86_64 (con motores y Coil) | 70,9 MB / 73,9 MB | 2026-11-08 |
| `RoomJobStoreDeviceTest` en 24115RA8EG | OK 2/2 | 2026-11-08 |
| T7.7 manual en 24115RA8EG | OK: todos los pasos | 2026-11-08 |
| T8.3 manual en 24115RA8EG | OK: todos los casos | 2026-11-08 |
| T9.10 manual en 24115RA8EG | OK (idioma por app del sistema no funciona: se quitó) | 2026-11-08 |
| T9.7–T9.9 manual en 24115RA8EG | OK: solo Wi‑Fi, carpeta de destino, temporales | 2026-11-08 |
| `ConverterDeviceTest` en 24115RA8EG | OK: análisis y casos de main.md §5.1, cancelación sin procesos ni archivos | 2026-11-08 |
| `EngineDeviceTest` en 24115RA8EG (Android 15, arm64) | 5/5 OK: init 2,7 s (primera vez) / 26 ms; actualización 2025.11.12 → 2026.10.26 en 7,7 s; YouTube 57 formatos; Dailymotion OK; cancelación sin procesos sueltos | 2026-11-07 |

Último commit validado: ver `git log -1` (commit de cierre de la Etapa 9).

## Dependencias importantes

AGP 9.4.1 · Gradle 9.8.1 · Kotlin 2.4.21 · Compose BOM 2026.09.00 · Hilt 2.60.1 · ktlint plugin 14.2.0.
youtubedl-android 0.18.1 (ADR-001) · Coil 3.6.3 (miniaturas) · Room 2.8.5 (ADR-017).

## Bloqueos

Ninguno.

## Problemas conocidos

- El yt-dlp incluido en el APK (2025.11.12) da 403 en YouTube y Dailymotion hasta que se actualiza. Desde T11.9 la app lo actualiza sola al primer arranque (validado); sin red en ese momento, la primera descarga puede fallar hasta el próximo arranque.

- APK con R8 probado en el teléfono con la variante `minified` (T11.1). Si se agregan dependencias que usan reflexión, volver a probarla.

- Advertencias de lint aceptadas: `OldTargetApi` (target 36 a propósito, ADR-004); `AndroidGradlePluginVersion` y `NewerVersionAvailable` (hay versiones nuevas; se actualizan en una tarea propia, no a mitad de etapa); `DataExtractionRules` (`allowBackup=false` sigue haciendo falta para Android ≤ 11).
- El teléfono de pruebas rechazó varias veces (2026-11-08) el servicio en primer plano con `InvalidForegroundServiceTypeException` ("type none") al empezar una conversión, y la app se cerraba. Causa sin confirmar (¿límite de 6 h de `mediaProcessing` o el sistema del fabricante?). Desde 02f198c, `JobService` reintenta con `dataSync` y, si tampoco puede, se detiene sin cerrar la app (queda un aviso `JobService` en logcat). Se repitió en T11.8 (2026-11-09, instalación limpia, dos conversiones): el teléfono rechaza `mediaProcessing` de forma sistemática y el respaldo funciona. Las conversiones cuentan entonces contra el límite de `dataSync`.
- `YtDlpDownloaderTest.cancellingKillsTheProcessAndLeavesNoPartialFiles` falló una vez por tiempo (10 s) durante `verify.sh` con la máquina cargada (2026-11-08); pasó 3 veces sola y en el verify siguiente. Si se repite, subir el tiempo o esperar la línea de destino en vez del progreso.
- linuxtv.org (fuente de v4l-utils) responde con una página anti-bots a descargas automáticas (2026-11-09). `package-gpl-sources.sh` lo detecta por SHA-256 y falla. Para v1.0.0 se usó el archivo armado antes, ya verificado. En la próxima versión, buscar un espejo de v4l-utils 1.24.1.
- Errores con "Detalles técnicos" (texto del motor sin query strings, seleccionable).
- Un primer intento de descarga de Dailymotion falló con `Unknown` y no se volvió a reproducir (2026-11-07). Si reaparece, pedir el detalle técnico.
- Pendientes de validar en el teléfono (FEASIBILITY §12): TikTok y primer arranque. (Tiempos de conversión en arm64 medidos en la Etapa 6.) (Análisis de Dailymotion y cancelación con ffmpeg hijo ya validados.)
- Verificación de desarrolladores de Google (global en 2027): decidir cuenta y clave de firma antes de la Etapa 12.
- La máquina se volvió a colgar (2026-11-08) con un build y las extensiones de Java de VS Code corriendo su propio Gradle (~1 GB cada uno) aunque la importación estaba desactivada. El usuario desactivó para este proyecto "Gradle for Java" y "Language Support for Java (Red Hat)". Antes de compilar, chequear `pgrep -fa GradleDaemon`.
- La máquina se colgó durante un build (2026-11-07). Se limitó Gradle (2 workers, heap de 1,5 GB, daemon de Kotlin de 1 GB) y `verify.sh` corre en dos pasos. Pico medido después del cambio: 8,9 GB usados en total.
- El disco de la máquina de desarrollo tiene poco espacio (~21 GB libres al 2026-11-06).
