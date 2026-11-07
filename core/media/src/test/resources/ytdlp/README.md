# Fixtures de `yt-dlp --dump-json`

Estructura y valores tomados de la salida de yt-dlp 2026.10.26 observada en la prueba de la
Etapa 0 (Big Buck Bunny, Blender Foundation, CC BY), recortados a los campos que usa el parser.
Las URLs firmadas de los formatos se reemplazaron por `https://example.invalid/…`: nunca se
guardan URLs con tokens (CLAUDE.md).

- `youtube-real.json`: salida **real** de yt-dlp 2025.11.12 en el teléfono (2026-11-07), recortada
  por `EngineDeviceTest.trimForFixture` (sin URLs, descripción corta).
- `youtube.json`, `dailymotion.json`, `minimal.json`, `playlist.json`: reconstruidos, para casos borde
  (notación científica, tamaños faltantes, playlists).
- `dailymotion-real.json`: salida **real** de yt-dlp 2026.10.26 en el teléfono (2026-11-07), después
  de la actualización verificada (T3.7). Con 2025.11.12 Dailymotion devolvía 403.
