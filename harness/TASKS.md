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

## Etapa 3 · Motor de extracción

- [x] **T3.1** · P1 · `UrlValidator` en el dominio: solo `http`/`https`, host obligatorio, largo máximo, rechazo de URLs que empiecen con `-`, extracción de URL desde texto compartido.
  *Depende de:* —
  *Acepta:* pruebas unitarias (esquemas inválidos, `javascript:`, `file:`, espacios, texto con URL embebida).
- [x] **T3.2** · P1 · Módulo `:core:media` con youtubedl-android 0.18.1, splits por ABI e inicialización en segundo plano con verificación de espacio.
  *Depende de:* —
  *Acepta:* `verify.sh` OK; tamaño del APK por ABI registrado en STATE.md.
- [~] **T3.3** · P1 · Parser propio de `--dump-json` → `MediaInfo` (kotlinx.serialization), tolerante a campos faltantes.
  *Hecho en el teléfono (2026-11-07):* fixture real de YouTube (`youtube-real.json`) con prueba.
  *Falta:* fixture real de Dailymotion: con el yt-dlp del APK devuelve 403; se captura después de T3.7.
  *Depende de:* T3.2
  *Acepta:* pruebas con fixtures JSON reales de YouTube y Dailymotion (guardados en `core/media/src/test/resources`).
- [x] **T3.4** · P1 · Clasificador de errores: stderr de yt-dlp → `MediaError`.
  *Depende de:* T3.2
  *Acepta:* pruebas con los mensajes reales de la Etapa 0 (`HTTP Error 403`, "only works when logged-in", `Unsupported URL`, errores de red).
- [~] **T3.5** · P1 · `ProcessRunner`: lanza un binario con argumentos en lista, lee stdout/stderr en streaming y, al cancelar la corrutina, mata el árbol de procesos por PID.
  *Depende de:* T3.2
  *Acepta:* pruebas en la JVM con `sleep`/`sh` (cancelación, timeout, código de salida, hijos muertos).
  *Falta:* confirmar en el teléfono que la cancelación mata un ffmpeg hijo real (`EngineDeviceTest.t4`). En el primer intento no arrancó ffmpeg porque Dailymotion falló: revalidar después de T3.7. `/system/bin/sh` y el wrapper sí funcionan (todas las ejecuciones del teléfono pasan por ProcessRunner).
- [~] **T3.6** · P1 · `YtDlpExtractor` implementa `MediaExtractor`, con `--cache-dir` propio, `--no-playlist` y `--` antes de la URL.
  *Depende de:* T3.3, T3.4, T3.5
  *Acepta:* prueba unitaria de los argumentos construidos; `[~]` hasta probar YouTube y Dailymotion en el teléfono.
  *Hecho:* comando y extractor probados en la JVM con un yt-dlp falso (éxito, error clasificado, timeout, URL inválida).
  *Teléfono (2026-11-07):* YouTube OK (5,4 s, 35 formatos). Dailymotion falla con 403 por el yt-dlp viejo (clasificado bien como `EngineOutdated`): revalidar después de T3.7.
- [x] **T3.8** · P1 · Inicio muestra el resultado del análisis: cargando (incluida la primera inicialización del motor), `MediaCard` con datos y cantidad de formatos, o `ErrorState`.
  *Depende de:* T3.6, T2.2
  *Acepta:* pruebas de `HomeViewModel` con un `MediaExtractor` falso (cargando → éxito / error / cancelar al cambiar el enlace); `[~]` hasta probarlo en el teléfono.
  *Validado por el usuario en el teléfono (2026-11-07):* pegar, analizar YouTube (tarjeta con 35 formatos) y error de Dailymotion.
- [~] **T3.7** · P1 · Actualizador de yt-dlp (`ExtractorEngine`): descarga, verificación de firma y SHA-256, prueba de `--version`, activación y conservación de la versión anterior.
  *Depende de:* T3.5
  *Acepta:* pruebas con un release falso (hash incorrecto → rechazo; firma inválida → rechazo; versión rota → rollback).
  *Hecho:* verificación OpenPGP propia (sin Bouncy Castle) probada con la firma real de 2026.10.26 y con una clave de prueba; respeta `_update_spec`; 22 pruebas. Falta: correr la actualización real en el teléfono (`EngineDeviceTest.t1b`).
- [~] **T3.9** · P1 · Inicio ofrece "Actualizar motor" cuando el error es `EngineOutdated`, y reintenta el análisis al terminar.
  *Depende de:* T3.7
  *Acepta:* pruebas de `HomeViewModel` (actualiza → reintenta; falla la actualización → error explicado); `[~]` hasta el teléfono.

## Etapa 4 · Descarga de video (resumen)

Selección de formato desde los formatos reales, opción "mejor calidad", `YtDlpDownloader` con parseo de progreso, merge sin recodificar, guardado en MediaStore, cancelación y parciales. Criterio del MVP: punto 6 de main.md §15.

## Etapa 5 · Extracción de audio (resumen)

MP3 / M4A / Opus primero; bitrate solo para formatos con pérdida; metadatos y portada; nombre editable; aviso de que subir el bitrate no mejora la calidad.

## Etapa 6 · Conversor (resumen)

`FfmpegConverter` (sin yt-dlp), selector de archivo por SAF, remux cuando el códec es compatible y transcodificación en el resto, progreso con `-progress`, MP4 → MP3 como criterio del MVP.

## Etapa 7 · Gestor de trabajos (resumen)

Room para trabajos, foreground service (`dataSync`/`mediaProcessing`), límite de concurrencia, reintentos, recuperación después de que muere el proceso, `onTimeout` de Android 15.

## Etapas 8–12

Ver main.md §14. Se detallan al llegar.
