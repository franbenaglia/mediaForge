# Tareas

Marcas: `[ ]` pendiente · `[x]` hecha y verificada · `[~]` hecha, falta validar en el teléfono · `[!]` bloqueada (ver STATE.md).
Prioridad: P1 (MVP crítico), P2 (MVP), P3 (posterior al MVP).
Las etapas futuras se detallan al empezarlas: una tarea tiene que entrar en una sesión.

---

## Etapa 1 · Harness y estructura ✅

- [x] **T1.1** Repo git, licencia GPL-3.0, `.gitignore`, `.editorconfig`.
- [x] **T1.2** Gradle (catálogo de versiones, `:app`, `:core:domain`), Hilt, Compose, tema claro/oscuro.
- [x] **T1.3** Contratos y modelos de dominio (ADR-005).
- [x] **T1.4** `FileNames` con pruebas.
- [x] **T1.5** `scripts/verify.sh` con lint y ktlint; scripts de dispositivo y release.
- [x] **T1.6** CLAUDE.md, harness, ARCHITECTURE y DECISIONS.

---

## Etapa 2 · Interfaz base ✅

- [x] **T2.1** · P1 · Navegación (Navigation Compose, rutas tipadas): Inicio, Descargas, Conversor, Historial, Ajustes, con barra inferior.
  *Depende de:* —
  *Acepta:* compila; prueba unitaria del grafo de rutas; cada destino muestra su título desde recursos.
- [x] **T2.2** · P1 · Pantalla Inicio (solo UI): campo URL, botón pegar, botón analizar, `HomeViewModel` con `UiState` (vacío / escribiendo / inválida / lista).
  *Depende de:* T2.1, T3.1
  *Acepta:* prueba de ViewModel: pegar y escribir actualizan el estado; con una URL inválida el botón queda deshabilitado.
- [x] **T2.3** · P2 · Componentes reutilizables: tarjeta de medio, fila de trabajo con progreso, estado vacío, estado de error.
  *Depende de:* T2.1
  *Acepta:* previews en claro y oscuro; sin textos incrustados.

## Etapa 3 · Motor de extracción ✅

- [x] **T3.1** · P1 · `UrlValidator` en el dominio: solo `http`/`https`, host obligatorio, largo máximo, rechazo de URLs que empiecen con `-`, extracción de URL desde texto compartido.
  *Depende de:* —
  *Acepta:* pruebas unitarias (esquemas inválidos, `javascript:`, `file:`, espacios, texto con URL embebida).
- [x] **T3.2** · P1 · Módulo `:core:media` con youtubedl-android 0.18.1, splits por ABI e inicialización en segundo plano con verificación de espacio.
  *Depende de:* —
  *Acepta:* `verify.sh` OK; tamaño del APK por ABI registrado en STATE.md.
- [x] **T3.3** · P1 · Parser propio de `--dump-json` → `MediaInfo` (kotlinx.serialization), tolerante a campos faltantes.
  *Hecho en el teléfono (2026-11-07):* fixture real de YouTube (`youtube-real.json`) con prueba.
  *Hecho en el teléfono (2026-11-07, después de T3.7):* fixture real de Dailymotion (`dailymotion-real.json`) con prueba.
  *Depende de:* T3.2
  *Acepta:* pruebas con fixtures JSON reales de YouTube y Dailymotion (guardados en `core/media/src/test/resources`).
- [x] **T3.4** · P1 · Clasificador de errores: stderr de yt-dlp → `MediaError`.
  *Depende de:* T3.2
  *Acepta:* pruebas con los mensajes reales de la Etapa 0 (`HTTP Error 403`, "only works when logged-in", `Unsupported URL`, errores de red).
- [x] **T3.5** · P1 · `ProcessRunner`: lanza un binario con argumentos en lista, lee stdout/stderr en streaming y, al cancelar la corrutina, mata el árbol de procesos por PID.
  *Depende de:* T3.2
  *Acepta:* pruebas en la JVM con `sleep`/`sh` (cancelación, timeout, código de salida, hijos muertos).
  *Validado en el teléfono (2026-11-07):* `EngineDeviceTest.t4` vio un `libffmpeg.so` real descargando HLS y, al cancelar, no quedó ningún proceso de ffmpeg ni de Python.
- [x] **T3.6** · P1 · `YtDlpExtractor` implementa `MediaExtractor`, con `--cache-dir` propio, `--no-playlist` y `--` antes de la URL.
  *Depende de:* T3.3, T3.4, T3.5
  *Acepta:* prueba unitaria de los argumentos construidos; `[~]` hasta probar YouTube y Dailymotion en el teléfono.
  *Hecho:* comando y extractor probados en la JVM con un yt-dlp falso (éxito, error clasificado, timeout, URL inválida).
  *Teléfono (2026-11-07):* YouTube OK (5,4 s, 35 formatos). Dailymotion falló con 403 con el yt-dlp viejo (clasificado bien como `EngineOutdated`); con 2026.10.26, OK (4,5 s).
