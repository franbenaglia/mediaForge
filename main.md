# PROYECTO: MediaForge
## Aplicación Android para descarga, extracción y conversión multimedia

**Destinatario:** Claude Code  
**Tipo de proyecto:** Aplicación Android nativa  
**Lenguaje principal:** Kotlin  
**Interfaz:** Jetpack Compose  
**Motor de extracción:** yt-dlp  
**Motor multimedia:** FFmpeg  
**Backend externo:** No  
**Distribución:** APK mediante landing page y GitHub Releases  
**Objetivo:** Aplicación funcional, mantenible y profesional para portfolio de desarrollo Android.

---

# 1. OBJETIVO GENERAL

Desarrollar una aplicación Android denominada provisionalmente **MediaForge**, destinada a descargar, administrar, convertir y procesar archivos multimedia.

La aplicación deberá permitir que el usuario:

1. Introduzca la URL de un video o recurso multimedia.
2. Identifique automáticamente la plataforma de origen.
3. Consulte la información del contenido.
4. Visualice los formatos y resoluciones disponibles.
5. Seleccione el formato deseado.
6. Descargue video o solamente audio cuando la descarga esté permitida.
7. Convierta archivos entre diferentes formatos multimedia.
8. Administre una cola de descargas.
9. Consulte su historial.
10. Reproduzca o comparta los archivos obtenidos.

También deberá funcionar como conversor multimedia independiente, utilizando archivos previamente almacenados en el dispositivo.

La aplicación será completamente independiente de servidores propios.

Todas las operaciones de descarga, extracción y conversión deberán ejecutarse localmente en Android.

La conexión a Internet solamente será necesaria para acceder a contenidos remotos, comprobar actualizaciones y descargar componentes opcionales.

## 1.1. Distribución

La aplicación NO está destinada inicialmente a Google Play.

Su distribución será mediante:

- APK descargable desde una landing page.
- GitHub Releases.
- Eventualmente, otros repositorios de aplicaciones Android.

La aplicación debe poder instalarse directamente mediante sideloading.

No incorporar dependencias de Google Play Services salvo que exista una justificación técnica clara.

## 1.2. Restricciones legales y operativas

La aplicación estará destinada a contenidos propios, autorizados, de dominio público o cuya descarga esté permitida por la plataforma y los titulares de derechos.

El soporte técnico de un extractor no debe interpretarse como autorización para descargar cualquier contenido.

No implementar mecanismos para:

- Eludir DRM.
- Evadir sistemas de pago.
- Obtener contenido privado sin autorización.
- Evadir controles de acceso.
- Extraer credenciales de otras aplicaciones.
- Evadir restricciones técnicas de acceso.

Las funciones deben respetar las condiciones aplicables de las plataformas.

No incorporar funciones de eliminación de marcas de agua como mecanismo para ocultar atribución o procedencia.

---

# 2. PLATAFORMAS MULTIMEDIA

Utilizar yt-dlp como motor principal de extracción de información y descarga.

La arquitectura no debe incorporar lógica específica de extracción dentro de la interfaz Android.

Debe existir una capa de abstracción independiente.

## 2.1. Plataformas objetivo

Investigar y contemplar compatibilidad con:

- YouTube.
- YouTube Shorts.
- Vimeo.
- Dailymotion.
- TikTok.
- Instagram Reels.
- Facebook Videos.
- X/Twitter.
- Reddit.
- Twitch.
- Otras plataformas compatibles con yt-dlp.

La lista representa plataformas objetivo, no una garantía de compatibilidad.

Priorizar inicialmente YouTube, Vimeo, Dailymotion y TikTok, siempre dentro de las restricciones de descarga aplicables.

El sistema deberá detectar cuándo una URL no es compatible, requiere autenticación autorizada o no puede procesarse.

No prometer soporte universal.

## 2.2. Detección automática

Al introducir una URL, la aplicación deberá:

1. Validar su estructura.
2. Identificar el dominio.
3. Comprobar si existe soporte mediante el motor extractor.
4. Obtener los metadatos disponibles.
5. Consultar los formatos existentes.
6. Informar las capacidades reales.

Evitar mantener una lista rígida de formatos por plataforma.

