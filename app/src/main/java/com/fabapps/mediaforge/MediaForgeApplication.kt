package com.fabapps.mediaforge

import android.app.Application
import com.fabapps.mediaforge.jobs.JobServiceStarter
import com.fabapps.mediaforge.jobs.QueueSettings
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MediaForgeApplication : Application() {
    @Inject lateinit var jobServiceStarter: JobServiceStarter

    @Inject lateinit var queueSettings: QueueSettings

    override fun onCreate() {
        super.onCreate()
        jobServiceStarter.install()
        queueSettings.install()
    }
}