- [x] **T3.8** · P1 · Inicio muestra el resultado del análisis: cargando (incluida la primera inicialización del motor), `MediaCard` con datos y cantidad de formatos, o `ErrorState`.
  *Depende de:* T3.6, T2.2
  *Acepta:* pruebas de `HomeViewModel` con un `MediaExtractor` falso (cargando → éxito / error / cancelar al cambiar el enlace); `[~]` hasta probarlo en el teléfono.
  *Validado por el usuario en el teléfono (2026-11-07):* pegar, analizar YouTube (tarjeta con 35 formatos) y error de Dailymotion.
- [x] **T3.7** · P1 · Actualizador de yt-dlp (`ExtractorEngine`): descarga, verificación de firma y SHA-256, prueba de `--version`, activación y conservación de la versión anterior.
  *Depende de:* T3.5
  *Acepta:* pruebas con un release falso (hash incorrecto → rechazo; firma inválida → rechazo; versión rota → rollback).
  *Hecho:* verificación OpenPGP propia (sin Bouncy Castle) probada con la firma real de 2026.10.26 y con una clave de prueba; respeta `_update_spec`; 22 pruebas. *Validado en el teléfono (2026-11-07):* 2025.11.12 → 2026.10.26 en 7,7 s con firma y hash verificados; YouTube pasa de 35 a 57 formatos.
- [x] **T3.9** · P1 · Inicio ofrece "Actualizar motor" cuando el error es `EngineOutdated`, y reintenta el análisis al terminar.
  *Depende de:* T3.7
  *Acepta:* pruebas de `HomeViewModel` (actualiza → reintenta; falla la actualización → error explicado); `[~]` hasta el teléfono.
  *Validado por el usuario en el teléfono (2026-11-07):* Dailymotion → "motor desactualizado" → "Actualizar motor" → reanaliza solo y muestra la tarjeta. La tarjeta no tiene miniatura (falta la librería de imágenes, T4.1).

## Etapa 4 · Descarga de video ✅

Criterio del MVP: punto 6 de main.md §15. La descarga corre en el `viewModelScope` (si se cierra la app, se cancela); el servicio en primer plano y la cola llegan en la Etapa 7.

- [x] **T4.1** · P2 · Miniatura en `MediaCard` con Coil 3 (solo `https`; sin miniatura o con error, fondo neutro).
  *Depende de:* T3.8
  *Acepta:* `verify.sh` OK; `[~]` hasta ver la miniatura de YouTube y Dailymotion en el teléfono.
  *Hecho:* `RemoteThumbnail` (Coil 3.6.3 + OkHttp), usado en la tarjeta de Inicio. Validada por el usuario en el teléfono (2026-11-07).
- [x] **T4.2** · P1 · Opciones de calidad en el dominio (`VideoOptions`): "mejor calidad" más una opción por altura disponible (video+audio o video solo + mejor audio), con tamaño estimado y sin duplicados.
  *Depende de:* —
  *Acepta:* pruebas con los fixtures reales de YouTube (formatos separados) y Dailymotion (HLS combinado).
  *Hecho:* formatos concretos por opción (tamaño exacto cuando el sitio lo da), H.264 > VP9 > AV1, AAC sin DRC, audio ≤ 160 kbps debajo de 720p. 13 pruebas.
- [x] **T4.3** · P1 · Comando de descarga (`YtDlpCommand.download`): selector `-f` desde `DownloadSelection.Video`, merge a MP4 sin recodificar, salida en una carpeta temporal por trabajo, plantilla de progreso propia, `--` antes de la URL.
  *Depende de:* T4.2
  *Acepta:* pruebas de los argumentos (mejor calidad, altura máxima, formatos explícitos, URL que parece opción).
  *Hecho:* formatos concretos con respaldo por altura (`299+258/bv*[height<=1080]+ba/b[height<=1080]`); los IDs se validan para que no cambien la sintaxis del selector. 6 pruebas nuevas.
- [x] **T4.4** · P1 · Parser de progreso: líneas de la plantilla → `JobState.Downloading` (bytes, total o estimado, velocidad), varias partes (video y audio) como un solo avance, `[Merger]` → `Processing(MERGE)`.
  *Depende de:* T4.3
  *Acepta:* pruebas con salida real o reconstruida (tamaños desconocidos, `NA`, dos partes, merge).
  *Hecho:* `YtDlpProgressParser` con salida reconstruida (6 pruebas); se valida con salida real en T4.8.
