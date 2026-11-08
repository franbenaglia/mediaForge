# MediaForge — Factibilidad técnica (Etapa 0)

**Fecha:** 2026-11-06
**Estado:** Etapa 0 completada en lo que se puede validar sin dispositivo físico. Las decisiones resultantes están en [DECISIONS.md](DECISIONS.md). Quedan pendientes de validación en un teléfono real los puntos de la sección 12.

---

## 1. Veredicto

**El proyecto es viable** con este stack:

| Componente | Elección | Motivo principal |
|---|---|---|
| yt-dlp + Python | Binarios nativos de **youtubedl-android 0.18.1** (`io.github.junkfood02.youtubedl-android`), detrás de un adaptador propio | Funciona hoy en Android 16 (probado), trae Python 3.12, QuickJS y 16 KB pages. Lo usan Seal y YTDLnis. |
| JS runtime (YouTube) | **QuickJS 2025-04-26**, incluido en la librería | Es la versión mínima que yt-dlp recomienda por rendimiento. |
| FFmpeg | **El mismo FFmpeg 7.1.1 ejecutable** que trae youtubedl-android, también para el conversor offline | Incluye todos los codificadores necesarios (sección 6). Agregar otro FFmpeg duplicaría ~35 MB. |
| Licencia de la app | **GPL-3.0** (obligatorio) | youtubedl-android es GPL-3.0 y el FFmpeg incluido está compilado con `--enable-gpl --enable-version3`. |
| minSdk / target | **minSdk 26**, target/compile **36** | Los binarios están compilados para API 24. 26 simplifica las APIs de archivos y notificaciones. |
| ABIs | **arm64-v8a** (principal) y **x86_64**. armeabi-v7a, opcional más adelante | Un APK por ABI. No se publica APK universal (pesaría ~200 MB). |

Condición crítica: **la versión de yt-dlp incluida en el APK queda vieja enseguida.** La app necesita un actualizador propio y seguro desde la primera versión (sección 8). Sin actualizar, la descarga desde YouTube falló con HTTP 403 en la prueba.

---

## 2. Entorno de la prueba técnica

- Proyecto: [spikes/engine-spike/](../spikes/engine-spike/). Es un test instrumentado y no es código de producción.
- Resultados crudos: [spikes/engine-spike/results-2026-11-06.log](../spikes/engine-spike/results-2026-11-06.log).
- Dispositivo: emulador Android 16 (API 36), x86_64. **El emulador cuelga la máquina de desarrollo; no se volverá a usar.** Las validaciones siguientes se hacen en un teléfono físico, con permiso previo.
- Build: AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21, JDK 17.
- Contenido de prueba: *Big Buck Bunny* (Blender Foundation, CC BY 3.0).

---

## 3. Integración de yt-dlp: alternativas evaluadas

| Opción | Estado (nov 2026) | Ventajas | Desventajas | Decisión |
|---|---|---|---|---|
| **A. youtubedl-android 0.18.1** (junkfood02 en Maven Central) | Último release: 2025-11-16 (pre-release). Sin commits desde entonces. GPL-3.0. | Funciona (probado). Incluye Python 3.12.11, FFmpeg 7.1.1, QuickJS, aria2c y soporte de 16 KB. yt-dlp se puede actualizar sin reinstalar. | Poco mantenimiento. API con singletons y defectos (sección 9). Sin `curl_cffi` (no permite *impersonation*). | **Elegida**, aislada detrás de `MediaExtractor` |
| B. Chaquopy 17.2 + `yt-dlp[default]` vía pip | Activo (Gradle plugin 17.2.0, oct 2026). MIT. Python 3.10–3.14. | Python moderno y mantenido. Dependencias por pip. Ejecución en proceso. | Igual necesita FFmpeg y QuickJS como ejecutables en jniLibs. Cancelar código Python en proceso es difícil (no se puede matar un hilo). Más integración propia. | **Plan B** si A se rompe |
| C. Paquetes de YTDLnis (`io.github.deniscerri:ytdlnis_packages.*`: Python 3.14.6, Deno 2.7.7) | Activos (sep 2026). GPL-3.0. | Python 3.14 con `curl_cffi` y Deno. | Pensados para YTDLnis (APKs auxiliares, layout propio). Dependería de las decisiones de otra app. | Referencia, no dependencia |

**Por qué A con adaptador:** es lo único probado de punta a punta hoy. Si deja de funcionar, el reemplazo (B o C) cambia solo la implementación del adaptador, no el dominio ni la UI.

