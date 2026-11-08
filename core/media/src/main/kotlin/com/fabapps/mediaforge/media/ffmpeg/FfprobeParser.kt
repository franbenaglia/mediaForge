package com.fabapps.mediaforge.media.ffmpeg

import com.fabapps.mediaforge.domain.convert.AudioStream
import com.fabapps.mediaforge.domain.convert.LocalMedia
import com.fabapps.mediaforge.domain.convert.VideoStream
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.MediaError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** `ffprobe -print_format json -show_format -show_streams` → [LocalMedia]. Toma la primera pista de cada tipo. */
object FfprobeParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(output: String, displayName: String): Outcome<LocalMedia> {
        val probe = try {
            json.decodeFromString<Probe>(output)
        } catch (e: SerializationException) {
            return Outcome.Failure(
                MediaError.UnsupportedConversion("unreadable ffprobe output: ${e.message?.take(200)}"),
            )
        } catch (e: IllegalArgumentException) {
            return Outcome.Failure(
                MediaError.UnsupportedConversion("unreadable ffprobe output: ${e.message?.take(200)}"),
            )
        }
        // Una portada incrustada (MP3, M4A) aparece como pista de video con attached_pic: no es video.
        val video = probe.streams.firstOrNull { it.codecType == "video" && it.disposition?.attachedPic != 1 }
        val audio = probe.streams.firstOrNull { it.codecType == "audio" }
        if (video == null && audio == null) {
            return Outcome.Failure(MediaError.UnsupportedConversion("no audio or video tracks"))
        }
        return Outcome.Success(
            LocalMedia(
                displayName = displayName,
                sizeBytes = probe.format?.size?.toLongOrNull(),
                durationSeconds = (probe.format?.duration ?: video?.duration ?: audio?.duration)?.toDoubleOrNull()
                    ?.takeIf { it > 0 && it.isFinite() },
                video = video?.codecName?.let { codec ->
                    VideoStream(codec, video.width, video.height, frameRate(video.avgFrameRate ?: video.rFrameRate))
                },
                audio = audio?.codecName?.let { codec ->
                    AudioStream(
                        codec = codec,
                        bitrateKbps = audio.bitRate?.toDoubleOrNull()?.div(1000)?.takeIf { it > 0 },
                        channels = audio.channels,
                        sampleRate = audio.sampleRate?.toIntOrNull(),
                    )
                },
            ),
        )
    }

    /** `30000/1001` → 29,97. `0/0` → nulo. */
    internal fun frameRate(text: String?): Double? {
        val parts = text?.split('/') ?: return null
        val num = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val den = parts.getOrNull(1)?.toDoubleOrNull() ?: 1.0
        return if (num > 0 && den > 0) num / den else null
    }

    @Serializable
    private data class Probe(val streams: List<Stream> = emptyList(), val format: Format? = null)

    @Serializable
    private data class Stream(
        @SerialName("codec_name") val codecName: String? = null,
        @SerialName("codec_type") val codecType: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        @SerialName("avg_frame_rate") val avgFrameRate: String? = null,
        @SerialName("r_frame_rate") val rFrameRate: String? = null,
        @SerialName("bit_rate") val bitRate: String? = null,
        val channels: Int? = null,
        @SerialName("sample_rate") val sampleRate: String? = null,
        val duration: String? = null,
        val disposition: Disposition? = null,
    )

    @Serializable
    private data class Disposition(@SerialName("attached_pic") val attachedPic: Int = 0)

    @Serializable
    private data class Format(val duration: String? = null, val size: String? = null)
}