- [x] **T4.5** · P1 · `YtDlpDownloader` implementa `MediaDownloader`: espacio libre, ejecución con `ProcessRunner`, errores clasificados, y al cancelar o fallar borra la carpeta temporal.
  *Depende de:* T4.3, T4.4
  *Acepta:* pruebas en la JVM con un yt-dlp falso (éxito, error, cancelación sin parciales).
  *Hecho:* carpeta por trabajo en `noBackupFilesDir/mediaforge/jobs`; espacio = 2 × estimado + 100 MB; `DownloadRequest.expectedBytes`. 7 pruebas. Sin timeout total (descargas largas).
- [x] **T4.6** · P1 · Guardado en `Movies/MediaForge` por MediaStore (API 29+: `RELATIVE_PATH` + `IS_PENDING`; API 26–28: permiso `WRITE_EXTERNAL_STORAGE` limitado a 28), con `FileNames.sanitize`/`unique`.
  *Depende de:* T4.5
  *Acepta:* prueba instrumentada en el teléfono (archivo visible en la galería, nombre repetido → sufijo).
  *Hecho:* `MediaLibrary` (dominio) y `MediaStoreLibrary`; `MediaLibraryDeviceTest` OK en el teléfono (2026-11-07): `Movies/MediaForge/`, `IS_PENDING` en 0, nombre repetido → ` (1)`, copia privada borrada. La ruta de Android 8–9 no se puede validar con el teléfono actual (Android 15).
- [x] **T4.7** · P1 · Inicio: elegir calidad, nombre editable, descargar, progreso (`JobRow`), cancelar y, al terminar, abrir o compartir.
  *Depende de:* T4.2, T4.5, T4.6
  *Acepta:* pruebas de `HomeViewModel` con un `MediaDownloader` falso; `[~]` hasta probarlo en el teléfono.
  *Hecho:* `DownloadPanel` (calidades con tamaño, nombre, progreso con `JobRow`, cancelar, abrir/compartir, aviso de dejar la app abierta), enlace bloqueado mientras descarga, permiso en Android 8–9. 9 pruebas nuevas del ViewModel. *Validada por el usuario (2026-11-07):* YouTube 360p descargado, reproducido con audio, cancelación OK. Arreglado: el teclado se abría solo al terminar (el campo bloqueado conservaba el foco); falta confirmar el arreglo.
- [x] **T4.8** · P1 · Prueba en el teléfono: descargar un video corto de YouTube (merge) y uno de Dailymotion, y cancelar a mitad sin dejar parciales.
  *Depende de:* T4.7
  *Acepta:* `EngineDeviceTest` o validación manual registrada en STATE.md.
  *Validada a mano por el usuario (2026-11-07):* YouTube (merge) y Dailymotion (HLS) descargados y reproducidos; cancelar sin parciales. Un primer intento de Dailymotion falló con `Unknown` sin detalle visible y no se pudo reproducir; desde entonces los errores muestran "Detalles técnicos". `EngineDeviceTest.t5` descarga Dailymotion (necesita correr antes `t1b`, porque reinstalar el APK de prueba borra el yt-dlp actualizado).

## Etapa 5 · Extracción de audio ✅

Criterio del MVP: punto 7 de main.md §15 (MP3 de contenido autorizado). El Python del APK trae `mutagen` 1.47 (verificado en el teléfono): portada y metadatos en MP3, M4A, Opus, OGG y FLAC.

- [x] **T5.1** · P1 · `AudioOptions` en el dominio: un formato por opción (MP3, M4A, Opus primero; OGG, WAV, FLAC), formato de origen concreto, copia sin recodificar cuando el códec coincide, bitrates por formato (ninguno si es sin pérdida o se copia), automático = el menor ≥ al original, aviso si el bitrate supera al original, tamaño estimado. ADR-015 (AAC como M4A).
  *Depende de:* —
  *Acepta:* pruebas con formatos armados y con los fixtures reales de YouTube y Dailymotion.
  *Hecho:* `AudioOptions` + `DownloadSelection.Audio.formatIds`. 11 pruebas + 2 con fixtures reales.
- [x] **T5.2** · P1 · `YtDlpCommand.download` para audio (`-x`, `--audio-format`, `--audio-quality`, `--embed-thumbnail` salvo WAV) y pasos de audio en el parser (`ExtractAudio`, `EmbedThumbnail`).
  *Depende de:* T5.1
  *Acepta:* pruebas de argumentos y de líneas de salida.
  *Hecho:* origen `<id>/bestaudio/best`, `--audio-quality <k>K` solo al recodificar, OGG = `vorbis`; parser con `ExtractAudio` ("Not converting" = extracción sin recodificar), `EmbedThumbnail` y `ThumbnailsConvertor`. 6 pruebas nuevas.
