# MediaForge

App Android nativa para descargar, extraer y convertir multimedia en el dispositivo, sin backend.
Kotlin + Jetpack Compose + yt-dlp + FFmpeg. `applicationId` `com.fabapps.mediaforge` (debug: `.debug`).
Licencia GPL-3.0. Repo público: https://github.com/franbenaglia/mediaForge

## Al iniciar una sesión (recuperación de contexto)

1. Leé `harness/STATE.md`: etapa actual, estado de la verificación, bloqueos y problemas conocidos.
2. Leé `harness/TASKS.md`: la próxima tarea es la primera sin marcar de la etapa en curso.
3. Leé `docs/DECISIONS.md`: no rediscutas ni reviertas un ADR sin consultarlo con el usuario.
4. Ejecutá `git status` y `git log --oneline -10`. Si el repo no coincide con `STATE.md`, investigá antes de seguir: el código manda sobre la documentación.
5. Consultá la sección de [main.md](main.md) que corresponde a la tarea. `main.md` es la especificación original; los ADR la ajustan donde hace falta.

No dependas de la memoria de conversaciones anteriores. Todo lo necesario está en el repo.

## Puerta de calidad (al cerrar cada tarea)

Una tarea está terminada solo si, en orden:

1. `./scripts/verify.sh` termina con `verify: OK` (compila, pruebas unitarias, lint, ktlint).
2. Se cumple el criterio de aceptación de la tarea en `harness/TASKS.md`. Si requiere teléfono, queda marcada `[~]` (hecha, sin validar en dispositivo) hasta que se pruebe.
3. `git status` no muestra cambios accidentales.
4. La documentación afectada está actualizada (`ARCHITECTURE.md`, ADR nuevo si hubo una decisión).
5. La tarea está marcada en `TASKS.md` y `STATE.md` refleja el estado.
6. Commit pequeño con mensaje `T<id>: <resumen>`. No mezclar refactor, arreglo y funcionalidad.

Si algo falla y no se puede resolver, registralo en `STATE.md` (Bloqueos) en lugar de marcar la tarea.
Al cerrar una sesión, agregá una entrada breve en `harness/SESSION_LOG.md`.
Si la sesión se corta a mitad de tarea, anotá en `STATE.md` qué quedó hecho, qué falta y si compila.

## Comandos

```bash
./scripts/verify.sh            # verificación completa sin dispositivo (obligatoria)
./gradlew ktlintFormat         # corrige el formato automáticamente
./scripts/test-device.sh       # pruebas instrumentadas en el teléfono (pedir permiso antes)
./scripts/build-release.sh     # APK de release (requiere keystore.properties)
```

Usá siempre `./gradlew`. El `gradle` del sistema es 7.4.2 y no sirve.

## Entorno

- JDK 17, SDK en `~/Android/Sdk`, compila contra la plataforma 37. AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21.
- **Nunca uses el emulador**: cuelga la máquina (fue borrado). Lo que requiera dispositivo va a un teléfono físico por USB.
- **Pedí permiso al usuario antes de instalar algo en el teléfono.** Cada instalación por USB muestra un aviso de ~10 s que por defecto deniega (`INSTALL_FAILED_USER_RESTRICTED`): avisá para que esté mirando y toque "Instalar". No envíes toques por adb sin permiso.
- El disco de la máquina tiene poco espacio libre. No agregues cachés ni artefactos grandes sin necesidad; `spikes/*/build` se puede borrar.
- `spikes/engine-spike/` es un proyecto Gradle aparte (prueba técnica de la Etapa 0). No forma parte del build.

## Estructura

```text
app/            UI (Compose), ViewModels, servicios Android, DI (Hilt)
core/domain/    Kotlin puro: modelos, contratos de motores, reglas (sin Android)
docs/           FEASIBILITY, ARCHITECTURE, DECISIONS
harness/        STATE, TASKS, SESSION_LOG
scripts/        verify, test-device, build-release
spikes/         pruebas técnicas descartables
main.md         especificación original del proyecto
```

Detalle y módulos previstos en `docs/ARCHITECTURE.md`.

## Reglas que no se negocian

- Sin DRM bypass, sin eludir login, pagos ni controles de acceso, sin quitar marcas de agua (main.md §1.2).
- Nunca construir comandos con concatenación de strings ni shell: argumentos como lista. Una URL nunca puede convertirse en una opción (`--`/validación previa).
- Nunca ejecutar código descargado sin verificar firma y hash. Los binarios nativos solo vienen del APK.
- Nombres de archivo siempre por `FileNames.sanitize` y `FileNames.unique`.
- Nada de funciones simuladas: si el motor no permite algo (por ejemplo, pausa real), se documenta, no se finge.
- No registrar en logs URLs con tokens, cookies ni credenciales.
- Textos de UI en recursos (`values/` inglés, `values-es/` español), nunca incrustados en composables.
- Lógica fuera de los composables; cada pantalla expone un `UiState` inmutable.
- Dominio sin dependencias de Android ni de yt-dlp/FFmpeg.
- Sin Google Play Services, sin analítica, sin anuncios, sin cuentas.