Las opciones deberán generarse a partir de la información obtenida.

## 2.3. Información del recurso

Cuando esté disponible, mostrar:

- Título.
- Plataforma.
- Autor o canal.
- Miniatura.
- Duración.
- Fecha de publicación.
- Descripción resumida.
- Resoluciones disponibles.
- Formatos disponibles.
- Códecs.
- Tamaño estimado.
- Disponibilidad de subtítulos.

No bloquear toda la operación si faltan metadatos opcionales.

---

# 3. DESCARGA DE VIDEO

El usuario podrá seleccionar el formato de salida y la calidad.

## 3.1. Contenedores

Contemplar:

- MP4.
- MKV.
- WebM.
- MOV.
- AVI.

Los formatos MP4, MKV y WebM tendrán prioridad.

MOV y AVI podrán incorporarse mediante conversión.

Distinguir claramente entre contenedores multimedia y códecs.

## 3.2. Resoluciones

Mostrar las resoluciones realmente disponibles:

- 360p.
- 480p.
- 720p.
- 1080p.
- 1440p.
- 2160p.
- Otras resoluciones.

Incorporar una opción de mejor calidad disponible.

## 3.3. Códecs

Contemplar, según compatibilidad:

- H.264.
- H.265/HEVC.
- VP9.
- AV1.

La aplicación deberá informar cuando una conversión requiera recodificación.

No asumir que todos los dispositivos Android pueden codificar o decodificar todos los formatos mediante aceleración de hardware.

## 3.4. Descarga y procesamiento

Diferenciar:

1. Descarga directa.
2. Descarga de pistas de audio y video separadas.
3. Combinación de pistas sin recodificación.
4. Conversión con recodificación.

Priorizar operaciones sin pérdida de calidad cuando sea posible.

Mostrar al usuario si la operación requiere conversión y tiempo adicional de procesamiento.

---

# 4. EXTRACCIÓN DE AUDIO

Permitir obtener solamente el audio de un recurso multimedia autorizado.

## 4.1. Formatos

Implementar soporte para:

- MP3.
- M4A.
- AAC.
- Opus.
- OGG.
- WAV.
- FLAC.

Priorizar MP3, M4A y Opus.

## 4.2. Calidad

Permitir seleccionar parámetros compatibles con el formato de salida.

Para MP3:

- 128 kbps.
- 192 kbps.
- 256 kbps.
- 320 kbps.

Agregar selección automática de calidad.

Para formatos sin pérdida, no presentar bitrates fijos que carezcan de sentido técnico.

Informar que aumentar el bitrate mediante conversión no mejora la calidad original.

## 4.3. Metadatos

Cuando corresponda, incorporar:

- Título.
- Autor.
- Nombre del archivo.
- Fecha.
- Imagen de portada.
- Duración.

Permitir editar manualmente el nombre final del archivo.

---

# 5. CONVERSOR MULTIMEDIA OFFLINE

La aplicación deberá incluir un módulo independiente de conversión de archivos locales.

Este componente no debe depender de yt-dlp.

Utilizar FFmpeg como motor principal.

## 5.1. Conversiones

Contemplar inicialmente:

**Video a video**

- MP4 → MKV.
- MKV → MP4.
- WebM → MP4.
- MOV → MP4.

**Video a audio**

- MP4 → MP3.
- MP4 → M4A.
- MKV → MP3.
- WebM → MP3.
- MP4 → WAV.

**Audio a audio**

- WAV → MP3.
- FLAC → MP3.
- M4A → MP3.
- MP3 → WAV.
- WAV → FLAC.

No limitar artificialmente las combinaciones cuando los códecs instalados sean compatibles.

## 5.2. Funcionalidades adicionales

Contemplar:

- Extracción de un fragmento de audio.
- Recorte de video.
- Cambio de resolución.
- Compresión de video.
- Extracción de fotogramas.
- Conversión masiva de archivos.
- Extracción de audio sin recodificación.

Estas funcionalidades podrán desarrollarse progresivamente.

## 5.3. Optimización

Siempre que sea posible:

- Evitar recodificaciones innecesarias.
- Utilizar operaciones de remux.
- Detectar compatibilidad entre contenedores y códecs.
- Controlar memoria y almacenamiento temporal.
- Limitar conversiones simultáneas.

