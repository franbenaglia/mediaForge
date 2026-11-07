package com.fabapps.mediaforge.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabapps.mediaforge.domain.download.AudioOption
import com.fabapps.mediaforge.domain.download.AudioOptions
import com.fabapps.mediaforge.domain.download.VideoOption
import com.fabapps.mediaforge.domain.download.VideoOptions
import com.fabapps.mediaforge.domain.engine.ExtractorEngine
import com.fabapps.mediaforge.domain.engine.MediaDownloader
import com.fabapps.mediaforge.domain.engine.MediaExtractor
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.MediaInfo
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import com.fabapps.mediaforge.domain.url.UrlCheck
import com.fabapps.mediaforge.domain.url.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val input: String = "",
    val status: InputStatus = InputStatus.EMPTY,
    /** Motivo a mostrar; solo cuando [status] es [InputStatus.INVALID]. */
    val invalidReason: UrlCheck.Reason? = null,
    val analysis: AnalysisState = AnalysisState.Idle,
    /** Calidades del recurso analizado; la primera es la mejor. */
    val options: List<VideoOption> = emptyList(),
    val selectedOption: Int = 0,
    val mode: DownloadMode = DownloadMode.VIDEO,
    /** Formatos de audio (MP3, M4A y Opus primero). */
    val audioOptions: List<AudioOption> = emptyList(),
    val selectedAudio: Int = 0,
    /** Bitrate elegido; nulo = automático. */
    val audioBitrate: Int? = null,
    /** Tamaño del audio de origen, para estimar una copia sin recodificar. */
    val audioSourceBytes: Long? = null,
    /** Nombre del archivo sin extensión; arranca con el título. */
    val fileName: String = "",
    val download: DownloadState = DownloadState.None,
) {
    /** Mientras se descarga, el enlace no cambia (la tarjeta y la descarga son del mismo recurso). */
    val inputLocked: Boolean get() = download.isActive

    val canAnalyze: Boolean
        get() = status == InputStatus.READY &&
            !inputLocked &&
            analysis !is AnalysisState.Loading &&
            analysis !is AnalysisState.UpdatingEngine

    val canDownload: Boolean
        get() = analysis is AnalysisState.Loaded &&
            !download.isActive &&
            (if (mode == DownloadMode.VIDEO) options.isNotEmpty() else audioOptions.isNotEmpty())

    val audioOption: AudioOption? get() = audioOptions.getOrNull(selectedAudio)

    /** Bitrate que se va a usar: el elegido o el automático; nulo si se copia o es sin pérdida. */
    val effectiveBitrate: Int? get() = audioOption?.selection(audioBitrate)?.bitrateKbps

    /** El bitrate elegido supera al del original: avisar que no mejora la calidad (main.md §4.2). */
    val bitrateExceedsSource: Boolean get() = effectiveBitrate?.let { audioOption?.exceedsSource(it) } == true

    val audioEstimatedBytes: Long? get() = audioOption?.estimatedBytes(audioBitrate, audioSourceBytes)

    val canCancelDownload: Boolean get() = download is DownloadState.Running

    /** Se ofrece actualizar solo cuando el error lo sugiere. */
    val canUpdateEngine: Boolean
        get() = (analysis as? AnalysisState.Failed)?.error is MediaError.EngineOutdated ||
            analysis is AnalysisState.UpdateFailed
}

enum class DownloadMode { VIDEO, AUDIO }

sealed interface DownloadState {
    val isActive: Boolean get() = this is Running || this is Saving

    data object None : DownloadState

    /** Estado del motor: pendiente, bajando o uniendo pistas. */
    data class Running(val job: JobState) : DownloadState

    /** Copiando a la galería. */
    data object Saving : DownloadState

    data class Saved(val file: OutputFile) : DownloadState

    data class Failed(val error: MediaError) : DownloadState

    data object Cancelled : DownloadState

    /** Android 8–9: el usuario no dio permiso de escritura. */
    data object PermissionDenied : DownloadState
}

enum class InputStatus {
    EMPTY,

