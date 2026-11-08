package com.fabapps.mediaforge.ui.converter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabapps.mediaforge.domain.convert.Clip
import com.fabapps.mediaforge.domain.convert.ClipProblem
import com.fabapps.mediaforge.domain.convert.ConversionPlan
import com.fabapps.mediaforge.domain.convert.ConversionPlanner
import com.fabapps.mediaforge.domain.convert.LocalMedia
import com.fabapps.mediaforge.domain.convert.MediaInspector
import com.fabapps.mediaforge.domain.convert.StreamPlan
import com.fabapps.mediaforge.domain.convert.TimeCodes
import com.fabapps.mediaforge.domain.download.AudioOptions
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.jobs.JobQueue
import com.fabapps.mediaforge.domain.jobs.JobRequest
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionRequest
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.VideoContainer
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import com.fabapps.mediaforge.jobs.InputAccess
import com.fabapps.mediaforge.ui.common.WorkState
import com.fabapps.mediaforge.ui.common.workState
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

/** Recorte tal como lo escribe el usuario. Inicio vacío = desde el principio; fin vacío = hasta el final. */
data class TrimInput(
    val enabled: Boolean = false,
    val start: String = "",
    val end: String = "",
    val exact: Boolean = false,
)

enum class TrimError {
    /** El inicio no es un tiempo válido. */
    START,

    /** El fin no es un tiempo válido. */
    END,
    TOO_SHORT,
    BEYOND_END,
}

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
    val trim: TrimInput = TrimInput(),
    val work: WorkState = WorkState.None,
) {
    val media: LocalMedia? get() = (input as? InputState.Ready)?.media

    /** Lo escrito en el recorte, leído: un fragmento, un error o nada (el archivo entero). */
    private val trimResult: Pair<Clip?, TrimError?>
        get() {
            if (!trim.enabled) return null to null
            val start = if (trim.start.isBlank()) 0.0 else TimeCodes.parse(trim.start) ?: return null to TrimError.START
            val end = if (trim.end.isBlank()) null else TimeCodes.parse(trim.end) ?: return null to TrimError.END
            val clip = Clip(start, end, trim.exact)
            return when (clip.problem(media?.durationSeconds)) {
                null -> (if (start == 0.0 && end == null) null else clip) to null
                ClipProblem.INVALID -> null to TrimError.START
                ClipProblem.TOO_SHORT -> null to TrimError.TOO_SHORT
                ClipProblem.BEYOND_END -> null to TrimError.BEYOND_END
            }
        }

    /** Fragmento a convertir; nulo = el archivo entero (o el recorte tiene un error). */
    val clip: Clip? get() = trimResult.first

    val trimError: TrimError? get() = trimResult.second

    /** Duración del fragmento, si se sabe. */
    val clipLength: Double? get() = clip?.lengthSeconds(media?.durationSeconds)

    /** Se corta copiando el video: el inicio cae en un fotograma clave (ADR-020). */
    val cutsAtKeyframe: Boolean get() = clip != null && readyPlan?.video == StreamPlan.Copy

    val target: ConversionTarget
        get() = when (kind) {
            TargetKind.VIDEO -> ConversionTarget.Video(container, maxHeight)
            TargetKind.AUDIO -> ConversionTarget.Audio(audioFormat, audioBitrate)
        }

    /** Plan con los datos del análisis rápido; el conversor lo vuelve a calcular con `ffprobe`. */
    val plan: Outcome<ConversionPlan>? get() = media?.let { ConversionPlanner.plan(it, target, clip) }

    val readyPlan: ConversionPlan? get() = (plan as? Outcome.Success)?.value

    val estimatedBytes: Long?
        get() = media?.let { m -> readyPlan?.let { ConversionPlanner.estimateBytes(m, it, clip) } }

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

    val canConvert: Boolean get() = readyPlan != null && trimError == null && !work.isActive

    val canCancel: Boolean get() = work is WorkState.Running

    val locked: Boolean get() = work.isActive

    companion object {
        val HEIGHTS = listOf(1080, 720, 480, 360)
    }
}

@HiltViewModel
class ConverterViewModel @Inject constructor(
    private val inspector: MediaInspector,
    private val queue: JobQueue,
    private val library: MediaLibrary,
    private val inputAccess: InputAccess,
) : ViewModel() {
    private val _state = MutableStateFlow(ConverterUiState())
    val state: StateFlow<ConverterUiState> = _state.asStateFlow()

    private var inspectJob: Job? = null

    /** Sigue en pantalla el trabajo encolado desde acá; la conversión corre en la cola (ADR-017). */
    private var trackJob: Job? = null
    private var workId: String? = null

    val needsStoragePermission: Boolean get() = library.needsWritePermission()

    /** Archivo elegido en el selector; `null` si se canceló. */
    fun onFilePicked(uri: String?) {
        if (uri == null) return
        inspectJob?.cancel()
        stopTracking()
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

    fun onTrimEnabledChange(enabled: Boolean) = edit { it.copy(trim = it.trim.copy(enabled = enabled)) }

    fun onTrimStartChange(text: String) = edit { it.copy(trim = it.trim.copy(start = text.take(TIME_CHARS))) }

    fun onTrimEndChange(text: String) = edit { it.copy(trim = it.trim.copy(end = text.take(TIME_CHARS))) }

    fun onExactCutChange(exact: Boolean) = edit { it.copy(trim = it.trim.copy(exact = exact)) }

    fun onConvert() {
        val current = _state.value
        val ready = current.input as? InputState.Ready ?: return
        if (!current.canConvert) return
        val request = ConversionRequest(
            jobId = UUID.randomUUID().toString(),
            input = ready.uri,
            target = current.target,
            fileName = current.fileName.ifBlank { ready.media.displayName.substringBeforeLast('.') },
            clip = current.clip,
        )
        _state.update { it.copy(work = WorkState.Running(JobState.Pending)) }
        // La cola puede correrla o reintentarla cuando esta pantalla ya no existe.
        inputAccess.keep(ready.uri)
        trackJob?.cancel()
        trackJob = viewModelScope.launch {
            val job = queue.enqueue(JobRequest.Conversion(request), title = request.fileName)
            workId = job.id
            queue.workState(job.id).collect { work -> _state.update { it.copy(work = work) } }
        }
    }

    /** Termina FFmpeg y borra la copia y la salida parcial (ADR-005). No durante el guardado. */
    fun onCancel() {
        if (!_state.value.canCancel) return
        val id = workId ?: return
        viewModelScope.launch { queue.cancel(id) }
    }

    fun onStoragePermissionDenied() {
        _state.update { it.copy(work = WorkState.PermissionDenied) }
    }

    /** Volver a elegir otro archivo. */
    fun onClear() {
        inspectJob?.cancel()
        stopTracking()
        _state.value = ConverterUiState()
    }

    /** Deja de seguir la conversión en pantalla; sigue en la cola y se ve en Descargas. */
    private fun stopTracking() {
        trackJob?.cancel()
        trackJob = null
        workId = null
    }

    /** Cambios de opciones: no mientras se convierte; borran el resultado anterior. */
    private fun edit(change: (ConverterUiState) -> ConverterUiState) {
        if (_state.value.locked) return
        _state.update { change(it).let { next -> if (next != it) next.copy(work = WorkState.None) else next } }
    }

    private companion object {
        /** `1:02:03.500` y algo de margen. */
        const val TIME_CHARS = 16
    }
}
