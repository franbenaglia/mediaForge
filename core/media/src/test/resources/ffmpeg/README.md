# Fixtures de FFmpeg

Salida real de `ffprobe -print_format json -show_format -show_streams` del FFmpeg 7.1.1 incluido en el APK,
ejecutado en el teléfono de pruebas (24115RA8EG, Android 15) el 2026-11-08. Los archivos de entrada se
generaron en el teléfono con `lavfi` (`testsrc2` y `sine`): no contienen material de terceros.

- `ffprobe-mp4-h264-aac.json`: 20 s, 1280×720 a 30 fps, H.264 High + AAC LC mono 128 kbps.
- `ffprobe-flac.json`: 5 s, FLAC mono 16 bits, etiqueta `title`.
- `ffprobe-mp3.json`: 5 s, MP3 mono 128 kbps.
