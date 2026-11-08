package com.fabapps.mediaforge.ui.converter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabapps.mediaforge.domain.convert.ConversionPlan
import com.fabapps.mediaforge.domain.convert.ConversionPlanner
import com.fabapps.mediaforge.domain.convert.LocalMedia
import com.fabapps.mediaforge.domain.convert.MediaInspector
import com.fabapps.mediaforge.domain.convert.StreamPlan
import com.fabapps.mediaforge.domain.download.AudioOptions
import com.fabapps.mediaforge.domain.engine.MediaConverter
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionRequest
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.model.VideoContainer
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import com.fabapps.mediaforge.ui.common.WorkState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TargetKind { VIDEO, AUDIO }

sealed interface InputState {
    data object None : InputState

    data object Inspecting : InputState

    data class Ready(val uri: String, val media: LocalMedia) : InputState

    data class Failed(val error: MediaError) : InputState
}

data class ConverterUiState(
    val input: InputState = InputState.None,
    val kind: TargetKind = TargetKind.AUDIO,
    val container: VideoContainer = VideoContainer.MP4,
    /** Altura máxima; nulo = la original. */
    val maxHeight: Int? = null,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    /** Nulo = automático. */
    val audioBitrate: Int? = null,
    val fileName: String = "",
    val work: WorkState = WorkState.None,
) {
    val media: LocalMedia? get() = (input as? InputState.Ready)?.media

    val target: ConversionTarget
        get() = when (kind) {
            TargetKind.VIDEO -> ConversionTarget.Video(container, maxHeight)
            TargetKind.AUDIO -> ConversionTarget.Audio(audioFormat, audioBitrate)
        }

    /** Plan con los datos del análisis rápido; el conversor lo vuelve a calcular con `ffprobe`. */
    val plan: Outcome<ConversionPlan>? get() = media?.let { ConversionPlanner.plan(it, target) }

    val readyPlan: ConversionPlan? get() = (plan as? Outcome.Success)?.value

    val estimatedBytes: Long? get() = media?.let { m -> readyPlan?.let { ConversionPlanner.estimateBytes(m, it) } }

    /** Alturas menores que la del archivo, para reducir la resolución. */
    val heightOptions: List<Int> get() = HEIGHTS.filter { h -> (media?.video?.height ?: 0) > h }

    val bitrates: List<Int> get() = AudioOptions.bitratesFor(audioFormat)

    val autoBitrate: Int? get() = AudioOptions.autoBitrate(audioFormat, media?.audio?.bitrateKbps)

    /** El bitrate elegido supera al original: no mejora la calidad (main.md §4.2). */
    val bitrateExceedsSource: Boolean
        get() {
            val encode = readyPlan?.audio as? StreamPlan.Encode ?: return false
            val source = media?.audio?.bitrateKbps ?: return false
            return kind == TargetKind.AUDIO && (encode.bitrateKbps ?: 0) > source + 8
        }

    val canConvert: Boolean get() = readyPlan != null && !work.isActive

    val canCancel: Boolean get() = work is WorkState.Running

    val locked: Boolean get() = work.isActive

    companion object {
        val HEIGHTS = listOf(1080, 720, 480, 360)
    }
}

@HiltViewModel
class ConverterViewModel @Inject constructor(
    private val inspector: MediaInspector,
    private val converter: MediaConverter,
    private val library: MediaLibrary,
) : ViewModel() {
    private val _state = MutableStateFlow(ConverterUiState())
    val state: StateFlow<ConverterUiState> = _state.asStateFlow()

    private var inspectJob: Job? = null
    private var workJob: Job? = null

    val needsStoragePermission: Boolean get() = library.needsWritePermission()

    /** Archivo elegido en el selector; `null` si se canceló. */
    fun onFilePicked(uri: String?) {
        if (uri == null || _state.value.locked) return
        inspectJob?.cancel()
        _state.value = ConverterUiState(input = InputState.Inspecting)
        inspectJob = viewModelScope.launch {
            when (val result = inspector.inspect(uri)) {
                is Outcome.Success -> _state.update {
                    val media = result.value
                    it.copy(
                        input = InputState.Ready(uri, media),
                        // Un archivo sin video solo puede ir a audio; con video, lo habitual es cambiar de formato de video.
                        kind = if (media.video == null) TargetKind.AUDIO else TargetKind.VIDEO,
                        fileName = media.displayName.substringBeforeLast('.').ifBlank { media.displayName },
                    )
                }
                is Outcome.Failure -> _state.update { it.copy(input = InputState.Failed(result.error)) }
            }
        }
    }

    fun onKindChange(kind: TargetKind) = edit { it.copy(kind = kind) }

    fun onContainerChange(container: VideoContainer) = edit { it.copy(container = container) }

    fun onMaxHeightChange(height: Int?) = edit {
        if (height == null || height in it.heightOptions) it.copy(maxHeight = height) else it
    }

    fun onAudioFormatChange(format: AudioFormat) = edit { it.copy(audioFormat = format, audioBitrate = null) }

    fun onBitrateChange(kbps: Int?) = edit {
        if (kbps == null || kbps in it.bitrates) it.copy(audioBitrate = kbps) else it
    }

    fun onFileNameChange(name: String) = edit { it.copy(fileName = name) }

    fun onConvert() {
        val current = _state.value
        val ready = current.input as? InputState.Ready ?: return
        if (!current.canConvert) return
        val request = ConversionRequest(
            jobId = UUID.randomUUID().toString(),
            input = ready.uri,
            target = current.target,
            fileName = current.fileName.ifBlank { ready.media.displayName.substringBeforeLast('.') },
        )
        _state.update { it.copy(work = WorkState.Running(JobState.Pending)) }
        workJob = viewModelScope.launch {
            converter.convert(request).collect { job ->
                when (job) {
                    is JobState.Completed -> save(job.outputs.first())
                    is JobState.Failed -> _state.update { it.copy(work = WorkState.Failed(job.error)) }
                    JobState.Cancelled -> _state.update { it.copy(work = WorkState.Cancelled) }
                    else -> _state.update { it.copy(work = WorkState.Running(job)) }
                }
            }
        }
    }

    /** Termina FFmpeg y borra la copia y la salida parcial (ADR-005). No durante el guardado. */
    fun onCancel() {
        if (!_state.value.canCancel) return
        workJob?.cancel()
        workJob = null
        _state.update { it.copy(work = WorkState.Cancelled) }
    }

    fun onStoragePermissionDenied() {
        _state.update { it.copy(work = WorkState.PermissionDenied) }
    }

    /** Volver a elegir otro archivo. */
    fun onClear() {
        if (_state.value.locked) return
        inspectJob?.cancel()
        _state.value = ConverterUiState()
    }

    private suspend fun save(file: OutputFile) {
        _state.update { it.copy(work = WorkState.Saving) }
        val result = library.save(file)
        _state.update {
            it.copy(
                work = when (result) {
                    is Outcome.Success -> WorkState.Saved(result.value)
                    is Outcome.Failure -> WorkState.Failed(result.error)
                },
            )
        }
    }

    /** Cambios de opciones: no mientras se convierte; borran el resultado anterior. */
    private fun edit(change: (ConverterUiState) -> ConverterUiState) {
        if (_state.value.locked) return
        _state.update { change(it).let { next -> if (next != it) next.copy(work = WorkState.None) else next } }
    }
}