    /** El texto todavía no es una URL válida, pero no se muestra error mientras se escribe. */
    TYPING,

    /** El usuario intentó analizar (o pegó) algo que no es válido. */
    INVALID,
    READY,
}

sealed interface AnalysisState {
    data object Idle : AnalysisState

    data object Loading : AnalysisState

    data class Loaded(val info: MediaInfo) : AnalysisState

    data class Failed(val error: MediaError) : AnalysisState

    /** Bajando y verificando una versión nueva de yt-dlp. */
    data object UpdatingEngine : AnalysisState

    /** No se pudo actualizar el motor. El análisis anterior había fallado con [MediaError.EngineOutdated]. */
    data class UpdateFailed(val error: MediaError) : AnalysisState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val extractor: MediaExtractor,
    private val engine: ExtractorEngine,
    private val downloader: MediaDownloader,
    private val library: MediaLibrary,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var analysisJob: Job? = null
    private var downloadJob: Job? = null

    /** En Android 8–9 hay que pedir el permiso de escritura antes de [onDownload]. */
    val needsStoragePermission: Boolean get() = library.needsWritePermission()

    fun onInputChange(text: String) {
        if (text == _state.value.input || _state.value.inputLocked) return
        cancelAnalysis()
        _state.update { current ->
            val status = when {
                text.isBlank() -> InputStatus.EMPTY
                UrlValidator.validate(text) is UrlCheck.Valid -> InputStatus.READY
                else -> InputStatus.TYPING
            }
            current.copy(input = text, status = status, invalidReason = null, analysis = AnalysisState.Idle)
        }
    }

    /** Pegado desde el portapapeles: si el texto trae una URL entre otras palabras, se queda solo con ella. */
    fun onPaste(clipboardText: String?) {
        val text = clipboardText?.trim().orEmpty()
        if (text.isEmpty() || _state.value.inputLocked) return
        cancelAnalysis()
        val found = UrlValidator.findUrl(text)
        _state.value = if (found != null) {
            HomeUiState(input = found.url, status = InputStatus.READY)
        } else {
            HomeUiState(input = text, status = InputStatus.INVALID, invalidReason = reasonFor(text))
        }
    }

    fun onAnalyze() {
        val check = UrlValidator.validate(_state.value.input)
        if (check is UrlCheck.Invalid) {
            _state.update { it.copy(status = InputStatus.INVALID, invalidReason = check.reason) }
            return
        }
        val url = (check as UrlCheck.Valid).url
        if (!_state.value.canAnalyze) return

        _state.update { it.copy(input = url, analysis = AnalysisState.Loading, download = DownloadState.None) }
        analysisJob = viewModelScope.launch {
            when (val result = extractor.extract(url)) {
                is Outcome.Success -> {
                    val info = result.value
                    val audio = AudioOptions.from(info)
                    _state.update {
                        it.copy(
                            analysis = AnalysisState.Loaded(info),
                            options = VideoOptions.from(info),
                            selectedOption = 0,
                            audioOptions = audio,
                            selectedAudio = 0,
                            audioBitrate = null,
                            audioSourceBytes = audio.firstOrNull()?.let { option ->
                                AudioOptions.sourceBytes(info, option)
                            },
                            fileName = info.title,
                        )
                    }
                }
                is Outcome.Failure -> _state.update { it.copy(analysis = AnalysisState.Failed(result.error)) }
            }
        }
    }

    /** Actualiza yt-dlp (con firma y hash verificados) y, si sale bien, vuelve a analizar. */
    fun onUpdateEngine() {
        if (!_state.value.canUpdateEngine) return
        cancelAnalysis()
        _state.update { it.copy(analysis = AnalysisState.UpdatingEngine) }
        analysisJob = viewModelScope.launch {
            when (val result = engine.update()) {
                is Outcome.Success -> {
                    _state.update { it.copy(analysis = AnalysisState.Idle) }
                    onAnalyze()
                }
                is Outcome.Failure -> _state.update { it.copy(analysis = AnalysisState.UpdateFailed(result.error)) }
            }
        }
    }

