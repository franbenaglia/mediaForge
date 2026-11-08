package com.fabapps.mediaforge.jobs

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.fabapps.mediaforge.domain.jobs.Job
import com.fabapps.mediaforge.domain.jobs.JobScheduler
import com.fabapps.mediaforge.domain.jobs.JobStatus
import com.fabapps.mediaforge.domain.jobs.SchedulerActivity
import com.fabapps.mediaforge.domain.model.JobState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Mantiene vivo el proceso mientras la cola tiene trabajo y muestra su progreso (ADR-017). No corre
 * trabajos: eso lo hace [JobScheduler]. Lo arranca [JobServiceStarter] y se detiene solo al vaciarse la cola.
 */
@AndroidEntryPoint
class JobService : Service() {
    @Inject lateinit var scheduler: JobScheduler

    @Inject lateinit var notifications: JobNotifications

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing: kotlinx.coroutines.Job? = null

    /** Tipos con los que está en primer plano; `null` si no lo está. */
    private var foregroundTypes: Int? = null

    /** El sistema rechazó `mediaProcessing` (pasa en algunos teléfonos o al agotar sus 6 h): se usa `dataSync`. */
    private var mediaProcessingRejected = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Después de startForegroundService hay que pasar a primer plano enseguida, haya o no trabajo.
        promote(scheduler.activity.value, notifications.progress(null, null, 0))
        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_JOB_ID)?.let { id -> scope.launch { scheduler.cancel(id) } }
        }
        if (observing == null && foregroundTypes != null) observing = scope.launch { observe() }
        return START_NOT_STICKY
    }

    private suspend fun observe() {
        combine(scheduler.activity, scheduler.live, scheduler.observeAll()) { activity, live, jobs ->
            Snapshot(activity, live, jobs)
        }.conflate().collect { snapshot ->
            if (!snapshot.activity.isBusy) {
                stop()
                observing?.cancel()
                observing = null
                return@collect
            }
            val running = snapshot.jobs.filter { it.status == JobStatus.RUNNING || it.status == JobStatus.SAVING }
            val main = running.lastOrNull()
            val others = snapshot.jobs.count { it.status.isActive } - 1
            val waitingForWifi = snapshot.activity.waitingForNetwork > 0
            promote(
                snapshot.activity,
                notifications.progress(main, main?.let { snapshot.live[it.id] }, others, waitingForWifi),
            )
            // La notificación no necesita más de dos actualizaciones por segundo.
            delay(UPDATE_INTERVAL_MS)
        }
    }

    private class Snapshot(val activity: SchedulerActivity, val live: Map<String, JobState>, val jobs: List<Job>)

    /** Primer plano con el tipo que corresponde a lo que corre; si el tipo no cambia, solo actualiza el texto. */
    private fun promote(activity: SchedulerActivity, notification: android.app.Notification) {
        val types = typesFor(activity)
        if (types == foregroundTypes) {
            notifications.notify(JobNotifications.PROGRESS_ID, notification)
            return
        }
        if (startForeground(notification, types)) return
        val fallback = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        if (types != fallback) {
            mediaProcessingRejected = true
            if (startForeground(notification, fallback)) return
        }
        // Sin primer plano no se puede seguir en segundo plano, pero la app no se cae: la cola sigue
        // mientras esté visible y el servicio vuelve a arrancar con el próximo trabajo.
        observing?.cancel()
        observing = null
        stop()
    }

    private fun startForeground(notification: android.app.Notification, types: Int): Boolean = try {
        ServiceCompat.startForeground(this, JobNotifications.PROGRESS_ID, notification, types)
        foregroundTypes = types
        true
    } catch (e: IllegalStateException) {
        // InvalidForegroundServiceTypeException y ForegroundServiceStartNotAllowedException.
        Log.w(TAG, "startForeground rechazado (tipos $types): ${e.javaClass.simpleName}")
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "startForeground rechazado (tipos $types): ${e.javaClass.simpleName}")
        false
    }

    /** Descargas: `dataSync`. Conversiones: `mediaProcessing` desde Android 15; antes, `dataSync`. */
    private fun typesFor(activity: SchedulerActivity): Int {
        // Antes de Android 10 no hay tipos: ServiceCompat los ignora.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var types = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            !mediaProcessingRejected &&
            activity.runningConversions > 0
        ) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        }
        if (activity.runningDownloads > 0 || activity.waiting > 0 || types == 0) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return types
    }

    /** Android 15+: se agotaron las 6 h del día para este tipo. Hay que parar en segundos. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        scope.launch {
            scheduler.interruptAll()
            stop()
        }
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foregroundTypes = null
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_CANCEL = "com.fabapps.mediaforge.action.CANCEL_JOB"
        private const val EXTRA_JOB_ID = "jobId"
        private const val UPDATE_INTERVAL_MS = 500L
        private const val TAG = "JobService"

        fun startIntent(context: Context) = Intent(context, JobService::class.java)

        fun cancelIntent(context: Context, jobId: String) =
            Intent(context, JobService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_JOB_ID, jobId)
    }
}
