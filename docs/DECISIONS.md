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

---

## ADR-017 · Gestor de trabajos: cola en el dominio, Room y servicio en primer plano sin WorkManager

**Fecha:** 2026-11-08 · **Estado:** aceptada

**Contexto:** hasta la Etapa 6 cada pantalla corre su trabajo en el `viewModelScope`: cerrar la app lo cancela. main.md §8.3 y §9 piden cola, persistencia, reintentos, notificaciones y recuperación. En Android 12+ un servicio en primer plano solo puede arrancar con la app visible (o por excepciones que no aplican), y en Android 15 un receptor de `BOOT_COMPLETED` no puede arrancar los tipos `dataSync` ni `mediaProcessing`.

**Decisión:**
- `JobScheduler` vive en `:core:domain`: Kotlin puro sobre `MediaDownloader`, `MediaConverter`, `MediaLibrary` y un `JobStore`. Se prueba en la JVM con tiempo virtual. Es un singleton con su propio `CoroutineScope`; cancelar un trabajo sigue siendo cancelar su corrutina (ADR-005).
- Room (2.8.5) implementa `JobStore` en `:core:data` (ADR-006). Los pedidos se guardan como JSON con versión. En Room van solo los cambios de estado; el progreso queda en memoria (`JobScheduler.live`): un trabajo recuperado empieza de cero, así que guardarlo no serviría.
- `JobService` (en `:app`) solo mantiene vivo el proceso y muestra la notificación. Lo arranca el scheduler cuando entra trabajo, que siempre es por una acción del usuario con la app visible, y se detiene cuando la cola queda vacía. Tipo `dataSync`, más `mediaProcessing` en Android 15+ mientras hay una conversión.
- Concurrencia: 2 descargas y 1 conversión a la vez (constantes hasta los ajustes de la Etapa 9).
- Reintento automático solo para errores de red (2 veces, a los 5 s y 15 s). El resto se reintenta a mano.
- Recuperación: al iniciar la app, lo que figuraba "en curso" o "guardando" vuelve a la cola (hasta 3 intentos) y se corre desde cero; si no, queda "interrumpido". `onTimeout` de Android 15 marca lo que corre como interrumpido.
- El conversor toma permiso persistente sobre la URI elegida, para poder correr o reintentar el trabajo más tarde.

**Descartadas:**
- WorkManager para reanudar después de que muere el proceso o se reinicia el teléfono (FEASIBILITY §10): no puede levantar el servicio en primer plano desde segundo plano y agregaría una dependencia para algo que no funcionaría.
- Cola dentro del servicio: atarla al ciclo de vida del servicio complica las pruebas y la consulta del estado desde la UI.
- Retomar la descarga desde el `.part` al recuperar: el descargador hoy empieza con la carpeta vacía. Queda para la pausa real (T7.8).

---

## ADR-018 · Compartir enlaces: solo ACTION_SEND, actividad única y análisis automático

**Fecha:** 2026-11-08 · **Estado:** aceptada

**Contexto:** main.md §8.1 pide aceptar URLs compartidas desde navegadores y otras apps y cargarlas en el analizador. Las apps comparten texto libre ("Mirá esto https://…"), a veces con el enlace en el asunto. El texto viene de otra app: no es confiable.

**Decisión:**
- `MainActivity` declara `ACTION_SEND` con `text/plain`. Nada de `ACTION_VIEW` para http/https: la app aparecería para abrir cualquier enlace web, y sin verificación de dominio Android 12+ igual los abre en el navegador.
- `launchMode="singleTask"`: compartir trae la ventana de MediaForge que ya existe (`onNewIntent`) en lugar de abrir otra copia dentro de la tarea de la app que comparte.
- La URL se busca con `SharedText.findUrl` (dominio, sobre `UrlValidator`): primero en el texto, después en el asunto. El texto se corta a 16 KB antes de buscar. Nunca se registra en logs.
- El enlace pasa por `ShareInbox` (singleton en memoria) hasta que `HomeViewModel` lo toma: así llega aunque Inicio no exista todavía. La app pasa a la pestaña Inicio y analiza sola; si no hay URL válida, muestra el motivo como al pegar.
- Un intent se procesa una sola vez: en `onCreate` sin estado guardado y sin venir de recientes, y en cada `onNewIntent`. Girar la pantalla no repite el análisis.