**Seal** (la otra app de referencia) no publica releases desde noviembre de 2024. **YTDLnis** está activa (v1.9.1.1, oct 2026) y es la mejor referencia de cómo se resuelven estos problemas.

---

## 4. YouTube: runtime de JavaScript, SABR y PO tokens

- Desde yt-dlp 2025.11.12, YouTube necesita un runtime de JS externo para resolver sus desafíos. La librería pasa `--js-runtimes quickjs:<ruta>` automáticamente.
- **Medido:** la extracción de info de YouTube funcionó con QuickJS (61 formatos, de 144p a 2160p, códecs avc1/vp9/av01, audio mp4a/opus).
- YouTube fuerza "SABR streaming" en algunos clientes y saltea formatos. Algunos formatos necesitan **PO tokens** (YTDLnis los genera con un WebView). **Para el MVP no se implementan PO tokens.** Si los formatos básicos dejan de funcionar sin ellos, se reevalúa.
- **Riesgo alto y permanente:** YouTube cambia con frecuencia. La app tiene que fallar bien (mensaje claro y sugerencia de actualizar el motor), no colgarse.

---

## 5. Otras plataformas

| Plataforma | Resultado | Comentario |
|---|---|---|
| YouTube | ✅ info · ⚠️ descarga | Info OK. La descarga con el yt-dlp incluido falló con **403**. Después de actualizar a 2026.10.26 la info funcionó en 28 s. **La descarga con la versión nueva quedó sin verificar**: se cortó al apagar el emulador. |
| Vimeo | ❌ | Ese video ahora exige iniciar sesión, y además falta un *impersonate target* (`curl_cffi`). |
| Dailymotion, TikTok | Sin probar | Se prueban en la Etapa 3, en un teléfono. |

**Conclusión:** la promesa de la app debe ser "lo que soporte yt-dlp, sin garantías". Esto ya está alineado con la sección 2.1 de [main.md](../main.md). Vimeo se reemplazó por Dailymotion como plataforma prioritaria (ADR-008).

---

## 6. FFmpeg

### 6.1. Lo que trae youtubedl-android (medido)

FFmpeg **7.1.1**, compilado con NDK r28c y API 24, con `--enable-gpl --enable-version3 --enable-mediacodec`.

Codificadores disponibles: `libmp3lame`, `aac`, `libopus`, `libvorbis`, `flac`, `alac`, `pcm_s16le`, `libx264`, `libx265`, `mpeg4`, `libvpx`, `libvpx-vp9`, `libaom-av1`, `libsvtav1`, `librav1e`, `h264_mediacodec`, `hevc_mediacodec` y `av1_mediacodec`. **Cubre todos los formatos de la especificación**, incluida la aceleración por hardware vía MediaCodec.

Conversiones probadas (todas OK): generar MP4 (x264 + AAC), MP4 → MP3 192k, remux MP4 → MKV con `-c copy`, MP4 → Opus y MP3 → FLAC. El progreso se obtiene con `-progress pipe:1`.

Rendimiento: codificar 10 s de video 360p con x264 tardó 18,8 s en el emulador, que usa CPU por software. No es representativo; hay que medirlo en un teléfono.

### 6.2. Alternativas descartadas

| Opción | Estado | Por qué no |
|---|---|---|
| FFmpegKit (arthenica) | Retirado en enero de 2025, binarios fuera de Maven Central en abril de 2025, repo archivado | Sin mantenimiento |
| `dev.ffmpegkit-maintained` 8.1.9 | Fork comunitario, 25 estrellas, un solo mantenedor | Duplicaría FFmpeg (~35 MB más). Mantenimiento no verificable. Algunas funciones son "Pro". |
| FFmpegKitNext | Continuación oficial, solo código fuente | Requiere compilación propia (Nix/NDK). Es mucho trabajo y espacio en disco para la ganancia que da. |
| Compilación propia | — | Se reserva para cuando haga falta un FFmpeg más nuevo o con menos peso. |

### 6.3. Diseño resultante

El conversor ejecuta `libffmpeg.so` con `ProcessBuilder` (argumentos como lista, nunca un string de shell), parsea `-progress pipe:1` y cancela con `Process.destroy()`. **No depende de yt-dlp**, como pide la especificación, aunque comparta el binario.

---

## 7. Tamaño

| Medida | Valor |
|---|---|
| APK debug arm64-v8a (sin UI, sin aria2c) | **57 MB** |
| APK debug x86_64 | 60 MB |
| Paquetes descomprimidos en el primer arranque (`noBackupFilesDir`) | **~243 MB** |
| Tiempo de la primera inicialización (unzip) | ~12 s (emulador) |
| Referencia: YTDLnis 1.9.1.1 arm64 | 66 MB (universal: 208 MB) |