Evaluar MediaCodec para operaciones que puedan beneficiarse de aceleración de hardware.

Mantener FFmpeg como motor general.

---

# 6. TECNOLOGÍAS Y ARQUITECTURA

## 6.1. Stack principal

Utilizar:

- Kotlin.
- Jetpack Compose.
- Material 3.
- Android Jetpack.
- Kotlin Coroutines.
- StateFlow.
- Room.
- DataStore.
- Hilt o una alternativa justificada de inyección de dependencias.
- yt-dlp.
- FFmpeg.

Evaluar Chaquopy para ejecutar yt-dlp, considerando la compatibilidad de Python y las arquitecturas Android.

No seleccionar bibliotecas únicamente por popularidad.

Verificar mantenimiento, compatibilidad y licencias.

## 6.2. Integración de yt-dlp

Investigar la integración de yt-dlp en Android.

Considerar como referencia:

https://github.com/yausername/youtubedl-android

Examinar también implementaciones y forks mantenidos.

Evaluar:

- Compatibilidad con Android actual.
- Arquitecturas ABI.
- Versión de Python.
- Tamaño del APK.
- Mecanismos de actualización.
- Manejo de errores.
- Cancelación de procesos.
- Extracción de metadatos.
- Integración con FFmpeg.

No asumir que un wrapper antiguo sigue siendo compatible.

Realizar una prueba técnica mínima antes de construir el resto de la aplicación.

## 6.3. Integración de FFmpeg

Seleccionar una distribución compatible con Android que permita realizar conversiones reales.

Verificar especialmente:

- Estado de mantenimiento.
- ABI compatibles.
- Tamaño de las bibliotecas.
- Códecs disponibles.
- Licencias LGPL/GPL.
- Restricciones de redistribución.
- Disponibilidad de los codificadores necesarios.

No asumir que los antiguos artefactos de FFmpegKit permanecen mantenidos.

Si es necesario, evaluar una compilación propia de FFmpeg para Android.

Documentar el procedimiento de compilación.

El proyecto deberá proporcionar los avisos, licencias y demás obligaciones correspondientes a sus dependencias.

## 6.4. Arquitectura por capas

Aplicar Clean Architecture de forma pragmática.

Separar:

**Presentation**

Componentes Jetpack Compose, navegación y ViewModels.

**Domain**

Modelos, contratos y casos de uso independientes de Android.

**Data**

Persistencia, implementaciones de repositorios y almacenamiento.

**Media Engine**

Integraciones con yt-dlp y FFmpeg.

**Download Engine**

Colas, descargas, estados, reintentos y control de tareas.

**Platform**

Servicios Android, permisos, notificaciones y acceso a archivos.

## 6.5. Interfaces

Crear abstracciones conceptualmente equivalentes a:

```kotlin
interface MediaExtractor {
    suspend fun extractInfo(url: String): MediaInfo
    suspend fun getFormats(url: String): List<MediaFormat>
}

interface MediaDownloader {
    suspend fun download(request: DownloadRequest): DownloadResult
    suspend fun cancel(downloadId: String)
}

interface MediaConverter {
    suspend fun convert(request: ConversionRequest): ConversionResult
    suspend fun cancel(conversionId: String)
}
```

Estas interfaces son orientativas.

Claude Code deberá definir los contratos definitivos, incluyendo progreso, cancelación cooperativa y estados mediante Flow cuando corresponda.

Evitar acoplar las clases de dominio a los detalles internos de yt-dlp o FFmpeg.

---

# 7. INTERFAZ DE USUARIO

Construir una interfaz moderna mediante Jetpack Compose y Material 3.

La aplicación deberá ofrecer modo claro y oscuro.

Priorizar una experiencia sencilla y profesional.

## 7.1. Pantalla principal

Elementos:

- Campo de URL.
- Botón para pegar desde portapapeles.
- Botón de análisis.
- Acceso a descargas.
- Acceso al conversor.
- Acceso al historial.

Cuando el usuario introduzca una URL válida, mostrar una tarjeta de información.

## 7.2. Pantalla de información multimedia

Mostrar:

- Miniatura.
- Título.
- Plataforma.
- Duración.
- Formatos disponibles.