- [x] **T5.3** · P1 · `YtDlpDownloader` acepta `DownloadSelection.Audio`: salida por extensión esperada (no la portada ni el original) y MIME que MediaStore acepta.
  *Depende de:* T5.2
  *Acepta:* pruebas con yt-dlp falso; `MediaLibraryDeviceTest` guarda MP3, M4A, Opus, OGG, WAV y FLAC en `Music/MediaForge` sin cambiar el nombre.
  *Hecho:* salida por extensión esperada (ignora portadas sueltas), MIME por formato (WAV = `audio/x-wav`). Prueba con yt-dlp falso OK; `savesEveryAudioFormatIntoMusic` OK en el teléfono (2026-11-08): los seis formatos en `Music/MediaForge/` sin cambio de nombre.
- [x] **T5.4** · P1 · Inicio: elegir Video o Audio; en audio, formato, bitrate (con "Automático"), avisos de calidad y tamaño.
  *Depende de:* T5.1, T5.3
  *Acepta:* pruebas de `HomeViewModel`; `[~]` hasta probarlo en el teléfono.
- [x] **T5.5** · P1 · Prueba en el teléfono: MP3 320 kbps con portada y título, M4A sin recodificar, Opus.
  *Depende de:* T5.4
  *Acepta:* validación manual registrada (el archivo se reproduce y muestra portada en la app de música).
  *Validada por el usuario (2026-11-08):* MP3 320 kbps con aviso de bitrate, portada y título; M4A sin recodificar; Opus. Todo se reproduce.

## Etapa 6 · Conversor (hecha)

Criterio del MVP: MP4 → MP3 (main.md §15). Sin yt-dlp: solo `libffmpeg.so` y `libffprobe.so` del APK (ADR-002, ADR-016). Mediciones en el teléfono (2026-11-08, 20 s de 720p30): remux 0,03 s; MP3 0,15 s; libx264 `veryfast` 3,8 s y 263 MB de RAM; VP9 `realtime` 4,6 s. `h264_mediacodec` no funciona desde el binario.

- [x] **T6.1** · P1 · Dominio: `LocalMedia` (pistas, códecs, duración) y `ConversionPlanner`: por pista, copiar si el contenedor de destino admite el códec, recodificar si no, descartar el video si el destino es audio; error explicado si no hay pista útil. ADR-016.
  *Depende de:* —
  *Acepta:* pruebas de la tabla de main.md §5.1 (MKV → MP4 remux, WebM → MP4 recodifica, MP4 → MP3, WAV → FLAC, audio → video imposible).
  *Hecho:* `LocalMedia`, `ConversionPlanner` (copia/recodifica/descarta por pista, reducción de resolución, estimación de tamaño), `MediaError.UnsupportedConversion`, pasos `REMUX` y `PREPARE_INPUT`. 16 pruebas.
- [x] **T6.2** · P1 · `:core:media`: parser de `ffprobe` (JSON), comando de FFmpeg desde el plan (`-nostdin`, `-progress pipe:1`, argumentos en lista, `-map` explícito) y parser de progreso (`out_time_us` / duración).
  *Depende de:* T6.1
  *Acepta:* pruebas con las salidas reales de `ffprobe` del teléfono.
  *Hecho:* `FfprobeParser` (ignora portadas `attached_pic`), `FfmpegCommand` (`-map 0:v:0`/`0:a:0?`, `-map_metadata 0`, `+faststart` en MP4/MOV/M4A), `FfmpegProgressParser`. 14 pruebas.
- [x] **T6.3** · P1 · `FfmpegConverter` implementa `MediaConverter`: copia la entrada a una carpeta privada por trabajo (con chequeo de espacio), `ffprobe`, plan, FFmpeg con progreso, cancelación y limpieza.
  *Depende de:* T6.2
  *Acepta:* pruebas en la JVM con ffmpeg/ffprobe falsos (éxito, error, cancelación sin parciales, entrada sin pistas).
  *Hecho:* `FfmpegConverter` + `InputSource` (`ContentInputSource` copia con cancelación por bloque), `FfmpegRuntime` desde `YoutubeDlNativeEngine`, registrado en Hilt. 8 pruebas.
- [x] **T6.4** · P1 · Análisis rápido de un archivo local sin copiarlo (`MediaExtractor` de Android → `LocalMedia`), para mostrar el plan antes de convertir.
  *Depende de:* T6.1
  *Acepta:* prueba instrumentada en el teléfono con archivos generados por FFmpeg.
  *Hecho:* `MediaInspector` (dominio), `ExtractorInspector` + `MimeCodecs`; `ConverterDeviceTest` OK en el teléfono (2026-11-08): análisis de MP4/WebM/WAV y los casos de §5.1 con URIs reales (MP4 → MP3 0,56 s; → M4A copia 0,40 s; → MKV remux 0,45 s; WebM → MP4 1,2 s; 480p 3,0 s; WAV → FLAC 0,43 s), cancelación sin procesos ni archivos. Encontró que FFmpeg necesita también las librerías de Python (`libc++_shared.so`): corregido.
