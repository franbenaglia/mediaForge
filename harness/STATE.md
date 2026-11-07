# Estado del proyecto

**Última actualización:** 2026-11-07

## Próximo paso

Tarea **T3.7** (actualizador verificado de yt-dlp). Después, volver a correr `EngineDeviceTest` en el teléfono (Dailymotion y cancelación con ffmpeg) y probar la app a mano (T3.8).

## Etapas

- [x] 0. Investigación técnica → [docs/FEASIBILITY.md](../docs/FEASIBILITY.md)
- [x] 1. Harness y estructura
- [x] 2. Interfaz base
- [ ] 3. Motor de extracción (en curso: T3.1–T3.6 y T3.8 hechas; T3.3, T3.5, T3.6 y T3.8 sin validar en el teléfono; falta T3.7)
- [ ] 4. Descarga de video
- [ ] 5. Extracción de audio
- [ ] 6. Conversor multimedia
- [ ] 7. Gestor de trabajos
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
- Pantalla Inicio: campo de enlace, pegar (extrae la URL de un texto), borrar y analizar. `HomeViewModel` con estados vacío/escribiendo/inválido/listo y motivo del error. "Analizar" ejecuta el análisis real: cargando → `MediaCard` con la cantidad de formatos, o error con reintento. Cambiar el enlace cancela el análisis en curso (y el proceso). Todavía sin miniatura: falta la librería de imágenes.
- `:core:media`: youtubedl-android 0.18.1 (Python, FFmpeg, QuickJS), un APK por ABI y `EngineInitializer` (una sola vez, fuera del hilo principal, chequea antes el espacio libre; estado observable). Todavía nadie lo llama: lo usa T3.6.
- `YtDlpInfoParser`: `--dump-json` → `MediaInfo` con la lista completa de formatos (descarta storyboards), tamaños exactos o aproximados, playlists rechazadas, JSON inválido como error. 13 pruebas.
- `YtDlpErrorClassifier`: stderr → `MediaError` (login, acceso denegado, bot check, DRM, red, espacio, sitio no soportado, motor desactualizado). Un 403 con el aviso de "versión de más de 90 días" sugiere actualizar. El detalle se guarda sin query strings. 17 pruebas.
- `ProcessRunner`: argumentos en lista vía `sh -c 'echo $$; exec "$@"'` (PID real, sin interpretar argumentos), stdout/stderr en streaming, timeout, y al cancelar mata hijos y proceso (buscados en `/proc`). 9 pruebas en la JVM con procesos reales.
- `YtDlpExtractor` (implementa `MediaExtractor`, registrado en Hilt): valida URL → `ensureReady` → `yt-dlp --dump-json` con `ProcessRunner` → parser o clasificador. Usa el mismo entorno que la librería, armado en `YoutubeDlNativeEngine.runtime()`. 9 pruebas.
- `scripts/verify.sh`, `scripts/test-device.sh`, `scripts/build-release.sh`.

## Última verificación

| Comando | Resultado | Fecha |
|---|---|---|
| `./scripts/verify.sh` | OK: 120 pruebas (34 dominio + 30 app + 56 media) + 4 instrumentadas del motor, lint sin errores, ktlint OK | 2026-11-07 |
| APK debug arm64-v8a / x86_64 (con motores) | 64,6 MB / 67,5 MB | 2026-11-07 |
| `EngineDeviceTest` en 24115RA8EG (Android 15, arm64) | init 2,7 s (primera vez) / 20 ms; YouTube OK 5,4 s, 35 formatos; Dailymotion 403 (yt-dlp viejo); cancelación sin validar | 2026-11-07 |

Último commit validado: ver `git log -1` (commit de cierre de la Etapa 2).

## Dependencias importantes

AGP 9.4.1 · Gradle 9.8.1 · Kotlin 2.4.21 · Compose BOM 2026.09.00 · Hilt 2.60.1 · ktlint plugin 14.2.0.
youtubedl-android 0.18.1 (ADR-001).

## Bloqueos

Ninguno.

## Problemas conocidos

- El yt-dlp incluido en el APK (2025.11.12) ya falla con Dailymotion (403). T3.7 es prioritaria.
- Fixtures de yt-dlp: YouTube ya es real; Dailymotion sigue reconstruido hasta T3.7.

- El APK de release (R8) todavía no se probó con youtubedl-android. Hay reglas en `core/media/consumer-rules.pro` (Jackson), pero se validan en el teléfono antes del primer release.

- Advertencias de lint aceptadas: `OldTargetApi` (target 36 a propósito, ADR-004); `AndroidGradlePluginVersion` y `NewerVersionAvailable` (hay versiones nuevas; se actualizan en una tarea propia, no a mitad de etapa); `DataExtractionRules` (`allowBackup=false` sigue haciendo falta para Android ≤ 11).
- Pendientes de validar en el teléfono (FEASIBILITY §12): descarga de YouTube con yt-dlp actualizado, Dailymotion y TikTok, cancelación con ffmpeg como proceso hijo, tiempos de conversión en arm64 y primer arranque.
- Verificación de desarrolladores de Google (global en 2027): decidir cuenta y clave de firma antes de la Etapa 12.
- La máquina se colgó durante un build (2026-11-07). Se limitó Gradle (2 workers, heap de 1,5 GB, daemon de Kotlin de 1 GB) y `verify.sh` corre en dos pasos. Pico medido después del cambio: 8,9 GB usados en total.
- El disco de la máquina de desarrollo tiene poco espacio (~21 GB libres al 2026-11-06).