Separar claramente las opciones:

**Video**

Seleccionar resolución, contenedor y calidad.

**Audio**

Seleccionar formato y bitrate.

Incluir una estimación de espacio necesario cuando pueda calcularse.

## 7.3. Administrador de descargas

Mostrar:

- Nombre del archivo.
- Plataforma.
- Progreso.
- Velocidad.
- Tamaño descargado.
- Tamaño total, si se conoce.
- Estado.
- Errores.

Estados sugeridos:

- Pendiente.
- Analizando.
- Descargando.
- Procesando.
- Completado.
- Fallido.
- Cancelado.

Diferenciar la fase de descarga de la fase de conversión.

## 7.4. Controles

Implementar:

- Cancelar.
- Reintentar.
- Eliminar.
- Compartir.
- Abrir archivo.
- Ver detalles.

Evaluar pausa y reanudación reales.

No simular una pausa si el motor no permite recuperar la transferencia.

Si solamente puede detenerse y reiniciarse un trabajo, documentar esa limitación.

## 7.5. Conversor

Crear una pantalla independiente.

Permitir:

1. Seleccionar archivo local.
2. Elegir formato de salida.
3. Configurar calidad.
4. Iniciar conversión.
5. Consultar progreso.
6. Guardar resultado.
7. Compartir archivo.

---

# 8. INTEGRACIÓN CON ANDROID

## 8.1. Compartir enlaces

Implementar recepción de enlaces mediante Android Sharesheet.

La aplicación deberá aceptar URLs compartidas desde:

- Navegadores.
- Aplicaciones multimedia.
- Otras aplicaciones Android.

El enlace recibido se cargará automáticamente en el analizador.

## 8.2. Almacenamiento

Utilizar mecanismos modernos de Android:

- Storage Access Framework.
- MediaStore.
- Directorios privados para archivos temporales.

Evitar solicitar acceso indiscriminado a todo el almacenamiento.

Permitir seleccionar una ubicación de destino cuando corresponda.

## 8.3. Ejecución prolongada

Las descargas deben continuar mientras el usuario utiliza otras aplicaciones, dentro de las restricciones de Android.

Evaluar Foreground Services y WorkManager según el tipo de tarea.

Incorporar:

- Notificaciones de progreso.
- Cancelación desde notificación.
- Gestión del ciclo de vida.
- Recuperación tras cierre del proceso.
- Persistencia de trabajos pendientes.

Respetar los requisitos de permisos y tipos de foreground service de las versiones Android soportadas.

No prometer ejecución indefinida si el sistema operativo puede interrumpirla.

## 8.4. Compatibilidad

Definir un minSdk razonable.

Priorizar dispositivos Android modernos.

Evaluar inicialmente:

- arm64-v8a.
- armeabi-v7a, si las dependencias lo permiten.

No incluir arquitecturas que carezcan de soporte real.

---

# 9. BASE DE DATOS Y PERSISTENCIA

Utilizar Room para mantener:

- Historial de descargas.
- Historial de conversiones.
- Trabajos pendientes.
- Estado de trabajos.
- Rutas de archivos.
- Metadatos.
- Configuración asociada a cada operación.

Utilizar DataStore para preferencias:

- Formato predeterminado.
- Calidad predeterminada.
- Cantidad máxima de descargas simultáneas.
- Preferencias de interfaz.
- Política de conexión.
- Opciones de almacenamiento.

El sistema deberá recuperar correctamente su estado después de reiniciar la aplicación.

---

# 10. ACTUALIZACIÓN DEL MOTOR DE EXTRACCIÓN

Una de las principales dificultades técnicas de yt-dlp es que las plataformas modifican regularmente sus interfaces.

La aplicación deberá desacoplar la versión de yt-dlp del resto del código tanto como sea posible.

Investigar la posibilidad de actualizar el motor sin reinstalar completamente el APK.

Requisitos:

1. Mostrar versión instalada.
2. Consultar si existen versiones compatibles más recientes.
3. Verificar integridad y autenticidad de las actualizaciones.
4. Permitir actualización manual.
5. Conservar una versión funcional anterior cuando resulte viable.
6. Informar errores de compatibilidad.

