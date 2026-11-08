package com.fabapps.mediaforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.fabapps.mediaforge.domain.jobs.JobScheduler
import com.fabapps.mediaforge.ui.MediaForgeApp
import com.fabapps.mediaforge.ui.theme.MediaForgeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var scheduler: JobScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaForgeTheme {
                MediaForgeApp()
            }
        }
    }

    /** Con la app visible la cola puede arrancar el servicio: recupera lo interrumpido y sigue (ADR-017). */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { scheduler.start() }
    }
}