**Descartadas:**
- Una actividad aparte, transparente, para recibir: agrega una pantalla sin necesidad; la app ya tiene una sola actividad.
- Encolar la descarga directamente al compartir: el usuario tiene que elegir calidad y formato (main.md §7.2).

---

## ADR-019 · Historial en su propia tabla y ajustes en DataStore

**Fecha:** 2026-11-08 · **Estado:** aceptada

**Contexto:** main.md §9 pide historial de descargas y conversiones y preferencias (formato y calidad predeterminados, descargas simultáneas, interfaz, conexión, almacenamiento). La lista de Descargas ya guarda los trabajos en Room, pero el usuario puede quitarlos y ahí también están los fallidos y cancelados.

**Decisión:**
- El historial es una tabla aparte (`history`, base de datos versión 2, migración automática). La cola anota una entrada cuando un trabajo se completa, con lo necesario para mostrarlo sin el trabajo: tipo, título, sitio, enlace u original, formato y archivo guardado. Quitar un trabajo de Descargas no toca el historial; quitar una entrada del historial no borra el archivo, salvo que se elija "Borrar archivo".
- Borrar un archivo pasa por MediaStore. Si la app ya no es dueña del archivo (por ejemplo, después de reinstalar), Android 11+ pide confirmación con `createDeleteRequest` y Android 10 con `RecoverableSecurityException`.
- Los ajustes viven en DataStore Preferences 1.3.0 dentro de `:core:data`, detrás de `SettingsStore` (dominio). Un valor desconocido o fuera de rango vuelve al predeterminado.
- El límite de descargas simultáneas (1 a 3) se aplica en vivo: bajarlo no corta lo que ya corre, solo frena lo que espera.
- "Solo Wi‑Fi" frena las descargas nuevas con red medida; las que corren siguen, porque cortarlas perdería lo bajado (no hay pausa real, T7.8).
- El idioma sigue al del teléfono: español si está en español, inglés en cualquier otro caso (`values/` es inglés). Sin selector propio ni `localeConfig`: en el teléfono de prueba (HyperOS, Android 15) el ajuste de idioma por app del sistema mostraba los idiomas pero no guardaba la elección (2026-11-08).

**Descartadas:**
- Historial como filtro de la tabla `jobs`: quitar un trabajo de la lista borraría su historial, y la lista de trabajos se llenaría de entradas viejas.
- Proto DataStore: los ajustes son pocos valores sueltos; Preferences alcanza y no necesita esquema.


---

## ADR-020 · Conversor avanzado: recorte, fotogramas, varios archivos y compresión

**Fecha:** 2026-11-08 · **Estado:** aceptada

**Contexto:** main.md §5.2 pide extraer fragmentos de audio, recortar video, extraer fotogramas, convertir varios archivos y comprimir video. Sin recodificar, FFmpeg solo puede cortar el video en un fotograma clave (en un video de YouTube suelen estar cada 2 a 5 s). Recodificar da el corte exacto pero tarda mucho más en el teléfono.

**Decisión:**
- El recorte va en el pedido (`ConversionRequest.clip`: inicio, fin opcional y "corte exacto"). El comando usa `-ss` antes de `-i` (busca sin leer todo el archivo) y `-t` con la duración del fragmento.
- Sin "corte exacto", el video se copia si se puede y el corte cae en el fotograma clave anterior al inicio: el fragmento puede empezar unos segundos antes. La pantalla lo explica; no se finge un corte exacto. Con "corte exacto", el video siempre se recodifica. El audio se corta bien aunque se copie.
- Los tiempos se escriben como `83`, `1:23`, `1:02:03` o con décimas (`1:23.5`). El fin no puede pasar la duración conocida y el fragmento dura al menos 0,5 s.
- Un fotograma es un destino más (`ConversionTarget.Image`: instante y JPG o PNG), con la resolución original. Se guarda en `Pictures/MediaForge`, o en la carpeta elegida.
- Varios archivos: un trabajo por archivo, todos con el mismo destino, hasta 50. Los incompatibles se informan y no se encolan. El recorte y el fotograma no se ofrecen para varios archivos, porque los instantes dependen de cada uno.
- Compresión: niveles de calidad que fuerzan la recodificación con otro CRF (H.264: 23, 26, 30; VP9: 32, 36, 40). "Original" copia cuando se puede.
- Los campos nuevos del pedido son opcionales en el JSON guardado: los trabajos guardados antes se siguen leyendo con la misma versión.

