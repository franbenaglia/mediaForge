# Revisión de seguridad

Revisión de la Etapa 11 (T11.2), punto por punto de main.md §11. Cada fila dice qué se hace y dónde está. "Aceptado" marca un riesgo conocido que no se puede o no conviene cerrar del lado de la app.

Última revisión: 2026-11-08.

## Modelo de amenazas

La app no tiene servidor ni cuentas. Lo que llega de afuera y no es de confianza:

- **Texto del usuario o de otra app:** la URL pegada o compartida (`ACTION_SEND`).
- **Respuestas de los sitios:** lo que imprime yt-dlp (títulos, descripciones, IDs de formato, URLs de miniaturas, elementos de listas) y los archivos que baja.
- **Archivos locales elegidos en el Conversor:** su contenido y su nombre.
- **La actualización de yt-dlp** que se baja de GitHub.

Lo que se protege: que nada de eso ejecute código, escriba fuera de las carpetas de la app, filtre datos del usuario o deje sin memoria o sin disco al teléfono.

## URLs (main.md §11.1)

| Punto | Cómo | Dónde |
|---|---|---|
| Validar el esquema | Solo `http` y `https`. Se rechazan `file:`, `content:`, `javascript:`, `data:` y cualquier otro. | `UrlValidator.validate`, `UrlValidatorTest` |
| Rechazar destinos no autorizados | Sin credenciales en la URL (`usuario:clave@`), sin `localhost`, `.local` ni direcciones privadas o reservadas (IPv4 e IPv6). Largo máximo de 2048. Host normalizado (minúsculas, IDN a ASCII). | `UrlValidator` |
| Sin concatenación de comandos | Cada comando es una lista de argumentos. `ProcessRunner` usa `sh -c 'echo $$; exec "$@"'` solo para conocer el PID: los argumentos llegan como parámetros posicionales y el shell no los interpreta. | `ProcessRunner`, `YtDlpCommand`, `FfmpegCommand` |
| Una URL no puede ser una opción | Va siempre al final, después de `--`, y además `require(!url.startsWith("-"))`. Los IDs de formato del sitio se validan (`[A-Za-z0-9][A-Za-z0-9_.-]{0,63}`), igual que el idioma de subtítulos (`--sub-langs` es una expresión regular, ADR-021). | `YtDlpCommand`, `YtDlpCommandTest` |
| URLs que vienen del sitio | Los elementos de una lista y las miniaturas pasan otra vez por `UrlValidator` (o por un chequeo de `http(s)`) antes de usarse. | `YtDlpInfoParser` |
| Redirecciones | yt-dlp sigue las redirecciones HTTP él mismo: la app solo valida la URL inicial. yt-dlp no abre `file://` sin `--enable-file-urls`, que no se usa. **Aceptado:** un sitio podría redirigir a una dirección de la red local; el riesgo es bajo porque la app no manda credenciales ni cookies. | — |
| Respuestas malformadas | El JSON se lee con campos opcionales; un valor raro en subtítulos o en elementos de una lista se ignora sin romper el análisis. La salida del análisis tiene un tope de 32 MB y de stderr se guardan solo las últimas 200 líneas. | `YtDlpInfoParser`, `YtDlpExtractor`, sus pruebas |
| Límites de tiempo | Análisis: 120 s (`YtDlpExtractor.DEFAULT_TIMEOUT`). Descargas: sin tope total (pueden durar horas); los cortes de red los detecta yt-dlp. Actualización: 15 s de conexión y 30 s de lectura. | `YtDlpExtractor`, `UrlConnectionFetcher` |

## Archivos (main.md §11.2)

| Punto | Cómo | Dónde |
|---|---|---|
| Sanitizar nombres | Todo nombre que se guarda pasa por `FileNames.sanitize`: sin separadores, `..`, caracteres de control ni nombres reservados, y como mucho 255 bytes. | `FileNames`, `FileNamesTest` |
| Path traversal | yt-dlp escribe en una carpeta propia del trabajo con un nombre fijo (`media.%(ext)s`); el título del sitio nunca forma parte de una ruta. El ID del trabajo se valida (`[A-Za-z0-9_-]{1,64}`). | `YtDlpDownloader`, `FfmpegConverter` |
| Archivos incompletos | Se trabaja en `noBackupFilesDir/mediaforge/jobs/<id>`. Si falla o se cancela, la carpeta se borra. En MediaStore se usa `IS_PENDING` hasta terminar de copiar. | `YtDlpDownloader`, `MediaStoreLibrary` |
| Sin sobrescrituras | `FileNames.unique` agrega `(1)`, `(2)`… si el nombre ya existe, en MediaStore y en la carpeta elegida. | `MediaStoreLibrary`, `DocumentTreeLibrary` |
| Espacio disponible | Antes de descargar: 2 × tamaño estimado + 100 MB. Antes de convertir: según el plan. | `YtDlpDownloader`, `FfmpegConverter` |
| Temporales | Se borran al terminar cada trabajo. Lo que quede de un proceso muerto se limpia desde Ajustes, sin tocar trabajos que corren. | `JobFoldersTempStorage`, `JobScheduler.withJobsFrozen` |
| Falta de almacenamiento | `MediaError.InsufficientStorage` con lo necesario y lo disponible. | `MediaError` |

