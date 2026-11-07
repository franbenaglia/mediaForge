package com.fabapps.mediaforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fabapps.mediaforge.ui.MediaForgeApp
import com.fabapps.mediaforge.ui.theme.MediaForgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaForgeTheme {
                MediaForgeApp()
            }
        }
    }
}
