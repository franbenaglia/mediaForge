# Arquitectura

Clean Architecture pragmática: el dominio no conoce Android, yt-dlp ni FFmpeg. Las decisiones están en [DECISIONS.md](DECISIONS.md).

## Módulos

| Módulo | Estado | Contenido | Depende de |
|---|---|---|---|
| `:app` | ✅ esqueleto | Compose + Material 3, navegación, ViewModels, foreground service, notificaciones, Sharesheet, Hilt | todos |
| `:core:domain` | ✅ contratos | Modelos (`MediaInfo`, `MediaFormat`, `JobState`, `MediaError`), contratos de motores, `FileNames` | coroutines |
| `:core:media` | Etapa 3 | `YtDlpExtractor`, `YtDlpDownloader`, `FfmpegConverter`, `ProcessRunner`, actualizador de yt-dlp | domain, youtubedl-android |
| `:core:data` | Etapa 7/9 | Room (trabajos, historial), DataStore (preferencias), repositorios | domain |

Las capas de main.md §6.4 se mapean así: Presentation y Platform → `:app`; Domain → `:core:domain`; Media Engine → `:core:media`; Data y Download Engine → `:core:data` (o `:core:jobs`, según la Etapa 7).

## Flujo de una descarga

```text
UI (URL) ──► ViewModel ──► MediaExtractor.extract(url) ──► YtDlpExtractor
                                                            └─ python + yt-dlp --dump-json (proceso)
         ◄── MediaInfo (formatos reales) ◄──────────────────┘
UI (elige formato) ──► JobRepository.enqueue(DownloadRequest) ──► Room (Pending)
Foreground service ──► MediaDownloader.download(req): Flow<JobState>
                         ├─ Downloading(progress, bytes, speed)   ← stdout de yt-dlp
                         ├─ Processing(MERGE / EXTRACT_AUDIO / TRANSCODE)  ← ffmpeg
                         └─ Completed(outputs) / Failed(error) / Cancelled
                       ──► Room (estado) ──► notificación + UI
```

Para cancelar, se cancela la corrutina del trabajo; el adaptador mata el proceso y sus hijos (ADR-005).

## Procesos nativos

- Los binarios (`libpython.so`, `libffmpeg.so`, `libqjs.so`) vienen en `jniLibs` del APK y se ejecutan desde `nativeLibraryDir` (`useLegacyPackaging = true`). Android no permite ejecutar binarios descargados (targetSdk ≥ 29).
- El primer arranque descomprime ~240 MB en `noBackupFilesDir`. Se hace con progreso, fuera del hilo principal y verificando antes el espacio libre.
- yt-dlp (zipapp de Python) es lo único actualizable sin un APK nuevo. Se descarga, se verifica (firma + SHA-256), se prueba y recién entonces se activa, conservando la versión anterior.
- Todo comando se arma como lista de argumentos. Antes de pasarla, la URL se valida (solo `http`/`https`) y se coloca después de `--`.

## Almacenamiento

- Temporales y archivos parciales en `cacheDir` (privado).
- Resultados en MediaStore (`Movies/MediaForge`, `Music/MediaForge`) o en una carpeta elegida por SAF. Sin permiso de almacenamiento amplio.
- Nombres por `FileNames.sanitize` + `FileNames.unique`.

## Ejecución en segundo plano

Foreground service de tipo `dataSync` (descargas) y `mediaProcessing` (conversiones, Android 15+), con un límite de 6 h cada 24 h en Android 15+. Los trabajos se persisten en Room; WorkManager reanuda los pendientes después de que muere el proceso. Detalle en FEASIBILITY §10.
