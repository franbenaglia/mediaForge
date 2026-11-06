# Estado del proyecto

**Última actualización:** 2026-11-06

## Próximo paso

Etapa 2, tarea **T2.1** en [TASKS.md](TASKS.md).

## Etapas

- [x] 0. Investigación técnica → [docs/FEASIBILITY.md](../docs/FEASIBILITY.md)
- [x] 1. Harness y estructura
- [ ] 2. Interfaz base
- [ ] 3. Motor de extracción
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

- Proyecto Gradle (`:app`, `:core:domain`), Hilt, tema Material 3 claro/oscuro, pantalla provisoria.
- Contratos de dominio: `MediaExtractor`, `MediaDownloader`, `MediaConverter`, `ExtractorEngine`, `JobState`, `MediaError`.
- `FileNames`: saneado de nombres y nombres únicos, con 14 pruebas.
- `scripts/verify.sh`, `scripts/test-device.sh`, `scripts/build-release.sh`.

## Última verificación

| Comando | Resultado | Fecha |
|---|---|---|
| `./scripts/verify.sh` | OK: 14 pruebas, lint sin errores, ktlint OK | 2026-11-06 |
| APK debug (sin motores) | 12 MB | 2026-11-06 |
| Pruebas en dispositivo | No hay todavía | — |

Último commit validado: ver `git log -1` (commit de cierre de la Etapa 1).

## Dependencias importantes

AGP 9.4.1 · Gradle 9.8.1 · Kotlin 2.4.21 · Compose BOM 2026.09.00 · Hilt 2.60.1 · ktlint plugin 14.2.0.
A partir de la Etapa 3: youtubedl-android 0.18.1 (ADR-001).

## Bloqueos

Ninguno.

## Problemas conocidos

- Advertencias de lint aceptadas: `OldTargetApi` (target 36 a propósito, ADR-004) y `AndroidGradlePluginVersion` (hay AGP 9.5.1 y Gradle 9.8.2; se actualizan en una tarea propia, no a mitad de etapa).
- Pendientes de validar en el teléfono (FEASIBILITY §12): descarga de YouTube con yt-dlp actualizado, Dailymotion y TikTok, cancelación con ffmpeg como proceso hijo, tiempos de conversión en arm64 y primer arranque.
- Verificación de desarrolladores de Google (global en 2027): decidir cuenta y clave de firma antes de la Etapa 12.
- El disco de la máquina de desarrollo tiene poco espacio (~21 GB libres al 2026-11-06).