- [x] **T6.5** · P1 · Pantalla Conversor: elegir archivo (SAF), datos del archivo, destino (contenedor de video o formato de audio con bitrate, resolución opcional), qué se copia y qué se recodifica, progreso, cancelar, guardar y abrir/compartir.
  *Depende de:* T6.3, T6.4
  *Acepta:* pruebas del ViewModel; `[~]` hasta probarlo en el teléfono.
  *Hecho:* `ConverterViewModel` (11 pruebas) y `ConverterScreen`. Validada por el usuario junto con T6.6.
- [x] **T6.6** · P1 · Prueba en el teléfono: MP4 → MP3 (MVP), MKV → MP4 sin recodificar, WebM → MP4, WAV → FLAC, cancelar a mitad.
  *Depende de:* T6.5
  *Acepta:* validación manual registrada.
  *Validada por el usuario (2026-11-08):* MP4 → MP3, cambio de contenedor sin recodificar, recodificación con cancelación a mitad y audio a WAV/FLAC desde la pantalla Conversor. Todo OK.
- [ ] **T6.7** · P3 · Leer la entrada sin copiarla (descriptor de archivo como stdin + protocolo `fd:` de FFmpeg) cuando sea buscable; copia como respaldo.
  *Depende de:* T6.3
  *Acepta:* prueba en el teléfono con un archivo grande; sin cambio de resultado.

## Etapa 7 · Gestor de trabajos (hecha; T7.8 queda pendiente, P3)

Descargas y conversiones pasan a una cola persistente que corre fuera de las pantallas, en un servicio en primer plano con notificación (main.md §7.3, §7.4, §8.3, §9). Diseño en ADR-017: la cola es lógica de dominio (probada en la JVM), Room vive en `:core:data` y el servicio en `:app`. Sin WorkManager: Android no deja arrancar el servicio desde segundo plano, así que los trabajos interrumpidos se recuperan al abrir la app.

- [x] **T7.1** · P1 · Dominio: `Job` (id, pedido de descarga o conversión, título, sitio, miniatura, estado persistido, error, archivo guardado, intentos, fechas), `JobStatus` (en cola, en curso, guardando, completado, fallido, cancelado, interrumpido) y contrato `JobStore` (guardar, observar todos, observar uno, borrar).
  *Depende de:* —
  *Acepta:* reglas de estado probadas (qué se puede cancelar, reintentar, borrar).
  *Hecho:* `domain/jobs/Job.kt` (`JobRequest`, `JobStatus`, `Job`, `JobStore`). 5 pruebas.
- [x] **T7.2** · P1 · Dominio: `JobScheduler`. Cola en orden de llegada; hasta 2 descargas y 1 conversión a la vez; corre `MediaDownloader`/`MediaConverter` y guarda con `MediaLibrary`; progreso en vivo en memoria y en el `JobStore` solo los cambios de estado; cancelar, reintentar a mano, borrar; reintento automático solo para errores de red (2 veces, a los 5 s y 15 s); al arrancar, lo que quedó "en curso" vuelve a la cola (hasta 3 intentos) y si no, queda "interrumpido".
  *Depende de:* T7.1
  *Acepta:* pruebas en la JVM con motores falsos y tiempo virtual (concurrencia, orden, cancelación, reintentos, recuperación).
  *Hecho:* `JobScheduler` (`live`, `activity`, `finished`; `start`, `enqueue`, `cancel`, `retry`, `delete`, `interruptAll`). 12 pruebas.
- [x] **T7.3** · P1 · `:core:data`: Room 2.8.5 (`JobEntity`, DAO con `Flow`, migraciones exportadas), pedidos guardados como JSON versionado, `RoomJobStore`; registrado en Hilt.
  *Depende de:* T7.1
  *Acepta:* pruebas del mapeo en la JVM (ida y vuelta de cada tipo de pedido y error) y prueba instrumentada del DAO en el teléfono.
  *Hecho:* `JobEntity`/`JobDao`/`MediaForgeDatabase` (esquema en `core/data/schemas/`), `RequestJson` (DTOs propios, versión 1), `JobMapper`, `RoomJobStore`, `DataModule`. 6 pruebas en la JVM. `RoomJobStoreDeviceTest` OK en el teléfono (2/2, 2026-11-08).
