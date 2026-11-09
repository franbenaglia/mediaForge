# Arquitectura

Clean Architecture pragmática: el dominio no conoce Android, yt-dlp ni FFmpeg. Las decisiones están en [DECISIONS.md](DECISIONS.md).

## Módulos

| Módulo | Estado | Contenido | Depende de |
|---|---|---|---|
| `:app` | ✅ Inicio con análisis y descarga | Compose + Material 3, navegación, ViewModels, foreground service, notificaciones, Sharesheet, Hilt | todos |
| `:core:domain` | ✅ contratos | Modelos (`MediaInfo`, `MediaFormat`, `JobState`, `MediaError`), contratos de motores y de `MediaLibrary`, `VideoOptions`, `FileNames`, `UrlValidator`, `SharedText` | coroutines |
| `:core:media` | 🟡 sin conversor | `YtDlpExtractor`, `YtDlpDownloader`, `ProcessRunner`, actualizador de yt-dlp, `MediaStoreLibrary`; `FfmpegConverter` en la Etapa 6 | domain, youtubedl-android |
| `:core:data` | 🟡 trabajos | Room: `JobEntity`, `JobDao`, `RoomJobStore` (implementa `JobStore`), pedidos en JSON con versión; DataStore en la Etapa 9 | domain |

Las capas de main.md §6.4 se mapean así: Presentation y Platform → `:app`; Domain → `:core:domain`; Media Engine → `:core:media`; Data → `:core:data`; Download Engine (la cola, `JobScheduler`) → `:core:domain` (ADR-017).

## Flujo de una descarga

```text
UI (URL) ──► ViewModel ──► MediaExtractor.extract(url) ──► YtDlpExtractor
                                                            └─ python + yt-dlp --dump-json (proceso)
         ◄── MediaInfo (formatos reales) ◄──────────────────┘
UI (elige formato) ──► JobQueue.enqueue(Download) ──► Room (QUEUED)
JobScheduler (con JobService en primer plano) ──► MediaDownloader.download(req): Flow<JobState>
                         ├─ Downloading(progress, bytes, speed)   ← stdout de yt-dlp
                         ├─ Processing(MERGE / EXTRACT_AUDIO / TRANSCODE)  ← ffmpeg
                         └─ Completed(outputs) / Failed(error) / Cancelled
                       ──► MediaLibrary.save ──► Room (COMPLETED) ──► notificación + UI
```

Para cancelar, se cancela la corrutina del trabajo; el adaptador mata el proceso y sus hijos (ADR-005).

Desde la Etapa 7, Inicio y Conversor no corren el trabajo: lo encolan en `JobQueue` (`JobScheduler`) y siguen su estado con `JobQueue.workState(id)` hasta que termina. El guardado en la galería lo hace la cola. Salir de la app no corta nada; el conversor conserva el permiso de lectura del archivo elegido (`InputAccess`) hasta que la conversión se guarda.

Recorte (Etapa 10, ADR-020): `ConversionRequest.clip` lleva inicio, fin y "corte exacto". `ConversionPlanner` lo valida contra la duración y recodifica el video solo si se pide corte exacto; `FfmpegCommand` pone `-ss` antes de `-i` y `-t` con la duración, y el progreso se mide sobre el fragmento. Un fotograma es `ConversionTarget.Image` (instante y JPG o PNG): el plan lleva `frameAtSeconds` y el comando saca un solo cuadro; se guarda en `Pictures/MediaForge`. Con varios archivos (`InputState.Batch`, hasta 50), el Conversor encola un trabajo por archivo compatible con el mismo destino.

Subtítulos (ADR-021): el análisis llena `MediaInfo.subtitles`; Inicio pone el elegido en `DownloadSelection.Video.subtitle` y yt-dlp lo incrusta (`--embed-subs`). Si el subtítulo falla, `YtDlpDownloader` vuelve a bajar sin él y termina con `JobNote.SUBTITLES_SKIPPED`, que llega al aviso por `JobScheduler.finished`.

Listas (ADR-021): el análisis siempre usa `--dump-single-json --flat-playlist`; si el sitio devuelve una lista, `MediaInfo.playlist` trae los elementos (sin formatos). Inicio usa `VideoOptions.forPlaylist` (calidades por altura) y encola un `DownloadRequest` por elemento elegido.

Calidades: `VideoOptions` arma una opción por altura con formatos concretos (`299+258`) para que el tamaño mostrado sea el que se baja; el comando agrega un respaldo por altura por si el sitio ya no ofrece esos IDs.

## Procesos nativos

- Los binarios (`libpython.so`, `libffmpeg.so`, `libqjs.so`) vienen en `jniLibs` del APK y se ejecutan desde `nativeLibraryDir` (`useLegacyPackaging = true`). Android no permite ejecutar binarios descargados (targetSdk ≥ 29).
- El primer arranque descomprime ~240 MB en `noBackupFilesDir`. Se hace con progreso, fuera del hilo principal y verificando antes el espacio libre.
- yt-dlp (zipapp de Python) es lo único actualizable sin un APK nuevo. Se descarga, se verifica (firma + SHA-256), se prueba y recién entonces se activa, conservando la versión anterior. `EngineUpdates` (app) lo hace solo al abrir la app, como mucho una vez por semana (`EngineAutoUpdate`, ADR-022).
- Todo comando se arma como lista de argumentos. Antes de pasarla, la URL se valida (solo `http`/`https`) y se coloca después de `--`.

