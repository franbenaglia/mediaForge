# Arquitectura

Clean Architecture pragmática: el dominio no conoce Android, yt-dlp ni FFmpeg. Las decisiones están en [DECISIONS.md](DECISIONS.md).

## Módulos

| Módulo | Estado | Contenido | Depende de |
|---|---|---|---|
| `:app` | ✅ Inicio con análisis y descarga | Compose + Material 3, navegación, ViewModels, foreground service, notificaciones, Sharesheet, Hilt | todos |
| `:core:domain` | ✅ contratos | Modelos (`MediaInfo`, `MediaFormat`, `JobState`, `MediaError`), contratos de motores y de `MediaLibrary`, `VideoOptions`, `FileNames`, `UrlValidator` | coroutines |
| `:core:media` | 🟡 sin conversor | `YtDlpExtractor`, `YtDlpDownloader`, `ProcessRunner`, actualizador de yt-dlp, `MediaStoreLibrary`; `FfmpegConverter` en la Etapa 6 | domain, youtubedl-android |
| `:core:data` | 🟡 trabajos | Room: `JobEntity`, `JobDao`, `RoomJobStore` (implementa `JobStore`), pedidos en JSON con versión; DataStore en la Etapa 9 | domain |

Las capas de main.md §6.4 se mapean así: Presentation y Platform → `:app`; Domain → `:core:domain`; Media Engine → `:core:media`; Data → `:core:data`; Download Engine (la cola, `JobScheduler`) → `:core:domain` (ADR-017).

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

**Hoy (Etapa 4)**, sin cola ni servicio: `HomeViewModel` colecta `MediaDownloader.download()` en su `viewModelScope` y, al llegar `Completed`, llama a `MediaLibrary.save()`. Si se cierra la app, la descarga se cancela (la UI lo avisa). La cola, Room y el servicio en primer plano llegan en la Etapa 7.

Calidades: `VideoOptions` arma una opción por altura con formatos concretos (`299+258`) para que el tamaño mostrado sea el que se baja; el comando agrega un respaldo por altura por si el sitio ya no ofrece esos IDs.

## Procesos nativos

- Los binarios (`libpython.so`, `libffmpeg.so`, `libqjs.so`) vienen en `jniLibs` del APK y se ejecutan desde `nativeLibraryDir` (`useLegacyPackaging = true`). Android no permite ejecutar binarios descargados (targetSdk ≥ 29).
- El primer arranque descomprime ~240 MB en `noBackupFilesDir`. Se hace con progreso, fuera del hilo principal y verificando antes el espacio libre.
- yt-dlp (zipapp de Python) es lo único actualizable sin un APK nuevo. Se descarga, se verifica (firma + SHA-256), se prueba y recién entonces se activa, conservando la versión anterior.
- Todo comando se arma como lista de argumentos. Antes de pasarla, la URL se valida (solo `http`/`https`) y se coloca después de `--`.

## Almacenamiento

- Descargas en curso en `noBackupFilesDir/mediaforge/jobs/<jobId>/` (privado, el sistema no lo borra a mitad). Si el trabajo falla o se cancela, se borra la carpeta; al guardar, se borra la copia privada (también si el guardado falla).
- Espacio previo: 2 × tamaño estimado + 100 MB (video y audio conviven en disco durante el merge).
- Resultados en MediaStore (`Movies/MediaForge`, `Music/MediaForge`) con `IS_PENDING` en Android 10+, sin permisos. En Android 8–9, `WRITE_EXTERNAL_STORAGE` (limitado a API 28) y el scanner. Carpeta elegida por SAF: más adelante.
- Nombres por `FileNames.sanitize` + `FileNames.unique`.

## Ejecución en segundo plano

Foreground service de tipo `dataSync` (descargas) y `mediaProcessing` (conversiones, Android 15+), con un límite de 6 h cada 24 h en Android 15+. Los trabajos se persisten en Room; WorkManager reanuda los pendientes después de que muere el proceso. Detalle en FEASIBILITY §10.
