# Estado del proyecto

**Última actualización:** 2026-11-07

## Próximo paso

Etapa 3, tarea **T3.2** (módulo `:core:media` con youtubedl-android). T3.1 ya está hecha.

## Etapas

- [x] 0. Investigación técnica → [docs/FEASIBILITY.md](../docs/FEASIBILITY.md)
- [x] 1. Harness y estructura
- [x] 2. Interfaz base
- [ ] 3. Motor de extracción (en curso: T3.1 hecha)
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
- Pantalla Inicio: campo de enlace, pegar (extrae la URL de un texto), borrar y analizar. `HomeViewModel` con estados vacío/escribiendo/inválido/listo y motivo del error. "Analizar" avisa que el análisis todavía no está disponible (llega con T3.6).
- `scripts/verify.sh`, `scripts/test-device.sh`, `scripts/build-release.sh`.

## Última verificación

| Comando | Resultado | Fecha |
|---|---|---|
| `./scripts/verify.sh` | OK: 61 pruebas (34 dominio + 27 app), lint sin errores, ktlint OK | 2026-11-07 |
| APK debug (sin motores) | 12 MB | 2026-11-06 |
| Pruebas en dispositivo | No hay todavía | — |

Último commit validado: ver `git log -1` (commit de cierre de la Etapa 2).

## Dependencias importantes

AGP 9.4.1 · Gradle 9.8.1 · Kotlin 2.4.21 · Compose BOM 2026.09.00 · Hilt 2.60.1 · ktlint plugin 14.2.0.
A partir de la Etapa 3: youtubedl-android 0.18.1 (ADR-001).

## Bloqueos

Ninguno.

## Problemas conocidos

- Advertencias de lint aceptadas: `OldTargetApi` (target 36 a propósito, ADR-004); `AndroidGradlePluginVersion` y `NewerVersionAvailable` (hay versiones nuevas; se actualizan en una tarea propia, no a mitad de etapa); `DataExtractionRules` (`allowBackup=false` sigue haciendo falta para Android ≤ 11).
- Pendientes de validar en el teléfono (FEASIBILITY §12): descarga de YouTube con yt-dlp actualizado, Dailymotion y TikTok, cancelación con ffmpeg como proceso hijo, tiempos de conversión en arm64 y primer arranque.
- Verificación de desarrolladores de Google (global en 2027): decidir cuenta y clave de firma antes de la Etapa 12.
- La máquina se colgó durante un build (2026-11-07). Se limitó Gradle (2 workers, heap de 1,5 GB, daemon de Kotlin de 1 GB) y `verify.sh` corre en dos pasos. Pico medido después del cambio: 8,9 GB usados en total.
- El disco de la máquina de desarrollo tiene poco espacio (~21 GB libres al 2026-11-06).
