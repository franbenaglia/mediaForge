package com.fabapps.mediaforge.jobs

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.fabapps.mediaforge.MainActivity
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.jobs.Job
import com.fabapps.mediaforge.domain.jobs.JobStatus
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.ui.common.messageRes
import com.fabapps.mediaforge.ui.common.progressFraction
import com.fabapps.mediaforge.ui.common.statusRes
import com.fabapps.mediaforge.ui.format.Formatters
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import javax.inject.Inject
import javax.inject.Singleton

/** Notificación del servicio (progreso) y avisos de trabajos terminados. Textos siempre de recursos. */
@Singleton
class JobNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_PROGRESS, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_progress))
                .setShowBadge(false)
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_RESULTS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_results))
                .build(),
        )
    }

    /**
     * Progreso del trabajo en curso más viejo, con Cancelar. Si no corre ninguno (espera de un
     * reintento), un texto genérico.
     */
    fun progress(main: Job?, state: JobState?, others: Int): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_nav_downloads)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp())
        if (main == null) {
            return builder.setContentTitle(context.getString(R.string.notification_waiting_retry)).build()
        }
        val fraction = state?.progressFraction
        builder
            .setContentTitle(main.title)
            .setContentText(state?.let(::describe) ?: context.getString(R.string.job_status_pending))
            .setProgress(PROGRESS_MAX, ((fraction ?: 0f) * PROGRESS_MAX).toInt(), fraction == null)
            .addAction(0, context.getString(R.string.notification_cancel), cancel(main.id))
        if (others > 0) {
            builder.setSubText(context.resources.getQuantityString(R.plurals.notification_more, others, others))
        }
        return builder.build()
    }

    /** Aviso de un trabajo que terminó: tocar abre el archivo guardado o la app. */
    fun showResult(job: Job) {
        val text = when (job.status) {
            JobStatus.COMPLETED -> context.getString(R.string.notification_done)
            JobStatus.FAILED -> context.getString(
                R.string.notification_failed,
                context.getString(job.error?.messageRes() ?: R.string.error_unknown),
            )
            JobStatus.INTERRUPTED -> context.getString(R.string.notification_interrupted)
            else -> return
        }
        val output = job.output
        val tap = if (job.status == JobStatus.COMPLETED && output != null) {
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(output.location.toUri(), output.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(context, job.id.hashCode(), view, PendingIntent.FLAG_IMMUTABLE)
        } else {
            openApp()
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_nav_downloads)
            .setContentTitle(job.title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        notify(job.id.hashCode(), notification)
    }

    fun notify(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(id, notification)
    }

    private fun describe(state: JobState): String {
        val locale = context.resources.configuration.locales[0]
        val parts = mutableListOf(context.getString(state.statusRes()))
        state.progressFraction?.let { parts += NumberFormat.getPercentInstance(locale).format(it) }
        if (state is JobState.Downloading) {
            state.bytesPerSecond?.let { parts += context.getString(R.string.job_speed, Formatters.bytes(it, locale)) }
        }
        return parts.joinToString(context.getString(R.string.detail_separator))
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancel(jobId: String): PendingIntent = PendingIntent.getService(
        context,
        jobId.hashCode(),
        JobService.cancelIntent(context, jobId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val PROGRESS_ID = 1
        private const val CHANNEL_PROGRESS = "jobs_progress"
        private const val CHANNEL_RESULTS = "jobs_results"
        private const val PROGRESS_MAX = 1000
    }
}