    fun onClear() {
        if (_state.value.inputLocked) return
        cancelAnalysis()
        _state.value = HomeUiState()
    }

    fun onSelectOption(index: Int) {
        if (index in _state.value.options.indices && !_state.value.download.isActive) {
            _state.update { it.copy(selectedOption = index) }
        }
    }

    fun onModeChange(mode: DownloadMode) {
        if (!_state.value.download.isActive) _state.update { it.copy(mode = mode, download = DownloadState.None) }
    }

    fun onSelectAudioFormat(index: Int) {
        val current = _state.value
        if (index !in current.audioOptions.indices || current.download.isActive) return
        val info = (current.analysis as? AnalysisState.Loaded)?.info
        val option = current.audioOptions[index]
        _state.update {
            it.copy(
                selectedAudio = index,
                audioBitrate = null,
                audioSourceBytes = info?.let { data -> AudioOptions.sourceBytes(data, option) },
            )
        }
    }

    /** `null` vuelve a "Automático". Solo valores que el formato ofrece. */
    fun onSelectBitrate(kbps: Int?) {
        val option = _state.value.audioOption ?: return
        if (_state.value.download.isActive || (kbps != null && kbps !in option.bitrates)) return
        _state.update { it.copy(audioBitrate = kbps) }
    }

    fun onFileNameChange(name: String) {
        if (!_state.value.download.isActive) _state.update { it.copy(fileName = name) }
    }

    /** Descarga la calidad elegida y la guarda en la galería. */
    fun onDownload() {
        val current = _state.value
        if (!current.canDownload) return
        val info = (current.analysis as AnalysisState.Loaded).info
        val (selection, expected) = when (current.mode) {
            DownloadMode.VIDEO -> current.options[current.selectedOption].let { it.selection to it.estimatedBytes }
            DownloadMode.AUDIO -> current.audioOption!!.selection(current.audioBitrate) to current.audioEstimatedBytes
        }
        val request = DownloadRequest(
            jobId = UUID.randomUUID().toString(),
            sourceUrl = info.sourceUrl,
            selection = selection,
            fileName = current.fileName.ifBlank { info.title },
            expectedBytes = expected,
        )
        _state.update { it.copy(download = DownloadState.Running(JobState.Pending)) }
        downloadJob = viewModelScope.launch {
            downloader.download(request).collect { job ->
                when (job) {
                    is JobState.Completed -> save(job.outputs.first())
                    is JobState.Failed -> _state.update { it.copy(download = DownloadState.Failed(job.error)) }
                    JobState.Cancelled -> _state.update { it.copy(download = DownloadState.Cancelled) }
                    else -> _state.update { it.copy(download = DownloadState.Running(job)) }
                }
            }
        }
    }

    fun onStoragePermissionDenied() {
        _state.update { it.copy(download = DownloadState.PermissionDenied) }
    }

    /**
     * Cancelar termina yt-dlp y ffmpeg y borra los parciales (ADR-005). No se ofrece mientras se
     * guarda: la copia es corta y cortarla dejaría un archivo a medias en la galería.
     */
    fun onCancelDownload() {
        if (!_state.value.canCancelDownload) return
        downloadJob?.cancel()
        downloadJob = null
        _state.update { it.copy(download = DownloadState.Cancelled) }
    }

    private suspend fun save(file: OutputFile) {
        _state.update { it.copy(download = DownloadState.Saving) }
        val result = library.save(file)
        _state.update {
            it.copy(
                download = when (result) {
                    is Outcome.Success -> DownloadState.Saved(result.value)
                    is Outcome.Failure -> DownloadState.Failed(result.error)
                },
            )
        }
    }

    /** Cancelar la corrutina termina también el proceso de yt-dlp (ADR-005). */
    private fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
    }

    private fun reasonFor(text: String): UrlCheck.Reason =
        (UrlValidator.validate(text) as? UrlCheck.Invalid)?.reason ?: UrlCheck.Reason.NOT_A_URL
}