No descargar y ejecutar código arbitrario desde fuentes desconocidas.

No implementar mecanismos de actualización que comprometan la seguridad del dispositivo.

Si las restricciones de Android impiden actualizar de forma segura algún componente, utilizar actualizaciones completas del APK.

---

# 11. SEGURIDAD Y ROBUSTEZ

Implementar medidas apropiadas para una aplicación que procesa URLs y archivos externos.

## 11.1. URLs

- Validar el esquema.
- Rechazar protocolos no autorizados.
- Evitar ejecución de comandos construidos mediante concatenación insegura.
- No permitir que una URL altere arbitrariamente argumentos de ejecución.
- Controlar redirecciones.
- Manejar respuestas malformadas.
- Aplicar límites de tiempo.

## 11.2. Archivos

- Sanitizar nombres.
- Evitar path traversal.
- Gestionar archivos incompletos.
- Evitar sobrescrituras accidentales.
- Comprobar espacio disponible.
- Limpiar archivos temporales.
- Gestionar fallos por falta de almacenamiento.

## 11.3. Procesamiento

- Evitar bloqueo del hilo principal.
- Controlar concurrencia.
- Liberar recursos nativos.
- Gestionar cancelaciones.
- Controlar consumo de memoria.
- Manejar correctamente procesos interrumpidos.

No registrar credenciales, tokens o información sensible en logs.

---

# 12. PRUEBAS

El proyecto deberá incluir pruebas automatizadas y procedimientos de validación manual.

## 12.1. Pruebas unitarias

Cubrir:

- Validación de URLs.
- Selección de formatos.
- Construcción segura de solicitudes.
- Cálculo de parámetros de conversión.
- Manejo de estados.
- Persistencia.
- Gestión de errores.
- Nombres de archivos.

## 12.2. Pruebas de integración

Verificar:

- Integración real con yt-dlp.
- Integración real con FFmpeg.
- Extracción de metadatos.
- Descargas autorizadas.
- Conversiones multimedia.
- Manejo de archivos temporales.
- Cancelación de tareas.

## 12.3. Pruebas Android

Utilizar pruebas instrumentadas cuando corresponda.

Verificar:

- Navegación.
- Permisos.
- Rotación.
- Segundo plano.
- Notificaciones.
- Compartir enlaces.
- Recuperación de estado.

## 12.4. Archivos de prueba

Incluir un conjunto pequeño de archivos multimedia de uso permitido para realizar pruebas reproducibles.

No depender exclusivamente de plataformas externas para validar FFmpeg.

Separar las pruebas offline de las pruebas que necesitan Internet.

Los tests de integración con plataformas externas no deberán bloquear indiscriminadamente todas las compilaciones si un servicio remoto modifica su funcionamiento.

---

# 13. DEVELOPMENT HARNESS OBLIGATORIO

**Este requisito es fundamental.**

Claude Code deberá implementar un sistema persistente de administración y seguimiento del desarrollo antes de comenzar la implementación principal.

El objetivo es evitar:

- Pérdidas de contexto.
- Pérdidas de decisiones.
- Repetición de tareas.
- Modificaciones contradictorias.
- Etapas incompletas.
- Regresiones no detectadas.
- Pérdida de información cuando termina una sesión de Claude Code.

El harness deberá permitir que una nueva sesión retome el proyecto utilizando únicamente los archivos persistentes del repositorio.

No depender de la memoria de la conversación.

## 13.1. Estructura propuesta

Crear una estructura similar a:

```text
MediaForge/
│
├── app/
│
├── core/
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── REQUIREMENTS.md
│   ├── DEVELOPMENT_PLAN.md
│   ├── DECISIONS.md
│   ├── TESTING.md
│   ├── CHANGELOG.md
│   └── RELEASE.md
│
├── harness/
│   ├── PROJECT_STATE.md
│   ├── SESSION_LOG.md
│   ├── NEXT_STEPS.md
│   ├── KNOWN_ISSUES.md
│   ├── TASKS.md
│   ├── QUALITY_GATES.md
│   └── RECOVERY.md
│
├── scripts/
│   ├── verify.sh
│   ├── test.sh
│   ├── build.sh
│   └── release.sh
│
├── CLAUDE.md
│
└── README.md
```

