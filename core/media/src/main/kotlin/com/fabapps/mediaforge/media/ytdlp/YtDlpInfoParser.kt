package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.MediaFormat
import com.fabapps.mediaforge.domain.model.MediaInfo
import com.fabapps.mediaforge.domain.model.SourceSite
import com.fabapps.mediaforge.domain.model.SubtitleTrack
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Convierte la salida de `yt-dlp --dump-json` en [MediaInfo].
 *
 * Reemplaza a `VideoInfo` de youtubedl-android, que solo mapea `requested_formats` (FEASIBILITY §9).
 * Todo campo es opcional salvo `id`: faltan seguido según el sitio.
 */
object YtDlpInfoParser {
    const val MAX_DESCRIPTION_CHARS = 5_000
    const val MAX_SUBTITLES = 60

    /** Claves de `subtitles` que no son subtítulos (el chat de un directo). */
    private val NOT_SUBTITLES = setOf("live_chat")

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val uploadDateFormat = DateTimeFormatter.BASIC_ISO_DATE

    /** Nombres para mostrar de los sitios prioritarios (ADR-008). El resto usa la clave del extractor. */
    private val siteNames = mapOf(
        "Youtube" to "YouTube",
        "Dailymotion" to "Dailymotion",
        "TikTok" to "TikTok",
        "Vimeo" to "Vimeo",
        "Twitch" to "Twitch",
        "Reddit" to "Reddit",
        "Twitter" to "X",
        "Instagram" to "Instagram",
        "Facebook" to "Facebook",
    )

    fun parse(output: String): Outcome<MediaInfo> {
        val dto = try {
            json.decodeFromString<InfoDto>(output.trim())
        } catch (e: SerializationException) {
            return Outcome.Failure(MediaError.Unknown(detail = "invalid yt-dlp JSON: ${e.message?.take(200)}"))
        } catch (e: IllegalArgumentException) {
            return Outcome.Failure(MediaError.Unknown(detail = "invalid yt-dlp JSON: ${e.message?.take(200)}"))
        }

        if (dto.type == "playlist" || dto.type == "multi_video") {
            return Outcome.Failure(MediaError.UnsupportedSite(detail = "playlists are not supported yet"))
        }
        val id = dto.id?.takeIf { it.isNotBlank() }
            ?: return Outcome.Failure(MediaError.Unknown(detail = "yt-dlp JSON without id"))

        val extractorKey = dto.extractorKey ?: dto.extractor ?: "Generic"
        return Outcome.Success(
            MediaInfo(
                id = id,
                sourceUrl = dto.webpageUrl ?: dto.originalUrl.orEmpty(),
                title = dto.title?.takeIf { it.isNotBlank() } ?: dto.fulltitle?.takeIf { it.isNotBlank() } ?: id,
                site = SourceSite(extractorKey = extractorKey, displayName = siteNames[extractorKey] ?: extractorKey),
                uploader = dto.uploader ?: dto.channel ?: dto.creator,
                durationSeconds = dto.duration?.takeIf { it >= 0 }?.toLong(),
                thumbnailUrl = dto.thumbnail?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                uploadDate = dto.uploadDate?.let(::parseDate),
                description = dto.description?.takeIf { it.isNotBlank() }?.take(MAX_DESCRIPTION_CHARS),
                formats = dto.formats.orEmpty().mapNotNull(::toFormat),
                subtitles = subtitles(dto),
            ),
        )
    }

