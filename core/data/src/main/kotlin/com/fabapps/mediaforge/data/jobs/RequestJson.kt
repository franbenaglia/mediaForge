package com.fabapps.mediaforge.data.jobs

import com.fabapps.mediaforge.domain.jobs.JobRequest
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionRequest
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.VideoContainer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pedidos guardados como JSON. Las clases de acá son una copia explícita del dominio: si el dominio cambia,
 * el formato guardado no cambia sin querer. Al cambiar el formato se sube [VERSION] y se agrega la lectura vieja.
 */
object RequestJson {
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun encode(request: JobRequest): String = json.encodeToString(RequestDto.serializer(), request.toDto())

    /** `null` si el JSON no se puede leer o nombra un formato que esta versión no conoce. */
    fun decode(text: String, version: Int): JobRequest? {
        if (version != VERSION) return null
        return runCatching { json.decodeFromString(RequestDto.serializer(), text).toDomain() }.getOrNull()
    }

    @Serializable
    private sealed interface RequestDto

    @Serializable
    @SerialName("download")
    private data class DownloadDto(
        val jobId: String,
        val sourceUrl: String,
        val selection: SelectionDto,
        val fileName: String,
        val embedMetadata: Boolean,
        val expectedBytes: Long? = null,
    ) : RequestDto

    @Serializable
    @SerialName("conversion")
    private data class ConversionDto(
        val jobId: String,
        val input: String,
        val target: TargetDto,
        val fileName: String,
    ) : RequestDto

    @Serializable
    private sealed interface SelectionDto

    @Serializable
    @SerialName("video")
    private data class VideoSelectionDto(
        val formatIds: List<String>,
        val maxHeight: Int? = null,
        val container: String,
    ) : SelectionDto

    @Serializable
    @SerialName("audio")
    private data class AudioSelectionDto(
        val format: String,
        val bitrateKbps: Int? = null,
        val formatIds: List<String>,
    ) : SelectionDto

    @Serializable
    private sealed interface TargetDto

    @Serializable
    @SerialName("video")
    private data class VideoTargetDto(val container: String, val maxHeight: Int? = null) : TargetDto

    @Serializable
    @SerialName("audio")
    private data class AudioTargetDto(val format: String, val bitrateKbps: Int? = null) : TargetDto

    private fun JobRequest.toDto(): RequestDto = when (this) {
        is JobRequest.Download -> with(request) {
            DownloadDto(jobId, sourceUrl, selection.toDto(), fileName, embedMetadata, expectedBytes)
        }
        is JobRequest.Conversion -> with(request) { ConversionDto(jobId, input, target.toDto(), fileName) }
    }

    private fun DownloadSelection.toDto(): SelectionDto = when (this) {
        is DownloadSelection.Video -> VideoSelectionDto(formatIds, maxHeight, container.name)
        is DownloadSelection.Audio -> AudioSelectionDto(format.name, bitrateKbps, formatIds)
    }

    private fun ConversionTarget.toDto(): TargetDto = when (this) {
        is ConversionTarget.Video -> VideoTargetDto(container.name, maxHeight)
        is ConversionTarget.Audio -> AudioTargetDto(format.name, bitrateKbps)
    }

    private fun RequestDto.toDomain(): JobRequest = when (this) {
        is DownloadDto -> JobRequest.Download(
            DownloadRequest(jobId, sourceUrl, selection.toDomain(), fileName, embedMetadata, expectedBytes),
        )
        is ConversionDto -> JobRequest.Conversion(ConversionRequest(jobId, input, target.toDomain(), fileName))
    }

    private fun SelectionDto.toDomain(): DownloadSelection = when (this) {
        is VideoSelectionDto -> DownloadSelection.Video(formatIds, maxHeight, VideoContainer.valueOf(container))
        is AudioSelectionDto -> DownloadSelection.Audio(AudioFormat.valueOf(format), bitrateKbps, formatIds)
    }

    private fun TargetDto.toDomain(): ConversionTarget = when (this) {
        is VideoTargetDto -> ConversionTarget.Video(VideoContainer.valueOf(container), maxHeight)
        is AudioTargetDto -> ConversionTarget.Audio(AudioFormat.valueOf(format), bitrateKbps)
    }
}