**Descartadas:**
- Cortar sin recodificar solo el tramo inicial (recodificar hasta el siguiente fotograma clave y copiar el resto): es frágil con FFmpeg desde la línea de comandos (parámetros del codificador que tienen que coincidir) y no vale el riesgo.
- Varios fotogramas por trabajo (uno cada N segundos): un trabajo produce un archivo (ADR-017); se puede agregar más adelante con un ZIP o una carpeta.


---

## ADR-021 · Subtítulos incrustados y listas de reproducción

**Fecha:** 2026-11-08 · **Estado:** aceptada

**Contexto:** main.md §5.1 pide descargar subtítulos cuando el sitio los ofrece. YouTube ofrece subtítulos manuales y automáticos (reconocimiento de voz), y además traducciones automáticas a ~150 idiomas. Sin `-i`, yt-dlp corta toda la descarga si un subtítulo falla, y con YouTube eso pasa seguido (HTTP 429).

**Decisión:**
- El análisis lista los subtítulos manuales (ordenados por nombre, sin `live_chat`) y un solo automático: el del idioma original (`xx-orig`, o el del campo `language`), y solo si no hay uno manual de ese idioma. No se listan las traducciones automáticas. Máximo 60.
- Se elige uno, solo para video (`DownloadSelection.Video.subtitle`). Va incrustado como pista de texto (`--write-subs` o `--write-auto-subs`, `--sub-langs <idioma>`, `--sub-format vtt/srt/best`, `--embed-subs`), no como archivo aparte: un trabajo produce un archivo (ADR-017).
- El idioma es la clave de yt-dlp y se valida (`[A-Za-z0-9][A-Za-z0-9_-]{0,31}`): `--sub-langs` es una expresión regular y no puede recibir ni `-all` ni comodines.
- MOV no admite subtítulos de texto: el pedido lo rechaza (Inicio siempre baja MP4).
- Si yt-dlp falla con "Unable to download video subtitles", el video se baja de nuevo sin subtítulos y el trabajo termina con `JobNote.SUBTITLES_SKIPPED`; el aviso de resultado lo dice. Cualquier otro error falla como siempre. La nota vive en memoria (no se guarda en Room).

**Listas de reproducción (T10.7):**
- El análisis usa `--dump-single-json --flat-playlist --playlist-items 1-200` siempre: un video sale igual que con `--dump-json`; una lista sale con sus elementos sin analizar cada video (unos segundos en vez de minutos). `--no-playlist` sigue: un enlace de video dentro de una lista (`watch?v=…&list=…`) trae solo el video.
- Se leen hasta 200 elementos. No se ofrecen los privados, borrados (`[Private video]`, `[Deleted video]`), los que yt-dlp marca como de suscriptores, pago o con cuenta (main.md §1.2), las listas anidadas (pestañas de canal) ni los que no traen una URL `http(s)` válida. La pantalla dice cuántos quedaron afuera y si la lista tenía más de 200.
- Un solo modo y calidad para todos. Como no hay formatos por video, las calidades son por altura ("La mejor", 1080, 720, 480, 360: `bv*[height<=h]+ba/b[height<=h]`) y sin tamaño; en audio, el formato elegido con bitrate automático o elegido. Sin subtítulos ni nombre editable: cada archivo lleva el título de su video.
- Un trabajo por elemento elegido (ADR-017), encolados de una vez; no se siguen en Inicio: se ven en Descargas. Cada video se analiza recién al bajarlo, así que un elemento que falla no frena a los otros.

**Descartadas:**
- `-i` (`--ignore-errors`): también ocultaría errores del video.
- Varios idiomas a la vez o archivo `.srt` aparte: más opciones en la pantalla para un caso poco común; se puede agregar después.
- Analizar cada video de la lista para mostrar calidades y tamaños reales: con 200 elementos tarda minutos y hace cientos de pedidos a YouTube.
- Un solo trabajo para toda la lista: un fallo la cortaría entera y no se podría cancelar un video suelto.
