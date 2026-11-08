package com.fabapps.mediaforge.jobs

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.fabapps.mediaforge.domain.jobs.JobScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Conecta la cola con Android: arranca [JobService] cuando entra trabajo y avisa cuando un trabajo
 * termina. Se instala una vez, al crear la aplicación.
 */
@Singleton
class JobServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduler: JobScheduler,
    private val notifications: JobNotifications,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun install() {
        notifications.createChannels()
        // UNDISPATCHED: suscripto antes de que la cola pueda emitir algo.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.finished.collect(notifications::showResult)
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.activity.map { it.isBusy }.distinctUntilChanged().filter { it }.collect { startService() }
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, JobService.startIntent(context))
        } catch (e: IllegalStateException) {
            // Android 12+ no deja arrancarlo desde segundo plano (ForegroundServiceStartNotAllowedException).
            // La cola sigue mientras viva el proceso; si muere, se recupera al abrir la app.
            Log.w(TAG, "foreground service not allowed now", e)
        }
    }

    private companion object {
        const val TAG = "JobServiceStarter"
    }
}
