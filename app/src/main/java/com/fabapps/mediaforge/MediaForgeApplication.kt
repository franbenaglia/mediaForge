package com.fabapps.mediaforge

import android.app.Application
import com.fabapps.mediaforge.jobs.JobServiceStarter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MediaForgeApplication : Application() {
    @Inject lateinit var jobServiceStarter: JobServiceStarter

    override fun onCreate() {
        super.onCreate()
        jobServiceStarter.install()
    }
}