Claude Code podrá adaptar esta estructura si existe una organización técnicamente superior.

La documentación debe permanecer sincronizada con el código.

## 13.2. Archivo CLAUDE.md

Crear un archivo principal que explique:

- Objetivo del proyecto.
- Arquitectura.
- Tecnologías.
- Reglas de desarrollo.
- Ubicación del harness.
- Comandos de compilación.
- Comandos de pruebas.
- Flujo de trabajo.
- Procedimiento de recuperación de contexto.

Este archivo deberá ser el punto de entrada para cada nueva sesión.

## 13.3. PROJECT_STATE.md

Mantener el estado actual del proyecto.

Registrar:

- Etapa activa.
- Etapas completadas.
- Funcionalidades implementadas.
- Funcionalidades pendientes.
- Estado de compilación.
- Estado de pruebas.
- Dependencias importantes.
- Bloqueos técnicos.
- Último commit validado.

Actualizarlo al finalizar cada unidad de trabajo.

## 13.4. SESSION_LOG.md

Registrar de forma resumida:

- Fecha de sesión.
- Objetivos.
- Tareas realizadas.
- Archivos modificados.
- Pruebas ejecutadas.
- Resultados.
- Problemas encontrados.
- Decisiones relevantes.

No utilizar este archivo como un registro excesivamente detallado de cada línea de código.

Su función es conservar información necesaria para la continuidad del desarrollo.

## 13.5. NEXT_STEPS.md

Mantener una lista priorizada de próximos trabajos.

Cada tarea deberá indicar:

- Identificador.
- Descripción.
- Prioridad.
- Dependencias.
- Criterio de aceptación.

Evitar tareas demasiado grandes o ambiguas.

## 13.6. DECISIONS.md

Registrar decisiones arquitectónicas importantes.

Utilizar registros ADR cuando corresponda.

Ejemplos:

- Selección del wrapper de yt-dlp.
- Integración de Python.
- Selección de distribución FFmpeg.
- Arquitecturas ABI.
- Modelo de ejecución en segundo plano.
- Política de actualización.
- Formato de persistencia.

Documentar también las alternativas descartadas y sus motivos.

## 13.7. QUALITY_GATES.md

Definir controles obligatorios para considerar terminada una tarea.

Como mínimo:

1. Código compila.
2. Pruebas relacionadas pasan.
3. No aparecen errores nuevos de análisis estático.
4. No quedan cambios accidentales.
5. Se actualiza la documentación afectada.
6. Se registra el estado del trabajo.
7. Se crea un commit cuando corresponde.

Si alguna verificación falla, registrar el motivo.

No marcar una tarea como completada sin cumplir sus criterios de aceptación.

## 13.8. Recuperación de sesiones

Al comenzar una nueva sesión, Claude Code deberá:

1. Leer `CLAUDE.md`.
2. Leer `harness/PROJECT_STATE.md`.
3. Leer `harness/NEXT_STEPS.md`.
4. Consultar `harness/KNOWN_ISSUES.md`.
5. Revisar los últimos commits.
6. Comprobar el estado de Git.
7. Identificar la siguiente tarea.
8. Continuar sin repetir trabajo completado.

Ante diferencias entre documentación y repositorio, deberá investigarlas.

No asumir automáticamente que la documentación es correcta si contradice el código.

## 13.9. Automatización

Crear scripts de verificación reproducibles.

Idealmente, un comando como:

```bash
./scripts/verify.sh
```

deberá ejecutar:

- Compilación.
- Pruebas unitarias.
- Análisis estático.
- Comprobaciones de calidad disponibles.

Crear scripts independientes para operaciones más costosas, como pruebas instrumentadas.

## 13.10. Git

Utilizar Git desde el inicio.

Crear commits pequeños, coherentes y descriptivos.

No mezclar cambios de arquitectura, correcciones no relacionadas y nuevas funcionalidades en un único commit.

No realizar commits de:

- Credenciales.
- Secrets.
- Archivos temporales.
- Datos privados.
- Artefactos innecesarios.

Antes de cerrar una etapa, comprobar que el repositorio se encuentra en un estado consistente.

## 13.11. Protección frente a interrupciones

