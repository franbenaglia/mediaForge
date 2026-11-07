# Registro de sesiones

Una entrada por sesión, la más reciente arriba. Solo lo necesario para continuar.

---

## 2026-11-07 · Etapa 2

**Hecho:** T2.1 (navegación tipada con barra inferior), T2.3 (componentes y formateadores), T3.1 (`UrlValidator`, adelantada porque la necesita T2.2) y T2.2 (pantalla Inicio). 61 pruebas.

**Problemas:** la máquina se colgó durante `verify.sh`. Se limitó la memoria de Gradle, `verify.sh` corre en dos pasos con `nice` y se desactivó la importación de Gradle de la extensión de Java de VS Code (generaba `bin/` y otro build en paralelo).

**Verificación:** `./scripts/verify.sh` OK (pico de 8,9 GB usados en total).

---

## 2026-11-06 · Etapas 0 y 1

**Objetivo:** investigación técnica y estructura del proyecto.

**Hecho:**
- Investigación de yt-dlp, FFmpeg, Chaquopy, YTDLnis/Seal, límites de foreground services y verificación de desarrolladores → `docs/FEASIBILITY.md`.
- Prueba técnica en `spikes/engine-spike/` (emulador API 36): YouTube info OK, descarga 403 con yt-dlp viejo, actualización OK, FFmpeg con todos los codificadores, conversiones offline OK, Vimeo exige login.
- Etapa 1: repo, Gradle, `:app` + `:core:domain`, contratos, `FileNames` (14 pruebas), scripts, harness y ADR-001 a ADR-013.

**Decisiones del usuario:** GPL-3.0 con repo público en GitHub (franbenaglia/mediaForge); Dailymotion reemplaza a Vimeo; no usar el emulador; pedir permiso antes de instalar en el teléfono.

**Problemas:** el emulador colgó la máquina y se eliminó. El disco estaba al 98%; se liberaron ~16 GB (cachés de Gradle y de Android Studio, emulador).

**Verificación:** `./scripts/verify.sh` OK.