- [x] **T7.4** · P1 · `:app`: `JobService` en primer plano (`dataSync`; `mediaProcessing` para conversiones en Android 15+), que arranca cuando hay trabajo y se detiene al vaciarse la cola. Notificación con progreso y botón Cancelar; aviso al terminar o fallar cada trabajo. `onTimeout` (Android 15) marca lo que corre como interrumpido. Permiso de notificaciones (Android 13+) pedido al encolar por primera vez.
  *Depende de:* T7.2, T7.3
  *Acepta:* compila y pasa lint; se valida en T7.7.
  *Hecho:* `JobsModule` (scheduler y `@ApplicationScope`), `JobServiceStarter`, `JobService`, `JobNotifications` (dos canales, progreso con Cancelar, aviso que abre el archivo). El permiso de notificaciones se pide en T7.5, al encolar. Validado en el teléfono (T7.7).
- [x] **T7.5** · P1 · Inicio y Conversor encolan en lugar de correr en el `viewModelScope` y siguen el trabajo por id (el progreso se sigue viendo en la pantalla). El conversor conserva el permiso de lectura de la URI (`takePersistableUriPermission`) y lo suelta al terminar el trabajo.
  *Depende de:* T7.2
  *Acepta:* pruebas de `HomeViewModel` y `ConverterViewModel` con un scheduler falso.
  *Hecho:* `JobQueue` (dominio) implementada por `JobScheduler`; `WorkState.of` y `JobQueue.workState(id)`; estado `Interrupted`; `InputAccess` (permiso persistente, se suelta al guardar); permiso de notificaciones al encolar. Mientras un trabajo corre se puede pegar otro enlace o elegir otro archivo y encolar más (pedido del usuario en la prueba de T7.7). Las pruebas usan el `JobScheduler` real con motores falsos y `InMemoryJobStore`.
- [x] **T7.6** · P1 · Pantalla Descargas (administrador): lista de trabajos con nombre, sitio, progreso, velocidad, tamaños, estado y fase (descarga o procesamiento), error con detalle; acciones Cancelar, Reintentar, Eliminar de la lista, Abrir, Compartir y Ver detalles.
  *Depende de:* T7.2
  *Acepta:* pruebas del ViewModel; `[~]` hasta probarlo en el teléfono.
  *Hecho:* `DownloadsViewModel` (6 pruebas) y `DownloadsScreen` (tarjeta por trabajo: sitio y formato, fase con progreso, Abrir, Compartir, Cancelar, Reintentar, Quitar, Detalles con fecha, intentos, archivo y error técnico). Validado en el teléfono (T7.7).
- [x] **T7.7** · P1 · Prueba en el teléfono: dos descargas a la vez y una conversión en cola; salir de la app y volver; cancelar desde la notificación; cerrar la app desde recientes a mitad y reabrirla (el trabajo vuelve a la cola); reintentar uno fallido.
  *Depende de:* T7.4, T7.5, T7.6
  *Acepta:* validación manual registrada.
  *Hecho:* el usuario validó en 24115RA8EG (Android 15, 2026-11-08): permiso de notificaciones, varias descargas y una conversión a la vez, salir y volver, aviso final que abre el archivo, cancelar desde la notificación, cerrar desde recientes y reabrir, reintentar y Detalles.
- [ ] **T7.8** · P3 · Pausa real de descargas: cancelar el proceso conservando el `.part` y relanzar con `--continue` (FEASIBILITY §10); también para retomar después de una interrupción. Documentar los casos en que reinicia (streams fragmentados).
  *Depende de:* T7.7
  *Acepta:* prueba en el teléfono con una descarga HTTP grande.

## Etapa 8 · Android Sharesheet (hecha)

Recibir enlaces con "Compartir" desde el navegador, apps multimedia u otras apps; el enlace se carga en Inicio y se analiza solo (main.md §8.1). Diseño en ADR-018.

- [x] **T8.1** · P1 · Dominio: `SharedText.findUrl(texto, asunto)`: busca la primera URL válida en el texto compartido (con o sin palabras alrededor, con o sin esquema), después en el asunto; corta textos enormes antes de buscar; si no hay, devuelve el motivo.
  *Depende de:* —
  *Acepta:* pruebas en la JVM (YouTube, TikTok con texto, URL sin esquema, asunto, solo texto, `file://`, localhost, vacío, texto gigante).
  *Hecho:* `domain/url/SharedText.kt` sobre `UrlValidator`. 10 pruebas.
- [x] **T8.2** · P1 · `:app`: `MainActivity` recibe `ACTION_SEND` `text/plain` (`singleTask`; `onCreate` solo la primera vez y no desde recientes, y `onNewIntent`). `ShareIntents` lee `EXTRA_TEXT`, `ClipData` y `EXTRA_SUBJECT` sin fallar con extras rotos. `ShareInbox` (singleton) guarda el último enlace hasta que Inicio lo toma; la app pasa a Inicio, carga el enlace y lo analiza. Un trabajo en curso sigue en la cola.
  *Depende de:* T8.1
  *Acepta:* pruebas de `HomeViewModel` (enlace compartido antes y después de abrir Inicio, texto sin URL, con una descarga en curso); compila y pasa lint.
  *Hecho:* `share/ShareIntents.kt`, `share/ShareInbox.kt`, `MainActivity` (`singleTask`, `onNewIntent`, contador que lleva a Inicio), filtro `SEND` en el manifest, `HomeViewModel.onShared`. 5 pruebas nuevas. Validado en el teléfono (T8.3).
