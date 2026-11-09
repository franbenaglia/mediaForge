package com.fabapps.mediaforge

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import com.fabapps.mediaforge.engine.EngineUpdates
import com.fabapps.mediaforge.jobs.JobServiceStarter
import com.fabapps.mediaforge.jobs.QueueSettings
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MediaForgeApplication : Application() {
    @Inject lateinit var jobServiceStarter: JobServiceStarter

    @Inject lateinit var queueSettings: QueueSettings

    @Inject lateinit var engineUpdates: EngineUpdates

    override fun onCreate() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) enableStrictMode()
        super.onCreate()
        jobServiceStarter.install()
        queueSettings.install()
        engineUpdates.install()
    }

    /**
     * Solo en debug (T11.6): anota en logcat (etiqueta `StrictMode`) el disco o la red en el hilo
     * principal y los recursos sin cerrar. No cierra la app: sirve para encontrarlos al usarla.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }
}