El sistema deberá soportar interrupciones inesperadas.

Para ello:

- Trabajar en tareas pequeñas.
- Actualizar el harness regularmente.
- Mantener commits verificables.
- Registrar bloqueos.
- Conservar pasos concretos de recuperación.

El objetivo es que una interrupción no obligue a reconstruir manualmente el estado del desarrollo.

---

# 14. PLAN DE DESARROLLO

Dividir el trabajo en etapas.

Claude Code deberá crear un plan detallado antes de implementar.

## Etapa 0. Investigación técnica

Objetivos:

- Evaluar integración yt-dlp.
- Evaluar integración FFmpeg.
- Comprobar compatibilidad Android.
- Revisar licencias.
- Estimar tamaño del APK.
- Validar arquitecturas compatibles.
- Identificar riesgos.

Entregable: documento de factibilidad técnica.

No comenzar el desarrollo completo sin resolver los principales bloqueos de integración.

## Etapa 1. Harness y estructura

- Crear repositorio.
- Crear documentación.
- Implementar harness.
- Configurar Gradle.
- Crear estructura Android.
- Configurar pruebas.
- Validar compilación mínima.

## Etapa 2. Interfaz base

- Jetpack Compose.
- Navegación.
- Material 3.
- Tema claro y oscuro.
- Pantalla principal.
- Componentes reutilizables.

## Etapa 3. Motor de extracción

- Integrar yt-dlp.
- Validar URLs.
- Obtener metadatos.
- Consultar formatos.
- Gestionar errores.

## Etapa 4. Descarga de video

- Descargar archivos.
- Seleccionar calidad.
- Administrar almacenamiento.
- Mostrar progreso.
- Gestionar cancelaciones.

## Etapa 5. Extracción de audio

- Integrar procesamiento de audio.
- Generar MP3.
- Incorporar formatos adicionales.
- Configurar calidad.
- Administrar metadatos.

## Etapa 6. Conversor multimedia

- Integrar FFmpeg.
- Seleccionar archivos locales.
- Convertir formatos.
- Mostrar progreso.
- Gestionar errores.

## Etapa 7. Gestor de trabajos

- Cola de operaciones.
- Persistencia.
- Reintentos.
- Cancelación.
- Concurrencia.
- Recuperación de trabajos.

## Etapa 8. Android Sharesheet

- Recepción de URLs.
- Validación.
- Integración con pantalla principal.
- Pruebas con aplicaciones externas.

## Etapa 9. Historial y configuración

- Room.
- DataStore.
- Historial.
- Preferencias.
- Administración de archivos.

## Etapa 10. Funciones avanzadas

- Playlists autorizadas.
- Subtítulos.
- Conversión masiva.
- Recorte.
- Extracción de fotogramas.
- Actualización del extractor, si resulta viable.

## Etapa 11. Calidad

- Pruebas unitarias.
- Pruebas de integración.
- Pruebas instrumentadas.
- Corrección de errores.
- Optimización.
- Auditoría de licencias.
- Revisión de seguridad.

## Etapa 12. Distribución

- Configuración de firma.
- Generación de APK release.
- Documentación de instalación.
- GitHub Releases.
- README.
- Capturas de pantalla.
- Información para landing page.

---

# 15. CRITERIOS DE ACEPTACIÓN DEL MVP

La primera versión funcional deberá cumplir:

1. Instalarse en un dispositivo Android compatible.
2. Iniciar sin errores.
3. Permitir introducir una URL.
4. Obtener información de un recurso compatible.
5. Mostrar los formatos disponibles.
6. Descargar un video de prueba autorizado.
7. Extraer audio MP3 de contenido autorizado.
8. Seleccionar calidad cuando corresponda.
9. Mostrar progreso real.
10. Permitir cancelar una operación.
11. Convertir un MP4 local a MP3.
12. Convertir un archivo multimedia local a otro formato compatible.
13. Guardar archivos correctamente.
14. Permitir abrir y compartir archivos.
15. Recuperarse de errores habituales.
16. Mantener un historial persistente.
17. Ejecutar las operaciones sin backend propio.
18. Superar las pruebas definidas para el MVP.

No considerar completo el MVP si solamente se implementan pantallas de demostración.