- [x] **T8.3** · P1 · Prueba en el teléfono: compartir desde Chrome, desde la app de YouTube y desde otra app (por ejemplo, un mensaje o una nota con texto y enlace); con la app cerrada, abierta en otra pestaña y con una descarga en curso; un texto sin enlace muestra el motivo; girar la pantalla no vuelve a analizar.
  *Depende de:* T8.2
  *Acepta:* validación manual registrada.
  *Hecho:* el usuario validó todos los casos en 24115RA8EG (Android 15, 2026-11-08).

## Etapa 9 · Historial y configuración (en curso)

Historial persistente de descargas y conversiones, ajustes con DataStore y administración de los archivos guardados (main.md §9, §8.2, §10, §15 punto 16). Diseño en ADR-019.

- [x] **T9.1** · P1 · Dominio: `HistoryEntry` (tipo video, audio o conversión; título, sitio, enlace de origen u original, formato, archivo guardado, miniatura, fecha) armado desde un `Job` completado; contrato `HistoryStore`; búsqueda y filtro por tipo. `Settings` (modo, calidad y audio predeterminados, descargas simultáneas 1–3, solo Wi‑Fi, tema, colores del sistema, carpeta de destino) con valores fuera de rango corregidos; contrato `SettingsStore`.
  *Depende de:* —
  *Acepta:* pruebas en la JVM (armado de la entrada por tipo, filtro y búsqueda, rangos de los ajustes).
  *Hecho:* `domain/history/History.kt` (`HistoryEntry.from`, `HistorySearch`, `HistoryStore`) y `domain/settings/Settings.kt` (`Settings.normalized`, `SettingsStore`). 11 pruebas.
- [x] **T9.2** · P1 · `:core:data`: tabla `history` en Room (versión 2, migración automática desde la 1, esquema exportado) y `RoomHistoryStore`; DataStore Preferences 1.3.0 en `DataStoreSettingsStore` (un valor desconocido vuelve al predeterminado); todo en Hilt.
  *Depende de:* T9.1
  *Acepta:* pruebas en la JVM (mapeo del historial; ajustes con un archivo temporal) y prueba de la migración 1 → 2 en el teléfono.
  *Hecho:* `HistoryEntity`/`HistoryDao`, base versión 2 con `AutoMigration(1, 2)` (esquema `2.json`), `RoomHistoryStore`, `DataStoreSettingsStore` (archivo `files/datastore/settings.preferences_pb`, se reemplaza si está dañado), Hilt. 6 pruebas en la JVM. `MigrationDeviceTest` escrita. La migración real se validó al actualizar la app en el teléfono: los trabajos de la versión 1 siguieron en Descargas (2026-11-08).
- [x] **T9.3** · P1 · La cola anota en el historial cada trabajo completado y toma de los ajustes el límite de descargas simultáneas (bajar el límite no corta lo que ya corre).
  *Depende de:* T9.2
  *Acepta:* pruebas de `JobScheduler` (entrada al completar, nada al fallar o cancelar; cambio de límite en vivo).
  *Hecho:* `JobScheduler(history = …)` anota al completar; `setDownloadLimit`; `QueueSettings` (en `MediaForgeApplication`) lleva el ajuste a la cola. 2 pruebas.
- [x] **T9.4** · P1 · Inicio arranca con el modo, la calidad (la mayor que no supere la elegida) y el formato y bitrate de audio de los ajustes.
  *Depende de:* T9.2
  *Acepta:* pruebas de `HomeViewModel`.
  *Hecho:* `VideoOptions.preferredIndex` y `AudioOptions.preferredIndex` (3 pruebas); `HomeViewModel` lee los ajustes al terminar el análisis (3 pruebas). Cambiar de modo o calidad a mano sigue igual.