**Impacto:** la app ocupa ~300 MB instalada. La primera inicialización tiene que mostrar progreso, correr fuera del hilo principal y verificar antes que haya espacio libre.

---

## 8. Actualización del motor

Restricción de Android (targetSdk ≥ 29): **no se pueden ejecutar binarios nativos descargados**, solo los que vienen en `jniLibs` del APK.

| Componente | ¿Actualizable sin reinstalar? |
|---|---|
| yt-dlp (zipapp de Python) | **Sí.** Se ejecuta como script con el Python incluido. Medido: actualización a 2026.10.26 en 44 s. |
| Python, FFmpeg, QuickJS | **No.** Requieren un APK nuevo. |

El actualizador de la librería **no sirve tal cual**: no verifica checksums ni firmas, y borra la versión anterior antes de instalar la nueva. MediaForge implementa su propio actualizador:

1. Consulta el último release de `yt-dlp/yt-dlp` en GitHub.
2. Descarga el asset `yt-dlp` (zipapp) junto con `SHA2-256SUMS` y `SHA2-256SUMS.sig`.
3. Verifica la firma con la clave pública de yt-dlp incluida en el APK, y después el hash.
4. Instala en un directorio nuevo y prueba que funcione (`--version`). Recién entonces cambia el puntero de la versión activa.
5. Conserva la versión anterior para poder volver atrás. Nunca toca la versión que viene en el APK.

---

## 9. Defectos de youtubedl-android que el adaptador debe cubrir

1. **`VideoInfo.formats` mapea `requested_formats`**, no la lista completa de formatos. → Se parsea la salida de `--dump-json` con un parser propio (kotlinx.serialization).
2. **`destroyChildProcesses` pasa a `pstree` el ID de texto de la app en lugar del PID**, y lo interpola en un comando de shell. Los procesos hijos (ffmpeg) pueden quedar vivos, y además hay riesgo de inyección si el ID no es controlado. → Usar solo IDs generados internamente (UUID) y, después de cancelar, matar el árbol de procesos por PID real (`ProcessHandle` no existe en Android: usar `/proc/<pid>/task/*/children`).
3. **El actualizador no verifica integridad** (sección 8).
4. **API bloqueante con singletons globales.** → Envolver con `withContext(Dispatchers.IO)`, exponer `Flow` de progreso y cancelar de forma cooperativa.
5. **Mensajes de error = stderr crudo.** → Clasificar los errores (requiere login, no soportado, 403, red, sin espacio, motor desactualizado) para mostrarlos en la UI.
6. Agrega `--no-cache-dir` siempre. → Pasar un `--cache-dir` propio (mejora el tiempo de YouTube).

Medido: la cancelación con `destroyProcessById` funcionó (`CanceledException` a los 8 s, sin procesos sueltos, sin archivos parciales). En esa prueba ffmpeg todavía no había arrancado; el caso de ffmpeg como proceso hijo sigue pendiente.

---

## 10. Ejecución en segundo plano

- Las descargas y conversiones corren en un **foreground service** con notificación (`dataSync` para descargas, `mediaProcessing` para conversiones en Android 15+).
- **Límite de Android 15+:** cada uno de esos tipos tiene 6 h por cada 24 h en segundo plano. Al vencer, el sistema llama a `onTimeout()` y hay que detener el servicio en segundos. → El trabajo se persiste en Room y se marca como "interrumpido, reintentable".
- WorkManager se usa para reanudar trabajos pendientes después de que el proceso muere o el teléfono se reinicia, no para la transferencia en sí. *(Revisado en ADR-017: no se usa WorkManager; los trabajos se recuperan al abrir la app.)*
- **Pausa real:** yt-dlp reanuda archivos `.part` con `--continue` en descargas HTTP. "Pausar" = cancelar el proceso conservando el `.part`, y "reanudar" = relanzar el mismo comando. Se documenta que no funciona en todos los casos (algunos streams fragmentados reinician).

---

## 11. Distribución: verificación de desarrolladores de Google

- Desde el **30/09/2026**, los dispositivos certificados exigen que las apps instaladas por fuera de Play vengan de **desarrolladores verificados**. Por ahora rige en Brasil, Indonesia, Singapur y Tailandia; **se extiende a todo el mundo en 2027**.
- Las apps sin verificar se pueden seguir instalando con adb o con un "flujo avanzado" que agrega fricción.
- Existe una cuenta para estudiantes y hobbistas que permite instalar en hasta 20 dispositivos sin documento de identidad.
- **Impacto en MediaForge:** para que cualquiera pueda instalar el APK desde la landing en 2027 hace falta **registrar la clave de firma en una cuenta de desarrollador verificada**. No hace falta resolverlo hasta la Etapa 12, pero condiciona qué clave de firma se crea (sección 13).

