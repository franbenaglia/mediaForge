# Changelog

All notable changes to MediaForge. Versions follow [Semantic Versioning](https://semver.org).

## [1.0.0] - 2026-11-09

First public release.

### Downloads
- Paste or share a link (Android share sheet) and see the real formats the site offers, with sizes.
- Video in MP4 up to the chosen quality, or audio only: MP3, M4A, Opus, OGG (Vorbis), WAV or FLAC, with bitrate choice and copy without re-encoding when possible.
- Embedded subtitles: the site's manual subtitles or the automatic ones in the original language.
- Public playlists: pick the videos (up to 200) and download them all with the same mode and quality.
- Title, artist and cover art embedded in audio files.
- Tested with YouTube, Dailymotion and TikTok; other sites supported by yt-dlp may work.

### Converter (offline)
- Convert local files between video and audio formats, remuxing without quality loss when possible.
- Trim (fast or frame-accurate), extract a frame as JPG or PNG, convert up to 50 files at once, compress video by quality level.

### Queue and files
- Background queue with real progress, cancel, automatic retries on network errors, and a "Wi‑Fi only" option.
- Saves to the gallery (Movies, Music, Pictures) or to a folder you choose. Never overwrites a file.
- Persistent history with search, filters, download again and delete file.

### Engine
- yt-dlp updates itself (at most once a week, can be turned off) with OpenPGP signature and SHA-256 verification; the previous version is kept.

### Quality
- English and Spanish, light and dark theme, Material You colors, TalkBack and large text support.
- No accounts, no ads, no analytics, no Google Play Services. Nothing leaves the phone except the requests to the sites you choose and to GitHub for engine updates.

### Known limitations
- No real pause: cancelling and starting again restarts the download.
- Content that needs signing in, DRM-protected content and paid content are not supported, by design.
- Android 8.0 or later, on arm64 or x86_64 devices.

## Development history (before 1.0.0)

MediaForge was built in stages between 2026-11-06 and 2026-11-09. Each stage is recorded in `harness/` and in the architecture decision records (`docs/DECISIONS.md`).

| Stage | Content |
|---|---|
| 0 | Technical feasibility: yt-dlp, FFmpeg and Python on Android |
| 1–2 | Project structure and base interface |
| 3–5 | Extraction engine, video download, audio extraction |
| 6 | Offline converter |
| 7 | Background job queue |
| 8 | Android share sheet |
| 9 | History and settings |
| 10 | Trim, frames, batch conversion, compression, subtitles, playlists |
| 11 | Quality: R8, security review, license audit, instrumented tests, StrictMode, accessibility |
| 12 | Distribution |