- [x] **T9.5** · P1 · Pantalla Historial: lista del más nuevo al más viejo, búsqueda, filtro (todo, video, audio, conversiones), Abrir, Compartir, Descargar de nuevo (carga el enlace en Inicio), Quitar del historial, Borrar archivo (con confirmación y, si Android lo pide, el permiso del sistema); marca los archivos que ya no existen. En Descargas, "Quitar terminados".
  *Depende de:* T9.3
  *Acepta:* pruebas del ViewModel; `[~]` hasta probarlo en el teléfono.
  *Hecho:* `files/SavedFiles` (existe, borrar con MediaStore o documento; `createDeleteRequest` / `RecoverableSecurityException`), `HistoryViewModel` (10 pruebas) y `HistoryScreen` (búsqueda, filtros, tarjeta con miniatura, diálogo de borrado, revisa archivos al volver). "Descargar de nuevo" usa `ShareInbox`. En Descargas quedó "Quitar completados" (los fallidos y cancelados se quedan para reintentar; 1 prueba).
- [x] **T9.6** · P1 · Pantalla Ajustes: predeterminados de descarga, descargas simultáneas, tema y colores del sistema, motor (versión instalada, buscar e instalar actualización verificada), acerca de (versión, licencia GPL-3.0, código fuente, sin rastreo).
  *Depende de:* T9.2
  *Acepta:* pruebas del ViewModel; `[~]` hasta probarlo en el teléfono.
  *Hecho:* `SettingsViewModel` (7 pruebas) y `SettingsScreen`; `MainActivity` aplica tema y colores; El idioma sigue al del teléfono (inglés por defecto, español si el teléfono está en español): el ajuste de idioma por app del sistema no guardaba la elección en el teléfono de prueba y se quitó el botón. "Solo Wi‑Fi" y la carpeta de destino no se muestran hasta T9.7 y T9.8. Se borró `NotImplementedScreen`: ya no queda ninguna sección sin hacer.
- [~] **T9.7** · P2 · Política de conexión "Solo Wi‑Fi": con datos móviles las descargas nuevas esperan ("Esperando Wi‑Fi") y arrancan solas al volver el Wi‑Fi; las que ya corren siguen; las conversiones no dependen de la red.
  *Depende de:* T9.3
  *Acepta:* pruebas de `JobScheduler` con una red falsa; `[~]` hasta probarlo en el teléfono.
  *Hecho:* `JobScheduler.setDownloadsAllowed` y `downloadsAllowed` (en `JobQueue`), `SchedulerActivity.waitingForNetwork`; `UnmeteredNetwork` (red por defecto con internet y sin cobro por datos, solo se escucha con el ajuste activo); `QueueSettings`; notificación "Esperando Wi‑Fi" (el servicio sigue vivo mientras espera); estado en Descargas, aviso en Inicio, interruptor en Ajustes. Permiso `ACCESS_NETWORK_STATE`. 4 pruebas.
- [~] **T9.8** · P2 · Carpeta de destino elegida con el selector de carpetas del sistema (permiso persistente); si se pierde el permiso, vuelve a la galería y avisa. La galería sigue siendo el predeterminado.
  *Depende de:* T9.6
  *Acepta:* prueba en el teléfono (guardar en una carpeta elegida y abrir el archivo).
  *Hecho:* `FolderLibrary` (dominio) y `DocumentTreeLibrary` (`:core:media`, crea el documento con nombre único y lee el que dejó el proveedor); `RoutingLibrary` elige carpeta o galería (`@GalleryLibrary`), y si la carpeta no sirve vuelve a la galería, borra el ajuste, suelta el permiso y avisa con una notificación; `DestinationFolders` (permiso persistente); sección "Guardar en" en Ajustes. 6 pruebas.
- [~] **T9.9** · P2 · Archivos temporales: tamaño de las carpetas privadas de trabajos y botón para limpiar las que no pertenecen a un trabajo activo.
  *Depende de:* T9.6
  *Acepta:* pruebas de la regla de qué se puede borrar.
  *Hecho:* `TempFolders.removable` (dominio, 3 pruebas); `JobScheduler.withJobsFrozen` (borra sin que arranque un trabajo a mitad, 1 prueba); `JobFoldersTempStorage` (`noBackupFilesDir/mediaforge/jobs`); sección "Archivos temporales" en Ajustes con tamaño, Limpiar y lo liberado (1 prueba). La caché de yt-dlp y el `TMPDIR` quedan para el sistema.
- [x] **T9.10** · P1 · Prueba en el teléfono: el historial sobrevive a cerrar la app y a actualizarla (migración), búsqueda y filtros, borrar un archivo, descargar de nuevo; los ajustes se aplican y se conservan.
  *Depende de:* T9.5, T9.6
  *Acepta:* validación manual registrada.
  *Hecho:* el usuario validó en 24115RA8EG (2026-11-08): migración con los trabajos anteriores, predeterminados, límite de descargas, tema, motor, historial (búsqueda, filtros, descargar de nuevo, borrar archivo), quitar completados, todo se conserva al reabrir. El ajuste de idioma por app no funcionaba en el teléfono y se quitó (ver ADR-019).

## Etapas 10–12

Ver main.md §14. Se detallan al llegar.
