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
import com.fabapps.mediaforge.domain.model.ImageFormat
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.VideoContainer
import com.fabapps.mediaforge.domain.model.VideoQuality
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

enum class TargetKind {
    VIDEO,
    AUDIO,

    /** Un fotograma del video. */
    IMAGE,
}

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

    /** Varios archivos, todos al mismo destino (ADR-020). [truncated]: se eligieron más de [MAX_BATCH]. */
    data class Batch(val files: List<BatchFile>, val truncated: Boolean = false) : InputState {
        companion object {
            const val MAX_BATCH = 50
        }
    }
}

/** Un archivo de una conversión de varios: se está leyendo, está listo ([media]) o no se pudo leer ([error]). */
data class BatchFile(val uri: String, val media: LocalMedia? = null, val error: MediaError? = null) {
    val inspecting: Boolean get() = media == null && error == null
}

data class ConverterUiState(
    val input: InputState = InputState.None,
    val kind: TargetKind = TargetKind.AUDIO,
    val container: VideoContainer = VideoContainer.MP4,
    /** Altura máxima; nulo = la original. */
    val maxHeight: Int? = null,
    val videoQuality: VideoQuality = VideoQuality.ORIGINAL,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    /** Nulo = automático. */
    val audioBitrate: Int? = null,
    val fileName: String = "",
    val trim: TrimInput = TrimInput(),
    /** Instante del fotograma, como lo escribe el usuario; vacío = el principio. */
    val frameTime: String = "",
    val imageFormat: ImageFormat = ImageFormat.JPG,
    val work: WorkState = WorkState.None,
    /** Cuántas conversiones se encolaron con estas opciones (varios archivos). */
    val batchQueued: Int? = null,
) {
    val media: LocalMedia? get() = (input as? InputState.Ready)?.media

    val batch: InputState.Batch? get() = input as? InputState.Batch

    /** Plan de un archivo de la lista con el destino elegido; nulo mientras se lee. */
    fun planFor(file: BatchFile): Outcome<ConversionPlan>? =
        file.error?.let { Outcome.Failure(it) } ?: file.media?.let { ConversionPlanner.plan(it, target) }

    /** Los archivos de la lista que se pueden convertir al destino elegido. */
    val batchReady: List<BatchFile> get() = batch?.files.orEmpty().filter { planFor(it) is Outcome.Success }

    /** El archivo, o alguno de la lista, tiene video. */
    val anyVideo: Boolean
        get() = media?.video != null || batch?.files.orEmpty().any { it.media?.video != null }

    /** Lo escrito en el recorte, leído: un fragmento, un error o nada (el archivo entero). */
    private val trimResult: Pair<Clip?, TrimError?>
        get() {
            if (!trim.enabled || kind == TargetKind.IMAGE) return null to null
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

    /** Instante del fotograma leído; nulo si no es un tiempo válido. */
    private val frameAt: Double? get() = if (frameTime.isBlank()) 0.0 else TimeCodes.parse(frameTime)

    /** Error del instante del fotograma (solo [TrimError.START] o [TrimError.BEYOND_END]). */
    val frameError: TrimError?
        get() {
            if (kind != TargetKind.IMAGE) return null
            val at = frameAt ?: return TrimError.START
            val duration = media?.durationSeconds
            return if (duration != null && at >= duration) TrimError.BEYOND_END else null
        }

    /** Duración del fragmento, si se sabe. */
    val clipLength: Double? get() = clip?.lengthSeconds(media?.durationSeconds)

    /** Se corta copiando el video: el inicio cae en un fotograma clave (ADR-020). */
    val cutsAtKeyframe: Boolean get() = clip != null && readyPlan?.video == StreamPlan.Copy

    val target: ConversionTarget
        get() = when (kind) {
            TargetKind.VIDEO -> ConversionTarget.Video(container, maxHeight, videoQuality)
            TargetKind.AUDIO -> ConversionTarget.Audio(audioFormat, audioBitrate)
            // Con un instante inválido se planea igual (para mostrar el resto); no se puede convertir.
            TargetKind.IMAGE -> ConversionTarget.Image(if (frameError == null) frameAt ?: 0.0 else 0.0, imageFormat)
        }

    /** Plan con los datos del análisis rápido; el conversor lo vuelve a calcular con `ffprobe`. */
    val plan: Outcome<ConversionPlan>? get() = media?.let { ConversionPlanner.plan(it, target, clip) }

    val readyPlan: ConversionPlan? get() = (plan as? Outcome.Success)?.value

    val estimatedBytes: Long?
        get() = media?.let { m -> readyPlan?.let { ConversionPlanner.estimateBytes(m, it, clip) } }

    /** Alturas menores que la del archivo, para reducir la resolución. */
    val heightOptions: List<Int>
        get() {
            val tallest = media?.video?.height ?: batch?.files.orEmpty().maxOfOrNull { it.media?.video?.height ?: 0 }
            return HEIGHTS.filter { h -> (tallest ?: 0) > h }
        }

    val bitrates: List<Int> get() = AudioOptions.bitratesFor(audioFormat)

    val autoBitrate: Int? get() = AudioOptions.autoBitrate(audioFormat, media?.audio?.bitrateKbps)

    /** El bitrate elegido supera al original: no mejora la calidad (main.md §4.2). */
    val bitrateExceedsSource: Boolean
        get() {
            val encode = readyPlan?.audio as? StreamPlan.Encode ?: return false
            val source = media?.audio?.bitrateKbps ?: return false
            return kind == TargetKind.AUDIO && (encode.bitrateKbps ?: 0) > source + 8
        }

    val canConvert: Boolean
        get() {
            val files = batch?.files
            if (files != null) {
                return kind != TargetKind.IMAGE &&
                    files.none { it.inspecting } &&
                    batchReady.isNotEmpty() &&
                    batchQueued == null
            }
            return readyPlan != null && trimError == null && frameError == null && !work.isActive
        }

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

    /** El usuario eligió el tipo de destino a mano: la lista de varios no lo cambia al terminar de leerse. */
    private var kindChosen = false

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

    /** Varios archivos elegidos en el selector. Con uno solo, es el flujo de siempre. */
    fun onFilesPicked(uris: List<String>) {
        val distinct = uris.distinct()
        if (distinct.size <= 1) return onFilePicked(distinct.firstOrNull())
        inspectJob?.cancel()
        stopTracking()
        kindChosen = false
        val chosen = distinct.take(InputState.Batch.MAX_BATCH)
        _state.value = ConverterUiState(
            input = InputState.Batch(chosen.map { BatchFile(it) }, truncated = distinct.size > chosen.size),
        )
        inspectJob = viewModelScope.launch {
            chosen.forEach { uri ->
                val result = inspector.inspect(uri)
                _state.update { state ->
                    val batch = state.batch ?: return@update state
                    val files = batch.files.map { file ->
                        if (file.uri != uri) {
                            file
                        } else {
                            when (result) {
                                is Outcome.Success -> file.copy(media = result.value)
                                is Outcome.Failure -> file.copy(error = result.error)
                            }
                        }
                    }
                    state.copy(input = batch.copy(files = files))
                }
            }
            // Si todos los que se leyeron tienen video, lo habitual es convertir a otro formato de video.
            _state.update { state ->
                val read = state.batch?.files.orEmpty().mapNotNull { it.media }
                if (!kindChosen && read.isNotEmpty() && read.all { it.video != null }) {
                    state.copy(kind = TargetKind.VIDEO)
                } else {
                    state
                }
            }
        }
    }

    fun onKindChange(kind: TargetKind) {
        // Un fotograma depende del instante de cada archivo: no se ofrece para varios.
        if (kind == TargetKind.IMAGE && _state.value.batch != null) return
        kindChosen = true
        edit { it.copy(kind = kind) }
    }

    fun onContainerChange(container: VideoContainer) = edit { it.copy(container = container) }

    fun onVideoQualityChange(quality: VideoQuality) = edit { it.copy(videoQuality = quality) }

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

    fun onFrameTimeChange(text: String) = edit { it.copy(frameTime = text.take(TIME_CHARS)) }

    fun onImageFormatChange(format: ImageFormat) = edit { it.copy(imageFormat = format) }

    fun onConvert() {
        val current = _state.value
        if (current.batch != null) return convertBatch(current)
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

    /** Un trabajo por archivo compatible, todos con el mismo destino. Se siguen en Descargas. */
    private fun convertBatch(current: ConverterUiState) {
        if (!current.canConvert) return
        val files = current.batchReady
        val target = current.target
        files.forEach { inputAccess.keep(it.uri) }
        _state.update { it.copy(batchQueued = files.size) }
        viewModelScope.launch {
            files.forEach { file ->
                val display = file.media?.displayName.orEmpty()
                val name = display.substringBeforeLast('.').ifBlank { display }
                val request = ConversionRequest(UUID.randomUUID().toString(), file.uri, target, name)
                queue.enqueue(JobRequest.Conversion(request), title = name)
            }
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
        _state.update {
            change(it).let { next ->
                if (next != it) next.copy(work = WorkState.None, batchQueued = null) else next
            }
        }
    }

    private companion object {
        /** `1:02:03.500` y algo de margen. */
        const val TIME_CHARS = 16
    }
}
