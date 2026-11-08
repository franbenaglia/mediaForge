package com.fabapps.mediaforge.media.ffmpeg

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.net.toUri
import com.fabapps.mediaforge.domain.convert.AudioStream
import com.fabapps.mediaforge.domain.convert.LocalMedia
import com.fabapps.mediaforge.domain.convert.MediaInspector
import com.fabapps.mediaforge.domain.convert.VideoStream
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.MediaError
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [MediaInspector] con `MediaExtractor`: lee solo los encabezados, sin copiar. Es una vista previa;
 * el conversor vuelve a analizar con `ffprobe` después de copiar (ADR-016).
 */
class ExtractorInspector(
    private val context: Context,
    private val inputs: InputSource,
    private val io: CoroutineDispatcher,
) : MediaInspector {

    override suspend fun inspect(input: String): Outcome<LocalMedia> = withContext(io) {
        val info = inputs.describe(input)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, input.toUri(), null)
            var video: VideoStream? = null
            var audio: AudioStream? = null
            var durationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                durationUs = maxOf(durationUs, format.long(MediaFormat.KEY_DURATION) ?: 0)
                when {
                    mime.startsWith("video/") && video == null -> video = VideoStream(
                        codec = MimeCodecs.video(mime),
                        width = format.int(MediaFormat.KEY_WIDTH),
                        height = format.int(MediaFormat.KEY_HEIGHT),
                        fps = format.number(MediaFormat.KEY_FRAME_RATE),
                    )
                    mime.startsWith("audio/") && audio == null -> audio = AudioStream(
                        codec = MimeCodecs.audio(mime, format.int(MediaFormat.KEY_PCM_ENCODING)),
                        bitrateKbps = format.int(MediaFormat.KEY_BIT_RATE)?.takeIf { it > 0 }?.div(1000.0),
                        channels = format.int(MediaFormat.KEY_CHANNEL_COUNT),
                        sampleRate = format.int(MediaFormat.KEY_SAMPLE_RATE),
                    )
                }
            }
            if (video == null && audio == null) {
                return@withContext Outcome.Failure(MediaError.UnsupportedConversion("no audio or video tracks"))
            }
            Outcome.Success(
                LocalMedia(
                    displayName = info?.displayName ?: input.toUri().lastPathSegment ?: "media",
                    sizeBytes = info?.sizeBytes,
                    durationSeconds = durationUs.takeIf { it > 0 }?.div(1_000_000.0),
                    video = video,
                    audio = audio,
                ),
            )
        } catch (e: IOException) {
            Outcome.Failure(MediaError.UnsupportedConversion(e.message))
        } catch (e: IllegalArgumentException) {
            Outcome.Failure(MediaError.UnsupportedConversion(e.message))
        } catch (e: SecurityException) {
            Outcome.Failure(MediaError.UnsupportedConversion(e.message))
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.int(key: String): Int? = if (containsKey(key)) {
        runCatching {
            getInteger(key)
        }.getOrNull()
    } else {
        null
    }

    private fun MediaFormat.long(key: String): Long? = if (containsKey(key)) {
        runCatching {
            getLong(key)
        }.getOrNull()
    } else {
        null
    }

    /** La tasa de cuadros puede venir como entero o como float según el contenedor. */
    private fun MediaFormat.number(key: String): Double? = if (!containsKey(key)) {
        null
    } else {
        runCatching { getInteger(key).toDouble() }.recoverCatching { getFloat(key).toDouble() }.getOrNull()
    }
}

/** MIME de Android → nombre de códec de FFmpeg. Lo desconocido queda como el MIME (no coincide con nada). */
object MimeCodecs {
    fun video(mime: String): String = when (mime) {
        "video/avc" -> "h264"
        "video/hevc" -> "hevc"
        "video/x-vnd.on2.vp8" -> "vp8"
        "video/x-vnd.on2.vp9" -> "vp9"
        "video/av01" -> "av1"
        "video/mp4v-es" -> "mpeg4"
        "video/3gpp" -> "h263"
        "video/mpeg2" -> "mpeg2video"
        else -> mime
    }

    /** `audio/raw` es PCM; con codificación 2 (16 bits) es lo mismo que produce WAV. */
    fun audio(mime: String, pcmEncoding: Int? = null): String = when (mime) {
        "audio/mp4a-latm" -> "aac"
        "audio/mpeg" -> "mp3"
        "audio/opus" -> "opus"
        "audio/vorbis" -> "vorbis"
        "audio/flac" -> "flac"
        "audio/ac3" -> "ac3"
        "audio/eac3" -> "eac3"
        "audio/alac" -> "alac"
        "audio/raw" -> if (pcmEncoding == null || pcmEncoding == PCM_16BIT) "pcm_s16le" else "pcm"
        else -> mime
    }

    private const val PCM_16BIT = 2
}