---

## 12. Pendiente de validar en un teléfono físico

Todo esto requiere un teléfono arm64 conectado y permiso explícito antes de instalar:

1. Descarga de video y audio de YouTube con yt-dlp actualizado.
2. Dailymotion y TikTok (info y descarga).
3. Cancelación con ffmpeg como proceso hijo (merge/postproceso).
4. Tiempos reales de conversión (x264 vs `h264_mediacodec`).
5. Primer arranque en arm64 (tiempo de unzip, espacio).

Ninguno bloquea las Etapas 1 y 2 (harness, estructura, UI base), que se validan en la JVM.

---

## 13. Riesgos

| # | Riesgo | Prob. | Impacto | Mitigación |
|---|---|---|---|---|
| R1 | YouTube rompe la extracción o la descarga | Alta | Alto | Actualizador verificado de yt-dlp, errores claros, el conversor sigue funcionando |
| R2 | youtubedl-android queda abandonado | Media | Medio | Adaptador aislado; plan B con Chaquopy o paquetes de YTDLnis |
| R3 | Python 3.12 deja de ser soportado por yt-dlp (EOL de 3.12: oct 2028) | Baja (2 años) | Alto | Plan B; seguir los avisos de deprecación de yt-dlp |
| R4 | Sitios que requieren *impersonation* (`curl_cffi`) o login | Alta | Medio | No prometer soporte; detectar el error y explicarlo |
| R5 | Espacio en disco del dispositivo (~300 MB instalada) | Media | Medio | Chequeo previo, limpieza de temporales, aviso en la landing |
| R6 | Verificación de desarrolladores de Google en 2027 | Alta | Alto (distribución) | Decidir cuenta y clave de firma antes de la Etapa 12 |
| R7 | **Disco de la máquina de desarrollo al 98% (~4,5 GB libres)** | Alta | Alto | Liberar espacio antes de la Etapa 1. `~/.gradle` ocupa 9,4 GB con varias versiones viejas de Gradle. |
| R8 | El emulador cuelga la máquina | Cierta | Medio | Pruebas en la JVM; dispositivo físico con permiso previo |
| R9 | Obligaciones de licencia (GPL-3.0, FFmpeg, x264/x265, Python, QuickJS MIT, yt-dlp Unlicense) | Cierta | Medio | Repo GPL-3.0, pantalla de licencias, `THIRD_PARTY_NOTICES.md`, código fuente público |

---

## 14. Decisiones del usuario (resueltas el 2026-11-06)

1. **Licencia GPL-3.0 con repo público:** confirmada → ADR-003. Repo: https://github.com/franbenaglia/mediaForge
2. **Espacio en disco:** liberados ~16 GB (cachés de Gradle y Android Studio, emulador). Quedan ~21 GB libres.
3. **Plataformas prioritarias:** Dailymotion reemplaza a Vimeo → ADR-008.
4. **Teléfono de prueba:** se usa uno físico, pidiendo permiso antes de instalar → ADR-009.

---

## 15. Fuentes

- youtubedl-android: https://github.com/yausername/youtubedl-android (releases y código fuente, leídos el 2026-11-06)
- yt-dlp EJS (runtimes de JS): https://github.com/yt-dlp/yt-dlp/wiki/EJS · anuncio: https://github.com/yt-dlp/yt-dlp/issues/15012
- YTDLnis y sus paquetes: https://github.com/deniscerri/ytdlnis · https://github.com/deniscerri/ytdlnis-packages
- Seal: https://github.com/JunkFood02/Seal
- Chaquopy: https://chaquo.com/chaquopy/doc/current/versions.html
- FFmpegKit (archivado): https://github.com/arthenica/ffmpeg-kit · fork: https://github.com/ffmpegkit-maintained/ffmpeg · FFmpegKitNext: https://github.com/arthenica/ffmpeg-kit-next/wiki/Building-Android
- Android 15, límites de foreground services: https://developer.android.com/about/versions/15/behavior-changes-15
- Verificación de desarrolladores: https://android-developers.googleblog.com/2026/06/android-developer-verification.html · https://www.androidauthority.com/android-sideloading-developer-verification-first-wave-rollout-3717921/
