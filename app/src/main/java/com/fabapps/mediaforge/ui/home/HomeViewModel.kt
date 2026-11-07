package com.fabapps.mediaforge.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabapps.mediaforge.domain.engine.ExtractorEngine
import com.fabapps.mediaforge.domain.engine.MediaExtractor
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.MediaInfo
import com.fabapps.mediaforge.domain.url.UrlCheck
import com.fabapps.mediaforge.domain.url.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
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
) {
    val canAnalyze: Boolean
        get() = status == InputStatus.READY &&
            analysis !is AnalysisState.Loading &&
            analysis !is AnalysisState.UpdatingEngine

    /** Se ofrece actualizar solo cuando el error lo sugiere. */
    val canUpdateEngine: Boolean
        get() = (analysis as? AnalysisState.Failed)?.error is MediaError.EngineOutdated ||
            analysis is AnalysisState.UpdateFailed
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
class HomeViewModel @Inject constructor(private val extractor: MediaExtractor, private val engine: ExtractorEngine) :
    ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var analysisJob: Job? = null

    fun onInputChange(text: String) {
        if (text == _state.value.input) return
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
        if (text.isEmpty()) return
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

        _state.update { it.copy(input = url, analysis = AnalysisState.Loading) }
        analysisJob = viewModelScope.launch {
            val analysis = when (val result = extractor.extract(url)) {
                is Outcome.Success -> AnalysisState.Loaded(result.value)
                is Outcome.Failure -> AnalysisState.Failed(result.error)
            }
            _state.update { it.copy(analysis = analysis) }
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
        cancelAnalysis()
        _state.value = HomeUiState()
    }

    /** Cancelar la corrutina termina también el proceso de yt-dlp (ADR-005). */
    private fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
    }

    private fun reasonFor(text: String): UrlCheck.Reason =
        (UrlValidator.validate(text) as? UrlCheck.Invalid)?.reason ?: UrlCheck.Reason.NOT_A_URL
}