## Procesamiento (main.md §11.3)

| Punto | Cómo | Dónde |
|---|---|---|
| Sin bloquear el hilo principal | Procesos, disco y red en `Dispatchers.IO` (`@IoDispatcher`). T11.6 agrega `StrictMode` en debug para comprobarlo. | Adaptadores de `:core:media` |
| Concurrencia | `JobScheduler` limita las descargas simultáneas (1 a 3, en Ajustes) y las conversiones (una a la vez por defecto). | `JobScheduler` |
| Recursos nativos | Al cancelar se mata el árbol de procesos completo (yt-dlp y su ffmpeg), no solo el padre. | `ProcessRunner.killTree`, `EngineDeviceTest` |
| Cancelaciones | Cancelar la corrutina termina los procesos y borra los parciales (ADR-005). | `YtDlpDownloader`, `FfmpegConverter` |
| Memoria | Tope a la salida del análisis (32 MB). Las líneas de progreso se procesan de a una. stderr guarda las últimas 50 líneas al descargar y 200 al analizar. | `YtDlpExtractor`, `YtDlpDownloader` |
| Procesos interrumpidos | Al arrancar, los trabajos que quedaron corriendo pasan a "Interrumpido" y se pueden reintentar. `onTimeout` (Android 15+) corta lo que corre. | `JobScheduler.start`, `JobService` |

## Código descargado

| Punto | Cómo | Dónde |
|---|---|---|
| Binarios | Python, FFmpeg y QuickJS vienen solo en el APK (`jniLibs`). Android no deja ejecutar binarios descargados (targetSdk ≥ 29). | ADR-004 |
| Actualización de yt-dlp | `SHA2-256SUMS` con firma OpenPGP verificada contra la clave fijada de yt-dlp (huella en el código); SHA-256 del zipapp; `--version` tiene que responder la versión esperada; recién entonces se activa y se conserva la anterior. Solo HTTPS (también después de una redirección), con tiempos de conexión y lectura y un tope de tamaño por respuesta. | `YtDlpUpdater`, `OpenPgp`, `UrlConnectionFetcher`, sus pruebas |

## Android

| Punto | Cómo | Dónde |
|---|---|---|
| Componentes exportados | Solo `MainActivity` (lanzador y `ACTION_SEND` de `text/plain`). `JobService` no está exportado. | `AndroidManifest.xml` |
| Texto compartido | Se lee como texto, se busca una URL y se valida igual que lo pegado. Nunca se abre un archivo ni una URI compartida. | `ShareIntents`, `SharedText`, ADR-018 |
| PendingIntents | Todos con `FLAG_IMMUTABLE`. | `JobNotifications` |
| Permisos | Internet, red, notificaciones, servicio en primer plano (`dataSync`, `mediaProcessing`) y `WRITE_EXTERNAL_STORAGE` solo hasta Android 9. Sin acceso a todos los archivos: los archivos de entrada se eligen con el selector del sistema. | Manifiestos |
| Copias de seguridad | `allowBackup=false` y reglas de extracción que excluyen todo (nube y traspaso entre dispositivos): el historial no sale del teléfono. | `AndroidManifest.xml`, `data_extraction_rules.xml` |

## Logs y datos

- Solo hay tres llamadas a `Log`, sin URLs ni nombres de archivo: tipo de servicio rechazado y servicio no permitido (`JobService`, `JobServiceStarter`).
- Los detalles técnicos de los errores pasan por `YtDlpErrorClassifier.redact`, que quita la query y el fragmento de las URLs (ahí van los tokens), y se recortan.
- La app no usa cookies ni credenciales: si un sitio pide iniciar sesión, el error lo dice y no hay forma de dárselas (main.md §1.2).
- Sin analítica, sin servicios de Google Play y sin red fuera de los sitios que pide el usuario y de GitHub para actualizar yt-dlp.

## Cambios de esta revisión

- Tope de 32 MB a la salida del análisis y últimas 200 líneas de stderr: antes se guardaba todo en memoria (`YtDlpExtractorTest.oversizedOutputIsRejectedInsteadOfFillingMemory`).
