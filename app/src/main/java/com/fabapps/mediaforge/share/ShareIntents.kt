package com.fabapps.mediaforge.share

import android.content.Intent

/** Lee un `ACTION_SEND` de texto (main.md §8.1). Los extras vienen de otra app: nada se da por bueno. */
object ShareIntents {
    fun read(intent: Intent?): SharedContent? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("text/") != true) return null
        // Un extra mal armado puede fallar al desarmarse; en ese caso se ignora el intent.
        return runCatching {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
            SharedContent(text, subject).takeIf { text != null || subject != null }
        }.getOrNull()
    }
}
