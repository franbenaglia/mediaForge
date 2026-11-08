package com.fabapps.mediaforge.ui.common

import androidx.annotation.StringRes
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.ProcessingStep

/** Texto corto del estado de un trabajo. */
@StringRes
fun JobState.statusRes(): Int = when (this) {
    JobState.Pending -> R.string.job_status_pending
    JobState.Analyzing -> R.string.job_status_analyzing
    is JobState.Downloading -> R.string.job_status_downloading
    is JobState.Processing -> when (step) {
        ProcessingStep.MERGE -> R.string.job_status_processing_merge
        ProcessingStep.EXTRACT_AUDIO -> R.string.job_status_processing_extract
        ProcessingStep.TRANSCODE -> R.string.job_status_processing_transcode
        ProcessingStep.METADATA -> R.string.job_status_processing_metadata
        ProcessingStep.REMUX -> R.string.job_status_processing_remux
        ProcessingStep.PREPARE_INPUT -> R.string.job_status_processing_prepare
        ProcessingStep.EXTRACT_FRAME -> R.string.job_status_processing_frame
    }
    is JobState.Completed -> R.string.job_status_completed
    is JobState.Failed -> R.string.job_status_failed
    JobState.Cancelled -> R.string.job_status_cancelled
}

/** Explicación para el usuario. El detalle técnico del motor no se muestra acá. */
@StringRes
fun MediaError.messageRes(): Int = when (this) {
    is MediaError.InvalidUrl -> R.string.error_invalid_url
    is MediaError.UnsupportedSite -> R.string.error_unsupported_site
    is MediaError.LoginRequired -> R.string.error_login_required
    is MediaError.AccessDenied -> R.string.error_access_denied
    is MediaError.Network -> R.string.error_network
    is MediaError.InsufficientStorage -> R.string.error_insufficient_storage
    is MediaError.UnsupportedConversion -> R.string.error_unsupported_conversion
    is MediaError.EngineOutdated -> R.string.error_engine_outdated
    is MediaError.Unknown -> R.string.error_unknown
}

/** `true` mientras el trabajo puede avanzar todavía (muestra barra de progreso). */
val JobState.isActive: Boolean
    get() = when (this) {
        JobState.Pending, JobState.Analyzing, is JobState.Downloading, is JobState.Processing -> true
        is JobState.Completed, is JobState.Failed, JobState.Cancelled -> false
    }

/** Progreso entre 0 y 1, o `null` si no se conoce (barra indeterminada). */
val JobState.progressFraction: Float?
    get() = when (this) {
        is JobState.Downloading -> progress
        is JobState.Processing -> progress
        else -> null
    }?.coerceIn(0f, 1f)
