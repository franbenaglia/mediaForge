package com.fabapps.mediaforge.ui.home

import androidx.lifecycle.ViewModel
import com.fabapps.mediaforge.domain.url.UrlCheck
import com.fabapps.mediaforge.domain.url.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HomeUiState(
    val input: String = "",
    val status: InputStatus = InputStatus.EMPTY,
    /** Motivo a mostrar; solo cuando [status] es [InputStatus.INVALID]. */
    val invalidReason: UrlCheck.Reason? = null,
    /** URL validada lista para analizar. La pantalla la consume y llama a [HomeViewModel.onAnalysisHandled]. */
    val pendingAnalysis: String? = null,
) {
    val canAnalyze: Boolean get() = status == InputStatus.READY
}

enum class InputStatus {
    EMPTY,

    /** El texto todavía no es una URL válida, pero no se muestra error mientras se escribe. */
    TYPING,

    /** El usuario intentó analizar (o pegó) algo que no es válido. */
    INVALID,
    READY,
}

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun onInputChange(text: String) {
        _state.update { current ->
            val status = when {
                text.isBlank() -> InputStatus.EMPTY
                UrlValidator.validate(text) is UrlCheck.Valid -> InputStatus.READY
                else -> InputStatus.TYPING
            }
            current.copy(input = text, status = status, invalidReason = null)
        }
    }

    /** Pegado desde el portapapeles: si el texto trae una URL entre otras palabras, se queda solo con ella. */
    fun onPaste(clipboardText: String?) {
        val text = clipboardText?.trim().orEmpty()
        if (text.isEmpty()) return
        val found = UrlValidator.findUrl(text)
        if (found != null) {
            _state.update { HomeUiState(input = found.url, status = InputStatus.READY) }
        } else {
            _state.update { HomeUiState(input = text, status = InputStatus.INVALID, invalidReason = reasonFor(text)) }
        }
    }

    fun onAnalyze() {
        _state.update { current ->
            when (val check = UrlValidator.validate(current.input)) {
                is UrlCheck.Valid -> current.copy(input = check.url, pendingAnalysis = check.url)
                is UrlCheck.Invalid -> current.copy(status = InputStatus.INVALID, invalidReason = check.reason)
            }
        }
    }

    fun onAnalysisHandled() {
        _state.update { it.copy(pendingAnalysis = null) }
    }

    fun onClear() {
        _state.value = HomeUiState()
    }

    private fun reasonFor(text: String): UrlCheck.Reason =
        (UrlValidator.validate(text) as? UrlCheck.Invalid)?.reason ?: UrlCheck.Reason.NOT_A_URL
}
