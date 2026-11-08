# Estado del proyecto

**Última actualización:** 2026-11-08

## Próximo paso

Etapa 7 (gestor de trabajos, ADR-017): tareas detalladas en `TASKS.md`; próxima T7.1. T6.7 (leer la entrada sin copiarla, P3) queda pendiente.

## Etapas

- [x] 0. Investigación técnica → [docs/FEASIBILITY.md](../docs/FEASIBILITY.md)
- [x] 1. Harness y estructura
- [x] 2. Interfaz base
- [x] 3. Motor de extracción (todo validado en el teléfono)
- [x] 4. Descarga de video (validada en el teléfono: YouTube y Dailymotion)
- [x] 5. Extracción de audio (validada en el teléfono: MP3, M4A, Opus)
- [x] 6. Conversor multimedia (validado en el teléfono: MP4 → MP3, remux, recodificar y cancelar, WAV/FLAC)
- [ ] 7. Gestor de trabajos (en curso)
- [ ] 8. Android Sharesheet
- [ ] 9. Historial y configuración
- [ ] 10. Funciones avanzadas
- [ ] 11. Calidad
- [ ] 12. Distribución

## Implementado

- Proyecto Gradle (`:app`, `:core:domain`), Hilt, tema Material 3 claro/oscuro.
- Navegación tipada con barra inferior (Inicio, Descargas, Conversor, Historial, Ajustes). Inicio funciona; las otras cuatro muestran "todavía no disponible".
- Componentes: `MediaCard` (miniatura como slot), `JobRow` (estado, progreso, tamaño y velocidad), `EmptyState`, `ErrorState`; textos de estados y errores; `Formatters` (bytes, duración).
- Contratos de dominio: `MediaExtractor`, `MediaDownloader`, `MediaConverter`, `ExtractorEngine`, `JobState`, `MediaError`.
- `FileNames`: saneado de nombres y nombres únicos, con 14 pruebas.
- `UrlValidator`: solo http/https, sin credenciales, sin direcciones locales, nada que empiece con `-`, hosts internacionales y búsqueda de URL en texto compartido, con 20 pruebas. Hecho antes de tiempo (Etapa 3) porque lo necesita T2.2.
- Pantalla Inicio (descarga, T4.7): elegir calidad, nombre, descargar con progreso, cancelar, guardar en la galería y abrir/compartir. Corre en el `viewModelScope`: cerrar la app cancela la descarga hasta la Etapa 7.
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
- `VideoOptions` (dominio): una calidad por altura con formatos concretos y tamaño estimado (T4.2).
- `scripts/verify.sh`, `scripts/test-device.sh`, `scripts/build-release.sh`.

## Última verificación

| Comando | Resultado | Fecha |
|---|---|---|
| `./scripts/verify.sh` | OK: 268 pruebas (72 dominio + 63 app + 133 media), lint sin errores, ktlint OK | 2026-11-08 |
| APK debug arm64-v8a / x86_64 (con motores y Coil) | 65,6 MB / 68,5 MB | 2026-11-07 |
| `ConverterDeviceTest` en 24115RA8EG | OK: análisis y casos de main.md §5.1, cancelación sin procesos ni archivos | 2026-11-08 |
| `EngineDeviceTest` en 24115RA8EG (Android 15, arm64) | 5/5 OK: init 2,7 s (primera vez) / 26 ms; actualización 2025.11.12 → 2026.10.26 en 7,7 s; YouTube 57 formatos; Dailymotion OK; cancelación sin procesos sueltos | 2026-11-07 |

Último commit validado: ver `git log -1` (commit de cierre de la Etapa 6).

## Dependencias importantes

AGP 9.4.1 · Gradle 9.8.1 · Kotlin 2.4.21 · Compose BOM 2026.09.00 · Hilt 2.60.1 · ktlint plugin 14.2.0.
youtubedl-android 0.18.1 (ADR-001) · Coil 3.6.3 (miniaturas).

## Bloqueos

Ninguno.

## Problemas conocidos

- El yt-dlp incluido en el APK (2025.11.12) falla con Dailymotion (403) hasta que se actualiza. Con T3.9 la app lo ofrece sola; evaluar actualizar automáticamente en el primer arranque (Etapa 9, ajustes).

- El APK de release (R8) todavía no se probó con youtubedl-android. Hay reglas en `core/media/consumer-rules.pro` (Jackson), pero se validan en el teléfono antes del primer release.

- Advertencias de lint aceptadas: `OldTargetApi` (target 36 a propósito, ADR-004); `AndroidGradlePluginVersion` y `NewerVersionAvailable` (hay versiones nuevas; se actualizan en una tarea propia, no a mitad de etapa); `DataExtractionRules` (`allowBackup=false` sigue haciendo falta para Android ≤ 11).
- Errores con "Detalles técnicos" (texto del motor sin query strings, seleccionable).
- Un primer intento de descarga de Dailymotion falló con `Unknown` y no se volvió a reproducir (2026-11-07). Si reaparece, pedir el detalle técnico.
- Pendientes de validar en el teléfono (FEASIBILITY §12): TikTok y primer arranque. (Tiempos de conversión en arm64 medidos en la Etapa 6.) (Análisis de Dailymotion y cancelación con ffmpeg hijo ya validados.)
- Verificación de desarrolladores de Google (global en 2027): decidir cuenta y clave de firma antes de la Etapa 12.
- La máquina se volvió a colgar (2026-11-08) con un build y las extensiones de Java de VS Code corriendo su propio Gradle (~1 GB cada uno) aunque la importación estaba desactivada. El usuario desactivó para este proyecto "Gradle for Java" y "Language Support for Java (Red Hat)". Antes de compilar, chequear `pgrep -fa GradleDaemon`.
- La máquina se colgó durante un build (2026-11-07). Se limitó Gradle (2 workers, heap de 1,5 GB, daemon de Kotlin de 1 GB) y `verify.sh` corre en dos pasos. Pico medido después del cambio: 8,9 GB usados en total.
- El disco de la máquina de desarrollo tiene poco espacio (~21 GB libres al 2026-11-06).