## Almacenamiento

- Descargas en curso en `noBackupFilesDir/mediaforge/jobs/<jobId>/` (privado, el sistema no lo borra a mitad). Si el trabajo falla o se cancela, se borra la carpeta; al guardar, se borra la copia privada (también si el guardado falla).
- Espacio previo: 2 × tamaño estimado + 100 MB (video y audio conviven en disco durante el merge).
- Resultados en MediaStore (`Movies/MediaForge`, `Music/MediaForge`) con `IS_PENDING` en Android 10+, sin permisos. En Android 8–9, `WRITE_EXTERNAL_STORAGE` (limitado a API 28) y el scanner. Carpeta elegida por SAF (Etapa 9): `RoutingLibrary` guarda ahí con `DocumentTreeLibrary` y, si la carpeta ya no sirve, vuelve a la galería y avisa.
- Nombres por `FileNames.sanitize` + `FileNames.unique`.

## Ejecución en segundo plano

ADR-017. `JobScheduler` (dominio) es un singleton con un alcance propio del proceso (`@ApplicationScope`): corre los trabajos, guarda los estados en Room y expone `live` (progreso), `activity` (qué corre) y `finished` (avisos).

- `JobServiceStarter` (se instala en `MediaForgeApplication`) arranca `JobService` cuando `activity.isBusy` pasa a `true` y publica el aviso de cada trabajo terminado.
- `JobService` solo mantiene el proceso en primer plano y actualiza la notificación (dos veces por segundo como mucho), con Cancelar. Tipo `dataSync`, más `mediaProcessing` en Android 15+ mientras corre una conversión. Se detiene solo cuando la cola queda vacía. `onTimeout` (6 h por día en Android 15+) interrumpe lo que corre.
- `MainActivity.onStart` llama a `JobScheduler.start()`: la primera vez recupera lo que quedó a medias si el proceso murió. Sin WorkManager: Android no deja arrancar el servicio desde segundo plano.

## Enlaces compartidos

Otra app comparte texto (`ACTION_SEND`, `text/plain`) → `MainActivity` (`singleTask`, una sola ventana) → `ShareIntents.read` (texto, `ClipData`, asunto) → `ShareInbox` (en memoria) → la app pasa a Inicio y `HomeViewModel` toma el contenido, busca la URL con `SharedText.findUrl` y la analiza. El intent se procesa una vez: en `onCreate` sin estado guardado y sin venir de recientes, y en `onNewIntent` (ADR-018).

## Historial y ajustes

- Cuando la cola completa un trabajo anota una `HistoryEntry` en `HistoryStore` (tabla `history`, ADR-019). Descargas es el administrador de trabajos; Historial es el registro de archivos guardados, con búsqueda, filtros, Descargar de nuevo (vía `ShareInbox`) y Borrar archivo (`SavedFiles`: MediaStore o documento, con la confirmación del sistema si hace falta).
- Temporales: las carpetas `jobs/<id>` de trabajos que no están corriendo se pueden limpiar desde Ajustes (`JobScheduler.withJobsFrozen` evita pisar uno que arranca).
- "Solo Wi‑Fi": `UnmeteredNetwork` + `QueueSettings` → `JobScheduler.setDownloadsAllowed`; el servicio sigue vivo con "Esperando Wi‑Fi".
- Los ajustes (`SettingsStore`) viven en DataStore Preferences (`files/datastore/settings.preferences_pb`). `QueueSettings` lleva el límite de descargas a la cola; Inicio lee los predeterminados al terminar cada análisis.

## Pruebas

- **En la JVM** (`./scripts/verify.sh`, sin red ni teléfono): dominio, ViewModels, comandos de yt-dlp y FFmpeg, parsers, Room y DataStore. Los motores se reemplazan por scripts de `sh` que imitan su salida.
- **En el teléfono** (`./scripts/test-device.sh`, con permiso del usuario):
  - `core:media`: motor, conversor y galería (`EngineDeviceTest`, `ConverterDeviceTest`, `MediaLibraryDeviceTest`).
  - `app`: navegación, enlace compartido y recreación (`AppDeviceTest`).
  - Las que necesitan Internet llevan `@RequiresNetwork`, y `test-device.sh --offline` las saltea (T11.5).
- **Archivos de prueba:** sin material de terceros salvo Big Buck Bunny (Blender Foundation, CC BY), y nunca con URLs firmadas. El origen de cada uno está en un `README.md` junto a los archivos:
  - `ytdlp/`: salidas de yt-dlp recortadas;
  - `ffmpeg/`: `ffprobe` de archivos generados con `lavfi`;
  - `update/`: un release real de yt-dlp con su firma.
- El conversor se prueba con archivos que genera el propio FFmpeg (`testsrc2`, `sine`), así que no depende de ningún sitio.