    /**
     * Manuales primero (ordenados por nombre) y después el automático del idioma original. YouTube
     * ofrece además traducciones automáticas a ~150 idiomas: esas no se listan (ADR-021).
     */
    private fun subtitles(dto: InfoDto): List<SubtitleTrack> {
        val manual = entries(dto.subtitles)
            .filterKeys { it !in NOT_SUBTITLES }
            .map { (lang, name) -> SubtitleTrack(lang, name) }
            .sortedBy { (it.name ?: it.language).lowercase() }
        val automatic = entries(dto.automaticCaptions)
        val original = dto.language
        // `xx-orig` es el reconocimiento de voz sin traducir; si no está, el del idioma del video.
        val key = "$original-orig".takeIf { original != null && it in automatic }
            ?: original?.takeIf { it in automatic }
            ?: automatic.keys.firstOrNull { it.endsWith("-orig") }
        val base = key?.removeSuffix("-orig")
        val auto = key
            ?.takeIf { manual.none { track -> track.language == base || track.language == key } }
            ?.let { SubtitleTrack(it, automatic[it], automatic = true) }
        return (manual + listOfNotNull(auto)).take(MAX_SUBTITLES)
    }

    /** Idioma → nombre (puede faltar), solo con claves válidas y al menos un archivo. */
    private fun entries(map: Map<String, JsonElement>?): Map<String, String?> = map.orEmpty()
        .filterKeys { SubtitleTrack.LANGUAGE.matches(it) }
        .mapNotNull { (lang, value) ->
            val files = (value as? JsonArray)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = files.firstNotNullOfOrNull { file ->
                ((file as? JsonObject)?.get("name") as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?.takeIf { it.isNotBlank() }
            }
            lang to name
        }
        .toMap()

    private fun toFormat(f: FormatDto): MediaFormat? {
        val formatId = f.formatId ?: return null
        // Storyboards (miniaturas de previsualización) y otros formatos sin contenido descargable.
        if (f.protocol == "mhtml" || f.ext == "mhtml") return null
        val videoCodec = codec(f.vcodec)
        val audioCodec = codec(f.acodec)
        if (f.vcodec == "none" && f.acodec == "none") return null

        val exact = f.filesize?.takeIf { it > 0 }?.toLong()
        val approx = f.filesizeApprox?.takeIf { it > 0 }?.toLong()
        return MediaFormat(
            formatId = formatId,
            container = f.ext ?: "unknown",
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            width = f.width?.takeIf { it > 0 }?.toInt(),
            height = f.height?.takeIf { it > 0 }?.toInt(),
            fps = f.fps?.takeIf { it > 0 },
            bitrateKbps = f.tbr?.takeIf { it > 0 },
            fileSizeBytes = exact ?: approx,
            fileSizeIsApproximate = exact == null && approx != null,
        )
    }

    /** `"none"` significa que la pista no existe; vacío o ausente, que no se sabe. */
    private fun codec(value: String?): String? = value?.takeUnless { it == "none" || it.isBlank() }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value, uploadDateFormat)
    } catch (_: DateTimeParseException) {
        null
    }

    @Serializable
    private data class InfoDto(
        @SerialName("_type") val type: String? = null,
        val id: String? = null,
        val title: String? = null,
        val fulltitle: String? = null,
        @SerialName("webpage_url") val webpageUrl: String? = null,
        @SerialName("original_url") val originalUrl: String? = null,
        @SerialName("extractor_key") val extractorKey: String? = null,
        val extractor: String? = null,
        val uploader: String? = null,
        val channel: String? = null,
        val creator: String? = null,
        val duration: Double? = null,
        val thumbnail: String? = null,
        @SerialName("upload_date") val uploadDate: String? = null,
        val description: String? = null,
        val formats: List<FormatDto>? = null,
        // Como JsonElement: un valor raro en los subtítulos no puede hacer fallar todo el análisis.
        val subtitles: Map<String, JsonElement>? = null,
        @SerialName("automatic_captions") val automaticCaptions: Map<String, JsonElement>? = null,
        val language: String? = null,
    )

    @Serializable
    private data class FormatDto(
        @SerialName("format_id") val formatId: String? = null,
        val ext: String? = null,
        val protocol: String? = null,
        val vcodec: String? = null,
        val acodec: String? = null,
        // Numéricos como Double: según el extractor llegan enteros o con decimales.
        val width: Double? = null,
        val height: Double? = null,
        val fps: Double? = null,
        val tbr: Double? = null,
        val filesize: Double? = null,
        @SerialName("filesize_approx") val filesizeApprox: Double? = null,
    )
}