Las funciones críticas deben ejecutarse realmente.

---

# 16. CALIDAD DEL PRODUCTO PARA PORTFOLIO

La aplicación debe demostrar competencias de desarrollo Android profesional.

Priorizar:

- Arquitectura mantenible.
- Código limpio.
- Separación de responsabilidades.
- Interfaces modernas.
- Integración de bibliotecas nativas.
- Procesamiento asíncrono.
- Persistencia.
- Gestión de errores.
- Seguridad.
- Pruebas automatizadas.
- Documentación.

Evitar complejidad arquitectónica innecesaria.

No incorporar servicios de pago, sistemas de cuentas o funcionalidades comerciales que no aporten al objetivo.

No agregar anuncios.

No incorporar analítica invasiva.

La aplicación deberá ser útil sin registro de usuario.

---

# 17. DOCUMENTACIÓN FINAL

Generar documentación suficiente para que otro desarrollador pueda:

1. Clonar el repositorio.
2. Configurar el entorno.
3. Compilar la aplicación.
4. Ejecutar pruebas.
5. Generar un APK.
6. Comprender la arquitectura.
7. Modificar o actualizar yt-dlp.
8. Modificar o actualizar FFmpeg.
9. Continuar el desarrollo.

Documentar versiones, dependencias y cualquier procedimiento que no pueda ejecutarse automáticamente.

Si alguna dependencia requiere compilación especial, incluir instrucciones reproducibles.

---

# 18. PREPARACIÓN PARA LANDING PAGE

La aplicación formará parte de un portfolio profesional.

Preparar información reutilizable para una landing page.

Incluir:

- Nombre del producto.
- Descripción breve en inglés.
- Descripción detallada en inglés.
- Principales funcionalidades.
- Tecnologías utilizadas.
- Requisitos mínimos.
- Capturas de pantalla.
- Versión actual.
- Historial de versiones.
- Enlace al APK.
- Enlace a GitHub, si el repositorio es público.
- Avisos legales y licencias.

No desarrollar la landing page dentro de este proyecto salvo que se solicite posteriormente.

Concentrarse en proporcionar los recursos necesarios.

---

# 19. INSTRUCCIONES OPERATIVAS PARA CLAUDE CODE

Debés trabajar como responsable técnico del proyecto.

No comenzar escribiendo toda la aplicación de una sola vez.

Primero:

1. Analizar la especificación.
2. Investigar las dependencias críticas.
3. Identificar riesgos de integración.
4. Crear el harness.
5. Crear el plan de desarrollo.
6. Inicializar el proyecto.
7. Realizar una prueba mínima de yt-dlp.
8. Realizar una prueba mínima de FFmpeg.
9. Validar la arquitectura propuesta.
10. Comenzar la implementación incremental.

Durante el desarrollo:

- Ejecutar pruebas.
- Mantener documentación.
- Registrar decisiones.
- Actualizar el harness.
- Crear commits coherentes.
- Evitar funcionalidades ficticias.
- No ocultar errores.
- No declarar completadas tareas sin validación.

Cuando una dependencia no sea viable, investigar alternativas compatibles y documentar la decisión.

No improvisar soluciones que comprometan el mantenimiento futuro.

No solicitar confirmación para decisiones técnicas menores que puedan resolverse mediante criterios de ingeniería.

Solicitar intervención del usuario únicamente cuando exista una decisión importante de alcance, costos, distribución, seguridad o una incompatibilidad que obligue a modificar los objetivos.

---

# 20. INSTRUCCIÓN FINAL

El objetivo es entregar una aplicación Android real, funcional y mantenible, no un prototipo visual.

La aplicación debe funcionar completamente en el dispositivo, sin backend externo, mediante Kotlin, Jetpack Compose, yt-dlp y FFmpeg o alternativas técnicamente justificadas.

El development harness es obligatorio y constituye parte integral del proyecto.

**La continuidad del desarrollo no debe depender de que Claude Code conserve el contexto de conversaciones anteriores.**

El repositorio debe contener toda la información necesaria para recuperar el estado y continuar trabajando.

**Comenzá por la Etapa 0: investigación técnica y validación de dependencias. A continuación, implementá el harness y avanzá progresivamente hasta completar el MVP.**