# MediaForge

Android app to download, extract and convert media **on the device**: no servers, no accounts, no ads.
Built with Kotlin, Jetpack Compose, [yt-dlp](https://github.com/yt-dlp/yt-dlp) and [FFmpeg](https://ffmpeg.org).

> **Status:** early development (stage 1 of 12). Not usable yet.

## Planned features

- Paste or share a link, see the real formats available, and download video or audio only.
- Audio extraction to MP3, M4A, Opus, FLAC and more.
- Offline converter for local files (remux without quality loss when possible).
- Download queue with progress, cancel and history.

MediaForge is intended for content you own, that is in the public domain, or that the platform and rights holders allow you to download. It does not bypass DRM, paywalls, logins or other access controls.

## Build

Requirements: JDK 17 and the Android SDK (platform 37).

```bash
./scripts/verify.sh      # build, unit tests, lint and ktlint
./gradlew assembleDebug  # debug APK in app/build/outputs/apk/debug/
```

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): modules and data flow
- [docs/DECISIONS.md](docs/DECISIONS.md): architecture decision records
- [docs/FEASIBILITY.md](docs/FEASIBILITY.md): technical feasibility study (in Spanish)

## License

[GPL-3.0](LICENSE). MediaForge bundles yt-dlp (Unlicense), Python (PSF), QuickJS (MIT) and FFmpeg built with GPL components, through [youtubedl-android](https://github.com/yausername/youtubedl-android) (GPL-3.0).
