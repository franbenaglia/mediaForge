package com.fabapps.mediaforge.domain.convert

/**
 * Un archivo local, descrito por `ffprobe` o por `MediaExtractor`. Los códecs usan los nombres de
 * FFmpeg (`h264`, `hevc`, `vp9`, `av1`, `aac`, `mp3`, `opus`, `vorbis`, `flac`, `pcm_s16le`…).
 */
data class LocalMedia(
    val displayName: String,
    val sizeBytes: Long? = null,
    val durationSeconds: Double? = null,
    val video: VideoStream? = null,
    val audio: AudioStream? = null,
)

data class VideoStream(val codec: String, val width: Int? = null, val height: Int? = null, val fps: Double? = null)

data class AudioStream(
    val codec: String,
    val bitrateKbps: Double? = null,
    val channels: Int? = null,
    val sampleRate: Int? = null,
)
